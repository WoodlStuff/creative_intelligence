package com.noi.image.label;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class LabelMetaData {
    private final String key, value;
    private final int distinctValueCount;
    private final String requestUUID;
    private final Long promptId;

    private final String modelName;
    private LabelMetaObject labelMetaObject;
    private LabelMetaColorGradient labelMetaColorGradient;
    private LabelMetaShadow labelMetaShadow;

    private LabelMetaData(String key, String value) {
        this(null, null, null, key, value, 1);
    }

    private LabelMetaData(String requestUUID, Long promptId, String modelName, String key, String value, int distinctValueCount) {
        this.requestUUID = requestUUID;
        if (promptId != null && promptId > 0L) {
            this.promptId = promptId;
        } else {
            this.promptId = null;
        }
        this.modelName = modelName;
        this.key = key;
        this.value = value;
        this.distinctValueCount = distinctValueCount;
    }

    public static LabelMetaData create(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException();
        }
        return new LabelMetaData(key, value);
    }

    public static LabelMetaData create(ResultSet rs) throws SQLException {
        return create(rs.getString("label_request_uuid"), rs.getLong("ai_prompt_id"), rs.getString("model_name"), rs.getString("meta_key"), rs.getString("meta_values"), rs.getInt("d_value_count"));
    }

    public static LabelMetaData create(String requestUUID, Long promptId, String modelName, String key, String value, int distinctValueCount) {
        if (key == null || value == null) {
            throw new IllegalArgumentException();
        }
        return new LabelMetaData(requestUUID, promptId, modelName, key, value, distinctValueCount);
    }

    public static LabelMetaData create(String key, String value, LabelMetaObject labelMetaObject) {
        LabelMetaData meta = create(key, value);
        meta.add(labelMetaObject);
        return meta;
    }

    protected void add(LabelMetaObject labelMetaObject) {
        this.labelMetaObject = labelMetaObject;
    }

    public LabelMetaObject getLabelMetaObject() {
        return labelMetaObject;
    }

    public LabelMetaColorGradient getLabelMetaColorGradient() {
        return labelMetaColorGradient;
    }

    public LabelMetaShadow getLabelMetaShadow() {
        return labelMetaShadow;
    }

    public boolean hasMetaObject() {
        return labelMetaObject != null;
    }

    public boolean hasMetaColorGradient() {
        return labelMetaColorGradient != null;
    }

    public boolean hasMetaShadow() {
        return labelMetaShadow != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabelMetaData data = (LabelMetaData) o;
        return key.equals(data.key) && value.equals(data.value) && Objects.equals(labelMetaObject, data.labelMetaObject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value, labelMetaObject);
    }

    @Override
    public String toString() {
        return "LabelMetaData{" +
                "promptId=" + promptId +
                ", modelName='" + modelName + '\'' +
                ", requestUUID='" + requestUUID + '\'' +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", distinctValueCount=" + distinctValueCount +
                '}';
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public int getDistinctValueCount() {
        return distinctValueCount;
    }

    public String getRequestUUID() {
        return requestUUID;
    }

    public String getModelName() {
        return modelName;
    }

    public void add(LabelMetaColorGradient colorGradient) {
        this.labelMetaColorGradient = colorGradient;
    }

    public void add(LabelMetaShadow shadow) {
        this.labelMetaShadow = shadow;
    }

    public Long getPromptId() {
        return promptId;
    }
}
