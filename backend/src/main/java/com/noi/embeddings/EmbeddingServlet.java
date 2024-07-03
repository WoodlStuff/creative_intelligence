package com.noi.embeddings;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.noi.image.AiImage;
import com.noi.image.label.LabelMetaData;
import com.noi.models.DbImage;
import com.noi.models.DbImageLabel;
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
import java.util.List;
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

        //??
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
        Connection con = null;
        try {
            imageId = Long.valueOf(pathTokens[0].trim());

            if (pathTokens.length > 1) {
                categoryName = pathTokens[1].trim();
            }

            con = Model.connectX();

            // look up the image and the label metadata for it
            AiImage image = DbImage.find(con, imageId);
            Map<String, List<LabelMetaData>> metaValues = DbImageLabel.findLabelMetaCategories(con, image);

            EmbeddingService embeddingService = EmbeddingService.getService();
            VectorService vectorService = VectorService.getService();

            // format a json doc with all the category data contained
            JsonObject inputDoc = new JsonObject();
            JsonArray categories = new JsonArray();
            inputDoc.add("categories", categories);
            for (Map.Entry<String, List<LabelMetaData>> cat : metaValues.entrySet()) {
                // if a filter is provided: only the matching category will be added to the response
                if (categoryName == null || categoryName.equalsIgnoreCase(cat.getKey())) {
                    JsonObject category = new JsonObject();
                    categories.add(category);
                    category.addProperty("category", cat.getKey());
                    JsonArray labels = new JsonArray();
                    category.add("labels", labels);
                    for (LabelMetaData meta : cat.getValue()) {
                        JsonObject label = new JsonObject();
                        labels.add(label);
                        label.addProperty("model_name", meta.getModelName());
                        label.addProperty("key", meta.getKey());
                        label.addProperty("value", meta.getValue());
                    }
                }
            }

            System.out.println("EmbeddingServlet posting: " + new Gson().toJson(categories));

            // create the embedding for the resulting doc, and post it to the vector db
            // 'vectors' is an array of {"category":"paralanguage","vector":[....]}, {"category": ...}]
            JsonArray vectors = embeddingService.createEmbeddings(image, categories);
            System.out.println("embedding for " + image + ": size=" + vectors.size());

            // for debug only!
//            JsonObject firstElement = vectors.get(0).getAsJsonObject();
            for (int i = 0; i < vectors.size(); i++) {
                JsonObject object = vectors.get(i).getAsJsonObject();
                System.out.println("embedding for " + object.get("category").getAsString() + ": dimension count=" + object.get("vector").getAsJsonArray().size());
            }

            Map<String, Long> upsertCounts = vectorService.upsert(image, vectors, INDEX_NAME);

            writeResponse(upsertCounts, resp);

        } catch (EmbeddingException | SQLException | NamingException e) {
            throw new ServletException(e);
        } finally {
            Model.close(con);
        }
    }

    private void writeResponse(long upsertCount, HttpServletResponse resp) throws IOException {
        resp.setContentType(ContentType.APPLICATION_JSON.toString());
        PrintWriter out = resp.getWriter();
        out.println("{\"upsertCount\":" + upsertCount + "}");
        out.flush();
    }

    private void writeResponse(Map<String, Long> upsertCounts, HttpServletResponse resp) throws IOException {
        resp.setContentType(ContentType.APPLICATION_JSON.toString());
        PrintWriter out = resp.getWriter();

        JsonObject root = new JsonObject();
        JsonArray a = new JsonArray();
        root.add("upsertCounts", a);
        for (Map.Entry<String, Long> entry : upsertCounts.entrySet()) {
            JsonObject count = new JsonObject();
            a.add(count);
            count.addProperty("category", entry.getKey());
            count.addProperty("upsertCount", entry.getValue());
        }

        out.write(new Gson().toJson(root));
        out.flush();
    }
}
