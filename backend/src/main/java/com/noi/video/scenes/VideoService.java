package com.noi.video.scenes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.noi.models.DbImage;
import com.noi.models.DbMedia;
import com.noi.models.DbVideo;
import com.noi.models.Model;
import com.noi.tools.FileTools;
import com.noi.video.AiVideo;

import javax.naming.NamingException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoService {

    public static JsonObject readAndFormatVideoResponse(Long videoId) throws SQLException, NamingException, IOException {
        Connection con = null;
        try {
            con = Model.connectX();
            return readAndFormatVideoResponse(con, videoId);
        } finally {
            Model.close(con);
        }
    }

    public static JsonObject readAndFormatVideoResponse(Connection con, Long videoId) throws SQLException, NamingException, IOException {
        List<AiVideo> videos = new ArrayList<>();
        videos.add(DbVideo.find(con, videoId));
        return readAndFormatVideoResponse(con, videos);
    }

    public static JsonObject readAndFormatVideoResponse(Connection con, List<AiVideo> videos) throws SQLException, NamingException, IOException {
        Map<AiVideo, List<AiVideo.SceneChange>> localVideoScenes = DbImage.findVideoSceneChanges(con, videos, false);
        Map<AiVideo, List<AiVideo.SceneChange>> llmVideoScenes = DbImage.findVideoSceneChanges(con, videos, true);
        //Map<AiVideo, String> transcripts = listVideoTranscipts(videos);
        Map<AiVideo, String> soundSummaries = listSoundSummaries(con, videos);
        Map<AiVideo, ORBParams> scoringParams = listORBScoringParams(con, videos);
        Map<AiVideo, String> videoSummaries = listVideoSummaries(con, videos);
        return formatVideosResponse(videos, scoringParams, localVideoScenes, llmVideoScenes, soundSummaries, videoSummaries);
    }

    private static JsonObject formatVideosResponse(List<AiVideo> videos, Map<AiVideo, ORBParams> scoringParams, Map<AiVideo, List<AiVideo.SceneChange>> localVideoScenes, Map<AiVideo, List<AiVideo.SceneChange>> llmVideoScenes, Map<AiVideo, String> soundSummaries, Map<AiVideo, String> videoSummaries) throws IOException {
        // {'videos': [
        //    { url: './v1.png', id: 1, name: 'video 1', 'file_pth': '/xxx', 'status': 'new'}, { url: './v2.png', id:2, name: 'video 2'}
        //  ]};
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        root.add("videos", array);
        for (AiVideo video : videos) {
            JsonObject i = new JsonObject();
            array.add(i);
            i.addProperty("id", video.getId());
            i.addProperty("name", video.getName());
            i.addProperty("key", video.getId());
            i.addProperty("url", video.getUrl());
            i.addProperty("name", FileTools.getFileName(video.getUrl(), false));
            // i.addProperty("file_path", video.getFilePath());
            i.addProperty("frame_rate", video.getFrameRate());
            i.addProperty("frame_count", video.getFrameCount());
            i.addProperty("seconds", video.getSeconds());
            i.addProperty("status", video.getStatus().getName());
            if (video.getBrand() != null) {
                i.addProperty("brand", video.getBrand().getName());
            }

            ORBParams orbParams = scoringParams.get(video);
            if (orbParams != null) {
                i.addProperty("orb_scoring_threshold", orbParams.getThreshold());
                i.addProperty("orb_scoring_max_distance", orbParams.getMaxDistance());
            } else {
                // defaults
                i.addProperty("orb_scoring_threshold", ORBService.SCENE_CHANGE_SCORE_THRESHOLD);
                i.addProperty("orb_scoring_max_distance", ORBService.MAX_SIMILARITY_DISTANCE);
            }

            addVideoScenes("orb", localVideoScenes.get(video), i);
            addVideoScenes("llm", llmVideoScenes.get(video), i);

            String soundSummary = soundSummaries.get(video);
            if (soundSummary != null) {
                i.addProperty("sound_summary", soundSummary);
            }

            String videoSummary = videoSummaries.get(video);
            if (videoSummary != null) {
                i.addProperty("video_summary", videoSummary);
            }
        }

        return root;
    }

    private static Map<AiVideo, String> listVideoSummaries(Connection con, List<AiVideo> videos) throws SQLException, NamingException {
        Map<AiVideo, String> summaries = new HashMap<>();
        for (AiVideo video : videos) {
            String summary = DbMedia.findMostRecentVideoSummary(con, video.getId());
            if (summary != null) {
                summaries.put(video, summary);
            }
        }

        return summaries;
    }

    private static Map<AiVideo, String> listSoundSummaries(Connection con, List<AiVideo> videos) throws SQLException, NamingException {
        Map<AiVideo, String> summaries = new HashMap<>();
        for (AiVideo video : videos) {
            String summary = DbMedia.findMostRecentSoundSummary(con, video.getId());
            if (summary != null) {
                summaries.put(video, summary);
            }
        }

        return summaries;
    }

    private static Map<AiVideo, ORBParams> listORBScoringParams(Connection con, List<AiVideo> videos) throws SQLException, NamingException {
        Map<AiVideo, ORBParams> params = new HashMap<>();
        for (AiVideo video : videos) {
            ORBParams p = DbImage.findSceneChangeORBParams(con, video);
            params.put(video, p);
        }

        return params;
    }

    private static void addVideoScenes(String scope, List<AiVideo.SceneChange> changes, JsonObject videoObject) {
        // add the scene images for this video as json array
        JsonArray scenes = new JsonArray();
        videoObject.add(scope + "_scenes", scenes);
        for (AiVideo.SceneChange sceneChange : changes) {
            JsonObject scene = new JsonObject();
            scenes.add(scene);
            scene.addProperty("key", String.format("%d-%d", sceneChange.getLastFrame(), sceneChange.getFirstFrame()));
            scene.addProperty("last_scene_url", sceneChange.getLastImage().getUrl());
            scene.addProperty("last_scene_image_id", sceneChange.getLastImage().getId());
            scene.addProperty("last_scene_frame", sceneChange.getLastFrame());
            scene.addProperty("first_scene_url", sceneChange.getFirstImage().getUrl());
            scene.addProperty("first_scene_image_id", sceneChange.getFirstImage().getId());
            scene.addProperty("first_scene_frame", sceneChange.getFirstFrame());

            scene.addProperty("score", sceneChange.getScore());
            scene.addProperty("explanation", sceneChange.getExplanation());
            scene.addProperty("is_new_video_scene", sceneChange.isNewScene());
        }
    }

    public static List<AiVideo> listVideos(Connection con, Long videoId, int limit) throws SQLException {
        if (videoId != null) {
            List<AiVideo> videos = new ArrayList<>();
            AiVideo video = DbVideo.find(con, videoId);
            if (video != null) {
                videos.add(video);
            }
            return videos;
        }

        return DbVideo.findMostRecent(con, limit);
    }

    public static List<AiVideo.SceneChange> filterScenesForSummary(AiVideo video, List<AiVideo.SceneChange> localVideoScenes, List<AiVideo.SceneChange> llmVideoScenes) {
        // todo: this needs some work on the edge cases !
        List<AiVideo.SceneChange> sceneChanges = llmVideoScenes.size() > 0 ? llmVideoScenes : localVideoScenes;

        // max: one scene change every 2 seconds;
        int maxSceneChanges = (video.getSeconds() / 2) > sceneChanges.size() ? sceneChanges.size() : (int) (video.getSeconds() / 2);
        int minSceneChanges = Math.min(maxSceneChanges, 15);

        // we prefer the llm verified scene changes, but if there are not enough of them we need to fall back to using our own ...
        if (llmVideoScenes.size() < minSceneChanges && localVideoScenes.size() > llmVideoScenes.size()) {
            System.out.println("WARNING: not enough labeled scene changes {len(llmSceneChanges)}: attempting a fallback ...");
            sceneChanges = localVideoScenes;

            // re-calculate the min and max counts
            maxSceneChanges = (video.getSeconds() / 2) > sceneChanges.size() ? sceneChanges.size() : (int) (video.getSeconds() / 2);
            minSceneChanges = Math.min(maxSceneChanges, 15);
        }

        // now filter the list down (if it's too large); filter the most matching pairs first
        double scoreFilterThreshold = 0.99d;
        while (sceneChanges.size() > maxSceneChanges) {
            // try to filter by score (low similarity score), in case we have too many scenes (will run into token limits with model!)
            System.out.printf("\r\ntoo many scene changes to summarize: filter {%d} scene changes to max={%d} with threshold %.02f....\r\n", sceneChanges.size(), maxSceneChanges, scoreFilterThreshold);
            // only keep the ones with a similarity score less than the threshold!
            List<AiVideo.SceneChange> newList = new ArrayList<>();
            for (AiVideo.SceneChange scene : sceneChanges) {
                if (scene.getScore() < scoreFilterThreshold) {
                    newList.add(scene);
                }
            }
            sceneChanges = newList;
            scoreFilterThreshold -= 0.01d;
        }

        return sceneChanges;
    }
}
