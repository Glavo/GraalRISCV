/*
 * This static musl example validates process identity, lightweight signal
 * setup, robust-list registration, thread-id bookkeeping, and selected prctl
 * operations. It does not deliver a real signal; it verifies the setup paths
 * that libc and language runtimes commonly require.
 */

#define _GNU_SOURCE

#include <errno.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>

#ifndef SYS_set_tid_address
#define SYS_set_tid_address __NR_set_tid_address
#endif

#ifndef SYS_set_robust_list
#define SYS_set_robust_list __NR_set_robust_list
#endif

#ifndef SYS_gettid
#define SYS_gettid __NR_gettid
#endif

#ifndef SYS_tgkill
#define SYS_tgkill __NR_tgkill
#endif

/* Minimal robust-list layout used by SYS_set_robust_list. */
struct robust_list_head {
    void *list;
    long futex_offset;
    void *list_op_pending;
};

/* Storage passed to process and signal syscalls that expect guest pointers. */
static int child_tid = 0;
static struct robust_list_head robust_head;
static unsigned char signal_stack[SIGSTKSZ];

/* Prints a stable failure token for the Gradle-side assertion. */
static int fail(const char *message) {
    puts(message);
    return 1;
}

/* Handler used only to validate sigaction installation. */
static void unused_handler(int signal_number) {
    (void) signal_number;
}

int main(void) {
    /* Identity calls should return deterministic positive ids for the synthetic process. */
    pid_t pid = getpid();
    if (pid <= 0 || getppid() < 0) {
        return fail("process-id-failed");
    }
    if (getuid() != geteuid() || getgid() != getegid()) {
        return fail("identity-failed");
    }

    /* Thread-id and robust-list registration are required by pthread and Go runtimes. */
    long tid = syscall(SYS_gettid);
    if (tid <= 0) {
        return fail("gettid-failed");
    }
    if (syscall(SYS_set_tid_address, &child_tid) != tid) {
        return fail("set-tid-address-failed");
    }

    memset(&robust_head, 0, sizeof(robust_head));
    if (syscall(SYS_set_robust_list, &robust_head, sizeof(robust_head)) != 0) {
        return fail("set-robust-list-failed");
    }

    /* Alternate signal stack state should round-trip through sigaltstack. */
    stack_t new_stack;
    memset(&new_stack, 0, sizeof(new_stack));
    new_stack.ss_sp = signal_stack;
    new_stack.ss_size = sizeof(signal_stack);
    if (sigaltstack(&new_stack, NULL) != 0) {
        return fail("sigaltstack-set-failed");
    }

    stack_t old_stack;
    memset(&old_stack, 0, sizeof(old_stack));
    if (sigaltstack(NULL, &old_stack) != 0 || old_stack.ss_sp != signal_stack || old_stack.ss_size != sizeof(signal_stack)) {
        return fail("sigaltstack-get-failed");
    }

    /* Install and block a handler to exercise signal mask and action syscalls. */
    struct sigaction action;
    memset(&action, 0, sizeof(action));
    action.sa_handler = unused_handler;
    sigemptyset(&action.sa_mask);
    if (sigaction(SIGUSR1, &action, NULL) != 0) {
        return fail("sigaction-failed");
    }

    sigset_t block_set;
    sigset_t old_set;
    sigemptyset(&block_set);
    sigaddset(&block_set, SIGUSR1);
    if (sigprocmask(SIG_BLOCK, &block_set, &old_set) != 0) {
        return fail("sigprocmask-failed");
    }

    /* Signal number 0 probes validate process and thread existence without delivery. */
    if (kill(pid, 0) != 0 || syscall(SYS_tgkill, pid, tid, 0) != 0) {
        return fail("signal-probe-failed");
    }

    /* The remaining checks cover prctl state commonly touched by runtimes. */
    if (prctl(PR_SET_NAME, "graalriscv", 0, 0, 0) != 0) {
        return fail("prctl-set-name-failed");
    }
    char name[16];
    memset(name, 0, sizeof(name));
    if (prctl(PR_GET_NAME, name, 0, 0, 0) != 0 || strcmp(name, "graalriscv") != 0) {
        return fail("prctl-get-name-failed");
    }

    int parent_death_signal = -1;
    if (prctl(PR_SET_PDEATHSIG, SIGTERM, 0, 0, 0) != 0
            || prctl(PR_GET_PDEATHSIG, &parent_death_signal, 0, 0, 0) != 0
            || parent_death_signal != SIGTERM) {
        return fail("prctl-pdeathsig-failed");
    }

    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0 || prctl(PR_GET_NO_NEW_PRIVS, 0, 0, 0, 0) != 1) {
        return fail("prctl-no-new-privs-failed");
    }

    int subreaper = -1;
    if (prctl(PR_SET_CHILD_SUBREAPER, 1, 0, 0, 0) != 0
            || prctl(PR_GET_CHILD_SUBREAPER, &subreaper, 0, 0, 0) != 0
            || subreaper != 1) {
        return fail("prctl-subreaper-failed");
    }

    if (prctl(PR_SET_TIMERSLACK, 12345, 0, 0, 0) != 0 || prctl(PR_GET_TIMERSLACK, 0, 0, 0, 0) != 12345) {
        return fail("prctl-timerslack-failed");
    }

    puts("process-signals-ok");
    return 0;
}
