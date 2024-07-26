package com.noi.video;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noi.AiBrand;
import com.noi.AiModel;
import com.noi.Status;
import com.noi.image.AiImage;
import com.noi.language.AiPrompt;
import com.noi.language.MetaKeyImages;
import com.noi.language.MetaKeyValues;
import com.noi.models.*;
import com.noi.tools.FileTools;
import com.noi.tools.JsonTools;
import com.noi.tools.SystemEnv;
import com.noi.web.BaseControllerServlet;
import com.noi.web.Path;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

@WebServlet(name = "VideoServlet", urlPatterns = {"/video/*", "/videos/*", "/video-story/*", "/uploadVideoFile"}, loadOnStartup = 0)
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
        Path path = Path.parse(req);
        String[] pathTokens = new String[]{};
        if (path.getPathInfo() != null && !path.getPathInfo().isEmpty()) {
            pathTokens = path.getPathInfo().split("/");
        }

        System.out.println("VideoServlet:POST: " + path);

        try {
            // accept a posted video file
            if ("video".equalsIgnoreCase(path.getServletPath())) {
                // get the id from the path , and read the posted json meta , then persist it all in the db
                handlePostedVideoMeta(req, resp, pathTokens);
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
            // {"video_id": params.id, "video_name": videoData[0].name}
//            String jsonPayload = FileTools.readToString(req.getInputStream());
//            JsonObject videoData = new JsonParser().parse(jsonPayload).getAsJsonObject();
//            if (videoData == null || videoData.isJsonNull()) {
//                throw new IllegalArgumentException("No valid json posted!");
//            }
//            Long id = JsonTools.getAsLong(videoData, "video_id");
//            String name = JsonTools.getAsString(videoData, "video_name");

            con = Model.connectX();
            AiVideo aiVideo = DbVideo.find(con, videoId);
            if (aiVideo != null) {
                System.out.println("found the video: " + aiVideo);
                DbVideo.update(con, aiVideo, Status.DELETED);
                resp.setStatus(HttpServletResponse.SC_ACCEPTED);
                addCOARSHeaders(resp);
                resp.setHeader("Access-Control-Allow-Methods", "*");
            }
            else {
                System.out.println("NOT found: video: " + aiVideo);
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
        String name = req.getParameter("fileName");
        if (name != null && !name.isEmpty() && !"name".equalsIgnoreCase(name)) {
            System.out.println("overwrite fileName with posted name:" + name);
            fileName = name; // overwrite
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
            AiVideo aiVideo = AiVideo.create(fileName, url, Status.NEW, aiBrand);
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

    private List<AiVideo> listVideos(HttpServletRequest req, String[] pathTokens) throws SQLException, NamingException {
        Connection con = null;
        try {
            int limit = 25; // default

            // is there a requested limit of images to return?
            if (req.getParameter("limit") != null) {
                limit = Integer.parseInt(req.getParameter("limit").trim());
            }

            Long videoId = null;
            if (pathTokens.length > 0) {
                videoId = Long.parseLong(pathTokens[0].trim());
            }

            con = Model.connectX();
            if (videoId != null) {
                List<AiVideo> videos = new ArrayList<>();
                AiVideo video = DbVideo.find(con, videoId);
                if (video != null) {
                    videos.add(video);
                }
                return videos;
            }

            return DbVideo.findMostRecent(con, limit);

        } finally {
            Model.close(con);
        }
    }

    private Map<AiVideo, List<AiVideo.SceneChange>> listVideoScenes(List<AiVideo> videos, boolean llmChanges) throws SQLException, NamingException {
        Map<AiVideo, List<AiVideo.SceneChange>> videoScenes = new HashMap<>();
        Connection con = null;
        try {
            con = Model.connectX();
            videoScenes.putAll(DbImage.findVideoScenes(con, videos, llmChanges));
        } finally {
            Model.close(con);
        }

        return videoScenes;
    }

    private void writeVideoList(HttpServletRequest req, HttpServletResponse resp, String[] pathTokens) throws SQLException, NamingException, IOException {
        List<AiVideo> videos = listVideos(req, pathTokens);
        Map<AiVideo, List<AiVideo.SceneChange>> localVideoScenes = listVideoScenes(videos, false);
        Map<AiVideo, List<AiVideo.SceneChange>> llmVideoScenes = listVideoScenes(videos, true);
        Map<AiVideo, String> soundSummaries = listSoundSummaries(videos);
        Map<AiVideo, String> videoSummaries = listVideoSummaries(videos);

        writeVideosResponse(videos, localVideoScenes, llmVideoScenes, soundSummaries, videoSummaries, resp);
    }

    private Map<AiVideo, String> listSoundSummaries(List<AiVideo> videos) throws SQLException, NamingException {
        Map<AiVideo, String> summaries = new HashMap<>();
        Connection con = null;
        try {
            con = Model.connectX();
            for (AiVideo video : videos) {
                String summary = DbMedia.findMostRecentSoundSummary(con, video.getId());
                if (summary != null) {
                    summaries.put(video, summary);
                }
            }
        } finally {
            Model.close(con);
        }

        return summaries;
    }

    private Map<AiVideo, String> listVideoSummaries(List<AiVideo> videos) throws SQLException, NamingException {
        Map<AiVideo, String> summaries = new HashMap<>();
        Connection con = null;
        try {
            con = Model.connectX();
            for (AiVideo video : videos) {
                String summary = DbMedia.findMostRecentVideoSummary(con, video.getId());
                if (summary != null) {
                    summaries.put(video, summary);
                }
            }
        } finally {
            Model.close(con);
        }

        return summaries;
    }

    private void writeVideosResponse(List<AiVideo> videos, Map<AiVideo, List<AiVideo.SceneChange>> localVideoScenes, Map<AiVideo, List<AiVideo.SceneChange>> llmVideoScenes, Map<AiVideo, String> soundSummaries, Map<AiVideo, String> videoSummaries, HttpServletResponse resp) throws IOException {
        // {'videos': [
        //    { url: './v1.png', id: 1, name: 'video 1', 'file_pth': '/xxx', 'status': 'new'}, { url: './v2.png', id:2, name: 'video 2'}
        //  ]};
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        root.add("videos", array);
        for (AiVideo video : videos) {
            JsonObject i = new JsonObject();
            array.add(i);
            i.addProperty("id", video.getId());
            i.addProperty("name", video.getName());
            i.addProperty("key", video.getId());
            i.addProperty("url", video.getUrl());
            i.addProperty("name", FileTools.getFileName(video.getUrl(), false));
            // i.addProperty("file_path", video.getFilePath());
            i.addProperty("frame_rate", video.getFrameRate());
            i.addProperty("frame_count", video.getFrameCount());
            i.addProperty("seconds", video.getSeconds());
            i.addProperty("status", video.getStatus().getName());
            if (video.getBrand() != null) {
                i.addProperty("brand", video.getBrand().getName());
            }
            addVideoScenes("orb", localVideoScenes.get(video), i);
            addVideoScenes("llm", llmVideoScenes.get(video), i);

            String soundSummary = soundSummaries.get(video);
            if (soundSummary != null) {
                i.addProperty("sound_summary", soundSummary);
            }

            String videoSummary = videoSummaries.get(video);
            if (videoSummary != null) {
                i.addProperty("video_summary", videoSummary);
            }
        }

        writeResponse(resp, root);
    }

    private void addVideoScenes(String scope, List<AiVideo.SceneChange> changes, JsonObject videoObject) {
        // add the scene images for this video as json array
        JsonArray scenes = new JsonArray();
        videoObject.add(scope + "_scenes", scenes);
        for (AiVideo.SceneChange sceneChange : changes) {
            JsonObject scene = new JsonObject();
            scenes.add(scene);
            scene.addProperty("key", String.format("%d-%d", sceneChange.getLastFrame(), sceneChange.getFirstFrame()));
            scene.addProperty("last_scene_url", sceneChange.getLastImage().getUrl());
            scene.addProperty("last_scene_image_id", sceneChange.getLastImage().getId());
            scene.addProperty("last_scene_frame", sceneChange.getLastFrame());
            scene.addProperty("first_scene_url", sceneChange.getFirstImage().getUrl());
            scene.addProperty("first_scene_image_id", sceneChange.getFirstImage().getId());
            scene.addProperty("first_scene_frame", sceneChange.getFirstFrame());

            scene.addProperty("score", sceneChange.getScore());
            scene.addProperty("explanation", sceneChange.getExplanation());
            scene.addProperty("is_new_video_scene", sceneChange.isNewScene());
        }
    }

    private void handlePostedVideoMeta(HttpServletRequest req, HttpServletResponse resp, String[] pathTokens) throws IOException, SQLException, NamingException {
        String jsonPayload = FileTools.readToString(req.getInputStream());
        JsonObject videoScenes = new JsonParser().parse(jsonPayload).getAsJsonObject();
        if (videoScenes == null || videoScenes.isJsonNull()) {
            throw new IllegalArgumentException("No valid json posted!");
        }

        // the video id is in the path
        Long videoId = null;
        if (pathTokens.length > 0) {
            videoId = Long.parseLong(pathTokens[0].trim());
        }
        if (videoId == null) {
            throw new IllegalArgumentException("no video id found in path");
        }

        // retire older scene change requests (we don't want to see old pairs that are no longer relevant)
        DbSimilarity.retireScenes(videoId);
        handleScenePost(videoId, videoScenes);

        List<AiVideo> videos = new ArrayList<>();
        videos.add(DbVideo.find(videoId));
        Map<AiVideo, List<AiVideo.SceneChange>> localVideoScenes = listVideoScenes(videos, false);
        Map<AiVideo, List<AiVideo.SceneChange>> llmVideoScenes = listVideoScenes(videos, true);
        //Map<AiVideo, String> transcripts = listVideoTranscipts(videos);
        Map<AiVideo, String> soundSummaries = listSoundSummaries(videos);
        Map<AiVideo, String> videoSummaries = listVideoSummaries(videos);

        writeVideosResponse(videos, localVideoScenes, llmVideoScenes, soundSummaries, videoSummaries, resp);
    }

    private static void handleScenePost(Long videoId, JsonObject videoLabels) throws SQLException, NamingException, IOException {
        System.out.println("\r\n--> handle posted scenes for videoId=" + videoId);
        Connection con = null;

        try {
            con = Model.connectX();

            // if the post contains 'scored_scene_changes', we are getting local scoring results,
            // otherwise we are getting the llm results

            // lookup the model id for our local scoring alg.
            AiModel orbModel = DbModel.ensure(con, "ORB");

            double scoreThreshold = JsonTools.getAsDouble(videoLabels, "score_threshold");
            int maxSimilarityDistance = JsonTools.getAsInt(videoLabels, "max_distance_for_similarity");

            if (videoLabels.has("scored_scene_changes")) {
                // int videoLength = JsonTools.getAsInt(videoLabels, "video_length_seconds", 0);
                int frames = JsonTools.getAsInt(videoLabels, "total_frames", 0);
                double fps = JsonTools.getAsDouble(videoLabels, "frames_per_second");

                // update the ai_videos record for this video
                AiVideo video = DbVideo.update(con, videoId, frames, fps);

                // read the raw scored scene changes and persist the frames as ai_images
                JsonArray scores = videoLabels.getAsJsonArray("scored_scene_changes");
                for (int i = 0; i < scores.size(); i++) {
                    JsonObject score = scores.get(i).getAsJsonObject();
                    int frame = JsonTools.getAsInt(score, "frame");
                    String url = JsonTools.getAsString(score, "image_url");
                    DbImage.createOrUpdate(con, video, frame, url, true, false);
                    Long imageId = DbImage.exists(con, videoId, frame);

                    int frameBefore = JsonTools.getAsInt(score, "frame_before");
                    String urlBefore = JsonTools.getAsString(score, "image_url_before");
                    Long imageIdBefore = DbImage.ensure(con, video, frameBefore, urlBefore).getId();

                    double similarity = JsonTools.getAsDouble(score, "similarity_score");

                    // persist the request and the score
                    DbSimilarity.insertRequest(con, null, maxSimilarityDistance, scoreThreshold, videoId, imageId, imageIdBefore, orbModel, null, similarity, null, true);
                }
            } else {
                // handle posted LLM responses
                // what text prompt was used to generate the same scene LLM answers
                String sameScenePrompt = JsonTools.getAsString(videoLabels, "same_scene_prompt_user");
                String sameSceneSystemPrompt = JsonTools.getAsString(videoLabels, "same_scene_prompt_system");
                String sameSceneModel = JsonTools.getAsString(videoLabels, "same_scene_model");
                AiModel sceneModel = DbModel.ensure(con, sameSceneModel);
                AiPrompt scenePrompt = DbLanguage.ensurePrompt(con, sameScenePrompt, sameSceneSystemPrompt, AiPrompt.TYPE_SCENE_CHANGE.getType());

                // read the labeled_changes () scene changes the LLM agreed to) and persist the model results
                JsonArray lScores = videoLabels.getAsJsonArray("labeled_changes");
                for (int i = 0; i < lScores.size(); i++) {
                    JsonObject score = lScores.get(i).getAsJsonObject();
                    String uuid = JsonTools.getAsString(score, "uuid");
                    int frame = JsonTools.getAsInt(score, "frame");
                    Long imageId = DbImage.exists(con, videoId, frame);

                    int frameBefore = JsonTools.getAsInt(score, "frame_before");
                    Long imageIdBefore = DbImage.exists(con, videoId, frameBefore);

                    double similarity = JsonTools.getAsDouble(score, "similarity_score");
                    String explanation = JsonTools.getAsString(score, "explanation");
                    if (imageId == null || imageIdBefore == null) {
                        System.out.println("WARNING: unable to log similarity for " + new Gson().toJson(score));
                        continue;
                    }
                    DbSimilarity.insertRequest(con, uuid, maxSimilarityDistance, scoreThreshold, videoId, imageId, imageIdBefore, sceneModel, scenePrompt, similarity, explanation, true);
                }

                // read the rejected_changes (LLM does not agree to the scene change) and persist the model results
                JsonArray rScores = videoLabels.getAsJsonArray("labeled_rejections");
                for (int i = 0; i < rScores.size(); i++) {
                    JsonObject score = rScores.get(i).getAsJsonObject();
                    String uuid = JsonTools.getAsString(score, "uuid");
                    int frame = JsonTools.getAsInt(score, "frame");
                    Long imageId = DbImage.exists(con, videoId, frame);

                    int frameBefore = JsonTools.getAsInt(score, "frame_before");
                    Long imageIdBefore = DbImage.exists(con, videoId, frameBefore);

                    double similarity = JsonTools.getAsDouble(score, "similarity_score");
                    String explanation = JsonTools.getAsString(score, "explanation");
                    DbSimilarity.insertRequest(con, uuid, maxSimilarityDistance, scoreThreshold, videoId, imageId, imageIdBefore, sceneModel, scenePrompt, similarity, explanation, false);
                }

                // process the media meta files (sound and video summaries)
                Long transcriptId = null;
                Long soundId = null;

                File media = new File(JsonTools.getAsString(videoLabels, "audio_transcript"));
                if (media.exists()) {
                    String metaFileContent = FileTools.readToString(new FileInputStream(media));
                    JsonObject meta = new JsonParser().parse(metaFileContent).getAsJsonObject();
                    String soundURL = JsonTools.getAsString(meta, "sound_url");
                    String uuid = JsonTools.getAsString(meta, "uuid");
                    String modelName = JsonTools.getAsString(meta, "model_name");
                    String transcript = JsonTools.getAsString(meta, "transcription");

                    soundId = DbMedia.ensureSound(con, videoId, soundURL);
                    AiModel model = DbModel.ensure(con, modelName);
                    Long requestId = DbMedia.insertTranscriptRequest(con, videoId, soundId, uuid, model);
                    transcriptId = DbMedia.insertTranscript(con, soundId, requestId, transcript);
                }

                media = new File(JsonTools.getAsString(videoLabels, "audio_summary"));
                if (media.exists() && transcriptId != null && soundId != null) {
                    String metaFileContent = FileTools.readToString(new FileInputStream(media));
                    JsonObject meta = new JsonParser().parse(metaFileContent).getAsJsonObject();
                    String uuid = JsonTools.getAsString(meta, "uuid");
                    String modelName = JsonTools.getAsString(meta, "model_name");
                    String systemPrompt = JsonTools.getAsString(meta, "system_prompt");
                    String userPrompt = JsonTools.getAsString(meta, "user_prompt");
                    String audioSummary = JsonTools.getAsString(meta, "summary");

                    AiPrompt prompt = DbLanguage.ensurePrompt(con, userPrompt, systemPrompt, AiPrompt.TYPE_AUDIO_SUMMARY.getType());
                    AiModel model = DbModel.ensure(con, modelName);
                    Long requestId = DbMedia.insertTranscriptSummaryRequest(con, uuid, transcriptId, prompt, model);
                    DbMedia.insertTranscriptSummary(con, soundId, requestId, audioSummary);
                }

                media = new File(JsonTools.getAsString(videoLabels, "video_summary"));
                if (media.exists()) {
                    String metaFileContent = FileTools.readToString(new FileInputStream(media));
                    JsonObject meta = new JsonParser().parse(metaFileContent).getAsJsonObject();
                    String uuid = JsonTools.getAsString(meta, "uuid");
                    String modelName = JsonTools.getAsString(meta, "model_name");
                    String systemPrompt = JsonTools.getAsString(meta, "system_prompt");
                    String userPrompt = JsonTools.getAsString(meta, "user_prompt");
                    String videoSummary = JsonTools.getAsString(meta, "summary");
                    JsonArray scenes = meta.getAsJsonArray("scenes");

                    AiPrompt prompt = DbLanguage.ensurePrompt(con, userPrompt, systemPrompt, AiPrompt.TYPE_VIDEO_SUMMARY.getType());
                    AiModel model = DbModel.ensure(con, modelName);
                    Long requestId = DbMedia.insertVideoSummaryRequest(con, uuid, videoId, prompt, model);

                    // loop through images / scenes used to create the summary and persist the relations
                    for (int i = 0; i < scenes.size(); i++) {
                        String sceneImageURL = scenes.get(i).getAsString();
                        AiImage image = DbImage.findForVideo(con, videoId, sceneImageURL);
                        DbMedia.insertVideoSummaryScene(con, videoId, image, requestId);
                    }

                    DbMedia.insertVideoSummary(con, requestId, videoId, videoSummary);
                }
            }
        } finally {
            Model.close(con);
        }
    }

    //    private void renderLocalVideo(Long videoId, HttpServletResponse resp) throws SQLException, NamingException, IOException {
//        Connection con = null;
//        try {
//            con = Model.connectX();
//            AiVideo video = DbVideo.find(con, videoId);
//            if (video != null && video.isLocal()) {
//                File localVideoFile = AiVideoService.getLocalVideoFile(video);
//                if (localVideoFile.exists()) {
//                    resp.setContentType("video/" + FileTools.getFileExtension(localVideoFile));
//                    BufferedInputStream in = new BufferedInputStream(Files.newInputStream(localVideoFile.toPath()));
//                    BufferedOutputStream bout = new BufferedOutputStream(resp.getOutputStream());
//                    FileTools.copy(in, bout);
//                    resp.setStatus(HttpServletResponse.SC_OK);
//                }
//            } else {
//                // redirect to the remote url:
//                resp.sendRedirect(video.getUrl());
//            }
//        } finally {
//            Model.close(con);
//        }
//    }

    private void writeVideoStoryData(Long videoId, HttpServletResponse resp) throws IOException, SQLException, NamingException {
        JsonObject root = new JsonObject();
        JsonObject story = new JsonObject();
        root.add("story", story);

        Connection con = null;

        try {
            con = Model.connectX();

            List<VideoFrameMoment> moments = DbVideo.findSummaryFrames(con, videoId);
            JsonArray momentsArray = new JsonArray();
            story.add("moments", momentsArray);
            for (VideoFrameMoment moment : moments) {
                JsonObject mom = new JsonObject();
                mom.addProperty("image_id", moment.getImageId());
                mom.addProperty("image_url", moment.getImageURL());
                mom.addProperty("video_frame_number", moment.getFrameNumber());
                mom.addProperty("frame_rate", moment.getVideoFrameRate());
                mom.addProperty("seconds", moment.getSecondsFromStart());

                momentsArray.add(mom);
            }

            Set<String> catNames = new TreeSet<>();
            JsonArray categories = new JsonArray();
            story.add("categories", categories);

            // Map<image_id> => Map<Category-Name> => KV-Pair[k,v]
            Map<Long, Map<String, List<MetaKeyValues>>> metaData = DbImageLabel.findImageCategoryKeyGroups(con, videoId);
            for (Map.Entry<Long, Map<String, List<MetaKeyValues>>> entry : metaData.entrySet()) {
                // one array per image
                JsonObject image = new JsonObject();
                categories.add(image);
                image.addProperty("image_id", entry.getKey());

                JsonArray storyElements = new JsonArray();
                image.add("story_elements", storyElements);

                for (Map.Entry<String, List<MetaKeyValues>> metaEntry : entry.getValue().entrySet()) {
                    String categoryName = metaEntry.getKey();
                    if (!catNames.contains(categoryName)) {
                        catNames.add(categoryName);
                    }

                    for (MetaKeyValues kv : metaEntry.getValue()) {
                        JsonObject storyElement = new JsonObject();
                        storyElements.add(storyElement);
                        storyElement.addProperty("category_name", categoryName);
                        storyElement.addProperty("key", kv.getKey());
                        storyElement.addProperty("values", kv.getValues());
                    }
                }
            }

            // add a map of image counts by category
            // select category_name, meta_key, meta_value, count(distinct ai_image_id) image_count, group_concat(distinct ai_image_id) image_ids from (select ai_image_id, name category_name, meta_key, meta_value, count(*) _count from (select distinct lmc.ai_image_id, i.video_frame_number, c.name, lmc.meta_key, lmc.meta_value from ai_image_label_meta_categories lmc join meta_categories c on c.id = lmc.meta_category_id join ai_images i on i.id = lmc.ai_image_id where i.ai_video_id=@video_id and name=@category)a group by 1,2,3,4)x group by 1,2,3 having count(distinct ai_image_id) > 1 order by count(distinct ai_image_id), category_name, meta_key, meta_value;
            JsonArray catImages = new JsonArray();
            story.add("category_images", catImages);
            Map<String, List<MetaKeyImages>> categoryImages = DbImageLabel.findCategoryImages(con, videoId);
            for (Map.Entry<String, List<MetaKeyImages>> entry : categoryImages.entrySet()) {
                for (MetaKeyImages mki : entry.getValue()) {
                    JsonObject categoryImageCounts = new JsonObject();
                    catImages.add(categoryImageCounts);
                    categoryImageCounts.addProperty("category_name", entry.getKey());
                    categoryImageCounts.addProperty("key", mki.getMetaKey());
                    categoryImageCounts.addProperty("value", mki.getMetaValue());
                    categoryImageCounts.add("image_ids", mki.getImageIds());
                }
            }

            // add unique category names as a separate array
            JsonArray categoryNames = new JsonArray();
            story.add("category_names", categoryNames);
            List<String> sorted = new ArrayList<>(catNames);
            System.out.println("sorted has a size of: " + sorted.size());
            Collections.sort(sorted);
            for (String catName : sorted) {
                categoryNames.add(catName);
            }
        } finally {
            Model.close(con);
        }

        writeResponse(resp, root);
    }
}
