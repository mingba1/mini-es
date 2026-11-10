package com.minies.core.index;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable inverted segment after {@link SegmentBuilder#build()}.
 *
 * <p>Postings layout: {@code field -> term -> List<Posting>} where {@link Posting#docOrd()}
 * is local to this segment.
 */
public final class InMemorySegment {

    /** Stored document metadata kept alongside the inverted index. */
    public static final class StoredDoc {
        private final String id;
        private final Map<String, Integer> fieldLengths;
        private final Map<String, String> fieldTexts;

        public StoredDoc(String id, Map<String, Integer> fieldLengths, Map<String, String> fieldTexts) {
            this.id = id;
            this.fieldLengths = Collections.unmodifiableMap(new HashMap<>(fieldLengths));
            this.fieldTexts = Collections.unmodifiableMap(new HashMap<>(fieldTexts));
        }

        public String id() {
            return id;
        }

        public Map<String, Integer> fieldLengths() {
            return fieldLengths;
        }

        public Map<String, String> fieldTexts() {
            return fieldTexts;
        }
    }

    private final Map<String, Map<String, List<Posting>>> postings;
    private final List<StoredDoc> docs;
    private final Map<String, Float> fieldBoosts;
    private final int maxDoc;
    /** Sum of field token lengths per field name (for avgFieldLength). */
    private final Map<String, Long> sumFieldLengths;

    InMemorySegment(
            Map<String, Map<String, List<Posting>>> postings,
            List<StoredDoc> docs,
            Map<String, Float> fieldBoosts,
            Map<String, Long> sumFieldLengths) {
        this.postings = deepFreezePostings(postings);
        this.docs = List.copyOf(docs);
        this.fieldBoosts = Map.copyOf(fieldBoosts);
        this.maxDoc = this.docs.size();
        this.sumFieldLengths = Map.copyOf(sumFieldLengths);
    }

    private static Map<String, Map<String, List<Posting>>> deepFreezePostings(
            Map<String, Map<String, List<Posting>>> src) {
        Map<String, Map<String, List<Posting>>> outer = new HashMap<>();
        for (Map.Entry<String, Map<String, List<Posting>>> fe : src.entrySet()) {
            Map<String, List<Posting>> inner = new HashMap<>();
            for (Map.Entry<String, List<Posting>> te : fe.getValue().entrySet()) {
                inner.put(te.getKey(), List.copyOf(te.getValue()));
            }
            outer.put(fe.getKey(), Collections.unmodifiableMap(inner));
        }
        return Collections.unmodifiableMap(outer);
    }

    public int maxDoc() {
        return maxDoc;
    }

    public int numDocs() {
        return maxDoc;
    }

    public List<StoredDoc> docs() {
        return docs;
    }

    public StoredDoc doc(int docOrd) {
        return docs.get(docOrd);
    }

    public Map<String, Float> fieldBoosts() {
        return fieldBoosts;
    }

    public float fieldBoost(String field) {
        return fieldBoosts.getOrDefault(field, 1.0f);
    }

    public Map<String, Map<String, List<Posting>>> postings() {
        return postings;
    }

    public List<Posting> postings(String field, String term) {
        Map<String, List<Posting>> byTerm = postings.get(field);
        if (byTerm == null) {
            return List.of();
        }
        List<Posting> list = byTerm.get(term);
        return list == null ? List.of() : list;
    }

    /** Number of documents containing {@code term} in {@code field}. */
    public int docFreq(String field, String term) {
        return postings(field, term).size();
    }

    /** Sum of term frequencies across documents for {@code field}/{@code term}. */
    public long totalTermFreq(String field, String term) {
        long sum = 0;
        for (Posting p : postings(field, term)) {
            sum += p.freq();
        }
        return sum;
    }

    /** Sum of docFreq over all terms in the field (segment helper). */
    public long sumDocFreq(String field) {
        Map<String, List<Posting>> byTerm = postings.get(field);
        if (byTerm == null) {
            return 0;
        }
        long sum = 0;
        for (List<Posting> list : byTerm.values()) {
            sum += list.size();
        }
        return sum;
    }

    /** Sum of totalTermFreq over all terms in the field. */
    public long sumTotalTermFreq(String field) {
        return sumFieldLengths.getOrDefault(field, 0L);
    }

    /**
     * Average field length (token count) across documents in this segment.
     * Uses {@code sumFieldLengths / numDocs} (docs missing the field contribute 0).
     */
    public double avgFieldLength(String field) {
        if (maxDoc == 0) {
            return 0.0;
        }
        return (double) sumFieldLengths.getOrDefault(field, 0L) / (double) maxDoc;
    }

    public int fieldLength(int docOrd, String field) {
        Integer len = docs.get(docOrd).fieldLengths().get(field);
        return len == null ? 0 : len;
    }

    public String docId(int docOrd) {
        return docs.get(docOrd).id();
    }

    @Override
    public String toString() {
        return "InMemorySegment{maxDoc=" + maxDoc + ", fields=" + postings.keySet() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InMemorySegment)) {
            return false;
        }
        InMemorySegment that = (InMemorySegment) o;
        return maxDoc == that.maxDoc && Objects.equals(docs, that.docs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxDoc, docs);
    }
}
