package com.noi.embeddings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class VectorMatch {
    private final Long id;
    private final double score;
    private final JsonArray values;
    private final JsonObject meta;

    private VectorMatch(Long id, double score, JsonArray values, JsonObject meta) {
        this.id = id;
        this.score = score;
        this.values = values;
        this.meta = meta;
    }

    public static VectorMatch parse(JsonObject match) {
        Long id = match.get("id").getAsLong();
        double score = match.get("score").getAsDouble();
        JsonArray values = match.get("values").getAsJsonArray();
        JsonElement metadata = match.get("metadata");
        JsonObject meta = null;
        if (metadata != null && !metadata.isJsonNull()) {
            meta = metadata.getAsJsonObject();
        }
        return new VectorMatch(id, score, values, meta);
    }

    public Long getId() {
        return id;
    }

    public double getScore() {
        return score;
    }

    public JsonArray getValues() {
        return values;
    }

    public JsonObject getMeta() {
        return meta;
    }

    @Override
    public String toString() {
        return "VectorMatch{" +
                "id=" + id +
                ", score=" + score +
                ", values=" + values +
                '}';
    }
}
