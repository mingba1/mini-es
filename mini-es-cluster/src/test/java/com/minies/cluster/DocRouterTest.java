package com.minies.cluster;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocRouterTest {

    @Test
    void shardIdStableAndInRange() {
        DocRouter router = new DocRouter(5);
        for (int i = 0; i < 100; i++) {
            int shard = router.shardId("doc-" + i);
            assertThat(shard).isBetween(0, 4);
            assertThat(router.shardId("doc-" + i)).isEqualTo(shard);
        }
    }

    @Test
    void distributesAcrossShards() {
        DocRouter router = new DocRouter(3);
        boolean[] seen = new boolean[3];
        for (int i = 0; i < 50; i++) {
            seen[router.shardId("id-" + i)] = true;
        }
        assertThat(seen[0] && seen[1] && seen[2]).isTrue();
    }
}
