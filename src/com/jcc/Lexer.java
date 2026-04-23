package com.jcc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final String src;
    private int pos = 0;
    private int line = 1;

    private static final Map<String, TokenType> KEYWORDS = new HashMap<String, TokenType>();
    static {
        KEYWORDS.put("int",    TokenType.KW_INT);
        KEYWORDS.put("return", TokenType.KW_RETURN);
        KEYWORDS.put("void",   TokenType.KW_VOID);
        KEYWORDS.put("char",   TokenType.KW_CHAR);
    }

    public Lexer(String src) {
        this.src = src;
    }

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

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r') {
                pos++;
            } else if (c == '\n') {
                line++;
                pos++;
            } else if (c == '/' && peek(1) == '/') {
                while (pos < src.length() && src.charAt(pos) != '\n') pos++;
            } else if (c == '/' && peek(1) == '*') {
                pos += 2;
                while (pos < src.length() - 1 &&
                       !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) {
                    if (src.charAt(pos) == '\n') line++;
                    pos++;
                }
                pos += 2;
            } else {
                break;
            }
        }
    }

    private char peek(int offset) {
        int i = pos + offset;
        return i < src.length() ? src.charAt(i) : '\0';
    }

    private Token nextToken() {
        int startLine = line;
        char c = src.charAt(pos);

        if (c == '"')  return readString(startLine);
        if (Character.isDigit(c)) return readNumber(startLine);
        if (Character.isLetter(c) || c == '_') return readIdent(startLine);

        switch (c) {
            case '(': pos++; return new Token(TokenType.LPAREN,  "(", startLine);
            case ')': pos++; return new Token(TokenType.RPAREN,  ")", startLine);
            case '{': pos++; return new Token(TokenType.LBRACE,  "{", startLine);
            case '}': pos++; return new Token(TokenType.RBRACE,  "}", startLine);
            case ';': pos++; return new Token(TokenType.SEMI,    ";", startLine);
            case ',': pos++; return new Token(TokenType.COMMA,   ",", startLine);
            case '*': pos++; return new Token(TokenType.STAR,    "*", startLine);
            case '=': pos++; return new Token(TokenType.ASSIGN,  "=", startLine);
            case '.':
                if (peek(1) == '.' && peek(2) == '.') {
                    pos += 3;
                    return new Token(TokenType.ELLIPSIS, "...", startLine);
                }
                throw new RuntimeException("Unexpected '.' at line " + line);
            default:
                throw new RuntimeException(
                    "Unexpected character '" + c + "' (0x" +
                    Integer.toHexString(c) + ") at line " + line);
        }
    }

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
                    default:   sb.append('\\').append(src.charAt(pos));
                }
            } else {
                sb.append(src.charAt(pos));
            }
            pos++;
        }
        pos++; // closing "
        return new Token(TokenType.STRING_LITERAL, sb.toString(), startLine);
    }

    private Token readNumber(int startLine) {
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() && Character.isDigit(src.charAt(pos)))
            sb.append(src.charAt(pos++));
        return new Token(TokenType.INT_LITERAL, sb.toString(), startLine);
    }

    private Token readIdent(int startLine) {
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() &&
               (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_'))
            sb.append(src.charAt(pos++));
        String word = sb.toString();
        TokenType type = KEYWORDS.containsKey(word) ? KEYWORDS.get(word) : TokenType.IDENTIFIER;
        return new Token(type, word, startLine);
    }
}
