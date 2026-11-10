package com.minies.core.search;

import java.util.Map;

/**
 * Educational query: accumulates per-document scores into a mutable map keyed by doc id.
 */
public interface Query {

    /**
     * Score this query against the searcher's segments, adding into {@code scores}
     * (existing values are summed — useful for boolean composition).
     */
    void accumulateScores(IndexSearcher searcher, Map<String, Float> scores);
}
