package com.minies.vector;

/** One knn hit: document id, similarity score, 1-based rank assigned by caller. */
public final class VectorHit {
    private final String docId;
    private final float score;
    private final int rank;

    public VectorHit(String docId, float score) {
        this(docId, score, -1);
    }

    public VectorHit(String docId, float score, int rank) {
        this.docId = docId;
        this.score = score;
        this.rank = rank;
    }

    public String docId() {
        return docId;
    }

    public float score() {
        return score;
    }

    public int rank() {
        return rank;
    }

    public VectorHit withRank(int rank) {
        return new VectorHit(docId, score, rank);
    }
}
