/*
 * This static musl example validates that the simulator can run a normal C
 * program with a libc entry point and formatted stdout output. It is the Linux
 * user-mode counterpart to the freestanding Hello World example.
 */

#include <stdio.h>

int main(void) {
    /* Exercise musl startup, stdio buffering, write syscall forwarding, and exit status handling. */
    printf("Hello World!\n");
    return 0;
}
