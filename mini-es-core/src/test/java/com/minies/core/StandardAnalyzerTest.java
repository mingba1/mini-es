package com.minies.core;

import com.minies.core.analysis.StandardAnalyzer;
import com.minies.core.analysis.Token;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StandardAnalyzerTest {

    @Test
    void tokenizesHelloWorld() {
        StandardAnalyzer analyzer = new StandardAnalyzer();
        List<Token> tokens = analyzer.analyze("Hello, World!");

        assertThat(tokens).extracting(Token::term).containsExactly("hello", "world");
        assertThat(tokens).extracting(Token::position).containsExactly(0, 1);
    }
}
