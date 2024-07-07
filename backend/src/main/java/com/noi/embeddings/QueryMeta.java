package com.noi.embeddings;

import com.google.gson.JsonObject;

public class QueryMeta {
    private final Long videoId;

    private QueryMeta(Long videoId) {
        this.videoId = videoId;
    }

    public static QueryMeta create(Long videoId) {
        return new QueryMeta(videoId);
    }

    public Long getVideoId() {
        return videoId;
    }

    @Override
    public String toString() {
        return "QueryMeta{" +
                "videoId=" + videoId +
                '}';
    }

    public JsonObject toJson() {
        JsonObject meta = new JsonObject();
        meta.addProperty("video-id", videoId);
        return meta;
    }
}
