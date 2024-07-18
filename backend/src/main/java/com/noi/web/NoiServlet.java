package com.noi.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noi.AiBrand;
import com.noi.AiModel;
import com.noi.Status;
import com.noi.image.AiImage;
import com.noi.image.AiImageRequest;
import com.noi.image.AiImageResponse;
import com.noi.image.AiImageService;
import com.noi.image.label.AiImageLabel;
import com.noi.image.label.LabelMetaData;
import com.noi.image.label.LabelService;
import com.noi.language.*;
import com.noi.models.*;
import com.noi.requests.ImageLabelResponse;
import com.noi.requests.NoiRequest;
import com.noi.requests.NoiResponse;
import com.noi.tools.FileTools;
import com.noi.tools.JsonTools;
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
            Map<AiModel, List<AiPrompt>> modelPrompts = new HashMap<>();

            Connection con = null;
            try {
                con = Model.connectX();

                // read the prompts once
                modelPrompts.putAll(LabelService.readPrompts(con));

                System.out.println("processing folder " + folder + " for " + modelPrompts.size() + " models ...");
                for (File f : dir.listFiles()) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".jpg")) {
                        System.out.println("\r\nprocessing image: " + f.getAbsolutePath());
                        AiImage image = DbImage.ensure(con, f.getAbsolutePath());

                        for (Map.Entry<AiModel, List<AiPrompt>> entry : modelPrompts.entrySet()) {
                            requestImageLabels(image.getId(), entry.getKey(), entry.getValue());
                        }

                        // GoogleVision has no concept of a prompt, so we only send one request there!
                        requestImageLabels(image.getId(), AiModel.GOOGLE_VISION, null);

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

        System.out.println("NoiServlet:GET: " + path);

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
            } else if ("prompt-lookup".equalsIgnoreCase(pathTokens[0])) {
                writePrompt(null, req, resp);
                return;
            } else if ("brands-lookup".equalsIgnoreCase(pathTokens[0])) {
                writeBrands(req, resp);
                return;
            }
        } catch (SQLException | NamingException e) {
            throw new RuntimeException(e);
        }

        // DEFAULT TO DENY EVERYTHING
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    private void writeBrands(HttpServletRequest req, HttpServletResponse resp) throws SQLException, NamingException, IOException {
        JsonObject root = new JsonObject();
        Connection con = null;
        try {
            con = Model.connectX();
            addBrands(con, root);
            writeResponse(resp, root);
        } finally {
            Model.close(con);
        }
    }

    private void addBrands(Connection con, JsonObject root) throws SQLException {
        // read all active brands and add to the json doc
        List<AiBrand> brands = DbBrand.findAll(con, Status.ACTIVE);
        JsonArray brandsArray = new JsonArray();
        JsonArray brandNamesArray = new JsonArray();
        root.add("brands", brandsArray);
        root.add("brand_names", brandNamesArray);
        for (AiBrand brand : brands) {
            brandsArray.add(brand.toJson());
            brandNamesArray.add(brand.getName());
        }
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
            } else {
                addDefaultPromptValuesToJson(con, root);
            }

            addCollections(con, root);
            writeResponse(resp, root);
        } finally {
            Model.close(con);
        }
    }

    private static void addDefaultPromptValuesToJson(Connection con, JsonObject root) throws SQLException {
        AiModel model = DbModel.find(con, AiModel.DEFAULT_VISION_MODEL.getName());
        if (model != null) {
            root.addProperty("model_id", model.getId());
        }
        root.addProperty("type", AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES.getType());
        root.addProperty("status_code", Status.NEW.getStatus());
        root.addProperty("system_prompt", "You are an advertising expert.");
        root.addProperty("prompt", " ... ");
    }

    /**
     * add all relevant lookup tables for: models, prompt types, and status
     *
     * @param con
     * @param root
     */
    private void addCollections(Connection con, JsonObject root) throws SQLException {
        List<AiModel> models = DbModel.findAll(con);
        ArrayList<AiModel> sorted = new ArrayList(models);
        Collections.sort(sorted);
        JsonArray m = new JsonArray();
        root.add("models", m);
        for (AiModel model : sorted) {
            JsonObject mod = new JsonObject();
            m.add(mod);
            mod.addProperty("id", model.getId());
            mod.addProperty("name", model.getName());
        }

        List<AiPrompt.Type> types = AiPrompt.getTypes();
        ArrayList<AiPrompt.Type> sortedTypes = new ArrayList(types);
        Collections.sort(sortedTypes);
        JsonArray t = new JsonArray();
        root.add("types", t);
        for (AiPrompt.Type type : sortedTypes) {
            JsonObject tp = new JsonObject();
            t.add(tp);
            tp.addProperty("code", type.getType());
            tp.addProperty("name", type.getName());
        }

        List<Status> statuses = Status.getAll();
        ArrayList<Status> sortedStatuses = new ArrayList(statuses);
        Collections.sort(sortedStatuses);
        JsonArray s = new JsonArray();
        root.add("statuses", s);
        for (Status status : sortedStatuses) {
            JsonObject st = new JsonObject();
            s.add(st);
            st.addProperty("code", status.getStatus());
            st.addProperty("name", status.getName());
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

        if (prompt.getName() != null) {
            root.addProperty("name", prompt.getName());
        }

        if (prompt.getModel() != null) {
            root.addProperty("model_id", prompt.getModel().getId());
            root.addProperty("model_name", prompt.getModel().getName());
        }

        root.addProperty("status", prompt.getStatus().getName());
        root.addProperty("status_code", prompt.getStatus().getStatus());
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
            List<AiImageLabel> annotations = new ArrayList<>();
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

        // video meta data: write to db what we get sent
        // post the score results (local scores)
        // curl -X POST http://localhost:8080/noi-server/api/video/<video-id> -d '{"video_length_seconds": 26, "total_frames": 652, "frames_per_second": 25.0, "score_threshold": 0.8, "max_distance_for_similarity": 60, "scored_scene_changes": [{"frame": 8, "frame_before": 0, ...}'
        // post the llm results
        // curl -X POST http://localhost:8080/noi-server/api/video/<video-id> -d '{"labeled_changes": [{"frame": 8, "frame_before": 0, ...}, {}], ...}'
        */
        try {
            Path path = Path.parse(req);
            if (path.getPathInfo() == null || path.getPathInfo().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            System.out.println("NoiServlet:POST: " + path);
            String[] pathTokens = path.getPathInfo().split("/");
            if (pathTokens.length == 0) {
                return;
            }

            if ("image".equalsIgnoreCase(pathTokens[0])) {
                // create new request for image generation
                handleImageGeneration(req, resp, path);
                return;
            } else if ("labels".equalsIgnoreCase(pathTokens[0])) {
                // label all images for a video
                if (pathTokens.length < 2) {
                    throw new IllegalArgumentException("video id is missing!");
                }

                handleVideoImagesLabelRequest(req, resp, pathTokens);
                return;

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
            } else if ("prompt".equalsIgnoreCase(pathTokens[0])) {
                // post / update a prompt
                handlePostedPrompt(req, resp, pathTokens);
            }
        } catch (SQLException | NamingException e) {
            throw new ServletException(e);
        }

        // DEFAULT: DENY EVERYTHING
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    private void handlePostedPrompt(HttpServletRequest req, HttpServletResponse resp, String[] pathTokens) throws IOException, SQLException, NamingException {
        String jsonPayload = FileTools.readToString(req.getInputStream());
        JsonObject promptData = new JsonParser().parse(jsonPayload).getAsJsonObject();
        if (promptData == null || promptData.isJsonNull()) {
            throw new IllegalArgumentException("No valid json posted!");
        }

        // the video id is in the path
        Long promptId = null;
        if (pathTokens.length > 1) {
            if (!"new".equalsIgnoreCase(pathTokens[1].trim())) {
                promptId = Long.parseLong(pathTokens[1].trim());
            }
        }

        // postData={id: promptId, name: name, model_id: modelId, prompt_type: promptType, status: status, prompt: prompt, system_prompt: systemPrompt};
        Long id = null;
        if (!"new".equalsIgnoreCase(JsonTools.getAsString(promptData, "id"))) {
            id = JsonTools.getAsLong(promptData, "id", null);
        }
        if ((id == null && promptId != null) ||
                (promptId == null && id != null)) {
            throw new IllegalArgumentException("id != posted id");
        }

        String name = JsonTools.getAsString(promptData, "name");
        if (name != null && name.isEmpty()) {
            name = null;
        }

        Long modelId = JsonTools.getAsLong(promptData, "model_id", null);
        int promptType = JsonTools.getAsInt(promptData, "prompt_type", -1);
        int status = JsonTools.getAsInt(promptData, "status", 0);
        String prompt = JsonTools.getAsString(promptData, "prompt", null);
        String systemPrompt = JsonTools.getAsString(promptData, "system_prompt");

        Connection con = null;
        try {
            con = Model.connectX();
            AiModel model = DbModel.find(con, modelId);
            AiPrompt aiPrompt = AiPrompt.create(id, name, model, prompt, promptType, systemPrompt, Status.parse(status));

            // no id? -> new record
            // else   -> update
            if (promptId == null) {
                // insert
                AiPrompt newPrompt = DbLanguage.insertPrompt(con, aiPrompt);
                promptId = newPrompt.getId();
            } else {
                // update
                DbLanguage.updatePrompt(con, aiPrompt);
            }
        } finally {
            Model.close(con);
        }

        // respond like a get
        writePrompt(promptId, req, resp);
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
        Map<AiModel, List<AiPrompt>> modelPrompts = new HashMap<>();

        // the model dictates what service we'll call
        Connection con = null;
        try {
            con = Model.connectX();
            images.addAll(DbImage.findVideoSummaryScenes(con, videoId));
            // split all prompts by their models
            // get all active prompts
            modelPrompts.putAll(LabelService.readPrompts(con));
        } finally {
            Model.close(con);
        }

        System.out.println("NoiServlet: labels for video " + videoId + ": processing " + images.size() + " images with " + modelPrompts.size() + " models ...");
        List<NoiResponse> responses = new ArrayList<>();
        for (AiImage image : images) {
            try {
                System.out.println("NoiServlet: labels for video " + videoId + " image: " + image.getUrl() + " ...");
                for (Map.Entry<AiModel, List<AiPrompt>> entry : modelPrompts.entrySet()) {
                    responses.addAll(requestImageLabels(image.getId(), entry.getKey(), entry.getValue()));
                }
                // call GoogleVision separately (no prompt here!)
                responses.addAll(requestImageLabels(image.getId(), AiModel.GOOGLE_VISION, null));
            } catch (SQLException | NamingException e) {
                e.printStackTrace();
            }
        }

        writeResponses(resp, responses);
    }


    private void handleImageGeneration(HttpServletRequest req, HttpServletResponse resp, Path path) throws IOException, SQLException, NamingException {
        String modelName = handleModelName(req, AiModel.DALL_E_3.getName());
        AiPrompt prompt = handlePrompt(req, path, 1);
        NoiResponse response = requestImage(prompt, modelName);
        writeResponse(resp, response);
    }

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