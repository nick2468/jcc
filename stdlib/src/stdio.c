/*
 * jcc stdlib - stdio.c
 *
 * Native implementation of printf / puts / putchar.
 * Uses Windows kernel32.dll (GetStdHandle + WriteFile) only.
 * No dependency on UCRT (ucrt.dll) or any other CRT.
 *
 * Format specifiers supported by printf:
 *   %%   literal percent
 *   %c   char  (int promoted)
 *   %s   const char *
 *   %d   signed int   (decimal)
 *   %i   signed int   (decimal, alias for %d)
 *   %u   unsigned int (decimal)
 *   %x   unsigned int (hex, lowercase)
 *   %X   unsigned int (hex, uppercase)
 *   %p   pointer      (hex, lowercase)
 *
 * Future: %ld/%lld, %f/%g, field widths, precision, left-justify.
 * When jcc can compile this file itself the gcc bootstrap step in
 * Main.java can be replaced with a jcc self-hosted compile.
 */

#include <stdarg.h>

/* ── Windows types (no windows.h needed) ────────────────────────────── */
typedef unsigned int   DWORD;
typedef void          *HANDLE;
typedef int            BOOL;
#define NULL           ((void *)0)
#define STD_OUTPUT_HANDLE  ((DWORD)(-11))

extern HANDLE GetStdHandle(DWORD nStdHandle);
extern BOOL   WriteFile(HANDLE hFile, const void *lpBuffer,
                        DWORD nNumberOfBytesToWrite,
                        DWORD *lpNumberOfBytesWritten,
                        void *lpOverlapped);

/* ── stdout handle (lazily initialised) ─────────────────────────────── */
static HANDLE _stdout;

static void _ensure_stdout(void) {
    if (!_stdout)
        _stdout = GetStdHandle(STD_OUTPUT_HANDLE);
}

/* ── primitive output ────────────────────────────────────────────────── */
static void _write_buf(const char *buf, DWORD len) {
    DWORD written;
    _ensure_stdout();
    WriteFile(_stdout, buf, len, &written, NULL);
}

static void _write_char(char c) {
    _write_buf(&c, 1);
}

/* ── integer → decimal ───────────────────────────────────────────────── */
static void _write_int(long long n) {
    char buf[20];
    int  i = 0;
    if (n < 0) { _write_char('-'); n = -n; }
    if (n == 0) { _write_char('0'); return; }
    while (n > 0) { buf[i++] = (char)('0' + n % 10); n /= 10; }
    while (i > 0) _write_char(buf[--i]);
}

static void _write_uint(unsigned long long n) {
    char buf[20];
    int  i = 0;
    if (n == 0) { _write_char('0'); return; }
    while (n > 0) { buf[i++] = (char)('0' + n % 10); n /= 10; }
    while (i > 0) _write_char(buf[--i]);
}

/* ── integer → hex ───────────────────────────────────────────────────── */
static void _write_hex(unsigned long long n, int upper) {
    static const char lo[] = "0123456789abcdef";
    static const char up[] = "0123456789ABCDEF";
    const char *digits = upper ? up : lo;
    char buf[16];
    int  i = 0;
    if (n == 0) { _write_char('0'); return; }
    while (n > 0) { buf[i++] = digits[n & 0xF]; n >>= 4; }
    while (i > 0) _write_char(buf[--i]);
}

/* ── printf ──────────────────────────────────────────────────────────── */
int printf(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    int count = 0;

    while (*fmt) {
        if (*fmt != '%') {
            _write_char(*fmt++);
            ++count;
            continue;
        }
        ++fmt; /* skip '%' */

        switch (*fmt++) {
            case '%':
                _write_char('%'); ++count;
                break;

            case 'c': {
                char c = (char)va_arg(args, int);
                _write_char(c); ++count;
                break;
            }

            case 's': {
                const char *s = va_arg(args, const char *);
                if (!s) s = "(null)";
                while (*s) { _write_char(*s++); ++count; }
                break;
            }

            case 'd':
            case 'i':
                _write_int((long long)va_arg(args, int));
                break;

            case 'u':
                _write_uint((unsigned long long)va_arg(args, unsigned int));
                break;

            case 'x':
                _write_hex((unsigned long long)va_arg(args, unsigned int), 0);
                break;

            case 'X':
                _write_hex((unsigned long long)va_arg(args, unsigned int), 1);
                break;

            case 'p':
                _write_hex((unsigned long long)va_arg(args, void *), 0);
                break;

            default:
                /* Unknown specifier: emit literally */
                _write_char('%');
                _write_char(*(fmt - 1));
                break;
        }
    }

    va_end(args);
    return count;
}

/* ── puts ────────────────────────────────────────────────────────────── */
int puts(const char *s) {
    DWORD len = 0;
    while (s[len]) ++len;
    _write_buf(s, len);
    _write_char('\n');
    return 0;
}

/* ── putchar ─────────────────────────────────────────────────────────── */
int putchar(int c) {
    _write_char((char)c);
    return c;
}
