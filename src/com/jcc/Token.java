package com.jcc;

/**
 * A single token produced by the Lexer.
 *
 * Tokens are immutable value objects — the Lexer creates them and the
 * Parser reads them; nothing else modifies them.  Every field is public
 * and final so callers can read them directly without getters.
 */
public class Token {

    /** What kind of token this is (keyword, operator, literal, etc.). */
    public final TokenType type;

    /**
     * The raw text from the source file, or the processed value for literals.
     *   IDENTIFIER / keyword  — the original word, e.g. "int", "main"
     *   INT_LITERAL           — decimal digits as a string, e.g. "42"
     *   STRING_LITERAL        — content with escape sequences already decoded
     *   operators/punctuation — the symbol text, e.g. "==", ";"
     *   EOF                   — empty string ""
     */
    public final String value;

    /** 1-based line number where this token appears in the source file. */
    public final int line;

    public Token(TokenType type, String value, int line) {
        this.type  = type;
        this.value = value;
        this.line  = line;
    }

    /** Compact representation used in error messages, e.g. "IDENTIFIER(main)@10". */
    public String toString() {
        return type + "(" + value + ")@" + line;
    }
}
