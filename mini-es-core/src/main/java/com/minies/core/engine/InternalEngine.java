package com.minies.core.engine;

import com.minies.core.analysis.Analyzer;
import com.minies.core.analysis.StandardAnalyzer;
import com.minies.core.document.Document;
import com.minies.core.index.IndexWriter;
import com.minies.core.merge.MergePolicy;
import com.minies.core.merge.TieredMergePolicy;
import com.minies.core.search.IndexSearcher;
import com.minies.core.search.Query;
import com.minies.core.search.TopDocs;
import com.minies.core.similarity.BM25Similarity;

import java.util.Map;

/**
 * Thin engine façade: durable-ish translog + {@link IndexWriter} + NRT {@link IndexSearcher}.
 *
 * <p>Documents are not visible to {@link #searcher()} until {@link #refresh()}.
 */
public final class InternalEngine {

    private final IndexWriter writer;
    private final Translog translog;
    private final BM25Similarity similarity;
    private volatile IndexSearcher searcher;

    public InternalEngine() {
        this(new StandardAnalyzer(), IndexWriter.DEFAULT_FIELD_BOOSTS, new TieredMergePolicy());
    }

    public InternalEngine(Analyzer analyzer, Map<String, Float> fieldBoosts, MergePolicy mergePolicy) {
        this.writer = new IndexWriter(analyzer, fieldBoosts, mergePolicy);
        this.translog = new Translog();
        this.similarity = new BM25Similarity();
        this.searcher = new IndexSearcher(writer.segments(), similarity);
    }

    public IndexWriter writer() {
        return writer;
    }

    public Translog translog() {
        return translog;
    }

    /** Append to translog and buffer in the writer (not yet searchable). */
    public synchronized void index(Document document) {
        translog.add(document);
        writer.index(document);
    }

    /**
     * Commit the writer buffer into a segment and publish a new searcher snapshot.
     */
    public synchronized void refresh() {
        writer.refresh();
        searcher = new IndexSearcher(writer.segments(), similarity);
    }

    /** Current near-real-time searcher (excludes unrefreshed buffer docs). */
    public IndexSearcher searcher() {
        return searcher;
    }

    public TopDocs search(Query query, int n) {
        return searcher().search(query, n);
    }

    /**
     * Simulate a commit: refresh so data is searchable, then clear the translog.
     */
    public synchronized void flush() {
        refresh();
        translog.clear();
    }

    public Analyzer analyzer() {
        return writer.analyzer();
    }
}
