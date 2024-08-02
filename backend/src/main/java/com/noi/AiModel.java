package com.noi;

import com.noi.image.label.GoogleVisionLabelService;
import com.noi.llm.google.GoogleService;
import com.noi.llm.midjourney.MidJourneyService;
import com.noi.llm.openai.OpenAIService;
import com.noi.requests.NoiRequest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AiModel implements Comparable<AiModel> {
    private final String name;
    private final String serviceName;
    private final Long id;

    private static final Map<String, AiModel> models = new HashMap<>();

    public static final AiModel GPT_3 = new AiModel(OpenAIService.NAME, "gpt-3.5-turbo");
    public static final AiModel GPT_4 = new AiModel(OpenAIService.NAME, "gpt-4");
    public static final AiModel GPT_4o = new AiModel(OpenAIService.NAME, "gpt-4o");
    public static final AiModel DALL_E_3 = new AiModel(OpenAIService.NAME, "dall-e-3");
    public static final AiModel DALL_E_2 = new AiModel(OpenAIService.NAME, "dall-e-2");

    // The latest GPT-4 Turbo model with vision capabilities.
    public static final AiModel GPT_4_TURBO = new AiModel(OpenAIService.NAME, "gpt-4-turbo");
    public static final AiModel WHISPER_1 = new AiModel(OpenAIService.NAME, "whisper-1");
    public static final AiModel TEXT_EMBEDDING_3_SMALL = new AiModel(OpenAIService.NAME, "text-embedding-3-small");

    public static final AiModel MID_JOURNEY = new AiModel(MidJourneyService.NAME, "midjourney");
    public static final AiModel GOOGLE_VISION = new AiModel(GoogleService.NAME, GoogleVisionLabelService.MODEL_NAME);

    public static final AiModel PY_INTERNAL_SCORING = new AiModel("ORB");
    public static final AiModel DEFAULT_VISION_MODEL = GPT_4o;

    private AiModel(Long id, String name, String serviceName) {
        this.id = id;
        this.name = name;
        this.serviceName = serviceName;

        models.put(name.toLowerCase(), this);
    }

    private AiModel(String name) {
        this(null, name);
    }

    private AiModel(String serviceName, String name) {
        this(null, name, serviceName);
    }

    private AiModel(Long id, AiModel model) {
        this(id, model.getName(), model.getServiceName());
    }

    public static String getModel(NoiRequest request) {
        return request.getModelName();
    }

    public static AiModel create(ResultSet rs) throws SQLException {
        AiModel model = getModel(rs.getString("name"));
        if (model == null) {
            throw new IllegalStateException();
        }
        return new AiModel(rs.getLong("id"), model);
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

    public String getServiceName() {
        return serviceName;
    }
}
