package com.noi.image.label;

import com.google.gson.*;
import com.noi.AiModel;
import com.noi.Status;
import com.noi.image.AiImage;
import com.noi.image.AiImageService;
import com.noi.language.AiImageLabelRequest;
import com.noi.language.AiLabelConsolidateRequest;
import com.noi.language.AiPrompt;
import com.noi.llm.openai.OpenAIService;
import com.noi.models.DbImage;
import com.noi.models.DbImageLabel;
import com.noi.models.DbRequest;
import com.noi.models.Model;
import com.noi.requests.AiRequestLogger;
import com.noi.requests.ImageLabelResponse;
import com.noi.tools.FileTools;
import com.noi.tools.JsonTools;
import com.noi.tools.SystemEnv;
import org.apache.commons.io.FileUtils;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

public class OpenAILabelService extends LabelService {
    public static final String MODEL_NAME = AiModel.DEFAULT_VISION_MODEL.getName();
    private static final String LABEL_URL = "https://api.openai.com/v1/chat/completions";

    private final String modelName;

    private String API_KEY = null;

    public OpenAILabelService(String modelName) {
        super(OpenAIService.NAME);
        if (modelName == null) {
            this.modelName = MODEL_NAME;
        } else {
            this.modelName = modelName;
        }
        API_KEY = SystemEnv.get("OPENAI_API_KEY", null);
    }

    @Override
    public ImageLabelResponse labelize(Connection con, AiImageLabelRequest request) throws SQLException, IOException {
        // read the image(s) for this request, and call the vision api per image
        AiImage image = DbImage.find(con, request.getImageId());

        // make sure we have a local copy of the image file!
        if (AiImageService.getLocalImageFile(image) == null) {
            AiImageService.downloadImage(image);
            DbImage.updateStatus(con, image, Status.DOWNLOADED);
        }

        List<AiImageLabel> labels = new ArrayList<>();
        labels.addAll(post(API_KEY, request, image));

        // persist the labels
        DbImageLabel.insert(con, request, labels);

        return ImageLabelResponse.create(request, labels);
    }

    @Override
    public String lookupThemeForWords(AiLabelConsolidateRequest request) throws IOException {
        // call the LLM and extract the result
        return post(API_KEY, request);
    }

    private List<AiImageLabel> post(String apiKey, AiImageLabelRequest request, AiImage image) throws IOException {
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;

        try {
            client = HttpClients.createDefault();

            HttpPost httpPost = createHttpPost(apiKey, request, image);
            response = client.execute(httpPost);

            return parseResponse(request, image, response);

        } finally {
            if (response != null) {
                response.close();
            }
            if (client != null) {
                client.close();
            }
        }
    }

    private String post(String apiKey, AiLabelConsolidateRequest request) throws IOException {
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;

        try {
            client = HttpClients.createDefault();

            HttpPost httpPost = createHttpPost(apiKey, request);
            response = client.execute(httpPost);

            return parseResponse(request, response);

        } finally {
            if (response != null) {
                response.close();
            }
            if (client != null) {
                client.close();
            }
        }
    }

    private HttpPost createHttpPost(String apiKey, AiImageLabelRequest request, AiImage image) throws IOException {
        System.out.println("posting to " + LABEL_URL + ": " + image);
        JsonObject payloadJson = createPayload(image, modelName, request);

        Gson gson = new Gson();
//        System.out.println("OpenAI Label Service: payload:\r\n" + gson.toJson(payloadJson));
        AiRequestLogger.logLabelRequest(AiRequestLogger.LABEL, request, payloadJson);

        StringEntity entity = new StringEntity(gson.toJson(payloadJson), ContentType.APPLICATION_JSON);

        HttpPost httpPost = new HttpPost(LABEL_URL);
        httpPost.setHeader("Authorization", "Bearer " + apiKey);
        httpPost.setHeader("User-Agent", "Noi");
        httpPost.setEntity(entity);
        return httpPost;
    }

    private HttpPost createHttpPost(String apiKey, AiLabelConsolidateRequest request) throws IOException {
        System.out.println("posting to " + LABEL_URL + ": " + request.getCompletedPrompt());
        String modelName = request.getPrompt().getModel().getName();
        JsonObject payloadJson = createPayload(null, modelName, false, request.getCompletedPrompt(), request.getPrompt().getSystemPrompt(), request.getPrompt().getPromptType(), null);

        Gson gson = new Gson();
        AiRequestLogger.logLabelRequest(AiRequestLogger.LABEL, request, payloadJson);

        StringEntity entity = new StringEntity(gson.toJson(payloadJson), ContentType.APPLICATION_JSON);

        HttpPost httpPost = new HttpPost(LABEL_URL);
        httpPost.setHeader("Authorization", "Bearer " + apiKey);
        httpPost.setHeader("User-Agent", "Noi");
        httpPost.setEntity(entity);
        return httpPost;
    }

    private List<AiImageLabel> parseResponse(AiImageLabelRequest request, AiImage image, CloseableHttpResponse response) throws IOException {
        String jsonResponse = FileTools.readToString(response.getEntity().getContent());
        System.out.println("response:" + jsonResponse);
        StatusLine statusLine = response.getStatusLine();
        System.out.println("response-code:" + statusLine.getStatusCode());

        AiRequestLogger.logLabelResponse(AiRequestLogger.LABEL, request, jsonResponse);

        if (statusLine.getStatusCode() == HttpServletResponse.SC_CREATED || statusLine.getStatusCode() == HttpServletResponse.SC_OK) {
            return parseLabels(request, image, jsonResponse);
        } else {
            ArrayList<AiImageLabel> labels = new ArrayList<>();
            labels.add(AiImageLabel.create(image, modelName, statusLine.getStatusCode(), statusLine.getReasonPhrase()));
            return labels;
        }
    }

    private String parseResponse(AiLabelConsolidateRequest request, CloseableHttpResponse response) throws IOException {
        String fileResponse = FileTools.readToString(response.getEntity().getContent());
        StatusLine statusLine = response.getStatusLine();

        // write to file log
        AiRequestLogger.logLabelResponse(AiRequestLogger.LABEL, request, fileResponse);

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

    private static JsonObject createPayload(AiImage image, String modelName, AiImageLabelRequest request) throws IOException {
        AiPrompt prompt = request.getPrompt();
        boolean highResolution = request.isHighResolution();

        String userPrompt = null;
        String systemPrompt = null;
        String responseFormat = null;
        if (prompt != null) {
            userPrompt = prompt.getPrompt();
            systemPrompt = prompt.getSystemPrompt();
            responseFormat = prompt.getResponseFormat();
        }

        return createPayload(image, modelName, highResolution, userPrompt, systemPrompt, prompt.getPromptType(), responseFormat);
    }

    private static JsonObject createPayload(AiImage image, String modelName, boolean highResolution, String userPrompt, String systemPrompt, int promptType, String responseFormat) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("model", modelName);
        //root.addProperty("max_tokens", 300);

        JsonArray messages = new JsonArray();
        root.add("messages", messages);

        if (systemPrompt != null) {
            JsonObject systemMessage = new JsonObject();
            messages.add(systemMessage);

            JsonTools.addProperty(systemMessage, "role", "system");
            JsonTools.addProperty(systemMessage, "content", systemPrompt);
        }

        JsonObject message = new JsonObject();
        messages.add(message);

        JsonTools.addProperty(message, "role", "user");

        JsonArray contentArray = new JsonArray();
        message.add("content", contentArray);

        JsonObject text = new JsonObject();
        contentArray.add(text);

        JsonTools.addProperty(text, "type", "text");
        JsonTools.addProperty(text, "text", userPrompt == null ? "What’s in this image?" : userPrompt);

        if (image != null) {
            JsonObject imageUrl = new JsonObject();
            contentArray.add(imageUrl);
            JsonTools.addProperty(imageUrl, "type", "image_url");
            // based on local / remote image source, create the payload as url, or as base64 encoded string
            JsonObject url = new JsonObject();
            if (image.isLocal()) {
            /*
            {"type": "image_url", "image_url": {
                "url": "data:image/png;base64,{base64_image_a}"}
            }
             */
                byte[] fileContent = FileUtils.readFileToByteArray(new File(image.getFilePath()));
                JsonTools.addProperty(url, "url", String.format("data:image/png;base64,%s", Base64.getEncoder().encodeToString(fileContent)));
            } else {
            /*
            {"type": "image_url", "image_url": {
                "url": "http://server.com/images/1.jpg"}
            }
             */
                JsonTools.addProperty(url, "url", image.getUrl());
            }
            imageUrl.add("image_url", url);

            if (highResolution) {
                // low, high, or auto (default)
                // https://platform.openai.com/docs/guides/vision/low-or-high-fidelity-image-understanding
                JsonTools.addProperty(url, "detail", "high");
            }
        }

        // add json response format (if the prompt type has one ... )
        if (responseFormat != null) {
            String schemaName = String.format("%s_response", AiPrompt.Type.parse(promptType).getName());
            addResponseFormat(root, responseFormat, schemaName);
        }

        return root;
    }

    private static void addResponseFormat(JsonObject root, String responseFormat, String name) {
        JsonObject formatNode = new JsonObject();
        root.add("response_format", formatNode);

        formatNode.addProperty("type", "json_schema");
        JsonObject schemaNode = new JsonObject();
        formatNode.add("json_schema", schemaNode);

        schemaNode.addProperty("name", name);
        schemaNode.addProperty("strict", true);

        JsonObject format = new JsonParser().parse(responseFormat).getAsJsonObject();
        schemaNode.add("schema", format);
    }

    private static List<AiImageLabel> parseLabels(AiImageLabelRequest request, AiImage image, String visionResponse) throws IOException {
        // parse for model, choices[{message.content}]
        List<AiImageLabel> labels = new ArrayList<>();
        JsonObject root = new JsonParser().parse(visionResponse).getAsJsonObject();
        if (root != null) {
            String modelName = JsonTools.getAsString(root, "model");
            JsonArray choicesArray = root.getAsJsonArray("choices");
            if (choicesArray != null && !choicesArray.isJsonNull()) {
                for (int c = 0; c < choicesArray.size(); c++) {
                    try {
                        JsonObject choice = choicesArray.get(c).getAsJsonObject();
                        JsonObject message = JsonTools.getAsObject(choice, "message");
                        if (message != null && !message.isJsonNull()) {
                            String content = JsonTools.getAsString(message, "content");

                            // see if there are any metadata (json) fragments in the content:
                            Map<String, List<LabelMetaData>> metaCategories = new HashMap<>();
                            if (content != null && content.contains("```json")) {
                                // parse out the json segment
                                int posStart = content.indexOf("```json");
                                int posEnd = content.indexOf("```", posStart + 7);
                                if (posEnd > posStart) {
                                    String json = content.substring(posStart + 7, posEnd);
                                    parseLabelsFromJson(request, metaCategories, json);

                                    // adjust the content (cut out the json, since we store that separately)
                                    StringBuilder cb = new StringBuilder();
                                    cb.append(content, 0, posStart);
                                    if (posEnd + 2 < content.length() - 1) {
                                        if (cb.length() > 0) {
                                            cb.append("; ");
                                        }
                                        cb.append(content.substring(posEnd + 3));
                                    }
                                    // re-assign to get the reduced content to populate the db with
                                    content = cb.toString().trim();
                                }
                                if (content.isEmpty()) {
                                    content = null;
                                }
                            } else if (content != null) {
                                // maybe the response is only a json doc (not wrapped in something else)?
                                parseLabelsFromJson(request, metaCategories, content.trim());

                                // if the full content was a json, we don't have to store that in db: we already have the raw file logged, and the parsed results in db
                                content = null;
                            }

                            labels.add(AiImageLabel.create(image, request.getUUID(), modelName, content, metaCategories));
                        }
                    } catch (IOException | JsonSyntaxException e) {
                        // catch here so we don't lose all messages!
                        System.out.println("ERROR for choice #" + c + ": " + e.getMessage());
                    }
                }
            }
        }
        return labels;
    }

    private static void parseLabelsFromJson(AiImageLabelRequest request, Map<String, List<LabelMetaData>> metaCategories, String json) throws IOException {
        JsonObject metaDataRoot = new JsonParser().parse(json).getAsJsonObject();
        Gson g = new Gson();
        String responseJsonSnippet = g.toJson(metaDataRoot);
        System.out.println("image label contains json meta: \n" + responseJsonSnippet);
        AiRequestLogger.logLabelResponseFragment(AiRequestLogger.LABEL, request, responseJsonSnippet);

        // categorize and structure the metadata; prepare it to be stored in normalized form
        metaCategories.putAll(AiImageLabel.parseMetaCategories(metaDataRoot, null, request.getPrompt().getPromptType()));
    }

    public static List<JsonObject> parseVisionResponseChoices(String visionResponse) {
        List<JsonObject> metaDataRoots = new ArrayList<>();

        JsonObject root = new JsonParser().parse(visionResponse).getAsJsonObject();
        if (root != null) {
//            String modelName = JsonTools.getAsString(root, "model");
            JsonArray choicesArray = root.getAsJsonArray("choices");
            if (choicesArray != null && !choicesArray.isJsonNull()) {
                for (int c = 0; c < choicesArray.size(); c++) {
                    JsonObject choice = choicesArray.get(c).getAsJsonObject();
                    JsonObject message = JsonTools.getAsObject(choice, "message");
                    if (message != null && !message.isJsonNull()) {
                        String content = JsonTools.getAsString(message, "content");

                        // see if there are any metadata (json) fragments in the content:
                        if (content != null && content.contains("```json")) {
                            // parse out the json segment
                            int posStart = content.indexOf("```json");
                            int posEnd = content.indexOf("```", posStart + 7);
                            if (posEnd > posStart) {
                                String json = content.substring(posStart + 7, posEnd);
                                JsonObject metaDataRoot = new JsonParser().parse(json).getAsJsonObject();
                                metaDataRoots.add(metaDataRoot);
                                Gson g = new Gson();
                                System.out.println("image label contains json meta: \n" + g.toJson(metaDataRoot));
                            }
                        }
                    }
                }
            }
        }

        return metaDataRoots;
    }

    public static void main(String[] args) throws IOException, SQLException {
//        String sampleResponse = "{  \"id\": \"chatcmpl-9HassgmEdVw6dXInTGHStZwcopUvh\",  \"object\": \"chat.completion\",  \"created\": 1713980386,  \"model\": \"gpt-4-1106-vision-preview\",  \"choices\": [    {      \"index\": 0,      \"message\": {        \"role\": \"assistant\",        \"content\": \"This image depicts an underwater scene with a rich and vibrant coral reef ecosystem. At the forefront, there is a sea turtle swimming among various species of colorful reef fish. In the background, several scuba divers can be seen exploring the area. The coral formations including sea fans and other types of coral provide a textured and diverse habitat for marine life. Sunlight is streaming down from the surface, creating a dappled lighting effect through the water.\\n\\n```json\\n{\\n  \\\"Basic Image Properties\\\": {\\n    \\\"size\\\": \\\"768x768\\\",\\n    \\\"aspect ratio\\\": \\\"1:1\\\"\\n  },\\n  \\\"Visual Composition\\\": {\\n    \\\"framing\\\": \\\"underwater centered\\\",\\n    \\\"balance\\\": \\\"symmetrical\\\",\\n    \\\"depth\\\": \\\"deep\\\",\\n    \\\"color scheme\\\": \\\"vivid blues and oranges\\\",\\n    \\\"texture\\\": \\\"detailed coral and fauna\\\",\\n    \\\"lines and shapes\\\": \\\"organic curves and forms\\\"\\n  },\\n  \\\"Content Elements\\\": {\\n    \\\"subject matter\\\": \\\"marine life, scuba divers\\\",\\n    \\\"brand logos\\\": \\\"none\\\",\\n    \\\"overlay text\\\": \\\"none\\\",\\n    \\\"call to action\\\": \\\"none\\\",\\n    \\\"product visibility\\\": \\\"none\\\"\\n  },\\n  \\\"Lighting and Quality\\\": {\\n    \\\"lighting type\\\": \\\"natural, dappled\\\",\\n    \\\"light direction\\\": \\\"from above\\\",\\n    \\\"brightness\\\": \\\"high luminosity underwater\\\",\\n    \\\"contrast\\\": \\\"high contrast between light and shadows\\\",\\n    \\\"sharpness\\\": \\\"sharp with fine details\\\",\\n    \\\"noise/grain\\\": \\\"none apparent\\\"\\n  },\\n  \\\"Mood and Atmosphere\\\": {\\n    \\\"mood\\\": \\\"serene, adventurous\\\",\\n    \\\"style\\\": \\\"naturalistic\\\",\\n    \\\"theme\\\": \\\"marine exploration\\\"\\n  },\\n  \\\"Audience and Context\\\": {\\n    \\\"target audience\\\": \\\"nature enthusiasts, divers\\\",\\n    \\\"cultural references\\\": \\\"none\\\",\\n    \\\"seasonality\\\": \\\"timeless\\\"\\n  },\\n  \\\"Technical Aspects\\\": {\\n    \\\"resolution\\\": \\\"high\\\",\\n    \\\"file format\\\": \\\"image presumed JPEG/PNG\\\",\\n    \\\"compression\\\": \\\"none evident\\\"\\n  },\\n  \\\"Interactivity Elements\\\": {\\n    \\\"interactive\\\": \\\"none\\\"\\n  }\\n}\\n```\"      },      \"logprobs\": null,      \"finish_reason\": \"stop\"    }  ],  \"usage\": {    \"prompt_tokens\": 934,    \"completion_tokens\": 449,    \"total_tokens\": 1383  },  \"system_fingerprint\": null}";
//
//        Long promptId = -1L;
//        String promptText = "a prompt";
//        String model = AiModel.DALL_E_3.toString();
//        AiPrompt prompt = AiPrompt.create(promptId, promptText);
//        AiImageRequest request = AiImageRequest.create(prompt, model);
//        String url = "http://localhost/image/1";
//        AiImage image = AiImage.create(request, url, null);
//        String systemRoleContent = null; // system instruction prompt
//
//        List<AiImageLabel> labels = parseLabels(image, sampleResponse);
//        for (AiImageLabel label : labels) {
//            System.out.println(label);
//        }

//        String visionResponse = "{\n" +
//                "          \"id\": \"chatcmpl-9BqGzsewgnyEUfBPAGrijgp0ZW0ps\",\n" +
//                "          \"object\": \"chat.completion\",\n" +
//                "          \"created\": 1712609573,\n" +
//                "          \"model\": \"gpt-4-1106-vision-preview\",\n" +
//                "          \"choices\": [\n" +
//                "            {\n" +
//                "              \"index\": 0,\n" +
//                "              \"message\": {\n" +
//                "                \"role\": \"assistant\",\n" +
//                "                \"content\": \"The image shows an elderly woman with white hair, smiling gently with her eyes closed. She is holding a bouquet of flowers, with a variety of colors and types, clutched in both hands close to her body. She is wearing a long-sleeved, loose-fitting white dress with embroidery on the chest. The setting appears to be outdoors, on a mountainous landscape with rolling hills in the background. The lighting is soft, suggesting it may be early morning or late afternoon, with a warm, tranquil ambiance.\"\n" +
//                "              },\n" +
//                "              \"logprobs\": null,\n" +
//                "              \"finish_reason\": \"stop\"\n" +
//                "            }\n" +
//                "          ],\n" +
//                "          \"usage\": {\n" +
//                "            \"prompt_tokens\": 778,\n" +
//                "            \"completion_tokens\": 105,\n" +
//                "            \"total_tokens\": 883\n" +
//                "          },\n" +
//                "          \"system_fingerprint\": null\n" +
//                "        }";

        // parse for choices,  (... and usage?)
        // --  ====  ====  ====  ====  ====  ====  ====  ====  ====  ====
        String folder = "/Users/martin/work/tmp/ai-data/%s/imageLabels/gpt-4-vision-preview/";

        // ---
//        String day = "20240501";
//        String folderURL = String.format(folder, day);

        // prompt id 6
//        File f = new File(folderURL, String.format("%s-res.json", "e9db7c61-08e9-48d7-b090-8425a97faf15"));
//        String jsonInput = FileTools.readInputStream(new FileInputStream(f));
//        List<JsonObject> roots = parseVisionResponseChoices(jsonInput);
//        for (JsonObject root : roots) {
//            Map<String, List<LabelMetaData>> categories = AiImageLabel.parseMetaCategories(root, null, AiImageLabel.PARSE_META_CATEGORIES);
//            System.out.println(categories);
//        }
//
        // prompt id 6
//        File f = new File(folderURL, String.format("%s-res.json", "076c4277-9166-42f3-8c13-5d4d4dc524cc"));
//        String jsonInput = FileTools.readInputStream(new FileInputStream(f));
//        List<JsonObject> roots = parseVisionResponseChoices(jsonInput);
//        for (JsonObject root : roots) {
//            Map<String, List<LabelMetaData>> categories = AiImageLabel.parseMetaCategories(root, null, AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES);
//            System.out.println(categories);
//        }
        Connection con = null;
        try {
            con = Model.connect();

            Long promptId = 1L;

            String day = "20240510";
//            String day = "20240508";
//            String day = "20240507";
//            String labelRequestUUID = "d0ed4962-bcb8-46ed-b879-2ff5fee82d65";
//            String labelRequestUUID = "390877e5-fb49-41e1-9a4e-26bc7cfaf863";
//            String labelRequestUUID = "b24e496b-25f7-4e47-96e7-5522462e7d66";
//            String labelRequestUUID = "47d29d3f-a6ec-48cd-917b-dfa93ba7e23e";
//            String labelRequestUUID = "fac154bb-5ad1-4d63-9ee7-a0777bf98216";
            String labelRequestUUID = "e042b0ea-e755-46f5-b5ce-b39cd16f5f51";

//            String day = "20240502";
//            String labelRequestUUID = "b2d5e997-e1d6-4443-82d8-de3bacc7961e";

            String folderURL = String.format(folder, day);
            File f = new File(folderURL, String.format("%s-res.json", labelRequestUUID));
            String jsonInput = FileTools.readInputStream(new FileInputStream(f));

            AiImageLabelRequest labelRequest = DbRequest.findForLabel(con, labelRequestUUID);
            AiImage image = DbImage.find(con, labelRequest.getImageId());

            List<AiImageLabel> labels = parseLabels(labelRequest, image, jsonInput);
            DbImageLabel.insert(con, labelRequest, labels);

            // -- =========================
            // prompt id 2
            labelRequestUUID = "96604fd2-a16e-4a5b-883d-88971d545372";
            f = new File(folderURL, String.format("%s-res.json", labelRequestUUID));

            jsonInput = FileTools.readInputStream(new FileInputStream(f));
            labelRequest = DbRequest.findForLabel(con, labelRequestUUID);
            image = DbImage.find(con, labelRequest.getImageId());

//            List<AiImageLabel> labels = parseLabels(labelRequest, image, jsonInput);
            DbImageLabel.insert(con, labelRequest, labels);

            //            List<JsonObject> roots = parseVisionResponseChoices(jsonInput);
//            for (JsonObject root : roots) {
//                Map<String, List<LabelMetaData>> categories = AiImageLabel.parseMetaCategories(root, null, AiPrompt.TYPE_IMAGE_LABEL_OBJECTS);
//                System.out.println(categories);
//            }


//        f = new File(folderURL, String.format("%s-res.json", uuid3));
//        jsonInput = FileTools.readInputStream(new FileInputStream(f));
//        roots = parseVisionResponseChoices(jsonInput);
//        for (JsonObject root : roots) {
//            Map<String, List<LabelMetaData>> categories = AiImageLabel.parseMetaCategories(root, null, AiImageLabel.PARSE_META_CATEGORIES);
//            System.out.println(categories);
//        }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            Model.close(con);
        }
    }
}
