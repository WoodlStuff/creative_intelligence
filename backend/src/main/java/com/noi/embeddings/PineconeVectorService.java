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
import java.util.HashMap;
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

    public PineconeVectorService(String apiKey) {
        super();
        if (apiKey == null) {
            apiKey = SystemEnv.get("PINECONE_API_KEY", null);
        }
        this.apiKey = apiKey;
    }

    @Override
    protected long upsert(AiImage image, JsonArray vector, String indexName, String nameSpace) throws EmbeddingException {
        // todo

        /*
        create the index:
        @see https://docs.pinecone.io/guides/indexes/create-an-index

        curl -s -X POST "https://api.pinecone.io/indexes" \
          -H "Accept: application/json" \
          -H "Content-Type: application/json" \
          -H "Api-Key: $PINECONE_API_KEY" \
          -d '{
                 "name": "serverless-index",
                 "dimension": 1024,
                 "metric": "cosine",
                 "spec": {
                    "serverless": {
                       "cloud": "aws",
                       "region": "us-east-1"
                    }
                 }
              }'

         -- response:
         {"name":"serverless-index","metric":"cosine","dimension":1024,"status":{"ready":false,"state":"Initializing"},"host":"serverless-index-0ee2lb1.svc.aped-4627-b74a.pinecone.io","spec":{"serverless":{"region":"us-east-1","cloud":"aws"}}}
         */


        return 0;
    }

    @Override
    protected Map<String, Long> upsert(AiImage image, JsonArray vectors, String indexName) throws EmbeddingException, IOException {
        // insert vectors into the indexName, each vector element contains a category name and an array of double
        Map<String, Long> categoryUpsertCounts = new HashMap<>();

        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;
        try {
            client = HttpClients.createDefault();

            String url = getIndexHostURL(indexName);
            HttpPost httpPost = createHttpPost(url, apiKey);

            for (int i = 0; i < vectors.size(); i++) {
                JsonObject categoryEmbeddings = vectors.get(i).getAsJsonObject();
                String category = categoryEmbeddings.get("category").getAsString();
                JsonArray vector = categoryEmbeddings.get("vector").getAsJsonArray();

                // todo: post it
                // namespace = category name
                // id = image id
                // vector = vectors for that category


                /*
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
                 */

                StringEntity entity = createPostEntity(image, category, vector);
                httpPost.setEntity(entity);
                response = client.execute(httpPost);
                long upsertCount = parseResponse(response);
                categoryUpsertCounts.put(category, upsertCount);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
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

    private long parseResponse(CloseableHttpResponse response) throws IOException {
//        {"upsertedCount":3}
        String jsonResponse = FileTools.readToString(response.getEntity().getContent());
        System.out.println("response:" + jsonResponse);
        System.out.println("response-code:" + response.getStatusLine().getStatusCode());

        if (response.getStatusLine().getStatusCode() == HttpServletResponse.SC_CREATED ||
                response.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
            JsonObject root = new JsonParser().parse(jsonResponse).getAsJsonObject();
            if (root != null) {
                JsonElement count = root.get("upsertedCount");
                if (count != null && !count.isJsonNull()) {
                    return count.getAsLong();
                }
            }
        }
        return -1L;
    }

    private String getIndexHostURL(String indexName) {
        // response from index create:
        /// {"name":"categories","metric":"cosine","dimension":1536,"status":{"ready":false,"state":"Initializing"},"host":"categories-0ee2lb1.svc.aped-4627-b74a.pinecone.io","spec":{"serverless":{"region":"us-east-1","cloud":"aws"}}}%
        String hostName = "categories-0ee2lb1.svc.aped-4627-b74a.pinecone.io"; // todo: lookup, based on index name
        return String.format("https://%s/vectors/upsert", hostName);
    }

    private HttpPost createHttpPost(String url, String apiKey) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Api-Key", apiKey);
        httpPost.setHeader("User-Agent", "Noi");
        return httpPost;
    }

    private StringEntity createPostEntity(AiImage image, String category, JsonArray vector) {
        JsonObject payload = createPayload(image, category, vector);
        return new StringEntity(new Gson().toJson(payload), ContentType.APPLICATION_JSON);
    }

    private JsonObject createPayload(AiImage image, String category, JsonArray vector) {
        JsonObject root = new JsonObject();
        root.addProperty("namespace", category);
        JsonArray vectors = new JsonArray();
        root.add("vectors", vectors);
        JsonObject v = new JsonObject();
        vectors.add(v);

        v.addProperty("id", image.getId().toString());
        v.add("values", vector);

        /*
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
         */

        return root;
    }

}
