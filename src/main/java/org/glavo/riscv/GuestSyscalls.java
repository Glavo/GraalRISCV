package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/// Handles the small Linux-compatible syscall subset exposed by the simulator.
@NotNullByDefault
public final class GuestSyscalls {
    /// The Linux RISC-V syscall number for `read`.
    private static final long SYS_READ = 63;

    /// The Linux RISC-V syscall number for `write`.
    private static final long SYS_WRITE = 64;

    /// The Linux RISC-V syscall number for `exit`.
    private static final long SYS_EXIT = 93;

    /// The Linux RISC-V syscall number for `brk`.
    private static final long SYS_BRK = 214;

    /// The guest memory accessed by syscall buffers.
    private final Memory memory;

    /// The host input stream used for guest stdin reads.
    private final InputStream in;

    /// The host output stream used for guest stdout writes.
    private final OutputStream out;

    /// The host output stream used for guest stderr writes.
    private final OutputStream err;

    /// The lowest program break accepted by the `brk` syscall.
    private final long initialProgramBreak;

    /// The current guest program break returned by `brk`.
    private long programBreak;

    /// Creates a syscall handler backed by the supplied host streams and heap boundary.
    public GuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak) {
        if (initialProgramBreak < memory.baseAddress() || initialProgramBreak > memory.endAddress()) {
            throw new RiscVException("Initial program break is outside guest memory: address=0x"
                    + Long.toUnsignedString(initialProgramBreak, 16));
        }

        this.memory = memory;
        this.in = in;
        this.out = out;
        this.err = err;
        this.initialProgramBreak = initialProgramBreak;
        this.programBreak = initialProgramBreak;
    }

    /// Executes the syscall described by the guest argument registers at the supplied program counter.
    public void handle(MachineState state, long pc) {
        long callNumber = state.register(17);
        if (callNumber == SYS_READ) {
            state.setRegister(10, read((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_WRITE) {
            state.setRegister(10, write((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_EXIT) {
            throw new ProgramExitException(state.register(10));
        }
        if (callNumber == SYS_BRK) {
            state.setRegister(10, brk(state.register(10)));
            return;
        }
        throw new RiscVException(unsupportedEcallMessage(state, pc, callNumber));
    }

    /// Builds a diagnostic message for a syscall that is not implemented by the simulator.
    private static String unsupportedEcallMessage(MachineState state, long pc, long callNumber) {
        return "Unsupported ecall number: " + callNumber
                + ", pc=0x" + unsignedHex(pc)
                + ", a0=0x" + unsignedHex(state.register(10))
                + ", a1=0x" + unsignedHex(state.register(11))
                + ", a2=0x" + unsignedHex(state.register(12))
                + ", a3=0x" + unsignedHex(state.register(13))
                + ", a4=0x" + unsignedHex(state.register(14))
                + ", a5=0x" + unsignedHex(state.register(15))
                + ", a6=0x" + unsignedHex(state.register(16))
                + ", a7=0x" + unsignedHex(state.register(17));
    }

    /// Formats a guest register value as an unsigned hexadecimal string.
    private static String unsignedHex(long value) {
        return Long.toUnsignedString(value, 16);
    }

    /// Reads bytes from guest stdin into guest memory and returns the guest syscall result.
    private long read(int fileDescriptor, long address, long length) {
        if (fileDescriptor != 0) {
            return -1;
        }
        if (length < 0) {
            return -1;
        }
        if (length > Integer.MAX_VALUE) {
            throw new RiscVException("Guest read syscall buffer is too large: " + length);
        }
        if (length == 0) {
            return 0;
        }

        try {
            byte[] buffer = new byte[(int) length];
            int count = in.read(buffer);
            if (count < 0) {
                return 0;
            }

            memory.writeBytes(address, buffer, 0, count);
            return count;
        } catch (IOException exception) {
            throw new RiscVException("Guest read syscall failed", exception);
        }
    }

    /// Writes guest memory bytes to stdout or stderr and returns the guest syscall result.
    private long write(int fileDescriptor, long address, long length) {
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

    /// Implements the Linux `brk` syscall within the simulator memory window.
    private long brk(long requestedAddress) {
        if (requestedAddress == 0) {
            return programBreak;
        }
        if (requestedAddress >= initialProgramBreak && requestedAddress <= memory.endAddress()) {
            programBreak = requestedAddress;
        }
        return programBreak;
    }
}
