package com.noi.embeddings;

import java.io.IOException;

public class EmbeddingException extends Exception {
    public EmbeddingException(IOException e) {
        super(e);
    }
}
