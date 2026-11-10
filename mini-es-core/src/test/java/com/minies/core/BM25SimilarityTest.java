package com.minies.core;

import com.minies.core.similarity.BM25Similarity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BM25SimilarityTest {

    @Test
    void idfMatchesLuceneFormula() {
        BM25Similarity bm25 = new BM25Similarity(1.2f, 0.6f);
        // N=100, n=10 → ln(1 + (100 - 10 + 0.5)/(10 + 0.5))
        double expected = Math.log(1.0 + (100 - 10 + 0.5) / (10 + 0.5));
        assertThat(bm25.idf(10, 100)).isCloseTo(expected, within(1e-12));
    }

    @Test
    void scoreMatchesHandComputation() {
        BM25Similarity bm25 = new BM25Similarity(1.2f, 0.6f);
        double k1 = bm25.k1();
        double b = bm25.b();
        double tf = 3.0;
        double docLen = 100.0;
        double avgdl = 80.0;
        double idf = bm25.idf(5, 50);

        // Use the same float-promoted k1/b the scorer uses to avoid binary float drift.
        double norm = 1.0 - b + b * (docLen / avgdl);
        double tfNorm = tf * (k1 + 1.0) / (tf + k1 * norm);
        double expected = idf * tfNorm;

        assertThat(bm25.score(tf, docLen, avgdl, idf)).isCloseTo(expected, within(1e-12));
    }
}
