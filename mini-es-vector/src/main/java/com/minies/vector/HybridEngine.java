package com.minies.vector;

import com.minies.core.document.Document;
import com.minies.core.document.Field;
import com.minies.core.engine.InternalEngine;
import com.minies.core.search.MultiMatchQuery;
import com.minies.core.search.Query;
import com.minies.core.search.ScoreDoc;
import com.minies.core.search.TopDocs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Single-shard hybrid engine: BM25 (InternalEngine) + dense vector index + RRF fusion.
 */
public final class HybridEngine {

    public enum FusionMode {
        RRF,
        BM25,
        KNN,
        WEIGHTED
    }

    private final InternalEngine lexical;
    private final EmbeddingService embedding;
    private final VectorIndex vectors;
    private final ReciprocalRankFusion rrf;
    private final float bm25Weight;
    private final float knnWeight;
    private final int windowSize;

    public HybridEngine(EmbeddingService embedding) {
        this(embedding, new HnswVectorIndex(embedding.dims()), new ReciprocalRankFusion());
    }

    public HybridEngine(EmbeddingService embedding, VectorIndex vectors, ReciprocalRankFusion rrf) {
        this.lexical = new InternalEngine();
        this.embedding = embedding;
        this.vectors = vectors;
        this.rrf = rrf;
        this.bm25Weight = 0.7f;
        this.knnWeight = 0.3f;
        this.windowSize = ReciprocalRankFusion.DEFAULT_WINDOW_SIZE;
    }

    public InternalEngine lexical() {
        return lexical;
    }

    public EmbeddingService embedding() {
        return embedding;
    }

    public VectorIndex vectors() {
        return vectors;
    }

    public synchronized void index(Document document) {
        lexical.index(document);
        String text = passageText(document);
        float[] vec = Vectors.l2Normalize(embedding.embedPassage(text));
        vectors.add(document.id(), vec);
    }

    public synchronized void refresh() {
        lexical.refresh();
    }

    public HybridSearchResult search(String queryText, int topN) {
        return search(queryText, topN, FusionMode.RRF);
    }

    public HybridSearchResult search(String queryText, int topN, FusionMode mode) {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        int limit = Math.max(1, topN);
        int window = Math.max(windowSize, limit);

        return switch (mode) {
            case BM25 -> new HybridSearchResult(bm25Hits(queryText, limit), "bm25", false, null);
            case KNN -> {
                List<ScoreDoc> knn = toScoreDocs(knnHits(queryText, limit));
                yield new HybridSearchResult(knn, "knn", false, null);
            }
            case WEIGHTED -> weighted(queryText, limit, window);
            case RRF -> rrfSearch(queryText, limit, window);
        };
    }

    private HybridSearchResult rrfSearch(String queryText, int topN, int window) {
        List<ScoreDoc> bm25;
        try {
            bm25 = bm25Hits(queryText, window);
        } catch (RuntimeException e) {
            throw e;
        }

        List<VectorHit> knn;
        try {
            knn = knnHits(queryText, window);
        } catch (RuntimeException e) {
            return new HybridSearchResult(bm25.subList(0, Math.min(topN, bm25.size())),
                    "bm25", true, "vector failed: " + e.getMessage());
        }

        List<ScoreDoc> fused = rrf.fuse(List.of(
                ReciprocalRankFusion.fromScoreDocs(bm25),
                ReciprocalRankFusion.fromVectorHits(knn)
        ), topN);
        return new HybridSearchResult(fused, "rrf", false, null);
    }

    private HybridSearchResult weighted(String queryText, int topN, int window) {
        List<ScoreDoc> bm25 = bm25Hits(queryText, window);
        List<VectorHit> knn = knnHits(queryText, window);
        java.util.Map<String, Float> scores = new java.util.HashMap<>();
        for (ScoreDoc d : bm25) {
            scores.merge(d.docId(), bm25Weight * d.score(), Float::sum);
        }
        for (VectorHit h : knn) {
            scores.merge(h.docId(), knnWeight * h.score(), Float::sum);
        }
        List<ScoreDoc> hits = new ArrayList<>();
        scores.forEach((id, s) -> hits.add(new ScoreDoc(id, s)));
        hits.sort((a, b) -> {
            int c = Float.compare(b.score(), a.score());
            return c != 0 ? c : a.docId().compareTo(b.docId());
        });
        return new HybridSearchResult(hits.subList(0, Math.min(topN, hits.size())), "weighted", false, null);
    }

    private List<ScoreDoc> bm25Hits(String queryText, int n) {
        Query q = MultiMatchQuery.of(queryText, lexical.analyzer());
        TopDocs top = lexical.search(q, n);
        return new ArrayList<>(Arrays.asList(top.scoreDocs()));
    }

    private List<VectorHit> knnHits(String queryText, int n) {
        float[] qv = Vectors.l2Normalize(embedding.embedQuery(queryText));
        return vectors.search(qv, n);
    }

    private static List<ScoreDoc> toScoreDocs(List<VectorHit> hits) {
        List<ScoreDoc> out = new ArrayList<>(hits.size());
        for (VectorHit h : hits) {
            out.add(new ScoreDoc(h.docId(), h.score()));
        }
        return out;
    }

    private static String passageText(Document document) {
        String title = "";
        String content = "";
        for (Field f : document.fields()) {
            if ("title".equals(f.name())) {
                title = f.text() == null ? "" : f.text();
            } else if ("content".equals(f.name())) {
                content = f.text() == null ? "" : f.text();
            }
        }
        if (title.isEmpty() && content.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Field f : document.fields()) {
                if (f.text() != null && !f.text().isBlank()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(f.text());
                }
            }
            return sb.toString().toLowerCase(Locale.ROOT);
        }
        return title + "\n" + content;
    }
}
