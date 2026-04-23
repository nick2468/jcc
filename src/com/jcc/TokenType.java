package com.jcc;

/**
 * Every distinct kind of token the lexer can produce.
 * The parser uses these to decide which grammar rule applies.
 * Adding a new operator or keyword means adding it here first.
 */
public enum TokenType {

    // ── Literals ──────────────────────────────────────────────────────────
    INT_LITERAL,        // e.g. 42, 0
    STRING_LITERAL,     // e.g. "hello\n"  (value already unescaped by lexer)

    // ── Names ─────────────────────────────────────────────────────────────
    IDENTIFIER,         // any name that isn't a keyword

    // ── Keywords ──────────────────────────────────────────────────────────
    KW_INT,
    KW_RETURN,
    KW_VOID,
    KW_CHAR,
    KW_IF,
    KW_ELSE,
    KW_WHILE,

    // ── Delimiters ────────────────────────────────────────────────────────
    LPAREN,             // (
    RPAREN,             // )
    LBRACE,             // {
    RBRACE,             // }
    LBRACKET,           // [
    RBRACKET,           // ]
    SEMI,               // ;
    COMMA,              // ,
    ELLIPSIS,           // ...

    // ── Assignment ────────────────────────────────────────────────────────
    ASSIGN,             // =   (must be tested BEFORE EQ_EQ)

    // ── Comparison operators ──────────────────────────────────────────────
    EQ_EQ,              // ==
    BANG_EQ,            // !=
    LT,                 // <
    GT,                 // >
    LT_EQ,              // <=
    GT_EQ,              // >=

    // ── Arithmetic operators ──────────────────────────────────────────────
    PLUS,               // +
    MINUS,              // -   (also unary negation)
    STAR,               // *   (also pointer dereference)
    SLASH,              // /
    PERCENT,            // %

    // ── Other unary operators ─────────────────────────────────────────────
    AMP,                // &   (address-of, or bitwise AND when binary)
    BANG,               // !   (logical not)

    // ── Sentinel ──────────────────────────────────────────────────────────
    EOF
}
