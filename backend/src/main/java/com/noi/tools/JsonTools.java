package com.noi.tools;

import com.google.gson.*;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class JsonTools {
    public static String getAsString(JsonObject object, String node) {
        JsonElement n = getNodeElement(object, node);
        if (n == null) return null;
        try {
            return n.getAsString();
        } catch (UnsupportedOperationException e) {
            System.out.println("WARNING:" + node + ":" + e.getMessage());
            return n.toString();
        }
    }

    public static String getAsStringCsl(JsonObject object, String node) {
        JsonElement n = getNodeElement(object, node);
        if (n == null) return null;

        if (n.isJsonArray()) {
            // flatten the array to a comma separated list
            StringBuilder b = new StringBuilder();
            JsonArray array = n.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    b.append(",");
                }
                b.append(array.get(i).getAsString());
            }
            return b.toString();
        } else if (n.isJsonPrimitive()) {
            return n.getAsString();
        }

        return n.toString().replace("{", "").replace("}", "");
    }

    public static String getAsString(JsonObject object, String node, String defaultValue) {
        String v = getAsString(object, node);
        if (v == null) {
            return defaultValue;
        }
        return v;
    }

    public static String[] getAsStringArray(JsonObject object, String node) {
        JsonElement n = getNodeElement(object, node);
        if (n == null) {
            return new String[]{};
        }

        if (!n.isJsonArray()) {
            throw new IllegalStateException();
        }
        JsonArray array = n.getAsJsonArray();
        String[] values = new String[array.size()];
        for (int i = 0; i < array.size(); i++) {
            values[i] = array.get(i).getAsString();
        }

        return values;
    }

    public static JsonObject getAsObject(JsonObject object, String node) {
        JsonElement n = getNodeElement(object, node);
        if (n == null) return null;
        return n.getAsJsonObject();
    }

    public static long getAsLong(JsonObject object, String node, Long defaultValue) {
        JsonElement n = getNodeElement(object, node);
        if (n == null) return defaultValue;
        return n.getAsLong();
    }

    public static long getAsLong(JsonObject object, String node) {
        return getAsLong(object, node, 0L);
    }

    public static int getAsInt(JsonObject object, String node) {
        return getAsInt(object, node, 0);
    }

    public static int getAsInt(JsonObject object, String node, int defaultValue) {
        JsonElement n = getNodeElement(object, node);
        if (n == null) return defaultValue;
        return n.getAsInt();
    }

    public static boolean getAsBoolean(JsonObject object, String node) {
        JsonElement n = getNodeElement(object, node);
        if (n == null) return false;
        return n.getAsBoolean();
    }

    public static double getAsDouble(JsonObject object, String node) {
        JsonElement n = getNodeElement(object, node);
        if (n == null) return 0.00d;
        return n.getAsDouble();
    }

    private static JsonElement getNodeElement(JsonObject object, String node) {
        if (object == null || object.isJsonNull()) {
            return null;
        }
        JsonElement n = object.get(node);
        if (n == null || n.isJsonNull()) {
            return null;
        }
        return n;
    }

    public static void addProperty(JsonObject jsonObject, String name, String value) {
        if (jsonObject == null || name == null || value == null) {
            return;
        }

        jsonObject.addProperty(name, value);
    }

    public static JsonObject toJson(InputStream inputStream) throws IOException {
        String jsonString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        return new JsonParser().parse(jsonString).getAsJsonObject();
    }

    public static String toString(JsonObject object) {
        return new Gson().toJson(object);
    }

    public static JsonElement toJsonArray(String[] elements) {
        JsonArray array = new JsonArray();
        for (String element : elements) {
            array.add(element);
        }
        return array;
    }

    public static JsonElement toJsonArray(int[] ints) {
        JsonArray array = new JsonArray();
        for (int element : ints) {
            array.add(element);
        }
        return array;
    }

    public static void main(String[] args) {
//        String json = "{\"colors\":{\"hair\":\"brown\",\"suit\":\"black\",\"shirt\":\"white\",\"tie\":\"maroon\"}}";
        String json = "{\"colors\":[\"blue\",\"red\",\"green\"]}";
        JsonElement root = new JsonParser().parse(json);
        JsonElement colors = root.getAsJsonObject().get("colors");
        if (colors.isJsonArray()) {
//            String csl = parseArrayToCsl(colors);
//            System.out.println(csl);
            System.out.println(colors.toString().replace("[", "").replace("]", "").replace("\"", ""));
        } else {
            if (colors.isJsonPrimitive()) {
                System.out.println(colors.getAsString());
            } else {
                System.out.println(colors.toString());

            }
        }
        // {"hair":"brown","suit":"black","shirt":"white","tie":"maroon"}
        System.out.println("done!");
    }
}
