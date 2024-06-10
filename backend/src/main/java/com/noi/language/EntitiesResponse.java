package com.noi.language;

import java.util.ArrayList;
import java.util.List;

public class EntitiesResponse {

    private final List<Entity> entities = new ArrayList<>();
    private final String languageCode;
    private final boolean languageSupported;

    private EntitiesResponse(String languageCode, boolean languageSupported, List<Entity> entities) {
        if (entities != null) {
            this.entities.addAll(entities);
        }
        this.languageCode = languageCode;
        this.languageSupported = languageSupported;
    }

    public static EntitiesResponse create(String languageCode, boolean languageSupported, List<Entity> entities) {
        return new EntitiesResponse(languageCode, languageSupported, entities);
    }

    @Override
    public String toString() {
        return "EntitiesResponse{" +
                "languageCode='" + languageCode + '\'' +
                ", languageSupported=" + languageSupported +
                ", entities=" + entities +
                '}';
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public boolean isLanguageSupported() {
        return languageSupported;
    }
}
