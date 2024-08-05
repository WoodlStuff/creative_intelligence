package com.noi.models;

import com.noi.AiModel;
import com.noi.Status;
import com.noi.language.AiPrompt;
import com.noi.video.scenes.SceneChangeRequest;

import javax.naming.NamingException;
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

    public static Long insertRequest(Connection con, String uuid, Integer maxSimilarityDistance, Double scoreThreshold, Long videoId, Long imageId, Long imageIdBefore, AiModel model, AiPrompt prompt, double similarity, String explanation, boolean isSceneChange) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert ignore into ai_similarity_requests (uuid, ai_image_id, ai_image_before_id, ai_model_id, ai_prompt_id, max_distance, score_threshold, score, explanation, is_scene_change, status, ai_video_id, created_at, updated_at) values(?,?,?,?,?,?,?,?,?,?,?,?, now(), now()) ON DUPLICATE KEY update updated_at=now(), max_distance=?, score_threshold=?, score=?, explanation=?, is_scene_change=?, id=LAST_INSERT_ID(id)");
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

            if (maxSimilarityDistance != null) {
                stmt.setInt(6, maxSimilarityDistance);
            } else {
                stmt.setString(6, null);
            }

            if (scoreThreshold != null) {
                stmt.setDouble(7, scoreThreshold);
            } else {
                stmt.setString(7, null);
            }

            stmt.setDouble(8, similarity);
            stmt.setString(9, explanation != null && explanation.length() > 255 ? explanation.substring(0, 254) : explanation);
            stmt.setBoolean(10, isSceneChange);

            stmt.setInt(11, Status.ACTIVE.getStatus());
            if (videoId != null) {
                stmt.setLong(12, videoId);
            } else {
                stmt.setString(12, null);
            }

            // on duplicate
            if (maxSimilarityDistance != null) {
                stmt.setInt(13, maxSimilarityDistance);
            } else {
                stmt.setString(13, null);
            }

            if (scoreThreshold != null) {
                stmt.setDouble(14, scoreThreshold);
            } else {
                stmt.setString(14, null);
            }


            stmt.setDouble(15, similarity);
            stmt.setString(16, explanation != null && explanation.length() > 255 ? explanation.substring(0, 254) : explanation);
            stmt.setBoolean(17, isSceneChange);

            return executeWithLastId(stmt);
        } finally {
            close(stmt);
        }
    }

    public static void retireScenesForOtherModels(Connection con, Long videoId, AiModel excludedModel) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("update ai_similarity_requests set status=?, updated_at=now() where ai_video_id=? and ai_model_id != ?");
            stmt.setInt(1, Status.RETIRED.getStatus());
            stmt.setLong(2, videoId);
            stmt.setLong(3, excludedModel.getId());
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static void retireScenes(Connection con, Long videoId) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("update ai_similarity_requests set status=?, updated_at=now() where ai_video_id=?");
            stmt.setInt(1, Status.RETIRED.getStatus());
            stmt.setLong(2, videoId);
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static void insertRequest(Connection con, SceneChangeRequest request, double similarityScore, boolean isSameScene, String llmExplanation) throws SQLException {
        // insert for LLM checked scene change
        insertRequest(con, request.getUUID(), null, null, request.getVideo().getId(), request.getSceneChange().getFirstImage().getId(), request.getSceneChange().getLastImage().getId(), request.getModel(), request.getPrompt(), similarityScore, llmExplanation, !isSameScene);
    }
}
