package com.minies.vector;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HnswRecallTest {

    @Test
    void hnswAgreesWithBruteForceOnTinySet() {
        int dims = 16;
        MockEmbeddingService emb = new MockEmbeddingService(dims);
        BruteForceVectorIndex brute = new BruteForceVectorIndex(dims);
        HnswVectorIndex hnsw = new HnswVectorIndex(dims, 8, 32, 32);

        String[] docs = {
                "swift mt103 payment",
                "foreign exchange wire transfer",
                "cooking pasta recipe",
                "basketball game highlights",
                "cross border remittance message error"
        };
        for (int i = 0; i < docs.length; i++) {
            float[] v = emb.embedPassage(docs[i]);
            String id = "d" + i;
            brute.add(id, v);
            hnsw.add(id, v);
        }

        float[] q = emb.embedQuery("跨境汇款报文出错");
        List<VectorHit> exact = brute.search(q, 3);
        List<VectorHit> approx = hnsw.search(q, 3);

        Set<String> exactIds = new HashSet<>();
        exact.forEach(h -> exactIds.add(h.docId()));
        Set<String> approxIds = new HashSet<>();
        approx.forEach(h -> approxIds.add(h.docId()));

        // On tiny data HNSW should recall all exact top-3 (or at least top-1).
        assertThat(approxIds).contains(exact.get(0).docId());
        long overlap = approxIds.stream().filter(exactIds::contains).count();
        assertThat(overlap).isGreaterThanOrEqualTo(2);
    }
}
