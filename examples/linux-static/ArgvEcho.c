#include <stdio.h>

int main(int argc, char **argv) {
    printf("argc=%d\n", argc);
    if (argc > 1) {
        printf("argv1=%s\n", argv[1]);
    }
    if (argc > 2) {
        printf("argv2=%s\n", argv[2]);
    }
    return argc == 3 ? 0 : 1;
}
