package com.noi.embeddings;

import com.noi.Status;
import com.noi.image.AiImage;
import com.noi.requests.NoiRequest;

import java.util.UUID;

public class AiImageVectorRequest extends NoiRequest {
    private final AiImage image;
    private final Long categoryId;
    private final int size;

    protected AiImageVectorRequest(Long id, String uuid, AiImage image, Long categoryId, String modelName, int size, Status status) {
        super(id, uuid, modelName, status);
        this.image = image;
        this.categoryId = categoryId;
        this.size = size;
    }

    public static AiImageVectorRequest create(Long id, UUID uuid, AiImage image, Long categoryId, String modelName, int size) {
        return new AiImageVectorRequest(id, uuid.toString(), image, categoryId, modelName, size, Status.NEW);
    }

    @Override
    public String toString() {
        return "AiImageVectorRequest{" +
                "image=" + image +
                ", categoryId=" + categoryId +
                ", size=" + size +
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

    public int getSize() {
        return size;
    }
}
