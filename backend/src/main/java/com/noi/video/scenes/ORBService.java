package com.noi.video.scenes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.noi.AiModel;
import com.noi.Status;
import com.noi.models.DbImage;
import com.noi.models.DbModel;
import com.noi.models.DbSimilarity;
import com.noi.models.DbVideo;
import com.noi.tools.JsonTools;
import com.noi.video.AiVideo;

import java.sql.Connection;
import java.sql.SQLException;

public class ORBService {
    public static final int MAX_SIMILARITY_DISTANCE = 60;
    public static final double SCENE_CHANGE_SCORE_THRESHOLD = 0.70d;

    public static void handleORBScenePost(Connection con, Long videoId, JsonObject videoLabels) throws SQLException {
        if (videoId == null) {
            throw new IllegalArgumentException();
        }

        // retire all existing scene changes for this video
        DbSimilarity.retireScenes(con, videoId);

        // lookup the model id for our local scoring alg.
        AiModel orbModel = DbModel.ensure(con, "ORB");

        // int videoLength = JsonTools.getAsInt(videoLabels, "video_length_seconds", 0);
        int frames = JsonTools.getAsInt(videoLabels, "total_frames", 0);
        double fps = JsonTools.getAsDouble(videoLabels, "frames_per_second");

        // update the ai_videos record for this video
        AiVideo video = DbVideo.update(con, videoId, frames, fps);
        if (video == null) {
            throw new IllegalStateException();
        }

        double scoreThreshold = JsonTools.getAsDouble(videoLabels, "score_threshold");
        int maxSimilarityDistance = JsonTools.getAsInt(videoLabels, "max_distance_for_similarity");

        // read the raw scored scene changes and persist the frames as ai_images
        JsonArray scores = videoLabels.getAsJsonArray("scored_scene_changes");
        for (int i = 0; i < scores.size(); i++) {
            JsonObject score = scores.get(i).getAsJsonObject();

            // get the first frame in the new scene
            int frame = JsonTools.getAsInt(score, "frame");
            String url = JsonTools.getAsString(score, "image_url");
            DbImage.createOrUpdate(con, video, frame, url, true, false);
            Long imageId = DbImage.exists(con, videoId, frame);

            // get the last scene in the previous scene
            int frameBefore = JsonTools.getAsInt(score, "frame_before");
            String urlBefore = JsonTools.getAsString(score, "image_url_before");
            Long imageIdBefore = DbImage.ensure(con, video, frameBefore, urlBefore).getId();

            double similarity = JsonTools.getAsDouble(score, "similarity_score");

            // persist the request and the score
            DbSimilarity.insertRequest(con, null, maxSimilarityDistance, scoreThreshold, videoId, imageId, imageIdBefore, orbModel, null, similarity, null, true, Status.COMPLETE);
        }
    }
}
