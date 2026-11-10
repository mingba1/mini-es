package com.minies.core;

import com.minies.core.document.Document;
import com.minies.core.engine.InternalEngine;
import com.minies.core.search.TermQuery;
import com.minies.core.search.TopDocs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshVisibilityTest {

    @Test
    void indexedDocsInvisibleUntilRefresh() {
        InternalEngine engine = new InternalEngine();
        engine.index(new Document("doc-1")
                .add("title", "Visible After Refresh")
                .add("content", "nrt semantics"));

        TopDocs before = engine.search(new TermQuery("title", "visible"), 10);
        assertThat(before.totalHits()).isZero();

        engine.refresh();

        TopDocs after = engine.search(new TermQuery("title", "visible"), 10);
        assertThat(after.totalHits()).isEqualTo(1);
        assertThat(after.scoreDocs()[0].docId()).isEqualTo("doc-1");
    }

    @Test
    void flushClearsTranslogAfterRefresh() {
        InternalEngine engine = new InternalEngine();
        engine.index(new Document("x").add("content", "hello world"));
        assertThat(engine.translog().size()).isEqualTo(1);

        engine.flush();

        assertThat(engine.translog().size()).isZero();
        assertThat(engine.search(new TermQuery("content", "hello"), 5).totalHits()).isEqualTo(1);
    }
}
