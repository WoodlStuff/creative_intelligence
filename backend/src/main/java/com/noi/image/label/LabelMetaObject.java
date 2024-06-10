package com.noi.image.label;

import java.util.Objects;

public class LabelMetaObject {
    private final String object, type, value, location, relativeSize, font, color, backgroundColor;
    private final String brand, gender;

    private LabelMetaObject(String object, String type, String value, String location, String relativeSize, String font, String color, String backgroundColor, String brand, String gender) {
        this.object = object;
        this.type = type;
        this.value = value;
        this.location = location;
        this.relativeSize = relativeSize;
        this.font = font;
        this.color = color;
        this.backgroundColor = backgroundColor;
        this.brand = brand;
        this.gender = gender;
    }

    public static LabelMetaObject create(String object, String type, String value, String location, String relativeSize, String font, String color, String backgroundColor, String brand, String gender) {
        if (object == null) {
            throw new IllegalArgumentException("o=" + object + "; t=" + type + "; v=" + value);
        }
        return new LabelMetaObject(object, type, value, location, relativeSize, font, color, backgroundColor, brand, gender);
    }

    public String getObject() {
        return object;
    }

    public String getLocation() {
        return location;
    }

    public String getRelativeSize() {
        return relativeSize;
    }

    public String getColor() {
        return color;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getFont() {
        return font;
    }

    public String getBrand() {
        return brand;
    }

    public String getGender() {
        return gender;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabelMetaObject that = (LabelMetaObject) o;
        return object.equals(that.object) && type.equals(that.type) && Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(object, type, location);
    }

    @Override
    public String toString() {
        return "LabelMetaObject{" +
                "object='" + object + '\'' +
                ", type='" + type + '\'' +
                ", value='" + value + '\'' +
                ", location='" + location + '\'' +
                ", relativeSize='" + relativeSize + '\'' +
                ", font='" + font + '\'' +
                ", color='" + color + '\'' +
                ", backgroundColor='" + backgroundColor + '\'' +
                '}';
    }
}
