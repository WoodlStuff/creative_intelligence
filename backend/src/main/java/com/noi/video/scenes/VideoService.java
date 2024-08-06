package com.noi.video.scenes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.noi.language.MetaKeyImages;
import com.noi.language.MetaKeyValues;
import com.noi.models.*;
import com.noi.tools.FileTools;
import com.noi.tools.SystemEnv;
import com.noi.video.AiVideo;
import com.noi.video.VideoFrameMoment;

import javax.naming.NamingException;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

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

    public static JsonObject readAndFormatVideoStoryData(Connection con, Long videoId) throws SQLException {
        JsonObject root = new JsonObject();
        JsonObject story = new JsonObject();
        root.add("story", story);

        List<VideoFrameMoment> moments = DbVideo.findSummaryFrames(con, videoId);
        JsonArray momentsArray = new JsonArray();
        story.add("moments", momentsArray);
        for (VideoFrameMoment moment : moments) {
            JsonObject mom = new JsonObject();
            mom.addProperty("image_id", moment.getImageId());
            mom.addProperty("image_url", moment.getImageURL());
            mom.addProperty("video_frame_number", moment.getFrameNumber());
            mom.addProperty("frame_rate", moment.getVideoFrameRate());
            mom.addProperty("seconds", moment.getSecondsFromStart());

            momentsArray.add(mom);
        }

        Set<String> catNames = new TreeSet<>();
        JsonArray categories = new JsonArray();
        story.add("categories", categories);

        // Map<image_id> => Map<Category-Name> => KV-Pair[k,v]
        Map<Long, Map<String, List<MetaKeyValues>>> metaData = DbImageLabel.findImageCategoryKeyGroups(con, videoId);
        for (Map.Entry<Long, Map<String, List<MetaKeyValues>>> entry : metaData.entrySet()) {
            // one array per image
            JsonObject image = new JsonObject();
            categories.add(image);
            image.addProperty("image_id", entry.getKey());

            JsonArray storyElements = new JsonArray();
            image.add("story_elements", storyElements);

            for (Map.Entry<String, List<MetaKeyValues>> metaEntry : entry.getValue().entrySet()) {
                String categoryName = metaEntry.getKey();
                if (!catNames.contains(categoryName)) {
                    catNames.add(categoryName);
                }

                for (MetaKeyValues kv : metaEntry.getValue()) {
                    JsonObject storyElement = new JsonObject();
                    storyElements.add(storyElement);
                    storyElement.addProperty("category_name", categoryName);
                    storyElement.addProperty("key", kv.getKey());
                    storyElement.addProperty("values", kv.getValues());
                }
            }
        }

        // add a map of image counts by category
        // select category_name, meta_key, meta_value, count(distinct ai_image_id) image_count, group_concat(distinct ai_image_id) image_ids from (select ai_image_id, name category_name, meta_key, meta_value, count(*) _count from (select distinct lmc.ai_image_id, i.video_frame_number, c.name, lmc.meta_key, lmc.meta_value from ai_image_label_meta_categories lmc join meta_categories c on c.id = lmc.meta_category_id join ai_images i on i.id = lmc.ai_image_id where i.ai_video_id=@video_id and name=@category)a group by 1,2,3,4)x group by 1,2,3 having count(distinct ai_image_id) > 1 order by count(distinct ai_image_id), category_name, meta_key, meta_value;
        JsonArray catImages = new JsonArray();
        story.add("category_images", catImages);
        Map<String, List<MetaKeyImages>> categoryImages = DbImageLabel.findCategoryImages(con, videoId);
        for (Map.Entry<String, List<MetaKeyImages>> entry : categoryImages.entrySet()) {
            for (MetaKeyImages mki : entry.getValue()) {
                JsonObject categoryImageCounts = new JsonObject();
                catImages.add(categoryImageCounts);
                categoryImageCounts.addProperty("category_name", entry.getKey());
                categoryImageCounts.addProperty("key", mki.getMetaKey());
                categoryImageCounts.addProperty("value", mki.getMetaValue());
                categoryImageCounts.add("image_ids", mki.getImageIds());
            }
        }

        // add unique category names as a separate array
        JsonArray categoryNames = new JsonArray();
        story.add("category_names", categoryNames);
        List<String> sorted = new ArrayList<>(catNames);
        System.out.println("sorted has a size of: " + sorted.size());
        Collections.sort(sorted);
        for (String catName : sorted) {
            categoryNames.add(catName);
        }

        return root;
    }

    public static File getSoundURL(AiVideo video) {
        String rootFolder = SystemEnv.get("NOI_PATH", "/Users/martin/work/tmp/ai-data");
        String videoFileName = FileTools.getFileName(video.getUrl(), false);
        File audioPath = FileTools.joinPath(rootFolder, "videos", videoFileName, videoFileName + ".mp3");
        System.out.println("OpenAIService:transcribeVideo:audioPath=" + audioPath);
        if (audioPath.exists()) {
            return audioPath;
        }
        return null;
    }
}
