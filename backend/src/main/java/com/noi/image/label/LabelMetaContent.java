package com.noi.image.label;

import java.util.Objects;

public class LabelMetaContent {
    private final String content, elementType, location, relativeSize, font, color, backgroundColor;

    private LabelMetaContent(String content, String elementType, String location, String relativeSize, String font, String color, String backgroundColor) {
        this.content = content;
        this.elementType = elementType;
        this.location = location;
        this.relativeSize = relativeSize;
        this.font = font;
        this.color = color;
        this.backgroundColor = backgroundColor;
    }

    @Override
    public String toString() {
        return "LabelMetaContent{" +
                "content='" + content + '\'' +
                ", elementType='" + elementType + '\'' +
                ", location='" + location + '\'' +
                ", relativeSize='" + relativeSize + '\'' +
                ", font='" + font + '\'' +
                ", color='" + color + '\'' +
                ", backgroundColor='" + backgroundColor + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabelMetaContent that = (LabelMetaContent) o;
        return content.equals(that.content) && elementType.equals(that.elementType) && location.equals(that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, elementType, location);
    }

    public static LabelMetaContent create(String content, String elementType, String location, String relativeSize, String font, String color, String backgroundColor) {
        return new LabelMetaContent(content, elementType, location, relativeSize, font, color, backgroundColor);
    }

    public String getContent() {
        return content;
    }

    public String getElementType() {
        return elementType;
    }

    public String getLocation() {
        return location;
    }

    public String getRelativeSize() {
        return relativeSize;
    }

    public String getFont() {
        return font;
    }

    public String getColor() {
        return color;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }
}
