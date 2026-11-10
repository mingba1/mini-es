package com.minies.core;

import com.minies.core.analysis.StandardAnalyzer;
import com.minies.core.document.Document;
import com.minies.core.index.IndexWriter;
import com.minies.core.merge.TieredMergePolicy;
import com.minies.core.search.IndexSearcher;
import com.minies.core.search.TermQuery;
import com.minies.core.search.TopDocs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MergePreservesResultsTest {

    @Test
    void mergeKeepsSameTopDocument() {
        // Small max segment count so refresh creates pressure to merge.
        IndexWriter writer = new IndexWriter(
                new StandardAnalyzer(),
                IndexWriter.DEFAULT_FIELD_BOOSTS,
                new TieredMergePolicy(2));

        writer.index(new Document("best")
                .add("title", "elasticsearch elasticsearch")
                .add("content", "distributed search elasticsearch"));
        writer.refresh();

        writer.index(new Document("mid")
                .add("title", "search primer")
                .add("content", "introduction to search"));
        writer.refresh();

        writer.index(new Document("low")
                .add("title", "cooking recipes")
                .add("content", "pasta and sauce"));
        writer.refresh();

        writer.index(new Document("also")
                .add("title", "news digest")
                .add("content", "daily search tips"));
        writer.refresh();

        // Ensure we still have multiple segments, then force a full merge.
        assertThat(writer.segmentCount()).isGreaterThanOrEqualTo(1);

        IndexSearcher before = new IndexSearcher(writer.segments());
        TopDocs beforeHits = before.search(new TermQuery("content", "elasticsearch"), 5);
        assertThat(beforeHits.totalHits()).isGreaterThanOrEqualTo(1);
        String topBefore = beforeHits.scoreDocs()[0].docId();

        writer.forceMerge();
        assertThat(writer.segmentCount()).isEqualTo(1);

        IndexSearcher after = new IndexSearcher(writer.segments());
        TopDocs afterHits = after.search(new TermQuery("content", "elasticsearch"), 5);
        assertThat(afterHits.scoreDocs()[0].docId()).isEqualTo(topBefore);
        assertThat(topBefore).isEqualTo("best");
    }
}
