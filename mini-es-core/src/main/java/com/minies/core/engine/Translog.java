package com.minies.core.engine;

import com.minies.core.document.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simplified append-only transaction log of index operations.
 * Cleared on {@link InternalEngine#flush()} after a successful refresh/commit simulation.
 */
public final class Translog {

    public enum OpType {
        INDEX
    }

    public static final class Operation {
        private final OpType type;
        private final Document document;

        public Operation(OpType type, Document document) {
            this.type = type;
            this.document = document;
        }

        public OpType type() {
            return type;
        }

        public Document document() {
            return document;
        }
    }

    private final List<Operation> operations = new ArrayList<>();

    public synchronized void add(Document document) {
        operations.add(new Operation(OpType.INDEX, document));
    }

    public synchronized List<Operation> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(operations));
    }

    public synchronized int size() {
        return operations.size();
    }

    public synchronized void clear() {
        operations.clear();
    }
}
