package com.noi.language;

import com.noi.requests.NoiRequest;

import java.util.UUID;

public class AiLabelConsolidateRequest extends NoiRequest {
    private final String categoryName, words;

    private AiLabelConsolidateRequest(UUID uuid, AiPrompt prompt, String categoryName, String words) {
        super(null, prompt, prompt.getModel().getName());
        this.categoryName = categoryName;
        this.words = words;
    }

    public static AiLabelConsolidateRequest create(AiPrompt prompt, String categoryName, String words) {
        return new AiLabelConsolidateRequest(null, prompt, categoryName, words);
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getWords() {
        return words;
    }
}
