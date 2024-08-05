package com.noi.llm.openai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noi.AiModel;
import com.noi.image.AiImage;
import com.noi.language.AiPrompt;
import com.noi.llm.LLMService;
import com.noi.models.DbImage;
import com.noi.models.DbLanguage;
import com.noi.models.DbMedia;
import com.noi.models.Model;
import com.noi.tools.FileTools;
import com.noi.tools.JsonTools;
import com.noi.tools.SystemEnv;
import com.noi.video.AiVideo;
import com.noi.video.audio.AudioExtractionRequest;
import com.noi.video.audio.AudioSummaryRequest;
import com.noi.video.scenes.VideoSceneSummaryRequest;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenAIService extends LLMService {
    private static final String COMPLETION_URL = "https://api.openai.com/v1/chat/completions";
    private static final String TRANSCRIPTION_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static String API_KEY = null;
    public static String NAME = "OpenAI";

    public OpenAIService(AiModel model) {
        super(NAME, model);
        API_KEY = SystemEnv.get("OPENAI_API_KEY", null);
    }

    @Override
    public JsonObject labelForSameVideoScene(AiVideo.SceneChange sceneChange) throws IOException, SQLException, NamingException {
        System.out.println("OpenAIService:labelForSameVideoScene: " + sceneChange);

        Connection con = null;

        try {
            con = Model.connectX();
            AiPrompt sceneChangePrompt = DbLanguage.findPrompt(con, AiPrompt.TYPE_SCENE_CHANGE);
            JsonObject payload = createSceneChangePayload(sceneChangePrompt, sceneChange);
            String content = postToCompletions(payload);
            String jsonResponse = content.replace("```json\n", "").replace("\n```", "").replace("\n", "");
            System.out.println("OpenAIService:labelForSameVideoScene:jsonResponse=" + jsonResponse);
            // now try to parse the json
            JsonObject responseRoot = new JsonObject();
            JsonObject root = new JsonParser().parse(jsonResponse).getAsJsonObject();
            if (root != null) {
                boolean isFromSameScene = JsonTools.getAsBoolean(root, "from_same_scene", true);
                String llmExplanation = JsonTools.getAsString(root, "explanation");
                responseRoot.addProperty("same_scene", isFromSameScene);
                responseRoot.addProperty("explanation", llmExplanation);
            } else {
                responseRoot.addProperty("error", true);
                responseRoot.addProperty("same_scene", true);
                responseRoot.addProperty("explanation", jsonResponse);
            }

            return responseRoot;

        } finally {
            Model.close(con);
        }
    }

    @Override
    public JsonObject transcribeVideo(AudioExtractionRequest request) throws IOException {
        JsonObject returnJson = new JsonObject();
        String rootFolder = SystemEnv.get("NOI_PATH", "/Users/martin/work/tmp/ai-data");
        AiVideo video = request.getVideo();


        // check if there is an audio file (was created by the py script that also extracted the scene images)
        // Note! the folder for the raw (ORB based) files can be different from the processed file folder (which is based on the name)
        String videoFileName = FileTools.getFileName(video.getUrl(), false);
        File audioPath = FileTools.joinPath(rootFolder, "videos", videoFileName, videoFileName + ".mp3");
        System.out.println("OpenAIService:transcribeVideo:audioPath=" + audioPath);
        if (!audioPath.exists()) {
            returnJson.addProperty("error", "No audio file found for " + video);
            return returnJson;
        }

        returnJson.addProperty("sound_url", audioPath.getAbsolutePath());
        returnJson.addProperty("uuid", request.getUUID());
        returnJson.addProperty("model_name", request.getModelName());

        String transcriptionResponse = postToTranscription(audioPath, request.getModel());
        JsonObject transcription = new JsonParser().parse(transcriptionResponse).getAsJsonObject();
        returnJson.addProperty("transcription", transcription.get("text").getAsString());

        // store as json file
        File transcriptPath = FileTools.joinPath(rootFolder, "videos", video.getName(), video.getName() + "_transcript.json");
        FileTools.writeToFile(returnJson, transcriptPath);

        return returnJson;
    }

    /**
     * read the file content from local file and format as base 64 string
     *
     * @param request
     * @return
     * @throws IOException
     */
    @Override
    public JsonObject summarizeVideoScenes(VideoSceneSummaryRequest request) throws IOException, SQLException, NamingException {
        System.out.println("OpenAIService:summarizeVideoScenes for " + request.getSceneChanges().size() + " scenes");
        JsonObject response = new JsonObject();
        if (request.getSceneChanges().size() == 0) {
            return response;
        }

        List<String> base64Frames = new ArrayList<>();
        List<String> sceneUrls = new ArrayList<>();

        List<AiVideo.SceneChange> sceneChanges = request.getSceneChanges();
        // add the starting image
        base64Frames.add(imageToBase64String(sceneChanges.get(0).getLastImage()));
        sceneUrls.add(sceneChanges.get(0).getLastImage().getUrl());
        // loop urls and convert the content to base64
        for (AiVideo.SceneChange sceneChange : sceneChanges) {
            base64Frames.add(imageToBase64String(sceneChange.getFirstImage()));
            sceneUrls.add(sceneChange.getFirstImage().getUrl());
        }

        Connection con = null;
        try {
            con = Model.connectX();
            AiPrompt summaryPrompt = DbLanguage.findPrompt(con, AiPrompt.TYPE_VIDEO_SUMMARY);
            if (summaryPrompt != null) {
                AiVideo video = request.getVideo();
                // format the message, and call the LLM
                AiModel summaryModel = summaryPrompt.getModel();
                JsonObject postData = createVideoSummaryPayload(summaryPrompt, summaryModel, base64Frames);
                String llmResponseContent = postToCompletions(postData);

                response.addProperty("uuid", request.getUUID());
                response.addProperty("model_name", summaryModel.getName());
                if (summaryPrompt.getSystemPrompt() != null) {
                    response.addProperty("system_prompt", summaryPrompt.getSystemPrompt());
                }
                response.addProperty("user_prompt", summaryPrompt.getPrompt());
                response.add("scenes", JsonTools.toJsonArray(sceneUrls));

                response.addProperty("summary", llmResponseContent);

                // write json to local file
                String rootFolder = SystemEnv.get("NOI_PATH", "/Users/martin/work/tmp/ai-data");
                File summaryFile = FileTools.joinPath(rootFolder, "videos", video.getName(), video.getName() + "_scene_summary.json");
                FileTools.writeToFile(response, summaryFile);

                // persist it all to db!
                Long requestId = DbMedia.insertVideoSummaryRequest(con, request.getUUID(), video.getId(), summaryPrompt, summaryModel);

                // loop through images / scenes used to create the summary and persist the relations
                for (String sceneImageURL : sceneUrls) {
                    AiImage image = DbImage.findForVideo(con, video.getId(), sceneImageURL);
                    DbMedia.insertVideoSummaryScene(con, video.getId(), image, requestId);
                }

                DbMedia.insertVideoSummary(con, requestId, video.getId(), llmResponseContent);
            }
        } finally {
            Model.close(con);
        }

        System.out.println("OpenAIService:done summarizeVideoScenes for " + request.getVideo().getName());

        return response;
    }

    @Override
    public JsonObject summarizeVideoSound(AudioSummaryRequest summaryRequest) throws IOException {
        AiVideo video = summaryRequest.getVideo();
        String rootFolder = SystemEnv.get("NOI_PATH", "/Users/martin/work/tmp/ai-data");

        JsonObject returnJson = new JsonObject();

        returnJson.addProperty("video_id", summaryRequest.getVideo().getId());
        returnJson.addProperty("uuid", summaryRequest.getUUID());
        returnJson.addProperty("model_name", summaryRequest.getModelName());
        returnJson.addProperty("system_prompt", summaryRequest.getPrompt().getSystemPrompt());
        returnJson.addProperty("user_prompt", summaryRequest.getPrompt().getPrompt());

        JsonObject postData = createSoundSummaryPayload(summaryRequest);
        String llmResponseContent = postToCompletions(postData);
        returnJson.addProperty("summary", llmResponseContent);

        // store as json file
        File summaryPath = FileTools.joinPath(rootFolder, "videos", video.getName(), video.getName() + "_transcript_summary.json");
        FileTools.writeToFile(returnJson, summaryPath);

        return returnJson;
    }

    private HttpPost createHttpPost(String url, String apiKey, File payloadFile, Map<String, String> formHeaders) {
        FileBody fileBody = new FileBody(payloadFile, ContentType.DEFAULT_BINARY);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("file", fileBody);

        for (Map.Entry<String, String> entry : formHeaders.entrySet()) {
            StringBody header = new StringBody(entry.getValue(), ContentType.MULTIPART_FORM_DATA);
            builder.addPart(entry.getKey(), header);
        }

        final HttpEntity entity = builder.build();
        return createHttpPost(url, apiKey, entity);
    }

    private HttpPost createHttpPost(String url, String apiKey, JsonObject payloadJson) {
        Gson gson = new Gson();
        StringEntity entity = new StringEntity(gson.toJson(payloadJson), ContentType.APPLICATION_JSON);
        return createHttpPost(url, apiKey, entity);
    }

    private HttpPost createHttpPost(String url, String apiKey, HttpEntity entity) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Authorization", "Bearer " + apiKey);
        httpPost.setHeader("User-Agent", "Noi");
        httpPost.setEntity(entity);
        return httpPost;
    }


    private String postToCompletions(JsonObject postPayload) throws IOException {
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;

        try {
            client = HttpClients.createDefault();

            HttpPost httpPost = createHttpPost(COMPLETION_URL, API_KEY, postPayload);
            response = client.execute(httpPost);

            return parseResponseMessageContent(response);

        } finally {
            if (response != null) {
                response.close();
            }
            if (client != null) {
                client.close();
            }
        }
    }

    private String postToTranscription(File payloadFile, AiModel model) throws IOException {
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;

        try {
            client = HttpClients.createDefault();

            // post to openAI
            Map<String, String> formHeader = new HashMap<>();
            formHeader.put("model", model.getName());
            //formHeader.put("response_format","text");
            HttpPost httpPost = createHttpPost(TRANSCRIPTION_URL, API_KEY, payloadFile, formHeader);
            response = client.execute(httpPost);

            return parseResponseMessageContent(response);

        } finally {
            if (response != null) {
                response.close();
            }
            if (client != null) {
                client.close();
            }
        }
    }

    private String parseResponseMessageContent(CloseableHttpResponse response) throws IOException {
        String fileResponse = FileTools.readToString(response.getEntity().getContent());
        StatusLine statusLine = response.getStatusLine();
        // write to file log
//        AiRequestLogger.logLabelResponse(AiRequestLogger.LABEL, request, fileResponse);

        if (statusLine.getStatusCode() == HttpServletResponse.SC_CREATED || statusLine.getStatusCode() == HttpServletResponse.SC_OK) {
            JsonObject root = new JsonParser().parse(fileResponse).getAsJsonObject();
            if (root != null) {
                //String modelName = JsonTools.getAsString(root, "model");
                JsonArray choicesArray = root.getAsJsonArray("choices");
                if (choicesArray != null && !choicesArray.isJsonNull()) {
                    for (int c = 0; c < choicesArray.size(); c++) {
                        JsonObject choice = choicesArray.get(c).getAsJsonObject();
                        JsonObject message = JsonTools.getAsObject(choice, "message");
                        if (message != null && !message.isJsonNull()) {
                            return JsonTools.getAsString(message, "content");
                        }
                    }
                }
            }

            return fileResponse;
        } else {
            return "Error";
        }
    }


    private String parseSummaryResponse(JsonObject llmResponse) {
        // summaryJson['summary'] = llmResponse.choices[0].message.content
        try {
            if (llmResponse != null && !llmResponse.isJsonNull()) {
                JsonArray choices = llmResponse.get("choices").getAsJsonArray();
                JsonObject choice = choices.get(0).getAsJsonObject();
                return choice.get("message").getAsJsonObject().get("content").getAsString();
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return e.getMessage();
        }
        return null;
    }

    private JsonObject createVideoSummaryPayload(AiPrompt summaryPrompt, AiModel model, List<String> base64Frames) {
        JsonObject post = new JsonObject();

        post.addProperty("model", model.getName());
        post.addProperty("temperature", 0);

        JsonArray messages = new JsonArray();
        post.add("messages", messages);

        if (summaryPrompt.getSystemPrompt() != null) {
            JsonObject system = new JsonObject();
            messages.add(system);
            system.addProperty("role", "system");
            system.addProperty("content", summaryPrompt.getSystemPrompt());
        }

        JsonObject user = new JsonObject();
        messages.add(user);
        user.addProperty("role", "user");
        JsonArray contentArray = new JsonArray();
        user.add("content", contentArray);

        JsonObject userPrompt = new JsonObject();
        contentArray.add(userPrompt);
        userPrompt.addProperty("type", "text");
        userPrompt.addProperty("text", summaryPrompt.getPrompt());
        for (String base64 : base64Frames) {
            JsonObject image = new JsonObject();
            contentArray.add(image);

            image.addProperty("type", "image_url");

            JsonObject imageUrl = new JsonObject();
            image.add("image_url", imageUrl);
            imageUrl.addProperty("url", base64);
            imageUrl.addProperty("detail", "low");
        }
        /*
        model=model,
                messages=[
                    {"role": "system",
                    "content": system_prompt},
                    {"role": "user", "content": [
                        user_prompt,
                        *map(lambda x: {"type": "image_url",
                                        "image_url": {"url": f'data:image/jpg;base64,{x}', "detail": "low"}}, base64Frames)
                    ],
                    }
                ],
                temperature=0,
         */
        return post;
    }

    private JsonObject createSceneChangePayload(AiPrompt scenePrompt, AiVideo.SceneChange sceneChange) throws IOException {
        List<String> base64Frames = new ArrayList<>();
        base64Frames.add(imageToBase64String(sceneChange.getLastImage()));
        base64Frames.add(imageToBase64String(sceneChange.getFirstImage()));

        JsonObject post = new JsonObject();

        post.addProperty("model", scenePrompt.getModel().getName());
        post.addProperty("temperature", 0);

        JsonArray messages = new JsonArray();
        post.add("messages", messages);

        JsonObject system = new JsonObject();
        if (scenePrompt.getSystemPrompt() != null) {
            messages.add(system);
            system.addProperty("role", "system");
            system.addProperty("content", scenePrompt.getSystemPrompt());
        }

        JsonObject user = new JsonObject();
        messages.add(user);
        user.addProperty("role", "user");
        JsonArray contentArray = new JsonArray();
        user.add("content", contentArray);

        JsonObject userPrompt = new JsonObject();
        contentArray.add(userPrompt);
        userPrompt.addProperty("type", "text");
        userPrompt.addProperty("text", scenePrompt.getPrompt());
        for (String base64 : base64Frames) {
            JsonObject image = new JsonObject();
            contentArray.add(image);

            image.addProperty("type", "image_url");

            JsonObject imageUrl = new JsonObject();
            image.add("image_url", imageUrl);
            imageUrl.addProperty("url", base64);
            imageUrl.addProperty("detail", "low");
        }
        return post;
    }

    private static JsonObject createSoundSummaryPayload(AudioSummaryRequest request) {
        AiPrompt prompt = request.getPrompt();
        AiModel model = request.getModel();
        String text = request.getText();

        JsonObject payLoad = new JsonObject();
        payLoad.addProperty("temperature", 0);
        payLoad.addProperty("model", model.getName());

        JsonArray array = new JsonArray();
        payLoad.add("messages", array);

        if (prompt.getSystemPrompt() != null) {
            JsonObject sysMessage = new JsonObject();
            array.add(sysMessage);
            sysMessage.addProperty("role", "system");
            sysMessage.addProperty("content", prompt.getSystemPrompt());
        }

        JsonObject userMessage = new JsonObject();
        array.add(userMessage);

        userMessage.addProperty("role", "user");

        JsonArray contentArray = new JsonArray();
        userMessage.add("content", contentArray);
        JsonObject content = new JsonObject();
        contentArray.add(content);

        content.addProperty("type", "text");
        if (prompt.getPrompt() != null && !prompt.getPrompt().isEmpty()) {
            // try to replace the placeholder token '{text}' with the actual text
            content.addProperty("text", prompt.getPrompt().replace("{text}", text));
        } else {
            // fallback
            content.addProperty("text", text);
        }

        return payLoad;
    }
}
