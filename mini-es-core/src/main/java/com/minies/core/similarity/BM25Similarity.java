package com.minies.core.similarity;

/**
 * BM25 scoring using the classic Lucene-style formulas:
 *
 * <pre>
 *   idf    = ln(1 + (N - n + 0.5) / (n + 0.5))
 *   tfNorm = tf * (k1 + 1) / (tf + k1 * (1 - b + b * docLen / avgdl))
 *   score  = idf * tfNorm
 * </pre>
 */
public final class BM25Similarity {

    public static final float DEFAULT_K1 = 1.2f;
    public static final float DEFAULT_B = 0.6f;

    private final float k1;
    private final float b;

    public BM25Similarity() {
        this(DEFAULT_K1, DEFAULT_B);
    }

    public BM25Similarity(float k1, float b) {
        this.k1 = k1;
        this.b = b;
    }

    public float k1() {
        return k1;
    }

    public float b() {
        return b;
    }

    /**
     * Inverse document frequency.
     *
     * @param docFreq  number of documents containing the term ({@code n})
     * @param docCount total documents in the collection / segment ({@code N})
     */
    public double idf(long docFreq, long docCount) {
        return Math.log(1.0 + (docCount - docFreq + 0.5) / (docFreq + 0.5));
    }

    /**
     * Full BM25 contribution for one term occurrence in one document.
     *
     * @param tf     term frequency in the document field
     * @param docLen field length (token count) of the document
     * @param avgdl  average field length across the collection
     * @param idf    precomputed IDF for the term
     */
    public double score(double tf, double docLen, double avgdl, double idf) {
        if (tf <= 0.0 || avgdl <= 0.0) {
            return 0.0;
        }
        double norm = 1.0 - b + b * (docLen / avgdl);
        double tfNorm = tf * (k1 + 1.0) / (tf + k1 * norm);
        return idf * tfNorm;
    }
}
