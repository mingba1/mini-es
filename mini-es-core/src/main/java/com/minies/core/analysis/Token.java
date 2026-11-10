package com.minies.core.analysis;

/**
 * A single analyzed token: the normalized term string and its position in the token stream.
 */
public final class Token {
    private final String term;
    private final int position;

    public Token(String term, int position) {
        this.term = term;
        this.position = position;
    }

    public String term() {
        return term;
    }

    public int position() {
        return position;
    }

    @Override
    public String toString() {
        return "Token{term='" + term + "', position=" + position + "}";
    }
}
