package com.noi.image.label;

import java.util.Objects;

public class LabelMetaColorGradient {
    private LabelMetaColorGradient(String location, String description) {
        this.location = location;
        this.description = description;
    }

    private final String location, description;

    public static LabelMetaColorGradient create(String location, String description) {
        return new LabelMetaColorGradient(location, description);
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabelMetaColorGradient that = (LabelMetaColorGradient) o;
        return location.equals(that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(location);
    }

    @Override
    public String toString() {
        return "LabelMetaColorGradient{" +
                "location='" + location + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
