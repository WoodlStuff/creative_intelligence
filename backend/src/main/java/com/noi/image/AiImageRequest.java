package com.noi.image;

import com.noi.language.AiImageLabelRequest;
import com.noi.language.AiPrompt;
import com.noi.requests.NoiRequest;
import com.noi.Status;

public class AiImageRequest extends NoiRequest {

    private final AiPrompt prompt;

    private AiImageRequest(String uuid, AiPrompt prompt, String modelName) {
        super(null, uuid, modelName, Status.NEW);
        this.prompt = prompt;
    }

    public static AiImageRequest create(String uuid, AiPrompt prompt, String modelName) {
        return new AiImageRequest(uuid, prompt, modelName);
    }

    public static AiImageRequest create(AiPrompt prompt, String modelName) {
        return create(null, prompt, modelName);
    }

    public AiPrompt getPrompt() {
        return prompt;
    }

    @Override
    public String toString() {
        return "AiImageRequest{" +
                "prompt=" + prompt +
                "modelName=" + modelName +
                '}';
    }
}
