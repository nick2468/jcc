package com.jcc;

import com.jcc.ast.AST;
import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    private Token peek()                  { return tokens.get(pos); }
    private Token consume()               { return tokens.get(pos++); }
    private boolean check(TokenType t)    { return peek().type == t; }
    private boolean checkIdent(String v)  { return check(TokenType.IDENTIFIER) && peek().value.equals(v); }

    private Token expect(TokenType type) {
        Token t = consume();
        if (t.type != type)
            throw new RuntimeException("Expected " + type + " but got " + t + " at line " + t.line);
        return t;
    }

    private boolean match(TokenType type) {
        if (check(type)) { consume(); return true; }
        return false;
    }

    private Token tokenAt(int i) {
        return tokens.get(Math.min(i, tokens.size() - 1));
    }

    // -------------------------------------------------------------------------
    // Top-level
    // -------------------------------------------------------------------------

    public AST.TranslationUnit parse() {
        List<AST.FunctionDef> functions = new ArrayList<AST.FunctionDef>();
        while (!check(TokenType.EOF)) {
            functions.add(parseFunctionDefOrDecl());
        }
        return new AST.TranslationUnit(functions);
    }

    // -------------------------------------------------------------------------
    // Types
    // -------------------------------------------------------------------------

    private boolean isTypeStart() {
        TokenType t = peek().type;
        return t == TokenType.KW_INT  ||
               t == TokenType.KW_VOID ||
               t == TokenType.KW_CHAR ||
               checkIdent("const")    ||
               checkIdent("unsigned") ||
               checkIdent("signed");
    }

    private AST.TypeSpec parseTypeSpec() {
        // Consume qualifiers
        while (checkIdent("const") || checkIdent("unsigned") || checkIdent("signed"))
            consume();

        String base;
        if      (check(TokenType.KW_INT))  { consume(); base = "int"; }
        else if (check(TokenType.KW_VOID)) { consume(); base = "void"; }
        else if (check(TokenType.KW_CHAR)) { consume(); base = "char"; }
        else throw new RuntimeException("Expected type specifier at line " + peek().line + ", got " + peek());

        int ptrs = 0;
        while (match(TokenType.STAR)) ptrs++;
        return new AST.TypeSpec(base, ptrs);
    }

    // -------------------------------------------------------------------------
    // Function definition / declaration
    // -------------------------------------------------------------------------

    private AST.FunctionDef parseFunctionDefOrDecl() {
        AST.TypeSpec ret  = parseTypeSpec();
        String name       = expect(TokenType.IDENTIFIER).value;
        expect(TokenType.LPAREN);
        List<AST.ParamDecl> params = parseParamList();
        expect(TokenType.RPAREN);

        if (match(TokenType.SEMI)) {
            return new AST.FunctionDef(ret, name, params, null); // declaration only
        }

        AST.CompoundStmt body = parseCompound();
        return new AST.FunctionDef(ret, name, params, body);
    }

    private List<AST.ParamDecl> parseParamList() {
        List<AST.ParamDecl> params = new ArrayList<AST.ParamDecl>();
        if (check(TokenType.RPAREN)) return params;

        // void-only parameter list: f(void)
        if (check(TokenType.KW_VOID) && tokenAt(pos + 1).type == TokenType.RPAREN) {
            consume();
            return params;
        }

        while (true) {
            if (check(TokenType.ELLIPSIS)) {
                consume();
                params.add(new AST.ParamDecl(null, null, true));
                break;
            }
            AST.TypeSpec type = parseTypeSpec();
            String paramName  = check(TokenType.IDENTIFIER) ? consume().value : null;
            params.add(new AST.ParamDecl(type, paramName, false));
            if (!match(TokenType.COMMA)) break;
        }
        return params;
    }

    // -------------------------------------------------------------------------
    // Statements
    // -------------------------------------------------------------------------

    private AST.CompoundStmt parseCompound() {
        expect(TokenType.LBRACE);
        List<AST.Stmt> stmts = new ArrayList<AST.Stmt>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF))
            stmts.add(parseStmt());
        expect(TokenType.RBRACE);
        return new AST.CompoundStmt(stmts);
    }

    private AST.Stmt parseStmt() {
        if (check(TokenType.KW_RETURN)) {
            consume();
            AST.Expr val = check(TokenType.SEMI) ? null : parseExpr();
            expect(TokenType.SEMI);
            return new AST.ReturnStmt(val);
        }
        AST.Expr expr = parseExpr();
        expect(TokenType.SEMI);
        return new AST.ExprStmt(expr);
    }

    // -------------------------------------------------------------------------
    // Expressions
    // -------------------------------------------------------------------------

    private AST.Expr parseExpr() {
        Token t = peek();

        if (t.type == TokenType.IDENTIFIER) {
            consume();
            if (match(TokenType.LPAREN)) {
                List<AST.Expr> args = new ArrayList<AST.Expr>();
                if (!check(TokenType.RPAREN)) {
                    args.add(parseExpr());
                    while (match(TokenType.COMMA)) args.add(parseExpr());
                }
                expect(TokenType.RPAREN);
                return new AST.CallExpr(t.value, args);
            }
            return new AST.IdentExpr(t.value);
        }

        if (t.type == TokenType.STRING_LITERAL) {
            consume();
            return new AST.StringLit(t.value);
        }

        if (t.type == TokenType.INT_LITERAL) {
            consume();
            return new AST.IntLit(Long.parseLong(t.value));
        }

        throw new RuntimeException("Unexpected token in expression: " + t + " at line " + t.line);
    }
}
