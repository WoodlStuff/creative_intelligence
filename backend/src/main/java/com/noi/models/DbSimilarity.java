package com.noi.models;

import com.noi.AiModel;
import com.noi.Status;
import com.noi.language.AiPrompt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DbSimilarity extends Model {
    public static void ensure(Connection con, Long imageId, Long imageIdBefore, Long requestId, double similarity, String explanation, boolean isSceneChange) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_image_similarities(ai_image_a_id, ai_image_b_id, score, explanation, is_scene_change, status, created_at, updated_at) values(?,?,?, ?,?,?, now(),now())");
            stmt.setLong(1, imageId);
            stmt.setLong(2, imageIdBefore);
            stmt.setDouble(3, similarity);
            stmt.setString(4, explanation != null && explanation.length() > 255 ? explanation.substring(0, 254) : explanation);
            stmt.setBoolean(5, isSceneChange);
            stmt.setInt(6, Status.NEW.getStatus());
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static Long insertRequest(Connection con, String uuid, Long imageId, Long imageIdBefore, AiModel model, AiPrompt prompt, double similarity, String explanation, boolean isSceneChange) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert ignore into ai_similarity_requests (uuid, ai_image_id, ai_image_before_id, ai_model_id, ai_prompt_id, score, explanation, is_scene_change, status, created_at, updated_at) values(?,?,?, ?,?,?, ?,?,?, now(), now()) ON DUPLICATE KEY update updated_at=now(), score=?, explanation=?, is_scene_change=?, id=LAST_INSERT_ID(id)");
            stmt.setString(1, uuid);
            stmt.setLong(2, imageId);
            stmt.setLong(3, imageIdBefore);

            if (model != null) {
                stmt.setLong(4, model.getId());
            } else {
                stmt.setString(4, null);
            }

            if (prompt != null) {
                stmt.setLong(5, prompt.getId());
            } else {
                stmt.setString(5, null);
            }

            stmt.setDouble(6, similarity);
            stmt.setString(7, explanation != null && explanation.length() > 255 ? explanation.substring(0, 254) : explanation);
            stmt.setBoolean(8, isSceneChange);

            stmt.setInt(9, Status.ACTIVE.getStatus());

            // on duplicate
            stmt.setDouble(10, similarity);
            stmt.setString(11, explanation != null && explanation.length() > 255 ? explanation.substring(0, 254) : explanation);
            stmt.setBoolean(12, isSceneChange);

            return executeWithLastId(stmt);
        } finally {
            close(stmt);
        }
    }

//    public static void insert(Connection con, Long requestId, double similarity, String explanation, boolean isSceneChange) throws SQLException {
//        PreparedStatement stmt = null;
//        try {
//            stmt = con.prepareStatement("insert into ai_image_similarities(ai_similarity_request_id, score, explanation, is_scene_change, status, created_at, updated_at) values(?,?,?,?,?, now(), now())");
//            stmt.setLong(1, requestId);
//            stmt.setDouble(2, similarity);
//            stmt.setString(3, explanation != null && explanation.length() > 255 ? explanation.substring(0, 254) : explanation);
//            stmt.setBoolean(4, isSceneChange);
//            stmt.setInt(5, Status.ACTIVE.getStatus());
//            stmt.executeUpdate();
//        } finally {
//            close(stmt);
//        }
//    }
}
