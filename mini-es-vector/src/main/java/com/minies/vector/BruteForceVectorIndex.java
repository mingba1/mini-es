package com.minies.vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Exact top-k by cosine (dot product on L2 vectors). Correctness baseline for HNSW. */
public final class BruteForceVectorIndex implements VectorIndex {

    private final List<String> ids = new ArrayList<>();
    private final List<float[]> vectors = new ArrayList<>();
    private final int dims;

    public BruteForceVectorIndex(int dims) {
        if (dims <= 0) {
            throw new IllegalArgumentException("dims must be > 0");
        }
        this.dims = dims;
    }

    @Override
    public synchronized void add(String docId, float[] vector) {
        if (vector.length != dims) {
            throw new IllegalArgumentException("expected dims=" + dims + " got " + vector.length);
        }
        ids.add(docId);
        vectors.add(vector.clone());
    }

    @Override
    public synchronized List<VectorHit> search(float[] query, int k) {
        if (query.length != dims) {
            throw new IllegalArgumentException("query dims mismatch");
        }
        if (k <= 0 || ids.isEmpty()) {
            return List.of();
        }
        List<VectorHit> all = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            all.add(new VectorHit(ids.get(i), Vectors.cosine(query, vectors.get(i))));
        }
        all.sort(Comparator
                .comparingDouble(VectorHit::score).reversed()
                .thenComparing(VectorHit::docId));
        int limit = Math.min(k, all.size());
        List<VectorHit> top = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            top.add(all.get(i).withRank(i + 1));
        }
        return top;
    }

    @Override
    public synchronized int size() {
        return ids.size();
    }
}
