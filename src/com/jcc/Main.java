package com.jcc;

import com.jcc.ast.AST;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Entry point for the jcc compiler.
 *
 * Compile pipeline for any .c file:
 *   1. Preprocessor  — expand #include directives from jcc's own stdlib/include/
 *   2. Lexer         — convert source text to a flat list of tokens
 *   3. Parser        — build an Abstract Syntax Tree from the token list
 *   4. CodeGen       — walk the AST and emit x86-64 NASM assembly
 *   5. NASM          — assemble the .asm file to a Win64 .obj file
 *
 * For the final executable jcc also compiles stdlib/src/stdio.c through the
 * same jcc pipeline (self-hosted), then links user.obj + stdio.obj with gcc.
 * Because stdio.obj defines printf/puts/putchar as strong symbols, GNU ld
 * picks them up before it ever reaches UCRT, so no UCRT dependency exists.
 *
 * Usage:
 *   jcc <source.c> [-o output]
 *
 * The -Djcc.home property (set by jcc.bat) tells the compiler where to find
 * stdlib/include/ headers and stdlib/src/stdio.c.
 */
public class Main {

    /** Path to the MinGW-w64 bin directory.  NASM lives here too. */
    private static final String MINGW = "C:\\Apps\\mingw64\\bin\\";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: jcc <source.c> [-o output]");
            System.exit(1);
        }

        // Determine output base name (strip .c; override with -o if given)
        String srcFile = args[0];
        String outBase = srcFile.replaceAll("(?i)\\.c$", "");
        for (int i = 1; i < args.length - 1; i++) {
            if ("-o".equals(args[i])) {
                outBase = args[i + 1].replaceAll("(?i)\\.exe$", "");
                i++;
            }
        }

        String jccHome  = System.getProperty("jcc.home", ".");
        String stdioSrc = jccHome + "/stdlib/src/stdio.c";
        String stdioObj = jccHome + "/stdlib/src/stdio.obj";

        String objFile = outBase + ".obj";
        String exeFile = outBase + ".exe";

        // ── Step 1-5: compile the user's source file ──────────────────────
        compileToObj(srcFile, objFile);

        // ── Step 1-5: (re)compile stdlib/stdio.c using jcc itself ─────────
        //
        // This is the self-hosting step: jcc compiles its own standard library
        // instead of falling back to gcc.  We only recompile when stdio.c is
        // newer than the cached stdio.obj (same incremental check as make).
        File stdioSrcFile = new File(stdioSrc);
        File stdioObjFile = new File(stdioObj);
        if (!stdioObjFile.exists() || stdioObjFile.lastModified() < stdioSrcFile.lastModified()) {
            System.out.println("[jcc] Compiling stdlib/stdio.c (self-hosted)");
            compileToObj(stdioSrc, stdioObj);
        }

        // ── Step 6: link user.obj + stdio.obj → executable ────────────────
        //
        // gcc is used purely as a linker driver here: it adds the Windows
        // startup code (crt0) and default libraries (kernel32 etc.) so we get
        // a runnable .exe without writing a linker script ourselves.
        System.out.println("[jcc] Linking (gcc)");
        run(MINGW + "gcc.exe", objFile, stdioObj, "-o", exeFile);

        System.out.println("[jcc] Built: " + exeFile);
    }

    /**
     * Run the full jcc pipeline on srcPath and produce a Win64 .obj file at objPath.
     *
     * This method is called for both the user's source file and stdlib/stdio.c,
     * making stdio.c a self-hosted compile rather than a gcc bootstrap.
     */
    private static void compileToObj(String srcPath, String objPath) throws Exception {
        System.out.println("[jcc] Preprocessing  " + srcPath);
        String src          = new String(Files.readAllBytes(Paths.get(srcPath)));
        String preprocessed = new Preprocessor(src).process();

        System.out.println("[jcc] Lexing");
        List<Token> tokens  = new Lexer(preprocessed).tokenize();

        System.out.println("[jcc] Parsing");
        AST.TranslationUnit tu = new Parser(tokens).parse();

        System.out.println("[jcc] Generating assembly");
        String asmSrc = new CodeGen(tu).generate();

        // Write the .asm file alongside the .obj (useful for debugging)
        String asmPath = objPath.replaceAll("(?i)\\.obj$", ".asm");
        Files.write(Paths.get(asmPath), asmSrc.getBytes("UTF-8"));
        System.out.println("[jcc] Wrote " + asmPath);

        System.out.println("[jcc] Assembling (nasm)");
        run(MINGW + "nasm.exe", "-f", "win64", asmPath, "-o", objPath);
    }

    /**
     * Run an external command, inheriting stdin/stdout/stderr.
     * Exits the JVM with the command's exit code if it fails.
     */
    private static void run(String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        int code = pb.start().waitFor();
        if (code != 0) {
            System.err.println("[jcc] Command failed (exit " + code + "): "
                               + String.join(" ", cmd));
            System.exit(code);
        }
    }
}
