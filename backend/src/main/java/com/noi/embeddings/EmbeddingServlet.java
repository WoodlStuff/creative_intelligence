package com.noi.embeddings;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = "EmbeddingServlet", urlPatterns = {"/embeddings/*"}, loadOnStartup = 0)
public class EmbeddingServlet extends HttpServlet {
    private static final String INDEX_NAME = "categories";

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

        Long imageId;
        String categoryName = null;
        imageId = Long.valueOf(pathTokens[0].trim());

        if (pathTokens.length > 1) {
            categoryName = pathTokens[1].trim();
        }


        Connection con = null;
        try {
            con = Model.connectX();
            EmbeddingService.ImageEmbeddings embeddings = EmbeddingService.getEmbeddings(con, imageId, categoryName);
            writeResponse(embeddings, resp);

        } catch (EmbeddingException | SQLException | NamingException e) {
            throw new ServletException(e);
        } finally {
            Model.close(con);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // curl -X POST http://localhost:8080/noi-server/embeddings/(image-id)[/category-name]
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

        Long imageId;
        String categoryName = null;
        imageId = Long.valueOf(pathTokens[0].trim());

        if (pathTokens.length > 1) {
            categoryName = pathTokens[1].trim();
        }


        Connection con = null;
        try {
            con = Model.connectX();
            EmbeddingService.ImageEmbeddings embeddings = EmbeddingService.getEmbeddings(con, imageId, categoryName);

            Map<String, Integer> upsertCounts = new HashMap<>();
            if (embeddings.hasVectors()) {
                VectorService vectorService = VectorService.getService();
                upsertCounts.putAll(vectorService.upsert(con, embeddings, INDEX_NAME));
            }
            writeResponse(upsertCounts, resp);

        } catch (EmbeddingException | SQLException | NamingException e) {
            throw new ServletException(e);
        } finally {
            Model.close(con);
        }
    }

    private void writeResponse(EmbeddingService.ImageEmbeddings embeddings, HttpServletResponse resp) throws IOException {
        // we haven't written to the vector db, so we only have the raw input for that.
        resp.setContentType(ContentType.APPLICATION_JSON.toString());
        PrintWriter out = resp.getWriter();

        JsonObject root = new JsonObject();
        root.addProperty("image-id", embeddings.getImage().getId());
        root.addProperty("category-name", embeddings.getCategoryName());
        root.add("categories", embeddings.getCategories());
        root.add("vectors", embeddings.getVectors());

        out.write(new Gson().toJson(root));
        out.flush();
    }

    private void writeResponse(Map<String, Integer> upsertCounts, HttpServletResponse resp) throws IOException {
        resp.setContentType(ContentType.APPLICATION_JSON.toString());
        PrintWriter out = resp.getWriter();

        JsonObject root = new JsonObject();
        JsonArray a = new JsonArray();
        root.add("upsertCounts", a);
        for (Map.Entry<String, Integer> entry : upsertCounts.entrySet()) {
            JsonObject count = new JsonObject();
            a.add(count);
            count.addProperty("category", entry.getKey());
            count.addProperty("upsertCount", entry.getValue());
        }

        out.write(new Gson().toJson(root));
        out.flush();
    }
}
