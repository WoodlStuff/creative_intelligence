package com.noi.language;

import com.google.gson.JsonObject;
import com.noi.tools.JsonTools;

import java.util.ArrayList;
import java.util.List;

public class ClassificationResponse {
    private final String languageCode;
    private final boolean languageSupported;
    private final List<ModerationCategory> categories = new ArrayList<>();

    private ClassificationResponse(String languageCode, boolean languageSupported, List<ModerationCategory> categories) {
        this.languageCode = languageCode;
        this.languageSupported = languageSupported;
        this.categories.addAll(categories);
    }

    public static ClassificationResponse create(JsonObject responseJson, String categoryNode) {
        String languageCode = JsonTools.getAsString(responseJson, "languageCode");
        boolean languageSupported = JsonTools.getAsBoolean(responseJson, "languageSupported");
        List<ModerationCategory> categories = ModerationCategory.parseArray(responseJson, categoryNode);

        /*
        {
          "categories": [
            {
              "name": "/News/Weather",
              "confidence": 0.37365577
            }
          ],
          "languageCode": "en",
          "languageSupported": true
        }
         */
        return ClassificationResponse.create(languageCode, languageSupported, categories);
    }

    private static ClassificationResponse create(String languageCode, boolean languageSupported, List<ModerationCategory> categories) {
        return new ClassificationResponse(languageCode, languageSupported, categories);
    }

    @Override
    public String toString() {
        return "ClassificationResponse{" +
                "languageCode='" + languageCode + '\'' +
                ", languageSupported=" + languageSupported +
                ", categories=" + categories +
                '}';
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public boolean isLanguageSupported() {
        return languageSupported;
    }

    public List<ModerationCategory> getCategories() {
        return categories;
    }
}
