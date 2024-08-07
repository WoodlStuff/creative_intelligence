package com.noi.embeddings;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.noi.image.AiImage;
import com.noi.models.DbImage;
import com.noi.models.Model;
import com.noi.web.Path;
import org.apache.http.entity.ContentType;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "EmbeddingServlet", urlPatterns = {"/embeddings/*", "/video-embeddings/*"}, loadOnStartup = 0)
public class EmbeddingServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Path path = Path.parse(req);
        if (path.getPathInfo() == null || path.getPathInfo().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        System.out.println("GET:EmbeddingServlet: " + path);

        String[] pathTokens = path.getPathInfo().split("/");
        if (pathTokens.length == 0) {
            return;
        }

        //servletPath='embeddings' | video-embeddings
        boolean isVideo = false;
        if ("video-embeddings".equalsIgnoreCase(path.getServletPath())) {
            //we want all the relevant images for the video!
            isVideo = true;
        }

        Long id;
        String categoryName = null;
        id = Long.valueOf(pathTokens[0].trim());

        if (pathTokens.length > 1) {
            categoryName = pathTokens[1].trim();
        }

        Connection con = null;
        try {
            con = Model.connectX();

            List<AiImage> images = new ArrayList<>();
            if (isVideo) {
                images.addAll(DbImage.findVideoSummaryScenes(con, id));
            } else {
                images.add(DbImage.find(con, id));
            }

            Map<Long, EmbeddingService.ImageEmbeddings> imageEmbeddings = new HashMap<>();
            for (AiImage image : images) {
                imageEmbeddings.put(image.getId(), EmbeddingService.getEmbeddings(con, image.getId(), categoryName, true));
            }

            writeEmbeddingResponse(imageEmbeddings, resp);

        } catch (EmbeddingException | SQLException | NamingException e) {
            throw new ServletException(e);
        } finally {
            Model.close(con);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // create embeddings for one image (and optional only one category)
        // curl -X POST http://localhost:8080/noi-server/embeddings/(image-id)[/category-name]
        // create embeddings for all images in the video (all images used to generate the summary!)
        // curl -X POST http://localhost:8080/noi-server/video-embeddings/(video-id)[/category-name]
        Path path = Path.parse(req);
        if (path.getPathInfo() == null || path.getPathInfo().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        System.out.println("POST:EmbeddingServlet: " + path);

        String[] pathTokens = path.getPathInfo().split("/");
        if (pathTokens.length == 0) {
            return;
        }

        //servletPath='embeddings' | video-embeddings
        boolean isVideo = false;
        if ("video-embeddings".equalsIgnoreCase(path.getServletPath())) {
            //we want all the relevant images for the video!
            isVideo = true;
        }

        Long id;
        String categoryName = null;
        id = Long.valueOf(pathTokens[0].trim());

        if (pathTokens.length > 1) {
            categoryName = pathTokens[1].trim();
        }

        Connection con = null;
        try {
            con = Model.connectX();

            List<AiImage> images = new ArrayList<>();
            if (isVideo) {
                images.addAll(DbImage.findVideoSummaryScenes(con, id));
            } else {
                images.add(DbImage.find(con, id));
            }

            System.out.println("EmbeddingServlet: processing " + images.size() + " images ...");

            Map<Long, Map<String, Integer>> upsertCounts = new HashMap<>();
            for (AiImage image : images) {
                EmbeddingService.ImageEmbeddings embeddings = EmbeddingService.getEmbeddings(con, image.getId(), categoryName);
                if (embeddings.hasVectors()) {
                    VectorService vectorService = VectorService.getService();
                    Map<String, Integer> imageCategoryCounts = vectorService.upsert(con, embeddings, VectorService.INDEX_NAME);
                    upsertCounts.put(image.getId(), imageCategoryCounts);
                }
            }
            System.out.println("EmbeddingServlet: DONE processing " + images.size() + " images ...");

            writeResponse(upsertCounts, resp);

        } catch (EmbeddingException | SQLException | NamingException e) {
            throw new ServletException(e);
        } finally {
            Model.close(con);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    /**
     * write response to a GET request.
     *
     * @param imageEmbeddings
     * @param resp
     * @throws IOException
     */
    private void writeEmbeddingResponse(Map<Long, EmbeddingService.ImageEmbeddings> imageEmbeddings, HttpServletResponse resp) throws IOException {
        // we haven't written to the vector db, so we only have the raw input for that.
        resp.setContentType(ContentType.APPLICATION_JSON.toString());
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        PrintWriter out = resp.getWriter();

        JsonObject root = new JsonObject();
        JsonArray images = new JsonArray();
        root.add("image_embeddings", images);
        for (Map.Entry<Long, EmbeddingService.ImageEmbeddings> entry : imageEmbeddings.entrySet()) {
            JsonObject emb = new JsonObject();
            images.add(emb);
            EmbeddingService.ImageEmbeddings embeddings = entry.getValue();
            emb.addProperty("image_id", entry.getKey());
            emb.addProperty("category_name", embeddings.getCategoryName());
            emb.add("categories", embeddings.getCategories());
            emb.add("vectors", embeddings.getVectors());
        }

        out.write(new Gson().toJson(root));
        out.flush();
    }

    /**
     * write response to a POST request.
     *
     * @param upsertCounts upsert counts by image id and category (==namespace)
     * @param resp
     * @throws IOException
     */
    private void writeResponse(Map<Long, Map<String, Integer>> upsertCounts, HttpServletResponse resp) throws IOException {
        resp.setContentType(ContentType.APPLICATION_JSON.toString());
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        PrintWriter out = resp.getWriter();

        JsonObject root = new JsonObject();
        JsonArray a = new JsonArray();
        root.add("upsertCounts", a);
        for (Map.Entry<Long, Map<String, Integer>> image : upsertCounts.entrySet()) {
            for (Map.Entry<String, Integer> entry : image.getValue().entrySet()) {
                JsonObject count = new JsonObject();
                a.add(count);
                count.addProperty("image_id", image.getKey());
                count.addProperty("category", entry.getKey());
                count.addProperty("upsertCount", entry.getValue());
            }
        }

        out.write(new Gson().toJson(root));
        out.flush();
    }
}
