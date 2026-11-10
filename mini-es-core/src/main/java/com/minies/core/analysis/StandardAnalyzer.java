package com.minies.core.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Educational analyzer: lowercase, split on non-alphanumeric characters
 * (Unicode letters and digits are kept), drop empty tokens.
 */
public final class StandardAnalyzer implements Analyzer {

    @Override
    public List<Token> analyze(String text) {
        List<Token> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }

        StringBuilder current = new StringBuilder();
        int position = 0;

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            if (Character.isLetterOrDigit(cp)) {
                current.appendCodePoint(Character.toLowerCase(cp));
            } else {
                position = flush(current, position, tokens);
            }
        }
        flush(current, position, tokens);
        return tokens;
    }

    private static int flush(StringBuilder current, int position, List<Token> tokens) {
        if (current.length() == 0) {
            return position;
        }
        tokens.add(new Token(current.toString(), position));
        current.setLength(0);
        return position + 1;
    }
}
