package com.noi.embeddings;

import java.io.IOException;
import java.sql.Connection;
import java.util.Map;

public abstract class VectorService {
    public static VectorService getService() {
        //todo: how to split to other service impls here...?
        return new PineconeVectorService(null);
    }

    protected abstract Map<String, Integer> upsert(Connection con, EmbeddingService.ImageEmbeddings embeddings, String indexName) throws EmbeddingException, IOException;
}
