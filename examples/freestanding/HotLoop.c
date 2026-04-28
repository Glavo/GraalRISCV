/*
 * This freestanding example is a small deterministic CPU hot loop. It avoids
 * libc and host file I/O so it can be used as a focused dispatch, arithmetic,
 * register, and memory-store performance probe for the simulator.
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

#ifndef HOT_LOOP_ITERATIONS
#define HOT_LOOP_ITERATIONS 1000000UL
#endif

/* Prevents the compiler from removing the loop while keeping the output stable. */
static volatile unsigned long sink;

/* Program entry point selected by the freestanding linker script. */
void _start(void) {
    static const char hex[] = "0123456789abcdef";
    char message[] = "checksum=0x0000000000000000\n";
    unsigned long value = 0x123456789abcdef0UL;

    /* Mix the loop index through shifts, multiplies, and stores to stress hot instruction dispatch. */
    for (unsigned long index = 0; index < HOT_LOOP_ITERATIONS; index++) {
        value ^= index + 0x9e3779b97f4a7c15UL;
        value = (value << 13) | (value >> 51);
        value = value * 0xbf58476d1ce4e5b9UL + 0x94d049bb133111ebUL;
        sink = value;
    }

    value = sink;
    /* Encode the checksum manually because the example intentionally has no libc. */
    for (int index = 0; index < 16; index++) {
        message[11 + index] = hex[(value >> ((15 - index) * 4)) & 0xf];
    }

    /* SYS_write(1, message, length) followed by SYS_exit(0). */
    (void) syscall3(64, 1, (long) message, sizeof(message) - 1);
    syscall1(93, 0);
}
