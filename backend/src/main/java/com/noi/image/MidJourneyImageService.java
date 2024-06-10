package com.noi.image;

import com.google.gson.*;
import com.noi.AiModel;
import com.noi.language.AiPrompt;
import com.noi.requests.NoiRequest;
import com.noi.tools.FileTools;
import com.noi.tools.JsonTools;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * see API docs at https://docs.imagineapi.dev/aypeeeye/image
 */
public class MidJourneyImageService extends AiImageService {

    private static String API_KEY = "??? TODO ???";
    private static String HOST = "https://cl.imagineapi.dev/items/images";

    protected MidJourneyImageService(String modelName) {
        super(modelName);
    }

    @Override
    public AiImageResponse generateImages(AiImageRequest request) throws IOException {
        // http post to api endpoint
        return post(request);
    }

    private AiImageResponse post(AiImageRequest request) throws IOException {
        List<AiImage> images = new ArrayList<>();

        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;

        try {

            client = HttpClients.createDefault();

//            for (Map.Entry<Integer, String> entry : request.getPrompt().entrySet()) {
            String prompt = request.getPrompt().getPrompt();
            System.out.println("posting to " + HOST + ": " + prompt);
            HttpPost httpPost = createHttpPost(request, prompt);
            response = client.execute(httpPost);

            // todo: parse id from response, (... and go into wait loop?)
            // note: this API is async and can pull or notify (webhooks)

            images.addAll(parseImageDetails(request, response));
//                AiImage img = parseResponse(prompt, promptIndex, response);
//                if (img != null) {
//                    images.add(img);
//                }
//            }

        } finally {
            if (response != null) {
                response.close();
            }
            if (client != null) {
                client.close();
            }
        }

        return AiImageResponse.create(request, images);
    }

    private HttpPost createHttpPost(NoiRequest request, String prompt) {
        Gson gson = new Gson();
        JsonObject payloadJson = createPayload(request, prompt);
        StringEntity entity = new StringEntity(gson.toJson(payloadJson), ContentType.APPLICATION_JSON);

        HttpPost httpPost = new HttpPost(HOST);
        httpPost.setHeader("Authorization", "Bearer " + API_KEY);
        httpPost.setHeader("User-Agent", "Noi");
        httpPost.setEntity(entity);
        return httpPost;
    }

    private List<AiImage> parseResponse(AiImageRequest request, CloseableHttpResponse response) throws IOException {
        String jsonResponse = FileTools.readToString(response.getEntity().getContent());
        System.out.println("response:" + jsonResponse);
        System.out.println("response-code:" + response.getStatusLine().getStatusCode());

        if (response.getStatusLine().getStatusCode() == HttpServletResponse.SC_CREATED ||
                response.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
            return parseImageDetails(request, jsonResponse);
        }
        return null;
    }

    private static List<AiImage> parseImageDetails(AiImageRequest request, String jsonResponse) throws IOException {
        List<AiImage> images = new ArrayList<>();
            /*
            response:
                {
                    "data": {
                        "id": UUID,
                        "prompt": string,
                        "results": null | string,
                        "user_created": string,
                        "date_created": string,
                        "status": "pending" | "in-progress" | "completed" | "failed",
                        "progress": number | null,
                        "url": null | string,
                        "error": null | string,
                        "upscaled_urls": null | string[],
                        "upscaled": string[],
                        "ref": string | null,
                    }
                }
         */

        JsonObject root = new JsonParser().parse(jsonResponse).getAsJsonObject();
        if (root != null) {
            JsonObject data = JsonTools.getAsObject(root, "data");
            if (data != null && !data.isJsonNull()) {
                String id = JsonTools.getAsString(data, "id");
                String status = JsonTools.getAsString(data, "status");
                JsonArray upscaledUrls = data.getAsJsonArray("upscaled_urls");
                if (upscaledUrls != null && !upscaledUrls.isJsonNull()) {
                    for (int i = 0; i < upscaledUrls.size(); i++) {
                        String url = upscaledUrls.get(i).getAsString();
                        images.add(AiImage.create(request, url, null));
                    }
                }
            }
        }
        return images;
    }

    private static List<AiImage> parseImageDetails(AiImageRequest request, CloseableHttpResponse httpResponse) throws IOException {
        String jsonResponse = FileTools.readToString(httpResponse.getEntity().getContent());

        return parseImageDetails(request, jsonResponse);
    }

    private JsonObject createPayload(NoiRequest request, String prompt) {
        JsonObject root = new JsonObject();
        root.addProperty("prompt", prompt);
        root.addProperty("ref", request.getUUID());
        return root;
    }

    public static void main(String[] args) throws IOException {
        String promptText = "this is an image prompt...";
        AiPrompt prompt = AiPrompt.create(null, promptText);
        // "status": "pending" | "in-progress" | "completed" | "failed"
        String jsonResponse = "{\n" +
                "\"data\": {\n" +
                "    \"id\": \"12345\",\n" +
                "        \"prompt\": \"a prompt\",  \n" +
                "        \"results\": \"????\",  \n" +
                "        \"user_created\": \"hmm\",  \n" +
                "        \"date_created\": \"xxx\",  \n" +
                "        \"status\": \"completed\",  \n" +
                "        \"progress\": null,\n" +
                "        \"url\": null,\n" +
                "        \"error\": null,\n" +
                "        \"upscaled_urls\": [\"http://localhost:8080/image/1\", \"http://localhost:8080/image/2\"],\n" +
                "        \"upscaled\": [],\n" +
                "        \"ref\": \"an id ref\"\n" +
                "        }\n" +
                "}";
        AiImageRequest request = AiImageRequest.create(prompt, AiModel.DALL_E_3.toString());
        List<AiImage> images = parseImageDetails(request, jsonResponse);
        for (AiImage image : images) {
            System.out.println(image);
        }
    }
}
