package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests guest syscall behavior directly against architectural state.
@NotNullByDefault
public final class GuestSyscallsTest {
    /// The guest program counter supplied to direct syscall tests.
    private static final long TEST_PC = Memory.DEFAULT_BASE_ADDRESS;

    /// Linux `EBADF` as a raw negative syscall result.
    private static final long EBADF = -9;

    /// Linux `EINVAL` as a raw negative syscall result.
    private static final long EINVAL = -22;

    /// Linux `ENOTTY` as a raw negative syscall result.
    private static final long ENOTTY = -25;

    /// Linux `ESPIPE` as a raw negative syscall result.
    private static final long ESPIPE = -29;

    /// The Linux RISC-V syscall number for `ioctl`.
    private static final long SYS_IOCTL = 29;

    /// The Linux RISC-V syscall number for `close`.
    private static final long SYS_CLOSE = 57;

    /// The Linux RISC-V syscall number for `lseek`.
    private static final long SYS_LSEEK = 62;

    /// The Linux RISC-V syscall number for `read`.
    private static final long SYS_READ = 63;

    /// The Linux RISC-V syscall number for `write`.
    private static final long SYS_WRITE = 64;

    /// The Linux RISC-V syscall number for `readv`.
    private static final long SYS_READV = 65;

    /// The Linux RISC-V syscall number for `writev`.
    private static final long SYS_WRITEV = 66;

    /// The Linux RISC-V syscall number for `fstat`.
    private static final long SYS_FSTAT = 80;

    /// The Linux RISC-V syscall number for `brk`.
    private static final long SYS_BRK = 214;

    /// The Linux generic tty `TCGETS` ioctl request number.
    private static final long TCGETS = 0x5401;

    /// The Linux generic tty `TIOCGWINSZ` ioctl request number.
    private static final long TIOCGWINSZ = 0x5413;

    /// The `st_mode` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_MODE_OFFSET = 16;

    /// The `st_nlink` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_LINK_COUNT_OFFSET = 20;

    /// The `st_blksize` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_BLOCK_SIZE_OFFSET = 56;

    /// The expected `st_mode` value for simulator standard streams.
    private static final int STANDARD_STREAM_STAT_MODE = 0020000 | 0666;

    /// Verifies that stdin EOF is reported as a zero-byte read.
    @Test
    public void readReturnsZeroAtEndOfFile() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(memory, new ByteArrayInputStream(new byte[0]));

            setSyscall(state, SYS_READ, 0, memory.baseAddress(), 8);
            state.syscalls().handle(state, TEST_PC);

            assertEquals(0, state.register(10));
        }
    }

    /// Verifies that short host reads only copy the returned byte count into guest memory.
    @Test
    public void readCopiesPartialInput() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            memory.writeByte(memory.baseAddress() + 2, (byte) 'Z');
            MachineState state = state(memory, new ByteArrayInputStream("AB".getBytes(StandardCharsets.UTF_8)));

            setSyscall(state, SYS_READ, 0, memory.baseAddress(), 8);
            state.syscalls().handle(state, TEST_PC);

            assertEquals(2, state.register(10));
            assertArrayEquals("AB".getBytes(StandardCharsets.UTF_8), memory.readBytes(memory.baseAddress(), 2));
            assertEquals('Z', memory.readUnsignedByte(memory.baseAddress() + 2));
        }
    }

    /// Verifies that invalid file descriptors return `-EBADF` and do not touch host output streams.
    @Test
    public void invalidFileDescriptorsReturnBadFileDescriptor() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            MachineState state = state(
                    memory,
                    new ByteArrayInputStream("A".getBytes(StandardCharsets.UTF_8)),
                    out,
                    err,
                    memory.baseAddress());

            memory.writeByte(memory.baseAddress(), (byte) 'Q');
            setSyscall(state, SYS_READ, 9, memory.baseAddress(), 1);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EBADF, state.register(10));
            assertEquals('Q', memory.readUnsignedByte(memory.baseAddress()));

            memory.writeByte(memory.baseAddress(), (byte) 'B');
            setSyscall(state, SYS_WRITE, 9, memory.baseAddress(), 1);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EBADF, state.register(10));
            assertEquals("", out.toString(StandardCharsets.UTF_8));
            assertEquals("", err.toString(StandardCharsets.UTF_8));
        }
    }

    /// Verifies that syscall buffers still use checked guest memory bounds.
    @Test
    public void syscallBuffersMustFitGuestMemory() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(memory, new ByteArrayInputStream("A".getBytes(StandardCharsets.UTF_8)));

            setSyscall(state, SYS_READ, 0, memory.endAddress(), 1);
            assertThrows(RiscVException.class, () -> state.syscalls().handle(state, TEST_PC));

            setSyscall(state, SYS_WRITE, 1, memory.endAddress(), 1);
            assertThrows(RiscVException.class, () -> state.syscalls().handle(state, TEST_PC));
        }
    }

    /// Verifies that standard file descriptor `close` is a deterministic no-op.
    @Test
    public void closeSupportsStandardFileDescriptors() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(memory, new ByteArrayInputStream(new byte[0]));

            setSyscall(state, SYS_CLOSE, 1, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));

            setSyscall(state, SYS_CLOSE, 9, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EBADF, state.register(10));
        }
    }

    /// Verifies that standard streams report as character devices through `fstat`.
    @Test
    public void fstatReportsStandardStreamsAsCharacterDevices() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(memory, new ByteArrayInputStream(new byte[0]));

            setSyscall(state, SYS_FSTAT, 1, memory.baseAddress(), 0);
            state.syscalls().handle(state, TEST_PC);

            assertEquals(0, state.register(10));
            assertEquals(STANDARD_STREAM_STAT_MODE, memory.readInt(memory.baseAddress() + STAT_MODE_OFFSET));
            assertEquals(1, memory.readInt(memory.baseAddress() + STAT_LINK_COUNT_OFFSET));
            assertEquals(4096, memory.readInt(memory.baseAddress() + STAT_BLOCK_SIZE_OFFSET));

            setSyscall(state, SYS_FSTAT, 9, memory.baseAddress(), 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EBADF, state.register(10));
        }
    }

    /// Verifies that `lseek` rejects standard streams as non-seekable.
    @Test
    public void lseekRejectsStandardStreamsAsPipes() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(memory, new ByteArrayInputStream(new byte[0]));

            setSyscall(state, SYS_LSEEK, 1, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(ESPIPE, state.register(10));

            setSyscall(state, SYS_LSEEK, 9, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EBADF, state.register(10));

            setSyscall(state, SYS_LSEEK, 1, 0, 9);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EINVAL, state.register(10));
        }
    }

    /// Verifies tty ioctl support used by common `isatty` and stdio setup paths.
    @Test
    public void ioctlSupportsTerminalQueries() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(memory, new ByteArrayInputStream(new byte[0]));

            memory.writeByte(memory.baseAddress(), (byte) 0x7f);
            setSyscall(state, SYS_IOCTL, 1, TCGETS, memory.baseAddress());
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));
            assertEquals(0, memory.readUnsignedByte(memory.baseAddress()));

            setSyscall(state, SYS_IOCTL, 1, TIOCGWINSZ, memory.baseAddress());
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));
            assertEquals(24, memory.readUnsignedShort(memory.baseAddress()));
            assertEquals(80, memory.readUnsignedShort(memory.baseAddress() + Short.BYTES));

            setSyscall(state, SYS_IOCTL, 1, 0x1234, memory.baseAddress());
            state.syscalls().handle(state, TEST_PC);
            assertEquals(ENOTTY, state.register(10));

            setSyscall(state, SYS_IOCTL, 9, TCGETS, memory.baseAddress());
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EBADF, state.register(10));
        }
    }

    /// Verifies that `writev` writes all guest iovec buffers to stderr.
    @Test
    public void writevWritesMultipleBuffers() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            MachineState state = state(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new ByteArrayOutputStream(),
                    err,
                    memory.baseAddress());
            long iovecAddress = memory.baseAddress();
            long firstBuffer = memory.baseAddress() + 64;
            long secondBuffer = memory.baseAddress() + 72;

            memory.writeBytes(firstBuffer, "Hi".getBytes(StandardCharsets.UTF_8), 0, 2);
            memory.writeBytes(secondBuffer, "!".getBytes(StandardCharsets.UTF_8), 0, 1);
            memory.writeLong(iovecAddress, firstBuffer);
            memory.writeLong(iovecAddress + Long.BYTES, 2);
            memory.writeLong(iovecAddress + 2L * Long.BYTES, secondBuffer);
            memory.writeLong(iovecAddress + 3L * Long.BYTES, 1);

            setSyscall(state, SYS_WRITEV, 2, iovecAddress, 2);
            state.syscalls().handle(state, TEST_PC);

            assertEquals(3, state.register(10));
            assertEquals("Hi!", err.toString(StandardCharsets.UTF_8));
        }
    }

    /// Verifies that `readv` fills guest iovec buffers from stdin in order.
    @Test
    public void readvReadsMultipleBuffers() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(
                    memory,
                    new ByteArrayInputStream("ABC".getBytes(StandardCharsets.UTF_8)),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    memory.baseAddress());
            long iovecAddress = memory.baseAddress();
            long firstBuffer = memory.baseAddress() + 64;
            long secondBuffer = memory.baseAddress() + 72;

            memory.writeByte(secondBuffer + 1, (byte) 'Z');
            memory.writeLong(iovecAddress, firstBuffer);
            memory.writeLong(iovecAddress + Long.BYTES, 2);
            memory.writeLong(iovecAddress + 2L * Long.BYTES, secondBuffer);
            memory.writeLong(iovecAddress + 3L * Long.BYTES, 2);

            setSyscall(state, SYS_READV, 0, iovecAddress, 2);
            state.syscalls().handle(state, TEST_PC);

            assertEquals(3, state.register(10));
            assertArrayEquals("AB".getBytes(StandardCharsets.UTF_8), memory.readBytes(firstBuffer, 2));
            assertEquals('C', memory.readUnsignedByte(secondBuffer));
            assertEquals('Z', memory.readUnsignedByte(secondBuffer + 1));
        }
    }

    /// Verifies that host input and output failures are surfaced as simulator exceptions.
    @Test
    public void propagatesHostIoFailures() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState readState = state(
                    memory,
                    new FailingInputStream(),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    memory.baseAddress());
            setSyscall(readState, SYS_READ, 0, memory.baseAddress(), 1);
            assertThrows(RiscVException.class, () -> readState.syscalls().handle(readState, TEST_PC));

            MachineState writeState = state(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new FailingOutputStream(),
                    new ByteArrayOutputStream(),
                    memory.baseAddress());
            memory.writeByte(memory.baseAddress(), (byte) 'A');
            setSyscall(writeState, SYS_WRITE, 1, memory.baseAddress(), 1);
            assertThrows(RiscVException.class, () -> writeState.syscalls().handle(writeState, TEST_PC));
        }
    }

    /// Verifies that `brk` reports and updates the program break inside guest memory.
    @Test
    public void brkTracksProgramBreakWithinGuestMemory() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            long initialBreak = memory.baseAddress() + 128;
            long requestedBreak = initialBreak + 64;
            MachineState state = state(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    initialBreak);

            setSyscall(state, SYS_BRK, 0, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(initialBreak, state.register(10));

            setSyscall(state, SYS_BRK, requestedBreak, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(requestedBreak, state.register(10));

            setSyscall(state, SYS_BRK, initialBreak - 1, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(requestedBreak, state.register(10));

            setSyscall(state, SYS_BRK, memory.endAddress() + 1, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(requestedBreak, state.register(10));
        }
    }

    /// Creates test machine state with empty output streams.
    private static MachineState state(Memory memory, ByteArrayInputStream in) {
        return state(memory, in, new ByteArrayOutputStream(), new ByteArrayOutputStream(), memory.baseAddress());
    }

    /// Creates test machine state with a syscall handler.
    private static MachineState state(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak) {
        GuestSyscalls syscalls = new GuestSyscalls(memory, in, out, err, initialProgramBreak);
        return new MachineState(
                memory,
                0,
                false,
                ElfImage.ABSENT_ADDRESS,
                ElfImage.ABSENT_ADDRESS,
                syscalls);
    }

    /// Populates the syscall number and the first three argument registers.
    private static void setSyscall(MachineState state, long callNumber, long a0, long a1, long a2) {
        state.setRegister(10, a0);
        state.setRegister(11, a1);
        state.setRegister(12, a2);
        state.setRegister(17, callNumber);
    }

    /// Input stream that always fails reads.
    private static final class FailingInputStream extends InputStream {
        /// Always throws an I/O failure.
        @Override
        public int read() throws IOException {
            throw new IOException("read failure");
        }

        /// Always throws an I/O failure.
        @Override
        public int read(byte[] bytes) throws IOException {
            throw new IOException("read failure");
        }
    }

    /// Output stream that always fails writes.
    private static final class FailingOutputStream extends OutputStream {
        /// Always throws an I/O failure.
        @Override
        public void write(int value) throws IOException {
            throw new IOException("write failure");
        }

        /// Always throws an I/O failure.
        @Override
        public void write(byte[] bytes) throws IOException {
            throw new IOException("write failure");
        }
    }
}
