package com.noi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class AiBrand {

    private final Long id;
    private final String name;
    private final Status status;

    private AiBrand(Long id, String name, int status, Type brandType) {
        this.id = id;
        this.name = name;
        this.status = Status.parse(status);
        this.brandType = brandType;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Status getStatus() {
        return status;
    }

    public Type getBrandType() {
        return brandType;
    }

    @Override
    public String toString() {
        return "AiBrand{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", brandType=" + brandType +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiBrand aiBrand = (AiBrand) o;
        return name.equals(aiBrand.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    private final Type brandType;

    public static Type createType(ResultSet rs) throws SQLException {
        return new Type(rs.getLong("id"), rs.getString("name"), rs.getInt("status"));
    }

    public static AiBrand create(ResultSet rs, Type brandType) throws SQLException {
        return new AiBrand(rs.getLong("id"), rs.getString("name"), rs.getInt("status"), brandType);
    }

    public static class Type {
        private final Long id;
        private final String name;
        private final Status status;

        private Type(Long id, String name, int status) {
            this.id = id;
            this.name = name;
            this.status = Status.parse(status);
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Status getStatus() {
            return status;
        }

        @Override
        public String toString() {
            return "Type{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", status=" + status +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Type type = (Type) o;
            return name.equals(type.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }
}
