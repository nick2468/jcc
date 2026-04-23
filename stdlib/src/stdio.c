/*
 * jcc stdlib - stdio.c  (v3 - jcc self-compiled)
 *
 * Native implementation of printf / puts / putchar.
 * Uses Windows kernel32.dll (GetStdHandle + WriteFile) only.
 * No dependency on UCRT or any other C runtime.
 *
 * Written using only the subset of C that jcc v3 can compile:
 *   global variables, local variables, if, while, assignment,
 *   arithmetic (+), comparisons (== !=), address-of (&),
 *   array indexing ([]), function calls, integer literals.
 *
 * printf in this version outputs the format string character by
 * character with no format-specifier expansion.  That is enough
 * for HelloWorld.c and any program that only uses string literals.
 *
 * The stdout handle is stored as char* so that the 64-bit pointer
 * value returned by GetStdHandle fits without truncation.
 * Passing -11 for STD_OUTPUT_HANDLE: GetStdHandle((DWORD)-11).
 */

/* ── Global state ─────────────────────────────────────────────────── */

/* Stdout HANDLE (8 bytes on x64); zero until first use. */
char *_stdout;

/* ── Windows kernel32 forward declarations ────────────────────────── */

/*
 * GetStdHandle(-11) returns the stdout HANDLE.
 * We declare the parameter as int; the low 32 bits hold the DWORD value.
 */
char *GetStdHandle(int nHandle);

/*
 * WriteFile: write nBytes bytes from buf into hFile.
 * nWritten receives the actual byte count; lpOverlapped is NULL (0).
 * Declared last param as int to keep the signature jcc-parseable.
 */
int WriteFile(char *hFile, char *buf, int nBytes, int *nWritten, int lpOverlapped);

/* ── Internal helpers ─────────────────────────────────────────────── */

/*
 * Open stdout on first use.  Subsequent calls are no-ops because
 * _stdout is non-zero after the first GetStdHandle call.
 */
void _init_stdout() {
    if (_stdout == 0) {
        _stdout = GetStdHandle(-11);
    }
}

/* ── Public API ───────────────────────────────────────────────────── */

/*
 * putchar: write a single character to stdout.
 * c is stored into a local byte slot; &tmp gives WriteFile a 1-byte buffer.
 * On x64 little-endian the least-significant byte of the qword is the
 * character value, so reading 1 byte from &tmp yields the correct byte.
 */
int putchar(int c) {
    int written;
    char tmp;
    tmp = c;
    _init_stdout();
    WriteFile(_stdout, &tmp, 1, &written, 0);
    return c;
}

/*
 * puts: write a null-terminated string followed by a newline.
 * Iterates until s[i] == 0, calling putchar for each character.
 * ASCII 10 is '\n'.
 */
int puts(char *s) {
    int i;
    i = 0;
    while (s[i] != 0) {
        putchar(s[i]);
        i = i + 1;
    }
    putchar(10);
    return 0;
}

/*
 * printf: output a null-terminated format string character by character.
 * Format specifiers are NOT expanded — the string is printed literally.
 * Sufficient for printf("Hello, World!\n") and similar fixed messages.
 */
int printf(char *fmt) {
    int i;
    i = 0;
    while (fmt[i] != 0) {
        putchar(fmt[i]);
        i = i + 1;
    }
    return 0;
}
