package com.minies.vector;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Deterministic hash-projection embedder for tests (no ONNX / HTTP).
 *
 * <p>Tokens contribute into fixed dims via FNV-1a; result is L2-normalized.
 */
public final class MockEmbeddingService implements EmbeddingService {

    private final int dims;
    private final String modelId;

    public MockEmbeddingService(int dims) {
        this(dims, "mock-hash-" + dims);
    }

    public MockEmbeddingService(int dims, String modelId) {
        if (dims <= 0) {
            throw new IllegalArgumentException("dims must be > 0");
        }
        this.dims = dims;
        this.modelId = modelId;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public int dims() {
        return dims;
    }

    @Override
    public float[] embedPassage(String text) {
        return embed(text == null ? "" : text);
    }

    @Override
    public float[] embedQuery(String text) {
        // Query-only instruction asymmetry (mirrors BGE-style prefix without real NLP).
        String q = text == null ? "" : text;
        return embed("query: " + q);
    }

    private float[] embed(String text) {
        float[] v = new float[dims];
        String normalized = text.toLowerCase(Locale.ROOT);
        String[] parts = normalized.split("[^\\p{L}\\p{N}]+");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            int h = fnv1a(part.getBytes(StandardCharsets.UTF_8));
            int idx = Math.floorMod(h, dims);
            float sign = (h & 1) == 0 ? 1.0f : -1.0f;
            v[idx] += sign;
            // spill a bit of mass into neighbor dims for smoother similarity
            v[Math.floorMod(idx + 1, dims)] += 0.25f * sign;
        }
        // avoid zero vector for empty text
        boolean any = false;
        for (float x : v) {
            if (x != 0f) {
                any = true;
                break;
            }
        }
        if (!any) {
            v[0] = 1.0f;
        }
        return Vectors.l2Normalize(v);
    }

    private static int fnv1a(byte[] data) {
        int hash = 0x811c9dc5;
        for (byte b : data) {
            hash ^= (b & 0xff);
            hash *= 0x01000193;
        }
        return hash;
    }
}
