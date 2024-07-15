package com.noi.embeddings;

import com.google.gson.JsonObject;
import com.noi.image.AiImage;

public class QueryMeta {
    private final AiImage image;
    private final boolean sameVideo;

    private QueryMeta(AiImage image, boolean sameVideo) {
        this.image = image;
        this.sameVideo = sameVideo;
    }

    public static QueryMeta create(AiImage image, boolean sameVideo) {
        return new QueryMeta(image, sameVideo);
    }

    public Long getVideoId() {
        return image.getVideoId();
    }

    public boolean isSameVideo() {
        return sameVideo;
    }

    @Override
    public String toString() {
        return "QueryMeta{" +
                "image=" + image +
                "sameVideo=" + sameVideo +
                '}';
    }

    public JsonObject toJson() {
        JsonObject meta = new JsonObject();
        meta.addProperty("video_id", image.getVideoId());
        if (image.getBrand() != null) {
            meta.addProperty("brand", image.getBrand().getName());
        }
        return meta;
    }
}
