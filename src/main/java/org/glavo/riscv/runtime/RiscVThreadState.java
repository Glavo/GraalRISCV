// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.ProgramExitException;
import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.parser.ElfImage;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/// Stores mutable RISC-V execution state for one guest thread.
@NotNullByDefault
public final class RiscVThreadState {
    /// The number of integer registers in RV64I.
    private static final int REGISTER_COUNT = 32;

    /// The standard RISC-V integer register ABI names.
    private static final String[] REGISTER_ABI_NAMES = {
            "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
            "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
            "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
            "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
    };

    /// The number of floating-point registers in RV64GC.
    private static final int FLOATING_POINT_REGISTER_COUNT = 32;

    /// The number of recent program-counter transitions retained for diagnostics.
    private static final int RECENT_PC_TRANSITION_COUNT = 16;

    /// The number of recent checked instructions retained for diagnostics.
    private static final int RECENT_INSTRUCTION_COUNT = 32;

    /// JVM-wide unique values used to distinguish instruction bytes in the decoded-block cache.
    private static final AtomicLong NEXT_INSTRUCTION_FETCH_GENERATION = new AtomicLong(1);

    /// The `fflags` floating-point exception flags CSR address.
    private static final int FFLAGS_CSR = 0x001;

    /// The `frm` floating-point rounding mode CSR address.
    private static final int FRM_CSR = 0x002;

    /// The `fcsr` floating-point control and status CSR address.
    private static final int FCSR_CSR = 0x003;

    /// The first writable vector CSR address.
    private static final int VSTART_CSR = 0x008;

    /// The fixed-point saturation vector CSR address.
    private static final int VXSAT_CSR = 0x009;

    /// The fixed-point rounding-mode vector CSR address.
    private static final int VXRM_CSR = 0x00a;

    /// The combined vector fixed-point control CSR address.
    private static final int VCSR_CSR = 0x00f;

    /// The `satp` supervisor address translation and protection CSR address.
    private static final int SATP_CSR = 0x180;

    /// The `stvec` supervisor trap-vector CSR address.
    private static final int STVEC_CSR = 0x105;

    /// The `mstatus` machine status CSR address.
    private static final int MSTATUS_CSR = 0x300;

    /// The `medeleg` machine exception delegation CSR address.
    private static final int MEDELEG_CSR = 0x302;

    /// The `mideleg` machine interrupt delegation CSR address.
    private static final int MIDELEG_CSR = 0x303;

    /// The `mie` machine interrupt-enable CSR address.
    private static final int MIE_CSR = 0x304;

    /// The `mtvec` machine trap-vector CSR address.
    private static final int MTVEC_CSR = 0x305;

    /// The `mepc` machine exception program-counter CSR address.
    private static final int MEPC_CSR = 0x341;

    /// The first PMP configuration CSR address used by `riscv-test-env`.
    private static final int PMPCFG0_CSR = 0x3a0;

    /// The first PMP address CSR address used by `riscv-test-env`.
    private static final int PMPADDR0_CSR = 0x3b0;

    /// The `mnstatus` resumable NMI CSR address used by `riscv-test-env`.
    private static final int MNSTATUS_CSR = 0x744;

    /// The `mhartid` machine hardware-thread id CSR address.
    private static final int MHARTID_CSR = 0xf14;

    /// The `cycle` user counter CSR address.
    private static final int CYCLE_CSR = 0xc00;

    /// The `time` user counter CSR address.
    private static final int TIME_CSR = 0xc01;

    /// The `instret` user counter CSR address.
    private static final int INSTRET_CSR = 0xc02;

    /// The first `hpmcounter` user hardware performance counter CSR address.
    private static final int FIRST_HPMCOUNTER_CSR = 0xc03;

    /// The last `hpmcounter` user hardware performance counter CSR address.
    private static final int LAST_HPMCOUNTER_CSR = 0xc1f;

    /// The read-only vector length CSR address.
    private static final int VL_CSR = 0xc20;

    /// The read-only vector type CSR address.
    private static final int VTYPE_CSR = 0xc21;

    /// The read-only vector register byte-length CSR address.
    private static final int VLENB_CSR = 0xc22;

    /// The writable bit mask for `fflags`.
    private static final int FFLAGS_MASK = 0x1f;

    /// The writable bit mask for `frm`.
    private static final int FRM_MASK = 0x7;

    /// The writable bit mask for `fcsr`.
    private static final int FCSR_MASK = 0xff;

    /// The mutable integer register file; `registers[0]` is never written and remains the hardwired zero register.
    private final long[] registers = new long[REGISTER_COUNT];

    /// The mutable floating-point register file, stored as raw 64-bit values.
    private final long[] floatingPointRegisters = new long[FLOATING_POINT_REGISTER_COUNT];

    /// The mutable vector architectural state.
    private final VectorUnit vectorUnit;

    /// The guest memory for this execution.
    private final Memory memory;

    /// The maximum number of guest instructions to execute, or zero when unlimited.
    private final long maxInstructions;

    /// Whether instruction tracing is enabled.
    private final boolean trace;

    /// Whether this state can retire whole decoded blocks without per-instruction checks.
    private final boolean canRetireBlock;

    /// The stream used for instruction trace output.
    private final PrintStream traceStream;

    /// The optional `tohost` symbol guest address.
    private long tohostAddress;

    /// The optional `fromhost` symbol guest address.
    private long fromhostAddress;

    /// Whether guest memory stores can trigger simulator side effects.
    private boolean storeSideEffectsEnabled;

    /// The syscall handler for guest environment calls.
    private final GuestSyscalls syscalls;

    /// The Linux user-mode thread state paired with this architectural state.
    private final GuestThread thread;

    /// The current guest program counter.
    private long pc;

    /// Recent program-counter transition sources.
    private final long[] recentPcTransitionSources = new long[RECENT_PC_TRANSITION_COUNT];

    /// Recent program-counter transition targets.
    private final long[] recentPcTransitionTargets = new long[RECENT_PC_TRANSITION_COUNT];

    /// The number of recorded program-counter transitions.
    private long recentPcTransitionCount;

    /// Recent checked instruction addresses.
    private final long[] recentInstructionAddresses = new long[RECENT_INSTRUCTION_COUNT];

    /// Recent checked instruction raw encodings.
    private final int[] recentInstructionRaws = new int[RECENT_INSTRUCTION_COUNT];

    /// The number of recorded checked instructions.
    private long recentInstructionCount;

    /// The compatibility storage for `mepc`, used by `mret` during `riscv-test-env` startup.
    private long machineExceptionProgramCounter;

    /// The number of retired guest instructions.
    private long instructionCount;

    /// The current instruction-fetch visibility generation used by decoded-block caches.
    private long instructionFetchGeneration = nextInstructionFetchGeneration();

    /// The low eight bits of the floating-point control and status register.
    private int floatingPointControlStatus;

    /// The active LR/SC reservation address, or `ABSENT_ADDRESS` when none exists.
    private long reservationAddress = ElfImage.ABSENT_ADDRESS;

    /// The byte width of the active LR/SC reservation.
    private int reservationLength;

    /// The value read by the active LR/SC reservation.
    private long reservationValue;

    /// Creates a new RISC-V thread state container.
    public RiscVThreadState(
            Memory memory,
            long maxInstructions,
            boolean trace,
            long tohostAddress,
            long fromhostAddress,
            GuestSyscalls syscalls) {
        this(memory, maxInstructions, trace, tohostAddress, fromhostAddress, syscalls, System.err);
    }

    /// Creates a new RISC-V thread state container with an explicit trace stream.
    public RiscVThreadState(
            Memory memory,
            long maxInstructions,
            boolean trace,
            long tohostAddress,
            long fromhostAddress,
            GuestSyscalls syscalls,
            OutputStream traceStream) {
        this(memory, maxInstructions, trace, tohostAddress, fromhostAddress, syscalls, traceStream, VectorUnit.DEFAULT_VLEN_BITS);
    }

    /// Creates a new RISC-V thread state container with an explicit trace stream and vector register length.
    public RiscVThreadState(
            Memory memory,
            long maxInstructions,
            boolean trace,
            long tohostAddress,
            long fromhostAddress,
            GuestSyscalls syscalls,
            OutputStream traceStream,
            int vectorVlenBits) {
        this(
                memory,
                maxInstructions,
                trace,
                tohostAddress,
                fromhostAddress,
                syscalls,
                asPrintStream(traceStream),
                syscalls.initialThread(),
                vectorVlenBits);
    }

    /// Creates a new RISC-V thread state container with a prepared trace print stream.
    private RiscVThreadState(
            Memory memory,
            long maxInstructions,
            boolean trace,
            long tohostAddress,
            long fromhostAddress,
            GuestSyscalls syscalls,
            PrintStream traceStream,
            GuestThread thread,
            int vectorVlenBits) {
        this.memory = memory;
        this.vectorUnit = new VectorUnit(vectorVlenBits);
        this.maxInstructions = maxInstructions;
        this.trace = trace;
        this.canRetireBlock = maxInstructions == 0 && !trace;
        this.traceStream = traceStream;
        this.tohostAddress = tohostAddress;
        this.fromhostAddress = fromhostAddress;
        this.storeSideEffectsEnabled = hasTohostSideEffects(tohostAddress);
        this.syscalls = syscalls;
        this.thread = thread;
    }

    /// Returns the guest memory for this execution.
    public Memory memory() {
        return memory;
    }

    /// Returns the current guest program counter.
    public long pc() {
        return pc;
    }

    /// Formats the current integer execution state for failure diagnostics.
    public String formatExecutionContext() {
        StringBuilder builder = new StringBuilder(512);
        builder.append("pc=0x")
                .append(Long.toUnsignedString(pc, 16))
                .append(", instructions=")
                .append(instructionCount);
        for (int index = 0; index < REGISTER_COUNT; index++) {
            builder.append(", x")
                    .append(index)
                    .append('/')
                    .append(REGISTER_ABI_NAMES[index])
                    .append("=0x")
                    .append(Long.toUnsignedString(registers[index], 16));
        }
        long transitionCount = Math.min(recentPcTransitionCount, RECENT_PC_TRANSITION_COUNT);
        if (transitionCount > 0) {
            builder.append(", recentPcTransitions=[");
            long first = recentPcTransitionCount - transitionCount;
            for (long ordinal = first; ordinal < recentPcTransitionCount; ordinal++) {
                int index = (int) (ordinal & (RECENT_PC_TRANSITION_COUNT - 1));
                if (ordinal > first) {
                    builder.append(", ");
                }
                builder.append("0x")
                        .append(Long.toUnsignedString(recentPcTransitionSources[index], 16))
                        .append("->0x")
                        .append(Long.toUnsignedString(recentPcTransitionTargets[index], 16));
            }
            builder.append(']');
            int lastIndex = (int) ((recentPcTransitionCount - 1) & (RECENT_PC_TRANSITION_COUNT - 1));
            appendInstructionBytes(builder, "lastPcSource", recentPcTransitionSources[lastIndex]);
        }
        appendRecentInstructions(builder);
        appendInstructionBytes(builder, "pc", pc);
        return builder.toString();
    }

    /// Appends recently retired checked instructions.
    private void appendRecentInstructions(StringBuilder builder) {
        long count = Math.min(recentInstructionCount, RECENT_INSTRUCTION_COUNT);
        if (count == 0) {
            return;
        }

        builder.append(", recentInstructions=[");
        long first = recentInstructionCount - count;
        for (long ordinal = first; ordinal < recentInstructionCount; ordinal++) {
            int index = (int) (ordinal & (RECENT_INSTRUCTION_COUNT - 1));
            if (ordinal > first) {
                builder.append(", ");
            }
            builder.append("0x")
                    .append(Long.toUnsignedString(recentInstructionAddresses[index], 16))
                    .append(":0x")
                    .append(Integer.toUnsignedString(recentInstructionRaws[index], 16));
        }
        builder.append(']');
    }

    /// Appends raw instruction bytes from a guest address when they are still readable.
    private void appendInstructionBytes(StringBuilder builder, String label, long address) {
        try {
            int half = memory.readUnsignedShort(address);
            builder.append(", ")
                    .append(label)
                    .append("Raw16=0x")
                    .append(Integer.toUnsignedString(half, 16));
            if ((half & 0b11) == 0b11) {
                builder.append(", ")
                        .append(label)
                        .append("Raw32=0x")
                        .append(Integer.toUnsignedString(memory.readInstructionInt(address), 16));
            }
        } catch (RiscVException ignored) {
            builder.append(", ")
                    .append(label)
                    .append("Raw=<unmapped>");
        }
    }

    /// Updates the current guest program counter.
    public void setPc(long pc) {
        setPcFromSource(this.pc, pc);
    }

    /// Updates the current guest program counter with an explicit transition source.
    public void setPcFromInstruction(long instructionAddress, long pc) {
        setPcFromSource(instructionAddress, pc);
    }

    /// Updates the current guest program counter and records the transition source.
    private void setPcFromSource(long sourcePc, long pc) {
        long maskedSourcePc = sourcePc & thread.pointerMask();
        long maskedPc = pc & thread.pointerMask();
        if (maskedSourcePc != maskedPc) {
            int transitionIndex = (int) (recentPcTransitionCount & (RECENT_PC_TRANSITION_COUNT - 1));
            recentPcTransitionSources[transitionIndex] = maskedSourcePc;
            recentPcTransitionTargets[transitionIndex] = maskedPc;
            recentPcTransitionCount++;
        }
        this.pc = maskedPc;
    }

    /// Returns an integer register value.
    public long register(int index) {
        return registers[index];
    }

    /// Updates an integer register value, ignoring writes to `x0`.
    public void setRegister(int index, long value) {
        if (index != 0) {
            registers[index] = value;
        }
    }

    /// Returns an integer register value for an already decoded register index.
    public long decodedRegister(int index) {
        return registers[index];
    }

    /// Updates an integer register value for an already decoded register index, ignoring writes to `x0`.
    public void setDecodedRegister(int index, long value) {
        if (index != 0) {
            registers[index] = value;
        }
    }

    /// Returns the mutable integer register array for decoded hot-path execution.
    public long[] decodedRegisters() {
        return registers;
    }

    /// Returns the raw 64-bit value of a floating-point register.
    public long floatingPointRegister(int index) {
        checkFloatingPointRegisterIndex(index);
        return floatingPointRegisters[index];
    }

    /// Updates the raw 64-bit value of a floating-point register.
    public void setFloatingPointRegister(int index, long value) {
        checkFloatingPointRegisterIndex(index);
        floatingPointRegisters[index] = value;
    }

    /// Returns a raw floating-point register value for an already decoded register index.
    public long decodedFloatingPointRegister(int index) {
        return floatingPointRegisters[index];
    }

    /// Updates a raw floating-point register value for an already decoded register index.
    public void setDecodedFloatingPointRegister(int index, long value) {
        floatingPointRegisters[index] = value;
    }

    /// Writes Linux RISC-V `user_regs_struct` register state to guest memory.
    void writeSignalUserRegisters(long address) {
        memory.writeLong(address, pc);
        for (int index = 1; index < REGISTER_COUNT; index++) {
            memory.writeLong(address + (long) index * Long.BYTES, registers[index]);
        }
    }

    /// Restores Linux RISC-V `user_regs_struct` register state from guest memory.
    void readSignalUserRegisters(long address) {
        pc = memory.readLong(address) & thread.pointerMask();
        registers[0] = 0;
        for (int index = 1; index < REGISTER_COUNT; index++) {
            registers[index] = memory.readLong(address + (long) index * Long.BYTES);
        }
    }

    /// Writes Linux RISC-V double-precision floating-point signal state to guest memory.
    void writeSignalFloatingPointState(long address, long unionSize) {
        memory.clear(address, unionSize);
        for (int index = 0; index < FLOATING_POINT_REGISTER_COUNT; index++) {
            memory.writeLong(address + (long) index * Long.BYTES, floatingPointRegisters[index]);
        }
        memory.writeInt(address + (long) FLOATING_POINT_REGISTER_COUNT * Long.BYTES, floatingPointControlStatus);
    }

    /// Restores Linux RISC-V double-precision floating-point signal state from guest memory.
    void readSignalFloatingPointState(long address) {
        for (int index = 0; index < FLOATING_POINT_REGISTER_COUNT; index++) {
            floatingPointRegisters[index] = memory.readLong(address + (long) index * Long.BYTES);
        }
        floatingPointControlStatus = memory.readInt(address + (long) FLOATING_POINT_REGISTER_COUNT * Long.BYTES) & FCSR_MASK;
    }

    /// Returns the mutable vector architectural state.
    public VectorUnit vectorUnit() {
        return vectorUnit;
    }

    /// Returns the optional `fromhost` symbol guest address.
    public long fromhostAddress() {
        return fromhostAddress;
    }

    /// Returns the syscall handler for guest environment calls.
    public GuestSyscalls syscalls() {
        return syscalls;
    }

    /// Returns the Linux thread id represented by this guest state.
    public int threadId() {
        return thread.id();
    }

    /// Returns the Linux user-mode thread state paired with this architectural state.
    GuestThread guestThread() {
        return thread;
    }

    /// Returns the active RISC-V userspace pointer mask length for this guest thread.
    public int pointerMaskLength() {
        return thread.pointerMaskLength();
    }

    /// Returns the active RISC-V userspace pointer mask for this guest thread.
    public long pointerMask() {
        return thread.pointerMask();
    }

    /// Activates this guest thread's pointer mask for the current host thread.
    public long enterPointerMask() {
        return memory.enterPointerMaskLength(thread.pointerMaskLength());
    }

    /// Activates this guest thread's syscall pointer mask for the current host thread.
    public long enterSyscallPointerMask() {
        return memory.enterPointerMaskLength(thread.taggedAddressAbiEnabled() ? thread.pointerMaskLength() : 0);
    }

    /// Restores the current host thread's previous pointer mask.
    public void restorePointerMask(long previousMask) {
        memory.restorePointerMask(previousMask);
    }

    /// Applies the current host thread's active RISC-V userspace pointer mask to a guest address.
    public long maskPointer(long address) {
        return address & thread.pointerMask();
    }

    /// Creates the child architectural state produced by a Linux thread-style `clone`.
    RiscVThreadState forkForClone(
            GuestThread childThread,
            long childPc,
            long stackAddress,
            long tlsAddress,
            boolean setThreadPointer) {
        RiscVThreadState child = new RiscVThreadState(
                memory,
                maxInstructions,
                trace,
                tohostAddress,
                fromhostAddress,
                syscalls,
                traceStream,
                childThread,
                vectorUnit.vlenBits());
        copyArchitecturalStateTo(child, childPc, stackAddress, tlsAddress, setThreadPointer);
        return child;
    }

    /// Creates the child architectural state produced by a Linux process-style `clone`.
    RiscVThreadState forkForProcess(
            GuestThread childThread,
            Memory childMemory,
            GuestSyscalls childSyscalls,
            long childPc,
            long stackAddress,
            long tlsAddress,
            boolean setThreadPointer) {
        RiscVThreadState child = new RiscVThreadState(
                childMemory,
                maxInstructions,
                trace,
                tohostAddress,
                fromhostAddress,
                childSyscalls,
                traceStream,
                childThread,
                vectorUnit.vlenBits());
        copyArchitecturalStateTo(child, childPc, stackAddress, tlsAddress, setThreadPointer);
        return child;
    }

    /// Copies register and CSR state into a freshly allocated clone child.
    private void copyArchitecturalStateTo(
            RiscVThreadState child,
            long childPc,
            long stackAddress,
            long tlsAddress,
            boolean setThreadPointer) {
        System.arraycopy(registers, 0, child.registers, 0, registers.length);
        System.arraycopy(floatingPointRegisters, 0, child.floatingPointRegisters, 0, floatingPointRegisters.length);
        vectorUnit.copyTo(child.vectorUnit);
        child.floatingPointControlStatus = floatingPointControlStatus;
        child.instructionFetchGeneration = instructionFetchGeneration;
        child.pc = childPc;
        child.recentPcTransitionCount = 0;
        child.recentInstructionCount = 0;
        child.registers[0] = 0;
        if (stackAddress != 0) {
            child.registers[2] = stackAddress;
        }
        if (setThreadPointer) {
            child.registers[4] = tlsAddress;
        }
        child.registers[10] = 0;
    }

    /// Replaces architectural state after a successful Linux `execve`.
    void resetForExec(long entryPoint, long stackPointer, long tohostAddress, long fromhostAddress) {
        Arrays.fill(registers, 0);
        Arrays.fill(floatingPointRegisters, 0);
        vectorUnit.reset();
        this.pc = entryPoint & thread.pointerMask();
        this.recentPcTransitionCount = 0;
        this.recentInstructionCount = 0;
        this.tohostAddress = tohostAddress;
        this.fromhostAddress = fromhostAddress;
        this.storeSideEffectsEnabled = hasTohostSideEffects(tohostAddress);
        registers[2] = stackPointer;
        machineExceptionProgramCounter = 0;
        floatingPointControlStatus = 0;
        reservationAddress = ElfImage.ABSENT_ADDRESS;
        reservationLength = 0;
        instructionFetchGeneration = nextInstructionFetchGeneration();
    }

    /// Returns the guest clear-child-TID address for this thread.
    long clearChildTidAddress() {
        return thread.clearChildTidAddress();
    }

    /// Updates the guest clear-child-TID address for this thread.
    void setClearChildTidAddress(long clearChildTidAddress) {
        thread.setClearChildTidAddress(clearChildTidAddress);
    }

    /// Reads a supported control and status register.
    public long readControlStatusRegister(int csr) {
        if (isHardwarePerformanceCounterCsr(csr)) {
            return 0;
        }

        return switch (csr) {
            case FFLAGS_CSR -> floatingPointControlStatus & FFLAGS_MASK;
            case FRM_CSR -> (floatingPointControlStatus >>> 5) & FRM_MASK;
            case FCSR_CSR -> floatingPointControlStatus & FCSR_MASK;
            case VSTART_CSR, VXSAT_CSR, VXRM_CSR, VCSR_CSR -> vectorUnit.readWritableControlStatusRegister(csr);
            case SATP_CSR, STVEC_CSR, MSTATUS_CSR, MEDELEG_CSR, MIDELEG_CSR, MIE_CSR, MTVEC_CSR,
                 PMPCFG0_CSR, PMPADDR0_CSR, MNSTATUS_CSR -> 0;
            case MEPC_CSR -> machineExceptionProgramCounter;
            case MHARTID_CSR -> 0;
            case CYCLE_CSR, TIME_CSR, INSTRET_CSR -> instructionCount;
            case VL_CSR -> vectorUnit.vectorLength();
            case VTYPE_CSR -> vectorUnit.vectorType();
            case VLENB_CSR -> vectorUnit.vlenBytes();
            default -> throw unsupportedControlStatusRegister(csr);
        };
    }

    /// Writes a supported writable control and status register.
    public void writeControlStatusRegister(int csr, long value) {
        if (isReadOnlyCounterCsr(csr)) {
            throw readOnlyControlStatusRegister(csr);
        }

        switch (csr) {
            case FFLAGS_CSR -> {
                floatingPointControlStatus = (floatingPointControlStatus & ~FFLAGS_MASK)
                        | ((int) value & FFLAGS_MASK);
            }
            case FRM_CSR -> {
                floatingPointControlStatus = (floatingPointControlStatus & ~(FRM_MASK << 5))
                        | (((int) value & FRM_MASK) << 5);
            }
            case FCSR_CSR -> floatingPointControlStatus = (int) value & FCSR_MASK;
            case VSTART_CSR, VXSAT_CSR, VXRM_CSR, VCSR_CSR -> vectorUnit.writeWritableControlStatusRegister(csr, value);
            case SATP_CSR, STVEC_CSR, MSTATUS_CSR, MEDELEG_CSR, MIDELEG_CSR, MIE_CSR, MTVEC_CSR,
                 PMPCFG0_CSR, PMPADDR0_CSR, MNSTATUS_CSR -> {
            }
            case MEPC_CSR -> machineExceptionProgramCounter = value;
            default -> throw unsupportedControlStatusRegister(csr);
        }
    }

    /// Returns the compatibility `mepc` value used by `mret`.
    public long machineExceptionProgramCounter() {
        return machineExceptionProgramCounter;
    }

    /// Returns the current floating-point dynamic rounding mode.
    public int floatingPointRoundingMode() {
        return (floatingPointControlStatus >>> 5) & FRM_MASK;
    }

    /// ORs floating-point exception flags into `fflags`.
    public void addFloatingPointFlags(int flags) {
        floatingPointControlStatus |= flags & FFLAGS_MASK;
    }

    /// Records one guest instruction retirement and enforces the instruction budget.
    public void beforeInstruction(long address, int raw) {
        if (canRetireBlock) {
            retireInstructionUnchecked();
            return;
        }

        beforeInstructionChecked(address, raw);
    }

    /// Returns true when guest instructions can be retired in block-sized batches.
    public boolean canRetireBlock() {
        return canRetireBlock;
    }

    /// Records multiple guest instruction retirements for an already decoded block.
    public void retireBlock(int retiredInstructions) {
        instructionCount += retiredInstructions;
    }

    /// Records one guest instruction retirement without budget or trace checks.
    public void retireInstructionUnchecked() {
        instructionCount++;
    }

    /// Returns the current instruction-fetch visibility generation.
    public long instructionFetchGeneration() {
        return Math.max(instructionFetchGeneration, syscalls.processInstructionFetchGeneration());
    }

    /// Makes subsequent instruction fetches see code modifications ordered before a `fence.i`.
    public void fenceInstructionFetch() {
        instructionFetchGeneration = nextInstructionFetchGeneration();
    }

    /// Returns the next JVM-wide instruction-fetch generation.
    static long nextInstructionFetchGeneration() {
        return NEXT_INSTRUCTION_FETCH_GENERATION.getAndUpdate(
                current -> current == Long.MAX_VALUE ? 1 : current + 1);
    }

    /// Records one guest instruction retirement on configurations that need checks or tracing.
    public void beforeInstructionChecked(long address, int raw) {
        if (maxInstructions > 0 && instructionCount >= maxInstructions) {
            throw new RiscVException("Guest instruction limit exceeded: " + maxInstructions
                    + ", pc=0x" + Long.toUnsignedString(address, 16));
        }

        recordRecentInstruction(address, raw);
        instructionCount++;
        if (trace) {
            traceInstruction(traceStream, address, raw);
        }
    }

    /// Records one instruction for failure diagnostics in checked execution modes.
    private void recordRecentInstruction(long address, int raw) {
        int index = (int) (recentInstructionCount & (RECENT_INSTRUCTION_COUNT - 1));
        recentInstructionAddresses[index] = address;
        recentInstructionRaws[index] = raw;
        recentInstructionCount++;
    }

    /// Sets the LR/SC reservation address, data width, and observed value.
    public void reserve(long address, int length, long value) {
        reservationAddress = address;
        reservationLength = length;
        reservationValue = value;
    }

    /// Returns true when the supplied address, data width, and value match the active LR/SC reservation.
    public boolean hasReservation(long address, int length, long value) {
        return reservationAddress == address && reservationLength == length && reservationValue == value;
    }

    /// Clears the active LR/SC reservation.
    public void clearReservation() {
        if (reservationAddress != ElfImage.ABSENT_ADDRESS) {
            reservationAddress = ElfImage.ABSENT_ADDRESS;
            reservationLength = 0;
            reservationValue = 0;
        }
    }

    /// Returns true when this thread currently holds an LR/SC reservation.
    public boolean hasActiveReservation() {
        return reservationAddress != ElfImage.ABSENT_ADDRESS;
    }

    /// Returns true when guest memory stores need simulator side-effect checks.
    public boolean hasStoreSideEffects() {
        return storeSideEffectsEnabled;
    }

    /// Handles simulator side effects after a guest memory store.
    public void afterStore(long address, int length) {
        if (storeSideEffectsEnabled) {
            afterStoreWithSideEffects(address, length);
        }
    }

    /// Handles simulator side effects after a store when side effects are enabled.
    public void afterStoreWithSideEffects(long address, int length) {
        if (Memory.overlaps(address, length, tohostAddress, Long.BYTES)) {
            long value = memory.readLong(tohostAddress);
            if (value != 0) {
                throw new ProgramExitException(value == 1 ? 0 : value >>> 1);
            }
        }
    }

    /// Validates a floating-point register index.
    private static void checkFloatingPointRegisterIndex(int index) {
        if (index < 0 || index >= FLOATING_POINT_REGISTER_COUNT) {
            throw new RiscVException("Invalid floating-point register index: " + index);
        }
    }

    /// Writes a single trace line for a guest instruction.
    private static void traceInstruction(PrintStream stream, long address, int raw) {
        stream.println("pc=0x" + Long.toUnsignedString(address, 16) + " raw=0x" + Integer.toUnsignedString(raw, 16));
    }

    /// Adapts an output stream to a print stream without double-wrapping existing print streams.
    private static PrintStream asPrintStream(OutputStream stream) {
        return stream instanceof PrintStream printStream ? printStream : new PrintStream(stream, true);
    }

    /// Returns true when stores must check for a test-harness `tohost` word.
    private static boolean hasTohostSideEffects(long tohostAddress) {
        return tohostAddress != ElfImage.ABSENT_ADDRESS;
    }

    /// Returns true when the CSR is a base user counter from `Zicntr`.
    private static boolean isBaseCounterCsr(int csr) {
        return csr == CYCLE_CSR || csr == TIME_CSR || csr == INSTRET_CSR;
    }

    /// Returns true when the CSR is a user hardware performance counter from `Zihpm`.
    private static boolean isHardwarePerformanceCounterCsr(int csr) {
        return csr >= FIRST_HPMCOUNTER_CSR && csr <= LAST_HPMCOUNTER_CSR;
    }

    /// Returns true when the CSR is a read-only user counter.
    private static boolean isReadOnlyCounterCsr(int csr) {
        return isBaseCounterCsr(csr) || isHardwarePerformanceCounterCsr(csr);
    }

    /// Creates a read-only CSR diagnostic.
    private static RiscVException readOnlyControlStatusRegister(int csr) {
        return new RiscVException("Control status register is read-only: 0x" + Integer.toUnsignedString(csr, 16));
    }

    /// Creates an unsupported CSR diagnostic.
    private static RiscVException unsupportedControlStatusRegister(int csr) {
        return new RiscVException("Unsupported control status register: 0x" + Integer.toUnsignedString(csr, 16));
    }
}
