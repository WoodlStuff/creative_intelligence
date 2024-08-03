package com.noi.video;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noi.tools.FileTools;
import com.noi.tools.JsonTools;
import com.noi.tools.SystemEnv;
import com.noi.web.BaseControllerServlet;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;


@WebServlet(name = "ORBServlet", urlPatterns = {"/orb/*"}, loadOnStartup = 0)
public class OrbServlet extends BaseControllerServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // posted data: {"video_name": videoData[0].name, "refresh": true, "llm": false, "maxSimilarityDistance": similarityDistance, "sceneChangeScoreThreshold": scoreThreshold}
        // read the posted json doc
        JsonObject postedData = readPostedJson(req);

        // add the root video path to what we send to the ORB script
        String rootPath = SystemEnv.get("NOI_PATH", null);
        File rootVideoPath = FileTools.joinPath(rootPath, "videos", null);
        postedData.addProperty("path", rootVideoPath.getAbsolutePath());

        // the python script is local and listens in port 8000
        final String orbURL = "http://localhost:8000/video";
        boolean refresh = JsonTools.getAsBoolean(postedData, "refresh", false);
        if (refresh) {
            // call the py server (on port 3000), and proxy the response.
            try {
                JsonObject orbResponseJson = postToOrbHost(orbURL, postedData);
                writeResponse(resp, orbResponseJson);

            } catch (IOException e) {
                // if the server is not running, look for a file to read for the response content...?
                System.out.printf("ORBServlet:unable to call remote endpoint [%s]; attempt to read local file ...", orbURL);
                JsonObject fileResponse = readOrbFile(req, postedData);
                writeResponse(resp, fileResponse);
            }
        } else {
            JsonObject fileResponse = readOrbFile(req, postedData);
            writeResponse(resp, fileResponse);
        }
    }

    private JsonObject readOrbFile(HttpServletRequest req, JsonObject postedData) throws FileNotFoundException {
        System.out.println("ORBServlet:readOrbFile:...");
        // posted data contains the video name (video_name)
        if (!postedData.has("video_name")) {
            throw new IllegalArgumentException("no video name provided!");
        }

        String videoName = postedData.get("video_name").getAsString();
        String rootPath = SystemEnv.get("NOI_PATH", null);
        File videoPath = FileTools.joinPath(rootPath, "videos", videoName, videoName + "-scenes-orb.json");
        System.out.println("ORBServlet:readOrbFile:videoPath=" + videoPath.getAbsolutePath());
        return new JsonParser().parse(new FileReader(videoPath)).getAsJsonObject();
    }

    private HttpPost createHttpPost(String url, String apiKey) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("User-Agent", "Noi");
        if (apiKey != null) {
            httpPost.setHeader("Api-Key", apiKey);
        }
        return httpPost;
    }

    private JsonObject postToOrbHost(String url, JsonObject payload) throws IOException {
        System.out.println("ORBServlet:postToOrbHost:" + url + " with \n" + new Gson().toJson(payload));
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;
        try {
            client = HttpClients.createDefault();

            HttpPost httpPost = createHttpPost(url, null);
            StringEntity entity = new StringEntity(new Gson().toJson(payload), ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);
            response = client.execute(httpPost);
            return readResponse(response);

        } finally {
            if (response != null) {
                response.close();
            }
            if (client != null) {
                client.close();
            }
        }
    }

    private JsonObject readResponse(CloseableHttpResponse response) throws IOException {
        String jsonResponse = FileTools.readToString(response.getEntity().getContent());
        System.out.println("ORBServlet:readResponse:" + jsonResponse);
        if (response.getStatusLine().getStatusCode() == HttpServletResponse.SC_CREATED ||
                response.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
            return new JsonParser().parse(jsonResponse).getAsJsonObject();
        }

        return new JsonObject();
    }
}
