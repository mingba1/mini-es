package com.minies.core.document;

/**
 * A named text field on a document. {@code boost} defaults to {@code 1.0f};
 * non-default boosts override {@link com.minies.core.index.IndexWriter} field defaults.
 */
public final class Field {
    private final String name;
    private final String text;
    private final float boost;

    public Field(String name, String text) {
        this(name, text, 1.0f);
    }

    public Field(String name, String text, float boost) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("field name required");
        }
        this.name = name;
        this.text = text == null ? "" : text;
        this.boost = boost;
    }

    public String name() {
        return name;
    }

    public String text() {
        return text;
    }

    public float boost() {
        return boost;
    }
}
