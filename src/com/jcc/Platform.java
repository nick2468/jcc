package com.jcc;

/**
 * Target platform for a jcc compilation.
 *
 * Auto-detected from os.name; overridden with -Djcc.target=win64|linux.
 */
public enum Platform {
    WIN64, LINUX;

    public static Platform detect() {
        String override = System.getProperty("jcc.target", "").toLowerCase();
        if (override.equals("linux")) return LINUX;
        if (override.equals("win64")) return WIN64;
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("windows") ? WIN64 : LINUX;
    }

    /** NASM output format flag value. */
    public String nasmFormat() { return this == WIN64 ? "win64" : "elf64"; }

    /** Object file extension, including the dot. */
    public String objExt()     { return this == WIN64 ? ".obj" : ".o"; }

    /** Executable file extension (empty string on Linux). */
    public String exeExt()     { return this == WIN64 ? ".exe" : ""; }

    /** Full path or name of the NASM assembler. */
    public String nasm() {
        return this == WIN64 ? "C:\\Apps\\mingw64\\bin\\nasm.exe" : "nasm";
    }

    /** Full path or name of the GCC linker driver. */
    public String gcc() {
        return this == WIN64 ? "C:\\Apps\\mingw64\\bin\\gcc.exe" : "gcc";
    }

    /** Filename (no directory) of the platform-specific stdio source. */
    public String stdioSrcName() {
        return this == WIN64 ? "stdio_win.c" : "stdio_linux.c";
    }

    /**
     * Integer/pointer argument registers in order.
     * Win64:   RCX RDX R8  R9              (4 register args, 32-byte shadow space)
     * Linux:   RDI RSI RDX RCX R8 R9       (6 register args, no shadow space)
     */
    public String[] argRegs() {
        return this == WIN64
            ? new String[]{ "rcx", "rdx", "r8", "r9" }
            : new String[]{ "rdi", "rsi", "rdx", "rcx", "r8", "r9" };
    }

    /**
     * Bytes of shadow space the caller must reserve below RSP before each CALL.
     * Win64: 32 bytes.  Linux: 0 bytes.
     */
    public int shadowSpace() { return this == WIN64 ? 32 : 0; }

    /**
     * Extra flags to append to the gcc link command.
     * Linux: -no-pie, because NASM emits R_X86_64_PC32 CALL relocations which
     * are incompatible with the PIE mode that modern GCC defaults to.
     */
    public String[] linkFlags() {
        return this == WIN64 ? new String[0] : new String[]{ "-no-pie" };
    }
}
