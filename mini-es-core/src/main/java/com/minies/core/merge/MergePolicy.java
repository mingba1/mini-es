package com.minies.core.merge;

import com.minies.core.index.InMemorySegment;

import java.util.List;

/**
 * Decides which committed segments (if any) should be merged next.
 */
public interface MergePolicy {

    /**
     * @return a non-empty list of segments to merge, or empty if no merge is needed
     */
    List<InMemorySegment> findMerges(List<InMemorySegment> segments);
}
