package com.noi.video;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.noi.AiBrand;
import com.noi.AiModel;
import com.noi.Status;
import com.noi.image.AiImage;
import com.noi.language.AiPrompt;
import com.noi.llm.LLMService;
import com.noi.models.*;
import com.noi.tools.FileTools;
import com.noi.tools.JsonTools;
import com.noi.tools.SystemEnv;
import com.noi.video.audio.AudioExtractionRequest;
import com.noi.video.audio.AudioSummaryRequest;
import com.noi.video.scenes.SceneChangeRequest;
import com.noi.video.scenes.VideoSceneSummaryRequest;
import com.noi.video.scenes.VideoService;
import com.noi.web.BaseControllerServlet;
import com.noi.web.Path;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "VideoServlet", urlPatterns = {"/video/*", "/scene-changes/*", "/scene-summary/*", "/sounds/*", "/videos/*", "/video-story/*", "/uploadVideoFile", "/video-llms/*"}, loadOnStartup = 0)
// Note: location == $NOI_PATH env
@MultipartConfig(location = "/Users/martin/work/tmp/ai-data/videos/", fileSizeThreshold = 1024 * 1024 * 40, // limit to 40MB file size?!?
        maxFileSize = 1024 * 1024 * 40, maxRequestSize = 1024 * 1024 * 41)
public class VideoServlet extends BaseControllerServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // render a video
        Path path = Path.parse(req);
        String[] pathTokens = new String[]{};
        if (path.getPathInfo() != null && !path.getPathInfo().isEmpty()) {
            pathTokens = path.getPathInfo().split("/");
        }

        System.out.println("VideoServlet:GET: " + path);

        try {
            if ("video".equalsIgnoreCase(path.getServletPath())) {
                // render the video content
                // id provided?
                if (pathTokens.length <= 0) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }

                Long videoId = Long.parseLong(pathTokens[0].trim());

                // render the video if it's local?
                renderLocalVideo(videoId, resp);
                return;

            } else if ("videos".equalsIgnoreCase(path.getServletPath())) {
                // by default return the most recent 10 videos we created locally
                writeVideoList(req, resp, pathTokens);
                return;
            } else if ("video-story".equalsIgnoreCase(path.getServletPath())) {
                if (pathTokens.length <= 0) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }

                Long videoId = Long.parseLong(pathTokens[0].trim());
                writeVideoStoryData(videoId, resp);
                return;
            }
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND); // default to NOT_FOUND! (404)

        } catch (SQLException | NamingException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // curl -X POST http://localhost:8080/noi-server/video-llms/<videoId>
        // curl -X POST http://localhost:8080/noi-server/scene-changes/<videoId>
        // curl -X POST http://localhost:8080/noi-server/scene-summary/<videoId>
        // curl -X POST http://localhost:8080/noi-server/sounds/<videoId>

        Path path = Path.parse(req);
        String[] pathTokens = new String[]{};
        if (path.getPathInfo() != null && !path.getPathInfo().isEmpty()) {
            pathTokens = path.getPathInfo().split("/");
        }

        System.out.println("VideoServlet:POST: " + path);

        try {
            // accept a posted video file
            if ("scene-changes".equalsIgnoreCase(path.getServletPath())) {
                // verify the scene changes determined by the local ORB model
                handleSceneChanges(resp, pathTokens);
                return;

            } else if ("scene-summary".equalsIgnoreCase(path.getServletPath())) {
                // summarize the video scenes
                handleSceneSummary(resp, pathTokens);
                return;

            } else if ("sounds".equalsIgnoreCase(path.getServletPath())) {
                // transcribe the video and create a summary
                handleSound(resp, pathTokens);
                return;

            } else if ("video-llms".equalsIgnoreCase(path.getServletPath())) {
                // call all of the video processing steps involving LLMs
                handleVideoLLMs(resp, pathTokens);
                return;

            } else if ("uploadVideoFile".equalsIgnoreCase(path.getServletPath())) {
                handleVideoPost(req, resp);
                return;
            }

            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);

        } catch (SQLException | NamingException e) {
            throw new ServletException(e);
        }
    }

    private void handleVideoLLMs(HttpServletResponse resp, String[] pathTokens) throws IOException, SQLException, NamingException {
        // process all LLM steps for processing a video
        // ( ... after the ORB based py process created the scene change frame images, and extracted the mp3 sound file ...)
        Long videoId = null;
        if (pathTokens.length > 0) {
            videoId = Long.valueOf(pathTokens[0].trim());
        }
        System.out.println("VideoServlet:handleVideoLLMs for videoId=" + videoId);

        Connection con = null;
        try {
            con = Model.connectX();
            AiVideo video = DbVideo.find(con, videoId);

            // transcribe and summarize the resulting text
            JsonObject root = handleSoundLLMs(con, video);
            if (root == null) return;

            // process the ORB created scene changes, by calling LLM(s) to verify the scene change for each
            handleSceneChangeLLMs(con, video);

            // create a summary from the scenes in the scene changes of the video
            handleVideoSummaryLLMs(con, video);

            // respond with the full video metadata
            JsonObject jsonResponse = VideoService.readAndFormatVideoResponse(con, videoId);
            writeResponse(resp, jsonResponse);
        } finally {
            Model.close(con);
        }
    }

    private void handleSound(HttpServletResponse resp, String[] pathTokens) throws SQLException, NamingException, IOException {
        // transcribe the video and create a summary
        Long videoId = null;
        if (pathTokens.length > 0) {
            videoId = Long.valueOf(pathTokens[0].trim());
        }
        System.out.println("VideoServlet:handleSound for videoId=" + videoId);

        Connection con = null;
        try {
            con = Model.connectX();
            AiVideo video = DbVideo.find(con, videoId);
            JsonObject root = handleSoundLLMs(con, video);
            if (root == null) return;

            writeResponse(resp, root);

        } finally {
            Model.close(con);
        }
    }

    private static JsonObject handleSoundLLMs(Connection con, AiVideo video) throws SQLException, IOException {
        AiPrompt prompt = DbLanguage.findPrompt(con, AiPrompt.TYPE_AUDIO_TRANSCRIPT);
        if (prompt == null) {
            System.out.println("no model defined for video sound transcription!");
            // todo: error handling
            return null;
        }
        System.out.println("VideoServlet:handleSoundLLMs for videoId=" + video.getId());

        JsonObject root = new JsonObject();
        File audioPath = VideoService.getSoundURL(video);
        if (audioPath == null || !audioPath.exists()) {
            // todo: error handling
            return null;
        }

        AudioExtractionRequest request = AudioExtractionRequest.create(video, prompt, audioPath);

        LLMService service = LLMService.getService(request);
        System.out.println("VideoServlet:handleSoundLLMs transcribing with " + service.getName());

        // persist the request
        Long soundId = DbMedia.ensureSound(con, video.getId(), audioPath.getAbsolutePath());
        AiModel model = DbModel.ensure(con, request.getModelName());
        Long requestId = DbMedia.insertTranscriptRequest(con, soundId, request.getUUID(), model);

        JsonObject textNode = service.transcribeVideo(request);

        // update the request status based on the outcome
        Status finalRequestStatus = Status.COMPLETE;
        String text = "No transcript available!";
        if (textNode.has("transcription")) {
            text = textNode.get("transcription").getAsString();
            root.addProperty("text", text);
        } else {
            finalRequestStatus = Status.FAILED;
        }
        DbMedia.updateTranscriptRequest(con, requestId, finalRequestStatus);

        // persist the transcript
        Long transcriptId = DbMedia.insertTranscript(con, soundId, requestId, text);

        prompt = DbLanguage.findPrompt(con, AiPrompt.TYPE_AUDIO_SUMMARY);
        if (prompt != null) {
            AudioSummaryRequest summaryRequest = AudioSummaryRequest.create(video, prompt, text);
            requestId = DbMedia.insertTranscriptSummaryRequest(con, summaryRequest.getUUID(), transcriptId, prompt);

            service = LLMService.getService(request);
            JsonObject summaryJson = service.summarizeVideoSound(summaryRequest);
            Status finalSummaryStatus = Status.COMPLETE;
            if (!summaryJson.has("summary")) {
                finalSummaryStatus = Status.FAILED;
            } else {
                String audioSummary = summaryJson.get("summary").getAsString();
                root.addProperty("sound_summary", audioSummary);
                DbMedia.insertTranscriptSummary(con, soundId, requestId, audioSummary);
            }
            DbMedia.updateTranscriptSummaryRequest(con, requestId, finalSummaryStatus);
        }
        return root;
    }

    private void handleSceneSummary(HttpServletResponse resp, String[] pathTokens) throws IOException, SQLException, NamingException {
        // summarize the video scenes

        Long videoId = null;
        if (pathTokens.length > 0) {
            videoId = Long.valueOf(pathTokens[0].trim());
        }
        System.out.println("VideoServlet:handleSceneSummary for videoId=" + videoId);

        Connection con = null;
        try {
            con = Model.connectX();
            AiVideo video = DbVideo.find(con, videoId);
            handleVideoSummaryLLMs(con, video);
        } finally {
            Model.close(con);
        }

        // respond with the full video metadata
        //readAndFormatVideoResponse(videoId, resp);
        JsonObject root = VideoService.readAndFormatVideoResponse(con, videoId);
        writeResponse(resp, root);
    }

    private JsonObject handleVideoSummaryLLMs(Connection con, AiVideo video) throws SQLException, IOException, NamingException {
        AiPrompt prompt = DbLanguage.findPrompt(con, AiPrompt.TYPE_VIDEO_SUMMARY);
        if (prompt == null) {
            // todo: error handling
            return null;
        }

        List<AiVideo.SceneChange> localVideoScenes = DbImage.findVideoSceneChanges(con, video, false);
        List<AiVideo.SceneChange> llmVideoScenes = DbImage.findVideoSceneChanges(con, video, true);
        System.out.println("VideoServlet:handleVideoSummaryLLMs: localChanges[" + localVideoScenes.size() + "] llmChanges[" + llmVideoScenes.size() + "]");
        List<AiVideo.SceneChange> sceneChanges = VideoService.filterScenesForSummary(video, localVideoScenes, llmVideoScenes);
        System.out.println("VideoServlet:handleVideoSummaryLLMs: with " + sceneChanges.size() + " scene changes");

        // call the service with the list of scene changes
        VideoSceneSummaryRequest request = VideoSceneSummaryRequest.create(video, prompt, sceneChanges);
        Long requestId = DbMedia.insertVideoSummaryRequest(con, video.getId(), request.getUUID(), prompt);

        // loop through images / scenes used to create the summary and persist the relations
        for (String sceneImageURL : request.getSceneUrls()) {
            AiImage image = DbImage.findForVideo(con, video.getId(), sceneImageURL);
            DbMedia.insertVideoSummaryScene(con, video.getId(), requestId, image);
        }

        LLMService service = LLMService.getService(request);
        JsonObject summaryResponse = service.summarizeVideoScenes(request);

        Status finalSummaryStatus = Status.COMPLETE;
        if (summaryResponse.has("summary")) {
            DbMedia.insertVideoSummary(con, video.getId(), requestId, summaryResponse.get("summary").getAsString());
        } else {
            finalSummaryStatus = Status.FAILED;
        }
        DbMedia.updateVideoSummaryRequest(con, requestId, finalSummaryStatus);

        return summaryResponse;
    }

    private void handleSceneChanges(HttpServletResponse resp, String[] pathTokens) throws SQLException, NamingException, IOException {
        // pre-condition: the py process has looked at the video frames and the resulting raw scene changes
        //   were posted to /video/<id> with the 'scored_scene_changes' as payload json doc

        // here: verify the scene changes determined by the local ORB model, by calling an LLM model
        // for the video id
        // * read the ORB generated scene changes
        // * call the LLM(s) for each scene change, and ask if they agree and why
        // * persist the results (file plus db)
        Long videoId = null;
        if (pathTokens.length > 0) {
            videoId = Long.valueOf(pathTokens[0].trim());
        }
        System.out.println("VideoServlet:handleSceneChanges for videoId=" + videoId);

        Connection con = null;
        try {
            con = Model.connectX();
            AiVideo video = DbVideo.find(con, videoId);

            handleSceneChangeLLMs(con, video);

            // respond with the full video metadata
//            readAndFormatVideoResponse(videoId, resp);
            JsonObject root = VideoService.readAndFormatVideoResponse(con, videoId);
            writeResponse(resp, root);

        } finally {
            Model.close(con);
        }
    }

    private JsonObject handleSceneChangeLLMs(Connection con, AiVideo video) throws SQLException, IOException, NamingException {
        // retire old llm evaluations
        AiModel orbModel = DbModel.ensure(con, "ORB");
        DbSimilarity.retireScenesForOtherModels(con, video.getId(), orbModel);

        // load the local ORB based scenes
        List<AiVideo.SceneChange> localVideoScenes = DbImage.findVideoSceneChanges(con, video, false);
        System.out.println("VideoServlet:handleSceneChangeLLMs: videoId=" + video.getId() + " processing " + localVideoScenes.size() + " scene changes ...");
        if (localVideoScenes.size() > 0) {
            // get the prompt to use for the LLM check of the scene changes
            AiPrompt prompt = DbLanguage.findPrompt(con, AiPrompt.TYPE_SCENE_CHANGE);

            JsonObject response = new JsonObject();
            response.addProperty("same_scene_prompt_user", prompt.getPrompt());
            response.addProperty("same_scene_prompt_system", prompt.getSystemPrompt());
            response.addProperty("same_scene_model", prompt.getModel().getName());

            Map<SceneChangeRequest, String> acceptedScenes = new HashMap<>();
            Map<SceneChangeRequest, String> rejectedScenes = new HashMap<>();

            // for each scene change, ask the LLM if they agree and why
            int count = 0;
            for (AiVideo.SceneChange sceneChange : localVideoScenes) {
                SceneChangeRequest request = SceneChangeRequest.create(video, sceneChange, prompt);
                // then persist it all
                Long requestId = DbSimilarity.insertRequest(con, request);

                LLMService service = LLMService.getService(request);
                System.out.printf("%d/%d:using %s\r\n", ++count, localVideoScenes.size(), service.getName());

                try {
                    JsonObject sceneResponse = service.labelForSameVideoScene(sceneChange);

                    String llmExplanation = JsonTools.getAsString(sceneResponse, "explanation");
                    boolean isSameScene = JsonTools.getAsBoolean(sceneResponse, "same_scene");
                    if (isSameScene) {
                        // ORB said it was a scene change, but the llm did not agree: rejected!
                        rejectedScenes.put(request, llmExplanation);
                    } else {
                        acceptedScenes.put(request, llmExplanation);
                    }

                    // ensure the images in the scene change exist in the local db
                    int lastSceneFrame = sceneChange.getFirstFrame();
                    Long lastSceneImageId = DbImage.exists(con, video.getId(), lastSceneFrame);
                    int newSceneFrame = sceneChange.getLastFrame();
                    Long newSceneImageId = DbImage.exists(con, video.getId(), newSceneFrame);

                    double similarityScore = sceneChange.getScore();

                    Status finalLLMStatus = Status.COMPLETE;
                    if (newSceneImageId == null || lastSceneImageId == null) {
                        System.out.println("WARNING: unable to log similarity for " + sceneChange);
                        finalLLMStatus = Status.FAILED;
                    }
                    // update the request with the final results
                    DbSimilarity.updateRequest(con, requestId, similarityScore, !isSameScene, llmExplanation, finalLLMStatus);
                } catch (SQLException | NamingException | IOException e) {
                    e.printStackTrace();
                }
            }

            // finalize the json file content
            addLlmResults(response, "labeled_changes", acceptedScenes);
            addLlmResults(response, "labeled_rejections", rejectedScenes);

            // write meta file : -scenes-llm.json
            String rootFolder = SystemEnv.get("NOI_PATH", "/Users/martin/work/tmp/ai-data");
            File summaryFile = FileTools.joinPath(rootFolder, "videos", video.getName(), video.getName() + "-scenes-llm.json");
            FileTools.writeToFile(response, summaryFile);

            return response;
        }

        return new JsonObject();
    }

    private void addLlmResults(JsonObject root, String arrayName, Map<SceneChangeRequest, String> scenes) {
        JsonArray array = new JsonArray();
        root.add(arrayName, array);
        for (Map.Entry<SceneChangeRequest, String> entry : scenes.entrySet()) {
            JsonObject change = new JsonObject();
            array.add(change);
            SceneChangeRequest request = entry.getKey();
            AiVideo.SceneChange scene = request.getSceneChange();

            change.addProperty("uuid", request.getUUID());
            change.addProperty("frame", scene.getFirstFrame());
            change.addProperty("frame_before", scene.getLastFrame());

            change.addProperty("image_url", scene.getFirstImage().getUrl());
            change.addProperty("image_url_before", scene.getLastImage().getUrl());
            change.addProperty("explanation", entry.getValue());
            change.addProperty("similarity_score", scene.getScore());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Path path = Path.parse(req);
        String[] pathTokens = new String[]{};
        if (path.getPathInfo() != null && !path.getPathInfo().isEmpty()) {
            pathTokens = path.getPathInfo().split("/");
        }

        System.out.println("VideoServlet:DELETE: " + path);

        // accept a posted video file
        if ("video".equalsIgnoreCase(path.getServletPath())) {
            // get the id from the path , and read the posted json meta , then persist it all in the db
            handleDeleteVideo(req, resp, pathTokens);
            return;
        }

        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setHeader("Access-Control-Allow-Methods", "*");
        addCOARSHeaders(resp);
    }

    private void handleDeleteVideo(HttpServletRequest req, HttpServletResponse resp, String[] pathTokens) throws IOException {
        // delete the posted video
        Long videoId = null;
        if (pathTokens.length <= 0) {
            System.out.println("VideoServlet:DELETE: no video id provided!");
            return;
        }

        videoId = Long.valueOf(pathTokens[0].trim());

        Connection con = null;
        try {
            con = Model.connectX();
            AiVideo aiVideo = DbVideo.find(con, videoId);
            if (aiVideo != null) {
                DbVideo.update(con, aiVideo, Status.DELETED);
                resp.setStatus(HttpServletResponse.SC_ACCEPTED);
                addCOARSHeaders(resp);
                resp.setHeader("Access-Control-Allow-Methods", "*");
            } else {
                System.out.println("handleDeleteVideo:NOT found: video: " + aiVideo);
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                addCOARSHeaders(resp);
                resp.setHeader("Access-Control-Allow-Methods", "*");
            }
        } catch (SQLException | NamingException e) {
            JsonObject root = new JsonObject();
            root.addProperty("error", e.getMessage());
            resp.setHeader("Access-Control-Allow-Methods", "*");
            writeResponse(resp, root);
        } finally {
            Model.close(con);
        }
    }

    private void handleVideoPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SQLException, NamingException {
        // a video is being posted! (as form-data)
        Part filePart = req.getPart("file");
        String fileName = filePart.getSubmittedFileName();
        System.out.println("submitted fileName:" + fileName);
        String name = req.getParameter("name");
        System.out.println("submitted name:" + name);
        if (name == null || name.isEmpty()) {
            System.out.println("no fileName overwrite provided!");
            name = FileTools.getFileName(fileName, false);
        }
        filePart.write(fileName);

        String brandName = req.getParameter("brand");

        AiBrand aiBrand = null;

        Connection con = null;
        try {
            con = Model.connectX();
            if (brandName != null) {
                aiBrand = DbBrand.find(con, brandName);
                if (aiBrand == null) {
                    aiBrand = DbBrand.insert(con, brandName);
                }
            }

            // todo: how to read the configured url from the MultipartConfig(location ... tag?
            // export NOI_PATH=/Users/martin/work/tmp/ai-data/videos
            String noiPath = SystemEnv.get("NOI_PATH", "/Users/martin/work/tmp/ai-data");
            if (!noiPath.endsWith("/")) {
                noiPath = noiPath + "/";
            }
            String url = noiPath + "videos/" + fileName;
            AiVideo aiVideo = AiVideo.create(name, url, Status.NEW, aiBrand);
            Long id = DbVideo.insert(con, aiVideo);
            System.out.println(fileName + ": created new video with id=" + id);

            JsonObject root = new JsonObject();
            root.addProperty("id", id);
            root.addProperty("name", aiVideo.getName());
            root.addProperty("status_code", aiVideo.getStatus().getStatus());
            root.addProperty("status_name", aiVideo.getStatus().getName());
            writeResponse(resp, root);

        } catch (SQLException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            JsonObject root = new JsonObject();
            root.addProperty("error", e.getMessage());
            writeResponse(resp, root);
        } finally {
            Model.close(con);
        }
    }

    private void renderLocalVideo(Long videoId, HttpServletResponse resp) throws SQLException, NamingException, IOException {
        Connection con = null;
        try {
            con = Model.connectX();
            AiVideo video = DbVideo.find(con, videoId);
            if (video != null && video.isLocal()) {
                File localVideoFile = AiVideoService.getLocalVideoFile(video);
                if (localVideoFile.exists()) {
                    resp.setContentType("video/" + FileTools.getFileExtension(localVideoFile));
                    BufferedInputStream in = new BufferedInputStream(Files.newInputStream(localVideoFile.toPath()));
                    BufferedOutputStream bout = new BufferedOutputStream(resp.getOutputStream());
                    FileTools.copy(in, bout);
                    resp.setStatus(HttpServletResponse.SC_OK);
                }
            } else {
                // redirect to the remote url:
                resp.sendRedirect(video.getUrl());
            }
        } finally {
            Model.close(con);
        }
    }

    private List<AiVideo> listVideos(Connection con, HttpServletRequest req, String[] pathTokens) throws SQLException, NamingException {
        int limit = 25; // default

        // is there a requested limit of images to return?
        if (req.getParameter("limit") != null) {
            limit = Integer.parseInt(req.getParameter("limit").trim());
        }

        Long videoId = null;
        if (pathTokens.length > 0) {
            videoId = Long.parseLong(pathTokens[0].trim());
        }

        return VideoService.listVideos(con, videoId, limit);
    }

    private void writeVideoList(HttpServletRequest req, HttpServletResponse resp, String[] pathTokens) throws SQLException, NamingException, IOException {
        Connection con = null;
        try {
            con = Model.connectX();
            List<AiVideo> videos = listVideos(con, req, pathTokens);
            JsonObject root = VideoService.readAndFormatVideoResponse(con, videos);
            writeResponse(resp, root);
        } finally {
            Model.close(con);
        }
    }

    private void writeVideoStoryData(Long videoId, HttpServletResponse resp) throws IOException, SQLException, NamingException {
        Connection con = null;
        try {
            con = Model.connectX();
            JsonObject jsonResponse = VideoService.readAndFormatVideoStoryData(con, videoId);
            writeResponse(resp, jsonResponse);
        } finally {
            Model.close(con);
        }
    }

    public static void main(String[] a) {
        int videoSeconds = 8;
        Integer[] integers = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        List<Integer> sceneChanges = Lists.newArrayList(integers);
        System.out.printf("filter {%d} scene changes to max={%d} with threshold %.02f...", sceneChanges.size(), 10, 0.99d);
//        int maxSceneChanges = (videoSeconds / 2) > sceneChanges.size() ? sceneChanges.size() : (int) (videoSeconds / 2);
//        int minSceneChanges = Math.min(maxSceneChanges, 15);
//        System.out.println(minSceneChanges + ":" + maxSceneChanges);

        // filters
        int scoreFilterThreshold = 8;
        int maxEncountered = scoreFilterThreshold;
        int maxSceneChanges = 4;
        while (sceneChanges.size() > maxSceneChanges) {
            // try to filter by score (low similarity score), in case we have too many scenes (will run into token limits with model!)
            System.out.println("fallback: filter {len(scenes)} scene changes to max={maxSceneChanges}...");
            // only keep the ones with a similarity score less than the threshold!
//            sceneChanges = [scene for scene in scenes if (scene['similarity_score'] >= scoreFilterThreshold)]
            List<Integer> newList = new ArrayList<>();
            for (Integer scene : sceneChanges) {
                if (scene > maxEncountered) {
                    maxEncountered = scene;
                }
                if (scene < scoreFilterThreshold) {
                    newList.add(scene);
                }
            }
            sceneChanges = newList;
            scoreFilterThreshold = Math.min(scoreFilterThreshold - 1, maxEncountered - 1);
            maxEncountered = scoreFilterThreshold;
        }


        System.exit(1);
    }
}
