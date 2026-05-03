// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.ProgramExitException;
import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.Memory;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/// Handles the FreeBSD RISC-V syscall ABI exposed by the simulator.
@NotNullByDefault
public final class FreeBsdGuestSyscalls extends GuestSyscalls {
    /// Creates a FreeBSD syscall handler backed by streams, lazy filesystem mounts, time source, credentials,
    /// terminal option, and guest thread runner.
    public FreeBsdGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            String @Unmodifiable [] filesystemMountSpecs,
            TimeSource timeSource,
            boolean useHostTty,
            GuestCredentials credentials,
            GuestThreadRunner guestThreadRunner) {
        super(
                memory,
                in,
                out,
                err,
                initialProgramBreak,
                filesystemMountSpecs,
                timeSource,
                useHostTty,
                credentials,
                guestThreadRunner);
    }

    /// Creates a child-process FreeBSD syscall handler by copying fork-inherited parent state.
    private FreeBsdGuestSyscalls(FreeBsdGuestSyscalls parent, Memory memory, GuestProcess process) {
        super(parent, memory, process);
    }

    /// The FreeBSD RISC-V syscall number for the `syscall` indirection entry.
    private static final int FREEBSD_SYS_SYSCALL = 0;

    /// The FreeBSD RISC-V syscall number for `exit`.
    private static final int FREEBSD_SYS_EXIT = 1;

    /// The FreeBSD RISC-V syscall number for `read`.
    private static final int FREEBSD_SYS_READ = 3;

    /// The FreeBSD RISC-V syscall number for `write`.
    private static final int FREEBSD_SYS_WRITE = 4;

    /// The FreeBSD RISC-V syscall number for `open`.
    private static final int FREEBSD_SYS_OPEN = 5;

    /// The FreeBSD RISC-V syscall number for `close`.
    private static final int FREEBSD_SYS_CLOSE = 6;

    /// The FreeBSD RISC-V syscall number for `chdir`.
    private static final int FREEBSD_SYS_CHDIR = 12;

    /// The FreeBSD RISC-V syscall number for `fchdir`.
    private static final int FREEBSD_SYS_FCHDIR = 13;

    /// The FreeBSD RISC-V syscall number for `getpid`.
    private static final int FREEBSD_SYS_GETPID = 20;

    /// The FreeBSD RISC-V syscall number for `getuid`.
    private static final int FREEBSD_SYS_GETUID = 24;

    /// The FreeBSD RISC-V syscall number for `geteuid`.
    private static final int FREEBSD_SYS_GETEUID = 25;

    /// The FreeBSD RISC-V syscall number for `access`.
    private static final int FREEBSD_SYS_ACCESS = 33;

    /// The FreeBSD RISC-V syscall number for `sync`.
    private static final int FREEBSD_SYS_SYNC = 36;

    /// The FreeBSD RISC-V syscall number for `kill`.
    private static final int FREEBSD_SYS_KILL = 37;

    /// The FreeBSD RISC-V syscall number for `sigaltstack`.
    private static final int FREEBSD_SYS_SIGALTSTACK = 53;

    /// The FreeBSD RISC-V syscall number for `getppid`.
    private static final int FREEBSD_SYS_GETPPID = 39;

    /// The FreeBSD RISC-V syscall number for `dup`.
    private static final int FREEBSD_SYS_DUP = 41;

    /// The FreeBSD RISC-V syscall number for `getegid`.
    private static final int FREEBSD_SYS_GETEGID = 43;

    /// The FreeBSD RISC-V syscall number for `getgid`.
    private static final int FREEBSD_SYS_GETGID = 47;

    /// The FreeBSD RISC-V syscall number for `ioctl`.
    private static final int FREEBSD_SYS_IOCTL = 54;

    /// The FreeBSD RISC-V syscall number for `readlink`.
    private static final int FREEBSD_SYS_READLINK = 58;

    /// The FreeBSD RISC-V syscall number for `execve`.
    private static final int FREEBSD_SYS_EXECVE = 59;

    /// The FreeBSD RISC-V syscall number for `munmap`.
    private static final int FREEBSD_SYS_MUNMAP = 73;

    /// The FreeBSD RISC-V syscall number for `mprotect`.
    private static final int FREEBSD_SYS_MPROTECT = 74;

    /// The FreeBSD RISC-V syscall number for `madvise`.
    private static final int FREEBSD_SYS_MADVISE = 75;

    /// The FreeBSD RISC-V syscall number for `setpgid`.
    private static final int FREEBSD_SYS_SETPGID = 82;

    /// The FreeBSD RISC-V syscall number for `dup2`.
    private static final int FREEBSD_SYS_DUP2 = 90;

    /// The FreeBSD RISC-V syscall number for `fcntl`.
    private static final int FREEBSD_SYS_FCNTL = 92;

    /// The FreeBSD RISC-V syscall number for `fsync`.
    private static final int FREEBSD_SYS_FSYNC = 95;

    /// The FreeBSD RISC-V syscall number for `gettimeofday`.
    private static final int FREEBSD_SYS_GETTIMEOFDAY = 116;

    /// The FreeBSD RISC-V syscall number for `getrusage`.
    private static final int FREEBSD_SYS_GETRUSAGE = 117;

    /// The FreeBSD RISC-V syscall number for `readv`.
    private static final int FREEBSD_SYS_READV = 120;

    /// The FreeBSD RISC-V syscall number for `writev`.
    private static final int FREEBSD_SYS_WRITEV = 121;

    /// The FreeBSD RISC-V syscall number for `setsid`.
    private static final int FREEBSD_SYS_SETSID = 147;

    /// The FreeBSD RISC-V syscall number for `__syscall`.
    private static final int FREEBSD_SYS___SYSCALL = 198;

    /// The FreeBSD RISC-V syscall number for `getrlimit`.
    private static final int FREEBSD_SYS_GETRLIMIT = 194;

    /// The FreeBSD RISC-V syscall number for `__sysctl`.
    private static final int FREEBSD_SYS___SYSCTL = 202;

    /// The FreeBSD RISC-V syscall number for `clock_gettime`.
    private static final int FREEBSD_SYS_CLOCK_GETTIME = 232;

    /// The FreeBSD RISC-V syscall number for `clock_getres`.
    private static final int FREEBSD_SYS_CLOCK_GETRES = 234;

    /// The FreeBSD RISC-V syscall number for `nanosleep`.
    private static final int FREEBSD_SYS_NANOSLEEP = 240;

    /// The FreeBSD RISC-V syscall number for `issetugid`.
    private static final int FREEBSD_SYS_ISSETUGID = 253;

    /// The FreeBSD RISC-V syscall number for `__getcwd`.
    private static final int FREEBSD_SYS___GETCWD = 326;

    /// The FreeBSD RISC-V syscall number for `sched_yield`.
    private static final int FREEBSD_SYS_SCHED_YIELD = 331;

    /// The FreeBSD RISC-V syscall number for `sigprocmask`.
    private static final int FREEBSD_SYS_SIGPROCMASK = 340;

    /// The FreeBSD RISC-V syscall number for `getresuid`.
    private static final int FREEBSD_SYS_GETRESUID = 360;

    /// The FreeBSD RISC-V syscall number for `getresgid`.
    private static final int FREEBSD_SYS_GETRESGID = 361;

    /// The FreeBSD RISC-V syscall number for `sigaction`.
    private static final int FREEBSD_SYS_SIGACTION = 416;

    /// The FreeBSD RISC-V syscall number for `thr_exit`.
    private static final int FREEBSD_SYS_THR_EXIT = 431;

    /// The FreeBSD RISC-V syscall number for `thr_self`.
    private static final int FREEBSD_SYS_THR_SELF = 432;

    /// The FreeBSD RISC-V syscall number for `thr_kill`.
    private static final int FREEBSD_SYS_THR_KILL = 433;

    /// The FreeBSD RISC-V syscall number for `_umtx_op`.
    private static final int FREEBSD_SYS_UMTX_OP = 454;

    /// The FreeBSD RISC-V syscall number for `thr_new`.
    private static final int FREEBSD_SYS_THR_NEW = 455;

    /// The FreeBSD RISC-V syscall number for `pread`.
    private static final int FREEBSD_SYS_PREAD = 475;

    /// The FreeBSD RISC-V syscall number for `pwrite`.
    private static final int FREEBSD_SYS_PWRITE = 476;

    /// The FreeBSD RISC-V syscall number for `mmap`.
    private static final int FREEBSD_SYS_MMAP = 477;

    /// The FreeBSD RISC-V syscall number for `lseek`.
    private static final int FREEBSD_SYS_LSEEK = 478;

    /// The FreeBSD RISC-V syscall number for `truncate`.
    private static final int FREEBSD_SYS_TRUNCATE = 479;

    /// The FreeBSD RISC-V syscall number for `ftruncate`.
    private static final int FREEBSD_SYS_FTRUNCATE = 480;

    /// The FreeBSD RISC-V syscall number for `faccessat`.
    private static final int FREEBSD_SYS_FACCESSAT = 489;

    /// The FreeBSD RISC-V syscall number for `cpuset_getaffinity`.
    private static final int FREEBSD_SYS_CPUSET_GETAFFINITY = 487;

    /// The FreeBSD RISC-V syscall number for `fchownat`.
    private static final int FREEBSD_SYS_FCHOWNAT = 491;

    /// The FreeBSD RISC-V syscall number for `mkdirat`.
    private static final int FREEBSD_SYS_MKDIRAT = 496;

    /// The FreeBSD RISC-V syscall number for `openat`.
    private static final int FREEBSD_SYS_OPENAT = 499;

    /// The FreeBSD RISC-V syscall number for `readlinkat`.
    private static final int FREEBSD_SYS_READLINKAT = 500;

    /// The FreeBSD RISC-V syscall number for `renameat`.
    private static final int FREEBSD_SYS_RENAMEAT = 501;

    /// The FreeBSD RISC-V syscall number for `unlinkat`.
    private static final int FREEBSD_SYS_UNLINKAT = 503;

    /// The FreeBSD RISC-V syscall number for `pipe2`.
    private static final int FREEBSD_SYS_PIPE2 = 542;

    /// The FreeBSD RISC-V syscall number for `ppoll`.
    private static final int FREEBSD_SYS_PPOLL = 545;

    /// The FreeBSD RISC-V syscall number for `fdatasync`.
    private static final int FREEBSD_SYS_FDATASYNC = 550;

    /// The FreeBSD RISC-V syscall number for `fstat`.
    private static final int FREEBSD_SYS_FSTAT = 551;

    /// The FreeBSD RISC-V syscall number for `fstatat`.
    private static final int FREEBSD_SYS_FSTATAT = 552;

    /// The FreeBSD RISC-V syscall number for `statfs`.
    private static final int FREEBSD_SYS_STATFS = 555;

    /// The FreeBSD RISC-V syscall number for `fstatfs`.
    private static final int FREEBSD_SYS_FSTATFS = 556;

    /// FreeBSD `CTL_QUERY`.
    private static final int FREEBSD_CTL_QUERY = 0;

    /// FreeBSD `CTL_QUERY_MIB`.
    private static final int FREEBSD_CTL_QUERY_MIB = 3;

    /// FreeBSD `CTL_HW`.
    private static final int FREEBSD_CTL_HW = 6;

    /// FreeBSD `HW_PAGESIZE`.
    private static final int FREEBSD_HW_PAGESIZE = 7;

    /// Synthetic FreeBSD sysctl MIB component for `kern`.
    private static final int FREEBSD_CTL_KERN = 1;

    /// Synthetic FreeBSD sysctl MIB component for `kern.smp`.
    private static final int FREEBSD_KERN_SMP = 1000;

    /// Synthetic FreeBSD sysctl MIB component for `kern.smp.maxcpus`.
    private static final int FREEBSD_KERN_SMP_MAXCPUS = 1;

    /// Synthetic FreeBSD `kern.smp.maxcpus` MIB exposed to Go runtime startup.
    private static final int @Unmodifiable [] FREEBSD_SYSCTL_KERN_SMP_MAXCPUS = {
            FREEBSD_CTL_KERN,
            FREEBSD_KERN_SMP,
            FREEBSD_KERN_SMP_MAXCPUS
    };

    /// FreeBSD sysctl name used by Go to discover the cpuset mask size.
    private static final String FREEBSD_SYSCTL_KERN_SMP_MAXCPUS_NAME = "kern.smp.maxcpus";

    /// FreeBSD `O_ACCMODE`.
    private static final long FREEBSD_O_ACCMODE = 0x0003;

    /// FreeBSD `O_NONBLOCK`.
    private static final long FREEBSD_O_NONBLOCK = 0x0004;

    /// FreeBSD `O_APPEND`.
    private static final long FREEBSD_O_APPEND = 0x0008;

    /// FreeBSD `O_CREAT`.
    private static final long FREEBSD_O_CREAT = 0x0200;

    /// FreeBSD `O_TRUNC`.
    private static final long FREEBSD_O_TRUNC = 0x0400;

    /// FreeBSD `O_EXCL`.
    private static final long FREEBSD_O_EXCL = 0x0800;

    /// FreeBSD `O_DIRECTORY`.
    private static final long FREEBSD_O_DIRECTORY = 0x0002_0000;

    /// FreeBSD `O_CLOEXEC`.
    private static final long FREEBSD_O_CLOEXEC = 0x0010_0000;

    /// FreeBSD `AT_EACCESS`.
    private static final long FREEBSD_AT_EACCESS = 0x0100;

    /// FreeBSD `AT_SYMLINK_NOFOLLOW`.
    private static final long FREEBSD_AT_SYMLINK_NOFOLLOW = 0x0200;

    /// FreeBSD `AT_REMOVEDIR`.
    private static final long FREEBSD_AT_REMOVEDIR = 0x0800;

    /// FreeBSD `AT_EMPTY_PATH`.
    private static final long FREEBSD_AT_EMPTY_PATH = 0x4000;


    /// FreeBSD `RLIMIT_NOFILE`.
    private static final int FREEBSD_RLIMIT_NOFILE = 8;


    /// FreeBSD `MAP_ANON`.
    private static final long FREEBSD_MAP_ANON = 0x1000;

    /// FreeBSD `MAP_EXCL`, used with `MAP_FIXED`.
    private static final long FREEBSD_MAP_EXCL = 0x4000;


    /// FreeBSD `SIG_BLOCK` signal-mask operation.
    private static final long FREEBSD_SIG_BLOCK = 1;

    /// FreeBSD `SIG_UNBLOCK` signal-mask operation.
    private static final long FREEBSD_SIG_UNBLOCK = 2;

    /// FreeBSD `SIG_SETMASK` signal-mask operation.
    private static final long FREEBSD_SIG_SETMASK = 3;

    /// The byte size of FreeBSD RISC-V `struct sigaction` used by Go.
    private static final long FREEBSD_SIGACTION_SIZE = Long.BYTES + Integer.BYTES + 4L * Integer.BYTES;

    /// The byte offset of `ss_sp` inside FreeBSD RISC-V `stack_t`.
    private static final long FREEBSD_SIGNAL_STACK_POINTER_OFFSET = 0;

    /// The byte offset of `ss_size` inside FreeBSD RISC-V `stack_t`.
    private static final long FREEBSD_SIGNAL_STACK_SIZE_OFFSET = Long.BYTES;

    /// The byte offset of `ss_flags` inside FreeBSD RISC-V `stack_t`.
    private static final long FREEBSD_SIGNAL_STACK_FLAGS_OFFSET = 2L * Long.BYTES;

    /// FreeBSD `SS_DISABLE`.
    private static final long FREEBSD_SS_DISABLE = 4;

    /// FreeBSD `_UMTX_OP_WAIT_UINT`.
    private static final long FREEBSD_UMTX_OP_WAIT_UINT = 0x0b;

    /// FreeBSD `_UMTX_OP_WAIT_UINT_PRIVATE`.
    private static final long FREEBSD_UMTX_OP_WAIT_UINT_PRIVATE = 0x0f;

    /// FreeBSD `_UMTX_OP_WAKE`.
    private static final long FREEBSD_UMTX_OP_WAKE = 0x03;

    /// FreeBSD `_UMTX_OP_WAKE_PRIVATE`.
    private static final long FREEBSD_UMTX_OP_WAKE_PRIVATE = 0x10;

    /// FreeBSD `EAGAIN` as a raw negative syscall result.
    private static final long FREEBSD_EAGAIN = -35;

    /// FreeBSD `ETIMEDOUT` as a raw negative syscall result.
    private static final long FREEBSD_ETIMEDOUT = -60;

    /// The byte offset of `start_func` inside FreeBSD `struct thr_param`.
    private static final long FREEBSD_THR_PARAM_START_FUNC_OFFSET = 0;

    /// The byte offset of `arg` inside FreeBSD `struct thr_param`.
    private static final long FREEBSD_THR_PARAM_ARG_OFFSET = Long.BYTES;

    /// The byte offset of `stack_base` inside FreeBSD `struct thr_param`.
    private static final long FREEBSD_THR_PARAM_STACK_BASE_OFFSET = 2L * Long.BYTES;

    /// The byte offset of `stack_size` inside FreeBSD `struct thr_param`.
    private static final long FREEBSD_THR_PARAM_STACK_SIZE_OFFSET = 3L * Long.BYTES;

    /// The byte offset of `tls_base` inside FreeBSD `struct thr_param`.
    private static final long FREEBSD_THR_PARAM_TLS_BASE_OFFSET = 4L * Long.BYTES;

    /// The byte offset of `child_tid` inside FreeBSD `struct thr_param`.
    private static final long FREEBSD_THR_PARAM_CHILD_TID_OFFSET = 6L * Long.BYTES;

    /// The byte offset of `parent_tid` inside FreeBSD `struct thr_param`.
    private static final long FREEBSD_THR_PARAM_PARENT_TID_OFFSET = 7L * Long.BYTES;

    /// The minimum FreeBSD `struct thr_param` byte size needed by the simulator.
    private static final long FREEBSD_THR_PARAM_MINIMUM_SIZE = 8L * Long.BYTES;


    /// Executes the FreeBSD syscall described by the guest argument registers at the supplied program counter.
    @Override
    public void handle(RiscVThreadState state, long pc) {
        long callNumber = state.register(5);
        boolean indirect = callNumber == FREEBSD_SYS_SYSCALL || callNumber == FREEBSD_SYS___SYSCALL;
        int argumentBaseRegister = indirect ? 11 : 10;
        if (indirect) {
            callNumber = state.register(10);
        }
        if (callNumber != (int) callNumber) {
            throw new RiscVException(unsupportedEcallMessage(state, pc, callNumber));
        }

        long previousMask = state.enterSyscallPointerMask();
        try {
            long result;
            try {
                switch ((int) callNumber) {
                case FREEBSD_SYS_EXIT -> {
                    long exitCode = freeBsdArgument(state, argumentBaseRegister, 0);
                    requestProcessExit(exitCode);
                    throw new ProgramExitException(exitCode);
                }
                case FREEBSD_SYS_READ -> result = read(
                        (int) freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_WRITE -> result = write(
                        (int) freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_OPEN -> result = openat(
                        AT_FDCWD,
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdOpenFlagsToLinux(freeBsdArgument(state, argumentBaseRegister, 1)),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_OPENAT -> result = openat(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdOpenFlagsToLinux(freeBsdArgument(state, argumentBaseRegister, 2)),
                        freeBsdArgument(state, argumentBaseRegister, 3));
                case FREEBSD_SYS_CLOSE -> result = close((int) freeBsdArgument(state, argumentBaseRegister, 0));
                case FREEBSD_SYS_CHDIR -> result = chdir(freeBsdArgument(state, argumentBaseRegister, 0));
                case FREEBSD_SYS_FCHDIR -> result = fchdir((int) freeBsdArgument(state, argumentBaseRegister, 0));
                case FREEBSD_SYS_GETPID -> result = process.id();
                case FREEBSD_SYS_GETPPID -> result = process.parentId();
                case FREEBSD_SYS_GETUID -> result = credentials.realUserId();
                case FREEBSD_SYS_GETEUID -> result = credentials.effectiveUserId();
                case FREEBSD_SYS_GETGID -> result = credentials.realGroupId();
                case FREEBSD_SYS_GETEGID -> result = credentials.effectiveGroupId();
                case FREEBSD_SYS_ACCESS -> result = faccessat(
                        AT_FDCWD,
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        0);
                case FREEBSD_SYS_FACCESSAT -> result = faccessat(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2),
                        freeBsdAtFlagsToLinux(freeBsdArgument(state, argumentBaseRegister, 3)));
                case FREEBSD_SYS_SYNC -> result = sync();
                case FREEBSD_SYS_KILL -> result = kill(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS_SIGALTSTACK -> result = freeBsdSigaltstack(
                        state,
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS_DUP -> result = dup((int) freeBsdArgument(state, argumentBaseRegister, 0));
                case FREEBSD_SYS_DUP2 -> result = dup3(
                        (int) freeBsdArgument(state, argumentBaseRegister, 0),
                        (int) freeBsdArgument(state, argumentBaseRegister, 1),
                        0);
                case FREEBSD_SYS_IOCTL -> result = ioctl(
                        (int) freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_READLINK -> result = readlinkat(
                        AT_FDCWD,
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_READLINKAT -> result = readlinkat(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2),
                        freeBsdArgument(state, argumentBaseRegister, 3));
                case FREEBSD_SYS_EXECVE -> result = execve(
                        state,
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_MUNMAP -> result = munmap(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS_MPROTECT -> result = mprotect(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_MADVISE -> result = madvise(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_SETPGID -> result = setpgid(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS_FCNTL -> result = fcntl(
                        (int) freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdFcntlCommandToLinux(freeBsdArgument(state, argumentBaseRegister, 1)),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_FSYNC -> result = fsync((int) freeBsdArgument(state, argumentBaseRegister, 0));
                case FREEBSD_SYS_FDATASYNC -> result = fdatasync((int) freeBsdArgument(state, argumentBaseRegister, 0));
                case FREEBSD_SYS_GETTIMEOFDAY -> result = gettimeofday(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS_READV -> result = readv(
                        (int) freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_WRITEV -> result = writev(
                        (int) freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_SETSID -> result = setsid();
                case FREEBSD_SYS_GETRLIMIT -> result = freeBsdGetrlimit(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS___SYSCTL -> result = freeBsdSysctl(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2),
                        freeBsdArgument(state, argumentBaseRegister, 3),
                        freeBsdArgument(state, argumentBaseRegister, 4),
                        freeBsdArgument(state, argumentBaseRegister, 5));
                case FREEBSD_SYS_CLOCK_GETTIME -> result = clockGettime(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS_CLOCK_GETRES -> result = clockGetres(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS_NANOSLEEP -> result = nanosleep(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS_ISSETUGID -> result = 0;
                case FREEBSD_SYS___GETCWD -> result = getcwd(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS_SCHED_YIELD -> result = schedYield();
                case FREEBSD_SYS_SIGPROCMASK -> result = freeBsdSigprocmask(
                        state,
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_GETRESUID -> result = getresid(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2),
                        credentials.realUserId(),
                        credentials.effectiveUserId(),
                        credentials.savedUserId());
                case FREEBSD_SYS_GETRESGID -> result = getresid(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2),
                        credentials.realGroupId(),
                        credentials.effectiveGroupId(),
                        credentials.savedGroupId());
                case FREEBSD_SYS_SIGACTION -> result = freeBsdSigaction(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_THR_EXIT -> {
                    freeBsdThrExit(state, freeBsdArgument(state, argumentBaseRegister, 0));
                    result = 0;
                }
                case FREEBSD_SYS_THR_SELF -> result = freeBsdThrSelf(
                        state,
                        freeBsdArgument(state, argumentBaseRegister, 0));
                case FREEBSD_SYS_THR_KILL -> result = freeBsdThrKill(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS_UMTX_OP -> result = freeBsdUmtxOp(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2),
                        freeBsdArgument(state, argumentBaseRegister, 3),
                        freeBsdArgument(state, argumentBaseRegister, 4));
                case FREEBSD_SYS_THR_NEW -> result = freeBsdThrNew(
                        state,
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS_PREAD -> result = pread64(
                        (int) freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2),
                        freeBsdArgument(state, argumentBaseRegister, 3));
                case FREEBSD_SYS_PWRITE -> result = pwrite64(
                        (int) freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2),
                        freeBsdArgument(state, argumentBaseRegister, 3));
                case FREEBSD_SYS_MMAP -> {
                    result = mmap(
                            freeBsdArgument(state, argumentBaseRegister, 0),
                            freeBsdArgument(state, argumentBaseRegister, 1),
                            freeBsdArgument(state, argumentBaseRegister, 2),
                            freeBsdMmapFlagsToLinux(freeBsdArgument(state, argumentBaseRegister, 3)),
                            freeBsdArgument(state, argumentBaseRegister, 4),
                            freeBsdArgument(state, argumentBaseRegister, 5));
                }
                case FREEBSD_SYS_CPUSET_GETAFFINITY -> result = freeBsdCpusetGetaffinity(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2),
                        freeBsdArgument(state, argumentBaseRegister, 3),
                        freeBsdArgument(state, argumentBaseRegister, 4));
                case FREEBSD_SYS_LSEEK -> result = lseek(
                        (int) freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        (int) freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_TRUNCATE -> result = truncate(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS_FTRUNCATE -> result = ftruncate(
                        (int) freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1));
                case FREEBSD_SYS_FCHOWNAT -> result = fchownat(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2),
                        freeBsdArgument(state, argumentBaseRegister, 3),
                        freeBsdAtFlagsToLinux(freeBsdArgument(state, argumentBaseRegister, 4)));
                case FREEBSD_SYS_MKDIRAT -> result = mkdirat(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2));
                case FREEBSD_SYS_RENAMEAT -> result = renameat(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2),
                        freeBsdArgument(state, argumentBaseRegister, 3));
                case FREEBSD_SYS_UNLINKAT -> result = unlinkat(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdAtFlagsToLinux(freeBsdArgument(state, argumentBaseRegister, 2)));
                case FREEBSD_SYS_PIPE2 -> result = pipe2(
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdOpenFlagsToLinux(freeBsdArgument(state, argumentBaseRegister, 1)));
                case FREEBSD_SYS_PPOLL -> result = ppoll(
                        state,
                        freeBsdArgument(state, argumentBaseRegister, 0),
                        freeBsdArgument(state, argumentBaseRegister, 1),
                        freeBsdArgument(state, argumentBaseRegister, 2),
                        freeBsdArgument(state, argumentBaseRegister, 3),
                        freeBsdArgument(state, argumentBaseRegister, 4));
                    default -> throw new RiscVException(unsupportedEcallMessage(state, pc, callNumber));
                }
            } catch (RiscVException exception) {
                throw new RiscVException(freeBsdSyscallFailureMessage(state, pc, callNumber, argumentBaseRegister, exception), exception);
            }
            setFreeBsdSyscallResult(state, result);
        } finally {
            state.restorePointerMask(previousMask);
        }
    }

    /// Reads one FreeBSD syscall argument register after optional syscall-number indirection.
    private static long freeBsdArgument(RiscVThreadState state, int baseRegister, int index) {
        int register = baseRegister + index;
        return register <= 17 ? state.register(register) : 0;
    }

    /// Stores a FreeBSD syscall result and error indicator in guest registers.
    private static void setFreeBsdSyscallResult(RiscVThreadState state, long result) {
        if (result < 0) {
            state.setRegister(10, -result);
            state.setRegister(5, 1);
            return;
        }
        state.setRegister(10, result);
        state.setRegister(5, 0);
    }

    /// Builds a diagnostic message for a FreeBSD syscall handler failure.
    private static String freeBsdSyscallFailureMessage(
            RiscVThreadState state,
            long pc,
            long callNumber,
            int argumentBaseRegister,
            RiscVException exception) {
        return "FreeBSD syscall failed: pc=0x"
                + Long.toUnsignedString(pc, 16)
                + ", call="
                + callNumber
                + ", a0=0x"
                + Long.toUnsignedString(freeBsdArgument(state, argumentBaseRegister, 0), 16)
                + ", a1=0x"
                + Long.toUnsignedString(freeBsdArgument(state, argumentBaseRegister, 1), 16)
                + ", a2=0x"
                + Long.toUnsignedString(freeBsdArgument(state, argumentBaseRegister, 2), 16)
                + ", a3=0x"
                + Long.toUnsignedString(freeBsdArgument(state, argumentBaseRegister, 3), 16)
                + ", a4=0x"
                + Long.toUnsignedString(freeBsdArgument(state, argumentBaseRegister, 4), 16)
                + ", a5=0x"
                + Long.toUnsignedString(freeBsdArgument(state, argumentBaseRegister, 5), 16)
                + ": "
                + exception.getMessage();
    }

    /// Handles the small read-only FreeBSD `__sysctl` surface required by Go runtime startup.
    private long freeBsdSysctl(
            long mibAddress,
            long mibLength,
            long outputAddress,
            long outputLengthAddress,
            long newValueAddress,
            long newValueLength) {
        if (mibAddress == 0 || mibLength <= 0 || mibLength > 24) {
            return EINVAL;
        }

        int[] mib = new int[(int) mibLength];
        for (int index = 0; index < mib.length; index++) {
            mib[index] = memory.readInt(mibAddress + (long) index * Integer.BYTES);
        }

        if (mib.length == 2 && mib[0] == FREEBSD_CTL_QUERY && mib[1] == FREEBSD_CTL_QUERY_MIB) {
            @Nullable String name = readFreeBsdSysctlName(newValueAddress, newValueLength);
            if (FREEBSD_SYSCTL_KERN_SMP_MAXCPUS_NAME.equals(name)) {
                return writeFreeBsdSysctlIntArray(outputAddress, outputLengthAddress, FREEBSD_SYSCTL_KERN_SMP_MAXCPUS);
            }
            return ENOENT;
        }

        if (mib.length == 2 && mib[0] == FREEBSD_CTL_HW && mib[1] == FREEBSD_HW_PAGESIZE) {
            return writeFreeBsdSysctlInt(outputAddress, outputLengthAddress, memory.pageSize());
        }

        if (Arrays.equals(mib, FREEBSD_SYSCTL_KERN_SMP_MAXCPUS)) {
            return writeFreeBsdSysctlInt(outputAddress, outputLengthAddress, 1);
        }

        return ENOENT;
    }

    /// Reads the sysctl query name supplied to `CTL_QUERY_MIB`.
    private @Nullable String readFreeBsdSysctlName(long address, long length) {
        if (address == 0 || length < 0 || length > Integer.MAX_VALUE) {
            return null;
        }
        byte[] bytes = memory.readBytes(address, length);
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, 0, end, StandardCharsets.US_ASCII);
    }

    /// Writes a 32-bit sysctl value and updates `oldlenp`.
    private long writeFreeBsdSysctlInt(long outputAddress, long outputLengthAddress, long value) {
        return writeFreeBsdSysctlBytes(outputAddress, outputLengthAddress, intBytes(value));
    }

    /// Writes a 32-bit sysctl MIB array and updates `oldlenp`.
    private long writeFreeBsdSysctlIntArray(
            long outputAddress,
            long outputLengthAddress,
            int @Unmodifiable [] values) {
        byte[] bytes = new byte[values.length * Integer.BYTES];
        for (int index = 0; index < values.length; index++) {
            writeLittleEndianInt(bytes, index * Integer.BYTES, values[index]);
        }
        return writeFreeBsdSysctlBytes(outputAddress, outputLengthAddress, bytes);
    }

    /// Writes a sysctl byte result while honoring the guest output length pointer.
    private long writeFreeBsdSysctlBytes(long outputAddress, long outputLengthAddress, byte @Unmodifiable [] bytes) {
        if (outputLengthAddress == 0) {
            return outputAddress == 0 ? 0 : EFAULT;
        }

        long availableLength = memory.readLong(outputLengthAddress);
        memory.writeLong(outputLengthAddress, bytes.length);
        if (outputAddress == 0) {
            return 0;
        }
        if (availableLength < bytes.length) {
            return ENOMEM;
        }

        memory.writeBytes(outputAddress, bytes, 0, bytes.length);
        return 0;
    }

    /// Returns a little-endian byte representation of a 32-bit integer.
    private static byte[] intBytes(long value) {
        byte[] bytes = new byte[Integer.BYTES];
        writeLittleEndianInt(bytes, 0, (int) value);
        return bytes;
    }

    /// Writes a little-endian 32-bit integer into a byte array.
    private static void writeLittleEndianInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> Byte.SIZE);
        bytes[offset + 2] = (byte) (value >>> (2 * Byte.SIZE));
        bytes[offset + 3] = (byte) (value >>> (3 * Byte.SIZE));
    }

    /// Writes a single-CPU FreeBSD affinity mask.
    private long freeBsdCpusetGetaffinity(
            long level,
            long which,
            long id,
            long setSize,
            long maskAddress) {
        if (setSize < Long.BYTES) {
            return EINVAL;
        }
        if (maskAddress == 0) {
            return EFAULT;
        }

        memory.clear(maskAddress, setSize);
        memory.writeLong(maskAddress, 1);
        return 0;
    }

    /// Reads and updates the calling guest thread's FreeBSD signal mask.
    private long freeBsdSigprocmask(RiscVThreadState state, long how, long setAddress, long oldSetAddress) {
        GuestThread thread = state.guestThread();
        long oldMask = thread.signalMask();
        if (oldSetAddress != 0) {
            memory.writeLong(oldSetAddress, oldMask);
            memory.writeLong(oldSetAddress + Long.BYTES, 0);
        }
        if (setAddress == 0) {
            return 0;
        }
        if (how != FREEBSD_SIG_BLOCK && how != FREEBSD_SIG_UNBLOCK && how != FREEBSD_SIG_SETMASK) {
            return EINVAL;
        }

        long requestedMask = memory.readLong(setAddress) & ~UNBLOCKABLE_SIGNAL_MASK;
        long updatedMask = oldMask;
        if (how == FREEBSD_SIG_BLOCK) {
            updatedMask |= requestedMask;
        } else if (how == FREEBSD_SIG_UNBLOCK) {
            updatedMask &= ~requestedMask;
        } else {
            updatedMask = requestedMask;
        }
        thread.setSignalMask(updatedMask);
        return 0;
    }

    /// Accepts FreeBSD signal action setup for a guest that never receives host signals.
    private long freeBsdSigaction(long signalNumber, long actionAddress, long oldActionAddress) {
        if (signalNumber < MIN_SIGNAL_NUMBER || signalNumber > MAX_SIGNAL_NUMBER) {
            return EINVAL;
        }
        if (oldActionAddress != 0) {
            memory.clear(oldActionAddress, FREEBSD_SIGACTION_SIZE);
        }
        return 0;
    }

    /// Writes the current FreeBSD thread id to the guest pointer.
    private long freeBsdThrSelf(RiscVThreadState state, long threadIdAddress) {
        if (threadIdAddress == 0) {
            return EFAULT;
        }
        memory.writeLong(threadIdAddress, state.threadId());
        return 0;
    }

    /// Exits the current FreeBSD guest thread.
    private void freeBsdThrExit(RiscVThreadState state, long threadIdAddress) {
        if (threadIdAddress != 0 && memory.isBacked(threadIdAddress, Long.BYTES)) {
            synchronized (threadLock) {
                memory.writeLong(threadIdAddress, 0);
                futexWakeLocked(threadIdAddress, 1, FUTEX_BITSET_MATCH_ANY);
            }
        }
        exitThread(state, 0);
    }

    /// Accepts FreeBSD thread-directed signal requests for live guest threads.
    private long freeBsdThrKill(long threadId, long signalNumber) {
        if (!isValidSignalNumber(signalNumber)) {
            return EINVAL;
        }
        if (threadId != 0 && !isKnownGuestThreadId(threadId)) {
            return ESRCH;
        }
        return 0;
    }

    /// Handles FreeBSD `_umtx_op` wait and wake operations needed by Go runtime locks.
    private long freeBsdUmtxOp(long address, long operation, long value, long value2, long timeoutAddress) {
        return switch ((int) operation) {
            case (int) FREEBSD_UMTX_OP_WAIT_UINT, (int) FREEBSD_UMTX_OP_WAIT_UINT_PRIVATE ->
                    freeBsdErrno(futexWait(address, value, timeoutAddress, FUTEX_BITSET_MATCH_ANY, false));
            case (int) FREEBSD_UMTX_OP_WAKE, (int) FREEBSD_UMTX_OP_WAKE_PRIVATE ->
                    futexWake(address, value, FUTEX_BITSET_MATCH_ANY);
            default -> ENOSYS;
        };
    }

    /// Converts Linux errno values returned by shared helpers to FreeBSD errno values when they differ.
    private static long freeBsdErrno(long result) {
        if (result == EAGAIN) {
            return FREEBSD_EAGAIN;
        }
        if (result == ETIMEDOUT) {
            return FREEBSD_ETIMEDOUT;
        }
        return result;
    }

    /// Writes the current FreeBSD resource limit for the guest process.
    private long freeBsdGetrlimit(long resource, long limitAddress) {
        if (limitAddress == 0) {
            return EFAULT;
        }
        int index = freeBsdResourceLimitIndex(resource);
        if (index < 0) {
            return EINVAL;
        }

        writeResourceLimit(limitAddress, resourceLimitCurrent[index], resourceLimitMaximum[index]);
        return 0;
    }

    /// Maps a FreeBSD resource id to the shared resource-limit table index.
    private static int freeBsdResourceLimitIndex(long resource) {
        if (resource == FREEBSD_RLIMIT_NOFILE) {
            return RLIMIT_NOFILE;
        }
        return resource >= 0 && resource < RESOURCE_LIMIT_COUNT ? (int) resource : -1;
    }

    /// Starts a FreeBSD guest thread from `struct thr_param`.
    private long freeBsdThrNew(RiscVThreadState state, long parameterAddress, long size) {
        if (parameterAddress == 0 || size < FREEBSD_THR_PARAM_MINIMUM_SIZE) {
            return EINVAL;
        }
        if (!memory.isBacked(parameterAddress, FREEBSD_THR_PARAM_MINIMUM_SIZE)) {
            return EFAULT;
        }
        if (!guestThreadingEnabled()) {
            return EAGAIN;
        }

        long startFunction = memory.readLong(parameterAddress + FREEBSD_THR_PARAM_START_FUNC_OFFSET);
        long argument = memory.readLong(parameterAddress + FREEBSD_THR_PARAM_ARG_OFFSET);
        long stackBase = memory.readLong(parameterAddress + FREEBSD_THR_PARAM_STACK_BASE_OFFSET);
        long stackSize = memory.readLong(parameterAddress + FREEBSD_THR_PARAM_STACK_SIZE_OFFSET);
        long tlsBase = memory.readLong(parameterAddress + FREEBSD_THR_PARAM_TLS_BASE_OFFSET);
        long childTidAddress = memory.readLong(parameterAddress + FREEBSD_THR_PARAM_CHILD_TID_OFFSET);
        long parentTidAddress = memory.readLong(parameterAddress + FREEBSD_THR_PARAM_PARENT_TID_OFFSET);
        if (startFunction == 0 || stackBase == 0 || stackSize <= 0 || stackBase > Long.MAX_VALUE - stackSize) {
            return EINVAL;
        }

        GuestThread childThread;
        synchronized (threadLock) {
            if (processExitRequested || threadFailure != null) {
                return EAGAIN;
            }
            childThread = processRegistry.createChildThread(process);
            if (childThread == null) {
                return ENOMEM;
            }
            childThread.setSignalMask(state.guestThread().signalMask());
            childThread.inheritExecutionControlsFrom(state.guestThread());
        }
        long threadId = childThread.id();

        RiscVThreadState child = state.forkForClone(childThread, startFunction, stackBase + stackSize, tlsBase, true);
        child.setRegister(10, argument);
        if (childTidAddress != 0) {
            childThread.setClearChildTidAddress(childTidAddress);
        }

        GuestThreadRunner currentRunner = guestThreadRunner;
        if (currentRunner == null) {
            return EAGAIN;
        }
        Thread thread;
        try {
            thread = new Thread(
                    () -> currentRunner.runGuestThread(memory, child),
                    "riscv-guest-thread-" + threadId);
        } catch (RuntimeException exception) {
            return EAGAIN;
        }
        thread.setUncaughtExceptionHandler((failedThread, throwable) -> recordThreadFailure(throwable));

        if (parentTidAddress != 0) {
            memory.writeLong(parentTidAddress, threadId);
        }
        if (childTidAddress != 0) {
            memory.writeLong(childTidAddress, threadId);
        }

        synchronized (threadLock) {
            if (processExitRequested || threadFailure != null) {
                return EAGAIN;
            }
            liveThreadCount++;
            guestThreads.add(thread);
            process.registerThread(childThread);
            processStatusPollingRequired = true;
        }

        try {
            thread.start();
        } catch (RuntimeException exception) {
            unregisterUnstartedGuestThread(thread, childThread);
            return EAGAIN;
        }
        return 0;
    }

    /// Registers or reports the FreeBSD alternate signal stack for the current guest thread.
    private long freeBsdSigaltstack(RiscVThreadState state, long stackAddress, long oldStackAddress) {
        long newStackPointer = 0;
        long newStackSize = 0;
        long newStackFlags = 0;
        if (stackAddress != 0) {
            newStackPointer = memory.readLong(stackAddress + FREEBSD_SIGNAL_STACK_POINTER_OFFSET);
            newStackSize = memory.readLong(stackAddress + FREEBSD_SIGNAL_STACK_SIZE_OFFSET);
            newStackFlags = Integer.toUnsignedLong(memory.readInt(stackAddress + FREEBSD_SIGNAL_STACK_FLAGS_OFFSET));
            if ((newStackFlags & ~FREEBSD_SS_DISABLE) != 0 || newStackSize < 0) {
                return EINVAL;
            }
            if ((newStackFlags & FREEBSD_SS_DISABLE) == 0 && newStackSize < MINIMUM_SIGNAL_STACK_SIZE) {
                return ENOMEM;
            }
        }

        if (oldStackAddress != 0) {
            writeFreeBsdSignalStack(state.guestThread(), oldStackAddress);
        }
        if (stackAddress != 0) {
            if ((newStackFlags & FREEBSD_SS_DISABLE) != 0) {
                state.guestThread().disableAlternateSignalStack();
            } else {
                state.guestThread().setAlternateSignalStack(newStackPointer, newStackSize, 0);
            }
        }
        return 0;
    }

    /// Writes the current FreeBSD RISC-V `stack_t` alternate signal stack.
    private void writeFreeBsdSignalStack(GuestThread thread, long stackAddress) {
        memory.writeLong(stackAddress + FREEBSD_SIGNAL_STACK_POINTER_OFFSET, thread.alternateSignalStackPointer());
        memory.writeLong(stackAddress + FREEBSD_SIGNAL_STACK_SIZE_OFFSET, thread.alternateSignalStackSize());
        memory.writeInt(
                stackAddress + FREEBSD_SIGNAL_STACK_FLAGS_OFFSET,
                thread.alternateSignalStackSize() == 0 ? (int) FREEBSD_SS_DISABLE : 0);
    }

    /// Translates FreeBSD open flags to the Linux-style internal flag set.
    private static long freeBsdOpenFlagsToLinux(long freeBsdFlags) {
        long flags = freeBsdFlags & FREEBSD_O_ACCMODE;
        if ((freeBsdFlags & FREEBSD_O_NONBLOCK) != 0) {
            flags |= O_NONBLOCK;
        }
        if ((freeBsdFlags & FREEBSD_O_APPEND) != 0) {
            flags |= O_APPEND;
        }
        if ((freeBsdFlags & FREEBSD_O_CREAT) != 0) {
            flags |= O_CREAT;
        }
        if ((freeBsdFlags & FREEBSD_O_TRUNC) != 0) {
            flags |= O_TRUNC;
        }
        if ((freeBsdFlags & FREEBSD_O_EXCL) != 0) {
            flags |= O_EXCL;
        }
        if ((freeBsdFlags & FREEBSD_O_DIRECTORY) != 0) {
            flags |= O_DIRECTORY;
        }
        if ((freeBsdFlags & FREEBSD_O_CLOEXEC) != 0) {
            flags |= O_CLOEXEC;
        }
        return flags;
    }

    /// Translates FreeBSD `*at` flags to the Linux-style internal flag set.
    private static long freeBsdAtFlagsToLinux(long freeBsdFlags) {
        long flags = 0;
        if ((freeBsdFlags & FREEBSD_AT_EACCESS) != 0) {
            flags |= AT_EACCESS;
        }
        if ((freeBsdFlags & FREEBSD_AT_SYMLINK_NOFOLLOW) != 0) {
            flags |= AT_SYMLINK_NOFOLLOW;
        }
        if ((freeBsdFlags & FREEBSD_AT_REMOVEDIR) != 0) {
            flags |= AT_REMOVEDIR;
        }
        if ((freeBsdFlags & FREEBSD_AT_EMPTY_PATH) != 0) {
            flags |= AT_EMPTY_PATH;
        }
        return flags;
    }

    /// Translates FreeBSD `mmap` flags to the Linux-style internal flag set.
    private static long freeBsdMmapFlagsToLinux(long freeBsdFlags) {
        long flags = freeBsdFlags & (MAP_SHARED | MAP_PRIVATE | MAP_FIXED);
        if ((freeBsdFlags & FREEBSD_MAP_ANON) != 0) {
            flags |= MAP_ANONYMOUS;
        }
        if ((freeBsdFlags & FREEBSD_MAP_EXCL) != 0) {
            flags |= MAP_FIXED_NOREPLACE;
        }
        return flags;
    }

    /// Translates FreeBSD `fcntl` commands to the Linux-style internal command set.
    private static long freeBsdFcntlCommandToLinux(long command) {
        if (command == 17) {
            return F_DUPFD_CLOEXEC;
        }
        return command;
    }

    /// Creates a child-process FreeBSD syscall handler.
    @Override
    protected GuestSyscalls createChildSyscalls(Memory childMemory, GuestProcess childProcess) {
        return new FreeBsdGuestSyscalls(this, childMemory, childProcess);
    }
}
