# jcc — Java C Compiler, version 4

A minimal K&R C compiler written in Java 1.8, targeting 64-bit x86-64.  
Compiles a growing subset of C to native executables on Windows and Linux (including WSL).

**v4 milestone — cross-platform:**  jcc now auto-detects Windows vs Linux and
selects the correct ABI (Win64: RCX/RDX/R8/R9, 32-byte shadow space;
System V: RDI/RSI/RDX/RCX/R8/R9, no shadow space), NASM output format
(`win64` / `elf64`), and stdlib (`stdio_win.c` / `stdio_linux.c`).

---

## Prerequisites

### Windows

| Tool | Location | Purpose |
|------|----------|---------|
| JDK 8+ | on `PATH` | compile and run jcc itself |
| NASM | `C:\Apps\mingw64\bin\nasm.exe` | assemble generated `.asm` |
| GCC (mingw64) | `C:\Apps\mingw64\bin\gcc.exe` | link `.obj` files into `.exe` |

Add `C:\Apps\mingw64\bin` to your system `PATH`.

### Linux / WSL (Ubuntu)

```sh
sudo apt install default-jdk-headless nasm gcc
```

---

## Building the compiler

Compile the Java sources once, or again after any `.java` edit.

**Windows**
```bat
build.bat
```

**Linux / WSL**
```sh
bash build.sh
```

Output goes to `out/` and is shared by both platforms.

---

## Running the tests

**Windows**
```bat
test.bat
```

**Linux / WSL**
```sh
bash test.sh
```

Expected output:
```
PASS  HelloWorld

Results: 1 passed, 0 failed
```

---

## Compiling a C file

**Windows**
```bat
jcc.bat examples\HelloWorld.c
examples\HelloWorld.exe

:: specify output name
jcc.bat mysource.c -o myapp.exe
myapp.exe
```

**Linux / WSL**
```sh
bash jcc.sh examples/HelloWorld.c
./examples/HelloWorld

# specify output name
bash jcc.sh mysource.c -o myapp
./myapp
```

On the first compile jcc also builds its platform stdlib (`stdio_win.c` or
`stdio_linux.c`) and caches the result; it is rebuilt only when the source
changes.

---

## Compilation pipeline

```
hello.c
  │
  ▼  Preprocessor.java
  │  • Expands #include <stdio.h> to stdlib/include/stdio.h
  │  • Strips all other # directives
  │
  ▼  Lexer.java
  │  • Produces a flat token stream
  │
  ▼  Parser.java
  │  • Recursive-descent → AST (ast/AST.java)
  │
  ▼  CodeGen.java
  │  • Walks AST → x86-64 NASM assembly
  │  • Windows x64 ABI: args in RCX/RDX/R8/R9, 32-byte shadow space
  │  • Two-pass: pre-scans locals for frame layout, then emits code
  │
  ▼  nasm.exe -f win64
  │  hello.asm → hello.obj
  │
  ▼  jcc itself (same pipeline, runs once / when stdio.c changes)
  │  stdlib/src/stdio.c → stdlib/src/stdio.obj   ← SELF-HOSTED
  │
  ▼  gcc.exe (linker only — no longer compiles any C)
     hello.obj + stdio.obj → hello.exe
     GNU ld resolves 'printf' from stdio.obj (strong symbol) before
     it reaches the ucrt import library — UCRT's printf is never used
```

---

## Native stdio implementation

`stdlib/src/stdio.c` is written using only the C features jcc v3 can compile,
and jcc compiles it itself on first use.  It depends only on `kernel32.dll`:

```
GetStdHandle(-11)               →  obtain stdout HANDLE (cached in _stdout)
WriteFile(handle, &byte, 1, …)  →  all output goes through this
```

In v3, `printf` outputs its format string character by character with no
format-specifier expansion.  `"Hello, World!\n"` is sufficient for most
introductory programs; `%d`, `%s` etc. are a future addition once jcc
supports `va_list` or the equivalent.

---

## Supported C features

**Types**
- `int`, `char`, `void`
- Pointer types (`char *`, `int *`, etc.)
- `const`, `unsigned`, `signed`, `static`, `extern`, `long` qualifiers (parsed and silently ignored)

**Declarations**
- Global variable declarations: `char *_stdout;`
- Function definitions with typed parameters
- Function forward declarations (including `...`)
- Local variable declarations inside function bodies

**Statements**
- Expression statements: `foo();`
- Assignment: `x = expr;`
- `return expr;` and bare `return;`
- `if (cond) stmt` and `if (cond) stmt else stmt`
- `while (cond) stmt`
- Compound (block) statements: `{ ... }`

**Expressions**
- Arithmetic: `+`  `-`  `*`  `/`  `%`
- Comparisons: `==`  `!=`  `<`  `>`  `<=`  `>=`
- Unary: `-` (negate)  `!` (logical not)  `&` (address-of)  `*` (dereference)
- Array subscript: `a[i]`
- Function calls (up to 5 arguments, matching the Windows x64 ABI)
- String literals (with `\n \t \r \\ \" \0` escapes)
- Integer literals (including negative via unary `-`)
- Identifier references

**Preprocessor**
- `#include <header>` — expanded from `stdlib/include/` if present, silently skipped otherwise
- All other `#` directives silently dropped

---

## Project structure

```
jcc/
  build.bat / build.sh        compile Java sources → out/
  jcc.bat   / jcc.sh          run the compiler
  test.bat  / test.sh         run the test suite
  examples/
    HelloWorld.c              example programs to compile
  src/com/jcc/
    Main.java                 entry point; orchestrates all stages
    Platform.java             WIN64 / LINUX config (NASM format, ABI, tool paths)
    Preprocessor.java         #include expansion and directive stripping
    Lexer.java                character stream → token stream
    Token.java                token value + source line
    TokenType.java            token type enum
    Parser.java               recursive-descent parser → AST
    CodeGen.java              AST → x86-64 NASM assembly (two-pass, platform-aware)
    ast/
      AST.java                all AST node types (inner static classes)
  stdlib/
    include/stdio.h           C header declaring printf, puts, putchar
    src/stdio_win.c           Windows stdio: kernel32 GetStdHandle + WriteFile
    src/stdio_linux.c         Linux stdio: POSIX write(1, &byte, 1)
  out/                        compiled .class files (generated by build.bat / build.sh)
```

---

## What changed from v2

| | v2 | v3 |
|--|----|----|
| stdio.c compiler | gcc (bootstrap) | **jcc itself (self-hosted)** |
| printf | full format specifiers (%d %s %x …) | literal string output only |
| Local variables | not supported | full stack allocation |
| Arithmetic | not supported | `+ - * / %` |
| Comparisons | not supported | `== != < > <= >=` |
| Control flow | not supported | `if / else` and `while` |
| Global variables | not supported | `.bss` section |
| Unary operators | not supported | `- ! & *` |
| Array indexing | not supported | `a[i]` (byte load, zero-extended) |
| CodeGen | single-pass, no frame | two-pass: pre-scan locals, then emit |
| Function args | up to 4 (registers only) | up to 5 (4 registers + stack slot) |

---

## Known limitations

- No `struct`, `union`, or `typedef`
- No `#define` or macro expansion
- Single translation unit only (no multi-file compilation)
- `printf` does not expand `%d`, `%s` etc. (format string printed literally)
- No `for` loop (use `while`)
- Assignment only to plain identifiers (no `*p = x` or `a[i] = x`)
- Array indexing always loads one byte (correct for `char *`; not yet for `int *`)
