package com.jcc;

import com.jcc.ast.AST;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    private static final String MINGW = "C:\\Apps\\mingw64\\bin\\";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: jcc <source.c> [-o output]");
            System.exit(1);
        }

        String srcFile = args[0];
        String outBase = srcFile.replaceAll("(?i)\\.c$", "");
        for (int i = 1; i < args.length - 1; i++) {
            if ("-o".equals(args[i])) {
                outBase = args[i + 1].replaceAll("(?i)\\.exe$", "");
                i++;
            }
        }

        String jccHome   = System.getProperty("jcc.home", ".");
        String stdioSrc  = jccHome + "/stdlib/src/stdio.c";
        String stdioObj  = jccHome + "/stdlib/src/stdio.obj";

        String src = new String(Files.readAllBytes(Paths.get(srcFile)));

        System.out.println("[jcc] Preprocessing " + srcFile);
        String preprocessed = new Preprocessor(src).process();

        System.out.println("[jcc] Lexing");
        List<Token> tokens = new Lexer(preprocessed).tokenize();

        System.out.println("[jcc] Parsing");
        AST.TranslationUnit tu = new Parser(tokens).parse();

        System.out.println("[jcc] Generating assembly");
        String asmSrc = new CodeGen(tu).generate();

        String asmFile = outBase + ".asm";
        String objFile = outBase + ".obj";
        String exeFile = outBase + ".exe";

        Files.write(Paths.get(asmFile), asmSrc.getBytes("UTF-8"));
        System.out.println("[jcc] Wrote " + asmFile);

        System.out.println("[jcc] Assembling (nasm)");
        run(MINGW + "nasm.exe", "-f", "win64", asmFile, "-o", objFile);

        // Compile stdlib/stdio.c if the .obj is missing or stale.
        File stdioSrcFile = new File(stdioSrc);
        File stdioObjFile = new File(stdioObj);
        if (!stdioObjFile.exists() || stdioObjFile.lastModified() < stdioSrcFile.lastModified()) {
            System.out.println("[jcc] Compiling stdlib stdio.c (bootstrap via gcc)");
            run(MINGW + "gcc.exe", "-c", stdioSrc, "-o", stdioObj);
        }

        // Link: user obj + our stdio obj.
        // GNU ld resolves 'printf' from stdio.obj (strong symbol) before it
        // reaches the ucrt import library, so UCRT's printf is never used.
        System.out.println("[jcc] Linking (gcc)");
        run(MINGW + "gcc.exe", objFile, stdioObj, "-o", exeFile);

        System.out.println("[jcc] Built: " + exeFile);
    }

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
