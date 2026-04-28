/*
 * This static musl example validates mutating file-system syscalls. It creates
 * a directory and file, truncates and reads the file, then renames and removes
 * both entries through the simulator's host-root sandbox.
 */

#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

int main(void) {
    /* Build a disposable working tree inside the guest root. */
    if (mkdir("/work", 0777) != 0) {
        puts("mkdir-failed");
        return 1;
    }

    int fd = open("/work/data.txt", O_CREAT | O_RDWR | O_TRUNC, 0644);
    if (fd < 0) {
        puts("open-failed");
        return 2;
    }

    /* Verify write, truncate, seek, and read on the same descriptor. */
    if (write(fd, "abcdef\n", 7) != 7) {
        puts("write-failed");
        close(fd);
        return 3;
    }
    if (ftruncate(fd, 4) != 0) {
        puts("ftruncate-failed");
        close(fd);
        return 4;
    }
    if (lseek(fd, 0, SEEK_SET) != 0) {
        puts("lseek-failed");
        close(fd);
        return 5;
    }

    char buffer[8] = {0};
    if (read(fd, buffer, 4) != 4) {
        puts("read-failed");
        close(fd);
        return 6;
    }
    if (strcmp(buffer, "abcd") != 0) {
        puts("content-failed");
        close(fd);
        return 7;
    }
    if (close(fd) != 0) {
        puts("close-failed");
        return 8;
    }

    /* Rename and remove the file to cover directory entry mutation. */
    if (rename("/work/data.txt", "/work/renamed.txt") != 0) {
        puts("rename-failed");
        return 9;
    }
    if (unlink("/work/renamed.txt") != 0) {
        puts("unlink-failed");
        return 10;
    }
    if (rmdir("/work") != 0) {
        puts("rmdir-failed");
        return 11;
    }

    puts("mutations-ok");
    return 0;
}
