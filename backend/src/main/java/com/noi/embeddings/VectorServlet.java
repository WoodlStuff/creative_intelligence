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
import java.util.List;

@WebServlet(name = "VectorServlet", urlPatterns = {"/vectors/*"}, loadOnStartup = 0)
public class VectorServlet extends HttpServlet {
    private static final String DEFAULT_CATEGORY = "situation";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // curl -X GET http://localhost:8080/noi-server/vectors/<image-id>/categoryName?videoId=123
        Path path = Path.parse(req);
        if (path.getPathInfo() == null || path.getPathInfo().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // find similar scenes in other videos!
        System.out.println("GET:VectorServlet: " + path);

        String[] pathTokens = path.getPathInfo().split("/");
        if (pathTokens.length == 0) {
            return;
        }

        Long id;
        id = Long.valueOf(pathTokens[0].trim());

        // we need a category to query! (that's a Pinecone limitation!)
        String categoryName = DEFAULT_CATEGORY;
        if (pathTokens.length > 1) {
            categoryName = pathTokens[1].trim();
        }

        boolean sameVideo = false;
        if (req.getParameter("sameVideo") != null) {
            sameVideo = Boolean.valueOf(req.getParameter("sameVideo"));
        }

        Connection con = null;
        try {
            con = Model.connectX();

            // namespace, vector,TopK, metadata: video-id !=
            AiImage image = DbImage.find(con, id);
            QueryMeta queryMeta = QueryMeta.create(image, sameVideo);
            List<VectorMatch> matches = new ArrayList<>();
            // 1) calculate the vector for the image id and category (to use as part of the query)
            EmbeddingService.ImageEmbeddings embeddings = EmbeddingService.getEmbeddings(con, image.getId(), categoryName);
            if (embeddings.hasVectors()) {
                System.out.println("VectorServlet: created embeddings with vector for image=" + image.getId() + " and cat=" + categoryName);
                // 2) use the namespace , the calculated vector, and the video id to query the topK matches
                VectorService vectorService = VectorService.getService();
                matches.addAll(vectorService.querySimilarImages(embeddings, categoryName, queryMeta));
            }
            writeResponse(image, categoryName, queryMeta, matches, resp);

        } catch (SQLException | NamingException | EmbeddingException e) {
            throw new ServletException(e);
        } finally {
            Model.close(con);
        }
    }

    private void writeResponse(AiImage image, String categoryName, QueryMeta queryMeta, List<VectorMatch> matches, HttpServletResponse resp) throws IOException {
        resp.setContentType(ContentType.APPLICATION_JSON.toString());
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        PrintWriter out = resp.getWriter();

        JsonObject root = new JsonObject();
        JsonObject q = new JsonObject();
        root.add("query", q);

        q.addProperty("image_id", image.getId());
        q.addProperty("category_name", categoryName);
        if (queryMeta != null) {
            q.add("query_metadata", queryMeta.toJson());
        }

        JsonArray r = new JsonArray();
        root.add("results", r);

        for (VectorMatch match : matches) {
            JsonObject m = new JsonObject();
            r.add(m);
            m.addProperty("image_id", match.getId());
            m.addProperty("score", match.getScore());
            //m.add("vector", match.getValues());
            m.add("vector_metadata", match.getMeta());
        }

        out.write(new Gson().toJson(root));
        out.flush();
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }
}
