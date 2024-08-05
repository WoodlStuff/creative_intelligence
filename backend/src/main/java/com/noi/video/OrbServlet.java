package com.noi.video;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noi.models.DbVideo;
import com.noi.models.Model;
import com.noi.tools.FileTools;
import com.noi.tools.JsonTools;
import com.noi.tools.SystemEnv;
import com.noi.video.scenes.ORBService;
import com.noi.video.scenes.VideoService;
import com.noi.web.BaseControllerServlet;
import com.noi.web.Path;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;


@WebServlet(name = "ORBServlet", urlPatterns = {"/orb/*", "/orbX/*"}, loadOnStartup = 0)
public class OrbServlet extends BaseControllerServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // posted data: {"video_id": 123, "video_name": videoData[0].name, "refresh": true, "llm": false, "maxSimilarityDistance": similarityDistance, "sceneChangeScoreThreshold": scoreThreshold}
        // read the posted json doc
        JsonObject postedData = readPostedJson(req);

        // add the root video path to what we send to the ORB script
        String rootPath = SystemEnv.get("NOI_PATH", null);
        File rootVideoPath = FileTools.joinPath(rootPath, "videos", null);
        postedData.addProperty("path", rootVideoPath.getAbsolutePath());

        final String orbURL = "http://localhost:8000/video";

        boolean refresh = JsonTools.getAsBoolean(postedData, "refresh", false);
        JsonObject orbResponseJson;
        if (refresh) {
            // call the py server (on port 3000), parse and store the response
            try {
                orbResponseJson = postToOrbHost(orbURL, postedData);
            } catch (IOException e) {
                // if the server is not running, look for a file to read for the response content...?
                System.out.printf("ORBServlet:unable to call remote endpoint [%s]; attempt to read local file ...", orbURL);
                orbResponseJson = readOrbFile(req, postedData);
            }
        } else {
            orbResponseJson = readOrbFile(req, postedData);
        }

        Path path = Path.parse(req);
        if ("orb".equals(path.getServletPath())) {
            // we want the raw ORB data
            writeResponse(resp, orbResponseJson);
            return;

        } else if ("orbX".equals(path.getServletPath())) {
            // we want the parsed (and stored) ORB data
            Connection con = null;
            try {
                con = Model.connectX();

                Long videoId = null;
                if (postedData.has("video_id")) {
                    videoId = postedData.get("video_id").getAsLong();
                } else if (postedData.has("video_name")) {
                    AiVideo video = DbVideo.find(con, postedData.get("video_name").getAsString());
                    if (video == null) {
                        throw new IllegalArgumentException();
                    }
                    videoId = video.getId();
                }

                // update the db with the new scene changes
                if (refresh) {
                    ORBService.handleORBScenePost(con, videoId, orbResponseJson);
                }

                // read the updated state and format it as json for the frontend
                JsonObject responseJson = VideoService.readAndFormatVideoResponse(con, videoId);
                // send json with the results
                writeResponse(resp, responseJson);
            } catch (SQLException | NamingException e) {
                throw new RuntimeException(e);
            } finally {
                Model.close(con);
            }
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
