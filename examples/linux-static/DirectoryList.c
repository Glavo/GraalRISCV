/*
 * This static musl example validates directory enumeration. The host-side test
 * prepares a guest-visible directory containing one file and one subdirectory,
 * then checks that readdir reports stable names and directory entry types.
 */

#include <dirent.h>
#include <stdio.h>
#include <string.h>

int main(void) {
    /* Open a pre-created directory under the sandboxed guest root. */
    DIR *directory = opendir("/entries");
    if (directory == NULL) {
        puts("opendir-failed");
        return 1;
    }

    int matched = 0;
    struct dirent *entry;
    while ((entry = readdir(directory)) != NULL) {
        /* Ignore synthetic entries and validate only the fixture contents. */
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
            continue;
        }

        /* Check both name translation and d_type propagation from the host file system. */
        if (strcmp(entry->d_name, "alpha.txt") == 0 && entry->d_type == DT_REG) {
            puts("alpha.txt:file");
            matched++;
        } else if (strcmp(entry->d_name, "nested") == 0 && entry->d_type == DT_DIR) {
            puts("nested:dir");
            matched++;
        } else {
            printf("unexpected:%s:%u\n", entry->d_name, (unsigned) entry->d_type);
            closedir(directory);
            return 2;
        }
    }

    if (closedir(directory) != 0) {
        puts("closedir-failed");
        return 3;
    }

    /* Both prepared entries must have been seen exactly once. */
    return matched == 2 ? 0 : 4;
}
