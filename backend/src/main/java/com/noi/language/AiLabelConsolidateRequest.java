package com.noi.language;

import com.noi.requests.NoiRequest;

import java.util.UUID;

public class AiLabelConsolidateRequest extends NoiRequest {
    private final String categoryName, words, completedPrompt;

    private AiLabelConsolidateRequest(UUID uuid, AiPrompt prompt, String categoryName, String words, String promptText) {
        super(null, prompt, prompt.getModel().getName());
        this.categoryName = categoryName;
        this.words = words;
        this.completedPrompt = promptText;
    }

    public static AiLabelConsolidateRequest create(AiPrompt prompt, String categoryName, String words) {
        // replace tokens on the prompt text:
        String promptText = prompt.getPrompt().replace("{category}", categoryName);
        promptText = promptText.replace("{category_name}", categoryName);
        promptText = promptText + ": " + words;

        return new AiLabelConsolidateRequest(null, prompt, categoryName, words, promptText);
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getWords() {
        return words;
    }

    public String getCompletedPrompt() {
        return completedPrompt;
    }
}
