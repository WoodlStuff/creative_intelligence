package com.noi.language;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.noi.tools.JsonTools;

import java.util.ArrayList;
import java.util.List;

public class ModerationCategory {
    private final String name;
    private final double confidence;

    private ModerationCategory(String name, double confidence) {
        this.name = name;
        this.confidence = confidence;
    }

    public static List<ModerationCategory> parseArray(JsonObject responseJson, String jsonLabel) {
        List<ModerationCategory> categories = new ArrayList<>();
        JsonArray array = responseJson.getAsJsonArray(jsonLabel);
        if (array != null && !array.isJsonNull()) {
            for (int i = 0; i < array.size(); i++) {
                categories.add(ModerationCategory.parse(array.get(i).getAsJsonObject()));
            }
        }
        /*
        "categories": [
            {
              "name": "/News/Weather",
              "confidence": 0.37365577
            }
          ],
         */

        return categories;
    }

    private static ModerationCategory parse(JsonObject jsonObject) {
        String name = JsonTools.getAsString(jsonObject, "name");
        double confidence = JsonTools.getAsDouble(jsonObject, "confidence");

        /*
        {
          "name": "/News/Weather",
          "confidence": 0.37365577
        }
         */
        return new ModerationCategory(name, confidence);
    }

    @Override
    public String toString() {
        return "ModerationCategory{" +
                "name='" + name + '\'' +
                ", confidence=" + confidence +
                '}';
    }

    public String getName() {
        return name;
    }

    public double getConfidence() {
        return confidence;
    }
}
