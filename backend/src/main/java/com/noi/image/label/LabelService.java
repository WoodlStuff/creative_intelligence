package com.noi.image.label;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.noi.AiModel;
import com.noi.image.AiImage;
import com.noi.language.AiImageLabelRequest;
import com.noi.language.AiPrompt;
import com.noi.models.DbLanguage;
import com.noi.models.Model;
import com.noi.requests.ImageLabelResponse;
import org.apache.http.entity.ContentType;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

public abstract class LabelService {
    public static final String OPEN_AI = "OpenAI";
    public static final String GOOGLE_VISION = "GoogleVision";
    private final String serviceName;

    protected LabelService(String serviceName) {
        this.serviceName = serviceName;
    }

    /*
    OpenAI: https://api.openai.com/v1/chat/completions (model: gpt-4-vision-preview)
    Google Vision: https://cloud.google.com/vision
     */
    public static LabelService getService(AiImageLabelRequest request) {
        String serviceName = getServiceName(request.getModelName());

        if (serviceName == null || OPEN_AI.equalsIgnoreCase(serviceName)) {
            return new OpenAILabelService(request.getModelName());

        } else if (GOOGLE_VISION.equalsIgnoreCase(serviceName)) {
            return new GoogleVisionLabelService();
        }

        return null;
    }

    private static String getServiceName(String modelName) {
        if (GoogleVisionLabelService.MODEL_NAME.equalsIgnoreCase(modelName)) {
            return LabelService.GOOGLE_VISION;
        }
        // default!
        return LabelService.OPEN_AI;
    }

    public static String getModelName(String serviceName, String modelName) {
        // if Google vision is the service we call to label the image, then the used model is defined by that!
        if (LabelService.GOOGLE_VISION.equalsIgnoreCase(serviceName)) {
            return GoogleVisionLabelService.MODEL_NAME;
        }

        // default
        if (modelName == null) {
            return OpenAILabelService.MODEL_NAME;
        }

        return modelName;
    }

    public static void writeLabelReport(AiImage image, List<AiImageLabel> annotations, Map<String, List<LabelMetaData>> metaValues, String categoryName, Long promptId, boolean hasEmbedding, HttpServletResponse response) throws IOException, SQLException, NamingException {
        // create the json doc
        JsonObject root = addImageLabels(image, annotations, metaValues, categoryName, promptId);
        root.addProperty("has_embedding", hasEmbedding);
        addPromptsForLookup(root);

        response.setContentType(ContentType.APPLICATION_JSON.toString());
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        Writer out = response.getWriter();
        Gson gson = new Gson();
        out.write(gson.toJson(root));
        out.flush();
        out.close();
    }

    private static void addPromptsForLookup(JsonObject root) throws SQLException, NamingException {
        Map<AiModel, List<AiPrompt>> labelPrompts = LabelService.readPrompts();
        JsonArray prompts = new JsonArray();
        root.add("prompts", prompts);
        for (Map.Entry<AiModel, List<AiPrompt>> entry : labelPrompts.entrySet()) {
            for (AiPrompt aiPrompt : entry.getValue()) {
                JsonObject prompt = new JsonObject();
                prompts.add(prompt);
                AiModel model = entry.getKey();
                prompt.addProperty("model_id", model.getId());
                prompt.addProperty("model_name", model.getName());

                prompt.addProperty("prompt_id", aiPrompt.getId());
                String name = "" + aiPrompt.getId(); // default, in case we don't have a name)
                if (aiPrompt.getName() != null && !aiPrompt.getName().isEmpty()) {
                    name = aiPrompt.getName();
                }
                prompt.addProperty("prompt_name", name);
            }
        }
    }

    public static JsonObject addImageLabels(AiImage image, List<AiImageLabel> annotations, Map<String, List<LabelMetaData>> metaValues, String categoryName, Long promptId) {
        JsonObject i = new JsonObject();
        i.addProperty("path", image.getFilePath());
        i.addProperty("video_frame_number", image.getVideoFrameNumber());
        i.addProperty("video_id", image.getVideoId());
        if (image.getBrand() != null) {
            i.addProperty("brand", image.getBrand().getName());
        }
        // annotations
        JsonArray a = new JsonArray();
        i.add("annotations", a);
        for (AiImageLabel label : annotations) {
            JsonObject l = new JsonObject();
            l.addProperty("request_uuid", label.getRequestUUID());
            l.addProperty("model_name", label.getModelName());
            l.addProperty("description", label.getContent());
            l.addProperty("mid", label.getMid());
            l.addProperty("score", label.getScore());
            l.addProperty("topicality", label.getTopicality());
            a.add(l);
        }

        // collect distinct category names (for filter functions in the UI ... )
        Set<String> catNames = new TreeSet<>();

        // categories
        JsonArray categories = new JsonArray();
        i.add("categories", categories);
        for (Map.Entry<String, List<LabelMetaData>> cat : metaValues.entrySet()) {
            // if a filter is provided: only the matching category will be added to the response
            String catName = cat.getKey();
            if (categoryName == null || categoryName.equalsIgnoreCase(catName)) {
                if (!catNames.contains(catName)) {
                    catNames.add(catName);
                }
                for (LabelMetaData meta : cat.getValue()) {
                    if(promptId == null || promptId.equals(meta.getPromptId())) {
                        JsonObject label = new JsonObject();
                        categories.add(label);
                        label.addProperty("category_name", catName);
                        label.addProperty("request_uuid", meta.getRequestUUID());
                        label.addProperty("model_name", meta.getModelName());
                        label.addProperty("key", meta.getKey());
                        label.addProperty("value", meta.getValue());
                    }
                }
            }
        }

        // now add in the unique category names as a separate element
        JsonArray categoryNames = new JsonArray();
        i.add("category_names", categoryNames);
        List<String> sorted = new ArrayList<>(catNames);
        Collections.sort(sorted);
        for (String catName : sorted) {
            categoryNames.add(catName);
        }

        return i;
    }

    public static Map<AiModel, List<AiPrompt>> readPrompts() throws SQLException, NamingException {
        Connection con = null;
        try {
            con = Model.connectX();
            return readPrompts(con);
        } finally {
            Model.close(con);
        }
    }

    public static Map<AiModel, List<AiPrompt>> readPrompts(Connection con) throws SQLException {
        Map<AiModel, List<AiPrompt>> modelPrompts = new HashMap<>();
        List<AiPrompt.Type> promptTypes = new ArrayList<>();
        promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES);
        promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_OBJECTS);
        promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_PROPERTIES);
        List<AiPrompt> dbPrompts = DbLanguage.findPrompts(con, promptTypes);
        for (AiPrompt p : dbPrompts) {
            List<AiPrompt> prompts = modelPrompts.get(p.getModel());
            if (prompts == null) {
                prompts = new ArrayList<>();
                modelPrompts.put(p.getModel(), prompts);
            }
            prompts.add(p);
        }

        return modelPrompts;
    }

    /**
     * Label all images for this request, and store the response(s) in the db.
     *
     * @param con
     * @param request
     * @return
     */
    public abstract ImageLabelResponse labelize(Connection con, AiImageLabelRequest request) throws SQLException, IOException;
}
