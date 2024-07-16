package com.noi.language;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noi.AiModel;
import com.noi.Status;
import com.noi.tools.FileTools;
import com.noi.tools.JsonTools;
import com.noi.web.Path;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class AiPrompt {
    public static final String DEFAULT_SYSTEM_PROMPT = "Please output the information as json. The labels should be concise, no more than four words each. Please use primitive json types in the response.";
    public static final String DEFAULT_USER_PROMPT = "What’s in this image?";

    private final Long id;
    private final AiModel model;
    private final String prompt, systemPrompt, name;
    private final int promptType;
    private final Status status;

    public static final Type TYPE_IMAGE_CREATION = new Type(0, "image_creation");
    public static final Type TYPE_IMAGE_LABEL_CATEGORIES = new Type(10, "label_categories");
    public static final Type TYPE_IMAGE_LABEL_OBJECTS = new Type(11, "image_label_objects");
    public static final Type TYPE_IMAGE_LABEL_PROPERTIES = new Type(12, "image_label_properties");

    public static final Type TYPE_SCENE_CHANGE = new Type(1, "scene_change");
    public static final Type TYPE_AUDIO_SUMMARY = new Type(3, "audio_summary");
    public static final Type TYPE_VIDEO_SUMMARY = new Type(4, "video_summary");

    public static final int TYPE_PROMPT = 2;

    private AiPrompt(Long id, AiModel model, String name, String prompt, int promptType, String systemPrompt, Status status) {
        this.id = id;
        this.model = model;
        this.name = name;
        this.prompt = prompt;
        this.promptType = promptType;
        this.systemPrompt = systemPrompt;
        this.status = status;
    }

    public static AiPrompt create(Long id, String prompt) {
        return create(id, prompt, TYPE_IMAGE_CREATION.getType());
    }

    public static AiPrompt create(Long id, String prompt, int promptType) {
        return new AiPrompt(id, null, null, prompt, promptType, null, Status.ACTIVE);
    }

    public static AiPrompt create(ResultSet rs, AiModel model) throws SQLException {
        Long id = rs.getLong("id");
        String name = rs.getString("name");
        String prompt = rs.getString("prompt");
        String systemPrompt = rs.getString("system_prompt");
        int promptType = rs.getInt("prompt_type");
        int status = rs.getInt("status");
        return new AiPrompt(id, model, name, prompt, promptType, systemPrompt, Status.parse(status));
    }

    public static AiPrompt create(Long id, String prompt, int promptType, String systemPrompt, int status) {
        return create(id, null, null, prompt, promptType, systemPrompt, Status.parse(status));
    }

    public static AiPrompt create(Long id, String name, AiModel model, String prompt, int promptType, String systemPrompt, Status status) {
        return new AiPrompt(id, model, name, prompt, promptType, systemPrompt, status);
    }

    public static AiPrompt create(Long id, AiPrompt prompt) {
        return new AiPrompt(id, prompt.getModel(), prompt.getName(), prompt.getPrompt(), prompt.getPromptType(), prompt.getSystemPrompt(), prompt.getStatus());
    }

    public static AiPrompt parse(HttpServletRequest req, Path path, int promptIdIndex) throws IOException {
        String[] pathTokens = path.getPathInfo().split("/");

        int promptType = -1;
        Long promptId = null;
        String prompt = null;

        if (pathTokens.length > promptIdIndex) {
            System.out.println("finding prompt from db for id=" + pathTokens[promptIdIndex]);
            String id = pathTokens[promptIdIndex].trim();
            promptId = Long.valueOf(id);

        } else {
            System.out.println("creating prompt from json post...");
            // was the prompt text posted?
            String jsonInput = FileTools.readInputStream(req.getInputStream());
            if (jsonInput == null) {
                throw new IllegalArgumentException();
            }

            JsonObject root = new JsonParser().parse(jsonInput).getAsJsonObject();
            prompt = JsonTools.getAsString(root, "prompt");
            if (prompt == null || prompt.isEmpty()) {
                throw new IllegalArgumentException("missing prompt param!");
            }

            promptType = JsonTools.getAsInt(root, "prompt_type", -1);
        }

        return AiPrompt.create(promptId, prompt, promptType);
    }

    public static List<Type> getTypes() {
        return new ArrayList<>(Type.types.values());
    }

    @Override
    public String toString() {
        return "AiPrompt{" +
                "id=" + id +
                ", prompt='" + prompt + '\'' +
                '}';
    }

    public Long getId() {
        return id;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public int getPromptType() {
        return promptType;
    }

    public Status getStatus() {
        return status;
    }

    public AiModel getModel() {
        return model;
    }

    public String getName() {
        return name;
    }

    //    public String getSystemPrompt() {
//// for prompt 1:       return "Please output the information as json. The labels should be concise, no more than four words each. Objects in the image should contain a name, a type, a primary color, the background color, the location within the image, a relative size, and any brand and gender information. Overlay text should contain the text content, the font and location within the image, and a relative size. The layout should list the aspect ratio, image orientation, and any dominant element in the image.";
//        return "Please output the information as json. The labels should be concise, no more than four words each. Please use primitive json types in the response.";
//    }

    public static class Type implements Comparable<Type> {
        private final int type;
        private final String name;

        private static Map<Integer, Type> types = new HashMap<>();

        private Type(int type, String name) {
            this.type = type;
            this.name = name;
            this.types.put(type, this);
        }

        public static Type parse(int type) {
            return types.get(type);
        }

        @Override
        public String toString() {
            return "Type{" +
                    "type=" + type +
                    ", name='" + name + '\'' +
                    '}';
        }

        public int getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Type type1 = (Type) o;
            return type == type1.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type);
        }

        @Override
        public int compareTo(Type o) {
            return this.name.compareTo(o.name);
        }
    }
}
