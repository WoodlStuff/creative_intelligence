package com.noi.embeddings;

import com.google.gson.JsonObject;

public class QueryMeta {
    private final Long videoId;
    private final boolean sameVideo;

    private QueryMeta(Long videoId, boolean sameVideo) {
        this.videoId = videoId;
        this.sameVideo = sameVideo;
    }

    public static QueryMeta create(Long videoId, boolean sameVideo) {
        return new QueryMeta(videoId, sameVideo);
    }

    public Long getVideoId() {
        return videoId;
    }

    public boolean isSameVideo() {
        return sameVideo;
    }

    @Override
    public String toString() {
        return "QueryMeta{" +
                "videoId=" + videoId +
                '}';
    }

    public JsonObject toJson() {
        JsonObject meta = new JsonObject();
        meta.addProperty("video_id", videoId);
        return meta;
    }
}
