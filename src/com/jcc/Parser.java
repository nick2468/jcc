package com.jcc;

import com.jcc.ast.AST;
import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser: turns a flat Token list into an AST.
 *
 * Entry point: parse() → AST.TranslationUnit
 *
 * Grammar handled (informal):
 *
 *   translation-unit  → (global-var-decl | function-def | function-decl)*
 *   global-var-decl   → type ident ";"
 *   function-def      → type ident "(" params ")" compound-stmt
 *   function-decl     → type ident "(" params ")" ";"
 *
 *   compound-stmt     → "{" stmt* "}"
 *   stmt              → decl-stmt | assign-stmt | if-stmt | while-stmt
 *                     | return-stmt | expr-stmt
 *
 *   expr              → compare-expr
 *   compare-expr      → add-expr ((== != < > <= >=) add-expr)*
 *   add-expr          → mul-expr ((+ -) mul-expr)*
 *   mul-expr          → unary-expr ((* / %) unary-expr)*
 *   unary-expr        → (- ! & *) unary-expr | postfix-expr
 *   postfix-expr      → primary ("[" expr "]" | "(" args ")")*
 *   primary           → ident | int-lit | string-lit | "(" expr ")"
 */
public class Parser {

    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // ── Token navigation ──────────────────────────────────────────────────

    private Token peek()               { return tokens.get(pos); }
    private Token consume()            { return tokens.get(pos++); }
    private boolean check(TokenType t) { return peek().type == t; }

    /** Consume and return the next token, but throw if it isn't the expected type. */
    private Token expect(TokenType type) {
        Token t = consume();
        if (t.type != type)
            throw new RuntimeException(
                "Expected " + type + " but got " + t + " at line " + t.line);
        return t;
    }

    /** Consume iff the current token matches; return whether we consumed. */
    private boolean match(TokenType type) {
        if (check(type)) { consume(); return true; }
        return false;
    }

    /** Look at any token in the stream without consuming. Safe if index is past EOF. */
    private Token tokenAt(int i) {
        return tokens.get(Math.min(i, tokens.size() - 1));
    }

    /** True if the current token can begin a type (keyword or common qualifier word). */
    private boolean isTypeStart() {
        TokenType t = peek().type;
        if (t == TokenType.KW_INT || t == TokenType.KW_VOID || t == TokenType.KW_CHAR)
            return true;
        // qualifiers stored as IDENTIFIER tokens
        String v = peek().value;
        return t == TokenType.IDENTIFIER &&
               (v.equals("const") || v.equals("unsigned") || v.equals("signed")
                || v.equals("static") || v.equals("extern") || v.equals("long"));
    }

    // ── Top-level ─────────────────────────────────────────────────────────

    /**
     * Parse the whole translation unit.
     * Distinguishes global variable declarations from function definitions/declarations
     * by looking past the type and name to see whether "(" follows.
     */
    public AST.TranslationUnit parse() {
        List<AST.GlobalVarDecl> globals   = new ArrayList<AST.GlobalVarDecl>();
        List<AST.FunctionDef>   functions = new ArrayList<AST.FunctionDef>();

        while (!check(TokenType.EOF)) {
            AST.TypeSpec type = parseTypeSpec();
            String name = expect(TokenType.IDENTIFIER).value;

            if (check(TokenType.LPAREN)) {
                // Function definition or forward declaration
                consume(); // (
                List<AST.ParamDecl> params = parseParamList();
                expect(TokenType.RPAREN);
                if (match(TokenType.SEMI)) {
                    // Forward declaration: body is null
                    functions.add(new AST.FunctionDef(type, name, params, null));
                } else {
                    functions.add(new AST.FunctionDef(type, name, params, parseCompound()));
                }
            } else {
                // Global variable declaration: "int x;"
                expect(TokenType.SEMI);
                globals.add(new AST.GlobalVarDecl(type, name));
            }
        }
        return new AST.TranslationUnit(globals, functions);
    }

    // ── Types ─────────────────────────────────────────────────────────────

    /**
     * Parse a type specifier, consuming optional qualifiers first.
     * Handles: [static] [extern] [const] [unsigned|signed] base-type [*]*
     * e.g. "const char *", "static int", "void"
     */
    private AST.TypeSpec parseTypeSpec() {
        // Consume any storage-class or cv-qualifiers
        while (check(TokenType.IDENTIFIER)) {
            String v = peek().value;
            if (v.equals("const") || v.equals("unsigned") || v.equals("signed")
                    || v.equals("static") || v.equals("extern") || v.equals("long"))
                consume();
            else
                break;
        }

        String base;
        if      (check(TokenType.KW_INT))  { consume(); base = "int";  }
        else if (check(TokenType.KW_VOID)) { consume(); base = "void"; }
        else if (check(TokenType.KW_CHAR)) { consume(); base = "char"; }
        else throw new RuntimeException(
            "Expected type specifier at line " + peek().line + ", got " + peek());

        int ptrs = 0;
        while (match(TokenType.STAR)) ptrs++;
        return new AST.TypeSpec(base, ptrs);
    }

    // ── Parameter lists ───────────────────────────────────────────────────

    /**
     * Parse a comma-separated parameter list, stopping before ")".
     * Handles: void (empty list), type name, ..., and unnamed type params.
     */
    private List<AST.ParamDecl> parseParamList() {
        List<AST.ParamDecl> params = new ArrayList<AST.ParamDecl>();
        if (check(TokenType.RPAREN)) return params;   // empty list: f()

        // f(void) treated as empty parameter list
        if (check(TokenType.KW_VOID) && tokenAt(pos + 1).type == TokenType.RPAREN) {
            consume(); // void
            return params;
        }

        while (true) {
            if (check(TokenType.ELLIPSIS)) {
                consume();
                params.add(new AST.ParamDecl(null, null, true));
                break; // ... must be last
            }
            AST.TypeSpec type  = parseTypeSpec();
            String paramName   = check(TokenType.IDENTIFIER) ? consume().value : null;
            params.add(new AST.ParamDecl(type, paramName, false));
            if (!match(TokenType.COMMA)) break;
        }
        return params;
    }

    // ── Statements ────────────────────────────────────────────────────────

    /** Parse a block of statements: "{ stmt* }" */
    private AST.CompoundStmt parseCompound() {
        expect(TokenType.LBRACE);
        List<AST.Stmt> stmts = new ArrayList<AST.Stmt>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF))
            stmts.add(parseStmt());
        expect(TokenType.RBRACE);
        return new AST.CompoundStmt(stmts);
    }

    /**
     * Parse one statement, dispatching on the leading token.
     *
     *   type-keyword   → local variable declaration
     *   return         → return statement
     *   if             → if / if-else
     *   while          → while loop
     *   {              → nested compound block
     *   ident =        → assignment statement
     *   otherwise      → expression statement (call etc.)
     */
    private AST.Stmt parseStmt() {
        // Local variable declaration: "int i;" or "char tmp;"
        if (isTypeStart()) {
            AST.TypeSpec type = parseTypeSpec();
            String name = expect(TokenType.IDENTIFIER).value;
            expect(TokenType.SEMI);
            return new AST.DeclStmt(type, name);
        }

        if (check(TokenType.KW_RETURN)) {
            consume();
            AST.Expr val = check(TokenType.SEMI) ? null : parseExpr();
            expect(TokenType.SEMI);
            return new AST.ReturnStmt(val);
        }

        if (check(TokenType.KW_IF)) {
            consume();
            expect(TokenType.LPAREN);
            AST.Expr cond = parseExpr();
            expect(TokenType.RPAREN);
            AST.Stmt then = parseStmtOrCompound();
            AST.Stmt else_ = null;
            if (match(TokenType.KW_ELSE)) else_ = parseStmtOrCompound();
            return new AST.IfStmt(cond, then, else_);
        }

        if (check(TokenType.KW_WHILE)) {
            consume();
            expect(TokenType.LPAREN);
            AST.Expr cond = parseExpr();
            expect(TokenType.RPAREN);
            return new AST.WhileStmt(cond, parseStmtOrCompound());
        }

        if (check(TokenType.LBRACE)) return parseCompound();

        // Assignment: "ident = expr;"  — detected by two-token lookahead
        if (check(TokenType.IDENTIFIER) && tokenAt(pos + 1).type == TokenType.ASSIGN) {
            String name = consume().value;  // consume identifier
            consume();                       // consume =
            AST.Expr rhs = parseExpr();
            expect(TokenType.SEMI);
            return new AST.AssignStmt(name, rhs);
        }

        // Expression statement: function call, etc.
        AST.Expr expr = parseExpr();
        expect(TokenType.SEMI);
        return new AST.ExprStmt(expr);
    }

    /**
     * Parse either a compound block or a single statement.
     * Used for if/while bodies that may or may not use braces.
     */
    private AST.Stmt parseStmtOrCompound() {
        if (check(TokenType.LBRACE)) return parseCompound();
        return parseStmt();
    }

    // ── Expression parsing (recursive descent with precedence) ────────────

    /**
     * Top-level expression entry point.
     * Delegates into the precedence chain: compare → add → mul → unary → postfix → primary.
     */
    private AST.Expr parseExpr() {
        return parseCompare();
    }

    /**
     * Comparison: a == b,  a != b,  a < b,  a > b,  a <= b,  a >= b.
     * Left-associative, lower precedence than arithmetic.
     */
    private AST.Expr parseCompare() {
        AST.Expr left = parseAdd();
        while (true) {
            String op = null;
            if      (check(TokenType.EQ_EQ))  op = "==";
            else if (check(TokenType.BANG_EQ)) op = "!=";
            else if (check(TokenType.LT))      op = "<";
            else if (check(TokenType.GT))      op = ">";
            else if (check(TokenType.LT_EQ))   op = "<=";
            else if (check(TokenType.GT_EQ))   op = ">=";
            else break;
            consume();
            left = new AST.BinaryExpr(op, left, parseAdd());
        }
        return left;
    }

    /** Additive: a + b, a - b.  Left-associative. */
    private AST.Expr parseAdd() {
        AST.Expr left = parseMul();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            String op = consume().value;
            left = new AST.BinaryExpr(op, left, parseMul());
        }
        return left;
    }

    /** Multiplicative: a * b, a / b, a % b.  Left-associative. */
    private AST.Expr parseMul() {
        AST.Expr left = parseUnary();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
            String op = consume().value;
            left = new AST.BinaryExpr(op, left, parseUnary());
        }
        return left;
    }

    /**
     * Unary prefix: -x, !x, &x (address-of), *x (dereference).
     * Right-associative (handled naturally by recursion).
     */
    private AST.Expr parseUnary() {
        if (check(TokenType.MINUS)) {
            consume();
            // Optimise: -literal → negative IntLit rather than a UnaryExpr node
            if (check(TokenType.INT_LITERAL))
                return new AST.IntLit(-Long.parseLong(consume().value));
            return new AST.UnaryExpr("-", parseUnary());
        }
        if (check(TokenType.BANG))  { consume(); return new AST.UnaryExpr("!", parseUnary()); }
        if (check(TokenType.AMP))   { consume(); return new AST.UnaryExpr("&", parseUnary()); }
        if (check(TokenType.STAR))  { consume(); return new AST.UnaryExpr("*", parseUnary()); }
        return parsePostfix();
    }

    /**
     * Postfix: function call foo(args) or array subscript arr[i].
     * Both can be chained: foo(a)[0] (unusual but valid grammar).
     */
    private AST.Expr parsePostfix() {
        AST.Expr expr = parsePrimary();
        while (true) {
            if (check(TokenType.LPAREN)) {
                consume();
                List<AST.Expr> args = new ArrayList<AST.Expr>();
                if (!check(TokenType.RPAREN)) {
                    args.add(parseExpr());
                    while (match(TokenType.COMMA)) args.add(parseExpr());
                }
                expect(TokenType.RPAREN);
                // The callee must be an identifier (direct call only for now)
                String callee = ((AST.IdentExpr) expr).name;
                expr = new AST.CallExpr(callee, args);
            } else if (check(TokenType.LBRACKET)) {
                consume();
                AST.Expr idx = parseExpr();
                expect(TokenType.RBRACKET);
                expr = new AST.IndexExpr(expr, idx);
            } else {
                break;
            }
        }
        return expr;
    }

    /** Lowest level: a literal value, a name, or a parenthesised sub-expression. */
    private AST.Expr parsePrimary() {
        Token t = peek();

        if (t.type == TokenType.IDENTIFIER) {
            consume();
            return new AST.IdentExpr(t.value);
        }
        if (t.type == TokenType.INT_LITERAL) {
            consume();
            return new AST.IntLit(Long.parseLong(t.value));
        }
        if (t.type == TokenType.STRING_LITERAL) {
            consume();
            return new AST.StringLit(t.value);
        }
        if (t.type == TokenType.LPAREN) {
            consume();
            AST.Expr inner = parseExpr();
            expect(TokenType.RPAREN);
            return inner;
        }
        throw new RuntimeException(
            "Unexpected token in expression: " + t + " at line " + t.line);
    }
}
