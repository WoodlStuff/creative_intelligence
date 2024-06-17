package com.noi.image;

import com.google.gson.*;
import com.noi.AiModel;
import com.noi.language.AiPrompt;
import com.noi.requests.NoiRequest;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenAiImageService extends AiImageService {

    private static final Map<AiModel, String> MODEL_URLS = new HashMap<>();

    static {
        MODEL_URLS.put(AiModel.GPT_3, "https://api.openai.com/v1/chat/completions");
        MODEL_URLS.put(AiModel.GPT_4, "https://api.openai.com/v1/chat/completions");
        MODEL_URLS.put(AiModel.DALL_E_3, "https://api.openai.com/v1/images/generations");
        MODEL_URLS.put(AiModel.DALL_E_2, "https://api.openai.com/v1/images/generations");
    }

    private String API_KEY = null;

    protected OpenAiImageService(String modelName) {
        super(modelName);
        // @Note! in $TOMCAT_HOME/bin/setenv.sh add export OPENAI_API_KEY=xxx
        API_KEY = SystemEnv.get("OPENAI_API_KEY", null);
    }

    @Override
    public AiImageResponse generateImages(AiImageRequest request) throws IOException {
        // http post to api endpoint
        return post(request);
    }

    private AiImageResponse post(AiImageRequest request) throws IOException {
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;

        List<AiImage> images = new ArrayList<>();

        try {
            client = HttpClients.createDefault();

            HttpPost httpPost = createHttpPost(request, request.getPrompt());
            response = client.execute(httpPost);

            AiImage img = parseResponse(request, response);
            if (img != null) {
                images.add(img);
            }

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

    private HttpPost createHttpPost(NoiRequest request, AiPrompt prompt) {
        String modelUrl = getModelUrl(request.getModel());
        System.out.println("posting to " + modelUrl + ": " + prompt);
        Gson gson = new Gson();
        JsonObject payloadJson = createPayload(request.getModel(), prompt);
        StringEntity entity = new StringEntity(gson.toJson(payloadJson), ContentType.APPLICATION_JSON);

        HttpPost httpPost = new HttpPost(modelUrl);
        httpPost.setHeader("Authorization", "Bearer " + API_KEY);
        httpPost.setHeader("User-Agent", "Noi");
        httpPost.setEntity(entity);
        return httpPost;
    }

    private static String getModelUrl(AiModel aiModel) {
        return MODEL_URLS.get(aiModel);
    }

    private AiImage parseResponse(AiImageRequest request, CloseableHttpResponse response) throws IOException {
        String jsonResponse = FileTools.readToString(response.getEntity().getContent());
        System.out.println("response:" + jsonResponse);
        System.out.println("response-code:" + response.getStatusLine().getStatusCode());

        /*
        response:{  "created": 1712015748,  "data": [    {      "revised_prompt": "A 20-year-old South Asian woman walking along a mountain trail. She is wearing a white crop top, denim shorts, and comfortable hiking boots. In her hands, she is carrying a bouquet of wildflowers she gathered along the way. The sun is shining, casting her silhouette against the breathtaking mountainous landscape behind her. She appears free-spirited and is clearly enjoying the calm tranquility of nature.",      "url": "https://oaidalleapiprodscus.blob.core.windows.net/private/org-AtfHy1Nregs9iAEfy8PvgJik/user-7uuDfNQymHYEcMP8Istj6W2y/img-xk77KKewrqGGMayxehd8YBj9.png?st=2024-04-01T22%3A55%3A48Z&se=2024-04-02T00%3A55%3A48Z&sp=r&sv=2021-08-06&sr=b&rscd=inline&rsct=image/png&skoid=6aaadede-4fb3-4698-a8f6-684d7786b067&sktid=a48cca56-e6da-484e-a814-9c849652bcb3&skt=2024-04-01T20%3A44%3A02Z&ske=2024-04-02T20%3A44%3A02Z&sks=b&skv=2021-08-06&sig=UiFl2eLgKU6OYDgfpc4pbSkrKH2Hjrh8fCtXBytNINg%3D"    }  ]}
        response-code:200
         */

        if (response.getStatusLine().getStatusCode() == HttpServletResponse.SC_CREATED ||
                response.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
            return parseImageDetails(request, jsonResponse);
        }
        return null;
    }

    private static AiImage parseImageDetails(AiImageRequest request, String jsonResponse) {
        JsonObject root = new JsonParser().parse(jsonResponse).getAsJsonObject();
        if (root != null) {
            JsonArray dataArray = root.getAsJsonArray("data");
            if (dataArray != null && !dataArray.isJsonNull() && dataArray.size() > 0) {
                JsonObject element = dataArray.get(0).getAsJsonObject();
                String url = element.get("url").getAsString();
                String revPrompt = null;
                JsonElement revisedPrompt = element.get("revised_prompt");
                if (revisedPrompt != null && !revisedPrompt.isJsonNull()) {
                    revPrompt = revisedPrompt.getAsString();
                }

                return AiImage.create(request, url, revPrompt);
            }
        }
        return null;
    }

    private JsonObject createPayload(AiModel model, AiPrompt prompt) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model.getName());

        if (model.getName().startsWith("gpt-")) {
            /* todo: add:
            "messages": [
                  {
                    "role": "system",
                    "content": "You are a poetic assistant, skilled in explaining complex programming concepts with creative flair."
                  },
                  {
                    "role": "user",
                    "content": "Compose a poem that explains the concept of recursion in programming."
                  }
                ]
             */
        } else if (model.getName().startsWith("dall-")) {
            root.addProperty("prompt", prompt.getPrompt());
            root.addProperty("n", 1);
            root.addProperty("size", "1024x1024");

        } else {
            // todo: new models ...
        }

        return root;
    }

    public static void main(String[] args) {
        String promptText = "this is an image prompt...";
        AiPrompt prompt = AiPrompt.create(null, promptText);
        String modelName = "a model";
        AiImageRequest aiRequest = AiImageRequest.create(prompt, modelName);

//        String jsonResponse = "{  \"created\": 1712016178,  \"data\": [    {      \"revised_prompt\": \"A 20 year old Caucasian woman is seen taking a serene walk in the mountains. She is self-assured and adventurous, attired in a vibrant white crop top that sublimely blends with the surrounding majestic landscape. In her hands, she delicately holds fresh mountain flowers imbued with the colors and essence of the wild. The sun, gently setting behind her, drenches the entire scene in a warm, golden light, intensifying the vision of her tranquil exploration amidst the untamed beauty of nature.\",      \"url\": \"https://oaidalleapiprodscus.blob.core.windows.net/private/org-AtfHy1Nregs9iAEfy8PvgJik/user-7uuDfNQymHYEcMP8Istj6W2y/img-Nwm8k3QaOomvIWQmhrZsOMIa.png?st=2024-04-01T23%3A02%3A58Z&se=2024-04-02T01%3A02%3A58Z&sp=r&sv=2021-08-06&sr=b&rscd=inline&rsct=image/png&skoid=6aaadede-4fb3-4698-a8f6-684d7786b067&sktid=a48cca56-e6da-484e-a814-9c849652bcb3&skt=2024-04-01T20%3A47%3A11Z&ske=2024-04-02T20%3A47%3A11Z&sks=b&skv=2021-08-06&sig=YiRoRqY9clXPNmTdjUm3zJbMPRuHYRU91R56A/ZFcxQ%3D\"    }  ]}";
        String jsonResponse = "{  \"created\": 1712606846,  \"data\": [    {      \"revised_prompt\": \"A centenarian woman of South Asian descent, traversing the rugged terrain of a mountain range. She's in a simple, elegant white dress that softly billows in the high altitude breeze. In her weathered hands, she holds a vibrant bouquet of wildflowers. The scene blends the beauty of nature, the tranquility of the mountains, and the grace of age.\",      \"url\": \"https://oaidalleapiprodscus.blob.core.windows.net/private/org-AtfHy1Nregs9iAEfy8PvgJik/user-7uuDfNQymHYEcMP8Istj6W2y/img-hO1M1O64IrZ5jIpsO9wJd4Bu.png?st=2024-04-08T19%3A07%3A26Z&se=2024-04-08T21%3A07%3A26Z&sp=r&sv=2021-08-06&sr=b&rscd=inline&rsct=image/png&skoid=6aaadede-4fb3-4698-a8f6-684d7786b067&sktid=a48cca56-e6da-484e-a814-9c849652bcb3&skt=2024-04-08T14%3A08%3A09Z&ske=2024-04-09T14%3A08%3A09Z&sks=b&skv=2021-08-06&sig=5msG1u4SMw7UQXMyHg23MdNFNx0WS4BaPjQcIrSOXG8%3D\"    }  ]}";
        AiImage image = parseImageDetails(aiRequest, jsonResponse);
        System.out.println(image);
    }
}
