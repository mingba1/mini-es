package com.minies.core.index;

/**
 * One posting entry: document ordinal within a segment and term frequency in that doc/field.
 */
public final class Posting {
    private final int docOrd;
    private final int freq;

    public Posting(int docOrd, int freq) {
        this.docOrd = docOrd;
        this.freq = freq;
    }

    /** Local ordinal inside the owning {@link InMemorySegment} (0 .. maxDoc-1). */
    public int docOrd() {
        return docOrd;
    }

    public int freq() {
        return freq;
    }

    @Override
    public String toString() {
        return "Posting{docOrd=" + docOrd + ", freq=" + freq + "}";
    }
}
