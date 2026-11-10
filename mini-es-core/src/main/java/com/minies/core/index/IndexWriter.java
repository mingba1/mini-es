package com.minies.core.index;

import com.minies.core.analysis.Analyzer;
import com.minies.core.analysis.StandardAnalyzer;
import com.minies.core.document.Document;
import com.minies.core.merge.MergePolicy;
import com.minies.core.merge.TieredMergePolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds a mutable in-memory buffer ({@link SegmentBuilder}) plus a list of committed
 * {@link InMemorySegment}s. Documents become searchable only after {@link #refresh()}.
 */
public final class IndexWriter {

    public static final Map<String, Float> DEFAULT_FIELD_BOOSTS = Map.of(
            "title", 3.0f,
            "content", 1.0f
    );

    private final Analyzer analyzer;
    private final Map<String, Float> defaultFieldBoosts;
    private final MergePolicy mergePolicy;

    private SegmentBuilder buffer;
    private final List<InMemorySegment> segments = new ArrayList<>();

    public IndexWriter() {
        this(new StandardAnalyzer(), DEFAULT_FIELD_BOOSTS, new TieredMergePolicy());
    }

    public IndexWriter(Analyzer analyzer, Map<String, Float> defaultFieldBoosts, MergePolicy mergePolicy) {
        this.analyzer = analyzer;
        this.defaultFieldBoosts = Map.copyOf(defaultFieldBoosts);
        this.mergePolicy = mergePolicy;
        this.buffer = new SegmentBuilder(analyzer, this.defaultFieldBoosts);
    }

    public Analyzer analyzer() {
        return analyzer;
    }

    public Map<String, Float> defaultFieldBoosts() {
        return defaultFieldBoosts;
    }

    /** Add a document to the mutable buffer (not yet searchable). */
    public synchronized void index(Document document) {
        buffer.add(document);
    }

    /**
     * Freeze the buffer into a new immutable segment and clear the buffer.
     * No-op if the buffer is empty.
     */
    public synchronized void refresh() {
        if (buffer.isEmpty()) {
            return;
        }
        segments.add(buffer.build());
        buffer = new SegmentBuilder(analyzer, defaultFieldBoosts);
        maybeMerge();
    }

    /**
     * Apply {@link MergePolicy}: if it selects segments, replace them with one merged segment.
     */
    public synchronized void maybeMerge() {
        List<InMemorySegment> toMerge = mergePolicy.findMerges(List.copyOf(segments));
        if (toMerge.isEmpty()) {
            return;
        }

        InMemorySegment merged = SegmentMerger.merge(toMerge);
        // Preserve relative order of untouched segments; append the merge result.
        List<InMemorySegment> next = new ArrayList<>();
        for (InMemorySegment s : segments) {
            if (!toMerge.contains(s)) {
                next.add(s);
            }
        }
        next.add(merged);
        segments.clear();
        segments.addAll(next);
    }

    /** Snapshot of committed segments (excludes the live buffer). */
    public synchronized List<InMemorySegment> segments() {
        return List.copyOf(segments);
    }

    public synchronized int segmentCount() {
        return segments.size();
    }

    public synchronized int bufferedDocs() {
        return buffer.size();
    }

    /** Force-merge every committed segment into one (useful for tests). */
    public synchronized void forceMerge() {
        if (segments.size() <= 1) {
            return;
        }
        InMemorySegment merged = SegmentMerger.merge(List.copyOf(segments));
        segments.clear();
        segments.add(merged);
    }

    /** Debug helper: field boost defaults as insertion-ordered map. */
    public static Map<String, Float> mutableDefaultBoosts() {
        return new LinkedHashMap<>(DEFAULT_FIELD_BOOSTS);
    }

    @Override
    public synchronized String toString() {
        return "IndexWriter{segments=" + segments.size()
                + ", buffered=" + buffer.size()
                + ", defaults=" + Collections.unmodifiableMap(defaultFieldBoosts) + "}";
    }
}
