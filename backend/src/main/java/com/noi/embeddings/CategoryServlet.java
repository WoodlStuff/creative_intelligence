package com.noi.embeddings;

import com.noi.AiModel;
import com.noi.image.AiImage;
import com.noi.image.label.AiImageLabel;
import com.noi.image.label.GoogleVisionLabelService;
import com.noi.image.label.LabelMetaData;
import com.noi.image.label.LabelService;
import com.noi.language.AiPrompt;
import com.noi.models.DbImage;
import com.noi.models.DbImageLabel;
import com.noi.models.DbLanguage;
import com.noi.models.Model;
import com.noi.requests.NoiResponse;
import com.noi.web.BaseControllerServlet;
import com.noi.web.Path;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WebServlet(name = "CategoryServlet", urlPatterns = {"/categories/*"}, loadOnStartup = 0)
public class CategoryServlet extends BaseControllerServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // label data for one image
        // curl http://localhost:8080/noi-server/categories/<image-id>

        // generate json doc for requested image (... and category name)
        Path path = Path.parse(req);
        if (path.getPathInfo() == null || path.getPathInfo().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        System.out.println("GET:CategoryServlet: " + path);

        String[] pathTokens = path.getPathInfo().split("/");
        if (pathTokens.length == 0) {
            return;
        }

        try {
            writeImageLabels(req, resp, pathTokens);
        } catch (SQLException | NamingException e) {
            throw new ServletException(e);
        }
    }

    private void writeImageLabels(HttpServletRequest req, HttpServletResponse resp, String[] pathTokens) throws SQLException, NamingException, IOException {
        // either the image and prompt id are provided in the path,
        // or the prompt id and image url are provided as parameters
        Long imgId = null;
        String categoryName = null;

        if (pathTokens.length > 0) {
            String imageId = pathTokens[0].trim();
            if (imageId.isEmpty()) {
                throw new IllegalArgumentException("image id is missing in the path: /label/<image-id>");
            }
            imgId = Long.valueOf(imageId);
//        } else {
//            String imageUrl = req.getParameter("image_url");
//            AiImage image = DbImage.find(imageUrl);
//            if (image != null) {
//                imgId = image.getId();
//            }
        }

        if (pathTokens.length > 1) {
            categoryName = pathTokens[1].trim();
        }

        // read the image and the labels, and format a response (json)
        writeLabelResponse(imgId, categoryName, resp);
    }

    private void writeLabelResponse(Long imgId, String categoryName, HttpServletResponse resp) throws SQLException, NamingException, IOException {
        // read the image and the labels, and format a response (json)
        Connection con = null;
        try {
            con = Model.connectX();
            AiImage image = DbImage.find(con, imgId);
            List<AiImageLabel> annotations = DbImageLabel.findAnnotations(con, image);
            Map<String, List<LabelMetaData>> metaValues = DbImageLabel.findLabelMetaCategories(con, image);

            LabelService.writeLabelReport(image, annotations, metaValues, categoryName, resp);
        } finally {
            Model.close(con);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Labels only!
        // curl -X POST http://localhost:8080/noi-server/categories/<image-id>/<prompt-id>
        // curl -X POST http://localhost:8080/noi-server/categories/12345 (for all active prompts don't specify one!)
        // call labelling service and pick specific label service (by default both will be called)
        // curl -X POST http://localhost:8080/noi-server/categories/12345/12
        // [or:
        // curl -X POST http://localhost:8080/noi-server/categories/12345 -d "{'prompt_type': 10, 'prompt': 'some text goes here'}"  -d "modelName=gpt-4o (the default) | GoogleVision"]
        // with url encoding for url parameters in passed image url
        // curl -X POST -G http://localhost:8080/noi-server/category -d "modelName=gpt-4o" -d "prompt_id=1" --data-urlencode "image_url=https://scontent.fbgi3-1.fna.fbcdn.net/v/t45.1600-4/407953252_120203198010660611_4827034948669956474_n.png?stp=cp0_dst-jpg_fr_q90_spS444&_nc_cat=100&ccb=1-7&_nc_sid=5f2048&_nc_ohc=bzeFfJzfm_0Q7kNvgFcIMKg&_nc_ht=scontent.fbgi3-1.fna&oh=00_AYDLxbzIFiQqvqhkMu2u3cMA7bIGqm2U5QlU66a9sl1FAg&oe=6644314E"

        Path path = Path.parse(req);
        if (path.getPathInfo() == null || path.getPathInfo().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        System.out.println("POST:CategoryServlet: " + path);

        String[] pathTokens = path.getPathInfo().split("/");
        if (pathTokens.length == 0) {
            return;
        }

        if (pathTokens.length <= 1) {
            throw new IllegalArgumentException("image id param is missing!");
        }

        // send request to label the images of a specific request (or prompt within a request ... )
        if (pathTokens.length <= 1 && req.getParameter("image_url") == null) {
            throw new IllegalArgumentException("image id or image_url param is missing!");
        }

        try {
            handleSingleImageLabelRequest(req, resp, path, pathTokens);
        } catch (SQLException | NamingException e) {
            throw new RuntimeException(e);
        }
        return;

    }

    private void handleSingleImageLabelRequest(HttpServletRequest req, HttpServletResponse resp, Path path, String[] pathTokens) throws IOException, SQLException, NamingException {
        // either the image and prompt id are provided in the path,
        // or the prompt id and image url are provided as parameters (if no image exists yet in our db!)
        Long imgId = null;
        AiPrompt prompt = null;

        if (pathTokens.length > 1) {
            String imageId = pathTokens[1].trim();
            if (imageId.isEmpty()) {
                throw new IllegalArgumentException("image id is missing in the path: /label/<image-id>/<prompt-id>");
            }
            imgId = Long.valueOf(imageId);

            if (pathTokens.length > 2) {
                // todo: revisit! (case: no id in url, but type and prompt as posted json)
                prompt = handlePrompt(req, path, 2);
            }
        } else {
            String imageUrl = req.getParameter("image_url");
            AiImage image = DbImage.findOrCreate(imageUrl);
            if (image != null) {
                imgId = image.getId();
            }

            String promptId = req.getParameter("prompt_id");
            if (promptId != null) {
                Long id = Long.valueOf(promptId.trim());
                prompt = DbLanguage.findPrompt(id);
            }
        }

        // the model dictates what service we'll call
        String modelName = handleModelName(req, AiModel.DEFAULT_VISION_MODEL.getName());

        AiPrompt[] prompts;
        // one specific prompt requested?
        if (prompt != null) {
            prompts = new AiPrompt[]{prompt};
        } else {
            // otherwise: use all active label prompts
            List<AiPrompt.Type> promptTypes = new ArrayList<>();
            promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES);
            promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_OBJECTS);
            promptTypes.add(AiPrompt.TYPE_IMAGE_LABEL_PROPERTIES);
            List<AiPrompt> dbPrompts = DbLanguage.findPrompts(promptTypes);
            prompts = new AiPrompt[dbPrompts.size()];
            dbPrompts.toArray(prompts);
        }

        List<NoiResponse> responses = new ArrayList<>();
        responses.addAll(requestImageLabels(imgId, modelName, prompts));
        responses.addAll(requestImageLabels(imgId, GoogleVisionLabelService.MODEL_NAME, null));

        writeLabelResponse(imgId, null, resp);
    }
}