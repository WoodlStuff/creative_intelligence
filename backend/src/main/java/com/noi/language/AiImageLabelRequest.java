package com.noi.language;

import com.noi.AiModel;
import com.noi.requests.NoiRequest;

public class AiImageLabelRequest extends NoiRequest {
    private final Long imageId;

    private AiImageLabelRequest(Long imageId, AiPrompt prompt, String modelName) {
        super(null, prompt, modelName);
        this.imageId = imageId;
    }

    public static AiImageLabelRequest create(Long imageId, AiPrompt prompt, AiModel model) {
        // todo: change base class to accept the AiModel (not the name)
        return new AiImageLabelRequest(imageId, prompt, model.getName());
    }

    public static AiImageLabelRequest create(Long id, Long imageId, AiPrompt prompt, String modelName) {
        AiImageLabelRequest request = new AiImageLabelRequest(imageId, prompt, modelName);
        request.setId(id);
        return request;
    }

    public Long getImageId() {
        return imageId;
    }
}
