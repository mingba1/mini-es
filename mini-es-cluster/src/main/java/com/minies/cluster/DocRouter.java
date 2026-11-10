package com.minies.cluster;

/**
 * ES-style document routing: {@code shardId = hash(routingKey) % numberOfPrimaryShards}.
 */
public final class DocRouter {

    private final int numberOfPrimaryShards;

    public DocRouter(int numberOfPrimaryShards) {
        if (numberOfPrimaryShards <= 0) {
            throw new IllegalArgumentException("numberOfPrimaryShards must be > 0");
        }
        this.numberOfPrimaryShards = numberOfPrimaryShards;
    }

    public int numberOfPrimaryShards() {
        return numberOfPrimaryShards;
    }

    public int shardId(String routingKey) {
        if (routingKey == null || routingKey.isEmpty()) {
            throw new IllegalArgumentException("routingKey required");
        }
        return Math.floorMod(murmur3(routingKey), numberOfPrimaryShards);
    }

    /** Simple 32-bit murmur3-ish mix for stable teaching demos. */
    static int murmur3(String key) {
        int h = 0x9747b28c;
        for (int i = 0; i < key.length(); i++) {
            h ^= key.charAt(i);
            h *= 0x5bd1e995;
            h ^= h >>> 15;
        }
        return h;
    }
}
