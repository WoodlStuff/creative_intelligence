package com.noi.image.label;

import java.util.Objects;

public class LabelMetaShadow {
    private final String subject, description;

    private LabelMetaShadow(String subject, String description) {
        this.subject = subject;
        this.description = description;
    }

    public static LabelMetaShadow create(String subject, String description) {
        return new LabelMetaShadow(subject, description);
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabelMetaShadow that = (LabelMetaShadow) o;
        return subject.equals(that.subject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject);
    }

    @Override
    public String toString() {
        return "LabelMetaShadow{" +
                "subject='" + subject + '\'' +
                ", desciption='" + description + '\'' +
                '}';
    }
}
