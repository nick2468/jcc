package com.jcc.ast;

import java.util.List;

/**
 * All Abstract Syntax Tree node types for jcc.
 *
 * Every node is a static inner class so the whole tree lives in one file.
 * The compiler pipeline is: source → Lexer → Parser (builds this AST)
 * → CodeGen (walks this AST to emit NASM assembly).
 *
 * Nodes are plain data holders — no behaviour, no visitor machinery.
 * The code generator uses instanceof checks to dispatch on node type.
 */
public class AST {

    // ── Base types ────────────────────────────────────────────────────────

    /** Root of every node hierarchy. No fields — just a common type. */
    public static abstract class Node {}

    /** A statement: something that stands alone on a line and has an effect. */
    public static abstract class Stmt extends Node {}

    /** An expression: something that produces a value. */
    public static abstract class Expr extends Node {}

    // ── Top-level ─────────────────────────────────────────────────────────

    /**
     * The whole parsed file.
     * globals  — file-scope variable declarations (e.g. "int _stdout;")
     * functions — function definitions AND forward declarations
     */
    public static class TranslationUnit extends Node {
        public final List<GlobalVarDecl> globals;
        public final List<FunctionDef>   functions;
        public TranslationUnit(List<GlobalVarDecl> globals, List<FunctionDef> functions) {
            this.globals = globals; this.functions = functions;
        }
    }

    // ── Type representation ───────────────────────────────────────────────

    /**
     * A C type such as "int", "char *", or "void **".
     * base     — the primitive: "int", "char", or "void"
     * pointers — number of * suffixes
     */
    public static class TypeSpec extends Node {
        public final String base;
        public final int    pointers;
        public TypeSpec(String base, int pointers) { this.base = base; this.pointers = pointers; }
        public String toString() {
            StringBuilder sb = new StringBuilder(base);
            for (int i = 0; i < pointers; i++) sb.append('*');
            return sb.toString();
        }
    }

    // ── Declarations ──────────────────────────────────────────────────────

    /**
     * A file-scope variable: "int x;" or "char *p;".
     * Emitted into the .bss section (zero-initialised).
     */
    public static class GlobalVarDecl extends Node {
        public final TypeSpec type;
        public final String   name;
        public GlobalVarDecl(TypeSpec type, String name) { this.type = type; this.name = name; }
    }

    /**
     * A function parameter in a definition or forward declaration.
     * varargs=true represents the "..." parameter.
     */
    public static class ParamDecl extends Node {
        public final TypeSpec type;
        public final String   name;    // may be null for unnamed params
        public final boolean  varargs;
        public ParamDecl(TypeSpec type, String name, boolean varargs) {
            this.type = type; this.name = name; this.varargs = varargs;
        }
    }

    /**
     * A function definition or forward declaration.
     * body == null means it is a declaration only (e.g. "extern int printf(...);" ).
     * CodeGen skips declarations; they appear as "extern" in the assembly.
     */
    public static class FunctionDef extends Node {
        public final TypeSpec        returnType;
        public final String          name;
        public final List<ParamDecl> params;
        public final CompoundStmt    body;   // null = forward declaration
        public FunctionDef(TypeSpec ret, String name, List<ParamDecl> params, CompoundStmt body) {
            this.returnType = ret; this.name = name; this.params = params; this.body = body;
        }
    }

    // ── Statements ────────────────────────────────────────────────────────

    /** A block: "{ stmt1; stmt2; ... }" */
    public static class CompoundStmt extends Stmt {
        public final List<Stmt> stmts;
        public CompoundStmt(List<Stmt> stmts) { this.stmts = stmts; }
    }

    /** Local variable declaration: "int i;" or "char tmp;" inside a function. */
    public static class DeclStmt extends Stmt {
        public final TypeSpec type;
        public final String   name;
        public DeclStmt(TypeSpec type, String name) { this.type = type; this.name = name; }
    }

    /**
     * Simple variable assignment: "x = expr;"
     * lhs is the variable name (plain identifier — no array element assignment yet).
     */
    public static class AssignStmt extends Stmt {
        public final String lhs;   // variable name being assigned to
        public final Expr   rhs;
        public AssignStmt(String lhs, Expr rhs) { this.lhs = lhs; this.rhs = rhs; }
    }

    /** return; or return expr; */
    public static class ReturnStmt extends Stmt {
        public final Expr value;   // null for bare return
        public ReturnStmt(Expr value) { this.value = value; }
    }

    /** An expression used as a statement, e.g. a function call: "foo(x);" */
    public static class ExprStmt extends Stmt {
        public final Expr expr;
        public ExprStmt(Expr expr) { this.expr = expr; }
    }

    /** if (cond) thenBranch   or   if (cond) thenBranch else elseBranch */
    public static class IfStmt extends Stmt {
        public final Expr cond;
        public final Stmt thenBranch;
        public final Stmt elseBranch;  // null if no else
        public IfStmt(Expr cond, Stmt then, Stmt else_) {
            this.cond = cond; this.thenBranch = then; this.elseBranch = else_;
        }
    }

    /** while (cond) body */
    public static class WhileStmt extends Stmt {
        public final Expr cond;
        public final Stmt body;
        public WhileStmt(Expr cond, Stmt body) { this.cond = cond; this.body = body; }
    }

    // ── Expressions ───────────────────────────────────────────────────────

    /** Function call: name(arg0, arg1, ...) */
    public static class CallExpr extends Expr {
        public final String     callee;
        public final List<Expr> args;
        public CallExpr(String callee, List<Expr> args) { this.callee = callee; this.args = args; }
    }

    /**
     * Binary expression: left op right.
     * op is one of: + - * / % == != < > <= >=
     */
    public static class BinaryExpr extends Expr {
        public final String op;
        public final Expr   left;
        public final Expr   right;
        public BinaryExpr(String op, Expr left, Expr right) {
            this.op = op; this.left = left; this.right = right;
        }
    }

    /**
     * Unary expression: op operand.
     * op is one of: - (negate)  ! (logical not)  & (address-of)  * (dereference)
     */
    public static class UnaryExpr extends Expr {
        public final String op;
        public final Expr   operand;
        public UnaryExpr(String op, Expr operand) { this.op = op; this.operand = operand; }
    }

    /** Array subscript: array[index]  (equivalent to *(array+index)) */
    public static class IndexExpr extends Expr {
        public final Expr array;
        public final Expr index;
        public IndexExpr(Expr array, Expr index) { this.array = array; this.index = index; }
    }

    /** A string literal value: "hello\n".  Value is already unescaped. */
    public static class StringLit extends Expr {
        public final String value;
        public StringLit(String value) { this.value = value; }
    }

    /** An integer literal value: 0, 42, -1. */
    public static class IntLit extends Expr {
        public final long value;
        public IntLit(long value) { this.value = value; }
    }

    /** A bare identifier used as a value: variable read or function name. */
    public static class IdentExpr extends Expr {
        public final String name;
        public IdentExpr(String name) { this.name = name; }
    }
}
