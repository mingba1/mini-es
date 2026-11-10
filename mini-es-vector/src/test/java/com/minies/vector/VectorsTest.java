package com.minies.vector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class VectorsTest {

    @Test
    void l2NormalizeUnitLength() {
        float[] n = Vectors.l2Normalize(new float[]{3f, 4f});
        assertThat(Vectors.norm(n)).isCloseTo(1.0f, within(1e-5f));
        assertThat(n[0]).isCloseTo(0.6f, within(1e-5f));
        assertThat(n[1]).isCloseTo(0.8f, within(1e-5f));
    }

    @Test
    void cosineOfIdenticalUnitVectorsIsOne() {
        float[] a = Vectors.l2Normalize(new float[]{1f, 2f, 3f});
        assertThat(Vectors.cosine(a, a)).isCloseTo(1.0f, within(1e-5f));
    }

    @Test
    void zeroVectorRejected() {
        assertThatThrownBy(() -> Vectors.l2Normalize(new float[]{0f, 0f}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
