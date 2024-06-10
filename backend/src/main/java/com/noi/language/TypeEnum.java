package com.noi.language;

import com.google.gson.JsonObject;
import com.noi.tools.JsonTools;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// @see https://cloud.google.com/natural-language/docs/reference/rest/v2/Entity#Type
public class TypeEnum {
    private final String type, name;

    private static final Map<String, TypeEnum> types = new HashMap<>();

    private TypeEnum(String type, String name) {
        this.type = type;
        this.name = name;
        types.put(type, this);
    }

    public static TypeEnum parse(JsonObject entityJson, String jsonLabel) {
        String type = JsonTools.getAsString(entityJson, jsonLabel);
        return TypeEnum.parse(type);
    }

    public static TypeEnum parse(String type) {
        TypeEnum t = types.get(type);
        if (t == null) {
            System.out.println("unknown Type [" + type + "]");
        }
        return t;
    }

    @Override
    public String toString() {
        return "TypeEnum{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeEnum typeEnum = (TypeEnum) o;
        return type.equals(typeEnum.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public static final TypeEnum UNKNOWN = new TypeEnum("UNKNOWN", "Unknown");
    public static final TypeEnum PERSON = new TypeEnum("PERSON", "Person");
    public static final TypeEnum LOCATION = new TypeEnum("LOCATION", "Location");
    public static final TypeEnum ORGANIZATION = new TypeEnum("ORGANIZATION", "Organization");
    public static final TypeEnum EVENT = new TypeEnum("EVENT", "Event");
    public static final TypeEnum WORK_OF_ART = new TypeEnum("WORK_OF_ART", "Artwork");
    public static final TypeEnum CONSUMER_GOOD = new TypeEnum("CONSUMER_GOOD", "Consumer product");
    public static final TypeEnum OTHER = new TypeEnum("OTHER", "Other types of entities");
    public static final TypeEnum PHONE_NUMBER = new TypeEnum("PHONE_NUMBER", "Phone number");
    public static final TypeEnum ADDRESS = new TypeEnum("ADDRESS", "Address");
    public static final TypeEnum DATE = new TypeEnum("DATE", "Date");
    public static final TypeEnum NUMBER = new TypeEnum("NUMBER", "Number");
    public static final TypeEnum PRICE = new TypeEnum("PRICE", "Price");
    public static final TypeEnum PLAIN_TEXT = new TypeEnum("PLAIN_TEXT", "PLAIN_TEXT");
    public static final TypeEnum TYPE_UNKNOWN = new TypeEnum("TYPE_UNKNOWN", "Unknown");
    public static final TypeEnum PROPER = new TypeEnum("PROPER", "Proper name");
    public static final TypeEnum COMMON = new TypeEnum("COMMON", "Common noun");
}
