package com.jcc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * A minimal C preprocessor that handles only #include directives.
 *
 * Real C preprocessors do macro expansion (#define), conditional compilation
 * (#ifdef), and much more — none of that is needed for jcc's self-contained
 * stdlib.  This class has one job: replace "#include <header>" lines with the
 * contents of the corresponding file from jcc's own stdlib/include/ directory.
 *
 * System headers not found in stdlib/include/ (like <stdio.h> in user code)
 * are silently skipped — any declarations they provide must come from linking.
 * All other preprocessor directives (#pragma, #define, etc.) are also dropped;
 * they appear only in system headers that jcc doesn't need to parse.
 */
public class Preprocessor {

    private final String src;

    /**
     * Directory that holds jcc's own header files (stdlib/include/).
     * Resolved from the "jcc.home" system property set by jcc.bat,
     * falling back to "." when running tests directly.
     */
    private final String stdlibInclude;

    public Preprocessor(String src) {
        this.src = src;
        String home = System.getProperty("jcc.home", ".");
        this.stdlibInclude = home + "/stdlib/include/";
    }

    /**
     * Process the source text.
     * #include lines for known headers are replaced with the header content.
     * All other # directives are removed.
     * Everything else is passed through unchanged.
     */
    public String process() throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : src.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include")) {
                String header = extractHeader(trimmed);
                if (header != null) {
                    java.nio.file.Path path = Paths.get(stdlibInclude + header);
                    if (Files.exists(path)) {
                        // Inline the header file content at this position
                        out.append(new String(Files.readAllBytes(path))).append('\n');
                    }
                    // Unknown system headers: declarations come from linking; skip silently.
                }
            } else if (trimmed.startsWith("#")) {
                // Drop all other preprocessor directives (#define, #pragma, etc.)
            } else {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    /**
     * Extract the header name from a #include directive.
     * Handles both angle-bracket form (#include <stdio.h>) and
     * quoted form (#include "myheader.h").
     * Returns null if neither form matches.
     */
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
