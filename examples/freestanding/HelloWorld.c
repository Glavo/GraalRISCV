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

void _start(void) {
    static const char message[] = "Hello World!\n";

    (void) syscall3(64, 1, (long) message, sizeof(message) - 1);
    syscall1(93, 0);
}
