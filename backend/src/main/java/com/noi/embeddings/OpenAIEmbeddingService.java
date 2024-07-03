package com.noi.embeddings;

import com.google.gson.*;
import com.noi.image.AiImage;
import com.noi.tools.FileTools;
import com.noi.tools.SystemEnv;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class OpenAIEmbeddingService extends EmbeddingService {
    public OpenAIEmbeddingService(String apiKey) {
        if (apiKey == null) {
            apiKey = SystemEnv.get("OPENAI_API_KEY", null);
        }
        this.apiKey = apiKey;
    }

    private final String apiKey;

    // models: text-embedding-3-small, text-embedding-3-large, text-embedding-ada-002
    private final String modelName = "text-embedding-3-small";

    private static final String EMBEDDING_URL = "https://api.openai.com/v1/embeddings";

    @Override
    protected JsonArray createEmbedding(AiImage image, JsonObject inputDoc) throws EmbeddingException, IOException {
        // post a doc to the embedding endpoint, and read the returned vector
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;
        try {

            client = HttpClients.createDefault();
            HttpPost httpPost = createHttpPost(apiKey, modelName, image, inputDoc);
            response = client.execute(httpPost);
            return parseResponse(image, response);
        } finally {
            if (response != null) {
                response.close();
            }
            if (client != null) {
                client.close();
            }
        }
    }

    @Override
    public JsonArray createEmbeddings(AiImage image, JsonArray categories) throws EmbeddingException, IOException {
        JsonArray responseArray = new JsonArray();

        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;
        try {

            client = HttpClients.createDefault();
            HttpPost httpPost = createHttpPost(apiKey);

            for (int i = 0; i < categories.size(); i++) {
                JsonObject category = categories.get(i).getAsJsonObject();
                StringEntity entity = createPostEntity(image, category);
                httpPost.setEntity(entity);
                response = client.execute(httpPost);
                JsonArray embeddingArray = parseResponse(image, response);
                if (response != null) {
                    response.close();
                }

                if (embeddingArray.isJsonNull() || embeddingArray.size() <= 0) {
                    continue;// skip it if it's empty
                }

                JsonObject vectorResponse = new JsonObject();
                responseArray.add(vectorResponse);

                vectorResponse.addProperty("category", category.get("category").getAsString());
                vectorResponse.add("vector", embeddingArray);
            }

        } finally {
            if (response != null) {
                response.close();
            }
            if (client != null) {
                client.close();
            }
        }

        return responseArray;
    }

    private JsonArray parseResponse(AiImage image, CloseableHttpResponse response) throws IOException {
        /*
        response:
        {
          "object": "list",
          "data": [
            {
              "object": "embedding",
              "index": 0,
              "embedding": [
                -0.006929283495992422,
                -0.005336422007530928,
                ... (omitted for spacing)
                -4.547132266452536e-05,
                -0.024047505110502243
              ],
            }
          ],
          "model": "text-embedding-3-small",
          "usage": {
            "prompt_tokens": 5,
            "total_tokens": 5
          }
        }
         */
        String jsonResponse = FileTools.readToString(response.getEntity().getContent());
        System.out.println("response:" + jsonResponse);
        System.out.println("response-code:" + response.getStatusLine().getStatusCode());

        if (response.getStatusLine().getStatusCode() == HttpServletResponse.SC_CREATED ||
                response.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
            JsonObject root = new JsonParser().parse(jsonResponse).getAsJsonObject();
            if (root != null) {
                JsonArray dataArray = root.getAsJsonArray("data");
                if (dataArray != null && !dataArray.isJsonNull() && dataArray.size() > 0) {
                    JsonElement embedding = dataArray.get(0).getAsJsonObject().get("embedding");
                    if (embedding != null && !embedding.isJsonNull() && embedding.isJsonArray()) {
                        return embedding.getAsJsonArray();
                    }
                }
            }
        } else {
            System.out.println("WARNING: unexpected http return code: " + response.getStatusLine().getStatusCode());
        }

        // default: empty
        return new JsonArray();
    }

    private HttpPost createHttpPost(String apiKey, String modelName, AiImage image, JsonObject inputDoc) throws IOException {
        System.out.println("posting to " + EMBEDDING_URL + ": " + image);

        /*
                curl https://api.openai.com/v1/embeddings \
          -H "Content-Type: application/json" \
          -H "Authorization: Bearer $OPENAI_API_KEY" \
          -d '{
            "input": "{\"function\":\"depict a domestic or preparatory activity in a household\"}",
            "model": "text-embedding-3-small"
          }'
         */
        JsonObject payload = createPayload(modelName, image, inputDoc);

        Gson gson = new Gson();
        //AiRequestLogger.logLabelRequest(AiRequestLogger.LABEL, request, payloadJson);

        StringEntity entity = new StringEntity(gson.toJson(payload), ContentType.APPLICATION_JSON);

        HttpPost httpPost = new HttpPost(EMBEDDING_URL);
        httpPost.setHeader("Authorization", "Bearer " + apiKey);
        httpPost.setHeader("User-Agent", "Noi");
        httpPost.setEntity(entity);
        return httpPost;
    }

    private HttpPost createHttpPost(String apiKey) {
        HttpPost httpPost = new HttpPost(EMBEDDING_URL);
        httpPost.setHeader("Authorization", "Bearer " + apiKey);
        httpPost.setHeader("User-Agent", "Noi");
        return httpPost;
    }

    private StringEntity createPostEntity(AiImage image, JsonObject categoryObject) {
        JsonObject payload = createPayload(modelName, image, categoryObject);
        return new StringEntity(new Gson().toJson(payload), ContentType.APPLICATION_JSON);
    }

    private JsonObject createPayload(String modelName, AiImage image, JsonObject categoryObject) {
        JsonObject root = new JsonObject();
        // todo: can we send json here, or do we need to flatten this to a string?
        root.addProperty("input", new Gson().toJson(categoryObject));
        root.addProperty("model", modelName);
        /*
        {
            "input": "{\"function\":\"depict a domestic or preparatory activity in a household\"}",
            "model": "text-embedding-3-small"
         }
         */
        return root;
    }
}
