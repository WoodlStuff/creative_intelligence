package com.noi.web;

import com.noi.AiModel;
import com.noi.image.label.LabelService;
import com.noi.language.AiImageLabelRequest;
import com.noi.language.AiPrompt;
import com.noi.models.DbLanguage;
import com.noi.models.DbRequest;
import com.noi.models.Model;
import com.noi.requests.ImageLabelResponse;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseControllerServlet extends HttpServlet {

    protected AiPrompt handlePrompt(HttpServletRequest req, Path path, int promptIdIndex) throws IOException, SQLException, NamingException {
        AiPrompt prompt = AiPrompt.parse(req, path, promptIdIndex);
        if (prompt.getId() == null) {
            // we need to insert the prompt!
            prompt = DbLanguage.insertPrompt(prompt);
        } else {
            // we need to look up the actual prompt text
            prompt = DbLanguage.findPrompt(prompt.getId());
        }

        if (prompt == null) {
            throw new IllegalStateException();
        }

        return prompt;
    }

    protected String handleModelName(HttpServletRequest req, String defaultName) {
        String modelName = defaultName; // set default model to generate images
        if (req.getParameter("modelName") != null) {
            modelName = req.getParameter("modelName");
        }
        return modelName;
    }

    protected static List<ImageLabelResponse> requestImageLabels(Long imageId, AiModel model, List<AiPrompt> prompts) throws SQLException, NamingException {
        List<ImageLabelResponse> responses = new ArrayList<>();
        // handle a GoogleVision request (has no prompt!)
        if (prompts == null) {
            try {
                AiImageLabelRequest request = AiImageLabelRequest.create(imageId, null, model);
                responses.add(requestImageLabels(request));
            } catch (IOException e) {
                // todo:
                e.printStackTrace();
            }
        } else {
            // models other than GoogleVision
            for (AiPrompt prompt : prompts) {
                if (prompt == null) {
                    System.out.println("skipping missing prompt!");
                    continue;
                }
                try {
                    AiImageLabelRequest request = AiImageLabelRequest.create(imageId, prompt, model);
                    responses.add(requestImageLabels(request));
                } catch (IOException e) {
                    // todo:
                    e.printStackTrace();
                }
            }
        }

        return responses;
    }

    protected static ImageLabelResponse requestImageLabels(AiImageLabelRequest request) throws SQLException, NamingException, IOException {
        Connection con = null;
        try {
            con = Model.connectX();
            request = DbRequest.insertForLabel(con, request);

            LabelService labelService = LabelService.getService(request);
            if (labelService != null) {
                ImageLabelResponse response = labelService.labelize(con, request);
                DbRequest.finishedForLabel(con, request);
                return response;
            }

            return ImageLabelResponse.create(request);
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
}
