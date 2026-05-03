// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.IllegalInstructionException;
import org.glavo.riscv.exception.MemoryAccessException;
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
import java.util.ArrayDeque;

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
            @Nullable HostPath hostRoot) {
        super(memory, in, out, err, initialProgramBreak, hostRoot);
    }

    /// Creates a Linux syscall handler backed by the supplied streams, resolved root mount, and guest time source.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            @Nullable HostPath hostRoot,
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
            String hostRootPath) {
        super(memory, in, out, err, initialProgramBreak, hostRootPath);
    }

    /// Creates a Linux syscall handler backed by the supplied streams, lazy root mount, and guest time source.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            String hostRootPath,
            TimeSource timeSource) {
        super(memory, in, out, err, initialProgramBreak, hostRootPath, timeSource);
    }

    /// Creates a Linux syscall handler backed by streams, lazy root mount, guest time source, and guest thread runner.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            String hostRootPath,
            TimeSource timeSource,
            GuestThreadRunner guestThreadRunner) {
        super(memory, in, out, err, initialProgramBreak, hostRootPath, timeSource, guestThreadRunner);
    }

    /// Creates a Linux syscall handler backed by streams, lazy root mount, guest time source, terminal option,
    /// and guest thread runner.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            String hostRootPath,
            TimeSource timeSource,
            boolean useHostTty,
            GuestThreadRunner guestThreadRunner) {
        super(memory, in, out, err, initialProgramBreak, hostRootPath, timeSource, useHostTty, guestThreadRunner);
    }

    /// Creates a Linux syscall handler backed by the supplied streams, lazy filesystem mounts, and guest time source.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            String @Unmodifiable [] filesystemMountSpecs,
            TimeSource timeSource) {
        super(memory, in, out, err, initialProgramBreak, filesystemMountSpecs, timeSource);
    }

    /// Creates a Linux syscall handler backed by streams, lazy filesystem mounts, guest time source,
    /// and guest thread runner.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            String @Unmodifiable [] filesystemMountSpecs,
            TimeSource timeSource,
            GuestThreadRunner guestThreadRunner) {
        super(memory, in, out, err, initialProgramBreak, filesystemMountSpecs, timeSource, guestThreadRunner);
    }

    /// Creates a Linux syscall handler backed by streams, lazy filesystem mounts, guest time source,
    /// terminal option, and guest thread runner.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
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

    /// Linux `CLONE_VM`.
    private static final long CLONE_VM = 0x00000100L;

    /// Linux `CSIGNAL` clone exit-signal mask.
    private static final long CLONE_EXIT_SIGNAL_MASK = 0xffL;

    /// Linux `SIGCHLD`.
    private static final long SIGNAL_CHILD = 17L;

    /// Linux `SIGILL`.
    private static final long SIGNAL_ILLEGAL_INSTRUCTION = 4L;

    /// Linux `SIGSEGV`.
    private static final long SIGNAL_SEGMENTATION_FAULT = 11L;

    /// Linux `SIG_DFL`.
    private static final long SIGNAL_DEFAULT_HANDLER = 0L;

    /// Linux `SIG_IGN`.
    private static final long SIGNAL_IGNORE_HANDLER = 1L;

    /// Linux `ILL_ILLOPC`.
    private static final int SIGNAL_CODE_ILLEGAL_OPCODE = 1;

    /// Linux `SA_ONSTACK`.
    private static final long SIGNAL_ACTION_ON_STACK = 0x08000000L;

    /// Linux `SA_NODEFER`.
    private static final long SIGNAL_ACTION_NODEFER = 0x40000000L;

    /// Linux `SA_RESETHAND`.
    private static final long SIGNAL_ACTION_RESET_HAND = 0x80000000L;

    /// The byte size of Linux generic 64-bit `siginfo_t`.
    private static final long SIGNAL_INFO_SIZE = 128;

    /// The byte offset of `si_signo` inside Linux generic 64-bit `siginfo_t`.
    private static final long SIGNAL_INFO_SIGNO_OFFSET = 0;

    /// The byte offset of `si_errno` inside Linux generic 64-bit `siginfo_t`.
    private static final long SIGNAL_INFO_ERRNO_OFFSET = Integer.BYTES;

    /// The byte offset of `si_code` inside Linux generic 64-bit `siginfo_t`.
    private static final long SIGNAL_INFO_CODE_OFFSET = 2L * Integer.BYTES;

    /// The byte offset of `si_addr` inside the Linux generic 64-bit `siginfo_t` fault union.
    private static final long SIGNAL_INFO_FAULT_ADDRESS_OFFSET = 2L * Long.BYTES;

    /// The byte offset of `sa_handler` inside Linux RISC-V 64-bit kernel `struct sigaction`.
    private static final long SIGNAL_ACTION_HANDLER_OFFSET = 0;

    /// The byte offset of `sa_flags` inside Linux RISC-V 64-bit kernel `struct sigaction`.
    private static final long SIGNAL_ACTION_FLAGS_OFFSET = Long.BYTES;

    /// The byte offset of `sa_mask` inside Linux RISC-V 64-bit kernel `struct sigaction`.
    private static final long SIGNAL_ACTION_MASK_OFFSET = 2L * Long.BYTES;

    /// The byte offset of `uc_flags` inside Linux RISC-V 64-bit `ucontext_t`.
    private static final long SIGNAL_CONTEXT_FLAGS_OFFSET = 0;

    /// The byte offset of `uc_link` inside Linux RISC-V 64-bit `ucontext_t`.
    private static final long SIGNAL_CONTEXT_LINK_OFFSET = Long.BYTES;

    /// The byte offset of `uc_stack` inside Linux RISC-V 64-bit `ucontext_t`.
    private static final long SIGNAL_CONTEXT_STACK_OFFSET = 2L * Long.BYTES;

    /// The byte offset of `uc_sigmask` inside Linux RISC-V 64-bit `ucontext_t`.
    private static final long SIGNAL_CONTEXT_MASK_OFFSET = 5L * Long.BYTES;

    /// The byte offset of `uc_mcontext` inside Linux RISC-V 64-bit `ucontext_t`.
    /// `mcontext_t` is 16-byte aligned, so glibc inserts padding after `uc_sigmask`.
    private static final long SIGNAL_CONTEXT_MACHINE_OFFSET = 176;

    /// The byte offset of general registers inside Linux RISC-V 64-bit `mcontext_t`.
    private static final long SIGNAL_MACHINE_REGISTERS_OFFSET = 0;

    /// The byte offset of floating-point registers inside Linux RISC-V 64-bit `mcontext_t`.
    private static final long SIGNAL_MACHINE_FLOATING_POINT_OFFSET = 32L * Long.BYTES;

    /// The byte size of Linux RISC-V 64-bit `mcontext_t`.
    private static final long SIGNAL_MACHINE_CONTEXT_SIZE = SIGNAL_MACHINE_FLOATING_POINT_OFFSET + 528;

    /// The byte offset of `struct __riscv_extra_ext_header.reserved` inside `mcontext_t`.
    private static final long SIGNAL_MACHINE_EXTRA_RESERVED_OFFSET = SIGNAL_MACHINE_FLOATING_POINT_OFFSET + 516;

    /// The byte offset of `struct __riscv_extra_ext_header.hdr.magic` inside `mcontext_t`.
    private static final long SIGNAL_MACHINE_EXTRA_MAGIC_OFFSET = SIGNAL_MACHINE_FLOATING_POINT_OFFSET + 520;

    /// The byte offset of `struct __riscv_extra_ext_header.hdr.size` inside `mcontext_t`.
    private static final long SIGNAL_MACHINE_EXTRA_SIZE_OFFSET = SIGNAL_MACHINE_FLOATING_POINT_OFFSET + 524;

    /// The byte offset of `siginfo_t` inside Linux RISC-V 64-bit `rt_sigframe`.
    private static final long SIGNAL_FRAME_INFO_OFFSET = 0;

    /// The byte offset of `ucontext_t` inside Linux RISC-V 64-bit `rt_sigframe`.
    private static final long SIGNAL_FRAME_CONTEXT_OFFSET = SIGNAL_INFO_SIZE;

    /// The rounded byte size of Linux RISC-V 64-bit `rt_sigframe`.
    private static final long SIGNAL_FRAME_SIZE = 1088;

    /// `addi a7, zero, __NR_rt_sigreturn`.
    private static final int SIGNAL_TRAMPOLINE_LOAD_SYSCALL = 0x08b00893;

    /// `ecall`.
    private static final int SIGNAL_TRAMPOLINE_ECALL = 0x00000073;

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

    /// Linux `SYS_RISCV_FLUSH_ICACHE_LOCAL` flag.
    private static final long RISCV_FLUSH_ICACHE_LOCAL = 1;

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

    /// The highest guest user address reported for an SV48-compatible Linux RISC-V process.
    private static final long RISCV_HIGHEST_SV48_USER_ADDRESS = (1L << 47) - 1;

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

    /// Guest signal actions installed through `rt_sigaction`.
    private final @Nullable SignalAction[] signalActions = new SignalAction[(int) MAX_SIGNAL_NUMBER + 1];

    /// Lazily mapped signal-return trampoline, or zero before allocation.
    private long signalTrampolineAddress;

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
            RiscVThreadState state,
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
    protected long clone3(RiscVThreadState state, long pc, long argumentsAddress, long size) {
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
            RiscVThreadState state,
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

        RiscVThreadState child = state.forkForClone(
                childThread,
                pc + Integer.BYTES,
                stackAddress,
                tlsAddress,
                (flags & CLONE_SETTLS) != 0);
        if ((flags & CLONE_CHILD_CLEARTID) != 0) {
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
            RiscVThreadState state,
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
        RiscVThreadState child = state.forkForProcess(
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

        GuestThreadRunner currentRunner = guestThreadRunner;
        if (currentRunner == null) {
            childSyscalls.close();
            childMemory.close();
            return EAGAIN;
        }
        Thread thread;
        try {
            thread = new Thread(() -> {
                try {
                    currentRunner.runGuestThread(childMemory, child);
                } finally {
                    childSyscalls.joinGuestThreads();
                    childSyscalls.close();
                    childMemory.close();
                }
            }, "riscv-guest-process-" + childProcess.id());
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
    protected static long setRobustList(RiscVThreadState state, long headAddress, long length) {
        if (length < 0) {
            return EINVAL;
        }
        state.guestThread().setRobustList(headAddress, length);
        return 0;
    }

    /// Reports the robust futex list registered for one guest thread.
    protected long getRobustList(RiscVThreadState state, long processId, long headAddress, long lengthAddress) {
        @Nullable GuestThread thread = guestThread(state, processId);
        if (thread == null) {
            return ESRCH;
        }
        memory.writeLong(headAddress, thread.robustListHeadAddress());
        memory.writeLong(lengthAddress, thread.robustListLength());
        return 0;
    }

    /// Registers or reports the alternate signal stack for the current guest thread.
    protected long sigaltstack(RiscVThreadState state, long stackAddress, long oldStackAddress) {
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
    protected long riscvHwprobe(long pairsAddress, long pairCount, long cpuSetSize, long cpuSetAddress, long flags, RiscVThreadState state) {
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

    /// Flushes guest instruction-fetch visibility for Linux `riscv_flush_icache`.
    protected long riscvFlushIcache(RiscVThreadState state, long startAddress, long endAddress, long flags) {
        if ((flags & ~RISCV_FLUSH_ICACHE_LOCAL) != 0 || Long.compareUnsigned(startAddress, endAddress) > 0) {
            return EINVAL;
        }

        fenceProcessInstructionFetch();
        if ((flags & RISCV_FLUSH_ICACHE_LOCAL) != 0) {
            state.fenceInstructionFetch();
        }
        return 0;
    }

    /// Writes values for all requested RISC-V hardware probe pairs.
    protected void populateHwprobePairs(long pairsAddress, long pairCount, RiscVThreadState state) {
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
    protected boolean hwprobePairsMatch(long pairsAddress, long pairCount, RiscVThreadState state) {
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
    protected static long hwprobeValue(long key, RiscVThreadState state) {
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
            case (int) RISCV_HWPROBE_KEY_HIGHEST_VIRT_ADDRESS -> RISCV_HIGHEST_SV48_USER_ADDRESS;
            case (int) RISCV_HWPROBE_KEY_TIME_CSR_FREQ -> NANOSECONDS_PER_SECOND;
            default -> throw new AssertionError("validated RISC-V hardware probe key");
        };
    }

    /// Returns the ISA extension bits visible through Linux `riscv_hwprobe`.
    protected static long hwprobeImaExtensions(RiscVThreadState state) {
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


    /// Registers or reports a Linux signal action.
    protected long rtSigaction(long signalNumber, long actionAddress, long oldActionAddress, long sigsetSize) {
        if (sigsetSize != KERNEL_SIGSET_SIZE) {
            return EINVAL;
        }
        if (signalNumber < MIN_SIGNAL_NUMBER || signalNumber > MAX_SIGNAL_NUMBER) {
            return EINVAL;
        }
        if (actionAddress != 0 && (signalNumber == SIGKILL || signalNumber == SIGSTOP)) {
            return EINVAL;
        }

        if (oldActionAddress != 0) {
            writeSignalAction(oldActionAddress, signalActions[(int) signalNumber]);
        }
        if (actionAddress != 0) {
            signalActions[(int) signalNumber] = readSignalAction(actionAddress);
        }
        return 0;
    }

    /// Reads a Linux RISC-V kernel `struct sigaction`.
    protected SignalAction readSignalAction(long actionAddress) {
        return new SignalAction(
                memory.readLong(actionAddress + SIGNAL_ACTION_HANDLER_OFFSET),
                memory.readLong(actionAddress + SIGNAL_ACTION_FLAGS_OFFSET),
                memory.readLong(actionAddress + SIGNAL_ACTION_MASK_OFFSET) & ~UNBLOCKABLE_SIGNAL_MASK);
    }

    /// Writes a Linux RISC-V kernel `struct sigaction`.
    protected void writeSignalAction(long actionAddress, @Nullable SignalAction action) {
        memory.clear(actionAddress, KERNEL_SIGACTION_SIZE);
        if (action != null) {
            memory.writeLong(actionAddress + SIGNAL_ACTION_HANDLER_OFFSET, action.handler());
            memory.writeLong(actionAddress + SIGNAL_ACTION_FLAGS_OFFSET, action.flags());
            memory.writeLong(actionAddress + SIGNAL_ACTION_MASK_OFFSET, action.mask());
        }
    }

    /// Reads and updates the calling guest thread's signal mask.
    protected long rtSigprocmask(RiscVThreadState state, long how, long setAddress, long oldSetAddress, long sigsetSize) {
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

    /// Delivers synchronous illegal-instruction faults to a registered guest `SIGILL` handler.
    @Override
    public boolean handleIllegalInstruction(RiscVThreadState state, IllegalInstructionException exception) {
        @Nullable SignalAction action = signalActions[(int) SIGNAL_ILLEGAL_INSTRUCTION];
        if (action == null
                || action.handler() == SIGNAL_DEFAULT_HANDLER
                || action.handler() == SIGNAL_IGNORE_HANDLER) {
            return false;
        }

        // Illegal instructions can be found while decoding a new dispatch target, before
        // the architectural PC has been materialized for that target.
        state.setPc(exception.address());
        deliverSignal(state, SIGNAL_ILLEGAL_INSTRUCTION, SIGNAL_CODE_ILLEGAL_OPCODE, exception.address(), action);
        return true;
    }

    /// Delivers synchronous memory-access faults to a registered guest `SIGSEGV` handler.
    @Override
    public boolean handleMemoryAccess(RiscVThreadState state, MemoryAccessException exception) {
        @Nullable SignalAction action = signalActions[(int) SIGNAL_SEGMENTATION_FAULT];
        if (action == null
                || action.handler() == SIGNAL_DEFAULT_HANDLER
                || action.handler() == SIGNAL_IGNORE_HANDLER) {
            return false;
        }

        deliverSignal(state, SIGNAL_SEGMENTATION_FAULT, exception.signalCode(), exception.address(), action);
        return true;
    }

    /// Builds a Linux RISC-V `rt_sigframe` and redirects execution to a guest signal handler.
    protected void deliverSignal(
            RiscVThreadState state,
            long signalNumber,
            int signalCode,
            long faultAddress,
            SignalAction action) {
        long frameAddress = signalFrameAddress(state, action);
        if (!memory.isBacked(frameAddress, SIGNAL_FRAME_SIZE)) {
            throw new RiscVException("Guest signal frame is not backed: address=0x"
                    + Long.toUnsignedString(frameAddress, 16)
                    + ", size="
                    + SIGNAL_FRAME_SIZE);
        }

        memory.clear(frameAddress, SIGNAL_FRAME_SIZE);
        writeSignalInfo(frameAddress + SIGNAL_FRAME_INFO_OFFSET, signalNumber, signalCode, faultAddress);
        writeSignalContext(state, frameAddress + SIGNAL_FRAME_CONTEXT_OFFSET);

        long trampolineAddress = ensureSignalTrampoline(state);
        state.setRegister(1, trampolineAddress);
        state.setRegister(2, frameAddress);
        state.setRegister(10, signalNumber);
        state.setRegister(11, frameAddress + SIGNAL_FRAME_INFO_OFFSET);
        state.setRegister(12, frameAddress + SIGNAL_FRAME_CONTEXT_OFFSET);
        state.setPc(action.handler());

        GuestThread thread = state.guestThread();
        long blockedSignals = action.mask();
        if ((action.flags() & SIGNAL_ACTION_NODEFER) == 0) {
            blockedSignals |= signalMask(signalNumber);
        }
        thread.setSignalMask((thread.signalMask() | blockedSignals) & ~UNBLOCKABLE_SIGNAL_MASK);
        if ((action.flags() & SIGNAL_ACTION_RESET_HAND) != 0) {
            signalActions[(int) signalNumber] = null;
        }
    }

    /// Computes the guest stack address used for a new signal frame.
    protected long signalFrameAddress(RiscVThreadState state, SignalAction action) {
        GuestThread thread = state.guestThread();
        long stackPointer = state.register(2);
        if ((action.flags() & SIGNAL_ACTION_ON_STACK) != 0 && thread.alternateSignalStackSize() > 0) {
            stackPointer = thread.alternateSignalStackPointer() + thread.alternateSignalStackSize();
        }
        return alignDown(stackPointer - SIGNAL_FRAME_SIZE, 16);
    }

    /// Writes a Linux generic `siginfo_t` for a synchronous instruction fault.
    protected void writeSignalInfo(long infoAddress, long signalNumber, int signalCode, long faultAddress) {
        memory.writeInt(infoAddress + SIGNAL_INFO_SIGNO_OFFSET, (int) signalNumber);
        memory.writeInt(infoAddress + SIGNAL_INFO_ERRNO_OFFSET, 0);
        memory.writeInt(infoAddress + SIGNAL_INFO_CODE_OFFSET, signalCode);
        memory.writeLong(infoAddress + SIGNAL_INFO_FAULT_ADDRESS_OFFSET, faultAddress);
    }

    /// Writes a Linux RISC-V `ucontext_t` for signal delivery.
    protected void writeSignalContext(RiscVThreadState state, long contextAddress) {
        GuestThread thread = state.guestThread();
        memory.writeLong(contextAddress + SIGNAL_CONTEXT_FLAGS_OFFSET, 0);
        memory.writeLong(contextAddress + SIGNAL_CONTEXT_LINK_OFFSET, 0);
        writeSignalStack(thread, contextAddress + SIGNAL_CONTEXT_STACK_OFFSET);
        memory.writeLong(contextAddress + SIGNAL_CONTEXT_MASK_OFFSET, thread.signalMask());

        long machineContextAddress = contextAddress + SIGNAL_CONTEXT_MACHINE_OFFSET;
        state.writeSignalUserRegisters(machineContextAddress + SIGNAL_MACHINE_REGISTERS_OFFSET);
        state.writeSignalFloatingPointState(
                machineContextAddress + SIGNAL_MACHINE_FLOATING_POINT_OFFSET,
                SIGNAL_MACHINE_CONTEXT_SIZE - SIGNAL_MACHINE_FLOATING_POINT_OFFSET);
        memory.writeInt(machineContextAddress + SIGNAL_MACHINE_EXTRA_RESERVED_OFFSET, 0);
        memory.writeInt(machineContextAddress + SIGNAL_MACHINE_EXTRA_MAGIC_OFFSET, 0);
        memory.writeInt(machineContextAddress + SIGNAL_MACHINE_EXTRA_SIZE_OFFSET, 0);
    }

    /// Restores guest state from the Linux RISC-V signal frame at the current stack pointer.
    protected void rtSigreturn(RiscVThreadState state) {
        long frameAddress = state.register(2);
        if (!memory.isBacked(frameAddress, SIGNAL_FRAME_SIZE)) {
            throw new RiscVException("Guest signal return frame is not backed: address=0x"
                    + Long.toUnsignedString(frameAddress, 16)
                    + ", size="
                    + SIGNAL_FRAME_SIZE);
        }

        long contextAddress = frameAddress + SIGNAL_FRAME_CONTEXT_OFFSET;
        state.guestThread().setSignalMask(memory.readLong(contextAddress + SIGNAL_CONTEXT_MASK_OFFSET)
                & ~UNBLOCKABLE_SIGNAL_MASK);

        long machineContextAddress = contextAddress + SIGNAL_CONTEXT_MACHINE_OFFSET;
        state.readSignalUserRegisters(machineContextAddress + SIGNAL_MACHINE_REGISTERS_OFFSET);
        state.readSignalFloatingPointState(machineContextAddress + SIGNAL_MACHINE_FLOATING_POINT_OFFSET);
    }

    /// Ensures an executable guest trampoline for returning from signal handlers.
    protected long ensureSignalTrampoline(RiscVThreadState state) {
        if (signalTrampolineAddress != 0) {
            return signalTrampolineAddress;
        }

        long address = findMmapAddress(0, pageSize, pageSize);
        if (address == 0 || !ensureMemoryBacking(address, pageSize, Memory.PROTECTION_READ_WRITE_EXECUTE, false)) {
            throw new RiscVException("Failed to allocate guest signal trampoline");
        }

        addMemoryMapping(address, pageSize, Memory.PROTECTION_READ_WRITE_EXECUTE);
        memory.writeInt(address, SIGNAL_TRAMPOLINE_LOAD_SYSCALL);
        memory.writeInt(address + Integer.BYTES, SIGNAL_TRAMPOLINE_ECALL);
        signalTrampolineAddress = address;
        fenceProcessInstructionFetch();
        state.fenceInstructionFetch();
        return address;
    }

    /// Handles the Linux `prctl` operations needed by single-process user-mode guests.
    protected long prctl(
            RiscVThreadState state,
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
    protected long getTidAddress(RiscVThreadState state, long address, long argument3, long argument4, long argument5) {
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
            RiscVThreadState state,
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
            RiscVThreadState state,
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

    /// Linux address family number for netlink sockets.
    private static final long AF_NETLINK = 16;

    /// Linux address family number for Unix domain sockets.
    private static final long AF_UNIX = 1;

    /// Linux socket type mask excluding socket creation flags.
    private static final long SOCK_TYPE_MASK = 0xf;

    /// Linux datagram socket type.
    private static final long SOCK_DGRAM = 2;

    /// Linux raw socket type.
    private static final long SOCK_RAW = 3;

    /// Linux netlink protocol number for route and interface metadata.
    private static final long NETLINK_ROUTE = 0;

    /// Byte size of Linux `struct sockaddr_nl`.
    private static final long SOCKADDR_NL_SIZE = 12;

    /// Byte offset of `nl_family` inside Linux `struct sockaddr_nl`.
    private static final long SOCKADDR_NL_FAMILY_OFFSET = 0;

    /// Byte offset of `nl_pid` inside Linux `struct sockaddr_nl`.
    private static final long SOCKADDR_NL_PID_OFFSET = 4;

    /// Byte offset of `nl_groups` inside Linux `struct sockaddr_nl`.
    private static final long SOCKADDR_NL_GROUPS_OFFSET = 8;

    /// Byte offset of `msg_name` inside Linux RISC-V 64-bit `struct msghdr`.
    private static final long MSGHDR_NAME_OFFSET = 0;

    /// Byte offset of `msg_namelen` inside Linux RISC-V 64-bit `struct msghdr`.
    private static final long MSGHDR_NAME_LENGTH_OFFSET = Long.BYTES;

    /// Byte offset of `msg_iov` inside Linux RISC-V 64-bit `struct msghdr`.
    private static final long MSGHDR_IOV_OFFSET = 2L * Long.BYTES;

    /// Byte offset of `msg_iovlen` inside Linux RISC-V 64-bit `struct msghdr`.
    private static final long MSGHDR_IOV_LENGTH_OFFSET = 3L * Long.BYTES;

    /// Byte offset of `msg_flags` inside Linux RISC-V 64-bit `struct msghdr`.
    private static final long MSGHDR_FLAGS_OFFSET = 6L * Long.BYTES;

    /// Byte size of Linux `struct nlmsghdr`.
    private static final int NETLINK_HEADER_SIZE = 16;

    /// Byte size of a minimal `NLMSG_DONE` response with a zero status payload.
    private static final int NETLINK_DONE_MESSAGE_SIZE = NETLINK_HEADER_SIZE + Integer.BYTES;

    /// Byte offset of `nlmsg_len` inside Linux `struct nlmsghdr`.
    private static final int NETLINK_HEADER_LENGTH_OFFSET = 0;

    /// Byte offset of `nlmsg_type` inside Linux `struct nlmsghdr`.
    private static final int NETLINK_HEADER_TYPE_OFFSET = Integer.BYTES;

    /// Byte offset of `nlmsg_flags` inside Linux `struct nlmsghdr`.
    private static final int NETLINK_HEADER_FLAGS_OFFSET = Integer.BYTES + Short.BYTES;

    /// Byte offset of `nlmsg_seq` inside Linux `struct nlmsghdr`.
    private static final int NETLINK_HEADER_SEQUENCE_OFFSET = 2 * Integer.BYTES;

    /// Byte offset of `nlmsg_pid` inside Linux `struct nlmsghdr`.
    private static final int NETLINK_HEADER_PORT_ID_OFFSET = 3 * Integer.BYTES;

    /// Linux netlink message type for end-of-dump responses.
    private static final int NETLINK_MESSAGE_DONE = 3;

    /// Linux rtnetlink message type for interface records.
    private static final int RTM_NEWLINK = 16;

    /// Linux rtnetlink message type for interface address records.
    private static final int RTM_NEWADDR = 20;

    /// Linux rtnetlink message type for route records.
    private static final int RTM_NEWROUTE = 24;

    /// Linux rtnetlink request type for interface records.
    private static final int RTM_GETLINK = 18;

    /// Linux rtnetlink request type for interface address records.
    private static final int RTM_GETADDR = 22;

    /// Linux rtnetlink request type for route records.
    private static final int RTM_GETROUTE = 26;

    /// Linux netlink flag marking one message as part of a multipart response.
    private static final int NETLINK_FLAG_MULTI = 2;

    /// Linux route attribute type for the outgoing interface index.
    private static final int RTA_OIF = 4;

    /// Linux route attribute type for the gateway address.
    private static final int RTA_GATEWAY = 5;

    /// Linux route attribute type for the route metric.
    private static final int RTA_PRIORITY = 6;

    /// Linux route attribute type for the preferred source address.
    private static final int RTA_PREFSRC = 7;

    /// Linux interface attribute type for the interface name.
    private static final int IFLA_IFNAME = 3;

    /// Linux interface attribute type for the link-layer address.
    private static final int IFLA_ADDRESS = 1;

    /// Linux interface attribute type for the link-layer broadcast address.
    private static final int IFLA_BROADCAST = 2;

    /// Linux interface address attribute type for the protocol address.
    private static final int IFA_ADDRESS = 1;

    /// Linux interface address attribute type for the local address.
    private static final int IFA_LOCAL = 2;

    /// Linux interface address attribute type for the interface label.
    private static final int IFA_LABEL = 3;

    /// Linux interface address attribute type for the IPv4 broadcast address.
    private static final int IFA_BROADCAST = 4;

    /// Linux interface index used for the synthetic loopback interface.
    private static final int LOOPBACK_INTERFACE_INDEX = 1;

    /// Linux interface index used for the synthetic Ethernet interface.
    private static final int ETHERNET_INTERFACE_INDEX = 2;

    /// Linux ARPHRD_LOOPBACK hardware type.
    private static final int LOOPBACK_HARDWARE_TYPE = 772;

    /// Linux ARPHRD_ETHER hardware type.
    private static final int ETHERNET_HARDWARE_TYPE = 1;

    /// Linux interface flags for an up and running loopback interface.
    private static final int LOOPBACK_INTERFACE_FLAGS = 0x49;

    /// Linux interface flags for an up and running Ethernet interface.
    private static final int ETHERNET_INTERFACE_FLAGS = 0x11043;

    /// Linux address family number for IPv4.
    private static final int AF_INET = 2;

    /// Linux rtnetlink host scope used by loopback addresses.
    private static final int RT_SCOPE_HOST = 254;

    /// Linux rtnetlink universe scope used by regular IPv4 addresses.
    private static final int RT_SCOPE_UNIVERSE = 0;

    /// Linux main routing table id.
    private static final int RT_TABLE_MAIN = 254;

    /// Linux static route protocol id.
    private static final int RTPROT_STATIC = 4;

    /// Linux unicast route type id.
    private static final int RTN_UNICAST = 1;

    /// Linux permanent interface-address flag.
    private static final int IFA_F_PERMANENT = 0x80;

    /// Linux socket type creation flags accepted by the minimal socket runtime.
    private static final long SUPPORTED_SOCKET_TYPE_FLAGS = O_NONBLOCK | O_CLOEXEC;

    /// Linux `SIOCGIFNAME` ioctl request number.
    private static final long SIOCGIFNAME = 0x8910L;

    /// Linux `SIOCGIFINDEX` ioctl request number.
    private static final long SIOCGIFINDEX = 0x8933L;

    /// Linux `IFNAMSIZ`.
    private static final int INTERFACE_NAME_SIZE = 16;

    /// Byte offset of `ifr_ifindex` in Linux `struct ifreq`.
    private static final long IFREQ_INTERFACE_INDEX_OFFSET = INTERFACE_NAME_SIZE;

    /// Minimum byte size needed for Linux `struct ifreq` name/index ioctls.
    private static final long IFREQ_NAME_INDEX_SIZE = IFREQ_INTERFACE_INDEX_OFFSET + Integer.BYTES;

    /// Creates a guest socket for the minimal network-related Linux runtime.
    protected long socket(long domain, long type, long protocol) {
        long socketType = type & SOCK_TYPE_MASK;
        long typeFlags = type & ~SOCK_TYPE_MASK;
        if ((typeFlags & ~SUPPORTED_SOCKET_TYPE_FLAGS) != 0) {
            return EINVAL;
        }
        if (domain == AF_NETLINK
                && (socketType == SOCK_RAW || socketType == SOCK_DGRAM)
                && protocol == NETLINK_ROUTE) {
            return addOpenFile(OpenFile.socket(new NetlinkRouteSocket(), (typeFlags & O_NONBLOCK) != 0));
        }
        if (domain == AF_UNIX && socketType == SOCK_DGRAM && protocol == 0) {
            return addOpenFile(OpenFile.socket(new NetworkInterfaceIoctlSocket(), (typeFlags & O_NONBLOCK) != 0));
        }
        return EAFNOSUPPORT;
    }

    /// Handles Linux network-interface ioctl requests on generic ioctl sockets.
    @Override
    protected long ioctl(int fileDescriptor, long request, long argument) {
        if (networkInterfaceIoctlSocket(fileDescriptor) == null) {
            return super.ioctl(fileDescriptor, request, argument);
        }
        if (!memory.isBacked(argument, IFREQ_NAME_INDEX_SIZE)) {
            return EFAULT;
        }

        if (request == SIOCGIFNAME) {
            @Nullable String name = interfaceName(memory.readInt(argument + IFREQ_INTERFACE_INDEX_OFFSET));
            if (name == null) {
                return ENODEV;
            }
            writeInterfaceName(argument, name);
            return 0;
        }
        if (request == SIOCGIFINDEX) {
            int index = interfaceIndex(readInterfaceName(argument));
            if (index == 0) {
                return ENODEV;
            }
            memory.writeInt(argument + IFREQ_INTERFACE_INDEX_OFFSET, index);
            return 0;
        }
        return ENOTTY;
    }

    /// Returns the network-interface ioctl socket backing a descriptor, or null when it is not such a socket.
    private @Nullable NetworkInterfaceIoctlSocket networkInterfaceIoctlSocket(int fileDescriptor) {
        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null || !openFile.isSocket()) {
            return null;
        }

        GuestSocket socket = openFile.socket();
        return socket instanceof NetworkInterfaceIoctlSocket ioctlSocket ? ioctlSocket : null;
    }

    /// Returns the synthetic interface name for an index, or null when absent.
    private static @Nullable String interfaceName(int index) {
        return switch (index) {
            case LOOPBACK_INTERFACE_INDEX -> "lo";
            case ETHERNET_INTERFACE_INDEX -> "eth0";
            default -> null;
        };
    }

    /// Returns the synthetic interface index for a name, or zero when absent.
    private static int interfaceIndex(String name) {
        return switch (name) {
            case "lo" -> LOOPBACK_INTERFACE_INDEX;
            case "eth0" -> ETHERNET_INTERFACE_INDEX;
            default -> 0;
        };
    }

    /// Reads a null-terminated Linux interface name from a guest `struct ifreq`.
    private String readInterfaceName(long ifreqAddress) {
        byte[] bytes = memory.readBytes(ifreqAddress, INTERFACE_NAME_SIZE);
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }

    /// Writes a null-terminated Linux interface name into a guest `struct ifreq`.
    private void writeInterfaceName(long ifreqAddress, String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, INTERFACE_NAME_SIZE - 1);
        memory.clear(ifreqAddress, INTERFACE_NAME_SIZE);
        memory.writeBytes(ifreqAddress, bytes, 0, length);
    }

    /// Binds a minimal netlink socket to a local port id.
    protected long bind(int fileDescriptor, long address, long addressLength) {
        @Nullable NetlinkRouteSocket socket = netlinkRouteSocket(fileDescriptor);
        if (socket == null) {
            return ENOTSOCK;
        }
        if (address != 0) {
            if (addressLength < SOCKADDR_NL_SIZE || !memory.isBacked(address, SOCKADDR_NL_SIZE)) {
                return EFAULT;
            }
            if (Short.toUnsignedLong(memory.readShort(address + SOCKADDR_NL_FAMILY_OFFSET)) != AF_NETLINK) {
                return EINVAL;
            }
            socket.bind(Integer.toUnsignedLong(memory.readInt(address + SOCKADDR_NL_PID_OFFSET)));
        } else {
            socket.bind(0);
        }
        return 0;
    }

    /// Sends one minimal netlink route request and queues an empty dump response.
    protected long sendto(
            int fileDescriptor,
            long bufferAddress,
            long length,
            long flags,
            long destinationAddress,
            long destinationLength) {
        @Nullable NetlinkRouteSocket socket = netlinkRouteSocket(fileDescriptor);
        if (socket == null) {
            return ENOTSOCK;
        }
        if (length < 0 || length > Integer.MAX_VALUE) {
            return EINVAL;
        }
        if (!memory.isBacked(bufferAddress, length)) {
            return EFAULT;
        }
        if (destinationAddress != 0 && destinationLength < SOCKADDR_NL_SIZE) {
            return EINVAL;
        }

        byte[] request = memory.readBytes(bufferAddress, Math.min(length, NETLINK_HEADER_SIZE));
        socket.enqueueResponse(request, process.id());
        return length;
    }

    /// Receives one queued minimal netlink route response into a flat guest buffer.
    protected long recvfrom(
            int fileDescriptor,
            long bufferAddress,
            long length,
            long flags,
            long sourceAddress,
            long sourceLengthAddress) {
        @Nullable NetlinkRouteSocket socket = netlinkRouteSocket(fileDescriptor);
        if (socket == null) {
            return ENOTSOCK;
        }
        if (length < 0 || length > Integer.MAX_VALUE) {
            return EINVAL;
        }
        if (!memory.isBacked(bufferAddress, length)) {
            return EFAULT;
        }

        @Nullable byte[] response = socket.pollResponse();
        if (response == null) {
            return EAGAIN;
        }
        writeSockaddrNl(sourceAddress, sourceLengthAddress, 0);
        int count = (int) Math.min(length, response.length);
        memory.writeBytes(bufferAddress, response, 0, count);
        return count;
    }

    /// Sends one minimal netlink route request described by a guest `struct msghdr`.
    protected long sendmsg(int fileDescriptor, long messageAddress, long flags) {
        @Nullable NetlinkRouteSocket socket = netlinkRouteSocket(fileDescriptor);
        if (socket == null) {
            return ENOTSOCK;
        }
        if (!memory.isBacked(messageAddress, MSGHDR_FLAGS_OFFSET + Integer.BYTES)) {
            return EFAULT;
        }

        long iovecAddress = memory.readLong(messageAddress + MSGHDR_IOV_OFFSET);
        long iovecCount = memory.readLong(messageAddress + MSGHDR_IOV_LENGTH_OFFSET);
        if (iovecCount < 0 || iovecCount > IOV_MAX) {
            return EINVAL;
        }

        byte[] request = readIovPrefix(iovecAddress, iovecCount, NETLINK_HEADER_SIZE);
        if (request == null) {
            return EFAULT;
        }
        long byteCount = iovByteCount(iovecAddress, iovecCount);
        if (byteCount < 0) {
            return byteCount;
        }
        socket.enqueueResponse(request, process.id());
        return byteCount;
    }

    /// Receives one queued minimal netlink route response into guest `struct msghdr` buffers.
    protected long recvmsg(int fileDescriptor, long messageAddress, long flags) {
        @Nullable NetlinkRouteSocket socket = netlinkRouteSocket(fileDescriptor);
        if (socket == null) {
            return ENOTSOCK;
        }
        if (!memory.isBacked(messageAddress, MSGHDR_FLAGS_OFFSET + Integer.BYTES)) {
            return EFAULT;
        }

        @Nullable byte[] response = socket.pollResponse();
        if (response == null) {
            return EAGAIN;
        }

        long nameAddress = memory.readLong(messageAddress + MSGHDR_NAME_OFFSET);
        long nameLength = Integer.toUnsignedLong(memory.readInt(messageAddress + MSGHDR_NAME_LENGTH_OFFSET));
        if (nameAddress != 0 && nameLength >= SOCKADDR_NL_SIZE) {
            writeSockaddrNl(nameAddress, 0, 0);
            memory.writeInt(messageAddress + MSGHDR_NAME_LENGTH_OFFSET, (int) SOCKADDR_NL_SIZE);
        }
        memory.writeInt(messageAddress + MSGHDR_FLAGS_OFFSET, 0);

        long iovecAddress = memory.readLong(messageAddress + MSGHDR_IOV_OFFSET);
        long iovecCount = memory.readLong(messageAddress + MSGHDR_IOV_LENGTH_OFFSET);
        if (iovecCount < 0 || iovecCount > IOV_MAX) {
            return EINVAL;
        }
        return writeIovBytes(iovecAddress, iovecCount, response);
    }

    /// Writes the local netlink socket address for `getsockname`.
    protected long getsockname(int fileDescriptor, long address, long lengthAddress) {
        @Nullable NetlinkRouteSocket socket = netlinkRouteSocket(fileDescriptor);
        if (socket == null) {
            return ENOTSOCK;
        }
        return writeSockaddrNl(address, lengthAddress, socket.portId(process.id()));
    }

    /// Reports that the minimal netlink socket has no connected peer.
    protected long getpeername(int fileDescriptor, long address, long lengthAddress) {
        return netlinkRouteSocket(fileDescriptor) == null ? ENOTSOCK : EINVAL;
    }

    /// Returns the netlink route socket backing a descriptor, or null when the descriptor is not such a socket.
    private @Nullable NetlinkRouteSocket netlinkRouteSocket(int fileDescriptor) {
        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null || !openFile.isSocket()) {
            return null;
        }

        GuestSocket socket = openFile.socket();
        return socket instanceof NetlinkRouteSocket netlinkSocket ? netlinkSocket : null;
    }

    /// Writes a Linux `struct sockaddr_nl` through either direct or value-result length addressing.
    private long writeSockaddrNl(long address, long lengthAddress, long portId) {
        if (lengthAddress != 0 && !memory.isBacked(lengthAddress, Integer.BYTES)) {
            return EFAULT;
        }
        if (address != 0) {
            if (!memory.isBacked(address, SOCKADDR_NL_SIZE)) {
                return EFAULT;
            }
            memory.clear(address, SOCKADDR_NL_SIZE);
            memory.writeShort(address + SOCKADDR_NL_FAMILY_OFFSET, (short) AF_NETLINK);
            memory.writeInt(address + SOCKADDR_NL_PID_OFFSET, (int) portId);
            memory.writeInt(address + SOCKADDR_NL_GROUPS_OFFSET, 0);
        }
        if (lengthAddress != 0) {
            memory.writeInt(lengthAddress, (int) SOCKADDR_NL_SIZE);
        }
        return 0;
    }

    /// Reads the netlink sequence number from a request header prefix.
    private static int netlinkSequence(byte[] header) {
        return header.length >= NETLINK_HEADER_SEQUENCE_OFFSET + Integer.BYTES
                ? getLittleEndianInt(header, NETLINK_HEADER_SEQUENCE_OFFSET)
                : 0;
    }

    /// Reads the netlink message type from a request header prefix.
    private static int netlinkType(byte[] header) {
        return header.length >= NETLINK_HEADER_TYPE_OFFSET + Short.BYTES
                ? getLittleEndianShort(header, NETLINK_HEADER_TYPE_OFFSET)
                : 0;
    }

    /// Reads a little-endian unsigned 16-bit value from a byte array.
    private static int getLittleEndianShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    /// Reads a little-endian 32-bit value from a byte array.
    private static int getLittleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    /// Writes a little-endian 16-bit value into a byte array.
    private static void putLittleEndianShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }

    /// Writes a little-endian 32-bit value into a byte array.
    private static void putLittleEndianInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }

    /// Returns a 4-byte aligned netlink payload size.
    private static int netlinkAlign(int value) {
        return (value + 3) & ~3;
    }

    /// Writes a netlink message header into a response buffer.
    private static void putNetlinkHeader(byte[] bytes, int type, int sequence, long portId) {
        putLittleEndianInt(bytes, NETLINK_HEADER_LENGTH_OFFSET, bytes.length);
        putLittleEndianShort(bytes, NETLINK_HEADER_TYPE_OFFSET, type);
        putLittleEndianShort(bytes, NETLINK_HEADER_FLAGS_OFFSET, NETLINK_FLAG_MULTI);
        putLittleEndianInt(bytes, NETLINK_HEADER_SEQUENCE_OFFSET, sequence);
        putLittleEndianInt(bytes, NETLINK_HEADER_PORT_ID_OFFSET, (int) portId);
    }

    /// Writes a Linux `struct rtattr` and its payload into a response buffer.
    private static void putRouteAttribute(byte[] bytes, int offset, int type, byte[] payload) {
        putLittleEndianShort(bytes, offset, Short.BYTES * 2 + payload.length);
        putLittleEndianShort(bytes, offset + Short.BYTES, type);
        System.arraycopy(payload, 0, bytes, offset + 2 * Short.BYTES, payload.length);
    }

    /// Returns a new byte array containing one little-endian 32-bit value.
    private static byte[] littleEndianIntBytes(int value) {
        byte[] bytes = new byte[Integer.BYTES];
        putLittleEndianInt(bytes, 0, value);
        return bytes;
    }

    /// Builds one synthetic `RTM_NEWLINK` message.
    private static byte[] netlinkLinkMessage(
            int sequence,
            long portId,
            int hardwareType,
            int interfaceIndex,
            int interfaceFlags,
            byte[] name,
            byte @Nullable [] hardwareAddress,
            byte @Nullable [] broadcastAddress) {
        int attributeOffset = NETLINK_HEADER_SIZE + 16;
        int nameAttributeSize = netlinkAlign(2 * Short.BYTES + name.length);
        int hardwareAddressAttributeSize = hardwareAddress == null
                ? 0
                : netlinkAlign(2 * Short.BYTES + hardwareAddress.length);
        int broadcastAddressAttributeSize = broadcastAddress == null
                ? 0
                : netlinkAlign(2 * Short.BYTES + broadcastAddress.length);
        int size = attributeOffset
                + hardwareAddressAttributeSize
                + broadcastAddressAttributeSize
                + nameAttributeSize;
        byte[] response = new byte[size];
        putNetlinkHeader(response, RTM_NEWLINK, sequence, portId);
        putLittleEndianShort(response, NETLINK_HEADER_SIZE + 2, hardwareType);
        putLittleEndianInt(response, NETLINK_HEADER_SIZE + 4, interfaceIndex);
        putLittleEndianInt(response, NETLINK_HEADER_SIZE + 8, interfaceFlags);
        putLittleEndianInt(response, NETLINK_HEADER_SIZE + 12, -1);
        int nextAttributeOffset = attributeOffset;
        if (hardwareAddress != null) {
            putRouteAttribute(response, nextAttributeOffset, IFLA_ADDRESS, hardwareAddress);
            nextAttributeOffset += hardwareAddressAttributeSize;
        }
        if (broadcastAddress != null) {
            putRouteAttribute(response, nextAttributeOffset, IFLA_BROADCAST, broadcastAddress);
            nextAttributeOffset += broadcastAddressAttributeSize;
        }
        putRouteAttribute(response, nextAttributeOffset, IFLA_IFNAME, name);
        return response;
    }

    /// Builds one synthetic IPv4 `RTM_NEWADDR` message.
    private static byte[] netlinkIpv4AddressMessage(
            int sequence,
            long portId,
            int prefixLength,
            int scope,
            int interfaceIndex,
            byte[] address,
            byte @Nullable [] broadcastAddress,
            byte[] label) {
        int attributeOffset = NETLINK_HEADER_SIZE + 8;
        int addressAttributeSize = netlinkAlign(2 * Short.BYTES + address.length);
        int broadcastAddressAttributeSize = broadcastAddress == null
                ? 0
                : netlinkAlign(2 * Short.BYTES + broadcastAddress.length);
        int labelAttributeSize = netlinkAlign(2 * Short.BYTES + label.length);
        byte[] response = new byte[attributeOffset
                + 2 * addressAttributeSize
                + broadcastAddressAttributeSize
                + labelAttributeSize];
        putNetlinkHeader(response, RTM_NEWADDR, sequence, portId);
        response[NETLINK_HEADER_SIZE] = AF_INET;
        response[NETLINK_HEADER_SIZE + 1] = (byte) prefixLength;
        response[NETLINK_HEADER_SIZE + 2] = (byte) IFA_F_PERMANENT;
        response[NETLINK_HEADER_SIZE + 3] = (byte) scope;
        putLittleEndianInt(response, NETLINK_HEADER_SIZE + 4, interfaceIndex);
        putRouteAttribute(response, attributeOffset, IFA_ADDRESS, address);
        putRouteAttribute(response, attributeOffset + addressAttributeSize, IFA_LOCAL, address);
        int nextAttributeOffset = attributeOffset + 2 * addressAttributeSize;
        if (broadcastAddress != null) {
            putRouteAttribute(response, nextAttributeOffset, IFA_BROADCAST, broadcastAddress);
            nextAttributeOffset += broadcastAddressAttributeSize;
        }
        putRouteAttribute(response, nextAttributeOffset, IFA_LABEL, label);
        return response;
    }

    /// Builds one synthetic loopback `RTM_NEWLINK` message.
    private static byte[] netlinkLoopbackLinkMessage(int sequence, long portId) {
        return netlinkLinkMessage(
                sequence,
                portId,
                LOOPBACK_HARDWARE_TYPE,
                LOOPBACK_INTERFACE_INDEX,
                LOOPBACK_INTERFACE_FLAGS,
                new byte[]{'l', 'o', 0},
                null,
                null);
    }

    /// Builds one synthetic Ethernet `RTM_NEWLINK` message.
    private static byte[] netlinkEthernetLinkMessage(int sequence, long portId) {
        return netlinkLinkMessage(
                sequence,
                portId,
                ETHERNET_HARDWARE_TYPE,
                ETHERNET_INTERFACE_INDEX,
                ETHERNET_INTERFACE_FLAGS,
                new byte[]{'e', 't', 'h', '0', 0},
                new byte[]{0x02, 0, 0, 0, 0, 0x01},
                new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff});
    }

    /// Builds one synthetic loopback IPv4 `RTM_NEWADDR` message.
    private static byte[] netlinkLoopbackAddressMessage(int sequence, long portId) {
        return netlinkIpv4AddressMessage(
                sequence,
                portId,
                8,
                RT_SCOPE_HOST,
                LOOPBACK_INTERFACE_INDEX,
                new byte[]{127, 0, 0, 1},
                null,
                new byte[]{'l', 'o', 0});
    }

    /// Builds one synthetic Ethernet IPv4 `RTM_NEWADDR` message.
    private static byte[] netlinkEthernetAddressMessage(int sequence, long portId) {
        return netlinkIpv4AddressMessage(
                sequence,
                portId,
                24,
                RT_SCOPE_UNIVERSE,
                ETHERNET_INTERFACE_INDEX,
                new byte[]{10, 0, 2, 15},
                new byte[]{10, 0, 2, (byte) 255},
                new byte[]{'e', 't', 'h', '0', 0});
    }

    /// Builds one synthetic IPv4 default-route `RTM_NEWROUTE` message.
    private static byte[] netlinkDefaultRouteMessage(int sequence, long portId) {
        int attributeOffset = NETLINK_HEADER_SIZE + 12;
        int integerAttributeSize = netlinkAlign(2 * Short.BYTES + Integer.BYTES);
        byte[] gateway = new byte[]{10, 0, 2, 2};
        byte[] preferredSource = new byte[]{10, 0, 2, 15};
        byte[] response = new byte[attributeOffset + 4 * integerAttributeSize];
        putNetlinkHeader(response, RTM_NEWROUTE, sequence, portId);
        response[NETLINK_HEADER_SIZE] = AF_INET;
        response[NETLINK_HEADER_SIZE + 4] = (byte) RT_TABLE_MAIN;
        response[NETLINK_HEADER_SIZE + 5] = RTPROT_STATIC;
        response[NETLINK_HEADER_SIZE + 6] = RT_SCOPE_UNIVERSE;
        response[NETLINK_HEADER_SIZE + 7] = RTN_UNICAST;
        putRouteAttribute(response, attributeOffset, RTA_OIF, littleEndianIntBytes(ETHERNET_INTERFACE_INDEX));
        putRouteAttribute(response, attributeOffset + integerAttributeSize, RTA_GATEWAY, gateway);
        putRouteAttribute(response, attributeOffset + 2 * integerAttributeSize, RTA_PRIORITY, littleEndianIntBytes(0));
        putRouteAttribute(response, attributeOffset + 3 * integerAttributeSize, RTA_PREFSRC, preferredSource);
        return response;
    }

    /// Builds one synthetic `NLMSG_DONE` message.
    private static byte[] netlinkDoneMessage(int sequence, long portId) {
        byte[] response = new byte[NETLINK_DONE_MESSAGE_SIZE];
        putNetlinkHeader(response, NETLINK_MESSAGE_DONE, sequence, portId);
        putLittleEndianInt(response, NETLINK_HEADER_SIZE, 0);
        return response;
    }

    /// Reads the first bytes from guest iovecs, returning null when any range is invalid.
    private byte @Nullable [] readIovPrefix(long iovecAddress, long iovecCount, int maximumLength) {
        if (maximumLength == 0) {
            return new byte[0];
        }
        if (iovecCount > 0 && !memory.isBacked(iovecAddress, iovecCount * IOVEC_SIZE)) {
            return null;
        }

        byte[] result = new byte[maximumLength];
        int copied = 0;
        for (long index = 0; index < iovecCount && copied < maximumLength; index++) {
            long entryAddress = iovecAddress + index * IOVEC_SIZE;
            long baseAddress = memory.readLong(entryAddress + IOVEC_BASE_OFFSET);
            long length = memory.readLong(entryAddress + IOVEC_LENGTH_OFFSET);
            if (length < 0 || length > Integer.MAX_VALUE || !memory.isBacked(baseAddress, length)) {
                return null;
            }
            int copyLength = (int) Math.min(length, maximumLength - copied);
            byte[] bytes = memory.readBytes(baseAddress, copyLength);
            System.arraycopy(bytes, 0, result, copied, copyLength);
            copied += copyLength;
        }

        if (copied == result.length) {
            return result;
        }

        byte[] truncated = new byte[copied];
        System.arraycopy(result, 0, truncated, 0, copied);
        return truncated;
    }

    /// Returns the total byte count described by guest iovecs, or a raw negative Linux error.
    private long iovByteCount(long iovecAddress, long iovecCount) {
        if (iovecCount > 0 && !memory.isBacked(iovecAddress, iovecCount * IOVEC_SIZE)) {
            return EFAULT;
        }
        long total = 0;
        for (long index = 0; index < iovecCount; index++) {
            long length = memory.readLong(iovecAddress + index * IOVEC_SIZE + IOVEC_LENGTH_OFFSET);
            if (length < 0 || Long.MAX_VALUE - total < length) {
                return EINVAL;
            }
            total += length;
        }
        return total;
    }

    /// Writes bytes into guest iovecs and returns the copied byte count, or a raw negative Linux error.
    private long writeIovBytes(long iovecAddress, long iovecCount, byte[] bytes) {
        if (iovecCount > 0 && !memory.isBacked(iovecAddress, iovecCount * IOVEC_SIZE)) {
            return EFAULT;
        }

        int copied = 0;
        for (long index = 0; index < iovecCount && copied < bytes.length; index++) {
            long entryAddress = iovecAddress + index * IOVEC_SIZE;
            long baseAddress = memory.readLong(entryAddress + IOVEC_BASE_OFFSET);
            long length = memory.readLong(entryAddress + IOVEC_LENGTH_OFFSET);
            if (length < 0 || length > Integer.MAX_VALUE || !memory.isBacked(baseAddress, length)) {
                return EFAULT;
            }
            int copyLength = (int) Math.min(length, bytes.length - copied);
            memory.writeBytes(baseAddress, bytes, copied, copyLength);
            copied += copyLength;
        }
        return copied;
    }

    /// Stores the queued responses for one minimal `NETLINK_ROUTE` socket.
    private static final class NetlinkRouteSocket implements GuestSocket {
        /// Queued response datagrams.
        private final ArrayDeque<byte[]> responses = new ArrayDeque<>();

        /// Local netlink port id, or zero until assigned lazily.
        private long portId;

        /// Stores the requested local port id.
        void bind(long requestedPortId) {
            this.portId = requestedPortId;
        }

        /// Returns the local port id, assigning the process id when the guest requested automatic binding.
        long portId(long processId) {
            if (portId == 0) {
                portId = processId;
            }
            return portId;
        }

        /// Queues synthetic responses for the supplied rtnetlink request.
        void enqueueResponse(byte[] request, long processId) {
            long localPortId = portId(processId);
            int sequence = netlinkSequence(request);
            int type = netlinkType(request);
            if (type == RTM_GETLINK) {
                responses.add(netlinkLoopbackLinkMessage(sequence, localPortId));
                responses.add(netlinkEthernetLinkMessage(sequence, localPortId));
            } else if (type == RTM_GETADDR) {
                responses.add(netlinkLoopbackAddressMessage(sequence, localPortId));
                responses.add(netlinkEthernetAddressMessage(sequence, localPortId));
            } else if (type == RTM_GETROUTE) {
                responses.add(netlinkDefaultRouteMessage(sequence, localPortId));
            }
            responses.add(netlinkDoneMessage(sequence, localPortId));
        }

        /// Returns and removes the next queued response, or null when none is available.
        @Nullable byte[] pollResponse() {
            return responses.poll();
        }
    }

    /// Marks a generic socket that exists only for Linux network-interface ioctl helpers.
    private static final class NetworkInterfaceIoctlSocket implements GuestSocket {
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
    protected long rseq(RiscVThreadState state, long address, long length, long flags, long signature) {
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
        System.arraycopy(parent.signalActions, 0, signalActions, 0, signalActions.length);
        signalTrampolineAddress = parent.signalTrampolineAddress;
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

    /// The Linux RISC-V syscall number for `rt_sigreturn`.
    private static final int SYS_RT_SIGRETURN = 139;

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

    /// The Linux RISC-V syscall number for `bind`.
    private static final int SYS_BIND = 200;

    /// The Linux RISC-V syscall number for `getsockname`.
    private static final int SYS_GETSOCKNAME = 204;

    /// The Linux RISC-V syscall number for `getpeername`.
    private static final int SYS_GETPEERNAME = 205;

    /// The Linux RISC-V syscall number for `sendto`.
    private static final int SYS_SENDTO = 206;

    /// The Linux RISC-V syscall number for `recvfrom`.
    private static final int SYS_RECVFROM = 207;

    /// The Linux RISC-V syscall number for `sendmsg`.
    private static final int SYS_SENDMSG = 211;

    /// The Linux RISC-V syscall number for `recvmsg`.
    private static final int SYS_RECVMSG = 212;

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

    /// The Linux RISC-V syscall number for `riscv_flush_icache`.
    private static final int SYS_RISCV_FLUSH_ICACHE = 259;

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
    public void handle(RiscVThreadState state, long pc) {
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
            case SYS_RT_SIGRETURN -> rtSigreturn(state);
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
            case SYS_BIND -> state.setRegister(10, bind(
                    (int) state.register(10),
                    state.register(11),
                    state.register(12)));
            case SYS_GETSOCKNAME -> state.setRegister(10, getsockname(
                    (int) state.register(10),
                    state.register(11),
                    state.register(12)));
            case SYS_GETPEERNAME -> state.setRegister(10, getpeername(
                    (int) state.register(10),
                    state.register(11),
                    state.register(12)));
            case SYS_SENDTO -> state.setRegister(10, sendto(
                    (int) state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14),
                    state.register(15)));
            case SYS_RECVFROM -> state.setRegister(10, recvfrom(
                    (int) state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14),
                    state.register(15)));
            case SYS_SENDMSG -> state.setRegister(10, sendmsg(
                    (int) state.register(10),
                    state.register(11),
                    state.register(12)));
            case SYS_RECVMSG -> state.setRegister(10, recvmsg(
                    (int) state.register(10),
                    state.register(11),
                    state.register(12)));
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
            case SYS_RISCV_FLUSH_ICACHE -> state.setRegister(10, riscvFlushIcache(
                    state,
                    state.register(10),
                    state.register(11),
                    state.register(12)));
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

    /// Linux RISC-V kernel signal action state.
    protected record SignalAction(long handler, long flags, long mask) {
    }
}
