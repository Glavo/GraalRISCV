#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>

int main(void) {
    int fd = open("/positioned.txt", O_CREAT | O_RDWR | O_TRUNC, 0644);
    if (fd < 0) {
        puts("open-failed");
        return 1;
    }
    if (write(fd, "0123456789", 10) != 10) {
        puts("write-failed");
        close(fd);
        return 2;
    }
    if (lseek(fd, 2, SEEK_SET) != 2) {
        puts("initial-lseek-failed");
        close(fd);
        return 3;
    }

    char buffer[16] = {0};
    if (pread(fd, buffer, 4, 5) != 4 || memcmp(buffer, "5678", 4) != 0) {
        puts("pread-failed");
        close(fd);
        return 4;
    }
    if (lseek(fd, 0, SEEK_CUR) != 2) {
        puts("pread-offset-failed");
        close(fd);
        return 5;
    }
    if (pwrite(fd, "AB", 2, 4) != 2) {
        puts("pwrite-failed");
        close(fd);
        return 6;
    }
    if (lseek(fd, 0, SEEK_CUR) != 2) {
        puts("pwrite-offset-failed");
        close(fd);
        return 7;
    }
    if (fdatasync(fd) != 0) {
        puts("fdatasync-failed");
        close(fd);
        return 8;
    }
    if (fsync(fd) != 0) {
        puts("fsync-failed");
        close(fd);
        return 9;
    }
#ifdef SYS_syncfs
    if (syscall(SYS_syncfs, fd) != 0) {
        puts("syncfs-failed");
        close(fd);
        return 10;
    }
#endif
    sync();

    if (lseek(fd, 0, SEEK_SET) != 0) {
        puts("final-lseek-failed");
        close(fd);
        return 11;
    }
    memset(buffer, 0, sizeof(buffer));
    if (read(fd, buffer, 10) != 10 || memcmp(buffer, "0123AB6789", 10) != 0) {
        puts("content-failed");
        close(fd);
        return 12;
    }
    if (close(fd) != 0) {
        puts("close-failed");
        return 13;
    }

    puts("positioned-io-ok");
    return 0;
}
