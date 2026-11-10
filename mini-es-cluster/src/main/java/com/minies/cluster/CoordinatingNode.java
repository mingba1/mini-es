package com.minies.cluster;

import com.minies.core.document.Document;
import com.minies.core.search.MultiMatchQuery;
import com.minies.core.search.Query;
import com.minies.core.search.ScoreDoc;
import com.minies.vector.HybridEngine;
import com.minies.vector.HybridSearchResult;
import com.minies.vector.ReciprocalRankFusion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ES coordinating-node semantics in-process: route writes, scatter-gather reads.
 */
public final class CoordinatingNode {

    private final ClusterState state;

    public CoordinatingNode(ClusterState state) {
        this.state = state;
    }

    public ClusterState state() {
        return state;
    }

    /**
     * Index on primary, synchronously replicate to open replicas (teaching sync-replication).
     */
    public void index(Document document) {
        int shardId = state.router().shardId(document.id());
        ShardAllocation primary = state.primary(shardId);
        primary.engine().index(document);
        for (ShardAllocation replica : state.replicas(shardId)) {
            replica.engine().index(document);
        }
    }

    public void refresh() {
        for (ShardAllocation a : state.allocations()) {
            if (a.engine().isOpen()) {
                a.engine().refresh();
            }
        }
    }

    public List<ScoreDoc> searchBm25(String queryText, int topN) {
        Query query = MultiMatchQuery.of(queryText, state.primary(0).engine().engine().lexical().analyzer());
        List<ScoreDoc> gathered = new ArrayList<>();
        for (ShardAllocation primary : state.openPrimaries()) {
            gathered.addAll(primary.engine().searchBm25(query, topN));
        }
        return mergeTop(gathered, topN);
    }

    public HybridSearchResult searchHybrid(String queryText, int topN, HybridEngine.FusionMode mode) {
        if (mode == HybridEngine.FusionMode.RRF) {
            // Per-shard hybrid then merge by RRF score (already fused locally).
            List<ScoreDoc> gathered = new ArrayList<>();
            boolean degraded = false;
            String reason = null;
            for (ShardAllocation primary : state.openPrimaries()) {
                HybridSearchResult local = primary.engine().searchHybrid(queryText, topN, mode);
                gathered.addAll(local.hits());
                if (local.degraded()) {
                    degraded = true;
                    reason = local.degradeReason();
                }
            }
            return new HybridSearchResult(mergeTop(gathered, topN), "rrf", degraded, reason);
        }
        List<ScoreDoc> gathered = new ArrayList<>();
        String fusion = mode.name().toLowerCase();
        for (ShardAllocation primary : state.openPrimaries()) {
            HybridSearchResult local = primary.engine().searchHybrid(queryText, topN, mode);
            gathered.addAll(local.hits());
            fusion = local.fusion();
        }
        return new HybridSearchResult(mergeTop(gathered, topN), fusion, false, null);
    }

    /**
     * Alternate gather: fuse per-shard BM25 legs with global RRF across shard rank lists.
     * Useful to show coordinating-node rank merge independent of local hybrid.
     */
    public List<ScoreDoc> scatterGatherRrf(String queryText, int topN) {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();
        List<List<? extends ReciprocalRankFusion.RankedHit>> legs = new ArrayList<>();
        Query query = MultiMatchQuery.of(queryText, state.primary(0).engine().engine().lexical().analyzer());
        for (ShardAllocation primary : state.openPrimaries()) {
            List<ScoreDoc> local = primary.engine().searchBm25(query, Math.max(topN, 20));
            legs.add(ReciprocalRankFusion.fromScoreDocs(local));
        }
        return rrf.fuse(legs, topN);
    }

    private static List<ScoreDoc> mergeTop(List<ScoreDoc> gathered, int topN) {
        Map<String, Float> best = new HashMap<>();
        for (ScoreDoc d : gathered) {
            best.merge(d.docId(), d.score(), Math::max);
        }
        List<ScoreDoc> merged = new ArrayList<>(best.size());
        best.forEach((id, score) -> merged.add(new ScoreDoc(id, score)));
        merged.sort(Comparator
                .comparingDouble(ScoreDoc::score).reversed()
                .thenComparing(ScoreDoc::docId));
        if (merged.size() > topN) {
            return new ArrayList<>(merged.subList(0, topN));
        }
        return merged;
    }
}
