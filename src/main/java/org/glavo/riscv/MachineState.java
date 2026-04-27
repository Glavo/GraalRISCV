package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.OutputStream;
import java.io.PrintStream;

/// Stores mutable architectural state for one guest execution.
@NotNullByDefault
public final class MachineState {
    /// The number of integer registers in RV64I.
    private static final int REGISTER_COUNT = 32;

    /// The mutable integer register file.
    private final long[] registers = new long[REGISTER_COUNT];

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

    /// Returns the optional `fromhost` symbol guest address.
    public long fromhostAddress() {
        return fromhostAddress;
    }

    /// Returns the syscall handler for guest environment calls.
    public GuestSyscalls syscalls() {
        return syscalls;
    }

    /// Records one guest instruction retirement and enforces the instruction budget.
    public void beforeInstruction(long address, int raw) {
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

    /// Writes a single trace line for a guest instruction.
    private static void traceInstruction(PrintStream stream, long address, int raw) {
        stream.println("pc=0x" + Long.toUnsignedString(address, 16) + " raw=0x" + Integer.toUnsignedString(raw, 16));
    }

    /// Adapts an output stream to a print stream without double-wrapping existing print streams.
    private static PrintStream asPrintStream(OutputStream stream) {
        return stream instanceof PrintStream printStream ? printStream : new PrintStream(stream, true);
    }
}
