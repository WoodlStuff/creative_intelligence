package com.noi.image.label;

import java.util.Objects;

public class LabelMetaData {
    private final String key, value;
    private final String requestUUID;
    private final String modelName;
    private LabelMetaObject labelMetaObject;
    private LabelMetaColorGradient labelMetaColorGradient;
    private LabelMetaShadow labelMetaShadow;

    private LabelMetaData(String key, String value) {
        this(null, null, key, value);
    }

    private LabelMetaData(String requestUUID, String modelName, String key, String value) {
        this.requestUUID = requestUUID;
        this.modelName = modelName;
        this.key = key;
        this.value = value;
    }

    public static LabelMetaData create(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException();
        }
        return new LabelMetaData(key, value);
    }

    public static LabelMetaData create(String requestUUID, String modelName, String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException();
        }
        return new LabelMetaData(requestUUID, modelName, key, value);
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
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                '}';
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
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
}
