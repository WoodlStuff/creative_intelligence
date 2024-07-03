package com.noi.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noi.AiModel;
import com.noi.image.AiImage;
import com.noi.image.AiImageRequest;
import com.noi.image.AiImageResponse;
import com.noi.image.AiImageService;
import com.noi.image.label.AiImageLabel;
import com.noi.image.label.GoogleVisionLabelService;
import com.noi.image.label.LabelMetaData;
import com.noi.image.label.LabelService;
import com.noi.language.*;
import com.noi.models.*;
import com.noi.requests.ImageLabelResponse;
import com.noi.requests.NoiRequest;
import com.noi.requests.NoiResponse;
import com.noi.tools.FileTools;
import com.noi.tools.JsonTools;
import com.noi.video.AiVideo;
import com.noi.video.AiVideoService;
import com.noi.video.VideoFrameMoment;
import org.apache.http.entity.ContentType;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;


@WebServlet(name = "NoiServlet", urlPatterns = {"/api/*"}, loadOnStartup = 0)
public class NoiServlet extends BaseControllerServlet {

    private static JsonObject processImageDirectory(String folder, String modelName) throws SQLException, NamingException, IOException {
        // read folder and look for images;
        //   for each image:
        //      * make sure we have an entry in ai_images
        //      * label the image for all active prompts
        //      * write a json file with the generated labels into the same folder, using the image file name (but .json extension)
        //      * merge the label metadata for each image onto one response json and send that json as the response

        // todo: make the path and the prompts configurable with params!

        // todo: add one call to GoogleVision
        // labelServiceName=GoogleVisionLabels
        JsonObject root = new JsonObject();
        JsonArray imagesArray = new JsonArray();
        root.add("images", imagesArray);

        File dir = new File(folder);
        if (dir.isDirectory()) {
            Connection con = null;
            try {
                con = Model.connectX();

                // read the prompts once
                List<AiPrompt.Type> promptTypes = new ArrayList<>();
                promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES);
                promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_OBJECTS);
                promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_PROPERTIES);
                List<AiPrompt> dbPrompts = DbLanguage.findPrompts(con, promptTypes);
                AiPrompt[] prompts = new AiPrompt[dbPrompts.size()];
                dbPrompts.toArray(prompts);

                System.out.println("processing folder " + folder + " for " + prompts.length + " prompts ...");
                for (File f : dir.listFiles()) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".jpg")) {
                        System.out.println("\r\nprocessing image: " + f.getAbsolutePath());
                        AiImage image = DbImage.ensure(con, f.getAbsolutePath());

                        // GoogleVision has no concept of a prompt, so we only send one request there!
                        if (!GoogleVisionLabelService.MODEL_NAME.equalsIgnoreCase(modelName)) {
                            requestImageLabels(image.getId(), modelName, prompts);
                        } else {
                            requestImageLabels(image.getId(), modelName, null);
                        }

                        // read the combined labels and annotations for this image (for all completed requests)
                        List<AiImageLabel> annotations = DbImageLabel.findAnnotations(con, image);
                        Map<String, List<LabelMetaData>> metaValues = DbImageLabel.findLabelMetaCategories(con, image);

                        // add the image meta to the json doc (for the response)
                        imagesArray.add(LabelService.addImageLabels(image, annotations, metaValues, null));

                        // create a json file for each image in the same local image folder
                        JsonObject imgRoot = LabelService.addImageLabels(image, annotations, metaValues, null);
                        File parentFolder = f.getParentFile();
                        String jsonFileName = f.getName().replace(".jpg", ".json");
                        File imgJsonFile = new File(parentFolder, jsonFileName);
                        System.out.println("writing meta to " + imgJsonFile.getAbsolutePath());
                        FileTools.writeToFile(imgRoot, imgJsonFile);
                    }
                }

            } finally {
                Model.close(con);
            }
        }

        return root;
    }

//    private static AiPrompt[] findPrompts(Connection con, Long[] promptIds) throws SQLException {
//        AiPrompt[] prompts = new AiPrompt[promptIds.length];
//        for (int i = 0; i < promptIds.length; i++) {
//            prompts[i] = DbLanguage.findPrompt(con, promptIds[i]);
//        }
//        return prompts;
//    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // curl http://localhost:8080/noi-server/api/request/123

        // meta about the most recent n images...
        // curl http://localhost:8080/noi-server/api/image/<image-id>

        // render the video bytes
        // curl http://localhost:8080/noi-server/api/video/<video-id>

        // meta about the most recent n videos
        // curl http://localhost:8080/noi-server/api/video/<video-id>

        // curl http://localhost:8080/noi-server/api/video-story/<video-id>

        // meta about the most recent n prompts
        // curl http://localhost:8080/noi-server/api/prompts

        // metadata for one prompt
        // curl http://localhost:8080/noi-server/api/prompt/<id>

        Path path = Path.parse(req);
        if (path.getPathInfo() == null || path.getPathInfo().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        System.out.println("NoiServlet: " + path);

        String[] pathTokens = path.getPathInfo().split("/");
        if (pathTokens.length == 0) {
            return;
        }

        try {

            if ("image".equalsIgnoreCase(pathTokens[0])) {
                renderImage(resp, pathTokens);
                return;

            } else if ("images".equalsIgnoreCase(pathTokens[0])) {
                List<AiImage> images = listImages(req);
                // render image metadata
                writeImagesResponse(images, resp);
                return;

            } else if ("video".equalsIgnoreCase(pathTokens[0])) {
                // render the video content
                // id provided?
                if (pathTokens.length <= 1) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }

                Long videoId = Long.parseLong(pathTokens[1].trim());

                // render the video if it's local?
                renderLocalVideo(videoId, resp);
                return;

            } else if ("videos".equalsIgnoreCase(pathTokens[0])) {
                // by default return the most recent 10 videos we created locally
                writeVideoList(req, resp, pathTokens);
                return;

//            } else if ("label".equalsIgnoreCase(pathTokens[0])) {
//                // read the labels for the image id
//                if (pathTokens.length <= 1 && req.getParameter("image_url") == null) {
//                    throw new IllegalArgumentException("image id or image_url param is missing!");
//                }
//
//                writeImageLabels(req, resp, pathTokens);
//                return;
            } else if ("prompts".equalsIgnoreCase(pathTokens[0])) {
                writePromptList(req, resp);
                return;
            } else if ("prompt".equalsIgnoreCase(pathTokens[0])) {
                if (pathTokens.length <= 1) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }

                Long promptId = Long.parseLong(pathTokens[1].trim());
                writePrompt(promptId, req, resp);
                return;
            } else if ("video-story".equalsIgnoreCase(pathTokens[0])) {
                if (pathTokens.length <= 1) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }

                Long videoId = Long.parseLong(pathTokens[1].trim());
                writeVideoStoryData(videoId, resp);
                return;
            }
        } catch (SQLException | NamingException e) {
            throw new RuntimeException(e);
        }

        // DEFAULT TO DENY EVERYTHING
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

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

            JsonArray categoryNames = new JsonArray();
            story.add("category_names", categoryNames);
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
            // add unique category names as a separate array
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

    private void writePrompt(Long promptId, HttpServletRequest req, HttpServletResponse resp) throws SQLException, NamingException, IOException {
        // {'id':123, 'type': 1, 'type_name': '????', 'prompt': 'some text here..', 'system_prompt': 'more text ...', 'status': 'active'}
        JsonObject root = new JsonObject();
        Connection con = null;
        try {
            con = Model.connectX();

            AiPrompt prompt = DbLanguage.findPrompt(con, promptId);
            if (prompt != null) {
                addPromptToJson(root, prompt);
            }
            writeResponse(resp, root);
        } finally {
            Model.close(con);
        }
    }

    private static void addPromptToJson(JsonObject root, AiPrompt prompt) {
        root.addProperty("id", prompt.getId());
        root.addProperty("type", prompt.getPromptType());

        AiPrompt.Type type = AiPrompt.Type.parse(prompt.getPromptType());
        root.addProperty("type_name", type == null ? "n/a" : type.getName());

        root.addProperty("prompt", prompt.getPrompt());
        // root.addProperty("link_text", prompt.getPrompt());

        if (prompt.getSystemPrompt() != null) {
            root.addProperty("system_prompt", prompt.getSystemPrompt());
        }

        root.addProperty("status", prompt.getStatus().getName());
    }

    private void writePromptList(HttpServletRequest req, HttpServletResponse resp) throws SQLException, NamingException, IOException {
        // prompts: [{'type_name': xxx, 'id', 'link_text': 'xxxx', 'status': 'active'}]
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        root.add("prompts", array);

        Connection con = null;
        try {
            con = Model.connectX();
            List<AiPrompt> prompts = DbLanguage.findPrompts(con);
            for (AiPrompt prompt : prompts) {
                JsonObject p = new JsonObject();
                addPromptToJson(p, prompt);
                array.add(p);
            }
        } finally {
            Model.close(con);
        }

        writeResponse(resp, root);
    }

//    private void writeImageLabels(HttpServletRequest req, HttpServletResponse resp, String[] pathTokens) throws SQLException, NamingException, IOException {
//        // either the image and prompt id are provided in the path,
//        // or the prompt id and image url are provided as parameters
//        Long imgId = null;
//
//        if (pathTokens.length > 1) {
//            String imageId = pathTokens[1].trim();
//            if (imageId.isEmpty()) {
//                throw new IllegalArgumentException("image id is missing in the path: /label/<image-id>");
//            }
//            imgId = Long.valueOf(imageId);
//        } else {
//            String imageUrl = req.getParameter("image_url");
//            AiImage image = DbImage.find(imageUrl);
//            if (image != null) {
//                imgId = image.getId();
//            }
//        }
//
//        // read the image and the labels, and format a response (json)
//        writeLabelResponse(imgId, resp);
//    }

    private void writeVideoList(HttpServletRequest req, HttpServletResponse resp, String[] pathTokens) throws SQLException, NamingException, IOException {
        List<AiVideo> videos = listVideos(req, pathTokens);
        Map<AiVideo, List<AiVideo.SceneChange>> localVideoScenes = listVideoScenes(videos, false);
        Map<AiVideo, List<AiVideo.SceneChange>> llmVideoScenes = listVideoScenes(videos, true);
        Map<AiVideo, String> soundSummaries = listSoundSummaries(videos);
        Map<AiVideo, String> videoSummaries = listVideoSummaries(videos);

        writeVideosResponse(videos, localVideoScenes, llmVideoScenes, soundSummaries, videoSummaries, resp);
    }

    private void renderImage(HttpServletResponse resp, String[] pathTokens) throws SQLException, NamingException, IOException {
        // id provided?
        if (pathTokens.length <= 1) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Long imageId = Long.parseLong(pathTokens[1].trim());

        // render the image if it's local?
        renderLocalImage(imageId, resp);
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

    private void renderLocalImage(Long imageId, HttpServletResponse resp) throws SQLException, NamingException, IOException {
        Connection con = null;
        try {
            con = Model.connectX();
            AiImage image = DbImage.find(con, imageId);
            if (image != null && image.isLocal()) {
                File localImageFile = AiImageService.getLocalImageFile(image);
                if (localImageFile.exists()) {
                    resp.setContentType("image/" + FileTools.getFileExtension(localImageFile));
                    BufferedInputStream in = new BufferedInputStream(Files.newInputStream(localImageFile.toPath()));
                    BufferedOutputStream bout = new BufferedOutputStream(resp.getOutputStream());
                    FileTools.copy(in, bout);
                    resp.setStatus(HttpServletResponse.SC_OK);
                }
            } else {
                // redirect to the remove url:
                resp.sendRedirect(image.getUrl());
            }
        } finally {
            Model.close(con);
        }
    }

    private Map<AiVideo, List<AiVideo.SceneChange>> listVideoScenes(List<AiVideo> videos, boolean llmChanges) throws SQLException, NamingException {
        Map<AiVideo, List<AiVideo.SceneChange>> videoScenes = new HashMap<>();
        Connection con = null;
        try {
            con = Model.connectX();
            // todo: add handling of multiple request (only use the most recent here!)
            videoScenes.putAll(DbImage.findVideoScenes(con, videos, llmChanges));
        } finally {
            Model.close(con);
        }

        return videoScenes;
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
            if (pathTokens.length > 1) {
                videoId = Long.parseLong(pathTokens[1].trim());
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

    private List<AiImage> listImages(HttpServletRequest req) throws SQLException, NamingException {
        Connection con = null;
        try {
            int limit = 25; // default

            // is there a requested limit of images to return?
            if (req.getParameter("limit") != null) {
                limit = Integer.parseInt(req.getParameter("limit").trim());
            }

            // images for a specific video requested?
            Long videoId = null;
            if (req.getParameter("video_id") != null) {
                videoId = Long.parseLong(req.getParameter("video_id").trim());
            }

            // specific frame requested?
            Integer frameNumber = null;
            if (req.getParameter("frame") != null) {
                frameNumber = Integer.parseInt(req.getParameter("frame").trim());
            }

            con = Model.connectX();
            return DbImage.findMostRecent(con, limit, videoId, frameNumber);

        } finally {
            Model.close(con);
        }
    }

//    private void writeLabelResponse(Long imgId, HttpServletResponse resp) throws SQLException, NamingException, IOException {
//        // read the image and the labels, and format a response (json)
//        Connection con = null;
//        try {
//            con = Model.connectX();
//            AiImage image = DbImage.find(con, imgId);
//            List<AiImageLabel> annotations = DbImageLabel.findAnnotations(con, image);
//            Map<String, List<LabelMetaData>> metaValues = DbImageLabel.findLabelMetaCategories(con, image);
//
//            LabelService.writeLabelReport(image, annotations, metaValues, null, resp);
//        } finally {
//            Model.close(con);
//        }
//    }

    /**
     * render meta data for one image
     *
     * @param images
     * @param resp
     * @throws IOException
     */
    private static void writeImagesResponse(List<AiImage> images, HttpServletResponse resp) throws IOException {
        // data = [
        //    { url: './image1.png', video_id: 99, frame: 344, status: 'active' }, { url: './image2.png', path: '/videos', video_id: 99, frame: 144, status: 'active' }
        //  ];
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        root.add("images", array);
        for (AiImage image : images) {
            JsonObject i = new JsonObject();
            array.add(i);
            i.addProperty("id", image.getId());
            i.addProperty("url", image.getUrl());
            i.addProperty("file_path", image.getFilePath());
            i.addProperty("status", image.getStatus().getName());

            if (image.getVideoId() != null) {
                i.addProperty("video_id", image.getVideoId());
            }

            if (image.getVideoFrameNumber() != null) {
                i.addProperty("frame", image.getVideoFrameNumber());
            }

            i.addProperty("is_new_video_scene", image.isNewVideoScene());
            i.addProperty("is_video_scene_snap", image.isVideoSceneSnap());
        }

        writeResponse(resp, root);
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
            i.addProperty("key", video.getId());
            i.addProperty("url", video.getUrl());
            i.addProperty("name", FileTools.getFileName(video.getUrl(), false));
            // i.addProperty("file_path", video.getFilePath());
            i.addProperty("frame_rate", video.getFrameRate());
            i.addProperty("frame_count", video.getFrameCount());
            i.addProperty("seconds", video.getSeconds());
            i.addProperty("status", video.getStatus().getName());

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

    private void writeResponses(HttpServletResponse httpResponse, List<NoiResponse> noiResponses) throws IOException {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        root.add("responses", array);
        for (NoiResponse noiResponse : noiResponses) {
            JsonObject response = new JsonObject();
            formatJsonResponse(response, noiResponse);
            array.add(response);
        }
        writeResponse(httpResponse, root);
    }

    private void writeResponse(HttpServletResponse httpResponse, NoiResponse noiResponse) throws IOException {
        JsonObject root = new JsonObject();

        formatJsonResponse(root, noiResponse);

        writeResponse(httpResponse, root);
    }

    private static void formatJsonResponse(JsonObject root, NoiResponse noiResponse) {
        NoiRequest request = noiResponse.getRequest();
        root.addProperty("uuid", request.getUUID());
        root.addProperty("model_name", request.getModelName());

        if (noiResponse.isErrorResponse()) {
            root.addProperty("error_code", noiResponse.getErrorCode());
            root.addProperty("error_message", noiResponse.getErrorMessage());
        }

        if (request.getPrompt() != null) {
            root.addProperty("prompt_type", request.getPrompt().getPromptType());
            if (request.getPrompt().getId() != null) {
                root.addProperty("prompt_id", request.getPrompt().getId());
            }
        }

        if (noiResponse instanceof ImageLabelResponse) {
            ImageLabelResponse ilp = (ImageLabelResponse) noiResponse;
            root.addProperty("label_count", ilp.getImageLabels().size());
            List<AiImageLabel> annotations = new ArrayList();
            long categorySize = 0;
            for (AiImageLabel label : ilp.getImageLabels()) {
                categorySize += label.getLabelCategories().size();
                if (label.isAnnotation()) {
                    annotations.add(label);
                }
            }
            root.addProperty("label_annotation_count", annotations.size());
            root.addProperty("label_category_count", categorySize);
        }
    }

    private static void writeResponse(HttpServletResponse response, JsonObject root) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        Gson gson = new Gson();
        response.setContentType(ContentType.APPLICATION_JSON.toString());
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        Writer out = response.getWriter();
        out.write(gson.toJson(root));
        out.flush();
        out.close();
    }

    private void writeResponse(HttpServletResponse resp) {
        // return json like: {...}
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setStatus(HttpServletResponse.SC_OK);
    }

//    @Override
//    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//        resp.setStatus(HttpServletResponse.SC_OK);
//        resp.setHeader("Access-Control-Allow-Origin", "*");
//        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
//    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // full cycle:

        // curl -X POST http://localhost:8080/noi-server/api/image -H "Content-Type: application/json" -d '{"x":1}'
        /* example payload:
        -d '{"scene":"walking in the mountains","featuredProtagonist":"20 year old woman","protagonistDetails":"wearing a white crop top","secondProtagonist":"","secondProtagonistDetails":"","imageStyle":"film photo","modelName":"dall-e-3","modifierWords":"flowers, suitcases","numberImages":1,"imageMaterial":"","imageLighting":"","imagePerspective":"","imageQuality":"","progress":66,"requestId":"","mainPrompt":"20 year old woman, walking in the montains, wearing a white crop top, holding [noi-word]","prompts":["20 year old woman, walking in the mountains, wearing down jacket and baseball hat, holding a baby goat"]}'
        or:
        -d '{"scene":"walking in the mountains","featuredProtagonist":"20 year old woman","protagonistDetails":"wearing a white crop top","secondProtagonist":"","secondProtagonistDetails":"","imageStyle":"film photo","modelName":"dall-e-3","modifierWords":"flowers, suitcases","numberImages":1,"imageMaterial":"","imageLighting":"","imagePerspective":"","imageQuality":"","progress":66,"requestId":"","mainPrompt":"20 year old woman, walking in the montains, wearing a white crop top, holding [noi-word]",
           "prompts":[
            "20 year old woman, walking in the mountains, wearing a white crop top, holding flowers",
            "20 year old woman, walking in the mountains, wearing a white crop top, holding suitcases"
            ]
        }'


        // NLP only
        // curl -X POST http://localhost:8080/noi-server/api/nlp/<prompt-id>
        // curl -X POST http://localhost:8080/noi-server/api/nlp/12345
        // or: send prompt in post payload:
        // curl -X POST http://localhost:8080/noi-server/api/nlp -d "{"prompt": "some text goes here"}"
        */
        try {
            Path path = Path.parse(req);
            if (path.getPathInfo() == null || path.getPathInfo().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            System.out.println("NoiServlet: " + path);
            String[] pathTokens = path.getPathInfo().split("/");
            if (pathTokens.length == 0) {
                return;
            }

            if ("image".equalsIgnoreCase(pathTokens[0])) {
                // create new request for image generation
                handleImageGeneration(req, resp, path);
                return;
            } else if ("video".equalsIgnoreCase(pathTokens[0])) {
                // get the id from the path , and read the posted json meta , then persist it all in the db
                handlePostedVideoMeta(req, resp, pathTokens);
                return;
            } else if ("labels".equalsIgnoreCase(pathTokens[0])) {
                // label all images for a video
                if (pathTokens.length < 2) {
                    throw new IllegalArgumentException("video id is missing!");
                }

                handleVideoImagesLabelRequest(req, resp, pathTokens);
                return;

//            } else if ("label".equalsIgnoreCase(pathTokens[0])) {
//                // send request to label the images of a specific request (or prompt within a request ... )
//                if (pathTokens.length <= 1 && req.getParameter("image_url") == null) {
//                    throw new IllegalArgumentException("image id or image_url param is missing!");
//                }
//
//                handleSingleImageLabelRequest(req, resp, path, pathTokens);
//                return;

            } else if ("nlp".equalsIgnoreCase(pathTokens[0])) {
                // send request to analyze the prompt text
                if (pathTokens.length <= 1) {
                    throw new IllegalArgumentException("request uuid is missing!");
                }

                handlePromptAnalysis(req, resp, path);
                return;
            } else if ("folder".equalsIgnoreCase(pathTokens[0])) {
                // get folder url from param
                String folder = "/Users/martin/work/tmp/ai-data/videos/Lm645eKoUrQ";
                //Long[] promptIds = new Long[]{9L, 10L, 12L};

                String modelName = AiModel.DEFAULT_VISION_MODEL.getName();
                if (req.getParameter("modelName") != null) {
                    modelName = req.getParameter("modelName").trim();
                }

                JsonObject root = processImageDirectory(folder, modelName);

                resp.setContentType(ContentType.APPLICATION_JSON.toString());
                Writer out = resp.getWriter();
                Gson gson = new Gson();
                out.write(gson.toJson(root));
                out.flush();
                out.close();
                resp.setStatus(HttpServletResponse.SC_OK);
                return;
            }
        } catch (SQLException | NamingException e) {
            throw new ServletException(e);
        }

        // DEFAULT: DENY EVERYTHING
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    private void handlePromptAnalysis(HttpServletRequest req, HttpServletResponse resp, Path path) throws IOException, SQLException, NamingException {
        String modelName = handleModelName(req, AiModel.DALL_E_3.getName());
        AiPrompt prompt = handlePrompt(req, path, 1);
        NLPResponse response = requestPromptAnalysis(prompt, modelName);
        writeResponse(resp, response);
    }

    private void handleVideoImagesLabelRequest(HttpServletRequest req, HttpServletResponse resp, String[] pathTokens) throws SQLException, NamingException, IOException {
        Long videoId = Long.parseLong(pathTokens[1].trim());
        List<AiImage> images = new ArrayList<>();
        List<AiPrompt> dbPrompts = new ArrayList<>();

        // the model dictates what service we'll call
        String modelName = handleModelName(req, AiModel.DEFAULT_VISION_MODEL.getName());

        List<AiPrompt.Type> promptTypes = new ArrayList<>();
        promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES);
        promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_OBJECTS);
        promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_PROPERTIES);

        Connection con = null;
        try {
            con = Model.connectX();
            // get all active prompts
            dbPrompts.addAll(DbLanguage.findPrompts(con, promptTypes));
            images.addAll(DbImage.findVideoSummaryScenes(videoId));
        } finally {
            Model.close(con);
        }

        AiPrompt[] prompts = new AiPrompt[dbPrompts.size()];
        dbPrompts.toArray(prompts);

        System.out.println("NoiServlet: labels for video " + videoId + ": processing " + images.size() + " images with " + dbPrompts.size() + " prompts ...");
        List<NoiResponse> responses = new ArrayList<>();
        for (AiImage image : images) {
            try {
                System.out.println("NoiServlet: labels for video " + videoId + " image: " + image.getUrl() + " ...");
                responses.addAll(requestImageLabels(image.getId(), modelName, prompts));
                // call GoogleVision separately (no prompt here!)
                responses.addAll(requestImageLabels(image.getId(), GoogleVisionLabelService.MODEL_NAME, null));
            } catch (SQLException | NamingException e) {
                e.printStackTrace();
            }
        }

        writeResponses(resp, responses);
    }

//    private void handleSingleImageLabelRequest(HttpServletRequest req, HttpServletResponse resp, Path path, String[] pathTokens) throws IOException, SQLException, NamingException {
//        // either the image and prompt id are provided in the path,
//        // or the prompt id and image url are provided as parameters
//        Long imgId = null;
//        AiPrompt prompt = null;
//
//        if (pathTokens.length > 1) {
//            String imageId = pathTokens[1].trim();
//            if (imageId.isEmpty()) {
//                throw new IllegalArgumentException("image id is missing in the path: /label/<image-id>/<prompt-id>");
//            }
//            imgId = Long.valueOf(imageId);
//
//            if (pathTokens.length > 2) {
//                // todo: revisit! (case: no id in url, but type and prompt as posted json)
//                prompt = handlePrompt(req, path, 2);
//            }
//        } else {
//            String imageUrl = req.getParameter("image_url");
//            AiImage image = DbImage.findOrCreate(imageUrl);
//            if (image != null) {
//                imgId = image.getId();
//            }
//
//            String promptId = req.getParameter("prompt_id");
//            if (promptId != null) {
//                Long id = Long.valueOf(promptId.trim());
//                prompt = DbLanguage.findPrompt(id);
//            }
//        }
//
//        // the model dictates what service we'll call
//        String modelName = handleModelName(req, AiModel.DEFAULT_VISION_MODEL.getName());
//
//        AiPrompt[] prompts;
//        // one specific prompt requested?
//        if (prompt != null) {
//            prompts = new AiPrompt[]{prompt};
//        } else {
//            // otherwise: use all active label prompts
//            List<AiPrompt.Type> promptTypes = new ArrayList<>();
//            promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES);
//            promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_OBJECTS);
//            promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_PROPERTIES);
//            List<AiPrompt> dbPrompts = DbLanguage.findPrompts(promptTypes);
//            prompts = new AiPrompt[dbPrompts.size()];
//            dbPrompts.toArray(prompts);
//        }
//
//        List<NoiResponse> responses = new ArrayList<>();
//        responses.addAll(requestImageLabels(imgId, modelName, prompts));
//        responses.addAll(requestImageLabels(imgId, GoogleVisionLabelService.MODEL_NAME, null));
//
//        //writeResponses(resp, responses);
//        writeLabelResponse(imgId, resp);
//    }

    private void handlePostedVideoMeta(HttpServletRequest req, HttpServletResponse resp, String[] pathTokens) throws IOException, SQLException, NamingException {
        String jsonPayload = FileTools.readToString(req.getInputStream());
        JsonObject videoScenes = new JsonParser().parse(jsonPayload).getAsJsonObject();
        if (videoScenes == null || videoScenes.isJsonNull()) {
            throw new IllegalArgumentException("No valid json posted!");
        }

        // the video id is in the path
        Long videoId = null;
        if (pathTokens.length > 1) {
            videoId = Long.parseLong(pathTokens[1].trim());
        }
        if (videoId == null) {
            throw new IllegalArgumentException("no video id found in path");
        }

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

    private void handleImageGeneration(HttpServletRequest req, HttpServletResponse resp, Path path) throws IOException, SQLException, NamingException {
        String modelName = handleModelName(req, AiModel.DALL_E_3.getName());
        AiPrompt prompt = handlePrompt(req, path, 1);
        NoiResponse response = requestImage(prompt, modelName);
        writeResponse(resp, response);
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

    private static void handleScenePost(Long videoId, JsonObject videoLabels) throws SQLException, NamingException, IOException {
        System.out.println("\r\n--> handle posted scenes for videoId=" + videoId);
        Connection con = null;

        try {
            con = Model.connectX();

            // int videoLength = JsonTools.getAsInt(videoLabels, "video_length_seconds", 0);
            int frames = JsonTools.getAsInt(videoLabels, "total_frames", 0);
            double fps = JsonTools.getAsDouble(videoLabels, "frames_per_second");

            // update the ai_videos record for this video
            DbVideo.update(con, videoId, frames, fps);

            // what text prompt was used to generate the same scene LLM answers
            String sameScenePrompt = JsonTools.getAsString(videoLabels, "same_scene_prompt_user");
            String sameSceneSystemPrompt = JsonTools.getAsString(videoLabels, "same_scene_prompt_system");
            String sameSceneModel = JsonTools.getAsString(videoLabels, "same_scene_model");
            // lookup the model id for our local scoring alg.
            AiModel orbModel = DbModel.ensure(con, "ORB");
            AiModel sceneModel = DbModel.ensure(con, sameSceneModel);
            AiPrompt scenePrompt = DbLanguage.ensurePrompt(con, sameScenePrompt, sameSceneSystemPrompt, AiPrompt.TYPE_SCENE_CHANGE.getType());

            // read the raw scored scene changes and persist the frames as ai_images
            JsonArray scores = videoLabels.getAsJsonArray("scored_scene_changes");
            for (int i = 0; i < scores.size(); i++) {
                JsonObject score = scores.get(i).getAsJsonObject();
                int frame = JsonTools.getAsInt(score, "frame");
                String url = JsonTools.getAsString(score, "image_url");
                DbImage.createOrUpdate(con, videoId, frame, url, true, false);
                Long imageId = DbImage.exists(con, videoId, frame);

                int frameBefore = JsonTools.getAsInt(score, "frame_before");
                String urlBefore = JsonTools.getAsString(score, "image_url_before");
                Long imageIdBefore = DbImage.ensure(con, videoId, frameBefore, urlBefore).getId();

                double similarity = JsonTools.getAsDouble(score, "similarity_score");

                // persist the request and the score
                DbSimilarity.insertRequest(con, null, imageId, imageIdBefore, orbModel, null, similarity, null, true);
            }

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
                DbSimilarity.insertRequest(con, uuid, imageId, imageIdBefore, sceneModel, scenePrompt, similarity, explanation, true);
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
                DbSimilarity.insertRequest(con, uuid, imageId, imageIdBefore, sceneModel, scenePrompt, similarity, explanation, false);
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

        } finally {
            Model.close(con);
        }
    }

//    private String handleModelName(HttpServletRequest req, String defaultName) {
//        String modelName = defaultName; // set default model to generate images
//        if (req.getParameter("modelName") != null) {
//            modelName = req.getParameter("modelName");
//        }
//        return modelName;
//    }

//    private AiPrompt handlePrompt(HttpServletRequest req, Path path, int promptIdIndex) throws IOException, SQLException, NamingException {
//        AiPrompt prompt = AiPrompt.parse(req, path, promptIdIndex);
//        if (prompt.getId() == null) {
//            // we need to insert the prompt!
//            prompt = DbLanguage.insertPrompt(prompt);
//        } else {
//            // we need to look up the actual prompt text
//            prompt = DbLanguage.findPrompt(prompt.getId());
//        }
//
//        if (prompt == null) {
//            throw new IllegalStateException();
//        }
//
//        return prompt;
//    }

    private NLPResponse requestPromptAnalysis(AiPrompt prompt, String modelName) throws IOException, SQLException, NamingException {
        System.out.println("NOI: request prompt analysis...");
        NLPRequest request = NLPRequest.create(prompt, modelName);
        request = DbRequest.insertForPrompt(request);

        NLPService service = NLPService.getService();
        if (service != null) {
            AnnotationResponse annotationResponse = service.annotateText(request, true);
            System.out.println("NOI: done requesting prompt analysis!");
            NLPResponse nlpResponse = NLPResponse.create(request, annotationResponse);
            DbRequest.finishedForPrompt(request);

            return nlpResponse;
        }
        return NLPResponse.create(request, null);
    }

    private NoiResponse requestImage(AiPrompt prompt, String modelName) throws IOException, SQLException, NamingException {
        System.out.printf("Noi: request new image from model %s for %s%n", modelName, prompt);

        AiImageRequest request = AiImageRequest.create(prompt, modelName);
        request = DbRequest.insertForImage(request);

        AiImageResponse response = null;
        // parse the model name
        AiImageService imageService = AiImageService.getService(request);
        if (imageService != null) {
            // send the request to generate images ...
            System.out.println("requesting images using " + imageService.getModelName());
            response = imageService.generateImages(request);

            for (AiImage image : response.getImages()) {
                com.noi.models.DbImage.insert(request, image);
                AiImageService.downloadImage(image);
            }

            DbRequest.finishedForImage(request);
        }

        return response;
    }
}

// https://scontent.fbgi3-1.fna.fbcdn.net/v/t45.1600-4/407953252_120203198010660611_4827034948669956474_n.png?stp=cp0_dst-jpg_fr_q90_spS444
// https://scontent.fbgi3-1.fna.fbcdn.net/v/t45.1600-4/407953252_120203198010660611_4827034948669956474_n.png?stp=cp0_dst-jpg_fr_q90_spS444
// https://scontent.fbgi3-1.fna.fbcdn.net/v/t45.1600-4/407953252_120203198010660611_4827034948669956474_n.png?stp=cp0_dst-jpg_fr_q90_spS444&_nc_cat=100&ccb=1-7&_nc_sid=5f2048&_nc_ohc=bzeFfJzfm_0Q7kNvgFcIMKg&_nc_ht=scontent.fbgi3-1.fna&oh=00_AYDLxbzIFiQqvqhkMu2u3cMA7bIGqm2U5QlU66a9sl1FAg&oe=6644314E