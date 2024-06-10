package com.noi.language;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

public class MetaData {
    private final Map<String, String> meta = new HashMap<>();

    private MetaData() {
    }

    public static MetaData parse(JsonObject json, String jsonLabel) {
        // { "name": "wrench", "mass": "1.3kg", "count": "3" }.
        MetaData metaData = new MetaData();
        JsonObject metaJson = json.getAsJsonObject(jsonLabel);
        if (metaJson != null && !metaJson.isJsonNull()) {
            for (Map.Entry<String, JsonElement> entry : metaJson.entrySet()) {
                metaData.meta.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        return metaData;
    }

    public Map<String, String> getMeta() {
        return meta;
    }
}
