/*
 * This static musl example validates common runtime-service syscalls used by
 * larger static programs. It covers clocks, sleeping, rusage, CPU affinity,
 * uname, limits, randomness, and anonymous memory mapping operations.
 */

#define _GNU_SOURCE

#include <errno.h>
#include <sched.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/random.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <sys/time.h>
#include <sys/utsname.h>
#include <time.h>
#include <unistd.h>

#ifndef SYS_getrandom
#define SYS_getrandom __NR_getrandom
#endif

#ifndef SYS_prlimit64
#define SYS_prlimit64 __NR_prlimit64
#endif

/* Prints a stable failure token for the Gradle-side assertion. */
static int fail(const char *message) {
    puts(message);
    return 1;
}

int main(void) {
    /* Clock queries should return normalized timespec/timeval values. */
    struct timespec realtime;
    if (clock_gettime(CLOCK_REALTIME, &realtime) != 0 || realtime.tv_nsec < 0 || realtime.tv_nsec >= 1000000000L) {
        return fail("clock-realtime-failed");
    }

    struct timespec monotonic;
    if (clock_gettime(CLOCK_MONOTONIC, &monotonic) != 0 || monotonic.tv_nsec < 0 || monotonic.tv_nsec >= 1000000000L) {
        return fail("clock-monotonic-failed");
    }

    struct timeval timeval;
    if (gettimeofday(&timeval, NULL) != 0 || timeval.tv_usec < 0 || timeval.tv_usec >= 1000000L) {
        return fail("gettimeofday-failed");
    }

    /* A zero-duration sleep should succeed without advancing guest-visible state incorrectly. */
    struct timespec zero_sleep = {0, 0};
    if (nanosleep(&zero_sleep, NULL) != 0) {
        return fail("nanosleep-failed");
    }

    /* These process information calls are used by libc and language runtimes during startup. */
    struct rusage usage;
    if (getrusage(RUSAGE_SELF, &usage) != 0) {
        return fail("getrusage-failed");
    }

    cpu_set_t affinity;
    CPU_ZERO(&affinity);
    if (sched_getaffinity(0, sizeof(affinity), &affinity) != 0 || !CPU_ISSET(0, &affinity)) {
        return fail("sched-affinity-failed");
    }

    struct utsname utsname;
    if (uname(&utsname) != 0 || strcmp(utsname.machine, "riscv64") != 0) {
        return fail("uname-failed");
    }

    struct rlimit limit;
    if (syscall(SYS_prlimit64, 0, RLIMIT_NOFILE, NULL, &limit) != 0 || limit.rlim_cur == 0) {
        return fail("prlimit-failed");
    }

    /* Random bytes must be nonzero so callers do not treat the source as stubbed out. */
    unsigned char random_bytes[16];
    memset(random_bytes, 0, sizeof(random_bytes));
    if (syscall(SYS_getrandom, random_bytes, sizeof(random_bytes), GRND_NONBLOCK) != sizeof(random_bytes)) {
        return fail("getrandom-failed");
    }
    unsigned int random_sum = 0;
    for (size_t index = 0; index < sizeof(random_bytes); index++) {
        random_sum += random_bytes[index];
    }
    if (random_sum == 0) {
        return fail("getrandom-zero-failed");
    }

    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) {
        return fail("page-size-failed");
    }

    /* Reserve two pages, commit one page, and then discard it through madvise. */
    void *mapping = mmap(
            NULL,
            (size_t) page_size * 2,
            PROT_NONE,
            MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE,
            -1,
            0);
    if (mapping == MAP_FAILED) {
        return fail("mmap-reserve-failed");
    }

    if (mprotect(mapping, (size_t) page_size, PROT_READ | PROT_WRITE) != 0) {
        munmap(mapping, (size_t) page_size * 2);
        return fail("mprotect-write-failed");
    }

    unsigned char *bytes = mapping;
    bytes[0] = 0x5a;
    bytes[page_size - 1] = 0xa5;
    if (madvise(mapping, (size_t) page_size, MADV_DONTNEED) != 0) {
        munmap(mapping, (size_t) page_size * 2);
        return fail("madvise-failed");
    }
    if (bytes[0] != 0 || bytes[page_size - 1] != 0) {
        munmap(mapping, (size_t) page_size * 2);
        return fail("madvise-clear-failed");
    }

    /* Protect and unmap the region to validate the end of the mapping lifecycle. */
    if (mprotect(mapping, (size_t) page_size, PROT_NONE) != 0) {
        munmap(mapping, (size_t) page_size * 2);
        return fail("mprotect-none-failed");
    }
    if (munmap(mapping, (size_t) page_size * 2) != 0) {
        return fail("munmap-failed");
    }

    puts("runtime-services-ok");
    return 0;
}
