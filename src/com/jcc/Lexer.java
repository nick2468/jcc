package com.jcc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a C source string into a flat list of Token objects.
 *
 * Single-pass, character-at-a-time.  The main entry point is tokenize().
 * Whitespace, line comments (//) and block comments (/* *\/) are silently
 * consumed between tokens.  Line numbers are tracked so error messages
 * can reference source locations.
 */
public class Lexer {

    private final String src;
    private int pos  = 0;   // index of next character to read
    private int line = 1;   // current 1-based source line number

    /** C keywords mapped to their token type. Everything else is IDENTIFIER. */
    private static final Map<String, TokenType> KEYWORDS = new HashMap<String, TokenType>();
    static {
        KEYWORDS.put("int",    TokenType.KW_INT);
        KEYWORDS.put("return", TokenType.KW_RETURN);
        KEYWORDS.put("void",   TokenType.KW_VOID);
        KEYWORDS.put("char",   TokenType.KW_CHAR);
        KEYWORDS.put("if",     TokenType.KW_IF);
        KEYWORDS.put("else",   TokenType.KW_ELSE);
        KEYWORDS.put("while",  TokenType.KW_WHILE);
        // static/const/unsigned/signed are consumed as qualifiers in the
        // parser, not as distinct keywords — leave them as IDENTIFIER tokens.
    }

    public Lexer(String src) {
        this.src = src;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Tokenize the entire source string.  Always ends with an EOF token. */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<Token>();
        while (pos < src.length()) {
            skipWhitespace();
            if (pos >= src.length()) break;
            tokens.add(nextToken());
        }
        tokens.add(new Token(TokenType.EOF, "", line));
        return tokens;
    }

    // ── Whitespace / comment skipping ─────────────────────────────────────

    /**
     * Advance past whitespace characters and comments.
     * Newlines increment the line counter so later tokens have correct line numbers.
     */
    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r') {
                pos++;
            } else if (c == '\n') {
                line++;
                pos++;
            } else if (c == '/' && peek(1) == '/') {
                // Line comment — skip to end of line
                while (pos < src.length() && src.charAt(pos) != '\n') pos++;
            } else if (c == '/' && peek(1) == '*') {
                // Block comment — skip until */
                pos += 2;
                while (pos < src.length() - 1 &&
                       !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) {
                    if (src.charAt(pos) == '\n') line++;
                    pos++;
                }
                pos += 2; // skip closing */
            } else {
                break; // real token starts here
            }
        }
    }

    // ── Single-token production ────────────────────────────────────────────

    /** Read the next token from the current position.  Never returns null. */
    private Token nextToken() {
        int startLine = line;
        char c = src.charAt(pos);

        // Dispatch on first character
        if (c == '"')                             return readString(startLine);
        if (Character.isDigit(c))                 return readNumber(startLine);
        if (Character.isLetter(c) || c == '_')   return readIdent(startLine);

        // Single- and double-character punctuation / operators
        switch (c) {
            // Delimiters
            case '(': pos++; return tok(TokenType.LPAREN,   "(", startLine);
            case ')': pos++; return tok(TokenType.RPAREN,   ")", startLine);
            case '{': pos++; return tok(TokenType.LBRACE,   "{", startLine);
            case '}': pos++; return tok(TokenType.RBRACE,   "}", startLine);
            case '[': pos++; return tok(TokenType.LBRACKET, "[", startLine);
            case ']': pos++; return tok(TokenType.RBRACKET, "]", startLine);
            case ';': pos++; return tok(TokenType.SEMI,     ";", startLine);
            case ',': pos++; return tok(TokenType.COMMA,    ",", startLine);
            case '%': pos++; return tok(TokenType.PERCENT,  "%", startLine);
            case '+': pos++; return tok(TokenType.PLUS,     "+", startLine);
            case '/': pos++; return tok(TokenType.SLASH,    "/", startLine);
            case '&': pos++; return tok(TokenType.AMP,      "&", startLine);

            // * is both multiply and pointer-dereference
            case '*': pos++; return tok(TokenType.STAR,     "*", startLine);

            // - is both subtract and unary negate
            case '-': pos++; return tok(TokenType.MINUS,    "-", startLine);

            // = vs ==
            case '=':
                if (peek(1) == '=') { pos += 2; return tok(TokenType.EQ_EQ,  "==", startLine); }
                pos++; return tok(TokenType.ASSIGN, "=", startLine);

            // ! vs !=
            case '!':
                if (peek(1) == '=') { pos += 2; return tok(TokenType.BANG_EQ, "!=", startLine); }
                pos++; return tok(TokenType.BANG, "!", startLine);

            // < vs <=
            case '<':
                if (peek(1) == '=') { pos += 2; return tok(TokenType.LT_EQ, "<=", startLine); }
                pos++; return tok(TokenType.LT, "<", startLine);

            // > vs >=
            case '>':
                if (peek(1) == '=') { pos += 2; return tok(TokenType.GT_EQ, ">=", startLine); }
                pos++; return tok(TokenType.GT, ">", startLine);

            // ... (variadic parameter marker)
            case '.':
                if (peek(1) == '.' && peek(2) == '.') {
                    pos += 3;
                    return tok(TokenType.ELLIPSIS, "...", startLine);
                }
                throw new RuntimeException("Unexpected '.' at line " + line);

            default:
                throw new RuntimeException(
                    "Unexpected character '" + c + "' (0x" +
                    Integer.toHexString(c) + ") at line " + line);
        }
    }

    // ── Compound token readers ─────────────────────────────────────────────

    /** Read a double-quoted string literal, interpreting backslash escapes. */
    private Token readString(int startLine) {
        pos++; // opening "
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() && src.charAt(pos) != '"') {
            if (src.charAt(pos) == '\\') {
                pos++;
                switch (src.charAt(pos)) {
                    case 'n':  sb.append('\n'); break;
                    case 't':  sb.append('\t'); break;
                    case 'r':  sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    case '"':  sb.append('"');  break;
                    case '0':  sb.append('\0'); break;
                    default:   sb.append('\\').append(src.charAt(pos)); break;
                }
            } else {
                sb.append(src.charAt(pos));
            }
            pos++;
        }
        pos++; // closing "
        return tok(TokenType.STRING_LITERAL, sb.toString(), startLine);
    }

    /** Read a sequence of digits as a decimal integer literal. */
    private Token readNumber(int startLine) {
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() && Character.isDigit(src.charAt(pos)))
            sb.append(src.charAt(pos++));
        return tok(TokenType.INT_LITERAL, sb.toString(), startLine);
    }

    /**
     * Read an identifier or keyword.
     * After collecting the word, look it up in KEYWORDS; if found, use that
     * token type, otherwise fall back to IDENTIFIER.
     */
    private Token readIdent(int startLine) {
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() &&
               (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_'))
            sb.append(src.charAt(pos++));
        String word = sb.toString();
        TokenType type = KEYWORDS.containsKey(word) ? KEYWORDS.get(word) : TokenType.IDENTIFIER;
        return tok(type, word, startLine);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Look ahead without consuming; returns '\0' if beyond end of source. */
    private char peek(int offset) {
        int i = pos + offset;
        return i < src.length() ? src.charAt(i) : '\0';
    }

    private Token tok(TokenType type, String value, int line) {
        return new Token(type, value, line);
    }
}
