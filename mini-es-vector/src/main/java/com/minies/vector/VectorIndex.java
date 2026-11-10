package com.minies.vector;

import java.util.List;

/** Dense vector ANN / exact index. Vectors must already be L2-normalized. */
public interface VectorIndex {

    void add(String docId, float[] vector);

    List<VectorHit> search(float[] query, int k);

    int size();
}
