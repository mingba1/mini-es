package com.minies.vector;

import com.minies.core.search.ScoreDoc;

import java.util.List;

public final class HybridSearchResult {
    private final List<ScoreDoc> hits;
    private final String fusion;
    private final boolean degraded;
    private final String degradeReason;

    public HybridSearchResult(List<ScoreDoc> hits, String fusion, boolean degraded, String degradeReason) {
        this.hits = List.copyOf(hits);
        this.fusion = fusion;
        this.degraded = degraded;
        this.degradeReason = degradeReason;
    }

    public List<ScoreDoc> hits() {
        return hits;
    }

    public String fusion() {
        return fusion;
    }

    public boolean degraded() {
        return degraded;
    }

    public String degradeReason() {
        return degradeReason;
    }
}
