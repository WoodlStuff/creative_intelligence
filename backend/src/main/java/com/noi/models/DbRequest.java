package com.noi.models;

import com.noi.Status;
import com.noi.image.AiImageRequest;
import com.noi.language.AiImageLabelRequest;
import com.noi.language.AiPrompt;
import com.noi.language.NLPRequest;

import javax.naming.NamingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DbRequest extends Model {

//    public static NoiRequest insert(NoiRequest request) throws SQLException, NamingException {
//        Connection con = null;
//        try {
//            con = connectX();
//            return insert(con, request);
//        } finally {
//            close(con);
//        }
//    }
//
//    public static NoiRequest insert(Connection con, NoiRequest request) throws SQLException {
//        // request and prompts
//        Long promptId = insertPrompt(con, request);
//
//        PreparedStatement stmt = null;
//        try {
//            stmt = con.prepareStatement("insert into ai_requests(uuid, ai_prompt_id , status, created_at, updated_at) values(?, ?, ?, now(), now())");
//            stmt.setString(1, request.getUUID());
//            stmt.setLong(2, promptId);
//            stmt.setInt(3, Status.NEW.getStatus());
//
//            Long id = executeWithLastId(stmt);
//            if (id > 0L) {
//                request.setId(id); // link to db id for db references
//                return request;
//            }
//        } finally {
//            close(stmt);
//        }
//
//        return null;
//    }
//

    private static Long insertPrompt(Connection con, String prompt) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_prompts(prompt, status, created_at, updated_at) values(?,?, now(), now())");
            stmt.setString(1, prompt);
            stmt.setInt(2, Status.NEW.getStatus());

            return executeWithLastId(stmt);
        } finally {
            close(stmt);
        }
    }

//    public static NoiRequest find(String requestUUID) throws SQLException, NamingException {
//        Connection con = null;
//        try {
//            con = connectX();
//            return find(con, requestUUID);
//        } finally {
//            close(con);
//        }
//    }

//    public static NoiRequest find(Connection con, String requestUUID) throws SQLException {
//        PreparedStatement stmt = null;
//        try {
//            stmt = con.prepareStatement("select id, uuid, model_name, image_style, scene, modifier_words, status from ai_requests where uuid=?");
//            stmt.setString(1, requestUUID);
//            ResultSet rs = stmt.executeQuery();
//            if (rs.next()) {
//                NoiRequest request = NoiRequest.create(rs);
//                return addPrompts(con, request);
//            }
//
//        } finally {
//            close(stmt);
//        }
//
//        return null;
//    }

    public static AiImageRequest insertForImage(AiImageRequest request) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            return insertForImage(con, request);
        } finally {
            close(con);
        }
    }

    public static AiImageRequest insertForImage(Connection con, AiImageRequest request) throws SQLException {
        if (request.getPrompt() == null || request.getPrompt().getId() == null) {
            throw new IllegalArgumentException();
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_image_requests(ai_prompt_id, uuid, model_name, status, created_at, updated_at) values(?,?,?,?,now(), now())");
            stmt.setLong(1, request.getPrompt().getId());
            stmt.setString(2, request.getUUID());
            stmt.setString(3, request.getModelName());
            stmt.setInt(4, Status.ACTIVE.getStatus());
            Long requestId = executeWithLastId(stmt);
            request.setId(requestId);

        } finally {
            close(stmt);
        }
        return request;
    }


    public static void finishedForImage(AiImageRequest request) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            finishedForImage(con, request);
        } finally {
            close(con);
        }
    }

    public static void finishedForImage(Connection con, AiImageRequest request) throws SQLException {
        // we'd only come here if we had requested an image to be generated based on a prompt
        if (request.getPrompt() == null || request.getPrompt().getId() == null) {
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("update ai_image_requests set status=?, updated_at=now() where id=?");
            stmt.setInt(1, Status.COMPLETE.getStatus());
            stmt.setLong(2, request.getId());
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }


    public static AiImageLabelRequest insertForLabel(AiImageLabelRequest request) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            return insertForLabel(con, request);
        } finally {
            close(con);
        }
    }

    public static AiImageLabelRequest insertForLabel(Connection con, AiImageLabelRequest request) throws SQLException {
        if (request.getImageId() == null) {
            throw new IllegalArgumentException();
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_label_requests(ai_image_id, ai_prompt_id, uuid, model_name, status, created_at, updated_at) values(?,?,?,?,?,now(), now())");
            stmt.setLong(1, request.getImageId());
            AiPrompt prompt = request.getPrompt();
            if (prompt != null) {
                Long promptId = prompt.getId();
                if (promptId == null) {
                    // first persist the prompt and get the id
                    prompt = DbLanguage.insertPrompt(con, prompt.getPrompt(), prompt.getSystemPrompt(), AiPrompt.TYPE_IMAGE_LABEL_CATEGORIES.getType());
                    promptId = prompt.getId();
                }
                stmt.setLong(2, promptId);
            } else {
                stmt.setString(2, null); // no prompt!?!
            }
            stmt.setString(3, request.getUUID());
            stmt.setString(4, request.getModelName());
            stmt.setInt(5, Status.ACTIVE.getStatus());
            Long requestId = executeWithLastId(stmt);
            request.setId(requestId);

        } finally {
            close(stmt);
        }
        return request;
    }

    public static void finishedForLabel(AiImageLabelRequest request) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            finishedForLabel(con, request);
        } finally {
            close(con);
        }
    }

    public static void finishedForLabel(Connection con, AiImageLabelRequest request) throws SQLException {
        if (request.getImageId() == null) {
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("update ai_label_requests set status=?, updated_at=now() where id=?");
            stmt.setInt(1, Status.COMPLETE.getStatus());
            stmt.setLong(2, request.getId());
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static void updateForLabel(Connection con, AiImageLabelRequest request, AiPrompt prompt) throws SQLException {
        if (request.getImageId() == null || request.getId() == null
                || prompt == null || prompt.getId() == null) {
            throw new IllegalArgumentException();
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("update ai_label_requests set ai_prompt_id=?, updated_at=now() where id=? and ai_prompt_id is null");
            stmt.setLong(1, prompt.getId());
            stmt.setLong(2, request.getId());
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static NLPRequest insertForPrompt(NLPRequest request) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            return insertForPrompt(con, request);
        } finally {
            close(con);
        }
    }

    public static NLPRequest insertForPrompt(Connection con, NLPRequest request) throws SQLException {
        if (request.getPrompt() == null || request.getPrompt().getId() == null) {
            throw new IllegalArgumentException();
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_nlp_requests(ai_prompt_id, uuid, model_name, status, created_at, updated_at) values(?,?,?,?,now(), now())");
            stmt.setLong(1, request.getPrompt().getId());
            stmt.setString(2, request.getUUID());
            stmt.setString(3, request.getModelName());
            stmt.setInt(4, Status.ACTIVE.getStatus());
            Long requestId = executeWithLastId(stmt);
            request.setId(requestId);

        } finally {
            close(stmt);
        }
        return request;
    }

    public static void finishedForPrompt(NLPRequest request) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            finishedForPrompt(con, request);
        } finally {
            close(con);
        }
    }

    public static void finishedForPrompt(Connection con, NLPRequest request) throws SQLException {
        if (request.getPrompt() == null || request.getPrompt().getId() == null) {
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("update ai_nlp_requests set status=?, updated_at=now() where id=?");
            stmt.setInt(1, Status.COMPLETE.getStatus());
            stmt.setLong(2, request.getId());
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static AiImageLabelRequest findForLabel(Connection con, String labelRequestUUID) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select id, ai_image_id, ai_prompt_id, model_name from ai_label_requests where uuid=?");
            stmt.setString(1, labelRequestUUID);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                AiPrompt prompt = DbLanguage.findPrompt(con, rs.getLong("ai_prompt_id"));
                return AiImageLabelRequest.create(rs.getLong("id"), rs.getLong("ai_image_id"), prompt, rs.getString("model_name"));
            }

        } finally {
            close(stmt);
        }

        return null;
    }
}
