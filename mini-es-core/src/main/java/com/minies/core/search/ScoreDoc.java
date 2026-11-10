package com.minies.core.search;

/**
 * One hit: external document id, score, and optional segment-local ordinal.
 */
public final class ScoreDoc {
    private final String docId;
    private final float score;
    private final int segmentDoc;

    public ScoreDoc(String docId, float score) {
        this(docId, score, -1);
    }

    public ScoreDoc(String docId, float score, int segmentDoc) {
        this.docId = docId;
        this.score = score;
        this.segmentDoc = segmentDoc;
    }

    public String docId() {
        return docId;
    }

    public float score() {
        return score;
    }

    /** Local doc ordinal in the segment that produced the hit, or -1 if unknown. */
    public int segmentDoc() {
        return segmentDoc;
    }

    @Override
    public String toString() {
        return "ScoreDoc{docId='" + docId + "', score=" + score + ", segmentDoc=" + segmentDoc + "}";
    }
}
