#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

int main(void) {
    if (mkdir("/work", 0777) != 0) {
        puts("mkdir-failed");
        return 1;
    }

    int fd = open("/work/message.txt", O_CREAT | O_WRONLY | O_TRUNC, 0644);
    if (fd < 0) {
        puts("open-write-failed");
        return 2;
    }
    if (write(fd, "cwd-data\n", 9) != 9) {
        puts("write-failed");
        close(fd);
        return 3;
    }
    if (close(fd) != 0) {
        puts("close-write-failed");
        return 4;
    }

    int root = open("/", O_RDONLY | O_DIRECTORY);
    if (root < 0) {
        puts("open-root-failed");
        return 5;
    }
    if (chdir("/work") != 0) {
        puts("chdir-failed");
        close(root);
        return 6;
    }

    char cwd[64];
    if (getcwd(cwd, sizeof cwd) == NULL || strcmp(cwd, "/work") != 0) {
        puts("getcwd-work-failed");
        close(root);
        return 7;
    }

    FILE *in = fopen("message.txt", "r");
    if (in == NULL) {
        puts("open-read-failed");
        close(root);
        return 8;
    }

    char buffer[32];
    if (fgets(buffer, sizeof buffer, in) == NULL) {
        puts("read-failed");
        fclose(in);
        close(root);
        return 9;
    }
    if (fclose(in) != 0) {
        puts("close-read-failed");
        close(root);
        return 10;
    }
    if (strcmp(buffer, "cwd-data\n") != 0) {
        puts("content-failed");
        close(root);
        return 11;
    }

    if (fchdir(root) != 0) {
        puts("fchdir-failed");
        close(root);
        return 12;
    }
    if (close(root) != 0) {
        puts("close-root-failed");
        return 13;
    }
    if (getcwd(cwd, sizeof cwd) == NULL || strcmp(cwd, "/") != 0) {
        puts("getcwd-root-failed");
        return 14;
    }

    puts("cwd-ok");
    return 0;
}
