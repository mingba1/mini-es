package com.minies.cluster;

/** Assignment of one shard copy. */
public final class ShardAllocation {
    private final int shardId;
    private final ShardRole role;
    private final String nodeId;
    private final ShardEngine engine;

    public ShardAllocation(int shardId, ShardRole role, String nodeId, ShardEngine engine) {
        this.shardId = shardId;
        this.role = role;
        this.nodeId = nodeId;
        this.engine = engine;
    }

    public int shardId() {
        return shardId;
    }

    public ShardRole role() {
        return role;
    }

    public String nodeId() {
        return nodeId;
    }

    public ShardEngine engine() {
        return engine;
    }

    public ShardAllocation withRole(ShardRole newRole) {
        return new ShardAllocation(shardId, newRole, nodeId, engine);
    }
}
