package com.jcc.ast;

import java.util.List;

public class AST {

    public static abstract class Node {}

    public static class TranslationUnit extends Node {
        public final List<FunctionDef> functions;
        public TranslationUnit(List<FunctionDef> functions) { this.functions = functions; }
    }

    public static class TypeSpec extends Node {
        public final String base;   // "int", "char", "void"
        public final int pointers;  // number of *
        public TypeSpec(String base, int pointers) { this.base = base; this.pointers = pointers; }
        public String toString() {
            StringBuilder sb = new StringBuilder(base);
            for (int i = 0; i < pointers; i++) sb.append('*');
            return sb.toString();
        }
    }

    public static class ParamDecl extends Node {
        public final TypeSpec type;
        public final String name;     // may be null for unnamed params
        public final boolean varargs; // represents ...
        public ParamDecl(TypeSpec type, String name, boolean varargs) {
            this.type = type; this.name = name; this.varargs = varargs;
        }
    }

    public static class FunctionDef extends Node {
        public final TypeSpec returnType;
        public final String name;
        public final List<ParamDecl> params;
        public final CompoundStmt body; // null = forward declaration only
        public FunctionDef(TypeSpec ret, String name, List<ParamDecl> params, CompoundStmt body) {
            this.returnType = ret; this.name = name; this.params = params; this.body = body;
        }
    }

    public static class CompoundStmt extends Node {
        public final List<Stmt> stmts;
        public CompoundStmt(List<Stmt> stmts) { this.stmts = stmts; }
    }

    public static abstract class Stmt extends Node {}

    public static class ReturnStmt extends Stmt {
        public final Expr value; // may be null for bare return
        public ReturnStmt(Expr value) { this.value = value; }
    }

    public static class ExprStmt extends Stmt {
        public final Expr expr;
        public ExprStmt(Expr expr) { this.expr = expr; }
    }

    public static abstract class Expr extends Node {}

    public static class CallExpr extends Expr {
        public final String callee;
        public final List<Expr> args;
        public CallExpr(String callee, List<Expr> args) { this.callee = callee; this.args = args; }
    }

    public static class StringLit extends Expr {
        public final String value; // unescaped (Java string)
        public StringLit(String value) { this.value = value; }
    }

    public static class IntLit extends Expr {
        public final long value;
        public IntLit(long value) { this.value = value; }
    }

    public static class IdentExpr extends Expr {
        public final String name;
        public IdentExpr(String name) { this.name = name; }
    }
}
