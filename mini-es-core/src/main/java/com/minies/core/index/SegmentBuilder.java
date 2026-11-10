package com.minies.core.index;

import com.minies.core.analysis.Analyzer;
import com.minies.core.analysis.Token;
import com.minies.core.document.Document;
import com.minies.core.document.Field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable accumulator that turns documents into an immutable {@link InMemorySegment}.
 */
public final class SegmentBuilder {

    private final Analyzer analyzer;
    private final Map<String, Float> defaultFieldBoosts;

    private final List<InMemorySegment.StoredDoc> docs = new ArrayList<>();
    private final Map<String, Map<String, List<Posting>>> postings = new HashMap<>();
    private final Map<String, Float> fieldBoosts = new HashMap<>();
    private final Map<String, Long> sumFieldLengths = new HashMap<>();

    public SegmentBuilder(Analyzer analyzer, Map<String, Float> defaultFieldBoosts) {
        this.analyzer = analyzer;
        this.defaultFieldBoosts = Map.copyOf(defaultFieldBoosts);
    }

    public boolean isEmpty() {
        return docs.isEmpty();
    }

    public int size() {
        return docs.size();
    }

    public void add(Document document) {
        int docOrd = docs.size();
        Map<String, Integer> fieldLengths = new HashMap<>();
        Map<String, String> fieldTexts = new HashMap<>();

        for (Field field : document.fields()) {
            float boost = resolveBoost(field);
            fieldBoosts.merge(field.name(), boost, Math::max);

            List<Token> tokens = analyzer.analyze(field.text());
            fieldLengths.put(field.name(), tokens.size());
            fieldTexts.put(field.name(), field.text());
            sumFieldLengths.merge(field.name(), (long) tokens.size(), Long::sum);

            // term -> frequency within this field for this document
            Map<String, Integer> tf = new HashMap<>();
            for (Token token : tokens) {
                tf.merge(token.term(), 1, Integer::sum);
            }

            Map<String, List<Posting>> byTerm =
                    postings.computeIfAbsent(field.name(), k -> new HashMap<>());
            for (Map.Entry<String, Integer> e : tf.entrySet()) {
                byTerm.computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                        .add(new Posting(docOrd, e.getValue()));
            }
        }

        docs.add(new InMemorySegment.StoredDoc(document.id(), fieldLengths, fieldTexts));
    }

    /**
     * Effective boost: explicit non-default {@link Field#boost()} wins; otherwise writer defaults
     * (e.g. title=3.0, content=1.0).
     */
    private float resolveBoost(Field field) {
        if (field.boost() != 1.0f) {
            return field.boost();
        }
        return defaultFieldBoosts.getOrDefault(field.name(), 1.0f);
    }

    public InMemorySegment build() {
        return new InMemorySegment(postings, docs, fieldBoosts, sumFieldLengths);
    }
}
