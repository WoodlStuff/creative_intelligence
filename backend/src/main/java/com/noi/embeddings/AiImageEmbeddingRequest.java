package com.noi.embeddings;

import com.noi.Status;
import com.noi.image.AiImage;
import com.noi.requests.NoiRequest;

import java.util.UUID;

public class AiImageEmbeddingRequest extends NoiRequest {
    private final AiImage image;
    private final Long categoryId;

    protected AiImageEmbeddingRequest(Long id, String uuid, AiImage image, Long categoryId, String modelName, Status status) {
        super(id, uuid, modelName, status);
        this.image = image;
        this.categoryId = categoryId;
    }

    public static AiImageEmbeddingRequest create(Long id, UUID uuid, AiImage image, Long categoryId, String modelName) {
        return new AiImageEmbeddingRequest(id, uuid.toString(), image, categoryId, modelName, Status.NEW);
    }

    @Override
    public String toString() {
        return "AiImageEmbeddingRequest{" +
                "id=" + getId() +
                "image=" + image +
                ", categoryId=" + categoryId +
                ", modelName='" + modelName + '\'' +
                ", uuid=" + uuid +
                ", prompt=" + prompt +
                ", status=" + status +
                '}';
    }

    public AiImage getImage() {
        return image;
    }

    public Long getCategoryId() {
        return categoryId;
    }
}
