package com.noi.language;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noi.models.DbLanguage;
import com.noi.tools.FileTools;
import com.noi.tools.JsonTools;
import com.noi.tools.SystemEnv;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Docs at: https://cloud.google.com/natural-language/docs/reference/rest
 *
 * @ see account details: https://console.cloud.google.com/apis/dashboard?project=api-project-748579429046
 * credentials: https://cloud.google.com/docs/authentication/rest
 * gcloud client: https://cloud.google.com/sdk/docs/install
 */
public class GoogleNLPService extends NLPService {
    private static final String API_HOST = "https://language.googleapis.com";
    private static final String API_PATH = "/v2/documents";

    private String GOOGLE_API_KEY = null;

    public GoogleNLPService() {
        GOOGLE_API_KEY = SystemEnv.get("GOOGLE_NPL_API_KEY", null);
    }

    @Override
    EntitiesResponse analyzeEntities(NLPRequest request) throws IOException {
        // POST /v2/documents:analyzeEntities

        JsonObject root = createPayload(request);
        JsonObject responseJson = post(root, "analyzeEntities");

        /*
        curl "https://language.googleapis.com/v2/documents:analyzeEntities" \
          -X POST \
          -H "X-Goog-Api-Key: $GOOGLE_API_KEY" \
          -H "Content-Type: application/json" \
          -d '{"document":{"content":"The rain in Spain stays mainly in the plain.", "type":"PLAIN_TEXT"}}'
         */

        String languageCode = JsonTools.getAsString(responseJson, "languageCode");
        boolean languageSupported = JsonTools.getAsBoolean(responseJson, "languageSupported");

        List<Entity> entities = Entity.parseArray(responseJson, "entities");

        EntitiesResponse entityResponse = EntitiesResponse.create(languageCode, languageSupported, entities);
        try {
            DbLanguage.persistEntityResponse(request, entityResponse);
        } catch (SQLException | NamingException e) {
            throw new RuntimeException(e);
        }

        return entityResponse;

        /* response:
        {
          "entities": [
            {
              "name": "Spain",
              "type": "LOCATION",
              "metadata": {},
              "mentions": [
                {
                  "text": {
                    "content": "Spain",
                    "beginOffset": -1
                  },
                  "type": "PROPER",
                  "probability": 0.888
                }
              ]
            },
            {
              "name": "plain",
              "type": "LOCATION",
              "metadata": {},
              "mentions": [
                {
                  "text": {
                    "content": "plain",
                    "beginOffset": -1
                  },
                  "type": "COMMON",
                  "probability": 0.853
                }
              ]
            },
            {
              "name": "rain",
              "type": "OTHER",
              "metadata": {},
              "mentions": [
                {
                  "text": {
                    "content": "rain",
                    "beginOffset": -1
                  },
                  "type": "COMMON",
                  "probability": 0.876
                }
              ]
            }
          ],
          "languageCode": "en",
          "languageSupported": true
        }
                 */

        //persistEntities(request, entities);
    }

    private JsonObject createPayload(NLPRequest request) {
        if (request.getPrompt() == null || request.getPrompt().getPrompt() == null) {
            throw new IllegalArgumentException();
        }

        // '{"document":{"content":"The rain in Spain stays mainly in the plain.", "type":"PLAIN_TEXT"}}'
        JsonObject document = new JsonObject();
        document.addProperty("content", request.getPrompt().getPrompt());
        document.addProperty("type", TypeEnum.PLAIN_TEXT.getType());

        JsonObject root = new JsonObject();
        root.add("document", document);

        return root;
    }

    @Override
    SentimentResponse analyzeSentiment(NLPRequest request) throws IOException {
        // 	POST /v2/documents:analyzeSentiment
        JsonObject root = createPayload(request);
        JsonObject responseJson = post(root, "analyzeSentiment");

        String languageCode = JsonTools.getAsString(responseJson, "languageCode");
        boolean languageSupported = JsonTools.getAsBoolean(responseJson, "languageSupported");
        Sentiment documentSentiment = Sentiment.parse(responseJson, "documentSentiment");
        SentimentResponse sentimentResponse = SentimentResponse.create(languageCode, languageSupported, documentSentiment);

        List<SentenceSentiment> sentenceSentiments = SentenceSentiment.parseArray(responseJson, "sentences");
        sentimentResponse.addSentences(sentenceSentiments);

        try {
            DbLanguage.persistSentimentResponse(request, sentimentResponse);
        } catch (SQLException | NamingException e) {
            throw new RuntimeException(e);
        }

        return sentimentResponse;

        /*
            curl "https://language.googleapis.com/v2/documents:analyzeSentiment" \
              -X POST \
              -H "X-Goog-Api-Key: $GOOGLE_API_KEY" \
              -H "Content-Type: application/json" \
              -d '{"document":{"content":"The rain in Spain stays mainly in the plain.", "type":"PLAIN_TEXT"}}' \
         */

        /* response:
            {
              "documentSentiment": {
                "magnitude": 0.026,
                "score": -0.008
              },
              "languageCode": "en",
              "sentences": [
                {
                  "text": {
                    "content": "The rain in Spain stays mainly in the plain.",
                    "beginOffset": -1
                  },
                  "sentiment": {
                    "magnitude": 0.026,
                    "score": -0.009
                  }
                }
              ],
              "languageSupported": true
            }
         */
    }

    @Override
    public AnnotationResponse annotateText(NLPRequest request, boolean persistResponses) throws IOException {
        // 	POST /v2/documents:annotateText
        // one call to rule them all!
        JsonObject root = createPayload(request);

        // add annotation flags to request doc
        JsonObject features = new JsonObject();
        root.add("features", features);
        features.addProperty("extractEntities", true);
        features.addProperty("extractDocumentSentiment", true);
        features.addProperty("classifyText", true);
        features.addProperty("moderateText", true);

        JsonObject responseJson = post(root, "annotateText");

        String languageCode = JsonTools.getAsString(responseJson, "languageCode");
        boolean languageSupported = JsonTools.getAsBoolean(responseJson, "languageSupported");

        Sentiment documentSentiment = Sentiment.parse(responseJson, "documentSentiment");
        SentimentResponse sentimentResponse = SentimentResponse.create(languageCode, languageSupported, documentSentiment);
        List<SentenceSentiment> sentenceSentiments = SentenceSentiment.parseArray(responseJson, "sentences");
        sentimentResponse.addSentences(sentenceSentiments);
        if (persistResponses) {
            try {
                DbLanguage.persistSentimentResponse(request, sentimentResponse);
            } catch (SQLException | NamingException e) {
                throw new RuntimeException(e);
            }
        }

        List<Entity> entities = Entity.parseArray(responseJson, "entities");
        EntitiesResponse entityResponse = EntitiesResponse.create(languageCode, languageSupported, entities);
        if (persistResponses) {
            try {
                DbLanguage.persistEntityResponse(request, entityResponse);
            } catch (SQLException | NamingException e) {
                throw new RuntimeException(e);
            }
        }

        ClassificationResponse classificationResponse = ClassificationResponse.create(responseJson, "moderationCategories");
        ClassificationResponse categoryResponse = ClassificationResponse.create(responseJson, "categories");
        if (persistResponses) {
            try {
                DbLanguage.persistClassificationResponse(request, classificationResponse, NLPService.TYPE_CLASSIFY);
                DbLanguage.persistClassificationResponse(request, categoryResponse, NLPService.TYPE_MODERATE);
            } catch (SQLException | NamingException e) {
                throw new RuntimeException(e);
            }
        }
        return AnnotationResponse.create(languageCode, languageSupported, sentimentResponse, entityResponse, classificationResponse, categoryResponse);
    }


    @Override
    ClassificationResponse classifyText(NLPRequest request) throws IOException {
        // 	POST /v2/documents:classifyText

        JsonObject root = createPayload(request);
        JsonObject responseJson = post(root, "classifyText");

        ClassificationResponse sentimentResponse = ClassificationResponse.create(responseJson, "categories");
        try {
            DbLanguage.persistClassificationResponse(request, sentimentResponse, NLPService.TYPE_CLASSIFY);
        } catch (SQLException | NamingException e) {
            throw new RuntimeException(e);
        }

        return sentimentResponse;

        /*
            curl "https://language.googleapis.com/v2/documents:classifyText" \
              -X POST \
              -H "X-Goog-Api-Key: $GOOGLE_API_KEY" \
              -H "Content-Type: application/json" \
              -d '{"document":{"content":"The rain in Spain stays mainly in the plain.", "type":"PLAIN_TEXT"}}'
        */

        /* response:
            {
              "categories": [
                {
                  "name": "/News/Weather",
                  "confidence": 0.37365577
                }
              ],
              "languageCode": "en",
              "languageSupported": true
            }
         */
    }

    @Override
    ClassificationResponse moderateText(NLPRequest request) throws IOException {
        // 	POST /v2/documents:moderateText

        JsonObject root = createPayload(request);
        JsonObject responseJson = post(root, "moderateText");

        ClassificationResponse classificationResponse = ClassificationResponse.create(responseJson, "moderationCategories");
        try {
            DbLanguage.persistClassificationResponse(request, classificationResponse, NLPService.TYPE_MODERATE);
        } catch (SQLException | NamingException e) {
            throw new RuntimeException(e);
        }

        return classificationResponse;

        /*
            curl "https://language.googleapis.com/v2/documents:moderateText" \
              -X POST \
              -H "X-Goog-Api-Key: $GOOGLE_API_KEY" \
              -H "Content-Type: application/json" \
              -d '{"document":{"content":"The rain in Spain stays mainly in the plain.", "type":"PLAIN_TEXT"}}'          */

        /* response:
            {
              "moderationCategories": [
                {
                  "name": "Toxic",
                  "confidence": 0.014621646
                },
                ......
                {
                  "name": "Legal",
                  "confidence": 0.0029382957
                }
              ],
              "languageCode": "en",
              "languageSupported": true
            }         */
    }

    @Override
    public String getModelName() {
        return "language.googleapis.com";
    }

    private static HttpPost createHttpPost(JsonObject payloadJson, String path) {
        Gson gson = new Gson();
        StringEntity entity = new StringEntity(gson.toJson(payloadJson), ContentType.APPLICATION_JSON);

        HttpPost httpPost = new HttpPost(String.format("%s%s:%s", API_HOST, API_PATH, path));
//        httpPost.setHeader("Authorization", "Bearer " + API_KEY);
        String apiKey = SystemEnv.get("GOOGLE_NPL_API_KEY", null);

        httpPost.setHeader("X-Goog-Api-Key", apiKey);

        //httpPost.setHeader("x-goog-user-project", GOOGLE_CLOUD_PROJECT_ID);
        httpPost.setHeader("User-Agent", "Noi");
        httpPost.setEntity(entity);
        return httpPost;
    }

    private JsonObject post(JsonObject payloadJson, String path) throws IOException {
        System.out.println("GoogleNLPService: posting to " + path);
        Gson g = new Gson();
        System.out.println("payload=" + g.toJson(payloadJson));

        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;

        try {
            client = HttpClients.createDefault();

            HttpPost httpPost = createHttpPost(payloadJson, path);
            response = client.execute(httpPost);

            return parseResponse(response);

        } finally {
            if (response != null) {
                response.close();
            }
            if (client != null) {
                client.close();
            }
        }
    }

    private JsonObject parseResponse(CloseableHttpResponse response) throws IOException {
        String jsonResponse = FileTools.readToString(response.getEntity().getContent());
        System.out.println("response:" + jsonResponse);
        System.out.println("response-code:" + response.getStatusLine().getStatusCode());

        if (response.getStatusLine().getStatusCode() == HttpServletResponse.SC_CREATED ||
                response.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
            return new JsonParser().parse(jsonResponse).getAsJsonObject();
        }

        throw new IllegalStateException("unable to parse service response!");
    }

    public static void main(String[] args) throws IOException {
        AiPrompt prompt = AiPrompt.create(null, "Illustrate a dynamic underwater scene where a Caucasian male diver, radiating playful energy, interacts with a large shark against the backdrop of a beautifully vibrant coral reef. The reef, teeming with multicolored life and marine flora, is blurred to showcase the sense of depth and distance. The diver, equipped with a full set of scuba gear, is neither afraid nor aggressive towards the shark, reinforcing the friendly ambiance. The shark too, seems to be in a playful mood, perhaps surprised by the diver’s nonchalant attitude. Extra care should be taken to not make this scene fearful, but rather joyful and unexpected.");
        NLPRequest request = NLPRequest.create(prompt, null);

        EntitiesResponse entitiesResponse = new GoogleNLPService().analyzeEntities(request);
        System.out.println(entitiesResponse);

        SentimentResponse sentimentResponse = new GoogleNLPService().analyzeSentiment(request);
        System.out.println(sentimentResponse);

        ClassificationResponse classificationResponse = new GoogleNLPService().classifyText(request);
        System.out.println(classificationResponse);

        classificationResponse = new GoogleNLPService().moderateText(request);
        System.out.println(classificationResponse);

        //todo:
        //        AnnotationResponse annotationResponse = new GoogleNLPService().annotateText(request);
//


    }
}
