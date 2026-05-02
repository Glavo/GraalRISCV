// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import org.glavo.riscv.exception.ProgramExitException;
import org.glavo.riscv.constants.RiscVExtensions;
import org.glavo.riscv.constants.Rva22Profile;
import org.glavo.riscv.constants.Rva23Profile;
import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.Memory;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/// Handles the Linux-compatible syscall subset exposed by the simulator.
@NotNullByDefault
public final class LinuxGuestSyscalls extends GuestSyscalls {
    /// Creates a Linux syscall handler backed by the supplied host streams and heap boundary.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak) {
        super(memory, in, out, err, initialProgramBreak);
    }

    /// Creates a Linux syscall handler backed by the supplied host streams, heap boundary, and resolved root mount.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            @Nullable TruffleFile hostRoot) {
        super(memory, in, out, err, initialProgramBreak, hostRoot);
    }

    /// Creates a Linux syscall handler backed by the supplied streams, resolved root mount, and guest time source.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            @Nullable TruffleFile hostRoot,
            TimeSource timeSource) {
        super(memory, in, out, err, initialProgramBreak, hostRoot, timeSource);
    }

    /// Creates a Linux syscall handler backed by the supplied streams, filesystem namespace, and guest time source.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            GuestFileSystem fileSystem,
            TimeSource timeSource) {
        super(memory, in, out, err, initialProgramBreak, fileSystem, timeSource);
    }

    /// Creates a Linux syscall handler backed by streams, filesystem namespace, guest time source, and credentials.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            GuestFileSystem fileSystem,
            TimeSource timeSource,
            GuestCredentials credentials) {
        super(memory, in, out, err, initialProgramBreak, fileSystem, timeSource, credentials);
    }

    /// Creates a Linux syscall handler backed by the supplied host streams, heap boundary, and lazy root mount.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String hostRootPath) {
        super(memory, in, out, err, initialProgramBreak, env, hostRootPath);
    }

    /// Creates a Linux syscall handler backed by the supplied streams, lazy root mount, and guest time source.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String hostRootPath,
            TimeSource timeSource) {
        super(memory, in, out, err, initialProgramBreak, env, hostRootPath, timeSource);
    }

    /// Creates a Linux syscall handler backed by streams, lazy root mount, guest time source, and guest thread runner.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String hostRootPath,
            TimeSource timeSource,
            GuestThreadRunner guestThreadRunner) {
        super(memory, in, out, err, initialProgramBreak, env, hostRootPath, timeSource, guestThreadRunner);
    }

    /// Creates a Linux syscall handler backed by streams, lazy root mount, guest time source, terminal option,
    /// and guest thread runner.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String hostRootPath,
            TimeSource timeSource,
            boolean useHostTty,
            GuestThreadRunner guestThreadRunner) {
        super(memory, in, out, err, initialProgramBreak, env, hostRootPath, timeSource, useHostTty, guestThreadRunner);
    }

    /// Creates a Linux syscall handler backed by the supplied streams, lazy filesystem mounts, and guest time source.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String @Unmodifiable [] filesystemMountSpecs,
            TimeSource timeSource) {
        super(memory, in, out, err, initialProgramBreak, env, filesystemMountSpecs, timeSource);
    }

    /// Creates a Linux syscall handler backed by streams, lazy filesystem mounts, guest time source,
    /// and guest thread runner.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String @Unmodifiable [] filesystemMountSpecs,
            TimeSource timeSource,
            GuestThreadRunner guestThreadRunner) {
        super(memory, in, out, err, initialProgramBreak, env, filesystemMountSpecs, timeSource, guestThreadRunner);
    }

    /// Creates a Linux syscall handler backed by streams, lazy filesystem mounts, guest time source,
    /// terminal option, and guest thread runner.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String @Unmodifiable [] filesystemMountSpecs,
            TimeSource timeSource,
            boolean useHostTty,
            GuestThreadRunner guestThreadRunner) {
        super(
                memory,
                in,
                out,
                err,
                initialProgramBreak,
                env,
                filesystemMountSpecs,
                timeSource,
                useHostTty,
                guestThreadRunner);
    }

    /// Creates a Linux syscall handler backed by streams, lazy filesystem mounts, time source, credentials,
    /// terminal option, and guest thread runner.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
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
                env,
                filesystemMountSpecs,
                timeSource,
                useHostTty,
                credentials,
                guestThreadRunner);
    }

    /// Linux `CLONE_VM`.
    private static final long CLONE_VM = 0x00000100L;

    /// Linux `CSIGNAL` clone exit-signal mask.
    private static final long CLONE_EXIT_SIGNAL_MASK = 0xffL;

    /// Linux `SIGCHLD`.
    private static final long SIGNAL_CHILD = 17L;

    /// Linux `CLONE_FS`.
    private static final long CLONE_FS = 0x00000200L;

    /// Linux `CLONE_FILES`.
    private static final long CLONE_FILES = 0x00000400L;

    /// Linux `CLONE_SIGHAND`.
    private static final long CLONE_SIGHAND = 0x00000800L;

    /// Linux `CLONE_PIDFD`.
    private static final long CLONE_PIDFD = 0x00001000L;

    /// Linux `CLONE_THREAD`.
    private static final long CLONE_THREAD = 0x00010000L;

    /// Linux `CLONE_SYSVSEM`.
    private static final long CLONE_SYSVSEM = 0x00040000L;

    /// Linux `CLONE_SETTLS`.
    private static final long CLONE_SETTLS = 0x00080000L;

    /// Linux `CLONE_PARENT_SETTID`.
    private static final long CLONE_PARENT_SETTID = 0x00100000L;

    /// Linux `CLONE_CHILD_CLEARTID`.
    private static final long CLONE_CHILD_CLEARTID = 0x00200000L;

    /// Linux `CLONE_DETACHED`.
    private static final long CLONE_DETACHED = 0x00400000L;

    /// Linux `CLONE_CHILD_SETTID`.
    private static final long CLONE_CHILD_SETTID = 0x01000000L;

    /// Linux clone flags required for the supported thread-style parent return path.
    private static final long REQUIRED_THREAD_CLONE_FLAGS =
            CLONE_VM | CLONE_SIGHAND | CLONE_THREAD;

    /// Linux clone flags accepted by the supported thread-style parent return path.
    private static final long SUPPORTED_THREAD_CLONE_FLAGS =
            REQUIRED_THREAD_CLONE_FLAGS
                    | CLONE_FS
                    | CLONE_FILES
                    | CLONE_SYSVSEM
                    | CLONE_SETTLS
                    | CLONE_PARENT_SETTID
                    | CLONE_CHILD_CLEARTID
                    | CLONE_DETACHED
                    | CLONE_CHILD_SETTID;

    /// Clone flags accepted for independent child-process creation.
    private static final long SUPPORTED_PROCESS_CLONE_FLAGS =
            CLONE_EXIT_SIGNAL_MASK
                    | CLONE_FS
                    | CLONE_FILES
                    | CLONE_SYSVSEM
                    | CLONE_SETTLS
                    | CLONE_PARENT_SETTID
                    | CLONE_CHILD_CLEARTID
                    | CLONE_CHILD_SETTID;

    /// The minimum supported Linux `struct clone_args` byte size.
    private static final long CLONE_ARGS_MINIMUM_SIZE = 64;

    /// The known Linux `struct clone_args` byte size.
    private static final long CLONE_ARGS_KNOWN_SIZE = 88;

    /// The byte offset of `flags` inside `struct clone_args`.
    private static final long CLONE_ARGS_FLAGS_OFFSET = 0;

    /// The byte offset of `child_tid` inside `struct clone_args`.
    private static final long CLONE_ARGS_CHILD_TID_OFFSET = 16;

    /// The byte offset of `parent_tid` inside `struct clone_args`.
    private static final long CLONE_ARGS_PARENT_TID_OFFSET = 24;

    /// The byte offset of `exit_signal` inside `struct clone_args`.
    private static final long CLONE_ARGS_EXIT_SIGNAL_OFFSET = 32;

    /// The byte offset of `stack` inside `struct clone_args`.
    private static final long CLONE_ARGS_STACK_OFFSET = 40;

    /// The byte offset of `stack_size` inside `struct clone_args`.
    private static final long CLONE_ARGS_STACK_SIZE_OFFSET = 48;

    /// The byte offset of `tls` inside `struct clone_args`.
    private static final long CLONE_ARGS_TLS_OFFSET = 56;

    /// The byte offset of `set_tid` inside `struct clone_args`.
    private static final long CLONE_ARGS_SET_TID_OFFSET = 64;

    /// The byte offset of `set_tid_size` inside `struct clone_args`.
    private static final long CLONE_ARGS_SET_TID_SIZE_OFFSET = 72;

    /// The byte offset of `cgroup` inside `struct clone_args`.
    private static final long CLONE_ARGS_CGROUP_OFFSET = 80;

    /// The byte size of Linux `struct riscv_hwprobe`.
    private static final long RISCV_HWPROBE_PAIR_SIZE = 16;

    /// The byte offset of `key` inside `struct riscv_hwprobe`.
    private static final long RISCV_HWPROBE_KEY_OFFSET = 0;

    /// The byte offset of `value` inside `struct riscv_hwprobe`.
    private static final long RISCV_HWPROBE_VALUE_OFFSET = Long.BYTES;

    /// Linux `RISCV_HWPROBE_WHICH_CPUS`.
    private static final long RISCV_HWPROBE_WHICH_CPUS = 1;

    /// Linux `RISCV_HWPROBE_KEY_MVENDORID`.
    private static final long RISCV_HWPROBE_KEY_MVENDORID = 0;

    /// Linux `RISCV_HWPROBE_KEY_MARCHID`.
    private static final long RISCV_HWPROBE_KEY_MARCHID = 1;

    /// Linux `RISCV_HWPROBE_KEY_MIMPID`.
    private static final long RISCV_HWPROBE_KEY_MIMPID = 2;

    /// Linux `RISCV_HWPROBE_KEY_BASE_BEHAVIOR`.
    private static final long RISCV_HWPROBE_KEY_BASE_BEHAVIOR = 3;

    /// Linux `RISCV_HWPROBE_BASE_BEHAVIOR_IMA`.
    private static final long RISCV_HWPROBE_BASE_BEHAVIOR_IMA = 1;

    /// Linux `RISCV_HWPROBE_KEY_IMA_EXT_0`.
    private static final long RISCV_HWPROBE_KEY_IMA_EXT_0 = 4;

    /// Linux `RISCV_HWPROBE_KEY_CPUPERF_0`.
    private static final long RISCV_HWPROBE_KEY_CPUPERF_0 = 5;

    /// Linux `RISCV_HWPROBE_MISALIGNED_EMULATED`.
    private static final long RISCV_HWPROBE_MISALIGNED_EMULATED = 1;

    /// Linux `RISCV_HWPROBE_KEY_ZICBOZ_BLOCK_SIZE`.
    private static final long RISCV_HWPROBE_KEY_ZICBOZ_BLOCK_SIZE = 6;

    /// Linux `RISCV_HWPROBE_KEY_HIGHEST_VIRT_ADDRESS`.
    private static final long RISCV_HWPROBE_KEY_HIGHEST_VIRT_ADDRESS = 7;

    /// Linux `RISCV_HWPROBE_KEY_TIME_CSR_FREQ`.
    private static final long RISCV_HWPROBE_KEY_TIME_CSR_FREQ = 8;

    /// Linux `RISCV_HWPROBE_KEY_MISALIGNED_SCALAR_PERF`.
    private static final long RISCV_HWPROBE_KEY_MISALIGNED_SCALAR_PERF = 9;

    /// Linux `RISCV_HWPROBE_MISALIGNED_SCALAR_EMULATED`.
    private static final long RISCV_HWPROBE_MISALIGNED_SCALAR_EMULATED = 1;

    /// Linux `RISCV_HWPROBE_KEY_MISALIGNED_VECTOR_PERF`.
    private static final long RISCV_HWPROBE_KEY_MISALIGNED_VECTOR_PERF = 10;

    /// Linux `RISCV_HWPROBE_MISALIGNED_VECTOR_SLOW`.
    private static final long RISCV_HWPROBE_MISALIGNED_VECTOR_SLOW = 2;

    /// Linux `RISCV_HWPROBE_KEY_VENDOR_EXT_THEAD_0`.
    private static final long RISCV_HWPROBE_KEY_VENDOR_EXT_THEAD_0 = 11;

    /// Linux `RISCV_HWPROBE_KEY_ZICBOM_BLOCK_SIZE`.
    private static final long RISCV_HWPROBE_KEY_ZICBOM_BLOCK_SIZE = 12;

    /// Linux `RISCV_HWPROBE_KEY_VENDOR_EXT_SIFIVE_0`.
    private static final long RISCV_HWPROBE_KEY_VENDOR_EXT_SIFIVE_0 = 13;

    /// Linux `RISCV_HWPROBE_KEY_VENDOR_EXT_MIPS_0`.
    private static final long RISCV_HWPROBE_KEY_VENDOR_EXT_MIPS_0 = 14;

    /// Linux `RISCV_HWPROBE_KEY_ZICBOP_BLOCK_SIZE`.
    private static final long RISCV_HWPROBE_KEY_ZICBOP_BLOCK_SIZE = 15;

    /// Linux `MEMBARRIER_CMD_QUERY`.
    private static final long MEMBARRIER_CMD_QUERY = 0;

    /// The original Linux `struct rseq` byte size including padding.
    private static final long RSEQ_ORIGINAL_SIZE = 32;

    /// The Linux `struct rseq` alignment used for the original 32-byte layout.
    private static final long RSEQ_ALIGNMENT = 32;

    /// Linux `RSEQ_FLAG_UNREGISTER`.
    private static final long RSEQ_FLAG_UNREGISTER = 1;

    /// The byte offset of `cpu_id_start` inside Linux `struct rseq`.
    private static final long RSEQ_CPU_ID_START_OFFSET = 0;

    /// The byte offset of `cpu_id` inside Linux `struct rseq`.
    private static final long RSEQ_CPU_ID_OFFSET = Integer.BYTES;

    /// The byte offset of `rseq_cs` inside Linux `struct rseq`.
    private static final long RSEQ_CRITICAL_SECTION_OFFSET = 2L * Integer.BYTES;

    /// The byte offset of `flags` inside Linux `struct rseq`.
    private static final long RSEQ_FLAGS_OFFSET = RSEQ_CRITICAL_SECTION_OFFSET + Long.BYTES;

    /// The byte offset of `node_id` inside Linux `struct rseq`.
    private static final long RSEQ_NODE_ID_OFFSET = RSEQ_FLAGS_OFFSET + Integer.BYTES;

    /// The byte offset of `mm_cid` inside Linux `struct rseq`.
    private static final long RSEQ_MEMORY_MAP_CONCURRENCY_ID_OFFSET = RSEQ_NODE_ID_OFFSET + Integer.BYTES;

    /// Linux `PR_SET_PDEATHSIG`.
    private static final long PR_SET_PDEATHSIG = 1;

    /// Linux `PR_GET_PDEATHSIG`.
    private static final long PR_GET_PDEATHSIG = 2;

    /// Linux `PR_GET_DUMPABLE`.
    private static final long PR_GET_DUMPABLE = 3;

    /// Linux `PR_SET_DUMPABLE`.
    private static final long PR_SET_DUMPABLE = 4;

    /// Linux `PR_SET_NAME`.
    private static final long PR_SET_NAME = 15;

    /// Linux `PR_GET_NAME`.
    private static final long PR_GET_NAME = 16;

    /// Linux `PR_SET_TIMERSLACK`.
    private static final long PR_SET_TIMERSLACK = 29;

    /// Linux `PR_GET_TIMERSLACK`.
    private static final long PR_GET_TIMERSLACK = 30;

    /// Linux `PR_SET_CHILD_SUBREAPER`.
    private static final long PR_SET_CHILD_SUBREAPER = 36;

    /// Linux `PR_GET_CHILD_SUBREAPER`.
    private static final long PR_GET_CHILD_SUBREAPER = 37;

    /// Linux `PR_SET_NO_NEW_PRIVS`.
    private static final long PR_SET_NO_NEW_PRIVS = 38;

    /// Linux `PR_GET_NO_NEW_PRIVS`.
    private static final long PR_GET_NO_NEW_PRIVS = 39;

    /// Linux `PR_GET_TID_ADDRESS`.
    private static final long PR_GET_TID_ADDRESS = 40;

    /// Linux `PR_SET_THP_DISABLE`.
    private static final long PR_SET_THP_DISABLE = 41;

    /// Linux `PR_GET_THP_DISABLE`.
    private static final long PR_GET_THP_DISABLE = 42;

    /// Linux `PR_SET_TAGGED_ADDR_CTRL`.
    private static final long PR_SET_TAGGED_ADDR_CTRL = 55;

    /// Linux `PR_GET_TAGGED_ADDR_CTRL`.
    private static final long PR_GET_TAGGED_ADDR_CTRL = 56;

    /// Linux `PR_GET_AUXV`.
    private static final long PR_GET_AUXV = 0x4155_5856L;

    /// Linux `PR_TAGGED_ADDR_ENABLE`.
    private static final long PR_TAGGED_ADDR_ENABLE = 1;

    /// Linux `PR_PMLEN_SHIFT`.
    private static final long PR_PMLEN_SHIFT = 24;

    /// Linux `PR_PMLEN_MASK`.
    private static final long PR_PMLEN_MASK = 0x7fL << PR_PMLEN_SHIFT;

    /// The default disabled RISC-V userspace pointer mask length.
    private static final int POINTER_MASK_LENGTH_DISABLED = 0;

    /// The RISC-V userspace pointer mask length implemented by this simulator.
    private static final int POINTER_MASK_LENGTH_7 = 7;

    /// Linux `PR_SET_VMA`.
    private static final long PR_SET_VMA = 0x53564d41L;

    /// Linux `PR_SET_VMA_ANON_NAME`.
    private static final long PR_SET_VMA_ANON_NAME = 0;

    /// Handles the parent return path for Linux `clone` requests.
    protected long clone(
            MachineState state,
            long pc,
            long flags,
            long stackAddress,
            long parentTidAddress,
            long tlsAddress,
            long childTidAddress) {
        long exitSignal = flags & CLONE_EXIT_SIGNAL_MASK;
        long controlFlags = flags & ~CLONE_EXIT_SIGNAL_MASK;
        if ((controlFlags & CLONE_THREAD) != 0) {
            if (exitSignal != 0) {
                return EINVAL;
            }
            return cloneThread(state, pc, controlFlags, stackAddress, parentTidAddress, tlsAddress, childTidAddress);
        }
        return cloneProcess(state, pc, flags, stackAddress, parentTidAddress, tlsAddress, childTidAddress);
    }

    /// Handles Linux `clone3` by translating `struct clone_args` to the existing clone implementation.
    protected long clone3(MachineState state, long pc, long argumentsAddress, long size) {
        if (size < CLONE_ARGS_MINIMUM_SIZE) {
            return EINVAL;
        }
        if (!memory.isBacked(argumentsAddress, Math.min(size, CLONE_ARGS_KNOWN_SIZE))) {
            return EFAULT;
        }
        if (size > CLONE_ARGS_KNOWN_SIZE) {
            long extraSize = size - CLONE_ARGS_KNOWN_SIZE;
            if (extraSize > Integer.MAX_VALUE) {
                return E2BIG;
            }
            if (!memory.isBacked(argumentsAddress + CLONE_ARGS_KNOWN_SIZE, extraSize)) {
                return EFAULT;
            }
            byte[] extraBytes = memory.readBytes(argumentsAddress + CLONE_ARGS_KNOWN_SIZE, extraSize);
            for (byte extraByte : extraBytes) {
                if (extraByte != 0) {
                    return E2BIG;
                }
            }
        }

        long flags = memory.readLong(argumentsAddress + CLONE_ARGS_FLAGS_OFFSET);
        long exitSignal = memory.readLong(argumentsAddress + CLONE_ARGS_EXIT_SIGNAL_OFFSET);
        if ((flags & CLONE_EXIT_SIGNAL_MASK) != 0 || (exitSignal & ~CLONE_EXIT_SIGNAL_MASK) != 0) {
            return EINVAL;
        }

        long childTidAddress = memory.readLong(argumentsAddress + CLONE_ARGS_CHILD_TID_OFFSET);
        long parentTidAddress = memory.readLong(argumentsAddress + CLONE_ARGS_PARENT_TID_OFFSET);
        long stackAddress = memory.readLong(argumentsAddress + CLONE_ARGS_STACK_OFFSET);
        long stackSize = memory.readLong(argumentsAddress + CLONE_ARGS_STACK_SIZE_OFFSET);
        long tlsAddress = memory.readLong(argumentsAddress + CLONE_ARGS_TLS_OFFSET);
        long setTidAddress = cloneArgumentLong(argumentsAddress, size, CLONE_ARGS_SET_TID_OFFSET);
        long setTidSize = cloneArgumentLong(argumentsAddress, size, CLONE_ARGS_SET_TID_SIZE_OFFSET);
        long cgroup = cloneArgumentLong(argumentsAddress, size, CLONE_ARGS_CGROUP_OFFSET);
        if ((flags & CLONE_PIDFD) != 0 || setTidAddress != 0 || setTidSize != 0 || cgroup != 0) {
            return EINVAL;
        }

        long childStackAddress = clone3ChildStackAddress(stackAddress, stackSize);
        if (childStackAddress < 0) {
            return EINVAL;
        }
        return clone(
                state,
                pc,
                flags | exitSignal,
                childStackAddress,
                parentTidAddress,
                tlsAddress,
                childTidAddress);
    }

    /// Reads an optional 64-bit `struct clone_args` field when it is present in the supplied size.
    protected long cloneArgumentLong(long argumentsAddress, long size, long offset) {
        return size >= offset + Long.BYTES ? memory.readLong(argumentsAddress + offset) : 0;
    }

    /// Converts a `clone3` stack base and size to the child stack pointer expected by `clone`.
    protected static long clone3ChildStackAddress(long stackAddress, long stackSize) {
        if (stackAddress == 0) {
            return stackSize == 0 ? 0 : -1;
        }
        if (stackSize < 0 || Long.MAX_VALUE - stackAddress < stackSize) {
            return -1;
        }
        return stackSize == 0 ? stackAddress : stackAddress + stackSize;
    }

    /// Handles the parent return path for Linux thread-style `clone` requests.
    protected long cloneThread(
            MachineState state,
            long pc,
            long flags,
            long stackAddress,
            long parentTidAddress,
            long tlsAddress,
            long childTidAddress) {
        if (stackAddress == 0
                || (flags & REQUIRED_THREAD_CLONE_FLAGS) != REQUIRED_THREAD_CLONE_FLAGS
                || (flags & ~SUPPORTED_THREAD_CLONE_FLAGS) != 0) {
            return EINVAL;
        }
        if (!guestThreadingEnabled()) {
            return EAGAIN;
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

        MachineState child = state.forkForClone(
                childThread,
                pc + Integer.BYTES,
                stackAddress,
                tlsAddress,
                (flags & CLONE_SETTLS) != 0);
        if ((flags & CLONE_CHILD_CLEARTID) != 0) {
            childThread.setClearChildTidAddress(childTidAddress);
        }

        TruffleLanguage.Env currentEnv = env;
        GuestThreadRunner currentRunner = guestThreadRunner;
        Thread thread;
        try {
            thread = currentEnv.newTruffleThreadBuilder(() -> currentRunner.runGuestThread(memory, child)).build();
        } catch (RuntimeException exception) {
            return EAGAIN;
        }
        thread.setUncaughtExceptionHandler((failedThread, throwable) -> recordThreadFailure(throwable));

        if ((flags & CLONE_PARENT_SETTID) != 0) {
            memory.writeInt(parentTidAddress, (int) threadId);
        }
        if ((flags & CLONE_CHILD_SETTID) != 0) {
            memory.writeInt(childTidAddress, (int) threadId);
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
        return threadId;
    }

    /// Handles the parent return path for Linux process-style `clone` requests.
    protected long cloneProcess(
            MachineState state,
            long pc,
            long flags,
            long stackAddress,
            long parentTidAddress,
            long tlsAddress,
            long childTidAddress) {
        long exitSignal = flags & CLONE_EXIT_SIGNAL_MASK;
        if ((flags & ~SUPPORTED_PROCESS_CLONE_FLAGS) != 0
                || (flags & CLONE_VM) != 0
                || exitSignal != SIGNAL_CHILD && exitSignal != 0) {
            return EINVAL;
        }
        if (!guestThreadingEnabled()) {
            return EAGAIN;
        }
        synchronized (threadLock) {
            if (processExitRequested || threadFailure != null) {
                return EAGAIN;
            }
        }

        @Nullable GuestProcess childProcess = processRegistry.createChildProcess(process);
        if (childProcess == null) {
            return ENOMEM;
        }

        Memory childMemory;
        try {
            childMemory = memory.fork();
        } catch (RiscVException exception) {
            return ENOMEM;
        }

        GuestSyscalls childSyscalls = createChildSyscalls(childMemory, childProcess);
        GuestThread childThread = childProcess.initialThread();
        childThread.setSignalMask(state.guestThread().signalMask());
        childThread.inheritExecutionControlsFrom(state.guestThread());
        if ((flags & CLONE_CHILD_CLEARTID) != 0) {
            childThread.setClearChildTidAddress(childTidAddress);
        }

        long childStackAddress = stackAddress == 0 ? state.register(2) : stackAddress;
        MachineState child = state.forkForProcess(
                childThread,
                childMemory,
                childSyscalls,
                pc + Integer.BYTES,
                childStackAddress,
                tlsAddress,
                (flags & CLONE_SETTLS) != 0);

        if ((flags & CLONE_PARENT_SETTID) != 0) {
            memory.writeInt(parentTidAddress, childProcess.id());
        }
        if ((flags & CLONE_CHILD_SETTID) != 0) {
            childMemory.writeInt(childTidAddress, childProcess.id());
        }

        TruffleLanguage.Env currentEnv = env;
        GuestThreadRunner currentRunner = guestThreadRunner;
        Thread thread;
        try {
            thread = currentEnv.newTruffleThreadBuilder(() -> {
                try {
                    currentRunner.runGuestThread(childMemory, child);
                } finally {
                    childSyscalls.joinGuestThreads();
                    childSyscalls.close();
                    childMemory.close();
                }
            }).build();
        } catch (RuntimeException exception) {
            childSyscalls.close();
            childMemory.close();
            return EAGAIN;
        }
        thread.setUncaughtExceptionHandler((failedThread, throwable) -> childSyscalls.recordThreadFailure(throwable));

        ChildProcess childRecord = new ChildProcess(childProcess.id(), childProcess.processGroupId(), childSyscalls, thread);
        synchronized (childProcessLock) {
            childProcesses.add(childRecord);
        }

        try {
            thread.start();
        } catch (RuntimeException exception) {
            removeUnstartedChildProcess(childRecord);
            childSyscalls.close();
            childMemory.close();
            return EAGAIN;
        }
        return childProcess.id();
    }


    /// Accepts a robust futex list registration for the current guest thread.
    protected static long setRobustList(MachineState state, long headAddress, long length) {
        if (length < 0) {
            return EINVAL;
        }
        state.guestThread().setRobustList(headAddress, length);
        return 0;
    }

    /// Reports the robust futex list registered for one guest thread.
    protected long getRobustList(MachineState state, long processId, long headAddress, long lengthAddress) {
        @Nullable GuestThread thread = guestThread(state, processId);
        if (thread == null) {
            return ESRCH;
        }
        memory.writeLong(headAddress, thread.robustListHeadAddress());
        memory.writeLong(lengthAddress, thread.robustListLength());
        return 0;
    }

    /// Registers or reports the alternate signal stack for the current guest thread.
    protected long sigaltstack(MachineState state, long stackAddress, long oldStackAddress) {
        long newStackPointer = 0;
        long newStackSize = 0;
        long newStackFlags = 0;
        if (stackAddress != 0) {
            newStackPointer = memory.readLong(stackAddress + SIGNAL_STACK_POINTER_OFFSET);
            newStackFlags = Integer.toUnsignedLong(memory.readInt(stackAddress + SIGNAL_STACK_FLAGS_OFFSET));
            newStackSize = memory.readLong(stackAddress + SIGNAL_STACK_SIZE_OFFSET);
            if ((newStackFlags & ~SUPPORTED_SIGNAL_STACK_FLAGS) != 0
                    || (newStackFlags & SS_ONSTACK) != 0
                    || newStackSize < 0) {
                return EINVAL;
            }
            if ((newStackFlags & SS_DISABLE) == 0 && newStackSize < MINIMUM_SIGNAL_STACK_SIZE) {
                return ENOMEM;
            }
        }

        if (oldStackAddress != 0) {
            writeSignalStack(state.guestThread(), oldStackAddress);
        }
        if (stackAddress != 0) {
            if ((newStackFlags & SS_DISABLE) != 0) {
                state.guestThread().disableAlternateSignalStack();
            } else {
                state.guestThread().setAlternateSignalStack(newStackPointer, newStackSize, newStackFlags);
            }
        }
        return 0;
    }

    /// Writes the current Linux RISC-V 64-bit `stack_t` alternate signal stack.
    protected void writeSignalStack(GuestThread thread, long stackAddress) {
        memory.writeLong(stackAddress + SIGNAL_STACK_POINTER_OFFSET, thread.alternateSignalStackPointer());
        memory.writeInt(stackAddress + SIGNAL_STACK_FLAGS_OFFSET, (int) thread.alternateSignalStackFlags());
        memory.writeInt(stackAddress + SIGNAL_STACK_FLAGS_PADDING_OFFSET, 0);
        memory.writeLong(stackAddress + SIGNAL_STACK_SIZE_OFFSET, thread.alternateSignalStackSize());
    }


    /// Reports a deterministic single-CPU affinity mask for static libc queries.
    protected long schedGetaffinity(long processId, long cpuSetSize, long cpuSetAddress) {
        if (cpuSetSize < MINIMUM_CPU_AFFINITY_MASK_SIZE) {
            return EINVAL;
        }

        memory.clear(cpuSetAddress, cpuSetSize);
        memory.writeLong(cpuSetAddress, 1);
        return MINIMUM_CPU_AFFINITY_MASK_SIZE;
    }


    /// Reports deterministic RISC-V hardware probe values for the single simulated CPU.
    protected long riscvHwprobe(long pairsAddress, long pairCount, long cpuSetSize, long cpuSetAddress, long flags, MachineState state) {
        if (pairCount < 0
                || pairCount > Integer.MAX_VALUE
                || (flags & ~RISCV_HWPROBE_WHICH_CPUS) != 0
                || pairCount > Long.MAX_VALUE / RISCV_HWPROBE_PAIR_SIZE
                || (cpuSetAddress == 0 && cpuSetSize != 0)
                || (cpuSetAddress != 0 && cpuSetSize <= 0)) {
            return EINVAL;
        }

        if ((flags & RISCV_HWPROBE_WHICH_CPUS) == 0) {
            if (cpuSetAddress != 0 && !cpuSetContainsGuestCpu(cpuSetAddress)) {
                return EINVAL;
            }
            populateHwprobePairs(pairsAddress, pairCount, state);
            return 0;
        }

        if (cpuSetAddress == 0) {
            return EINVAL;
        }

        boolean selected = cpuSetContainsGuestCpu(cpuSetAddress) || cpuSetIsEmpty(cpuSetAddress, cpuSetSize);
        boolean matches = selected && hwprobePairsMatch(pairsAddress, pairCount, state);
        memory.clear(cpuSetAddress, cpuSetSize);
        if (matches) {
            memory.writeByte(cpuSetAddress, (byte) 1);
        }
        return 0;
    }

    /// Writes values for all requested RISC-V hardware probe pairs.
    protected void populateHwprobePairs(long pairsAddress, long pairCount, MachineState state) {
        for (long index = 0; index < pairCount; index++) {
            long pairAddress = pairsAddress + index * RISCV_HWPROBE_PAIR_SIZE;
            long key = memory.readLong(pairAddress + RISCV_HWPROBE_KEY_OFFSET);
            if (isSupportedHwprobeKey(key)) {
                memory.writeLong(pairAddress + RISCV_HWPROBE_VALUE_OFFSET, hwprobeValue(key, state));
            } else {
                memory.writeLong(pairAddress + RISCV_HWPROBE_KEY_OFFSET, -1);
                memory.writeLong(pairAddress + RISCV_HWPROBE_VALUE_OFFSET, 0);
            }
        }
    }

    /// Returns true when all supplied RISC-V hardware probe constraints match the simulated CPU.
    protected boolean hwprobePairsMatch(long pairsAddress, long pairCount, MachineState state) {
        boolean matches = true;
        for (long index = 0; index < pairCount; index++) {
            long pairAddress = pairsAddress + index * RISCV_HWPROBE_PAIR_SIZE;
            long key = memory.readLong(pairAddress + RISCV_HWPROBE_KEY_OFFSET);
            long requestedValue = memory.readLong(pairAddress + RISCV_HWPROBE_VALUE_OFFSET);
            if (!isSupportedHwprobeKey(key)) {
                memory.writeLong(pairAddress + RISCV_HWPROBE_KEY_OFFSET, -1);
                memory.writeLong(pairAddress + RISCV_HWPROBE_VALUE_OFFSET, 0);
                matches = false;
                continue;
            }

            long actualValue = hwprobeValue(key, state);
            if (isHwprobeBitmaskKey(key)) {
                if ((actualValue & requestedValue) != requestedValue) {
                    matches = false;
                }
            } else if (actualValue != requestedValue) {
                matches = false;
            }
        }
        return matches;
    }

    /// Returns true when a hardware probe key is supported by this simulator.
    protected static boolean isSupportedHwprobeKey(long key) {
        return key >= RISCV_HWPROBE_KEY_MVENDORID && key <= RISCV_HWPROBE_KEY_ZICBOP_BLOCK_SIZE;
    }

    /// Returns the deterministic value for a supported RISC-V hardware probe key.
    protected static long hwprobeValue(long key, MachineState state) {
        return switch ((int) key) {
            case (int) RISCV_HWPROBE_KEY_MVENDORID,
                    (int) RISCV_HWPROBE_KEY_MARCHID,
                    (int) RISCV_HWPROBE_KEY_MIMPID,
                    (int) RISCV_HWPROBE_KEY_VENDOR_EXT_THEAD_0,
                    (int) RISCV_HWPROBE_KEY_VENDOR_EXT_SIFIVE_0,
                    (int) RISCV_HWPROBE_KEY_VENDOR_EXT_MIPS_0 -> 0;
            case (int) RISCV_HWPROBE_KEY_ZICBOZ_BLOCK_SIZE,
                    (int) RISCV_HWPROBE_KEY_ZICBOM_BLOCK_SIZE,
                    (int) RISCV_HWPROBE_KEY_ZICBOP_BLOCK_SIZE -> RiscVExtensions.CACHE_BLOCK_SIZE;
            case (int) RISCV_HWPROBE_KEY_BASE_BEHAVIOR -> RISCV_HWPROBE_BASE_BEHAVIOR_IMA;
            case (int) RISCV_HWPROBE_KEY_IMA_EXT_0 -> hwprobeImaExtensions(state);
            case (int) RISCV_HWPROBE_KEY_CPUPERF_0 -> RISCV_HWPROBE_MISALIGNED_EMULATED;
            case (int) RISCV_HWPROBE_KEY_MISALIGNED_SCALAR_PERF -> RISCV_HWPROBE_MISALIGNED_SCALAR_EMULATED;
            case (int) RISCV_HWPROBE_KEY_MISALIGNED_VECTOR_PERF -> RISCV_HWPROBE_MISALIGNED_VECTOR_SLOW;
            case (int) RISCV_HWPROBE_KEY_HIGHEST_VIRT_ADDRESS -> Long.MAX_VALUE;
            case (int) RISCV_HWPROBE_KEY_TIME_CSR_FREQ -> NANOSECONDS_PER_SECOND;
            default -> throw new AssertionError("validated RISC-V hardware probe key");
        };
    }

    /// Returns the ISA extension bits visible through Linux `riscv_hwprobe`.
    protected static long hwprobeImaExtensions(MachineState state) {
        return state.vectorUnit().vlenBits() >= Rva23Profile.MINIMUM_VLEN_BITS
                ? Rva23Profile.HWPROBE_REPORTED_EXTENSIONS
                : Rva22Profile.HWPROBE_REPORTED_EXTENSIONS;
    }

    /// Returns true when a hardware probe key uses bitmask matching.
    protected static boolean isHwprobeBitmaskKey(long key) {
        return key == RISCV_HWPROBE_KEY_BASE_BEHAVIOR
                || key == RISCV_HWPROBE_KEY_IMA_EXT_0
                || key == RISCV_HWPROBE_KEY_CPUPERF_0
                || key == RISCV_HWPROBE_KEY_VENDOR_EXT_THEAD_0
                || key == RISCV_HWPROBE_KEY_VENDOR_EXT_SIFIVE_0
                || key == RISCV_HWPROBE_KEY_VENDOR_EXT_MIPS_0;
    }

    /// Returns true when the supplied CPU set contains the simulator's only CPU.
    protected boolean cpuSetContainsGuestCpu(long cpuSetAddress) {
        return (memory.readUnsignedByte(cpuSetAddress) & 1) != 0;
    }

    /// Returns true when all bytes in a guest CPU set are zero.
    protected boolean cpuSetIsEmpty(long cpuSetAddress, long cpuSetSize) {
        for (long index = 0; index < cpuSetSize; index++) {
            if (memory.readUnsignedByte(cpuSetAddress + index) != 0) {
                return false;
            }
        }
        return true;
    }


    /// Accepts signal sends that target known guest thread ids.
    protected long tkill(long threadId, long signalNumber) {
        if (!isValidSignalNumber(signalNumber)) {
            return EINVAL;
        }
        return isKnownGuestThreadId(threadId) ? 0 : ESRCH;
    }

    /// Accepts signal sends that target known guest thread ids in the current process.
    protected long tgkill(long processId, long threadId, long signalNumber) {
        if (!isValidSignalNumber(signalNumber)) {
            return EINVAL;
        }
        if (processId != process.id()) {
            return ESRCH;
        }
        return isKnownGuestThreadId(threadId) ? 0 : ESRCH;
    }


    /// Returns the process group id for the current process or a tracked child process.
    protected long getpgid(long processId) {
        if (processId == 0 || processId == process.id()) {
            return process.processGroupId();
        }
        if (processId != (int) processId) {
            return ESRCH;
        }
        synchronized (childProcessLock) {
            @Nullable ChildProcess child = childProcess((int) processId);
            if (child != null) {
                return child.processGroupId();
            }
        }
        return ESRCH;
    }


    /// Writes deterministic process CPU times and returns elapsed clock ticks.
    protected long times(long tmsAddress) {
        long elapsedTicks = elapsedClockTicks();
        if (tmsAddress != 0) {
            memory.clear(tmsAddress, TMS_SIZE);
            memory.writeLong(tmsAddress + TMS_USER_TIME_OFFSET, elapsedTicks);
            memory.writeLong(tmsAddress + TMS_SYSTEM_TIME_OFFSET, 0);
            memory.writeLong(tmsAddress + TMS_CHILD_USER_TIME_OFFSET, 0);
            memory.writeLong(tmsAddress + TMS_CHILD_SYSTEM_TIME_OFFSET, 0);
        }
        return elapsedTicks;
    }


    /// Writes a deterministic Linux `struct utsname` for the simulated guest.
    protected long uname(long utsnameAddress) {
        memory.clear(utsnameAddress, UTSNAME_SIZE);
        writeNullTerminatedAscii(utsnameAddress + UTSNAME_SYSNAME_OFFSET, "Linux", UTSNAME_FIELD_SIZE);
        writeNullTerminatedAscii(utsnameAddress + UTSNAME_NODENAME_OFFSET, "localhost", UTSNAME_FIELD_SIZE);
        writeNullTerminatedAscii(utsnameAddress + UTSNAME_RELEASE_OFFSET, "6.12.0", UTSNAME_FIELD_SIZE);
        writeNullTerminatedAscii(utsnameAddress + UTSNAME_VERSION_OFFSET, "#1 SMP", UTSNAME_FIELD_SIZE);
        writeNullTerminatedAscii(utsnameAddress + UTSNAME_MACHINE_OFFSET, "riscv64", UTSNAME_FIELD_SIZE);
        writeNullTerminatedAscii(utsnameAddress + UTSNAME_DOMAINNAME_OFFSET, "localdomain", UTSNAME_FIELD_SIZE);
        return 0;
    }

    /// Writes deterministic Linux `struct sysinfo` values for libc and coreutils memory queries.
    protected long sysinfo(long sysinfoAddress) {
        if (sysinfoAddress == 0) {
            return EFAULT;
        }

        long totalRam = memory.size();
        long committedRam = saturatedMultiply(memory.committedPages(), memory.pageSize());
        long freeRam = Math.max(0, totalRam - committedRam);
        short processes = (short) Math.min(Short.toUnsignedInt((short) -1), process.threadCount());

        memory.clear(sysinfoAddress, SYSINFO_SIZE);
        memory.writeLong(sysinfoAddress + SYSINFO_UPTIME_OFFSET, Math.max(0, elapsedDuration().getSeconds()));
        memory.writeLong(sysinfoAddress + SYSINFO_LOADS_OFFSET, 0);
        memory.writeLong(sysinfoAddress + SYSINFO_LOADS_OFFSET + Long.BYTES, 0);
        memory.writeLong(sysinfoAddress + SYSINFO_LOADS_OFFSET + 2L * Long.BYTES, 0);
        memory.writeLong(sysinfoAddress + SYSINFO_TOTAL_RAM_OFFSET, totalRam);
        memory.writeLong(sysinfoAddress + SYSINFO_FREE_RAM_OFFSET, freeRam);
        memory.writeLong(sysinfoAddress + SYSINFO_SHARED_RAM_OFFSET, 0);
        memory.writeLong(sysinfoAddress + SYSINFO_BUFFER_RAM_OFFSET, 0);
        memory.writeLong(sysinfoAddress + SYSINFO_TOTAL_SWAP_OFFSET, 0);
        memory.writeLong(sysinfoAddress + SYSINFO_FREE_SWAP_OFFSET, 0);
        memory.writeShort(sysinfoAddress + SYSINFO_PROCESSES_OFFSET, processes);
        memory.writeLong(sysinfoAddress + SYSINFO_TOTAL_HIGH_OFFSET, 0);
        memory.writeLong(sysinfoAddress + SYSINFO_FREE_HIGH_OFFSET, 0);
        memory.writeInt(sysinfoAddress + SYSINFO_MEMORY_UNIT_OFFSET, 1);
        return 0;
    }

    /// Returns a saturated product of two non-negative byte counts.
    protected static long saturatedMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    /// Writes a null-terminated US-ASCII string into a fixed-size guest field.
    protected void writeNullTerminatedAscii(long address, String value, int fieldSize) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        int length = Math.min(bytes.length, fieldSize - 1);
        memory.writeBytes(address, bytes, 0, length);
    }


    /// Accepts signal action setup for a guest that never delivers host signals.
    protected long rtSigaction(long signalNumber, long actionAddress, long oldActionAddress, long sigsetSize) {
        if (sigsetSize != KERNEL_SIGSET_SIZE) {
            return EINVAL;
        }
        if (signalNumber < MIN_SIGNAL_NUMBER || signalNumber > MAX_SIGNAL_NUMBER) {
            return EINVAL;
        }

        if (oldActionAddress != 0) {
            memory.clear(oldActionAddress, KERNEL_SIGACTION_SIZE);
        }
        return 0;
    }

    /// Reads and updates the calling guest thread's signal mask.
    protected long rtSigprocmask(MachineState state, long how, long setAddress, long oldSetAddress, long sigsetSize) {
        if (sigsetSize != KERNEL_SIGSET_SIZE) {
            return EINVAL;
        }
        if (setAddress != 0 && how != SIG_BLOCK && how != SIG_UNBLOCK && how != SIG_SETMASK) {
            return EINVAL;
        }

        GuestThread thread = state.guestThread();
        long oldMask = thread.signalMask();
        if (oldSetAddress != 0) {
            memory.writeLong(oldSetAddress, oldMask);
        }
        if (setAddress != 0) {
            long requestedMask = memory.readLong(setAddress) & ~UNBLOCKABLE_SIGNAL_MASK;
            long updatedMask = oldMask;
            if (how == SIG_BLOCK) {
                updatedMask |= requestedMask;
            } else if (how == SIG_UNBLOCK) {
                updatedMask &= ~requestedMask;
            } else {
                updatedMask = requestedMask;
            }
            thread.setSignalMask(updatedMask);
        }
        return 0;
    }

    /// Handles the Linux `prctl` operations needed by single-process user-mode guests.
    protected long prctl(
            MachineState state,
            long option,
            long argument2,
            long argument3,
            long argument4,
            long argument5) {
        if (option == PR_SET_PDEATHSIG) {
            return setParentDeathSignal(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_PDEATHSIG) {
            return getParentDeathSignal(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_DUMPABLE) {
            return noArguments(argument2, argument3, argument4, argument5) ? dumpable : EINVAL;
        }
        if (option == PR_SET_DUMPABLE) {
            return setDumpable(argument2, argument3, argument4, argument5);
        }
        if (option == PR_SET_NAME) {
            return setProcessName(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_NAME) {
            return getProcessName(argument2, argument3, argument4, argument5);
        }
        if (option == PR_SET_TIMERSLACK) {
            return setTimerSlack(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_TIMERSLACK) {
            return noArguments(argument2, argument3, argument4, argument5) ? timerSlackNanoseconds : EINVAL;
        }
        if (option == PR_SET_CHILD_SUBREAPER) {
            return setChildSubreaper(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_CHILD_SUBREAPER) {
            return getChildSubreaper(argument2, argument3, argument4, argument5);
        }
        if (option == PR_SET_NO_NEW_PRIVS) {
            return setNoNewPrivileges(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_NO_NEW_PRIVS) {
            return noArguments(argument2, argument3, argument4, argument5) ? (noNewPrivileges ? 1 : 0) : EINVAL;
        }
        if (option == PR_GET_TID_ADDRESS) {
            return getTidAddress(state, argument2, argument3, argument4, argument5);
        }
        if (option == PR_SET_THP_DISABLE) {
            return setTransparentHugePagesDisabled(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_THP_DISABLE) {
            return noArguments(argument2, argument3, argument4, argument5) ? transparentHugePagesDisabled : EINVAL;
        }
        if (option == PR_SET_TAGGED_ADDR_CTRL) {
            return setTaggedAddressControl(state, argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_TAGGED_ADDR_CTRL) {
            return getTaggedAddressControl(state, argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_AUXV) {
            return getAuxiliaryVector(argument2, argument3, argument4, argument5);
        }
        if (option == PR_SET_VMA) {
            return setVirtualMemoryAreaName(argument2, argument3, argument4, argument5);
        }
        return EINVAL;
    }

    /// Stores the parent-death signal value used by `PR_GET_PDEATHSIG`.
    protected long setParentDeathSignal(long signalNumber, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        if (signalNumber != 0 && (signalNumber < MIN_SIGNAL_NUMBER || signalNumber > MAX_SIGNAL_NUMBER)) {
            return EINVAL;
        }
        parentDeathSignal = (int) signalNumber;
        return 0;
    }

    /// Writes the configured parent-death signal to a guest `int` pointer.
    protected long getParentDeathSignal(long address, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        memory.writeInt(address, parentDeathSignal);
        return 0;
    }

    /// Stores the dumpable state exposed by `PR_GET_DUMPABLE`.
    protected long setDumpable(long value, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5) || (value != 0 && value != 1)) {
            return EINVAL;
        }
        dumpable = (int) value;
        return 0;
    }

    /// Copies a Linux task command name from guest memory.
    protected long setProcessName(long address, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }

        for (int index = 0; index < processName.length; index++) {
            processName[index] = 0;
        }
        for (int index = 0; index < TASK_COMMAND_LENGTH - 1; index++) {
            int value = memory.readUnsignedByte(address + index);
            if (value == 0) {
                return 0;
            }
            processName[index] = (byte) value;
        }
        return 0;
    }

    /// Copies the Linux task command name to guest memory.
    protected long getProcessName(long address, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        memory.writeBytes(address, processName, 0, processName.length);
        return 0;
    }

    /// Copies the initial Linux auxiliary vector for `PR_GET_AUXV`.
    protected long getAuxiliaryVector(long address, long byteCount, long argument4, long argument5) {
        if (argument4 != 0 || argument5 != 0) {
            return EINVAL;
        }
        if (Long.compareUnsigned(byteCount, auxiliaryVectorBytes.length) < 0) {
            return auxiliaryVectorBytes.length;
        }
        if (!memory.isBacked(address, auxiliaryVectorBytes.length)) {
            return EFAULT;
        }

        memory.writeBytes(address, auxiliaryVectorBytes, 0, auxiliaryVectorBytes.length);
        return auxiliaryVectorBytes.length;
    }

    /// Stores the timer slack value exposed by `PR_GET_TIMERSLACK`.
    protected long setTimerSlack(long value, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5) || value < 0) {
            return EINVAL;
        }
        timerSlackNanoseconds = value == 0 ? DEFAULT_TIMER_SLACK_NANOSECONDS : value;
        return 0;
    }

    /// Stores the child-subreaper flag exposed by `PR_GET_CHILD_SUBREAPER`.
    protected long setChildSubreaper(long value, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5) || (value != 0 && value != 1)) {
            return EINVAL;
        }
        childSubreaper = value == 1;
        return 0;
    }

    /// Writes the child-subreaper flag to a guest `int` pointer.
    protected long getChildSubreaper(long address, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        memory.writeInt(address, childSubreaper ? 1 : 0);
        return 0;
    }

    /// Applies the monotonic `PR_SET_NO_NEW_PRIVS` flag.
    protected long setNoNewPrivileges(long value, long argument3, long argument4, long argument5) {
        if (value != 1 || !unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        noNewPrivileges = true;
        return 0;
    }

    /// Writes the clear-child-TID address to a guest `long` pointer.
    protected long getTidAddress(MachineState state, long address, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        memory.writeLong(address, state.clearChildTidAddress());
        return 0;
    }

    /// Stores the transparent huge page disable state exposed by `PR_GET_THP_DISABLE`.
    protected long setTransparentHugePagesDisabled(long value, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5) || (value != 0 && value != 1)) {
            return EINVAL;
        }
        transparentHugePagesDisabled = (int) value;
        return 0;
    }

    /// Stores the Linux RISC-V tagged-address control state for the current guest thread.
    protected static long setTaggedAddressControl(
            MachineState state,
            long control,
            long argument3,
            long argument4,
            long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        if ((control & ~(PR_TAGGED_ADDR_ENABLE | PR_PMLEN_MASK)) != 0) {
            return EINVAL;
        }

        long requestedPointerMaskLength = (control & PR_PMLEN_MASK) >>> PR_PMLEN_SHIFT;
        int pointerMaskLength;
        if (requestedPointerMaskLength == POINTER_MASK_LENGTH_DISABLED) {
            pointerMaskLength = POINTER_MASK_LENGTH_DISABLED;
        } else if (requestedPointerMaskLength <= POINTER_MASK_LENGTH_7) {
            pointerMaskLength = POINTER_MASK_LENGTH_7;
        } else {
            return EINVAL;
        }

        state.guestThread().setTaggedAddressControl(
                pointerMaskLength,
                (control & PR_TAGGED_ADDR_ENABLE) != 0);
        return 0;
    }

    /// Returns the Linux RISC-V tagged-address control state for the current guest thread.
    protected static long getTaggedAddressControl(
            MachineState state,
            long argument2,
            long argument3,
            long argument4,
            long argument5) {
        if (!noArguments(argument2, argument3, argument4, argument5)) {
            return EINVAL;
        }

        GuestThread thread = state.guestThread();
        long control = (long) thread.pointerMaskLength() << PR_PMLEN_SHIFT;
        return thread.taggedAddressAbiEnabled()
                ? control | PR_TAGGED_ADDR_ENABLE
                : control;
    }

    /// Accepts Linux memory-area naming requests as a no-op in the flat guest memory model.
    protected static long setVirtualMemoryAreaName(long suboption, long startAddress, long length, long nameAddress) {
        if (suboption != PR_SET_VMA_ANON_NAME || startAddress < 0 || length < 0 || nameAddress < 0) {
            return EINVAL;
        }
        return 0;
    }

    /// Returns true when all `prctl` arguments are unused zero values.
    protected static boolean noArguments(long argument2, long argument3, long argument4, long argument5) {
        return argument2 == 0 && unusedArgumentsAreZero(argument3, argument4, argument5);
    }

    /// Returns true when all trailing `prctl` arguments are unused zero values.
    protected static boolean unusedArgumentsAreZero(long argument3, long argument4, long argument5) {
        return argument3 == 0 && argument4 == 0 && argument5 == 0;
    }


    /// Implements the Linux `brk` syscall within the simulator memory window.
    protected long brk(long requestedAddress) {
        if (requestedAddress == 0) {
            return programBreak;
        }
        if (requestedAddress >= initialProgramBreak
                && requestedAddress <= memory.endAddress()
                && !overlapsMemoryMappings(initialProgramBreak, requestedAddress - initialProgramBreak)) {
            if (requestedAddress > programBreakBackingEnd
                    && !ensureProgramBreakBacking(programBreakBackingEnd, requestedAddress - programBreakBackingEnd)) {
                return programBreak;
            }
            if (requestedAddress > programBreakBackingEnd) {
                programBreakBackingEnd = requestedAddress;
            }
            programBreak = requestedAddress;
        }
        return programBreak;
    }


    /// Accepts no-op real, effective, and saved user or group id updates.
    protected static long setresid(
            long requestedRealId,
            long requestedEffectiveId,
            long requestedSavedId,
            long currentRealId,
            long currentEffectiveId,
            long currentSavedId) {
        if (!isValidSetresidArgument(requestedRealId)
                || !isValidSetresidArgument(requestedEffectiveId)
                || !isValidSetresidArgument(requestedSavedId)) {
            return EINVAL;
        }
        if (!isUnchangedOrCurrentId(requestedRealId, currentRealId)
                || !isUnchangedOrCurrentId(requestedEffectiveId, currentEffectiveId)
                || !isUnchangedOrCurrentId(requestedSavedId, currentSavedId)) {
            return EPERM;
        }
        return 0;
    }

    /// Returns true when an id argument is valid for `setresuid` or `setresgid`.
    protected static boolean isValidSetresidArgument(long requestedId) {
        return isSetresidUnchanged(requestedId) || requestedId >= 0 && requestedId < GuestCredentials.MAX_ID;
    }

    /// Returns true for the `(uid_t) -1` sentinel that leaves one id unchanged.
    protected static boolean isSetresidUnchanged(long requestedId) {
        return requestedId == -1L || requestedId == GuestCredentials.MAX_ID;
    }

    /// Returns true when a requested id is the no-change sentinel or the current id.
    protected static boolean isUnchangedOrCurrentId(long requestedId, long currentId) {
        return isSetresidUnchanged(requestedId) || requestedId == currentId;
    }

    /// Returns the unchanged filesystem user or group id.
    protected static long setfsid(long requestedId, long currentId) {
        if (!isValidSetresidArgument(requestedId)) {
            return currentId;
        }
        return currentId;
    }

    /// Writes the configured supplementary group list for identity queries.
    protected long getgroups(long size, long listAddress) {
        if (size < 0 || size > Integer.MAX_VALUE) {
            return EINVAL;
        }

        int groupCount = credentials.supplementaryGroupCount();
        if (size == 0) {
            return groupCount;
        }
        if (size < groupCount) {
            return EINVAL;
        }
        long bytes = (long) groupCount * Integer.BYTES;
        if (!memory.isBacked(listAddress, bytes)) {
            return EFAULT;
        }

        for (int index = 0; index < groupCount; index++) {
            memory.writeInt(listAddress + (long) index * Integer.BYTES,
                    GuestCredentials.idToInt(credentials.supplementaryGroupAt(index)));
        }
        return groupCount;
    }

    /// Rejects socket creation for the current non-networked user-mode runtime.
    protected static long socket(long domain, long type, long protocol) {
        return EAFNOSUPPORT;
    }


    /// Implements Linux `mremap` for tracked anonymous mappings.
    protected long mremap(long oldAddress, long oldSize, long newSize, long flags, long newAddress) {
        if (!isPageAligned(oldAddress) || oldSize <= 0 || newSize <= 0 || (flags & ~SUPPORTED_MREMAP_FLAGS) != 0) {
            return EINVAL;
        }
        boolean fixed = (flags & MREMAP_FIXED) != 0;
        boolean mayMove = (flags & MREMAP_MAYMOVE) != 0;
        if (fixed && !mayMove) {
            return EINVAL;
        }

        long oldLength = alignUp(oldSize, pageSize);
        long newLength = alignUp(newSize, pageSize);
        if (oldLength <= 0 || newLength <= 0
                || !isValidGuestRange(oldAddress, oldLength)
                || (fixed && (!isPageAligned(newAddress) || !isValidGuestRange(newAddress, newLength)))) {
            return ENOMEM;
        }

        @Nullable MemoryMapping mapping = memoryMappingCovering(oldAddress);
        if (mapping == null || oldAddress != mapping.address() || oldLength != mapping.length()) {
            return ENOMEM;
        }
        if (fixed && rangesOverlap(oldAddress, oldAddress + oldLength, newAddress, newAddress + newLength)) {
            return EINVAL;
        }
        if (fixed) {
            removeMemoryMappings(newAddress, newLength);
        }

        if (!fixed && newLength == oldLength) {
            return oldAddress;
        }
        if (!fixed && newLength < oldLength) {
            removeMemoryMappings(oldAddress + newLength, oldLength - newLength);
            return oldAddress;
        }

        long growthLength = newLength - oldLength;
        if (!fixed && isMmapRangeAvailable(oldAddress + oldLength, growthLength)) {
            if (!ensureMemoryBacking(oldAddress + oldLength, growthLength, mapping.protection(), false)) {
                return ENOMEM;
            }
            resizeMemoryMapping(mapping, newLength);
            return oldAddress;
        }
        if (!mayMove) {
            return ENOMEM;
        }

        long targetAddress = fixed ? newAddress : findMmapAddress(0, newLength, pageSize);
        if (targetAddress == 0 || !isMmapRangeAvailable(targetAddress, newLength)) {
            return ENOMEM;
        }
        return moveMemoryMapping(mapping, oldLength, targetAddress, newLength);
    }


    /// Reports mapped guest pages as resident for Linux `mincore` probes.
    protected long mincore(long address, long length, long vectorAddress) {
        if (!isPageAligned(address) || length < 0) {
            return EINVAL;
        }
        if (length == 0) {
            return 0;
        }

        long alignedLength = alignUp(length, pageSize);
        if (alignedLength <= 0 || !isValidGuestRange(address, alignedLength)) {
            return ENOMEM;
        }
        if (!isMappedRange(address, alignedLength)) {
            return ENOMEM;
        }

        long pageCount = alignedLength / pageSize;
        if (pageCount > Integer.MAX_VALUE) {
            throw new RiscVException("Guest mincore vector is too large: " + pageCount);
        }
        if (!memory.isBacked(vectorAddress, pageCount)) {
            return EFAULT;
        }

        byte[] vector = new byte[(int) pageCount];
        for (int index = 0; index < vector.length; index++) {
            vector[index] = 1;
        }
        memory.writeBytes(vectorAddress, vector, 0, vector.length);
        return 0;
    }


    /// Gets or lowers Linux resource limits for the current guest process.
    protected long prlimit64(long processId, long resource, long newLimitAddress, long oldLimitAddress) {
        if (processId != 0 && processId != process.id()) {
            return ESRCH;
        }
        if (resource < 0 || resource >= RESOURCE_LIMIT_COUNT) {
            return EINVAL;
        }

        int index = (int) resource;
        long oldCurrent = resourceLimitCurrent[index];
        long oldMaximum = resourceLimitMaximum[index];
        if (oldLimitAddress != 0) {
            writeResourceLimit(oldLimitAddress, oldCurrent, oldMaximum);
        }

        if (newLimitAddress != 0) {
            long newCurrent = memory.readLong(newLimitAddress + RLIMIT_CURRENT_OFFSET);
            long newMaximum = memory.readLong(newLimitAddress + RLIMIT_MAXIMUM_OFFSET);
            if (Long.compareUnsigned(newCurrent, newMaximum) > 0) {
                return EINVAL;
            }
            if (Long.compareUnsigned(newMaximum, oldMaximum) > 0) {
                return EPERM;
            }
            resourceLimitCurrent[index] = newCurrent;
            resourceLimitMaximum[index] = newMaximum;
        }
        return 0;
    }


    /// Fills a guest buffer with deterministic bytes for the Linux `getrandom` syscall.
    protected long getrandom(long address, long length, long flags) {
        if ((flags & ~GETRANDOM_SUPPORTED_FLAGS) != 0) {
            return EINVAL;
        }
        if (length < 0) {
            return EINVAL;
        }
        if (length > Integer.MAX_VALUE) {
            throw new RiscVException("Guest getrandom syscall buffer is too large: " + length);
        }
        if (length == 0) {
            return 0;
        }

        return writeDeterministicRandomBytes(address, length);
    }

    /// Reports no supported Linux `membarrier` commands for runtime capability probes.
    protected static long membarrier(long command, long flags, long cpuId) {
        if (flags != 0 || cpuId != 0) {
            return EINVAL;
        }
        return command == MEMBARRIER_CMD_QUERY ? 0 : EINVAL;
    }

    /// Handles Linux restartable sequence registration for the current guest thread.
    protected long rseq(MachineState state, long address, long length, long flags, long signature) {
        long rseqLength = length & 0xffff_ffffL;
        long rseqFlags = flags & 0xffff_ffffL;
        long rseqSignature = signature & 0xffff_ffffL;
        GuestThread thread = state.guestThread();
        if ((rseqFlags & RSEQ_FLAG_UNREGISTER) != 0) {
            return unregisterRseq(thread, address, rseqLength, rseqFlags, rseqSignature);
        }

        if (rseqFlags != 0) {
            return EINVAL;
        }

        if (thread.hasRestartableSequence()) {
            if (thread.restartableSequenceAddress() != address || thread.restartableSequenceLength() != rseqLength) {
                return EINVAL;
            }
            if (thread.restartableSequenceSignature() != rseqSignature) {
                return EPERM;
            }
            return EBUSY;
        }

        if (rseqLength < RSEQ_ORIGINAL_SIZE || !isAligned(address, RSEQ_ALIGNMENT)) {
            return EINVAL;
        }
        if (!memory.isBacked(address, rseqLength)) {
            return EFAULT;
        }

        initializeRseqArea(address);
        thread.setRestartableSequence(address, rseqLength, rseqSignature);
        return 0;
    }

    /// Unregisters the current thread's restartable sequence area.
    protected long unregisterRseq(GuestThread thread, long address, long length, long flags, long signature) {
        if ((flags & ~RSEQ_FLAG_UNREGISTER) != 0) {
            return EINVAL;
        }
        if (!thread.hasRestartableSequence() || thread.restartableSequenceAddress() != address) {
            return EINVAL;
        }
        if (thread.restartableSequenceLength() != length) {
            return EINVAL;
        }
        if (thread.restartableSequenceSignature() != signature) {
            return EPERM;
        }
        if (!memory.isBacked(address, length)) {
            return EFAULT;
        }

        resetRseqArea(address);
        thread.clearRestartableSequence();
        return 0;
    }

    /// Initializes the guest-visible fields that Linux updates while rseq is registered.
    protected void initializeRseqArea(long address) {
        memory.writeLong(address + RSEQ_CRITICAL_SECTION_OFFSET, 0);
        memory.writeInt(address + RSEQ_CPU_ID_START_OFFSET, 0);
        memory.writeInt(address + RSEQ_CPU_ID_OFFSET, 0);
        memory.writeInt(address + RSEQ_FLAGS_OFFSET, 0);
        memory.writeInt(address + RSEQ_NODE_ID_OFFSET, 0);
        memory.writeInt(address + RSEQ_MEMORY_MAP_CONCURRENCY_ID_OFFSET, 0);
    }

    /// Resets the guest-visible fields that Linux invalidates during rseq unregistration.
    protected void resetRseqArea(long address) {
        memory.writeLong(address + RSEQ_CRITICAL_SECTION_OFFSET, 0);
        memory.writeInt(address + RSEQ_CPU_ID_START_OFFSET, -1);
        memory.writeInt(address + RSEQ_CPU_ID_OFFSET, -1);
        memory.writeInt(address + RSEQ_NODE_ID_OFFSET, 0);
        memory.writeInt(address + RSEQ_MEMORY_MAP_CONCURRENCY_ID_OFFSET, 0);
    }


    /// Creates a child-process Linux syscall handler by copying fork-inherited parent state.
    private LinuxGuestSyscalls(LinuxGuestSyscalls parent, Memory memory, GuestProcess process) {
        super(parent, memory, process);
    }

    /// The Linux RISC-V syscall number for `getxattr`.
    private static final int SYS_GETXATTR = 8;

    /// The Linux RISC-V syscall number for `lgetxattr`.
    private static final int SYS_LGETXATTR = 9;

    /// The Linux RISC-V syscall number for `fgetxattr`.
    private static final int SYS_FGETXATTR = 10;

    /// The Linux RISC-V syscall number for `listxattr`.
    private static final int SYS_LISTXATTR = 11;

    /// The Linux RISC-V syscall number for `llistxattr`.
    private static final int SYS_LLISTXATTR = 12;

    /// The Linux RISC-V syscall number for `flistxattr`.
    private static final int SYS_FLISTXATTR = 13;

    /// The Linux RISC-V syscall number for `getcwd`.
    private static final int SYS_GETCWD = 17;

    /// The Linux RISC-V syscall number for `eventfd2`.
    private static final int SYS_EVENTFD2 = 19;

    /// The Linux RISC-V syscall number for `epoll_create1`.
    private static final int SYS_EPOLL_CREATE1 = 20;

    /// The Linux RISC-V syscall number for `epoll_ctl`.
    private static final int SYS_EPOLL_CTL = 21;

    /// The Linux RISC-V syscall number for `epoll_pwait`.
    private static final int SYS_EPOLL_PWAIT = 22;

    /// The Linux RISC-V syscall number for `dup`.
    private static final int SYS_DUP = 23;

    /// The Linux RISC-V syscall number for `dup3`.
    private static final int SYS_DUP3 = 24;

    /// The Linux RISC-V syscall number for `fcntl`.
    private static final int SYS_FCNTL = 25;

    /// The Linux RISC-V syscall number for `ioctl`.
    private static final int SYS_IOCTL = 29;

    /// The Linux RISC-V syscall number for `mkdirat`.
    private static final int SYS_MKDIRAT = 34;

    /// The Linux RISC-V syscall number for `unlinkat`.
    private static final int SYS_UNLINKAT = 35;

    /// The Linux RISC-V syscall number for `renameat`.
    private static final int SYS_RENAMEAT = 38;

    /// The Linux RISC-V syscall number for `statfs`.
    private static final int SYS_STATFS = 43;

    /// The Linux RISC-V syscall number for `fstatfs`.
    private static final int SYS_FSTATFS = 44;

    /// The Linux RISC-V syscall number for `truncate`.
    private static final int SYS_TRUNCATE = 45;

    /// The Linux RISC-V syscall number for `ftruncate`.
    private static final int SYS_FTRUNCATE = 46;

    /// The Linux RISC-V syscall number for `faccessat`.
    private static final int SYS_FACCESSAT = 48;

    /// The Linux RISC-V syscall number for `chdir`.
    private static final int SYS_CHDIR = 49;

    /// The Linux RISC-V syscall number for `fchdir`.
    private static final int SYS_FCHDIR = 50;

    /// The Linux RISC-V syscall number for `fchownat`.
    private static final int SYS_FCHOWNAT = 54;

    /// The Linux RISC-V syscall number for `openat`.
    private static final int SYS_OPENAT = 56;

    /// The Linux RISC-V syscall number for `close`.
    private static final int SYS_CLOSE = 57;

    /// The Linux RISC-V syscall number for `pipe2`.
    private static final int SYS_PIPE2 = 59;

    /// The Linux RISC-V syscall number for `getdents64`.
    private static final int SYS_GETDENTS64 = 61;

    /// The Linux RISC-V syscall number for `lseek`.
    private static final int SYS_LSEEK = 62;

    /// The Linux RISC-V syscall number for `read`.
    private static final int SYS_READ = 63;

    /// The Linux RISC-V syscall number for `write`.
    private static final int SYS_WRITE = 64;

    /// The Linux RISC-V syscall number for `readv`.
    private static final int SYS_READV = 65;

    /// The Linux RISC-V syscall number for `writev`.
    private static final int SYS_WRITEV = 66;

    /// The Linux RISC-V syscall number for `pread64`.
    private static final int SYS_PREAD64 = 67;

    /// The Linux RISC-V syscall number for `pwrite64`.
    private static final int SYS_PWRITE64 = 68;

    /// The Linux RISC-V syscall number for `pselect6`.
    private static final int SYS_PSELECT6 = 72;

    /// The Linux RISC-V syscall number for `ppoll`.
    private static final int SYS_PPOLL = 73;

    /// The Linux RISC-V syscall number for `splice`.
    private static final int SYS_SPLICE = 76;

    /// The Linux RISC-V syscall number for `readlinkat`.
    private static final int SYS_READLINKAT = 78;

    /// The Linux RISC-V syscall number for `newfstatat`.
    private static final int SYS_NEWFSTATAT = 79;

    /// The Linux RISC-V syscall number for `fstat`.
    private static final int SYS_FSTAT = 80;

    /// The Linux RISC-V syscall number for `sync`.
    private static final int SYS_SYNC = 81;

    /// The Linux RISC-V syscall number for `fsync`.
    private static final int SYS_FSYNC = 82;

    /// The Linux RISC-V syscall number for `fdatasync`.
    private static final int SYS_FDATASYNC = 83;

    /// The Linux RISC-V syscall number for `exit`.
    private static final int SYS_EXIT = 93;

    /// The Linux RISC-V syscall number for `exit_group`.
    private static final int SYS_EXIT_GROUP = 94;

    /// The Linux RISC-V syscall number for `set_tid_address`.
    private static final int SYS_SET_TID_ADDRESS = 96;

    /// The Linux RISC-V syscall number for `futex`.
    private static final int SYS_FUTEX = 98;

    /// The Linux RISC-V syscall number for `set_robust_list`.
    private static final int SYS_SET_ROBUST_LIST = 99;

    /// The Linux RISC-V syscall number for `get_robust_list`.
    private static final int SYS_GET_ROBUST_LIST = 100;

    /// The Linux RISC-V syscall number for `nanosleep`.
    private static final int SYS_NANOSLEEP = 101;

    /// The Linux RISC-V syscall number for `clock_gettime`.
    private static final int SYS_CLOCK_GETTIME = 113;

    /// The Linux RISC-V syscall number for `clock_getres`.
    private static final int SYS_CLOCK_GETRES = 114;

    /// The Linux RISC-V syscall number for `clock_nanosleep`.
    private static final int SYS_CLOCK_NANOSLEEP = 115;

    /// The Linux RISC-V syscall number for `sched_getaffinity`.
    private static final int SYS_SCHED_GETAFFINITY = 123;

    /// The Linux RISC-V syscall number for `sched_yield`.
    private static final int SYS_SCHED_YIELD = 124;

    /// The Linux RISC-V syscall number for `kill`.
    private static final int SYS_KILL = 129;

    /// The Linux RISC-V syscall number for `tkill`.
    private static final int SYS_TKILL = 130;

    /// The Linux RISC-V syscall number for `tgkill`.
    private static final int SYS_TGKILL = 131;

    /// The Linux RISC-V syscall number for `sigaltstack`.
    private static final int SYS_SIGALTSTACK = 132;

    /// The Linux RISC-V syscall number for `rt_sigaction`.
    private static final int SYS_RT_SIGACTION = 134;

    /// The Linux RISC-V syscall number for `rt_sigprocmask`.
    private static final int SYS_RT_SIGPROCMASK = 135;

    /// The Linux RISC-V syscall number for `setresuid`.
    private static final int SYS_SETRESUID = 147;

    /// The Linux RISC-V syscall number for `getresuid`.
    private static final int SYS_GETRESUID = 148;

    /// The Linux RISC-V syscall number for `setresgid`.
    private static final int SYS_SETRESGID = 149;

    /// The Linux RISC-V syscall number for `getresgid`.
    private static final int SYS_GETRESGID = 150;

    /// The Linux RISC-V syscall number for `setfsuid`.
    private static final int SYS_SETFSUID = 151;

    /// The Linux RISC-V syscall number for `setfsgid`.
    private static final int SYS_SETFSGID = 152;

    /// The Linux RISC-V syscall number for `times`.
    private static final int SYS_TIMES = 153;

    /// The Linux RISC-V syscall number for `setpgid`.
    private static final int SYS_SETPGID = 154;

    /// The Linux RISC-V syscall number for `getpgid`.
    private static final int SYS_GETPGID = 155;

    /// The Linux RISC-V syscall number for `setsid`.
    private static final int SYS_SETSID = 157;

    /// The Linux RISC-V syscall number for `getgroups`.
    private static final int SYS_GETGROUPS = 158;

    /// The Linux RISC-V syscall number for `uname`.
    private static final int SYS_UNAME = 160;

    /// The Linux RISC-V syscall number for `getrusage`.
    private static final int SYS_GETRUSAGE = 165;

    /// The Linux RISC-V syscall number for `prctl`.
    private static final int SYS_PRCTL = 167;

    /// The Linux RISC-V syscall number for `getcpu`.
    private static final int SYS_GETCPU = 168;

    /// The Linux RISC-V syscall number for `gettimeofday`.
    private static final int SYS_GETTIMEOFDAY = 169;

    /// The Linux RISC-V syscall number for `getpid`.
    private static final int SYS_GETPID = 172;

    /// The Linux RISC-V syscall number for `getppid`.
    private static final int SYS_GETPPID = 173;

    /// The Linux RISC-V syscall number for `getuid`.
    private static final int SYS_GETUID = 174;

    /// The Linux RISC-V syscall number for `geteuid`.
    private static final int SYS_GETEUID = 175;

    /// The Linux RISC-V syscall number for `getgid`.
    private static final int SYS_GETGID = 176;

    /// The Linux RISC-V syscall number for `getegid`.
    private static final int SYS_GETEGID = 177;

    /// The Linux RISC-V syscall number for `gettid`.
    private static final int SYS_GETTID = 178;

    /// The Linux RISC-V syscall number for `sysinfo`.
    private static final int SYS_SYSINFO = 179;

    /// The Linux RISC-V syscall number for `socket`.
    private static final int SYS_SOCKET = 198;

    /// The Linux RISC-V syscall number for `getsockname`.
    private static final int SYS_GETSOCKNAME = 204;

    /// The Linux RISC-V syscall number for `getpeername`.
    private static final int SYS_GETPEERNAME = 205;

    /// The Linux RISC-V syscall number for `brk`.
    private static final int SYS_BRK = 214;

    /// The Linux RISC-V syscall number for `munmap`.
    private static final int SYS_MUNMAP = 215;

    /// The Linux RISC-V syscall number for `mremap`.
    private static final int SYS_MREMAP = 216;

    /// The Linux RISC-V syscall number for `clone`.
    private static final int SYS_CLONE = 220;

    /// The Linux RISC-V syscall number for `clone3`.
    private static final int SYS_CLONE3 = 435;

    /// The Linux RISC-V syscall number for `execve`.
    private static final int SYS_EXECVE = 221;

    /// The Linux RISC-V syscall number for `mmap`.
    private static final int SYS_MMAP = 222;

    /// The Linux RISC-V syscall number for `mprotect`.
    private static final int SYS_MPROTECT = 226;

    /// The Linux RISC-V syscall number for `mincore`.
    private static final int SYS_MINCORE = 232;

    /// The Linux RISC-V syscall number for `madvise`.
    private static final int SYS_MADVISE = 233;

    /// The Linux RISC-V syscall number for `riscv_hwprobe`.
    private static final int SYS_RISCV_HWPROBE = 258;

    /// The Linux RISC-V syscall number for `wait4`.
    private static final int SYS_WAIT4 = 260;

    /// The Linux RISC-V syscall number for `prlimit64`.
    private static final int SYS_PRLIMIT64 = 261;

    /// The Linux RISC-V syscall number for `syncfs`.
    private static final int SYS_SYNCFS = 267;

    /// The Linux RISC-V syscall number for `renameat2`.
    private static final int SYS_RENAMEAT2 = 276;

    /// The Linux RISC-V syscall number for `getrandom`.
    private static final int SYS_GETRANDOM = 278;

    /// The Linux RISC-V syscall number for `membarrier`.
    private static final int SYS_MEMBARRIER = 283;

    /// The Linux RISC-V syscall number for `statx`.
    private static final int SYS_STATX = 291;

    /// The Linux RISC-V syscall number for `rseq`.
    private static final int SYS_RSEQ = 293;

    /// The Linux RISC-V syscall number for `faccessat2`.
    private static final int SYS_FACCESSAT2 = 439;

    /// Executes the Linux syscall described by the guest argument registers at the supplied program counter.
    @Override
    public void handle(MachineState state, long pc) {
        long callNumber = state.register(17);
        if (callNumber != (int) callNumber) {
            throw new RiscVException(unsupportedEcallMessage(state, pc, callNumber));
        }

        long previousMask = state.enterSyscallPointerMask();
        try {
            switch ((int) callNumber) {
                case SYS_GETXATTR -> state.setRegister(10, getxattr(
                        state.register(10),
                        state.register(11),
                        state.register(12),
                        state.register(13),
                        true));
                case SYS_LGETXATTR -> state.setRegister(10, getxattr(
                        state.register(10),
                        state.register(11),
                        state.register(12),
                        state.register(13),
                        false));
                case SYS_FGETXATTR -> state.setRegister(10, fgetxattr(
                        (int) state.register(10),
                        state.register(11),
                        state.register(12),
                        state.register(13)));
                case SYS_LISTXATTR -> state.setRegister(10, listxattr(
                        state.register(10),
                        state.register(11),
                        state.register(12),
                        true));
                case SYS_LLISTXATTR -> state.setRegister(10, listxattr(
                        state.register(10),
                        state.register(11),
                        state.register(12),
                        false));
                case SYS_FLISTXATTR -> state.setRegister(10, flistxattr(
                        (int) state.register(10),
                        state.register(11),
                        state.register(12)));
                case SYS_GETCWD -> state.setRegister(10, getcwd(state.register(10), state.register(11)));
            case SYS_EVENTFD2 -> state.setRegister(10, eventfd2(state.register(10), state.register(11)));
            case SYS_EPOLL_CREATE1 -> state.setRegister(10, epollCreate1(state.register(10)));
            case SYS_EPOLL_CTL -> state.setRegister(10, epollCtl(
                    (int) state.register(10),
                    (int) state.register(11),
                    (int) state.register(12),
                    state.register(13)));
            case SYS_EPOLL_PWAIT -> state.setRegister(10, epollPwait(
                    state,
                    (int) state.register(10),
                    state.register(11),
                    (int) state.register(12),
                    (int) state.register(13),
                    state.register(14),
                    state.register(15)));
            case SYS_DUP -> state.setRegister(10, dup((int) state.register(10)));
            case SYS_DUP3 -> state.setRegister(10, dup3((int) state.register(10), (int) state.register(11), state.register(12)));
            case SYS_FCNTL -> state.setRegister(10, fcntl((int) state.register(10), state.register(11), state.register(12)));
            case SYS_IOCTL -> state.setRegister(10, ioctl((int) state.register(10), state.register(11), state.register(12)));
            case SYS_MKDIRAT -> state.setRegister(10, mkdirat(state.register(10), state.register(11), state.register(12)));
            case SYS_UNLINKAT -> state.setRegister(10, unlinkat(state.register(10), state.register(11), state.register(12)));
            case SYS_RENAMEAT -> state.setRegister(10, renameat(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            case SYS_STATFS -> state.setRegister(10, statfs(state.register(10), state.register(11)));
            case SYS_FSTATFS -> state.setRegister(10, fstatfs((int) state.register(10), state.register(11)));
            case SYS_TRUNCATE -> state.setRegister(10, truncate(state.register(10), state.register(11)));
            case SYS_FTRUNCATE -> state.setRegister(10, ftruncate((int) state.register(10), state.register(11)));
            case SYS_FACCESSAT -> state.setRegister(10, faccessat(state.register(10), state.register(11), state.register(12), 0));
            case SYS_CHDIR -> state.setRegister(10, chdir(state.register(10)));
            case SYS_FCHDIR -> state.setRegister(10, fchdir((int) state.register(10)));
            case SYS_FCHOWNAT -> state.setRegister(10, fchownat(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14)));
            case SYS_OPENAT -> state.setRegister(10, openat(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            case SYS_CLOSE -> state.setRegister(10, close((int) state.register(10)));
            case SYS_PIPE2 -> state.setRegister(10, pipe2(state.register(10), state.register(11)));
            case SYS_GETDENTS64 -> state.setRegister(10, getdents64((int) state.register(10), state.register(11), state.register(12)));
            case SYS_LSEEK -> state.setRegister(10, lseek((int) state.register(10), state.register(11), (int) state.register(12)));
            case SYS_READ -> state.setRegister(10, read((int) state.register(10), state.register(11), state.register(12)));
            case SYS_WRITE -> state.setRegister(10, write((int) state.register(10), state.register(11), state.register(12)));
            case SYS_READV -> state.setRegister(10, readv((int) state.register(10), state.register(11), state.register(12)));
            case SYS_WRITEV -> state.setRegister(10, writev((int) state.register(10), state.register(11), state.register(12)));
            case SYS_PREAD64 -> state.setRegister(10, pread64(
                    (int) state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            case SYS_PWRITE64 -> state.setRegister(10, pwrite64(
                    (int) state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            case SYS_PSELECT6 -> state.setRegister(10, pselect6(
                    state,
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14),
                    state.register(15)));
            case SYS_PPOLL -> state.setRegister(10, ppoll(
                    state,
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14)));
            case SYS_SPLICE -> state.setRegister(10, splice(
                    (int) state.register(10),
                    state.register(11),
                    (int) state.register(12),
                    state.register(13),
                    state.register(14),
                    state.register(15)));
            case SYS_READLINKAT -> state.setRegister(10, readlinkat(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            case SYS_NEWFSTATAT -> state.setRegister(10, newfstatat(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            case SYS_FSTAT -> state.setRegister(10, fstat((int) state.register(10), state.register(11)));
            case SYS_SYNC -> state.setRegister(10, sync());
            case SYS_FSYNC -> state.setRegister(10, fsync((int) state.register(10)));
            case SYS_FDATASYNC -> state.setRegister(10, fdatasync((int) state.register(10)));
            case SYS_EXIT_GROUP -> {
                requestProcessExit(state.register(10));
                throw new ProgramExitException(state.register(10));
            }
            case SYS_EXIT -> exitThread(state, state.register(10));
            case SYS_SET_TID_ADDRESS -> state.setRegister(10, setTidAddress(state, state.register(10)));
            case SYS_FUTEX -> state.setRegister(10, futex(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14),
                    state.register(15)));
            case SYS_SET_ROBUST_LIST -> state.setRegister(10, setRobustList(state, state.register(10), state.register(11)));
            case SYS_GET_ROBUST_LIST -> state.setRegister(10, getRobustList(
                    state,
                    state.register(10),
                    state.register(11),
                    state.register(12)));
            case SYS_NANOSLEEP -> state.setRegister(10, nanosleep(state.register(10), state.register(11)));
            case SYS_CLOCK_GETTIME -> state.setRegister(10, clockGettime(state.register(10), state.register(11)));
            case SYS_CLOCK_GETRES -> state.setRegister(10, clockGetres(state.register(10), state.register(11)));
            case SYS_CLOCK_NANOSLEEP -> state.setRegister(10, clockNanosleep(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            case SYS_SCHED_GETAFFINITY -> state.setRegister(10, schedGetaffinity(state.register(10), state.register(11), state.register(12)));
            case SYS_SCHED_YIELD -> state.setRegister(10, schedYield());
            case SYS_KILL -> state.setRegister(10, kill(state.register(10), state.register(11)));
            case SYS_TKILL -> state.setRegister(10, tkill(state.register(10), state.register(11)));
            case SYS_TGKILL -> state.setRegister(10, tgkill(state.register(10), state.register(11), state.register(12)));
            case SYS_SIGALTSTACK -> state.setRegister(10, sigaltstack(state, state.register(10), state.register(11)));
            case SYS_RT_SIGACTION -> state.setRegister(10, rtSigaction(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            case SYS_RT_SIGPROCMASK -> state.setRegister(10, rtSigprocmask(
                    state,
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            case SYS_GETRESUID -> state.setRegister(10, getresid(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    credentials.realUserId(),
                    credentials.effectiveUserId(),
                    credentials.savedUserId()));
            case SYS_SETRESUID -> state.setRegister(10, setresid(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    credentials.realUserId(),
                    credentials.effectiveUserId(),
                    credentials.savedUserId()));
            case SYS_GETRESGID -> state.setRegister(10, getresid(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    credentials.realGroupId(),
                    credentials.effectiveGroupId(),
                    credentials.savedGroupId()));
            case SYS_SETRESGID -> state.setRegister(10, setresid(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    credentials.realGroupId(),
                    credentials.effectiveGroupId(),
                    credentials.savedGroupId()));
            case SYS_SETFSUID -> state.setRegister(10, setfsid(
                    state.register(10),
                    credentials.effectiveUserId()));
            case SYS_SETFSGID -> state.setRegister(10, setfsid(
                    state.register(10),
                    credentials.effectiveGroupId()));
            case SYS_TIMES -> state.setRegister(10, times(state.register(10)));
            case SYS_SETPGID -> state.setRegister(10, setpgid(state.register(10), state.register(11)));
            case SYS_GETPGID -> state.setRegister(10, getpgid(state.register(10)));
            case SYS_SETSID -> state.setRegister(10, setsid());
            case SYS_GETGROUPS -> state.setRegister(10, getgroups(state.register(10), state.register(11)));
            case SYS_UNAME -> state.setRegister(10, uname(state.register(10)));
            case SYS_GETRUSAGE -> state.setRegister(10, getrusage(state.register(10), state.register(11)));
            case SYS_PRCTL -> state.setRegister(10, prctl(
                    state,
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14)));
            case SYS_GETCPU -> state.setRegister(10, getcpu(state.register(10), state.register(11)));
            case SYS_GETTIMEOFDAY -> state.setRegister(10, gettimeofday(state.register(10), state.register(11)));
            case SYS_GETPID -> state.setRegister(10, process.id());
            case SYS_GETTID -> state.setRegister(10, state.threadId());
            case SYS_GETPPID -> state.setRegister(10, process.parentId());
            case SYS_GETUID -> state.setRegister(10, credentials.realUserId());
            case SYS_GETEUID -> state.setRegister(10, credentials.effectiveUserId());
            case SYS_GETGID -> state.setRegister(10, credentials.realGroupId());
            case SYS_GETEGID -> state.setRegister(10, credentials.effectiveGroupId());
            case SYS_SYSINFO -> state.setRegister(10, sysinfo(state.register(10)));
            case SYS_SOCKET -> state.setRegister(10, socket(state.register(10), state.register(11), state.register(12)));
            case SYS_GETSOCKNAME, SYS_GETPEERNAME -> state.setRegister(10, ENOTSOCK);
            case SYS_CLONE -> state.setRegister(10, clone(
                    state,
                    pc,
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14)));
            case SYS_CLONE3 -> state.setRegister(10, clone3(
                    state,
                    pc,
                    state.register(10),
                    state.register(11)));
            case SYS_EXECVE -> state.setRegister(10, execve(
                    state,
                    state.register(10),
                    state.register(11),
                    state.register(12)));
            case SYS_BRK -> state.setRegister(10, brk(state.register(10)));
            case SYS_MUNMAP -> state.setRegister(10, munmap(state.register(10), state.register(11)));
            case SYS_MREMAP -> state.setRegister(10, mremap(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14)));
            case SYS_MMAP -> state.setRegister(10, mmap(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14),
                    state.register(15)));
            case SYS_MPROTECT -> state.setRegister(10, mprotect(state.register(10), state.register(11), state.register(12)));
            case SYS_MINCORE -> state.setRegister(10, mincore(state.register(10), state.register(11), state.register(12)));
            case SYS_MADVISE -> state.setRegister(10, madvise(state.register(10), state.register(11), state.register(12)));
            case SYS_RISCV_HWPROBE -> state.setRegister(10, riscvHwprobe(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14),
                    state));
            case SYS_WAIT4 -> state.setRegister(10, wait4(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            case SYS_PRLIMIT64 -> state.setRegister(10, prlimit64(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            case SYS_SYNCFS -> state.setRegister(10, syncfs((int) state.register(10)));
            case SYS_RENAMEAT2 -> state.setRegister(10, renameat2(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14)));
            case SYS_GETRANDOM -> state.setRegister(10, getrandom(state.register(10), state.register(11), state.register(12)));
            case SYS_MEMBARRIER -> state.setRegister(10, membarrier(state.register(10), state.register(11), state.register(12)));
            case SYS_STATX -> state.setRegister(10, statx(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14)));
            case SYS_RSEQ -> state.setRegister(10, rseq(
                    state,
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            case SYS_FACCESSAT2 -> state.setRegister(10, faccessat(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
                default -> throw new RiscVException(unsupportedEcallMessage(state, pc, callNumber));
            }
        } finally {
            state.restorePointerMask(previousMask);
        }
    }

    /// Creates a child-process Linux syscall handler.
    @Override
    protected GuestSyscalls createChildSyscalls(Memory childMemory, GuestProcess childProcess) {
        return new LinuxGuestSyscalls(this, childMemory, childProcess);
    }
}
