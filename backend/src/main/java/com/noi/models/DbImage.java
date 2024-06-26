package com.noi.models;

import com.noi.Status;
import com.noi.image.AiImage;
import com.noi.image.AiImageRequest;
import com.noi.language.AiPrompt;
import com.noi.video.AiVideo;

import javax.naming.NamingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbImage extends Model {
    private static final String COLUMNS = "i.id, p.id prompt_id, i.image_url, rp.prompt revised_prompt, i.status, i.ai_video_id, i.video_frame_number, i.is_new_video_scene, i.is_video_scene_snap";

    public static void insert(Connection con, AiImageRequest request, AiImage image) throws SQLException {
        // insert image details , and link it to the prompt the image came from
        Long promptId = null;
        if (request.getPrompt() != null && request.getPrompt().getId() != null) {
            promptId = request.getPrompt().getId();
        }

        Long requestId = request.getId();

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_images(ai_image_request_id, ai_prompt_id, image_url, status, created_at, updated_at) values(?,?,?,?, now(), now())");

            if (requestId != null && requestId > 0L) {
                stmt.setLong(1, requestId);
            } else {
                stmt.setString(1, null);
            }

            if (promptId != null && promptId > 0L) {
                stmt.setLong(2, promptId);
            } else {
                stmt.setString(2, null);
            }

            stmt.setString(3, image.getUrl());
            stmt.setInt(4, Status.NEW.getStatus());
            Long id = executeWithLastId(stmt);
            if (id > 0L) {
                image.setId(id);
            }

            // any prompt suggestions from the model?
            if (image.getRevisedPrompt() != null) {
                linkRevisedPrompt(con, requestId, promptId, image.getRevisedPrompt());
            }
        } finally {
            close(stmt);
        }
    }

    private static void linkRevisedPrompt(Connection con, Long requestId, Long promptId, String revisedPrompt) throws SQLException {
        if (requestId == null || promptId == null) {
            System.out.println("WARNING: no requestId/promptId for revised prompt: " + revisedPrompt);
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_revised_prompts(ai_request_id, ai_prompt_id, prompt, status, created_at, updated_at) values(?,?,?,?, now(), now())");
            stmt.setLong(1, requestId);
            stmt.setLong(2, promptId);
            stmt.setString(3, revisedPrompt);
            stmt.setInt(4, Status.NEW.getStatus());
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static AiImage insert(Connection con, String url) throws SQLException {
        return insert(con, null, 0, url);
    }

    public static AiImage insert(Connection con, Long videoId, int frame, String url) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_images(ai_video_id, video_frame_number, image_url, status, created_at, updated_at) values(?,?,?, ?, now(), now())");
            if (videoId != null) {
                stmt.setLong(1, videoId);
            } else {
                stmt.setString(1, null);
            }

            if (frame >= 0) {
                stmt.setInt(2, frame);
            } else {
                stmt.setString(2, null);
            }

            stmt.setString(3, url);
            stmt.setInt(4, Status.NEW.getStatus());
            Long id = executeWithLastId(stmt);
            if (id > 0L) {
                return AiImage.create(id, videoId, frame, url, Status.NEW);
            }
        } finally {
            close(stmt);
        }

        return null;
    }

    public static void insert(AiImageRequest request, AiImage image) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            insert(con, request, image);
        } finally {
            close(con);
        }
    }

//    public static List<AiImage> findImages(Connection con, AiImageRequest request) throws SQLException {
//        List<AiImage> images = new ArrayList<>();
//
//        PreparedStatement stmt = null;
//        try {
//            stmt = con.prepareStatement("select i.id, p.id prompt_id, i.image_url, p.prompt, rp.prompt revised_prompt from ai_images i join ai_prompts p on p.id = i.ai_prompt_id left join ai_revised_prompts rp on p.id = rp.ai_prompt_id and i.ai_request_id = rp.ai_request_id where p.ai_request_id=? and i.status in(?,?)");
//            stmt.setLong(1, request.getId());
//            stmt.setInt(2, Status.NEW.getStatus());
//            stmt.setInt(3, Status.ACTIVE.getStatus());
//
//            ResultSet rs = stmt.executeQuery();
//            while (rs.next()) {
//                // map the image to it's prompt
////                Long promptId = rs.getLong("prompt_id");
//                Long promptId = rs.getLong("prompt_id");
//                String prompt = rs.getString("prompt");
//
//                images.add(AiImage.create(rs));
//            }
//        } finally {
//            close(stmt);
//        }
//
//        return images;
//    }

    public static List<AiImage> findImages(Connection con, Status status) throws SQLException {
        List<AiImage> images = new ArrayList<>();

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + COLUMNS + " from ai_images i left join ai_prompts p on p.id = i.ai_prompt_id left join ai_revised_prompts rp on p.id = rp.ai_prompt_id and i.ai_image_request_id = rp.ai_image_request_id where i.status=?");
            stmt.setInt(1, status.getStatus());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                AiPrompt prompt = DbLanguage.findPrompt(con, rs.getLong("prompt_id"));
                images.add(AiImage.create(rs, prompt));
            }
        } finally {
            close(stmt);
        }

        return images;
    }

    public static AiImage find(Long id) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            return find(con, id);
        } finally {
            close(con);
        }
    }

    public static AiImage find(String imageUrl) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            return find(con, imageUrl);
        } finally {
            close(con);
        }
    }


    public static AiImage findForVideo(Connection con, Long videoId, String imageURL) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + COLUMNS + " from ai_images i left join ai_prompts p on i.ai_prompt_id = p.id left join ai_revised_prompts rp on p.id = rp.ai_prompt_id and i.ai_image_request_id = rp.ai_image_request_id where i.ai_video_id=? and i.image_url=?");
            stmt.setLong(1, videoId);
            stmt.setString(2, imageURL);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                AiPrompt prompt = DbLanguage.findPrompt(con, rs.getLong("prompt_id"));
                return AiImage.create(rs, prompt);
            }
        } finally {
            close(stmt);
        }

        return null;
    }

    public static AiImage find(Connection con, Long id) throws SQLException {

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + COLUMNS + " from ai_images i left join ai_prompts p on i.ai_prompt_id = p.id left join ai_revised_prompts rp on p.id = rp.ai_prompt_id and i.ai_image_request_id = rp.ai_image_request_id where i.id=?");
            stmt.setLong(1, id);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                AiPrompt prompt = DbLanguage.findPrompt(con, rs.getLong("prompt_id"));
                return AiImage.create(rs, prompt);
            }
        } finally {
            close(stmt);
        }

        return null;
    }

    public static Long exists(Connection con, Long videoId, int frame) throws SQLException {

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select id from ai_images where ai_video_id=? and video_frame_number=?");
            stmt.setLong(1, videoId);
            stmt.setInt(2, frame);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        } finally {
            close(stmt);
        }

        return null;
    }

    public static AiImage find(Connection con, String imageUrl) throws SQLException {

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + COLUMNS + " from ai_images i left join ai_prompts p on i.ai_prompt_id = p.id left join ai_revised_prompts rp on p.id = rp.ai_prompt_id and i.ai_image_request_id = rp.ai_image_request_id where i.image_url=?");
            stmt.setString(1, imageUrl);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                AiPrompt prompt = DbLanguage.findPrompt(con, rs.getLong("prompt_id"));
                return AiImage.create(rs, prompt);
            }
        } finally {
            close(stmt);
        }

        return null;
    }

    /**
     * return the most recent <>limit</> rows of images (updated_at desc)
     *
     * @param con
     * @param limit
     * @param videoId     (optional: only images from this video)
     * @param frameNumber (optional: only this frame from this videoId)
     * @return
     */
    public static List<AiImage> findMostRecent(Connection con, int limit, Long videoId, Integer frameNumber) throws SQLException {
        if (frameNumber != null && videoId == null) {
            throw new IllegalArgumentException();
        }

        List<AiImage> images = new ArrayList<>();

        PreparedStatement stmt = null;
        try {
            StringBuilder query = new StringBuilder();
            String sql = "select " + COLUMNS + " from ai_images i left join ai_prompts p on i.ai_prompt_id = p.id left join ai_revised_prompts rp on p.id = rp.ai_prompt_id and i.ai_image_request_id = rp.ai_image_request_id";
            query.append(sql);

            query.append(" where i.status !=?");

            if (videoId != null) {
                query.append(" and i.ai_video_id=?");
            }
            if (frameNumber != null) {
                query.append(" and i.video_frame_number=?");
            }

            query.append(" order by i.video_frame_number desc, i.updated_at desc limit ?");

            stmt = con.prepareStatement(query.toString());
            int index = 1;
            stmt.setInt(index++, Status.DELETED.getStatus());

            if (videoId != null) {
                stmt.setLong(index++, videoId);
            }
            if (frameNumber != null) {
                stmt.setInt(index++, frameNumber);
            }

            stmt.setInt(index, limit);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                AiPrompt prompt = DbLanguage.findPrompt(con, rs.getLong("prompt_id"));
                images.add(AiImage.create(rs, prompt));
            }
        } finally {
            close(stmt);
        }
        return images;
    }

    public static void updateStatus(Connection con, AiImage image, Status status) throws SQLException {
        if (image == null || image.getId() == null || status == null) {
            throw new IllegalArgumentException();
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("update ai_images set status=?, updated_at=now() where id=?");
            stmt.setInt(1, status.getStatus());
            stmt.setLong(2, image.getId());
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static AiImage findOrCreate(String imageUrl) throws SQLException, NamingException {
        Connection con = null;
        AiImage image;

        try {
            con = connectX();
            image = find(con, imageUrl);

            if (image == null) {
                image = insert(con, imageUrl);
            }

            return image;

        } finally {
            close(con);
        }
    }

    public static AiImage ensure(Connection con, String path) throws SQLException {
        AiImage image = find(con, path);
        if (image != null) {
            return image;
        }

        return insert(con, path);
    }

    public static AiImage ensure(Connection con, Long videoId, int frame, String path) throws SQLException {
        Long id = exists(con, videoId, frame);
        if (id != null) {
            return find(con, id);
        }

        return insert(con, videoId, frame, path);
    }

    public static List<AiImage> findVideoScenes(Long videoId) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = Model.connectX();
            return findVideoScenes(con, videoId);
        } finally {
            Model.close(con);
        }
    }

    /**
     * find all images that were used to generate the video summary
     *
     * @param videoId
     * @return
     * @throws SQLException
     * @throws NamingException
     */
    public static List<AiImage> findVideoSummaryScenes(Long videoId) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = Model.connectX();
            return findVideoSummaryScenes(con, videoId);
        } finally {
            Model.close(con);
        }
    }

    public static List<AiImage> findVideoScenes(Connection con, Long videoId) throws SQLException {
        List<AiImage> images = new ArrayList<>();
        PreparedStatement stmt = null;
        try {
            StringBuilder query = new StringBuilder();
            String sql = "select " + COLUMNS + " from ai_images i left join ai_prompts p on i.ai_prompt_id = p.id left join ai_revised_prompts rp on p.id = rp.ai_prompt_id and i.ai_image_request_id = rp.ai_image_request_id";
            query.append(sql);

            query.append(" where i.ai_video_id=?");
            query.append(" order by i.video_frame_number asc");

            stmt = con.prepareStatement(query.toString());

            stmt.setLong(1, videoId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                AiPrompt prompt = DbLanguage.findPrompt(con, rs.getLong("prompt_id"));
                images.add(AiImage.create(rs, prompt));
            }

        } finally {
            close(stmt);
        }

        return images;
    }

    /**
     * find all images used to summarize the video (but only for the most recent request!).
     *
     * @param con
     * @param videoId
     * @return
     * @throws SQLException
     */
    public static List<AiImage> findVideoSummaryScenes(Connection con, Long videoId) throws SQLException {
        List<AiImage> images = new ArrayList<>();
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + COLUMNS + " from ai_images i left join ai_prompts p on i.ai_prompt_id = p.id left join ai_revised_prompts rp on p.id = rp.ai_prompt_id and i.ai_image_request_id = rp.ai_image_request_id join ai_video_summary_scenes vss on vss.ai_video_id = i.ai_video_id and vss.ai_image_id = i.id join (select id, uuid, ai_video_id, ai_model_id, ai_prompt_id from ai_video_summary_requests where ai_video_id=? order by id desc limit 1)vsr on vsr.ai_video_id = i.ai_video_id and vsr.id = vss.ai_video_summary_request_id order by i.video_frame_number asc");
            stmt.setLong(1, videoId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                AiPrompt prompt = DbLanguage.findPrompt(con, rs.getLong("prompt_id"));
                images.add(AiImage.create(rs, prompt));
            }
        } finally {
            close(stmt);
        }

        return images;
    }

    public static Map<AiVideo, List<AiVideo.SceneChange>> findVideoScenes(Connection con, List<AiVideo> videos, boolean llmChanges) throws SQLException {
        Map<AiVideo, List<AiVideo.SceneChange>> scenes = new HashMap<>();

        if (videos.size() == 0) {
            return scenes;
        }

        PreparedStatement stmt = null;
        try {
            StringBuilder query = new StringBuilder();
            query.append("select distinct m.name, r.ai_image_before_id last_image_id, r.ai_image_id first_image_id, r.is_scene_change, r.ai_image_id, last.image_url last_scene_url, last.video_frame_number last_frame, first.image_url first_scene_url, first.video_frame_number first_frame, r.score, r.explanation from ai_similarity_requests r join ai_models m on m.id = r.ai_model_id join ai_images last on r.ai_image_before_id = last.id join ai_images first on r.ai_image_id = first.id");
            // we only want the most recent requests per image pair, model and prompt
            query.append(" join (select max(r.id) _max_id, r.ai_image_id, r.ai_image_before_id, r.ai_model_id, r.ai_prompt_id from ai_similarity_requests r join ai_images i on i.id = r.ai_image_id where i.ai_video_id=? group by 2,3,4,5 order by r.ai_model_id, r.ai_prompt_id, r.ai_image_id, r.ai_image_before_id)recent on recent._max_id = r.id");
            query.append("  where last.ai_video_id = first.ai_video_id and first.ai_video_id=?");
            if (llmChanges) {
                query.append(" and m.name !=?");
            } else {
                query.append(" and m.name=?");
            }
            query.append("  order by first.video_frame_number asc");

            stmt = con.prepareStatement(query.toString());

            for (AiVideo video : videos) {
                List<AiVideo.SceneChange> changes = new ArrayList<>();
                stmt.setLong(1, video.getId());
                stmt.setLong(2, video.getId());
                stmt.setString(3, "ORB"); // ORB is our internal scoring model for scene changes
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    // AiPrompt prompt = DbLanguage.findPrompt(con, rs.getLong("ai_prompt_id"));
                    AiImage lastImage = DbImage.find(con, rs.getLong("last_image_id"));
                    AiImage firstImage = DbImage.find(con, rs.getLong("first_image_id"));
                    double score = rs.getDouble("score");
                    String explanation = rs.getString("explanation");
                    boolean isNewScene = rs.getBoolean("is_scene_change");
                    long lastFrame = rs.getLong("last_frame");
                    long firstFrame = rs.getLong("first_frame");
                    changes.add(AiVideo.SceneChange.create(lastImage, firstImage, lastFrame, firstFrame, score, explanation, isNewScene));
                }
                scenes.put(video, changes);

                Model.close(rs);
            }

        } finally {
            close(stmt);
        }

        return scenes;
    }

    public static void createOrUpdate(Connection con, Long videoId, int frame, String url, boolean isNewScene, boolean isSceneSnap) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_images(image_url, status, ai_video_id, video_frame_number, is_new_video_scene, is_video_scene_snap, created_at, updated_at) values(?,?,?,?,?,?,now(),now()) ON DUPLICATE KEY UPDATE updated_at=now(), image_url=?, status=?, is_new_video_scene=?, is_video_scene_snap=?");
            stmt.setString(1, url);
            stmt.setInt(2, Status.NEW.getStatus());
            stmt.setLong(3, videoId);
            stmt.setInt(4, frame);
            stmt.setBoolean(5, isNewScene);
            stmt.setBoolean(6, isSceneSnap);

            stmt.setString(7, url);
            stmt.setInt(8, Status.NEW.getStatus());
            stmt.setBoolean(9, isNewScene);
            stmt.setBoolean(10, isSceneSnap);
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }
}
