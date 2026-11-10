package com.minies.vector;

/**
 * Produces dense embeddings. Index-time and query-time must share the same model + L2 contract.
 */
public interface EmbeddingService {

    String modelId();

    int dims();

    /** Embed a passage (document). */
    float[] embedPassage(String text);

    /**
     * Embed a query. Implementations may apply an instruction prefix (e.g. BGE-zh)
     * that must not be applied to passages.
     */
    float[] embedQuery(String text);
}
