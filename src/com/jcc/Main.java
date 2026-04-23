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
 *   5. NASM          — assemble the .asm file to a platform .obj/.o file
 *
 * For the final executable jcc also compiles stdlib/src/stdio_{win,linux}.c
 * through the same jcc pipeline (self-hosted), then links user.obj + stdio.obj
 * with gcc.  Because stdio.obj defines printf/puts/putchar as strong symbols,
 * the linker picks them up before UCRT (Windows) or glibc stdio (Linux).
 *
 * Usage:
 *   jcc <source.c> [-o output]
 *
 * The -Djcc.home property (set by jcc.bat / jcc.sh) tells the compiler where
 * to find stdlib/include/ headers and the stdlib stdio source.
 *
 * The target platform is auto-detected from os.name.  Override with:
 *   -Djcc.target=win64   force Windows x64 ABI
 *   -Djcc.target=linux   force Linux / System V AMD64 ABI
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: jcc <source.c> [-o output]");
            System.exit(1);
        }

        Platform platform = Platform.detect();
        System.out.println("[jcc] Target: " + platform);

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
        String stdioSrc = jccHome + "/stdlib/src/" + platform.stdioSrcName();
        // Object file name mirrors the source name with the platform extension
        String stdioObjName = platform.stdioSrcName().replaceAll("\\.c$", platform.objExt());
        String stdioObj = jccHome + "/stdlib/src/" + stdioObjName;

        String objFile = outBase + platform.objExt();
        String exeFile = outBase + platform.exeExt();

        // ── Step 1-5: compile the user's source file ──────────────────────
        compileToObj(srcFile, objFile, platform);

        // ── Step 1-5: (re)compile stdlib stdio using jcc itself ───────────
        File stdioSrcFile = new File(stdioSrc);
        File stdioObjFile = new File(stdioObj);
        if (!stdioObjFile.exists() || stdioObjFile.lastModified() < stdioSrcFile.lastModified()) {
            System.out.println("[jcc] Compiling stdlib/" + platform.stdioSrcName() + " (self-hosted)");
            compileToObj(stdioSrc, stdioObj, platform);
        }

        // ── Step 6: link user.obj + stdio.obj → executable ────────────────
        System.out.println("[jcc] Linking (" + platform.gcc() + ")");
        String[] linkFlags = platform.linkFlags();
        String outName = exeFile.isEmpty() ? outBase : exeFile;
        String[] linkCmd = new String[5 + linkFlags.length];
        linkCmd[0] = platform.gcc();
        linkCmd[1] = objFile;
        linkCmd[2] = stdioObj;
        linkCmd[3] = "-o";
        linkCmd[4] = outName;
        System.arraycopy(linkFlags, 0, linkCmd, 5, linkFlags.length);
        run(linkCmd);

        System.out.println("[jcc] Built: " + outName);
    }

    /**
     * Run the full jcc pipeline on srcPath and produce a platform object file at objPath.
     */
    private static void compileToObj(String srcPath, String objPath, Platform platform)
            throws Exception {
        System.out.println("[jcc] Preprocessing  " + srcPath);
        String src          = new String(Files.readAllBytes(Paths.get(srcPath)));
        String preprocessed = new Preprocessor(src).process();

        System.out.println("[jcc] Lexing");
        List<Token> tokens  = new Lexer(preprocessed).tokenize();

        System.out.println("[jcc] Parsing");
        AST.TranslationUnit tu = new Parser(tokens).parse();

        System.out.println("[jcc] Generating assembly");
        String asmSrc = new CodeGen(tu, platform).generate();

        String asmPath = objPath.substring(0, objPath.lastIndexOf('.')) + ".asm";
        Files.write(Paths.get(asmPath), asmSrc.getBytes("UTF-8"));
        System.out.println("[jcc] Wrote " + asmPath);

        System.out.println("[jcc] Assembling (nasm -f " + platform.nasmFormat() + ")");
        run(platform.nasm(), "-f", platform.nasmFormat(), asmPath, "-o", objPath);
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
