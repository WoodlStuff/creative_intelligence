package com.noi.embeddings;

import java.io.IOException;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

public abstract class VectorService {
    static final String INDEX_NAME = "categories";

    public static VectorService getService() {
        //todo: how to split to other service impls here...?
        return new PineconeVectorService(null);
    }

    protected abstract Map<String, Integer> upsert(Connection con, EmbeddingService.ImageEmbeddings embeddings, String indexName) throws EmbeddingException, IOException;

    protected abstract List<VectorMatch> querySimilarImages(EmbeddingService.ImageEmbeddings embeddings, String categoryName, QueryMeta queryMeta) throws EmbeddingException, IOException;
}
