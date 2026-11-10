package com.minies.core.search;

import com.minies.core.analysis.Analyzer;
import com.minies.core.analysis.Token;
import com.minies.core.index.IndexWriter;

import java.util.List;
import java.util.Map;

/**
 * Helper that expands free text into a SHOULD {@link BooleanQuery} over multiple fields
 * (default: title + content with index-time boosts mirrored as query-time boosts).
 */
public final class MultiMatchQuery {

    private MultiMatchQuery() {
    }

    public static Query of(String text, Analyzer analyzer) {
        return of(text, analyzer, IndexWriter.DEFAULT_FIELD_BOOSTS);
    }

    /**
     * Analyze {@code text} and create SHOULD {@link TermQuery}s for each token × field,
     * using the provided per-field query boosts.
     */
    public static Query of(String text, Analyzer analyzer, Map<String, Float> fieldBoosts) {
        BooleanQuery.Builder builder = BooleanQuery.builder();
        List<Token> tokens = analyzer.analyze(text);
        boolean any = false;
        for (Token token : tokens) {
            for (Map.Entry<String, Float> field : fieldBoosts.entrySet()) {
                builder.should(new TermQuery(field.getKey(), token.term(), field.getValue()));
                any = true;
            }
        }
        if (!any) {
            // Empty text → match nothing via a MUST that no document can satisfy.
            return new TermQuery("__none__", "__empty__");
        }
        return builder.build();
    }
}
