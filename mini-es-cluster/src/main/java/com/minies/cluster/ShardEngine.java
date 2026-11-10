package com.minies.cluster;

import com.minies.core.document.Document;
import com.minies.core.search.Query;
import com.minies.core.search.ScoreDoc;
import com.minies.core.search.TopDocs;
import com.minies.vector.EmbeddingService;
import com.minies.vector.HybridEngine;
import com.minies.vector.HybridSearchResult;

import java.util.Arrays;
import java.util.List;

/** One shard copy (primary or replica) backed by a {@link HybridEngine}. */
public final class ShardEngine {

    private final int shardId;
    private final ShardRole role;
    private final HybridEngine engine;
    private volatile boolean open = true;

    public ShardEngine(int shardId, ShardRole role, EmbeddingService embedding) {
        this.shardId = shardId;
        this.role = role;
        this.engine = new HybridEngine(embedding);
    }

    public int shardId() {
        return shardId;
    }

    public ShardRole role() {
        return role;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    public void reopenAs(ShardRole newRole) {
        // Role is final for clarity; failover creates a new wrapper — keep engine data.
        throw new UnsupportedOperationException("use ClusterState.promoteReplica");
    }

    public HybridEngine engine() {
        return engine;
    }

    public void index(Document document) {
        ensureOpen();
        engine.index(document);
    }

    public void refresh() {
        ensureOpen();
        engine.refresh();
    }

    public List<ScoreDoc> searchBm25(Query query, int n) {
        ensureOpen();
        TopDocs top = engine.lexical().search(query, n);
        return Arrays.asList(top.scoreDocs());
    }

    public HybridSearchResult searchHybrid(String query, int n, HybridEngine.FusionMode mode) {
        ensureOpen();
        return engine.search(query, n, mode);
    }

    private void ensureOpen() {
        if (!open) {
            throw new IllegalStateException("shard " + shardId + " (" + role + ") is closed");
        }
    }
}
