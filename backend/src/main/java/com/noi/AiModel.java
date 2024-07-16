package com.noi;

import com.noi.image.label.GoogleVisionLabelService;
import com.noi.requests.NoiRequest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AiModel implements Comparable<AiModel> {
    private final String name;
    private final Long id;

    private static final Map<String, AiModel> models = new HashMap<>();

    public static final AiModel GPT_3 = new AiModel("gpt-3.5-turbo");
    public static final AiModel GPT_4 = new AiModel("gpt-4");
    public static final AiModel GPT_4o = new AiModel("gpt-4o");
    public static final AiModel DALL_E_3 = new AiModel("dall-e-3");
    public static final AiModel DALL_E_2 = new AiModel("dall-e-2");

    // The latest GPT-4 Turbo model with vision capabilities.
    public static final AiModel GPT_4_TURBO = new AiModel("gpt-4-turbo");
    public static final AiModel WHISPER_1 = new AiModel("whisper-1");
    public static final AiModel TEXT_EMBEDDING_3_SMALL = new AiModel("text-embedding-3-small");

    public static final AiModel MID_JOURNEY = new AiModel("midjourney");
    public static final AiModel GOOGLE_VISION = new AiModel(GoogleVisionLabelService.MODEL_NAME);

    public static final AiModel PY_INTERNAL_SCORING = new AiModel("ORB");
    public static final AiModel DEFAULT_VISION_MODEL = GPT_4o;

    private AiModel(Long id, String name) {
        this.id = id;
        this.name = name;
        models.put(name.toLowerCase(), this);
    }

    private AiModel(String name) {
        this(null, name);
    }

    public static String getModel(NoiRequest request) {
        return request.getModelName();
    }

    public static AiModel create(ResultSet rs) throws SQLException {
        return new AiModel(rs.getLong("id"), rs.getString("name"));
    }

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    @Override
    public String toString() {
        return "AiModel{" +
                "name='" + name + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiModel aiModel = (AiModel) o;
        return name.equals(aiModel.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public static AiModel getModel(String name) {
        return models.get(name.toLowerCase());
    }

    @Override
    public int compareTo(AiModel o) {
        return this.name.compareTo(o.name);
    }
}
