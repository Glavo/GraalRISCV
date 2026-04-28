/*
 * This static musl example validates statvfs and fstatvfs emulation. The
 * simulator reports deterministic synthetic file-system capacity values so
 * libc callers can run without depending on the host volume.
 */

#include <fcntl.h>
#include <stdio.h>
#include <sys/statvfs.h>
#include <unistd.h>

/* Checks the stable file-system values promised by the simulator. */
static int valid_statvfs(const struct statvfs *status) {
    return status->f_bsize == 4096
            && status->f_frsize == 4096
            && status->f_blocks == 1048576
            && status->f_bfree == 1048576
            && status->f_bavail == 1048576
            && status->f_files == 1048576
            && status->f_ffree == 1048576
            && status->f_favail == 1048576
            && status->f_namemax == 255;
}

int main(void) {
    /* Create a file so fstatvfs can validate descriptor-based lookup as well. */
    int fd = open("/status.txt", O_CREAT | O_RDWR | O_TRUNC, 0644);
    if (fd < 0) {
        puts("open-failed");
        return 1;
    }
    if (write(fd, "status\n", 7) != 7) {
        puts("write-failed");
        close(fd);
        return 2;
    }

    /* Path-based status should work for the guest root. */
    struct statvfs root_status;
    if (statvfs("/", &root_status) != 0 || !valid_statvfs(&root_status)) {
        puts("statvfs-failed");
        close(fd);
        return 3;
    }

    /* Descriptor-based status should return the same deterministic values. */
    struct statvfs file_status;
    if (fstatvfs(fd, &file_status) != 0 || !valid_statvfs(&file_status)) {
        puts("fstatvfs-failed");
        close(fd);
        return 4;
    }

    if (close(fd) != 0) {
        puts("close-failed");
        return 5;
    }

    puts("statvfs-ok");
    return 0;
}
