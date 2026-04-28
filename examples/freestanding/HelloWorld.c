/*
 * This freestanding example validates the smallest useful RISC-V user program
 * supported by the simulator. It does not use libc or a C runtime entry point;
 * it enters at _start, performs raw Linux write and exit syscalls, and verifies
 * ELF loading, guest memory reads, and basic ecall dispatch.
 */

/* Issues a three-argument Linux syscall using the RISC-V calling convention. */
static long syscall3(long number, long argument0, long argument1, long argument2) {
    register long a0 asm("a0") = argument0;
    register long a1 asm("a1") = argument1;
    register long a2 asm("a2") = argument2;
    register long a7 asm("a7") = number;

    asm volatile ("ecall" : "+r"(a0) : "r"(a1), "r"(a2), "r"(a7) : "memory");
    return a0;
}

/* Issues a one-argument syscall that does not return, currently used for exit. */
static void syscall1(long number, long argument0) {
    register long a0 asm("a0") = argument0;
    register long a7 asm("a7") = number;

    asm volatile ("ecall" :: "r"(a0), "r"(a7) : "memory");
    __builtin_unreachable();
}

/* Program entry point selected by the freestanding linker script. */
void _start(void) {
    static const char message[] = "Hello World!\n";

    /* SYS_write(1, message, length) followed by SYS_exit(0). */
    (void) syscall3(64, 1, (long) message, sizeof(message) - 1);
    syscall1(93, 0);
}
