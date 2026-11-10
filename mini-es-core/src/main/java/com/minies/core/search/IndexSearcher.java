package com.minies.core.search;

import com.minies.core.index.InMemorySegment;
import com.minies.core.similarity.BM25Similarity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Near-real-time searcher over a snapshot of committed {@link InMemorySegment}s.
 */
public final class IndexSearcher {

    private final List<InMemorySegment> segments;
    private final BM25Similarity similarity;

    public IndexSearcher(List<InMemorySegment> segments) {
        this(segments, new BM25Similarity());
    }

    public IndexSearcher(List<InMemorySegment> segments, BM25Similarity similarity) {
        this.segments = List.copyOf(segments);
        this.similarity = similarity;
    }

    public List<InMemorySegment> segments() {
        return segments;
    }

    public BM25Similarity similarity() {
        return similarity;
    }

    public TopDocs search(Query query, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be >= 0");
        }
        Map<String, Float> scores = new HashMap<>();
        query.accumulateScores(this, scores);

        List<ScoreDoc> hits = new ArrayList<>(scores.size());
        for (Map.Entry<String, Float> e : scores.entrySet()) {
            hits.add(new ScoreDoc(e.getKey(), e.getValue()));
        }
        hits.sort(Comparator
                .comparingDouble(ScoreDoc::score).reversed()
                .thenComparing(ScoreDoc::docId));

        long totalHits = hits.size();
        int limit = Math.min(n, hits.size());
        ScoreDoc[] top = hits.subList(0, limit).toArray(new ScoreDoc[0]);
        return new TopDocs(totalHits, top);
    }
}
