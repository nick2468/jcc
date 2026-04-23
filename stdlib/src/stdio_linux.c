/*
 * jcc stdlib - stdio_linux.c  (v4 - Linux / WSL target)
 *
 * Native implementation of printf / puts / putchar.
 * Uses POSIX write(2): write(1, &c, 1) to stdout (file descriptor 1).
 * No dependency on glibc stdio — only the write syscall via libc.
 *
 * Written using only the subset of C that jcc can compile:
 *   local variables, if, while, assignment, arithmetic (+),
 *   comparisons (!= 0), address-of (&), array indexing ([]),
 *   function calls, integer literals.
 */

/* ── POSIX forward declaration ────────────────────────────────────── */

/* write(fd, buf, n): writes n bytes from buf to fd; returns bytes written */
int write(int fd, char *buf, int n);

/* ── Public API ───────────────────────────────────────────────────── */

int putchar(int c) {
    char tmp;
    tmp = c;
    write(1, &tmp, 1);
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
