package com.noi.image;

import com.google.gson.JsonObject;
import com.noi.AiBrand;
import com.noi.Status;
import com.noi.models.DbBrand;
import com.noi.models.DbImage;
import com.noi.models.DbVideo;
import com.noi.models.Model;
import com.noi.tools.SystemEnv;
import com.noi.video.AiVideo;
import com.noi.web.BaseControllerServlet;
import com.noi.web.Path;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

@WebServlet(name = "ImageServlet", urlPatterns = {"/uploadImageFile"}, loadOnStartup = 0)
// Note: location == $NOI_PATH env
@MultipartConfig(location = "/Users/martin/work/tmp/ai-data/images/", fileSizeThreshold = 1024 * 1024 * 40, // limit to 40MB file size?!?
        maxFileSize = 1024 * 1024 * 40, maxRequestSize = 1024 * 1024 * 41)
public class ImageServlet extends BaseControllerServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // post image file to "/Users/martin/work/tmp/ai-data/images/"
        Path path = Path.parse(req);
        String[] pathTokens = new String[]{};
        if (path.getPathInfo() != null && !path.getPathInfo().isEmpty()) {
            pathTokens = path.getPathInfo().split("/");
        }

        System.out.println("ImageServlet:POST: " + path);

        try {
            // accept a posted image file
            if ("uploadImageFile".equalsIgnoreCase(path.getServletPath())) {
                handleImagePost(req, resp);
                return;
            }

            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);

        } catch (SQLException | NamingException e) {
            throw new ServletException(e);
        }
    }

    private void handleImagePost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SQLException, NamingException {
        // an image is being posted! (as form-data)
        Part filePart = req.getPart("file");
        String fileName = filePart.getSubmittedFileName();
        System.out.println("submitted fileName:" + fileName);
        String name = req.getParameter("fileName");
        if (name != null && !name.isEmpty() && !"name".equalsIgnoreCase(name)) {
            System.out.println("overwrite fileName with posted name:" + name);
            fileName = name; // overwrite
        }
        filePart.write(fileName);

        String brandName = req.getParameter("brand");

        AiBrand aiBrand = null;

        Connection con = null;
        try {
            con = Model.connectX();
            if (brandName != null) {
                aiBrand = DbBrand.find(con, brandName);
                if (aiBrand == null) {
                    aiBrand = DbBrand.insert(con, brandName);
                }
            }

            // todo: how to read the configured url from the MultipartConfig(location ... tag?
            // export NOI_PATH=/Users/martin/work/tmp/ai-data
            String noiPath = SystemEnv.get("NOI_PATH", "/Users/martin/work/tmp/ai-data");
            if (!noiPath.endsWith("/")) {
                noiPath = noiPath + "/";
            }
            String url = noiPath + "images/" + fileName;
            AiImage aiImage = AiImage.create(fileName, url, Status.NEW, aiBrand);
            Long id = DbImage.insert(con, null, aiImage);
            System.out.println(fileName + ": created new image with id=" + id);

            JsonObject root = new JsonObject();
            root.addProperty("id", id);
            root.addProperty("name", aiImage.getName());
            root.addProperty("status_code", aiImage.getStatus().getStatus());
            root.addProperty("status_name", aiImage.getStatus().getName());
            writeResponse(resp, root);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            JsonObject root = new JsonObject();
            root.addProperty("error", e.getMessage());
            writeResponse(resp, root);
        } finally {
            Model.close(con);
        }
    }
}
