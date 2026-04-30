// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import org.glavo.riscv.exception.ProgramExitException;
import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.parser.ElfImage;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.OutputStream;
import java.io.PrintStream;

/// Stores mutable architectural state for one guest execution.
@NotNullByDefault
public final class MachineState {
    /// The number of integer registers in RV64I.
    private static final int REGISTER_COUNT = 32;

    /// The number of floating-point registers in RV64GC.
    private static final int FLOATING_POINT_REGISTER_COUNT = 32;

    /// The `fflags` floating-point exception flags CSR address.
    private static final int FFLAGS_CSR = 0x001;

    /// The `frm` floating-point rounding mode CSR address.
    private static final int FRM_CSR = 0x002;

    /// The `fcsr` floating-point control and status CSR address.
    private static final int FCSR_CSR = 0x003;

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
    private final long tohostAddress;

    /// The optional `fromhost` symbol guest address.
    private final long fromhostAddress;

    /// Whether guest memory stores can trigger simulator side effects.
    private final boolean storeSideEffectsEnabled;

    /// The syscall handler for guest environment calls.
    private final GuestSyscalls syscalls;

    /// The Linux user-mode thread state paired with this architectural state.
    private final GuestThread thread;

    /// The current guest program counter.
    private long pc;

    /// The compatibility storage for `mepc`, used by `mret` during `riscv-test-env` startup.
    private long machineExceptionProgramCounter;

    /// The number of retired guest instructions.
    private long instructionCount;

    /// The current instruction-fetch visibility generation, incremented by `fence.i`.
    private long instructionFetchGeneration;

    /// The low eight bits of the floating-point control and status register.
    private int floatingPointControlStatus;

    /// The active LR/SC reservation address, or `ABSENT_ADDRESS` when none exists.
    private long reservationAddress = ElfImage.ABSENT_ADDRESS;

    /// Creates a new architectural state container.
    public MachineState(
            Memory memory,
            long maxInstructions,
            boolean trace,
            long tohostAddress,
            long fromhostAddress,
            GuestSyscalls syscalls) {
        this(memory, maxInstructions, trace, tohostAddress, fromhostAddress, syscalls, System.err);
    }

    /// Creates a new architectural state container with an explicit trace stream.
    public MachineState(
            Memory memory,
            long maxInstructions,
            boolean trace,
            long tohostAddress,
            long fromhostAddress,
            GuestSyscalls syscalls,
            OutputStream traceStream) {
        this(
                memory,
                maxInstructions,
                trace,
                tohostAddress,
                fromhostAddress,
                syscalls,
                asPrintStream(traceStream),
                syscalls.initialThread());
    }

    /// Creates a new architectural state container with a prepared trace print stream.
    private MachineState(
            Memory memory,
            long maxInstructions,
            boolean trace,
            long tohostAddress,
            long fromhostAddress,
            GuestSyscalls syscalls,
            PrintStream traceStream,
            GuestThread thread) {
        this.memory = memory;
        this.maxInstructions = maxInstructions;
        this.trace = trace;
        this.canRetireBlock = maxInstructions == 0 && !trace;
        this.traceStream = traceStream;
        this.tohostAddress = tohostAddress;
        this.fromhostAddress = fromhostAddress;
        this.storeSideEffectsEnabled = tohostAddress != ElfImage.ABSENT_ADDRESS;
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

    /// Updates the current guest program counter.
    public void setPc(long pc) {
        this.pc = pc;
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

    /// Creates the child architectural state produced by a Linux thread-style `clone`.
    MachineState forkForClone(
            GuestThread childThread,
            long childPc,
            long stackAddress,
            long tlsAddress,
            boolean setThreadPointer) {
        MachineState child = new MachineState(
                memory,
                maxInstructions,
                trace,
                tohostAddress,
                fromhostAddress,
                syscalls,
                traceStream,
                childThread);
        System.arraycopy(registers, 0, child.registers, 0, registers.length);
        System.arraycopy(floatingPointRegisters, 0, child.floatingPointRegisters, 0, floatingPointRegisters.length);
        child.floatingPointControlStatus = floatingPointControlStatus;
        child.instructionFetchGeneration = instructionFetchGeneration;
        child.pc = childPc;
        child.registers[0] = 0;
        child.registers[2] = stackAddress;
        if (setThreadPointer) {
            child.registers[4] = tlsAddress;
        }
        child.registers[10] = 0;
        return child;
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
        return switch (csr) {
            case FFLAGS_CSR -> floatingPointControlStatus & FFLAGS_MASK;
            case FRM_CSR -> (floatingPointControlStatus >>> 5) & FRM_MASK;
            case FCSR_CSR -> floatingPointControlStatus & FCSR_MASK;
            case SATP_CSR, STVEC_CSR, MSTATUS_CSR, MEDELEG_CSR, MIDELEG_CSR, MIE_CSR, MTVEC_CSR,
                 PMPCFG0_CSR, PMPADDR0_CSR, MNSTATUS_CSR -> 0;
            case MEPC_CSR -> machineExceptionProgramCounter;
            case MHARTID_CSR -> 0;
            case CYCLE_CSR, TIME_CSR, INSTRET_CSR -> instructionCount;
            default -> throw unsupportedControlStatusRegister(csr);
        };
    }

    /// Writes a supported writable control and status register.
    public void writeControlStatusRegister(int csr, long value) {
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
            case SATP_CSR, STVEC_CSR, MSTATUS_CSR, MEDELEG_CSR, MIDELEG_CSR, MIE_CSR, MTVEC_CSR,
                 PMPCFG0_CSR, PMPADDR0_CSR, MNSTATUS_CSR -> {
            }
            case MEPC_CSR -> machineExceptionProgramCounter = value;
            case CYCLE_CSR, TIME_CSR, INSTRET_CSR -> throw new RiscVException(
                    "Control status register is read-only: 0x" + Integer.toUnsignedString(csr, 16));
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
        return instructionFetchGeneration;
    }

    /// Makes subsequent instruction fetches see code modifications ordered before a `fence.i`.
    public void fenceInstructionFetch() {
        instructionFetchGeneration++;
    }

    /// Records one guest instruction retirement on configurations that need checks or tracing.
    public void beforeInstructionChecked(long address, int raw) {
        if (maxInstructions > 0 && instructionCount >= maxInstructions) {
            throw new RiscVException("Guest instruction limit exceeded: " + maxInstructions
                    + ", pc=0x" + Long.toUnsignedString(address, 16));
        }

        instructionCount++;
        if (trace) {
            traceInstruction(traceStream, address, raw);
        }
    }

    /// Sets the LR/SC reservation address.
    public void reserve(long address) {
        reservationAddress = address;
    }

    /// Returns true when the supplied address matches the active LR/SC reservation.
    public boolean hasReservation(long address) {
        return reservationAddress == address;
    }

    /// Clears the active LR/SC reservation.
    public void clearReservation() {
        if (reservationAddress != ElfImage.ABSENT_ADDRESS) {
            reservationAddress = ElfImage.ABSENT_ADDRESS;
        }
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
    @CompilerDirectives.TruffleBoundary
    private static void traceInstruction(PrintStream stream, long address, int raw) {
        stream.println("pc=0x" + Long.toUnsignedString(address, 16) + " raw=0x" + Integer.toUnsignedString(raw, 16));
    }

    /// Adapts an output stream to a print stream without double-wrapping existing print streams.
    private static PrintStream asPrintStream(OutputStream stream) {
        return stream instanceof PrintStream printStream ? printStream : new PrintStream(stream, true);
    }

    /// Creates an unsupported CSR diagnostic.
    private static RiscVException unsupportedControlStatusRegister(int csr) {
        return new RiscVException("Unsupported control status register: 0x" + Integer.toUnsignedString(csr, 16));
    }
}
