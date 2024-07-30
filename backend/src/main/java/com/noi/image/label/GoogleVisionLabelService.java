package com.noi.image.label;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noi.AiModel;
import com.noi.image.AiImage;
import com.noi.image.AiImageRequest;
import com.noi.image.AiImageService;
import com.noi.language.AiImageLabelRequest;
import com.noi.language.AiLabelConsolidateRequest;
import com.noi.language.AiPrompt;
import com.noi.models.DbImage;
import com.noi.models.DbImageLabel;
import com.noi.requests.ImageLabelResponse;
import com.noi.tools.FileTools;
import com.noi.tools.JsonTools;
import com.noi.tools.SystemEnv;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Note: this for now requires an access token to be regularly generated via:
 * google-cloud-sdk/bin/gcloud auth print-access-token
 * --> copy/ paste the token to API_KEY below
 */
public class GoogleVisionLabelService extends LabelService {
    private static final String HOST = "https://vision.googleapis.com/v1/images:annotate";

    // use (for now): google-cloud-sdk/bin/gcloud auth print-access-token to refresh the token
    private String API_KEY = null;
    private static final String GOOGLE_CLOUD_PROJECT_ID = "api-project-748579429046";
    public static final String MODEL_NAME = "GoogleVision";

    protected GoogleVisionLabelService() {
        super(null);
        // @Note! in $TOMCAT_HOME/bin/setenv.sh add export GOOGLE_API_KEY=xxx
        // see account details: https://console.cloud.google.com/apis/dashboard?project=xxx
        API_KEY = SystemEnv.get("GOOGLE_API_KEY", null);
    }

    @Override
    public ImageLabelResponse labelize(Connection con, AiImageLabelRequest request) throws SQLException, IOException {
        System.out.println("GoogleVisionLabelService.labelize " + request.getImageId() + ": " + request.getPrompt());
        AiImage image = DbImage.find(con, request.getImageId());
        List<AiImageLabel> labels = new ArrayList<>();

        labels.addAll(post(API_KEY, request, image));

        // persist the labels
        DbImageLabel.insertAnnotation(con, request, labels);

        return ImageLabelResponse.create(request, labels);
    }

    @Override
    public String lookupThemeForWords(AiLabelConsolidateRequest request) throws IOException {
        // todo:
        throw new IllegalStateException("implement me!");
    }

    private static JsonObject createPayload(AiImageLabelRequest aiRequest, AiImage aiImage) throws IOException {
        // todo: or use com.google.cloud.vision.v1.* ??
        System.out.println("GoogleVisionLabelService.createPayload for " + aiRequest + ": " + aiImage);
        byte[] fileContent;

        // check if there is a local file for this image
        File localImageFile = AiImageService.getLocalImageFile(aiImage);
        if (localImageFile != null) {
            // if so: use it!
            System.out.println("... from local file ...");
            fileContent = FileUtils.readFileToByteArray(localImageFile);
        } else {
            // otherwise: get the file content from the remote url
            System.out.println("... from remote URL ...");
            fileContent = IOUtils.toByteArray(new URL(aiImage.getUrl()));
        }

        String encodedString = Base64.getEncoder().encodeToString(fileContent);

        JsonObject root = new JsonObject();
        JsonArray requests = new JsonArray();
        JsonObject request = new JsonObject();
        JsonObject image = new JsonObject();
        JsonArray features = new JsonArray();

        root.add("requests", requests);
        requests.add(request);
        request.add("image", image);
        request.add("features", features);

        // Google mapping for prompt types
        if (aiRequest.getPrompt() != null) {
            int promptType = aiRequest.getPrompt().getPromptType();
            if (AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES.getType() == promptType) {
                addFeatures(features, new String[]{"LABEL_DETECTION", "TEXT_DETECTION", "DOCUMENT_TEXT_DETECTION", "FACE_DETECTION", "LANDMARK_DETECTION", "LOGO_DETECTION", "SAFE_SEARCH_DETECTION", "WEB_DETECTION"});
            } else if (AiPrompt.TYPE_IMAGE_LABEL_OBJECTS.getType() == promptType) {
                addFeatures(features, new String[]{"OBJECT_LOCALIZATION"});
//            } else if (AiPrompt.TYPE_IMAGE_LABEL_PROPERTIES.getType() == promptType) {
//                addFeatures(features, new String[]{"IMAGE_PROPERTIES"});
            }
        } else {
            // default (if no prompt is provided)
            addFeatures(features, new String[]{"LABEL_DETECTION", "TEXT_DETECTION", "LANDMARK_DETECTION", "LOGO_DETECTION", "OBJECT_LOCALIZATION", "WEB_DETECTION"});
        }
        image.addProperty("content", encodedString);

        /*
        {
          "requests": [
            {
              "image": {
                "content": "BASE64_ENCODED_IMAGE" // The base64 representation (ASCII string) of your binary image data. This string should look similar to the following string:
/9j/4QAYRXhpZgAA...9tAVx/zDQDlGxn//2Q==
                // or image at a gs:// - url:
                "source": {
                  "gcsImageUri": "CLOUD_STORAGE_IMAGE_URI"
                }
              },
              "features": [
                {
                  "maxResults": RESULTS_INT, // (Optional) An integer value of results to return. If you omit the "maxResults" field and its value, the API returns the default value of 10 results. This field does not apply to the following feature types: TEXT_DETECTION, DOCUMENT_TEXT_DETECTION, or CROP_HINTS.
                  "type": "LABEL_DETECTION"
                }
              ]
            }
          ]
        }
         */

        return root;
    }

    private static void addFeatures(JsonArray features, String[] fefe) {
        for (String f : fefe) {
            JsonObject feature = new JsonObject();
            //feature.addProperty("maxResults", 10);
            feature.addProperty("type", f);
            features.add(feature);
        }
    }

    private static List<AiImageLabel> post(String apiKey, AiImageLabelRequest request, AiImage image) throws IOException {
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;

        try {
            client = HttpClients.createDefault();

            HttpPost httpPost = createHttpPost(apiKey, request, image);
            response = client.execute(httpPost);

            return parseResponse(image, request, response);
        } finally {
            if (response != null) {
                response.close();
            }
            if (client != null) {
                client.close();
            }
        }
    }

    private static HttpPost createHttpPost(String apiKey, AiImageLabelRequest request, AiImage image) throws IOException {
        Gson gson = new Gson();
        JsonObject payloadJson = createPayload(request, image);
        StringEntity entity = new StringEntity(gson.toJson(payloadJson), ContentType.APPLICATION_JSON);

        HttpPost httpPost = new HttpPost(HOST);
        httpPost.setHeader("Authorization", "Bearer " + apiKey);
        httpPost.setHeader("x-goog-user-project", GOOGLE_CLOUD_PROJECT_ID);
        httpPost.setHeader("User-Agent", "Noi");
        httpPost.setEntity(entity);
        return httpPost;
    }

    private static List<AiImageLabel> parseResponse(AiImage image, AiImageLabelRequest request, CloseableHttpResponse httpResponse) throws IOException {
        String jsonResponse = FileTools.readToString(httpResponse.getEntity().getContent());
        System.out.println("response:" + jsonResponse);
        System.out.println("response-code:" + httpResponse.getStatusLine().getStatusCode());
        return parseResponse(image, request, jsonResponse);
    }

    private static List<AiImageLabel> parseResponse(AiImage image, AiImageLabelRequest request, String jsonResponse) {
        List<AiImageLabel> labels = new ArrayList<>();
        JsonObject root = new JsonParser().parse(jsonResponse).getAsJsonObject();
        if (root != null) {
            JsonArray responses = root.getAsJsonArray("responses");
            if (responses != null && !responses.isJsonNull()) {
                for (int i = 0; i < responses.size(); i++) {
                    JsonObject response = responses.get(i).getAsJsonObject();

                    // we request different things based on the prompt type, or a set of defaults if no prompt was provided
                    if (request.getPrompt() != null) {
                        if (AiPrompt.TYPE_IMAGE_LABEL_OBJECTS.getType() == request.getPrompt().getPromptType()) {
                            parseResponseAnnotationForCategory(image, labels, response, "localizedObjectAnnotations");
                        }
                        continue;
                    }

                    parseResponseAnnotationForCategory(image, labels, response, "labelAnnotations");
                    parseResponseAnnotationForCategory(image, labels, response, "textAnnotations");
                    parseResponseAnnotationForCategory(image, labels, response, "landmarkAnnotations");
                    parseResponseAnnotationForCategory(image, labels, response, "logoAnnotations");

                    parseResponseForSafeSearch(image, labels, response);
                    parseResponseForWebDetect(image, labels, response);

                    // todo: too complicated / specific?
                    // * faceAnnotations (location of face details in the image)
                    // * imagePropertiesAnnotation (RBG color values used with their fraction of pixels in the image
                }
            }
        }

        return labels;
    }

    private static void parseResponseForWebDetect(AiImage image, List<AiImageLabel> labels, JsonObject response) {
        if (!response.has("webDetection")) {
            return;
        }

        JsonObject web = response.get("webDetection").getAsJsonObject();
        parseResponseAnnotationForCategory(image, labels, web, "webEntities");

        /* todo: https://cloud.google.com/vision/docs/detecting-web
        // todo: add parsing of the image urls and best guess label(s)
            "webDetection": {
            "webEntities": [
              {
                "entityId": "/m/02p7_j8",
                "score": 1.44225,
                "description": "Carnival in Rio de Janeiro"
              },
              {
                "entityId": "/m/06gmr",
                "score": 1.2913725,
                "description": "Rio de Janeiro"
              },
              ...
            ],
            "fullMatchingImages": [
                  {
                    "url": "https://1000lugaresparair.files.wordpress.com/2017/11/quinten-de-graaf-278848.jpg"
                  },
                  ...
                ],
            "partialMatchingImages": [
                  {
                    "url": "https://www.linnanneito.fi/wp-content/uploads/sambakarnevaali-riossa.jpg"
                  },
                  ...
                ],
            "pagesWithMatchingImages": [
                  {
                    "url": "https://www.intrepidtravel.com/us/brazil/rio-carnival-122873",
                    "pageTitle": "\u003cb\u003eRio Carnival\u003c/b\u003e | Intrepid Travel US",
                    "partialMatchingImages": [
                      {
                        "url": "https://www.intrepidtravel.com/sites/intrepid/files/styles/large/public/elements/product/hero/GGSR-Brazil-rio-carnival-ladies.jpg"
                      },
                      ...
                ],
            "visuallySimilarImages": [
                  {
                    "url": "https://pbs.twimg.com/media/DVoQOx6WkAIpHKF.jpg"
                  },
                  ...
                ],
            "bestGuessLabels": [
                  {
                    "label": "rio carnival",
                    "languageCode": "en"
                  }
                ]
         */
    }

    private static void parseResponseForSafeSearch(AiImage image, List<AiImageLabel> labels, JsonObject response) {
        if (!response.has("safeSearchAnnotation")) {
            return;
        }

        /*
        todo: https://cloud.google.com/vision/docs/detecting-safe-search
        "safeSearchAnnotation": {
            "adult": "UNLIKELY",
            "spoof": "VERY_UNLIKELY",
            "medical": "VERY_UNLIKELY",
            "violence": "LIKELY",
            "racy": "POSSIBLE"
        }
        */
    }

    private static void parseResponseAnnotationForCategory(AiImage image, List<AiImageLabel> labels, JsonObject response, String category) {
        if (!response.has(category) || !response.get(category).isJsonArray()) {
            System.out.println("GoogleVisionLabelParser: " + category + " is not present as an array!");
            return;
        }

        JsonArray annotations = response.getAsJsonArray(category);
        if (annotations != null && !annotations.isJsonNull()) {
            for (int a = 0; a < annotations.size(); a++) {
                JsonObject annotation = annotations.get(a).getAsJsonObject();
                String mid = JsonTools.getAsString(annotation, "mid");
                String entityId = JsonTools.getAsString(annotation, "entityId"); // todo: same as mid?
                if (mid == null && entityId != null) {
                    mid = entityId;
                }
                String description = JsonTools.getAsString(annotation, "description");
                String name = JsonTools.getAsString(annotation, "name"); //todo: same as description?
                if (description == null && name != null) {
                    description = name;
                }
                double score = JsonTools.getAsDouble(annotation, "score");
                double topicality = JsonTools.getAsDouble(annotation, "topicality");

                labels.add(AiImageLabel.create(image, null, MODEL_NAME, description, mid, score, topicality));
            }
        }
    }

    public static void main(String[] args) throws IOException {
//        AiImage image = AiImage.create("prompt goes here", 0, "http://localhost/image/1", null);
//        String jsonResponse = "{\n" +
//                "          \"responses\": [\n" +
//                "            {\n" +
//                "              \"labelAnnotations\": [\n" +
//                "                {\n" +
//                "                  \"mid\": \"/m/01c8br\",\n" +
//                "                  \"description\": \"Street\",\n" +
//                "                  \"score\": 0.87294734,\n" +
//                "                  \"topicality\": 0.87294734\n" +
//                "                },\n" +
//                "                {\n" +
//                "                  \"mid\": \"/m/06pg22\",\n" +
//                "                  \"description\": \"Snapshot\",\n" +
//                "                  \"score\": 0.8523099,\n" +
//                "                  \"topicality\": 0.8523099\n" +
//                "                },\n" +
//                "                {\n" +
//                "                  \"mid\": \"/m/0dx1j\",\n" +
//                "                  \"description\": \"Town\",\n" +
//                "                  \"score\": 0.8481104,\n" +
//                "                  \"topicality\": 0.8481104\n" +
//                "                },\n" +
//                "                {\n" +
//                "                  \"mid\": \"/m/01d74z\",\n" +
//                "                  \"description\": \"Night\",\n" +
//                "                  \"score\": 0.80408716,\n" +
//                "                  \"topicality\": 0.80408716\n" +
//                "                },\n" +
//                "                {\n" +
//                "                  \"mid\": \"/m/01lwf0\",\n" +
//                "                  \"description\": \"Alley\",\n" +
//                "                  \"score\": 0.7133322,\n" +
//                "                  \"topicality\": 0.7133322\n" +
//                "                }\n" +
//                "              ]\n" +
//                "            }\n" +
//                "          ]\n" +
//                "        }";

//        // parse a static response
//        List<AiImageLabel> labels = parseResponse(image, jsonResponse);
//        for (AiImageLabel label : labels) {
//            System.out.println(label);
//        }

        // just the payload creation
        //        JsonObject json = createPayload(image);
//        Gson gson = new Gson();
//        System.out.println(gson.toJson(json));

        // use local file to test Base64 based post

        String imgUrl = "";
        Long imageId = -1L;
        Long promptId = -1L;
        String promptText = "A prompt to generate an image.";
        AiPrompt prompt = AiPrompt.create(promptId, promptText);
        AiImageLabelRequest request = AiImageLabelRequest.create(imageId, prompt, AiModel.GOOGLE_VISION);
        AiImageRequest imageRequest = AiImageRequest.create(prompt, AiModel.DALL_E_3.getName());
        // String fileName = "1.jpg";
        //String imgUrl = String.format("%s" + requestUUID + "/%s", AiImageService.FILE_ROOT_FOLDER, fileName);
        AiImage image = AiImage.create(imageRequest, imgUrl, null);
        image.setId(imageId);
        String API_KEY = SystemEnv.get("GOOGLE_API_KEY", null);
        List<AiImageLabel> imageLabels = post(API_KEY, request, image);
        for (AiImageLabel label : imageLabels) {
            System.out.println(label);
        }
    }
}
