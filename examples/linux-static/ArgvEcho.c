/*
 * This static musl example validates argv construction on the initial guest
 * stack. The Gradle test passes two arguments and checks that libc sees the
 * expected argc and argv strings.
 */

#include <stdio.h>

int main(int argc, char **argv) {
    /* Print the argument vector so the host-side test can verify stack setup. */
    printf("argc=%d\n", argc);
    if (argc > 1) {
        printf("argv1=%s\n", argv[1]);
    }
    if (argc > 2) {
        printf("argv2=%s\n", argv[2]);
    }

    /* Require exactly the expected program name plus two user arguments. */
    return argc == 3 ? 0 : 1;
}
