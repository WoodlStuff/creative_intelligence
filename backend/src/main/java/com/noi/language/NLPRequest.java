package com.noi.language;

import com.noi.requests.NoiRequest;

import java.util.UUID;

public class NLPRequest extends NoiRequest {
    private final AiPrompt prompt;

    protected NLPRequest(UUID uuid, AiPrompt prompt, String modelName) {
        super(uuid, modelName);
        this.prompt = prompt;
    }

    public static NLPRequest create(AiPrompt prompt, String modelName) {
        return new NLPRequest(null, prompt, modelName);
    }

    @Override
    public String toString() {
        return "NLPRequest{" +
                "prompt=" + prompt +
                '}';
    }

    public AiPrompt getPrompt() {
        return prompt;
    }
}
