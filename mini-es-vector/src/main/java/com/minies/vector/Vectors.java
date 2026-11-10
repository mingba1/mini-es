package com.minies.vector;

/**
 * Dense-vector helpers: L2 normalization and cosine (≡ dot product after L2).
 */
public final class Vectors {

    private Vectors() {
    }

    public static float[] l2Normalize(float[] input) {
        if (input == null || input.length == 0) {
            throw new IllegalArgumentException("vector required");
        }
        double sum = 0.0;
        for (float v : input) {
            sum += (double) v * v;
        }
        if (sum == 0.0) {
            throw new IllegalArgumentException("zero vector cannot be L2-normalized");
        }
        float inv = (float) (1.0 / Math.sqrt(sum));
        float[] out = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            out[i] = input[i] * inv;
        }
        return out;
    }

    /** Cosine similarity for already L2-normalized vectors. */
    public static float cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("dimension mismatch: " + a.length + " vs " + b.length);
        }
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return (float) sum;
    }

    public static float norm(float[] v) {
        double sum = 0.0;
        for (float x : v) {
            sum += (double) x * x;
        }
        return (float) Math.sqrt(sum);
    }
}
