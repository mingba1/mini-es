package com.minies.core.search;

import java.util.Arrays;

/**
 * Search result page: total matching hits and the top {@link ScoreDoc}s.
 */
public final class TopDocs {
    private final long totalHits;
    private final ScoreDoc[] scoreDocs;

    public TopDocs(long totalHits, ScoreDoc[] scoreDocs) {
        this.totalHits = totalHits;
        this.scoreDocs = scoreDocs == null ? new ScoreDoc[0] : scoreDocs.clone();
    }

    public long totalHits() {
        return totalHits;
    }

    public ScoreDoc[] scoreDocs() {
        return scoreDocs.clone();
    }

    @Override
    public String toString() {
        return "TopDocs{totalHits=" + totalHits + ", scoreDocs=" + Arrays.toString(scoreDocs) + "}";
    }
}
