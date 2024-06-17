package com.noi.language;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class MetaKeyValues {
    private MetaKeyValues(String key, String values) {
        this.key = key;
        this.values = values;
    }

    public static MetaKeyValues create(String key, String values) {
        return new MetaKeyValues(key, values);
    }

    public static MetaKeyValues create(ResultSet rs) throws SQLException {
        return new MetaKeyValues(rs.getString("meta_key"), rs.getString("meta_values"));
    }

    @Override
    public String toString() {
        return "MetaKeyValues{" +
                "key='" + key + '\'' +
                ", values='" + values + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetaKeyValues that = (MetaKeyValues) o;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    public String getKey() {
        return key;
    }

    public String getValues() {
        return values;
    }

    private final String key, values;

}
