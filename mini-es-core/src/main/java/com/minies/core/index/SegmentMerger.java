package com.minies.core.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges multiple immutable segments into one, remapping local doc ordinals.
 */
public final class SegmentMerger {

    private SegmentMerger() {
    }

    public static InMemorySegment merge(List<InMemorySegment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("segments required");
        }
        if (segments.size() == 1) {
            return segments.get(0);
        }

        List<InMemorySegment.StoredDoc> docs = new ArrayList<>();
        Map<String, Map<String, List<Posting>>> postings = new HashMap<>();
        Map<String, Float> fieldBoosts = new HashMap<>();
        Map<String, Long> sumFieldLengths = new HashMap<>();

        int docOrdOffset = 0;
        for (InMemorySegment segment : segments) {
            // Stored docs keep their relative order; ordinals shift by offset.
            docs.addAll(segment.docs());

            for (Map.Entry<String, Float> e : segment.fieldBoosts().entrySet()) {
                fieldBoosts.merge(e.getKey(), e.getValue(), Math::max);
            }

            for (Map.Entry<String, Map<String, List<Posting>>> fe : segment.postings().entrySet()) {
                String field = fe.getKey();
                Map<String, List<Posting>> byTerm =
                        postings.computeIfAbsent(field, k -> new HashMap<>());
                for (Map.Entry<String, List<Posting>> te : fe.getValue().entrySet()) {
                    List<Posting> out =
                            byTerm.computeIfAbsent(te.getKey(), k -> new ArrayList<>());
                    for (Posting p : te.getValue()) {
                        out.add(new Posting(p.docOrd() + docOrdOffset, p.freq()));
                    }
                }
            }

            for (InMemorySegment.StoredDoc doc : segment.docs()) {
                for (Map.Entry<String, Integer> e : doc.fieldLengths().entrySet()) {
                    sumFieldLengths.merge(e.getKey(), e.getValue().longValue(), Long::sum);
                }
            }

            docOrdOffset += segment.maxDoc();
        }

        return new InMemorySegment(postings, docs, fieldBoosts, sumFieldLengths);
    }
}
