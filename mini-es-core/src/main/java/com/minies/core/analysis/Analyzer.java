package com.minies.core.analysis;

import java.util.List;

/**
 * Turns raw text into a stream of {@link Token}s (tokenization + normalization).
 */
public interface Analyzer {
    List<Token> analyze(String text);
}
