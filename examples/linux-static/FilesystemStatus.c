#include <fcntl.h>
#include <stdio.h>
#include <sys/statvfs.h>
#include <unistd.h>

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

    struct statvfs root_status;
    if (statvfs("/", &root_status) != 0 || !valid_statvfs(&root_status)) {
        puts("statvfs-failed");
        close(fd);
        return 3;
    }

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
