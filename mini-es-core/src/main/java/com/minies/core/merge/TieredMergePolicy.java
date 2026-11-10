package com.minies.core.merge;

import com.minies.core.index.InMemorySegment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Simple educational merge policy: when segment count exceeds {@code maxSegmentCount},
 * merge the smallest half of segments into one.
 */
public final class TieredMergePolicy implements MergePolicy {

    public static final int DEFAULT_MAX_SEGMENT_COUNT = 8;

    private final int maxSegmentCount;

    public TieredMergePolicy() {
        this(DEFAULT_MAX_SEGMENT_COUNT);
    }

    public TieredMergePolicy(int maxSegmentCount) {
        if (maxSegmentCount < 1) {
            throw new IllegalArgumentException("maxSegmentCount must be >= 1");
        }
        this.maxSegmentCount = maxSegmentCount;
    }

    public int maxSegmentCount() {
        return maxSegmentCount;
    }

    @Override
    public List<InMemorySegment> findMerges(List<InMemorySegment> segments) {
        if (segments.size() <= maxSegmentCount) {
            return List.of();
        }

        List<InMemorySegment> sorted = new ArrayList<>(segments);
        sorted.sort(Comparator.comparingInt(InMemorySegment::maxDoc));

        int half = Math.max(2, sorted.size() / 2);
        return List.copyOf(sorted.subList(0, half));
    }
}
