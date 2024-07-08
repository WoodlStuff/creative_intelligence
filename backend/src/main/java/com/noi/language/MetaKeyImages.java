package com.noi.language;

import com.google.gson.JsonArray;

import java.sql.ResultSet;
import java.sql.SQLException;

public class MetaKeyImages {
    private final String metaKey, metaValue;
    private final JsonArray ids;

    private MetaKeyImages(String metaKey, String metaValue, JsonArray ids) {
        this.metaKey = metaKey;
        this.metaValue = metaValue;
        this.ids = ids;
    }

    public static MetaKeyImages create(ResultSet rs) throws SQLException {
        JsonArray jsonIds = new JsonArray();
        // convert the comma separated list of ids to a json array
        String ids = rs.getString("image_ids"); // comma separated long ids
        for (String id : ids.split(",")) {
            jsonIds.add(Long.valueOf(id.trim()));
        }
        return new MetaKeyImages(rs.getString("meta_key"), rs.getString("meta_value"), jsonIds);
    }

    public String getMetaKey() {
        return metaKey;
    }

    public String getMetaValue() {
        return metaValue;
    }

    public JsonArray getImageIds() {
        return ids;
    }
}
