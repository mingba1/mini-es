package com.minies.vector;

import com.minies.core.search.ScoreDoc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application-layer RRF aligned with hybrid-search-es:
 *
 * <pre>
 *   score(d) = Σ_leg 1 / (rankConstant + rank_leg(d))
 * </pre>
 *
 * Ranks are 1-based; entries beyond {@code windowSize} are ignored.
 */
public final class ReciprocalRankFusion {

    public static final int DEFAULT_RANK_CONSTANT = 60;
    public static final int DEFAULT_WINDOW_SIZE = 100;

    private final int rankConstant;
    private final int windowSize;

    public ReciprocalRankFusion() {
        this(DEFAULT_RANK_CONSTANT, DEFAULT_WINDOW_SIZE);
    }

    public ReciprocalRankFusion(int rankConstant, int windowSize) {
        if (rankConstant <= 0 || windowSize <= 0) {
            throw new IllegalArgumentException("rankConstant/windowSize must be > 0");
        }
        this.rankConstant = rankConstant;
        this.windowSize = windowSize;
    }

    public List<ScoreDoc> fuse(List<List<? extends RankedHit>> legs, int topN) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, float[]> legScores = new HashMap<>();

        for (int legIdx = 0; legIdx < legs.size(); legIdx++) {
            List<? extends RankedHit> leg = legs.get(legIdx);
            int limit = Math.min(windowSize, leg.size());
            for (int i = 0; i < limit; i++) {
                RankedHit hit = leg.get(i);
                int rank = hit.rank() > 0 ? hit.rank() : (i + 1);
                scores.merge(hit.docId(), 1.0 / (rankConstant + rank), Double::sum);
                float[] arr = legScores.computeIfAbsent(hit.docId(), id -> {
                    float[] zeros = new float[legs.size()];
                    for (int j = 0; j < zeros.length; j++) {
                        zeros[j] = Float.NaN;
                    }
                    return zeros;
                });
                arr[legIdx] = hit.score();
            }
        }

        List<ScoreDoc> fused = new ArrayList<>(scores.size());
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            fused.add(new ScoreDoc(e.getKey(), e.getValue().floatValue()));
        }
        fused.sort(Comparator
                .comparingDouble(ScoreDoc::score).reversed()
                .thenComparing(ScoreDoc::docId));
        if (fused.size() > topN) {
            return new ArrayList<>(fused.subList(0, topN));
        }
        return fused;
    }

    /** Convenience for BM25 {@link ScoreDoc} lists (assign 1-based ranks by order). */
    public static List<RankedHit> fromScoreDocs(List<ScoreDoc> docs) {
        List<RankedHit> out = new ArrayList<>(docs.size());
        for (int i = 0; i < docs.size(); i++) {
            ScoreDoc d = docs.get(i);
            out.add(new SimpleRankedHit(d.docId(), d.score(), i + 1));
        }
        return out;
    }

    public static List<RankedHit> fromVectorHits(List<VectorHit> hits) {
        List<RankedHit> out = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            VectorHit h = hits.get(i);
            int rank = h.rank() > 0 ? h.rank() : (i + 1);
            out.add(new SimpleRankedHit(h.docId(), h.score(), rank));
        }
        return out;
    }

    public interface RankedHit {
        String docId();

        float score();

        int rank();
    }

    public record SimpleRankedHit(String docId, float score, int rank) implements RankedHit {
    }
}
