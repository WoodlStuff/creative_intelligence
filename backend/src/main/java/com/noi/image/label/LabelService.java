package com.noi.image.label;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.noi.image.AiImage;
import com.noi.language.AiImageLabelRequest;
import com.noi.requests.ImageLabelResponse;
import org.apache.http.entity.ContentType;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public abstract class LabelService {
    public static final String OPEN_AI_COMPLETION = "OpenAiLabels";
    public static final String GOOGLE_VISION = "GoogleVisionLabels";
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

        if (serviceName == null || OPEN_AI_COMPLETION.equalsIgnoreCase(serviceName)) {
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
        return LabelService.OPEN_AI_COMPLETION;
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

    public static void writeLabelReport(AiImage image, List<AiImageLabel> annotations, Map<String, List<LabelMetaData>> metaValues, String categoryName, HttpServletResponse response) throws IOException {
        // create the json doc
        JsonObject root = addImageLabels(image, annotations, metaValues, categoryName);

        response.setContentType(ContentType.APPLICATION_JSON.toString());
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        Writer out = response.getWriter();
        Gson gson = new Gson();
        out.write(gson.toJson(root));
        out.flush();
        out.close();
    }

    public static JsonObject addImageLabels(AiImage image, List<AiImageLabel> annotations, Map<String, List<LabelMetaData>> metaValues, String categoryName) {
        JsonObject i = new JsonObject();
        i.addProperty("path", image.getFilePath());
        i.addProperty("video_frame_number", image.getVideoFrameNumber());
        i.addProperty("video_id", image.getVideoId());

        // annotations
        JsonArray a = new JsonArray();
        i.add("annotations", a);
        for (AiImageLabel label : annotations) {
            JsonObject l = new JsonObject();
            l.addProperty("request_uuid", label.getRequestUUID());
            l.addProperty("model_name", label.getModelName());
            l.addProperty("mid", label.getMid());
            l.addProperty("score", label.getScore());
            l.addProperty("topicality", label.getTopicality());
            a.add(l);
        }

        // categories
        JsonArray categories = new JsonArray();
        i.add("categories", categories);
        for (Map.Entry<String, List<LabelMetaData>> cat : metaValues.entrySet()) {
            // if a filter is provided: only the matching category will be added to the response
            if (categoryName == null || categoryName.equalsIgnoreCase(cat.getKey())) {
                for (LabelMetaData meta : cat.getValue()) {
                    JsonObject label = new JsonObject();
                    categories.add(label);
                    label.addProperty("category_name", cat.getKey());
                    label.addProperty("request_uuid", meta.getRequestUUID());
                    label.addProperty("model_name", meta.getModelName());
                    label.addProperty("key", meta.getKey());
                    label.addProperty("value", meta.getValue());
                }
            }
        }

        return i;
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
