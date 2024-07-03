package com.noi.embeddings;

import com.google.gson.JsonArray;
import com.noi.image.AiImage;

import java.io.IOException;
import java.util.Map;

public abstract class VectorService {
    public static VectorService getService() {
        //todo: how to split to other service impls here...?
        return new PineconeVectorService(null);
    }

    protected abstract long upsert(AiImage image, JsonArray vector, String indexName, String nameSpace) throws EmbeddingException;
    protected abstract Map<String, Long> upsert(AiImage image, JsonArray vectors, String indexName) throws EmbeddingException, IOException;
}
