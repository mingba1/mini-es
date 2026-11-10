package com.minies.vector;

import com.minies.core.document.Document;
import com.minies.core.search.ScoreDoc;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HybridEngineTest {

    @Test
    void rrfHybridReturnsHits() {
        EmbeddingService emb = new MockEmbeddingService(32);
        HybridEngine engine = new HybridEngine(emb, new BruteForceVectorIndex(32), new ReciprocalRankFusion());

        engine.index(new Document("1")
                .add("title", "外汇支付报文异常处理")
                .add("content", "SWIFT MT103 pacs.008 跨境汇款"));
        engine.index(new Document("2")
                .add("title", "番茄炒蛋做法")
                .add("content", "西红柿鸡蛋家常菜"));
        engine.index(new Document("3")
                .add("title", "SWIFT MT103 报文规范")
                .add("content", "支付清算报文格式说明"));
        engine.refresh();

        HybridSearchResult result = engine.search("跨境汇款报文出错怎么办", 5, HybridEngine.FusionMode.RRF);
        assertThat(result.fusion()).isEqualTo("rrf");
        assertThat(result.degraded()).isFalse();
        assertThat(result.hits()).isNotEmpty();
        assertThat(result.hits().get(0).docId()).isIn("1", "3");
    }

    @Test
    void bm25OnlyMode() {
        EmbeddingService emb = new MockEmbeddingService(16);
        HybridEngine engine = new HybridEngine(emb, new BruteForceVectorIndex(16), new ReciprocalRankFusion());
        engine.index(new Document("a").add("title", "hello world").add("content", "alpha"));
        engine.refresh();
        HybridSearchResult r = engine.search("hello", 5, HybridEngine.FusionMode.BM25);
        assertThat(r.fusion()).isEqualTo("bm25");
        assertThat(r.hits()).extracting(ScoreDoc::docId).contains("a");
    }
}
