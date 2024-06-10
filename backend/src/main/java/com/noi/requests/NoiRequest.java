package com.noi.requests;

import com.noi.AiModel;
import com.noi.Status;
import com.noi.language.AiPrompt;

import java.util.*;

public abstract class NoiRequest {

    protected final String modelName;

    protected final UUID uuid;
    private Long id;

    protected AiPrompt prompt;

    protected final Status status;

    protected NoiRequest(UUID uuid, String modelName) {
        this.uuid = uuid == null ? UUID.randomUUID() : uuid;
        this.status = Status.NEW;
        this.modelName = modelName;
    }

    protected NoiRequest(UUID uuid, AiPrompt prompt, String modelName) {
        this.uuid = uuid == null ? UUID.randomUUID() : uuid;
        this.status = Status.NEW;
        this.modelName = modelName;
        this.prompt = prompt;
    }

    protected NoiRequest(Long id, String uuid, String modelName, Status status) {
        this.id = id;
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
        this.uuid = UUID.fromString(uuid);
        this.status = status;
        this.modelName = modelName;
    }

    public String getUUID() {
        return uuid.toString();
    }

    public String getModelName() {
        return modelName;
    }

    public AiPrompt getPrompt() {
        return prompt;
    }

    public AiModel getModel() {
        return AiModel.getModel(modelName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NoiRequest aiRequest = (NoiRequest) o;
        return uuid.equals(aiRequest.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return "AiRequest{" +
                "uuid=" + uuid +
                ", id=" + id +
                ", status=" + status +
                ", modelName='" + modelName + '\'' +
                '}';
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }


    public void setPrompt(AiPrompt prompt) {
        this.prompt = prompt;
    }

}
