package com.noi.embeddings;

import com.google.gson.*;
import com.noi.image.AiImage;
import com.noi.models.DbImageLabel;
import com.noi.models.DbRequest;
import com.noi.tools.FileTools;
import com.noi.tools.SystemEnv;
import com.noi.video.AiVideo;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
-- create the index
curl -s -X POST "https://api.pinecone.io/indexes" \
          -H "Accept: application/json" \
          -H "Content-Type: application/json" \
          -H "Api-Key: $PINECONE_API_KEY" \
          -d '{
                 "name": "categories",
                 "dimension": 1536,
                 "metric": "cosine",
                 "spec": {
                    "serverless": {
                       "cloud": "aws",
                       "region": "us-east-1"
                    }
                 }
              }'

 -- Pinecone console:
 https://app.pinecone.io/organizations/-O0jdg0jZdUdOAa8Bz4Y/projects/9ea6c379-a2c8-4f9b-b5ab-03c203a942cb/indexes/categories/namespaces
 */
public class PineconeVectorService extends VectorService {
    private final String apiKey;

    private final String MODEL_NAME = "Pinecone";

    public PineconeVectorService(String apiKey) {
        super();
        if (apiKey == null) {
            apiKey = SystemEnv.get("PINECONE_API_KEY", null);
        }
        this.apiKey = apiKey;
    }

    private Map<String, Integer> upsert(Connection con, AiVideo video, AiImage image, JsonArray vectors, String indexName) throws EmbeddingException, IOException {
        // insert vectors into the indexName, each vector element contains a category name and an array of double
        Map<String, Integer> categoryUpsertCounts = new HashMap<>();

        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;
        try {
            client = HttpClients.createDefault();

            String hostName = getIndexHostURL(indexName);
            String postUrl = String.format("https://%s/vectors/upsert", hostName);
            HttpPost httpPost = createHttpPost(postUrl, apiKey);

            Map<String, Long> imageCategories = DbImageLabel.findAllCategories(con);

            for (int i = 0; i < vectors.size(); i++) {
                JsonObject categoryEmbeddings = vectors.get(i).getAsJsonObject();
                String category = categoryEmbeddings.get("category").getAsString();
                JsonArray vector = categoryEmbeddings.get("vector").getAsJsonArray();

                // log the request
                Long categoryId = imageCategories.get(category);
                AiImageVectorRequest request = DbRequest.insertForVector(con, image, categoryId, MODEL_NAME, vector.size());

                StringEntity entity = createUpsertEntity(video, image, category, vector);
                httpPost.setEntity(entity);
                response = client.execute(httpPost);

                int upsertCount = parseUpsertResponse(response);
                DbRequest.updateForVector(con, request, upsertCount);

                categoryUpsertCounts.put(category, upsertCount);
            }
        } catch (IOException | SQLException e) {
            throw new EmbeddingException(e);
        } finally {
            if (response != null) {
                response.close();
            }
            if (client != null) {
                client.close();
            }
        }

                /*
        insert a new row
        curl -X POST "https://$INDEX_HOST/vectors/upsert" \
          -H "Api-Key: $PINECONE_API_KEY" \
          -H 'Content-Type: application/json' \
          -d '{
            "vectors": [
              {
                "id": "vec1",
                "values": [1.0, -2.5]
              },
              {
                "id": "vec2",
                "values": [3.0, -2.0]
              },
              {
                "id": "vec3",
                "values": [0.5, -1.5]
              }
            ],
            "namespace": "ns2"
          }'

          response:
          {"upsertedCount":3}
         */

        return categoryUpsertCounts;
    }

    @Override
    public Map<String, Integer> upsert(Connection con, EmbeddingService.ImageEmbeddings embeddings, String indexName) throws EmbeddingException, IOException {
        return upsert(con, embeddings.getVideo(), embeddings.getImage(), embeddings.getVectors(), indexName);
    }

    @Override
    protected List<VectorMatch> querySimilarImages(EmbeddingService.ImageEmbeddings embeddings, String indexName, QueryMeta queryMeta) throws EmbeddingException, IOException {
        // query the vector db for matches in this category

        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;
        try {
            client = HttpClients.createDefault();

            String hostName = getIndexHostURL(indexName);
            String postUrl = String.format("https://%s/query", hostName);
            HttpPost httpPost = createHttpPost(postUrl, apiKey);

            StringEntity entity = createQueryEntity(embeddings, indexName, queryMeta);
            httpPost.setEntity(entity);
            response = client.execute(httpPost);

            return parseQueryResponse(response);

        } finally {
            if (response != null) {
                response.close();
            }
            if (client != null) {
                client.close();
            }
        }
    }

    private int parseUpsertResponse(CloseableHttpResponse response) throws IOException {
        String jsonResponse = FileTools.readToString(response.getEntity().getContent());
        if (response.getStatusLine().getStatusCode() == HttpServletResponse.SC_CREATED ||
                response.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
            JsonObject root = new JsonParser().parse(jsonResponse).getAsJsonObject();
            if (root != null) {
                JsonElement count = root.get("upsertedCount");
                if (count != null && !count.isJsonNull()) {
                    return count.getAsInt();
                }
            }
        }
        return -1;
    }

    private List<VectorMatch> parseQueryResponse(CloseableHttpResponse response) throws IOException {
        List<VectorMatch> vectorMatches = new ArrayList<>();

        String jsonResponse = FileTools.readToString(response.getEntity().getContent());
        System.out.println("PineconeVectorService:httpResponse:" + response.getStatusLine().getStatusCode());
        System.out.println("PineconeVectorService:httpResponse:" + jsonResponse);
        if (response.getStatusLine().getStatusCode() == HttpServletResponse.SC_CREATED ||
                response.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
            JsonObject root = new JsonParser().parse(jsonResponse).getAsJsonObject();
            if (root != null) {
                JsonArray matches = root.get("matches").getAsJsonArray();
                for (int i = 0; i < matches.size(); i++) {
                    JsonObject match = matches.get(i).getAsJsonObject();
                    vectorMatches.add(VectorMatch.parse(match));
                }
            }
        }
        return vectorMatches;
    }

    private String getIndexHostURL(String indexName) {
        // response from index create:
        /// {"name":"categories","metric":"cosine","dimension":1536,"status":{"ready":false,"state":"Initializing"},"host":"categories-0ee2lb1.svc.aped-4627-b74a.pinecone.io","spec":{"serverless":{"region":"us-east-1","cloud":"aws"}}}%
        String hostName = "categories-0ee2lb1.svc.aped-4627-b74a.pinecone.io"; // todo: lookup, based on index name
//        return String.format("https://%s/vectors/upsert", hostName);
        return hostName;
    }

    private HttpPost createHttpPost(String url, String apiKey) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Api-Key", apiKey);
        httpPost.setHeader("User-Agent", "Noi");
        return httpPost;
    }

    private StringEntity createUpsertEntity(AiVideo video, AiImage image, String category, JsonArray vector) {
        JsonObject payload = createUpsertPayload(video, image, category, vector);
        return new StringEntity(new Gson().toJson(payload), ContentType.APPLICATION_JSON);
    }

    private JsonObject createUpsertPayload(AiVideo video, AiImage image, String category, JsonArray vector) {
        JsonObject root = new JsonObject();
        root.addProperty("namespace", category);
        JsonArray vectors = new JsonArray();
        root.add("vectors", vectors);
        JsonObject v = new JsonObject();
        vectors.add(v);

        v.addProperty("id", image.getId().toString());
        v.add("values", vector);

        // if this image is part of a video, add the video metadata for future query
        if (video != null) {
            JsonObject metaData = new JsonObject();
            v.add("metadata", metaData);
            metaData.addProperty("video_id", video.getId());
            metaData.addProperty("frame_number", image.getVideoFrameNumber());
            double seconds = image.getVideoFrameNumber() / video.getFrameRate();
            metaData.addProperty("frame_seconds", Math.round(seconds));
            metaData.addProperty("video_frameCount", video.getFrameCount());
            metaData.addProperty("video_seconds", video.getSeconds());
        }

        return root;
    }

    private StringEntity createQueryEntity(EmbeddingService.ImageEmbeddings embeddings, String indexName, QueryMeta queryMeta) {
        JsonObject payload = createQueryPayload(embeddings, indexName, queryMeta);
        System.out.println("PineconeVectorService:query with: \r\n" + new Gson().toJson(payload));
        return new StringEntity(new Gson().toJson(payload), ContentType.APPLICATION_JSON);
    }

    private JsonObject createQueryPayload(EmbeddingService.ImageEmbeddings embeddings, String indexName, QueryMeta queryMeta) {
        JsonObject root = new JsonObject();
        // vectors is array of {"category":"paralanguage","vector":[....]}, {"category": ...}]
        JsonArray vectors = embeddings.getVectors();
        JsonArray vector = null;
        for (int i = 0; i < vectors.size(); i++) {
            JsonObject categoryVector = vectors.get(i).getAsJsonObject();
            if (indexName.equalsIgnoreCase(categoryVector.get("category").getAsString())) {
                // found it!
                vector = categoryVector.get("vector").getAsJsonArray();
                break;
            }
        }

        if (vector == null) {
            throw new IllegalArgumentException("no category found for index[" + indexName + "]");
        }

        root.addProperty("namespace", indexName);
        root.add("vector", vector);
        root.addProperty("topK", 3);

        //?? does this depend on weather or not we send a filter?
        root.addProperty("includeMetadata", true);

        // if a video id is provided, we are looking for images not in this video (from other videos)!
        if (queryMeta.getVideoId() != null) {
            // "filter": {"genre": {"$in": ["comedy", "documentary", "drama"]}},
            JsonObject filter = new JsonObject();
            root.add("filter", filter);
            JsonObject filterClause = new JsonObject();
            if (queryMeta.isSameVideo()) {
                filterClause.addProperty("$eq", queryMeta.getVideoId());
            } else {
                filterClause.addProperty("$ne", queryMeta.getVideoId());
            }
            filter.add("video_id", filterClause);
        }

        return root;
    }

}
