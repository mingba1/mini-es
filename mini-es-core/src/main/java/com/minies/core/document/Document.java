package com.minies.core.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An indexable document: stable string id plus a list of {@link Field}s.
 */
public final class Document {
    private final String id;
    private final List<Field> fields;

    public Document(String id) {
        this(id, new ArrayList<>());
    }

    public Document(String id, List<Field> fields) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("document id required");
        }
        this.id = id;
        this.fields = new ArrayList<>(fields);
    }

    public String id() {
        return id;
    }

    public List<Field> fields() {
        return Collections.unmodifiableList(fields);
    }

    public Document add(Field field) {
        fields.add(field);
        return this;
    }

    public Document add(String name, String text) {
        return add(new Field(name, text));
    }

    public Document add(String name, String text, float boost) {
        return add(new Field(name, text, boost));
    }
}
