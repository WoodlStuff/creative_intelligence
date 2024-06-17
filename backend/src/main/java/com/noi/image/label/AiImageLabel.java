package com.noi.image.label;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.noi.image.AiImage;
import com.noi.language.AiPrompt;
import com.noi.tools.JsonTools;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiImageLabel {
    private final String mid;
    private final double score, topicality;
    private final Map<String, List<LabelMetaData>> labelCategories = new HashMap<>();
    private final int statusCode;
    private final String reason;
    private Long id; // the db-id
    private final AiImage image;
    private final String modelName, content;
    private final boolean annotation;

    private final String requestUUID;

    private AiImageLabel(AiImage image, String requestUUID, String modelName, String content, boolean isAnnotation, Map<String, List<LabelMetaData>> labelCategories) {
        this(image, requestUUID, modelName, content, isAnnotation, null, 0.0, 0.0, labelCategories);
    }

    private AiImageLabel(AiImage image, String requestUUID, String modelName, String content, boolean isAnnotation, String mid, double score, double topicality, Map<String, List<LabelMetaData>> labelCategories) {
        this.statusCode = HttpServletResponse.SC_OK;
        this.reason = null; // the error reason (http response content)

        this.requestUUID = requestUUID;
        this.annotation = isAnnotation;
        this.image = image;
        this.modelName = modelName;
        this.content = content;
        this.mid = mid;
        this.score = score;
        this.topicality = topicality;
        if (labelCategories != null) {
            this.labelCategories.putAll(labelCategories);
        }
    }

    private AiImageLabel(AiImage image, String modelName, int statusCode, String reasonPhrase) {
        this.requestUUID = null;
        this.statusCode = statusCode;
        this.reason = reasonPhrase;
        this.annotation = false;
        this.image = image;
        this.modelName = modelName;
        this.content = null;
        this.mid = null;
        this.score = -1d;
        this.topicality = -1d;
    }

    public static AiImageLabel create(AiImage image, String requestUUID, String modelName, String content, Map<String, List<LabelMetaData>> labelCategories) {
        return new AiImageLabel(image, requestUUID, modelName, content, false, labelCategories);
    }

    /*
    Notes: (taken from Google Vision)
    mid - if present, contains a machine-generated identifier (MID) corresponding to the entity's Google Knowledge Graph entry. Note that mid values remain unique across different languages, so you can use these values to tie entities together from different languages. To inspect MID values, refer to the Google Knowledge Graph API documentation.
    score - the confidence score, which ranges from 0 (no confidence) to 1 (very high confidence).
    topicality - The relevancy of the Image Content Annotation (ICA) label to the image. It measures how important/central a label is to the overall context of a page.
     */
    public static AiImageLabel create(AiImage image, String requestUUID, String modelName, String content, String mid, double score, double topicality) {
        return new AiImageLabel(image, requestUUID, modelName, content, true, mid, score, topicality, null);
    }

    public static Map<String, List<LabelMetaData>> parseMetaCategories(JsonObject metaDataRoot, String rootCategory, int promptType) {
        if (promptType == AiPrompt.TYPE_IMAGE_LABEL_OBJECTS.getType()) {
            return parseImageElements(metaDataRoot);
        } else if (promptType == AiPrompt.TYPE_IMAGE_LABEL_PROPERTIES.getType()) {
            return parseImageProperties(metaDataRoot);
        }

        // otherwise it's AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES
        Map<String, List<LabelMetaData>> labelMeta = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : metaDataRoot.entrySet()) {
            try {
                //  have we reached the tip of the tree branch?
                if (!entry.getValue().isJsonPrimitive()) {
                    if (entry.getValue().isJsonArray()) {
                        JsonArray array = entry.getValue().getAsJsonArray();
                        for (int i = 0; i < array.size(); i++) {
                            JsonElement arrayElement = array.get(i);
                            if (arrayElement.isJsonPrimitive()) {
                                appendLabelMeta(labelMeta, rootCategory, entry.getKey(), arrayElement);
                            } else {
                                labelMeta.putAll(parseMetaCategories(arrayElement.getAsJsonObject(), entry.getKey(), promptType));
                            }
                        }
                    } else {
                        labelMeta.putAll(parseMetaCategories(entry.getValue().getAsJsonObject(), entry.getKey(), promptType));
                    }

                } else {
                    appendLabelMeta(labelMeta, rootCategory, entry.getKey(), entry.getValue());
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        return labelMeta;
    }

    private static Map<String, List<LabelMetaData>> parseImageProperties(JsonObject metaDataRoot) {
        Map<String, List<LabelMetaData>> labelMeta = new HashMap<>();
        // image_properties: {"size": "the size of the image", "format": "the encoding of the file (JPEG, PNG, ...)", "overall_color": "vibrant", "color_gradients": "Sky blue to white", "shadows": "minimal", "lighting"}
        JsonElement propertiesNode = metaDataRoot.get("image_properties");
        if (propertiesNode != null && propertiesNode.isJsonObject()) {
            labelMeta.putAll(parseMetaCategories(propertiesNode.getAsJsonObject(), "image_properties", AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES.getType()));
        }

        // language featured in the image (if any)
        // {"image_language": {"themes": "", "persuasive_language": "", "calls_to_action": "", "claims_language": "", "trust_queues": "", "figures_of_speech": "", "sense_of_urgency": "", "health_claims
        JsonElement languageNode = metaDataRoot.get("image_language");
        if (languageNode != null && languageNode.isJsonObject()) {
            labelMeta.putAll(parseMetaCategories(languageNode.getAsJsonObject(), "image_language", AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES.getType()));
        }

        return labelMeta;
    }

//    private static Map<String, List<LabelMetaData>> parseImageProperties(JsonObject metaDataRoot) {
//        Map<String, List<LabelMetaData>> labelMeta = new HashMap<>();
//        // image_properties:
//        // {"size":"768x768","image_format":"PNG","overall_color":"Dark tones","color_gradients":"Yes","shadows":"Present","lighting":"Artificial, dramatic"}
//        JsonElement imageProperties = metaDataRoot.get("image_properties");
//        if (imageProperties != null && !imageProperties.isJsonNull()) {
//            JsonObject properties = imageProperties.getAsJsonObject();
//            // map: overall_color:"xxxx",
//            JsonElement overallColor = imageProperties.getAsJsonObject().get("overall_color");
//            if (overallColor != null) {
//                appendLabelMeta(labelMeta, "image_properties", "overall_color", overallColor);
//            }
//
//            // map: lighting: [{direction:xxx}]
//            appendConditionalMeta(labelMeta, properties, "lighting");
//
//            // map: notable_features: [{text_overlay:xxx}]
//            appendConditionalMeta(labelMeta, properties, "notable_features");
//
////            JsonElement features = properties.get("notable_features");
////            if (features != null && features.isJsonArray()) {
////                JsonArray array = features.getAsJsonArray();
////                for (int i = 0; i < array.size(); i++) {
////                    JsonObject arrayElement = array.get(i).getAsJsonObject();
////                    for (Map.Entry<String, JsonElement> entry : arrayElement.entrySet()) {
////                        if (entry.getValue().isJsonPrimitive()) {
////                            appendLabelMeta(labelMeta, "notable_features", entry.getKey(), entry.getValue());
////                        } else {
////                            System.out.printf("WARNING: unexpected child node(s) for image_properties.notable_features.%s:%s%n", entry.getKey(), entry.getValue());
////                        }
////                    }
////                }
////            } else if (features != null && !features.isJsonNull()) {
////                appendLabelMeta(labelMeta, "notable_features", "??xxx??", features);
////            }
//
//            // ----------------------
//            // map LabelMetaProperty
//            // color_gradients[]: location, description
//            JsonElement colorGradients = properties.get("color_gradients");
//            if (colorGradients != null && !colorGradients.isJsonNull()) {
//                JsonArray gradientArray = colorGradients.getAsJsonArray();
//                for (int i = 0; i < gradientArray.size(); i++) {
//                    JsonObject gradient = gradientArray.get(i).getAsJsonObject();
//
//                    LabelMetaData meta = appendLabelMeta(labelMeta, "color_gradients", "location", gradient.get("location"));
//
//                    String location = JsonTools.getAsString(gradient, "location");
//                    String description = JsonTools.getAsString(gradient, "description");
//                    LabelMetaColorGradient colorGradient = LabelMetaColorGradient.create(location, description);
//                    meta.add(colorGradient);
//                }
//            }
//
//            // shadows:[]: subject, description
//            JsonElement shadows = properties.get("shadows");
//            if (shadows != null && !shadows.isJsonNull()) {
//                JsonArray shadowsArray = shadows.getAsJsonArray();
//                for (int i = 0; i < shadowsArray.size(); i++) {
//                    JsonObject shadow = shadowsArray.get(i).getAsJsonObject();
//
//                    LabelMetaData meta = appendLabelMeta(labelMeta, "shadows", "subject", shadow.get("subject"));
//
//                    String subject = JsonTools.getAsString(shadow, "subject");
//                    String description = JsonTools.getAsString(shadow, "description");
//                    LabelMetaShadow metaShadow = LabelMetaShadow.create(subject, description);
//                    meta.add(metaShadow);
//                }
//            }
//        }
//
//        return labelMeta;
//    }

    private static void appendConditionalMeta(Map<String, List<LabelMetaData>> labelMeta, JsonObject properties, String key) {
        JsonElement object = properties.get(key);
        if (object != null && object.isJsonArray()) {
            JsonArray array = properties.getAsJsonArray(key);
            for (int i = 0; i < array.size(); i++) {
                JsonObject arrayElement = array.get(i).getAsJsonObject();
                appendFromPrimitiveObject(arrayElement, labelMeta, key);
            }
        } else if (object != null && !object.isJsonNull()) {
            appendFromPrimitiveObject(object.getAsJsonObject(), labelMeta, key);
        }
    }

    private static void appendFromPrimitiveObject(JsonObject lighting, Map<String, List<LabelMetaData>> labelMeta, String key) {
        for (Map.Entry<String, JsonElement> entry : lighting.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                appendLabelMeta(labelMeta, key, entry.getKey(), entry.getValue());
            } else {
                System.out.printf("WARNING: unexpected child node(s) for %s.%s:%s%n", key, entry.getKey(), entry.getValue());
            }
        }
    }

    private static Map<String, List<LabelMetaData>> parseImageElements(JsonObject metaDataRoot) {
        Map<String, List<LabelMetaData>> labelMeta = new HashMap<>();
        // parse the layout: {orientation:'', aspect_ration:''}
        JsonElement layout = metaDataRoot.get("layout");
        if (layout != null && !layout.isJsonNull()) {
            if (layout.isJsonObject()) {
                JsonObject layoutObject = layout.getAsJsonObject();
                labelMeta.putAll(parseMetaCategories(layoutObject, "layout", AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES.getType()));
            } else if (layout.isJsonArray()) {
                // "layout":["water horizon","skyline"]
                JsonArray array = layout.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    JsonElement arrayElement = array.get(i);
                    if (arrayElement.isJsonPrimitive()) {
                        appendLabelMeta(labelMeta, "layout", "layout", arrayElement);
                    } else {
                        labelMeta.putAll(parseMetaCategories(arrayElement.getAsJsonObject(), "layout", AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES.getType()));
                    }
                }
            }
            // todo: add background and overlay ....see 20240502/b2d5....961e-resp.json
        }

        // image_elements:[{object:'',...},{content:'',...}]
        JsonElement imageElements = metaDataRoot.get("image_objects");
        if (imageElements != null && !imageElements.isJsonNull()) {
            JsonArray array = imageElements.getAsJsonArray();

            for (int i = 0; i < array.size(); i++) {
                JsonObject arrayObject = array.get(i).getAsJsonObject();
//                parseLabelObject(labelMeta, "image_objects", arrayObject);
                parseLabelObjectWithType(labelMeta, "image_objects", arrayObject);

                //                if (arrayObject.get("content") != null) {
//                    parseLabelContent(labelMeta, "image_objects", arrayObject);
//
//                } else if (arrayObject.get("object") != null) {
//                    parseLabelObject(labelMeta, "image_objects", arrayObject);
//
//                } else {
//                    // unknown element type!
//                    System.out.println("WARNING: unknown image_element child node: " + arrayObject.toString());
//                }
            }
        }

        // objects: [type, value, location, font, relative_size, background]
        imageElements = metaDataRoot.get("objects");
        if (imageElements != null && !imageElements.isJsonNull()) {
            // is it an array?
            if (imageElements.isJsonArray()) {
                JsonArray array = imageElements.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    JsonElement arrayElement = array.get(i);
                    if (arrayElement.isJsonObject()) {
                        JsonObject arrayObject = arrayElement.getAsJsonObject();
                        parseLabelObjectWithType(labelMeta, "image_objects", arrayObject);
                    } else if (arrayElement.isJsonPrimitive()) {
                        appendLabelMeta(labelMeta, "image_objects", "object", arrayElement);
                    }
                }
            } else if (!imageElements.isJsonPrimitive()) {
                // is it a map (k:v)?
                // "objects":{"person_1":"yellow shirt","person_2":"blue shirt", ...
                Map<String, List<LabelMetaData>> objects = parseMetaCategories(imageElements.getAsJsonObject(), "image_objects", AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES.getType());
                labelMeta.putAll(objects);
                for (List<LabelMetaData> metaList : objects.values()) {
                    for (LabelMetaData data : metaList) {
                        if (!data.hasMetaObject()) {
                            LabelMetaObject lmo = LabelMetaObject.create(data.getKey(), null, data.getValue(), null, null, null, null, null, null, null);
                            data.add(lmo);
                        }
                    }
                }
            }
        }

        // "overlay_text":[{"text":"Disappear for 1 month","font":"sans-serif","color":"white","size":"large","location":"upper right","emphasis":"bold"},{"text":"and become a master\nof AI tools","font":"sans-serif","color":"white","size":"large","location":"upper right","emphasis":"normal"},{"text":"1. Stop scrolling\n2. Take a 1-minute quiz\n3. Spend 15 minutes a\nday learning new skills\n4. Become a master of\nAI Tools and replace\nmonkey job!","font":"sans-serif","color":"white","size":"medium","location":"right side","emphasis":"normal"},{"text":"START NOW","font":"sans-serif","color":"white","size":"large","location":"bottom right","background_color":"black","emphasis":"bold","encapsulated":{"shape":"rectangle","color":"black","border_thickness":"medium"}}]
        imageElements = metaDataRoot.get("overlay_text");
        if (imageElements != null && !imageElements.isJsonNull()) {
            if (imageElements.isJsonArray()) {
                JsonArray array = imageElements.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    JsonObject arrayObject = array.get(i).getAsJsonObject();
                    parseLabelOverlayText(labelMeta, "overlay_text", arrayObject);
                }
            } else if (!imageElements.isJsonPrimitive()) {
                // "overlay_text":{"top_left_text":"Explore Barbados like never before!","logos":"Gears246 Bike Adventure Tours","main_heading":"eBIKE GROUP TOURS AVAILABLE","main_text":"Experience the thrill of a bike ride along the scenic spots in Barbados at your own pace. Book your eBike tour or rental today!"}
                Map<String, List<LabelMetaData>> objects = parseMetaCategories(imageElements.getAsJsonObject(), "overlay_text", AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES.getType());
                labelMeta.putAll(objects);
                for (List<LabelMetaData> metaList : objects.values()) {
                    for (LabelMetaData data : metaList) {
                        if (!data.hasMetaObject()) {
                            LabelMetaObject lmo = LabelMetaObject.create(data.getKey(), "text", data.getValue(), null, null, null, null, null, null, null);
                            data.add(lmo);
                        }
                    }
                }
            } else {
                appendLabelMeta(labelMeta, "overlay_text", "overlay_text", imageElements);
            }
        }

        // fallback
        if (labelMeta.isEmpty()) {
            return parseMetaCategories(metaDataRoot, "image_objects", AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES.getType());
        }
        return labelMeta;
    }

    private static void parseLabelObject(Map<String, List<LabelMetaData>> labelMeta, String categoryName, JsonObject arrayObject) {
        List<LabelMetaData> labels = labelMeta.get(categoryName);
        if (labels == null) {
            labels = new ArrayList<>();
            labelMeta.put(categoryName, labels);
        }

        String object = JsonTools.getAsString(arrayObject, "name");
        String type = JsonTools.getAsString(arrayObject, "type");
        String location = JsonTools.getAsString(arrayObject, "location");
        String font = JsonTools.getAsString(arrayObject, "font");
        String relativeSize = JsonTools.getAsString(arrayObject, "relative_size");
        String color = JsonTools.getAsStringCsl(arrayObject, "color");
        String gender = JsonTools.getAsString(arrayObject, "gender");
        String brand = JsonTools.getAsString(arrayObject, "brand");
        String backgroundColor = JsonTools.getAsStringCsl(arrayObject, "background_color");
        LabelMetaObject lmo = LabelMetaObject.create(object, type, null, location, relativeSize, font, color, backgroundColor, brand, gender);
        LabelMetaData data = LabelMetaData.create("object", object, lmo);
        labels.add(data);
    }

//    private static void parseLabelObjectWithType(Map<String, List<LabelMetaData>> labelMeta, String categoryName, JsonObject arrayObject) {
//        List<LabelMetaData> labels = labelMeta.get(categoryName);
//        if (labels == null) {
//            labels = new ArrayList<>();
//            labelMeta.put(categoryName, labels);
//        }
//
//        String object = JsonTools.getAsString(arrayObject, "object");
//        String type = JsonTools.getAsString(arrayObject, "type");
//        String gender = JsonTools.getAsString(arrayObject, "gender");
//        String brand = JsonTools.getAsString(arrayObject, "brand");
//        String text = JsonTools.getAsString(arrayObject, "text");
//        String details = JsonTools.getAsString(arrayObject, "details");
//
//        if (type == null && text != null) {
//            type = "text";
//        }
//
//        String key = object != null ? object : type;
//        if (key == null) {
//            Gson gson = new Gson();
//            System.out.println("unknown object " + gson.toJson(arrayObject));
//            return; // we don't know how to read this!
//        }
//
//        if (object == null && type != null) {
//            // fallback: object can't be null!
//            object = type;
//        }
//        String value = JsonTools.getAsString(arrayObject, "value");
//        if (value == null) {
//            value = JsonTools.getAsStringCsl(arrayObject, "content");
//        }
//        if (value == null && text != null) {
//            // fallback!
//            value = text;
//        }
//        if (value == null) {
//            // fallback!
//            value = details;
//        }
//
//        String location = null;
//        JsonElement locationNode = arrayObject.get("location");
//        if (locationNode != null) {
//            if (locationNode.isJsonPrimitive()) {
//                location = locationNode.getAsString();
//            } else {
//                location = JsonTools.getAsStringCsl(arrayObject, "location");
//            }
//        }
//
//        String font = JsonTools.getAsString(arrayObject, "font");
//        String relativeSize = JsonTools.getAsString(arrayObject, "relative_size");
//        if (relativeSize == null) {
//            // fallback for size
//            relativeSize = JsonTools.getAsString(arrayObject, "size");
//        }
//        String color = JsonTools.getAsStringCsl(arrayObject, "color");
//        if (color == null) {
//            // fallback: colors?
//            color = JsonTools.getAsStringCsl(arrayObject, "colors");
//        }
//        if (color == null) {
//            color = JsonTools.getAsStringCsl(arrayObject, "color_clothing");
//        }
//
//        String backgroundColor = JsonTools.getAsStringCsl(arrayObject, "background_color");
//        if (backgroundColor == null) {
//            // try this fallback
//            backgroundColor = JsonTools.getAsStringCsl(arrayObject, "background");
//        }
//        LabelMetaObject lmo = LabelMetaObject.create(object, type, value, location, relativeSize, font, color, backgroundColor, brand, gender);
//        LabelMetaData data = LabelMetaData.create("object", key, lmo);
//        labels.add(data);
//    }

    private static void parseLabelObjectWithType(Map<String, List<LabelMetaData>> labelMeta, String categoryName, JsonObject arrayObject) {
        List<LabelMetaData> labels = labelMeta.get(categoryName);
        if (labels == null) {
            labels = new ArrayList<>();
            labelMeta.put(categoryName, labels);
        }

        String name = JsonTools.getAsString(arrayObject, "name");
        String type = JsonTools.getAsString(arrayObject, "type");

        String gender = JsonTools.getAsString(arrayObject, "gender");
        String brand = JsonTools.getAsString(arrayObject, "brand");

        String color = JsonTools.getAsStringCsl(arrayObject, "color");
        if (color == null) {
            // fallback: colors?
            color = JsonTools.getAsStringCsl(arrayObject, "colors");
        }
        if (color == null) {
            color = JsonTools.getAsStringCsl(arrayObject, "primary_color");
        }

        String backgroundColor = JsonTools.getAsStringCsl(arrayObject, "background_color");
        if (backgroundColor == null) {
            // try this fallback
            backgroundColor = JsonTools.getAsStringCsl(arrayObject, "background");
        }

        String location = null;
        JsonElement locationNode = arrayObject.get("location");
        if (locationNode != null) {
            if (locationNode.isJsonPrimitive()) {
                location = locationNode.getAsString();
            } else {
                location = JsonTools.getAsStringCsl(arrayObject, "location");
            }
        }

        String relativeSize = JsonTools.getAsString(arrayObject, "relative_size");
        if (relativeSize == null) {
            // fallback for size
            relativeSize = JsonTools.getAsString(arrayObject, "size");
        }

        String font = JsonTools.getAsString(arrayObject, "font");

        LabelMetaObject lmo = LabelMetaObject.create(name, type, null, location, relativeSize, font, color, backgroundColor, brand, gender);
        LabelMetaData data = LabelMetaData.create(type, name, lmo);
        labels.add(data);
    }

    private static void parseLabelOverlayText(Map<String, List<LabelMetaData>> labelMeta, String categoryName, JsonObject arrayObject) {
        List<LabelMetaData> labels = labelMeta.get(categoryName);
        if (labels == null) {
            labels = new ArrayList<>();
            labelMeta.put(categoryName, labels);
        }

        String type = "text";
        String content = JsonTools.getAsString(arrayObject, "content");
        if (content == null) {
            content = JsonTools.getAsString(arrayObject, "text_content");
        }

        String gender = JsonTools.getAsString(arrayObject, "gender");
        String brand = JsonTools.getAsString(arrayObject, "brand");

        String color = JsonTools.getAsStringCsl(arrayObject, "color");
        if (color == null) {
            // fallback: colors?
            color = JsonTools.getAsStringCsl(arrayObject, "colors");
        }
        if (color == null) {
            color = JsonTools.getAsStringCsl(arrayObject, "primary_color");
        }

        String backgroundColor = JsonTools.getAsStringCsl(arrayObject, "background_color");
        if (backgroundColor == null) {
            // try this fallback
            backgroundColor = JsonTools.getAsStringCsl(arrayObject, "background");
        }

        String location = null;
        JsonElement locationNode = arrayObject.get("location");
        if (locationNode != null) {
            if (locationNode.isJsonPrimitive()) {
                location = locationNode.getAsString();
            } else {
                location = JsonTools.getAsStringCsl(arrayObject, "location");
            }
        }

        String relativeSize = JsonTools.getAsString(arrayObject, "relative_size");
        if (relativeSize == null) {
            // fallback for size
            relativeSize = JsonTools.getAsString(arrayObject, "size");
        }

        String font = JsonTools.getAsString(arrayObject, "font");

        LabelMetaObject lmo = LabelMetaObject.create(content, type, null, location, relativeSize, font, color, backgroundColor, brand, gender);
        LabelMetaData data = LabelMetaData.create(type, content, lmo);
        labels.add(data);
    }

    private static void parseLabelContent(Map<String, List<LabelMetaData>> labelMeta, String categoryName, JsonObject arrayObject) {
        List<LabelMetaData> labels = labelMeta.get(categoryName);
        if (labels == null) {
            labels = new ArrayList<>();
            labelMeta.put(categoryName, labels);
        }

        String content = JsonTools.getAsString(arrayObject, "content");
        String location = JsonTools.getAsString(arrayObject, "location");
        String brand = JsonTools.getAsString(arrayObject, "brand");
        String gender = JsonTools.getAsString(arrayObject, "gender");
        String backgroundColor = JsonTools.getAsString(arrayObject, "background_color");
        String relativeSize = JsonTools.getAsString(arrayObject, "relative_size");
        String color = JsonTools.getAsStringCsl(arrayObject, "color");
        String elementType = JsonTools.getAsStringCsl(arrayObject, "element_type");
        String font = JsonTools.getAsStringCsl(arrayObject, "font");

//        LabelMetaContent lmc = LabelMetaContent.create(content, elementType, location, relativeSize, font, color, backgroundColor);
        LabelMetaObject lmo = LabelMetaObject.create(content, elementType, null, location, relativeSize, font, color, backgroundColor, brand, gender);
        LabelMetaData data = LabelMetaData.create("content", content, lmo);
        labels.add(data);
    }

    private static void appendLabelMeta(Map<String, List<LabelMetaData>> labelMeta, String rootCategory, String key, JsonElement value) {
        // skip empty values
        if (value == null || value.isJsonNull() || value.getAsString().trim().isEmpty()) {
            return;
        }

        String category = rootCategory == null ? key : rootCategory;
        List<LabelMetaData> categoryMeta = labelMeta.get(category);
        if (categoryMeta == null) {
            categoryMeta = new ArrayList<>();
            labelMeta.put(category, categoryMeta);
        }
        LabelMetaData lm = LabelMetaData.create(key, value.getAsString());
        categoryMeta.add(lm);
//        return lm;
    }

    // create an error response!
    public static AiImageLabel create(AiImage image, String modelName, int statusCode, String reasonPhrase) {
        return new AiImageLabel(image, modelName, statusCode, reasonPhrase);
    }

    public Map<String, List<LabelMetaData>> getLabelCategories() {
        return labelCategories;
    }

    public AiImage getImage() {
        return image;
    }

    public String getModelName() {
        return modelName;
    }

    public String getContent() {
        return content;
    }

    public boolean isAnnotation() {
        return annotation;
    }

    public String getRequestUUID() {
        return requestUUID;
    }

    public String getMid() {
        return mid;
    }

    public double getScore() {
        return score;
    }

    public double getTopicality() {
        return topicality;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isErrorResponse() {
        return statusCode != HttpServletResponse.SC_OK;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "AiImageLabel{" +
                "mid='" + mid + '\'' +
                ", score=" + score +
                ", topicality=" + topicality +
                ", id=" + id +
                ", image=" + image +
                ", modelName='" + modelName + '\'' +
                ", content='" + content + '\'' +
                ", annotation=" + annotation +
                '}';
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
