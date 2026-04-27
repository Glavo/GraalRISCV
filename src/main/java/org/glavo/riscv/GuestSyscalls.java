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

    /// The guest memory accessed by syscall buffers.
    private final Memory memory;

    /// The host input stream used for guest stdin reads.
    private final InputStream in;

    /// The host output stream used for guest stdout writes.
    private final OutputStream out;

    /// The host output stream used for guest stderr writes.
    private final OutputStream err;

    /// Creates a syscall handler backed by the supplied host streams.
    public GuestSyscalls(Memory memory, InputStream in, OutputStream out, OutputStream err) {
        this.memory = memory;
        this.in = in;
        this.out = out;
        this.err = err;
    }

    /// Executes the syscall described by the guest argument registers.
    public void handle(MachineState state) {
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
        throw new RiscVException("Unsupported ecall number: " + callNumber);
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
}
