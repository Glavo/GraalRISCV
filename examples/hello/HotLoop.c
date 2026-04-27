static long syscall3(long number, long argument0, long argument1, long argument2) {
    register long a0 asm("a0") = argument0;
    register long a1 asm("a1") = argument1;
    register long a2 asm("a2") = argument2;
    register long a7 asm("a7") = number;

    asm volatile ("ecall" : "+r"(a0) : "r"(a1), "r"(a2), "r"(a7) : "memory");
    return a0;
}

static void syscall1(long number, long argument0) {
    register long a0 asm("a0") = argument0;
    register long a7 asm("a7") = number;

    asm volatile ("ecall" :: "r"(a0), "r"(a7) : "memory");
    __builtin_unreachable();
}

#ifndef HOT_LOOP_ITERATIONS
#define HOT_LOOP_ITERATIONS 1000000UL
#endif

static volatile unsigned long sink;

void _start(void) {
    static const char hex[] = "0123456789abcdef";
    char message[] = "checksum=0x0000000000000000\n";
    unsigned long value = 0x123456789abcdef0UL;

    for (unsigned long index = 0; index < HOT_LOOP_ITERATIONS; index++) {
        value ^= index + 0x9e3779b97f4a7c15UL;
        value = (value << 13) | (value >> 51);
        value = value * 0xbf58476d1ce4e5b9UL + 0x94d049bb133111ebUL;
        sink = value;
    }

    value = sink;
    for (int index = 0; index < 16; index++) {
        message[11 + index] = hex[(value >> ((15 - index) * 4)) & 0xf];
    }

    (void) syscall3(64, 1, (long) message, sizeof(message) - 1);
    syscall1(93, 0);
}
