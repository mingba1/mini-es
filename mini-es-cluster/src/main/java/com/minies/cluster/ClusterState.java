package com.minies.cluster;

import com.minies.vector.EmbeddingService;
import com.minies.vector.MockEmbeddingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process cluster metadata: primary + replica allocations per shard id.
 */
public final class ClusterState {

    private final DocRouter router;
    private final int numberOfReplicas;
    private final EmbeddingService embedding;
    private final CopyOnWriteArrayList<ShardAllocation> allocations = new CopyOnWriteArrayList<>();

    public ClusterState(int numberOfPrimaryShards, int numberOfReplicas) {
        this(numberOfPrimaryShards, numberOfReplicas, new MockEmbeddingService(32));
    }

    public ClusterState(int numberOfPrimaryShards, int numberOfReplicas, EmbeddingService embedding) {
        if (numberOfReplicas < 0) {
            throw new IllegalArgumentException("numberOfReplicas must be >= 0");
        }
        this.router = new DocRouter(numberOfPrimaryShards);
        this.numberOfReplicas = numberOfReplicas;
        this.embedding = embedding;
        bootstrap();
    }

    private void bootstrap() {
        for (int shard = 0; shard < router.numberOfPrimaryShards(); shard++) {
            String primaryNode = "node-p-" + shard;
            ShardEngine primary = new ShardEngine(shard, ShardRole.PRIMARY, embedding);
            allocations.add(new ShardAllocation(shard, ShardRole.PRIMARY, primaryNode, primary));
            for (int r = 0; r < numberOfReplicas; r++) {
                String replicaNode = "node-r-" + shard + "-" + r;
                ShardEngine replica = new ShardEngine(shard, ShardRole.REPLICA, embedding);
                allocations.add(new ShardAllocation(shard, ShardRole.REPLICA, replicaNode, replica));
            }
        }
    }

    public DocRouter router() {
        return router;
    }

    public EmbeddingService embedding() {
        return embedding;
    }

    public List<ShardAllocation> allocations() {
        return List.copyOf(allocations);
    }

    public ShardAllocation primary(int shardId) {
        return allocations.stream()
                .filter(a -> a.shardId() == shardId && a.role() == ShardRole.PRIMARY && a.engine().isOpen())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no open primary for shard " + shardId));
    }

    public List<ShardAllocation> replicas(int shardId) {
        List<ShardAllocation> out = new ArrayList<>();
        for (ShardAllocation a : allocations) {
            if (a.shardId() == shardId && a.role() == ShardRole.REPLICA && a.engine().isOpen()) {
                out.add(a);
            }
        }
        return out;
    }

    public List<ShardAllocation> openPrimaries() {
        List<ShardAllocation> out = new ArrayList<>();
        for (int s = 0; s < router.numberOfPrimaryShards(); s++) {
            out.add(primary(s));
        }
        return out;
    }

    /**
     * Failover teaching API: close primary, promote first open replica to primary.
     */
    public synchronized ShardAllocation failPrimary(int shardId) {
        ShardAllocation primary = allocations.stream()
                .filter(a -> a.shardId() == shardId && a.role() == ShardRole.PRIMARY)
                .findFirst()
                .orElseThrow();
        primary.engine().close();

        Optional<ShardAllocation> replica = allocations.stream()
                .filter(a -> a.shardId() == shardId && a.role() == ShardRole.REPLICA && a.engine().isOpen())
                .findFirst();
        if (replica.isEmpty()) {
            throw new IllegalStateException("no replica to promote for shard " + shardId);
        }

        ShardAllocation oldReplica = replica.get();
        ShardAllocation promoted = oldReplica.withRole(ShardRole.PRIMARY);
        // replace replica entry with promoted primary; keep closed primary for audit
        List<ShardAllocation> next = new ArrayList<>();
        for (ShardAllocation a : allocations) {
            if (a == oldReplica) {
                next.add(promoted);
            } else {
                next.add(a);
            }
        }
        allocations.clear();
        allocations.addAll(next);
        return promoted;
    }

    public ShardAllocation allocationOn(String nodeId) {
        return allocations.stream()
                .filter(a -> Objects.equals(a.nodeId(), nodeId))
                .findFirst()
                .orElseThrow();
    }
}
