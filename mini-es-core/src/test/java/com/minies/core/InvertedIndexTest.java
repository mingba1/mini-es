package com.minies.core;

import com.minies.core.analysis.StandardAnalyzer;
import com.minies.core.document.Document;
import com.minies.core.index.InMemorySegment;
import com.minies.core.index.IndexWriter;
import com.minies.core.index.Posting;
import com.minies.core.index.SegmentBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvertedIndexTest {

    @Test
    void indexesTwoDocsAndFindsTermPostings() {
        SegmentBuilder builder = new SegmentBuilder(new StandardAnalyzer(), IndexWriter.DEFAULT_FIELD_BOOSTS);

        builder.add(new Document("1")
                .add("title", "Elasticsearch Guide")
                .add("content", "full text search engine"));
        builder.add(new Document("2")
                .add("title", "Lucene Internals")
                .add("content", "inverted index and search"));

        InMemorySegment segment = builder.build();

        assertThat(segment.numDocs()).isEqualTo(2);

        List<Posting> searchPostings = segment.postings("content", "search");
        assertThat(searchPostings).hasSize(2);
        assertThat(searchPostings).extracting(Posting::docOrd).containsExactly(0, 1);
        assertThat(searchPostings).extracting(Posting::freq).containsExactly(1, 1);

        List<Posting> luceneTitle = segment.postings("title", "lucene");
        assertThat(luceneTitle).hasSize(1);
        assertThat(luceneTitle.get(0).docOrd()).isEqualTo(1);
        assertThat(segment.docId(1)).isEqualTo("2");
    }
}
