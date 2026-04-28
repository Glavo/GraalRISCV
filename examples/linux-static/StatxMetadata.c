#include <asm/unistd.h>
#include <fcntl.h>
#include <linux/stat.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#ifndef SYS_statx
#define SYS_statx __NR_statx
#endif

#ifndef AT_EMPTY_PATH
#define AT_EMPTY_PATH 0x1000
#endif

#ifndef AT_NO_AUTOMOUNT
#define AT_NO_AUTOMOUNT 0x800
#endif

#ifndef STATX_BASIC_STATS
#define STATX_BASIC_STATS 0x000007ffU
#endif

static int valid_regular_statx(const struct statx *status) {
    return (status->stx_mask & STATX_BASIC_STATS) == STATX_BASIC_STATS
            && status->stx_blksize == 4096
            && status->stx_nlink == 1
            && status->stx_uid == 1000
            && status->stx_gid == 1000
            && status->stx_size == 11
            && status->stx_blocks == 1
            && (status->stx_mode & S_IFMT) == S_IFREG;
}

int main(void) {
    int fd = open("/statx.txt", O_CREAT | O_RDWR | O_TRUNC, 0644);
    if (fd < 0) {
        puts("open-failed");
        return 1;
    }
    if (write(fd, "statx-data\n", 11) != 11) {
        puts("write-failed");
        close(fd);
        return 2;
    }

    struct statx status;
    memset(&status, 0, sizeof(status));
    if (syscall(SYS_statx, AT_FDCWD, "/statx.txt", AT_NO_AUTOMOUNT, STATX_BASIC_STATS, &status) != 0
            || !valid_regular_statx(&status)) {
        puts("path-statx-failed");
        close(fd);
        return 3;
    }

    memset(&status, 0, sizeof(status));
    if (syscall(SYS_statx, fd, "", AT_EMPTY_PATH, STATX_BASIC_STATS, &status) != 0
            || !valid_regular_statx(&status)) {
        puts("fd-statx-failed");
        close(fd);
        return 4;
    }

    if (close(fd) != 0) {
        puts("close-failed");
        return 5;
    }

    puts("statx-ok");
    return 0;
}
