package com.jcc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Preprocessor {
    private final String src;
    private final String stdlibInclude;

    public Preprocessor(String src) {
        this.src = src;
        String home = System.getProperty("jcc.home", ".");
        this.stdlibInclude = home + "/stdlib/include/";
    }

    public String process() throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : src.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include")) {
                String header = extractHeader(trimmed);
                if (header != null) {
                    java.nio.file.Path path = Paths.get(stdlibInclude + header);
                    if (Files.exists(path)) {
                        out.append(new String(Files.readAllBytes(path))).append('\n');
                    }
                    // Unknown system headers: declarations come from linking; skip silently.
                }
            } else if (trimmed.startsWith("#")) {
                // Skip all other preprocessor directives for now.
            } else {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private String extractHeader(String line) {
        int lt = line.indexOf('<');
        int gt = line.indexOf('>');
        int q1 = line.indexOf('"');
        int q2 = line.lastIndexOf('"');
        if (lt >= 0 && gt > lt) return line.substring(lt + 1, gt);
        if (q1 >= 0 && q2 > q1) return line.substring(q1 + 1, q2);
        return null;
    }
}
