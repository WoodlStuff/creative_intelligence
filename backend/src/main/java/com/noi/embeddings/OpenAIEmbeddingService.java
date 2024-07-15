package com.noi.embeddings;

import com.google.gson.*;
import com.noi.image.AiImage;
import com.noi.models.DbImageLabel;
import com.noi.models.DbRequest;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

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

    /**
     * @param con
     * @param image
     * @param categories
     * @return 'vectors': an array of {"category":"paralanguage","vector":[0.09, 0.12, ....]}, {"category": ...}]
     * @throws EmbeddingException
     * @throws IOException
     */
    @Override
    public JsonArray createEmbeddings(Connection con, AiImage image, JsonArray categories) throws EmbeddingException, IOException {
        JsonArray responseArray = new JsonArray();

        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;
        try {

            client = HttpClients.createDefault();
            HttpPost httpPost = createHttpPost(apiKey);

            Map<String, Long> imageCategories = DbImageLabel.findAllCategories(con);

            // loop the json array (of category documents)
            for (int i = 0; i < categories.size(); i++) {
                JsonObject category = categories.get(i).getAsJsonObject();
                String categoryName = category.get("category").getAsString();
                // log the embedding request!
                Long categoryId = imageCategories.get(categoryName);
                AiImageEmbeddingRequest request = DbRequest.insertForEmbedding(con, image, categoryId, modelName);

                StringEntity entity = createPostEntity(image, category);
                httpPost.setEntity(entity);
                JsonArray embeddingArray = null;
                response = client.execute(httpPost);
                if (response != null) {
                    embeddingArray = parseResponse(image, response);
                    response.close();

                    // update request log with vector dimensions
                    DbRequest.updateForEmbedding(con, request, embeddingArray.size());
                    if (embeddingArray.size() <= 0) {
                        continue;// skip it if it's empty
                    }
                }

                JsonObject vectorResponse = new JsonObject();
                responseArray.add(vectorResponse);

                vectorResponse.addProperty("category", categoryName);
                vectorResponse.addProperty("model", modelName);
                vectorResponse.add("vector", embeddingArray);
            }
        } catch (SQLException e) {
            throw new EmbeddingException(e);
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
        String jsonResponse = FileTools.readToString(response.getEntity().getContent());
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
        root.addProperty("input", new Gson().toJson(categoryObject));
        root.addProperty("model", modelName);
        return root;
    }
}
