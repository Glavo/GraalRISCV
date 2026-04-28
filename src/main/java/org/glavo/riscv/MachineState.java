package org.glavo.riscv;

import com.oracle.truffle.api.CompilerDirectives;
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

    /// The mutable integer register file.
    private final long[] registers = new long[REGISTER_COUNT];

    /// The mutable floating-point register file, stored as raw 64-bit values.
    private final long[] floatingPointRegisters = new long[FLOATING_POINT_REGISTER_COUNT];

    /// The guest memory for this execution.
    private final Memory memory;

    /// The maximum number of guest instructions to execute, or zero when unlimited.
    private final long maxInstructions;

    /// Whether instruction tracing is enabled.
    private final boolean trace;

    /// The stream used for instruction trace output.
    private final PrintStream traceStream;

    /// The optional `tohost` symbol guest address.
    private final long tohostAddress;

    /// The optional `fromhost` symbol guest address.
    private final long fromhostAddress;

    /// The syscall handler for guest environment calls.
    private final GuestSyscalls syscalls;

    /// The current guest program counter.
    private long pc;

    /// The number of retired guest instructions.
    private long instructionCount;

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
        this(memory, maxInstructions, trace, tohostAddress, fromhostAddress, syscalls, asPrintStream(traceStream));
    }

    /// Creates a new architectural state container with a prepared trace print stream.
    private MachineState(
            Memory memory,
            long maxInstructions,
            boolean trace,
            long tohostAddress,
            long fromhostAddress,
            GuestSyscalls syscalls,
            PrintStream traceStream) {
        this.memory = memory;
        this.maxInstructions = maxInstructions;
        this.trace = trace;
        this.traceStream = traceStream;
        this.tohostAddress = tohostAddress;
        this.fromhostAddress = fromhostAddress;
        this.syscalls = syscalls;
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
        checkRegisterIndex(index);
        return index == 0 ? 0 : registers[index];
    }

    /// Updates an integer register value, ignoring writes to `x0`.
    public void setRegister(int index, long value) {
        checkRegisterIndex(index);
        if (index != 0) {
            registers[index] = value;
        }
    }

    /// Returns an integer register value for an already decoded register index.
    long decodedRegister(int index) {
        return index == 0 ? 0 : registers[index];
    }

    /// Updates an integer register value for an already decoded register index, ignoring writes to `x0`.
    void setDecodedRegister(int index, long value) {
        if (index != 0) {
            registers[index] = value;
        }
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
    long decodedFloatingPointRegister(int index) {
        return floatingPointRegisters[index];
    }

    /// Updates a raw floating-point register value for an already decoded register index.
    void setDecodedFloatingPointRegister(int index, long value) {
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

    /// Reads a supported user-mode control and status register.
    public long readControlStatusRegister(int csr) {
        return switch (csr) {
            case FFLAGS_CSR -> floatingPointControlStatus & FFLAGS_MASK;
            case FRM_CSR -> (floatingPointControlStatus >>> 5) & FRM_MASK;
            case FCSR_CSR -> floatingPointControlStatus & FCSR_MASK;
            case CYCLE_CSR, TIME_CSR, INSTRET_CSR -> instructionCount;
            default -> throw unsupportedControlStatusRegister(csr);
        };
    }

    /// Writes a supported writable user-mode control and status register.
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
            case CYCLE_CSR, TIME_CSR, INSTRET_CSR -> throw new RiscVException(
                    "Control status register is read-only: 0x" + Integer.toUnsignedString(csr, 16));
            default -> throw unsupportedControlStatusRegister(csr);
        }
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
        if (maxInstructions == 0 && !trace) {
            instructionCount++;
            return;
        }

        beforeInstructionChecked(address, raw);
    }

    /// Returns true when guest instructions can be retired in block-sized batches.
    boolean canRetireBlock() {
        return maxInstructions == 0 && !trace;
    }

    /// Records multiple guest instruction retirements for an already decoded block.
    void retireBlock(int retiredInstructions) {
        instructionCount += retiredInstructions;
    }

    /// Records one guest instruction retirement on configurations that need checks or tracing.
    private void beforeInstructionChecked(long address, int raw) {
        if (maxInstructions > 0 && instructionCount >= maxInstructions) {
            throw new RiscVException("Guest instruction limit exceeded: " + maxInstructions);
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
        reservationAddress = ElfImage.ABSENT_ADDRESS;
    }

    /// Handles simulator side effects after a guest memory store.
    public void afterStore(long address, int length) {
        if (tohostAddress != ElfImage.ABSENT_ADDRESS && Memory.overlaps(address, length, tohostAddress, Long.BYTES)) {
            long value = memory.readLong(tohostAddress);
            if (value != 0) {
                throw new ProgramExitException(value == 1 ? 0 : value >>> 1);
            }
        }
    }

    /// Validates a register index.
    private static void checkRegisterIndex(int index) {
        if (index < 0 || index >= REGISTER_COUNT) {
            throw new RiscVException("Invalid register index: " + index);
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
