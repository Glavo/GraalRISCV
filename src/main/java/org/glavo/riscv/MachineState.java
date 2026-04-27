package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
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

    /// The optional `tohost` symbol guest address.
    private final long tohostAddress;

    /// The optional `fromhost` symbol guest address.
    private final long fromhostAddress;

    /// The host stream used for guest stdout writes.
    private final OutputStream out;

    /// The host stream used for guest stderr writes.
    private final OutputStream err;

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
            OutputStream out,
            OutputStream err) {
        this.memory = memory;
        this.maxInstructions = maxInstructions;
        this.trace = trace;
        this.tohostAddress = tohostAddress;
        this.fromhostAddress = fromhostAddress;
        this.out = out;
        this.err = err;
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

    /// Records one guest instruction retirement and enforces the instruction budget.
    public void beforeInstruction(long address, int raw) {
        if (maxInstructions > 0 && instructionCount >= maxInstructions) {
            throw new RiscVException("Guest instruction limit exceeded: " + maxInstructions);
        }

        instructionCount++;
        if (trace) {
            traceInstruction(System.err, address, raw);
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

    /// Writes guest memory bytes to stdout or stderr and returns the guest syscall result.
    public long write(int fileDescriptor, long address, long length) {
        if (length < 0) {
            return -1;
        }

        OutputStream stream;
        if (fileDescriptor == 1) {
            stream = out;
        } else if (fileDescriptor == 2) {
            stream = err;
        } else {
            return -1;
        }

        try {
            byte[] bytes = memory.readBytes(address, length);
            stream.write(bytes);
            stream.flush();
            return bytes.length;
        } catch (IOException exception) {
            throw new RiscVException("Guest write syscall failed", exception);
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
}
