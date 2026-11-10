package com.minies.vector;

import com.minies.core.search.ScoreDoc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ReciprocalRankFusionTest {

    @Test
    void bothListsFirstGetsTwoOverSixtyOne() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion(60, 100);
        List<ReciprocalRankFusion.RankedHit> bm25 = List.of(
                new ReciprocalRankFusion.SimpleRankedHit("a", 10f, 1),
                new ReciprocalRankFusion.SimpleRankedHit("b", 5f, 2)
        );
        List<ReciprocalRankFusion.RankedHit> knn = List.of(
                new ReciprocalRankFusion.SimpleRankedHit("a", 0.9f, 1),
                new ReciprocalRankFusion.SimpleRankedHit("c", 0.8f, 2)
        );

        List<ScoreDoc> fused = rrf.fuse(List.of(bm25, knn), 10);

        assertThat(fused.get(0).docId()).isEqualTo("a");
        assertThat(fused.get(0).score()).isCloseTo(2.0f / 61.0f, within(1e-6f));
        assertThat(fused).extracting(ScoreDoc::docId).containsExactly("a", "b", "c");
    }

    @Test
    void singleLegHitStillSurvives() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();
        List<ReciprocalRankFusion.RankedHit> bm25 = List.of(
                new ReciprocalRankFusion.SimpleRankedHit("only-bm25", 3f, 1)
        );
        List<ReciprocalRankFusion.RankedHit> knn = List.of(
                new ReciprocalRankFusion.SimpleRankedHit("only-knn", 0.99f, 1)
        );
        List<ScoreDoc> fused = rrf.fuse(List.of(bm25, knn), 10);
        assertThat(fused).extracting(ScoreDoc::docId).containsExactlyInAnyOrder("only-bm25", "only-knn");
    }
}
