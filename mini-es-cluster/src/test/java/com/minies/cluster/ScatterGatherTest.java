package com.minies.cluster;

import com.minies.core.document.Document;
import com.minies.core.search.ScoreDoc;
import com.minies.vector.HybridEngine;
import com.minies.vector.HybridSearchResult;
import com.minies.vector.MockEmbeddingService;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScatterGatherTest {

    @Test
    void indexesAcrossShardsAndGathersResults() {
        ClusterState state = new ClusterState(3, 1, new MockEmbeddingService(24));
        CoordinatingNode coord = new CoordinatingNode(state);

        for (int i = 0; i < 12; i++) {
            coord.index(new Document("doc-" + i)
                    .add("title", "title " + i)
                    .add("content", i % 2 == 0 ? "swift payment wire" : "cooking pasta"));
        }
        coord.refresh();

        Set<Integer> usedShards = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            usedShards.add(state.router().shardId("doc-" + i));
        }
        assertThat(usedShards.size()).isGreaterThan(1);

        List<ScoreDoc> hits = coord.searchBm25("swift payment", 5);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).docId()).startsWith("doc-");

        HybridSearchResult hybrid = coord.searchHybrid("payment wire", 5, HybridEngine.FusionMode.RRF);
        assertThat(hybrid.hits()).isNotEmpty();
        assertThat(hybrid.fusion()).isEqualTo("rrf");
    }
}
