/*
 * jcc stdlib - stdio_win.c  (v4 - Windows target)
 *
 * Native implementation of printf / puts / putchar.
 * Uses Windows kernel32.dll (GetStdHandle + WriteFile) only.
 * No dependency on UCRT or any other C runtime.
 *
 * Written using only the subset of C that jcc can compile:
 *   global variables, local variables, if, while, assignment,
 *   arithmetic (+), comparisons (== !=), address-of (&),
 *   array indexing ([]), function calls, integer literals.
 *
 * printf outputs the format string character by character with no
 * format-specifier expansion.
 *
 * The stdout handle is stored as char* so that the 64-bit pointer
 * value returned by GetStdHandle fits without truncation.
 * Passing -11 for STD_OUTPUT_HANDLE: GetStdHandle((DWORD)-11).
 */

/* ── Global state ─────────────────────────────────────────────────── */

char *_stdout;

/* ── Windows kernel32 forward declarations ────────────────────────── */

char *GetStdHandle(int nHandle);

int WriteFile(char *hFile, char *buf, int nBytes, int *nWritten, int lpOverlapped);

/* ── Internal helpers ─────────────────────────────────────────────── */

void _init_stdout() {
    if (_stdout == 0) {
        _stdout = GetStdHandle(-11);
    }
}

/* ── Public API ───────────────────────────────────────────────────── */

int putchar(int c) {
    int written;
    char tmp;
    tmp = c;
    _init_stdout();
    WriteFile(_stdout, &tmp, 1, &written, 0);
    return c;
}

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

int printf(char *fmt) {
    int i;
    i = 0;
    while (fmt[i] != 0) {
        putchar(fmt[i]);
        i = i + 1;
    }
    return 0;
}
