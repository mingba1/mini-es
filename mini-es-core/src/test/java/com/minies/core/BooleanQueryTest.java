package com.minies.core;

import com.minies.core.document.Document;
import com.minies.core.engine.InternalEngine;
import com.minies.core.search.BooleanQuery;
import com.minies.core.search.TermQuery;
import com.minies.core.search.TopDocs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BooleanQueryTest {

    @Test
    void mustRequiresIntersection() {
        InternalEngine engine = new InternalEngine();
        engine.index(new Document("a")
                .add("title", "java search")
                .add("content", "lucene tutorial"));
        engine.index(new Document("b")
                .add("title", "python search")
                .add("content", "machine learning"));
        engine.index(new Document("c")
                .add("title", "java basics")
                .add("content", "language intro"));
        engine.refresh();

        BooleanQuery query = BooleanQuery.builder()
                .must(new TermQuery("title", "java"))
                .must(new TermQuery("content", "lucene"))
                .build();

        TopDocs hits = engine.search(query, 10);

        assertThat(hits.totalHits()).isEqualTo(1);
        assertThat(hits.scoreDocs()[0].docId()).isEqualTo("a");
    }
}
