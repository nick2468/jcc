/*
 * jcc stdlib - stdio.c
 *
 * This is the seed of jcc's standard I/O implementation.
 *
 * Current state: printf, puts, and putchar are provided by the system
 * CRT (Windows UCRT) via extern linkage.  The linker resolves them from
 * ucrt.dll when gcc is used to link the final executable.
 *
 * Future implementation plan:
 *
 *   putchar(int c)
 *     Write one byte to stdout using WriteFile(GetStdHandle(STD_OUTPUT_HANDLE)).
 *
 *   puts(const char *s)
 *     Walk s calling putchar(); append '\n'.
 *
 *   printf(const char *fmt, ...)
 *     Parse fmt with va_list:
 *       %d  -> itoa, then puts
 *       %s  -> puts
 *       %c  -> putchar
 *       %%  -> putchar('%')
 *     All other characters passed straight to putchar.
 *
 * When jcc can compile this file itself, replace the extern linkage with
 * this implementation and stop linking against the system CRT.
 */
