package com.noi.language;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noi.tools.FileTools;
import com.noi.tools.JsonTools;
import com.noi.web.Path;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class AiPrompt {
    public static final String DEFAULT_SYSTEM_PROMPT = "Please output the information as json. The labels should be concise, no more than four words each. Please use primitive json types in the response.";
    public static final String DEFAULT_USER_PROMPT = "What’s in this image?";

    private final Long id;
    private final String prompt;
    private final int promptType;

    public static final int TYPE_IMAGE_CREATION = 0;
    public static final int TYPE_IMAGE_LABEL_CATEGORIES = 10;
    public static final int TYPE_IMAGE_LABEL_OBJECTS = 11;
    public static final int TYPE_IMAGE_LABEL_PROPERTIES = 12;

    public static final int TYPE_SCENE_CHANGE = 1;
    public static final int TYPE_AUDIO_SUMMARY = 3;
    public static final int TYPE_VIDEO_SUMMARY = 4;

    public static final int TYPE_PROMPT = 2;

    private AiPrompt(Long id, String prompt, int promptType) {
        this.id = id;
        this.prompt = prompt;
        this.promptType = promptType;
    }

    public static AiPrompt create(Long id, String prompt) {
        return create(id, prompt, TYPE_IMAGE_CREATION);
    }

    public static AiPrompt create(Long id, String prompt, int promptType) {
        return new AiPrompt(id, prompt, promptType);
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

    public int getPromptType() {
        return promptType;
    }

    public String getSystemPrompt() {
// for prompt 1:       return "Please output the information as json. The labels should be concise, no more than four words each. Objects in the image should contain a name, a type, a primary color, the background color, the location within the image, a relative size, and any brand and gender information. Overlay text should contain the text content, the font and location within the image, and a relative size. The layout should list the aspect ratio, image orientation, and any dominant element in the image.";
        return "Please output the information as json. The labels should be concise, no more than four words each. Please use primitive json types in the response.";
    }
}
