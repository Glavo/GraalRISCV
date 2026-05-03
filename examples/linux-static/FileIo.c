/*
 * This static musl example validates basic mounted file I/O through libc. It
 * creates a file, writes data, reopens the same path for reading, and confirms
 * the simulator's mounted host path mapping preserves the contents.
 */

#include <stdio.h>
#include <string.h>

int main(void) {
    /* Create a file at the guest root, which the test maps to a temporary host directory. */
    FILE *out = fopen("/output.txt", "w");
    if (out == NULL) {
        puts("open-write-failed");
        return 1;
    }

    /* Close after writing so the read path observes flushed data. */
    if (fputs("file-data\n", out) < 0 || fclose(out) != 0) {
        puts("write-failed");
        return 2;
    }

    /* Reopen the file through libc to exercise openat/read/close style syscalls. */
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

    /* The exit status lets the Gradle test distinguish stdout mismatch from syscall failure. */
    return strcmp(buffer, "file-data\n") == 0 ? 0 : 5;
}
