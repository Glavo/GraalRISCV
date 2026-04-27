#include <stdio.h>
#include <string.h>

int main(void) {
    FILE *out = fopen("/output.txt", "w");
    if (out == NULL) {
        puts("open-write-failed");
        return 1;
    }

    if (fputs("file-data\n", out) < 0 || fclose(out) != 0) {
        puts("write-failed");
        return 2;
    }

    FILE *in = fopen("/output.txt", "r");
    if (in == NULL) {
        puts("open-read-failed");
        return 3;
    }

    char buffer[64];
    if (fgets(buffer, sizeof buffer, in) == NULL) {
        fclose(in);
        puts("read-failed");
        return 4;
    }

    fclose(in);
    printf("%s", buffer);
    return strcmp(buffer, "file-data\n") == 0 ? 0 : 5;
}
