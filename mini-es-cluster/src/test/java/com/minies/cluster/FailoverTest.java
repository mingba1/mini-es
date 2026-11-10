package com.minies.cluster;

import com.minies.core.document.Document;
import com.minies.core.search.ScoreDoc;
import com.minies.vector.MockEmbeddingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailoverTest {

    @Test
    void promoteReplicaAfterPrimaryFailure() {
        ClusterState state = new ClusterState(2, 1, new MockEmbeddingService(16));
        CoordinatingNode coord = new CoordinatingNode(state);

        Document doc = new Document("pay-1")
                .add("title", "SWIFT MT103")
                .add("content", "payment message");
        int shard = state.router().shardId(doc.id());
        coord.index(doc);
        coord.refresh();

        List<ScoreDoc> before = coord.searchBm25("MT103", 5);
        assertThat(before).extracting(ScoreDoc::docId).contains("pay-1");

        ShardAllocation promoted = state.failPrimary(shard);
        assertThat(promoted.role()).isEqualTo(ShardRole.PRIMARY);
        assertThat(promoted.engine().isOpen()).isTrue();

        assertThatThrownBy(() -> state.allocations().stream()
                .filter(a -> a.shardId() == shard && a.nodeId().startsWith("node-p-"))
                .findFirst()
                .orElseThrow()
                .engine()
                .searchBm25(com.minies.core.search.MultiMatchQuery.of("MT103",
                        promoted.engine().engine().lexical().analyzer()), 5))
                .isInstanceOf(IllegalStateException.class);

        List<ScoreDoc> after = coord.searchBm25("MT103", 5);
        assertThat(after).extracting(ScoreDoc::docId).contains("pay-1");
    }
}
