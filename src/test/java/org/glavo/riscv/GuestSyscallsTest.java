package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests guest syscall behavior directly against architectural state.
@NotNullByDefault
public final class GuestSyscallsTest {
    /// A temporary host root for file syscall tests.
    @TempDir
    private Path tempDirectory;

    /// The guest program counter supplied to direct syscall tests.
    private static final long TEST_PC = Memory.DEFAULT_BASE_ADDRESS;

    /// Linux `EBADF` as a raw negative syscall result.
    private static final long EBADF = -9;

    /// Linux `ENOENT` as a raw negative syscall result.
    private static final long ENOENT = -2;

    /// Linux `EACCES` as a raw negative syscall result.
    private static final long EACCES = -13;

    /// Linux `ENOMEM` as a raw negative syscall result.
    private static final long ENOMEM = -12;

    /// Linux `EEXIST` as a raw negative syscall result.
    private static final long EEXIST = -17;

    /// Linux `ENODEV` as a raw negative syscall result.
    private static final long ENODEV = -19;

    /// Linux `EISDIR` as a raw negative syscall result.
    private static final long EISDIR = -21;

    /// Linux `EINVAL` as a raw negative syscall result.
    private static final long EINVAL = -22;

    /// Linux `ENOTTY` as a raw negative syscall result.
    private static final long ENOTTY = -25;

    /// Linux `ESPIPE` as a raw negative syscall result.
    private static final long ESPIPE = -29;

    /// The Linux RISC-V syscall number for `ioctl`.
    private static final long SYS_IOCTL = 29;

    /// The Linux RISC-V syscall number for `fcntl`.
    private static final long SYS_FCNTL = 25;

    /// The Linux RISC-V syscall number for `openat`.
    private static final long SYS_OPENAT = 56;

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

    /// The Linux RISC-V syscall number for `set_tid_address`.
    private static final long SYS_SET_TID_ADDRESS = 96;

    /// The Linux RISC-V syscall number for `set_robust_list`.
    private static final long SYS_SET_ROBUST_LIST = 99;

    /// The Linux RISC-V syscall number for `sched_getaffinity`.
    private static final long SYS_SCHED_GETAFFINITY = 123;

    /// The Linux RISC-V syscall number for `rt_sigaction`.
    private static final long SYS_RT_SIGACTION = 134;

    /// The Linux RISC-V syscall number for `rt_sigprocmask`.
    private static final long SYS_RT_SIGPROCMASK = 135;

    /// The Linux RISC-V syscall number for `getpid`.
    private static final long SYS_GETPID = 172;

    /// The Linux RISC-V syscall number for `gettid`.
    private static final long SYS_GETTID = 178;

    /// The Linux RISC-V syscall number for `brk`.
    private static final long SYS_BRK = 214;

    /// The Linux RISC-V syscall number for `munmap`.
    private static final long SYS_MUNMAP = 215;

    /// The Linux RISC-V syscall number for `mmap`.
    private static final long SYS_MMAP = 222;

    /// The Linux RISC-V syscall number for `getrandom`.
    private static final long SYS_GETRANDOM = 278;

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

    /// The expected `st_mode` value for read-only regular host files.
    private static final int REGULAR_FILE_STAT_MODE = 0100000 | 0444;

    /// The expected `st_mode` value for write-only regular host files.
    private static final int WRITABLE_REGULAR_FILE_STAT_MODE = 0100000 | 0222;

    /// The expected `st_mode` value for read-write regular host files.
    private static final int READ_WRITE_REGULAR_FILE_STAT_MODE = 0100000 | 0666;

    /// The `st_size` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_SIZE_OFFSET = 48;

    /// The Linux `AT_FDCWD` pseudo file descriptor for path-based syscalls.
    private static final long AT_FDCWD = -100;

    /// Linux `O_RDONLY`.
    private static final long O_RDONLY = 0;

    /// Linux `O_WRONLY`.
    private static final long O_WRONLY = 1;

    /// Linux `O_RDWR`.
    private static final long O_RDWR = 2;

    /// Linux `O_CREAT`.
    private static final long O_CREAT = 00000100L;

    /// Linux `O_EXCL`.
    private static final long O_EXCL = 00000200L;

    /// Linux `O_TRUNC`.
    private static final long O_TRUNC = 00001000L;

    /// Linux `O_APPEND`.
    private static final long O_APPEND = 00002000L;

    /// Linux `F_GETFL`.
    private static final long F_GETFL = 3;

    /// Linux `O_CLOEXEC`.
    private static final long O_CLOEXEC = 02000000L;

    /// The Linux generic kernel `sigset_t` size accepted by signal syscalls.
    private static final long KERNEL_SIGSET_SIZE = 8;

    /// The guest page size used by `mmap` and `munmap`.
    private static final long PAGE_SIZE = 4096;

    /// Linux `PROT_READ`.
    private static final long PROT_READ = 0x1;

    /// Linux `PROT_WRITE`.
    private static final long PROT_WRITE = 0x2;

    /// Linux `MAP_PRIVATE`.
    private static final long MAP_PRIVATE = 0x02;

    /// Linux `MAP_FIXED`.
    private static final long MAP_FIXED = 0x10;

    /// Linux `MAP_ANONYMOUS`.
    private static final long MAP_ANONYMOUS = 0x20;

    /// Linux `MAP_FIXED_NOREPLACE`.
    private static final long MAP_FIXED_NOREPLACE = 0x100000;

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

    /// Verifies that zero-length writes succeed without touching the guest address.
    @Test
    public void writeAcceptsZeroLengthInvalidAddress() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MachineState state = state(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    out,
                    new ByteArrayOutputStream(),
                    memory.baseAddress());

            setSyscall(state, SYS_WRITE, 1, 0, 0);
            state.syscalls().handle(state, TEST_PC);

            assertEquals(0, state.register(10));
            assertEquals("", out.toString(StandardCharsets.UTF_8));
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

    /// Verifies that `openat` exposes read-only host files below the configured host root.
    @Test
    public void openatReadsHostFileBelowRoot() throws Exception {
        Files.writeString(tempDirectory.resolve("message.txt"), "file-data", StandardCharsets.UTF_8);

        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 4096)) {
            MachineState state = state(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    memory.baseAddress(),
                    tempDirectory);
            long pathAddress = memory.baseAddress();
            long bufferAddress = memory.baseAddress() + 128;
            long statAddress = memory.baseAddress() + 256;
            writeGuestString(memory, pathAddress, "/message.txt");

            setSyscall(state, SYS_OPENAT, AT_FDCWD, pathAddress, O_RDONLY | O_CLOEXEC, 0);
            state.syscalls().handle(state, TEST_PC);
            int fileDescriptor = (int) state.register(10);
            assertEquals(3, fileDescriptor);

            setSyscall(state, SYS_READ, fileDescriptor, bufferAddress, 4);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(4, state.register(10));
            assertArrayEquals("file".getBytes(StandardCharsets.UTF_8), memory.readBytes(bufferAddress, 4));

            setSyscall(state, SYS_LSEEK, fileDescriptor, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));

            setSyscall(state, SYS_READ, fileDescriptor, bufferAddress, 9);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(9, state.register(10));
            assertArrayEquals("file-data".getBytes(StandardCharsets.UTF_8), memory.readBytes(bufferAddress, 9));

            setSyscall(state, SYS_FSTAT, fileDescriptor, statAddress, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));
            assertEquals(REGULAR_FILE_STAT_MODE, memory.readInt(statAddress + STAT_MODE_OFFSET));
            assertEquals(1, memory.readInt(statAddress + STAT_LINK_COUNT_OFFSET));
            assertEquals(9, memory.readLong(statAddress + STAT_SIZE_OFFSET));
            assertEquals(4096, memory.readInt(statAddress + STAT_BLOCK_SIZE_OFFSET));

            setSyscall(state, SYS_CLOSE, fileDescriptor, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));

            setSyscall(state, SYS_READ, fileDescriptor, bufferAddress, 1);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EBADF, state.register(10));
        }
    }

    /// Verifies that `openat` exposes writable host files below the configured host root.
    @Test
    public void openatWritesHostFilesBelowRoot() throws Exception {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 4096)) {
            MachineState state = state(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    memory.baseAddress(),
                    tempDirectory);
            long pathAddress = memory.baseAddress();
            long bufferAddress = memory.baseAddress() + 128;
            long statAddress = memory.baseAddress() + 256;
            long iovecAddress = memory.baseAddress() + 384;
            long firstIovecBuffer = memory.baseAddress() + 448;
            long secondIovecBuffer = memory.baseAddress() + 456;
            writeGuestString(memory, pathAddress, "/output.txt");

            setSyscall(state, SYS_OPENAT, AT_FDCWD, pathAddress, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
            state.syscalls().handle(state, TEST_PC);
            int fileDescriptor = (int) state.register(10);
            assertEquals(3, fileDescriptor);

            memory.writeBytes(bufferAddress, "file".getBytes(StandardCharsets.UTF_8), 0, 4);
            setSyscall(state, SYS_WRITE, fileDescriptor, bufferAddress, 4);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(4, state.register(10));

            setSyscall(state, SYS_READ, fileDescriptor, bufferAddress, 1);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EBADF, state.register(10));

            setSyscall(state, SYS_FSTAT, fileDescriptor, statAddress, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));
            assertEquals(WRITABLE_REGULAR_FILE_STAT_MODE, memory.readInt(statAddress + STAT_MODE_OFFSET));
            assertEquals(4, memory.readLong(statAddress + STAT_SIZE_OFFSET));

            setSyscall(state, SYS_CLOSE, fileDescriptor, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));
            assertEquals("file", Files.readString(tempDirectory.resolve("output.txt"), StandardCharsets.UTF_8));

            setSyscall(state, SYS_OPENAT, AT_FDCWD, pathAddress, O_WRONLY | O_APPEND, 0);
            state.syscalls().handle(state, TEST_PC);
            fileDescriptor = (int) state.register(10);
            assertEquals(3, fileDescriptor);

            setSyscall(state, SYS_FCNTL, fileDescriptor, F_GETFL, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(O_WRONLY | O_APPEND, state.register(10));

            memory.writeBytes(bufferAddress, "-".getBytes(StandardCharsets.UTF_8), 0, 1);
            setSyscall(state, SYS_WRITE, fileDescriptor, bufferAddress, 1);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(1, state.register(10));

            memory.writeBytes(firstIovecBuffer, "data".getBytes(StandardCharsets.UTF_8), 0, 4);
            memory.writeBytes(secondIovecBuffer, "\n".getBytes(StandardCharsets.UTF_8), 0, 1);
            memory.writeLong(iovecAddress, firstIovecBuffer);
            memory.writeLong(iovecAddress + Long.BYTES, 4);
            memory.writeLong(iovecAddress + 2L * Long.BYTES, secondIovecBuffer);
            memory.writeLong(iovecAddress + 3L * Long.BYTES, 1);
            setSyscall(state, SYS_WRITEV, fileDescriptor, iovecAddress, 2);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(5, state.register(10));

            setSyscall(state, SYS_CLOSE, fileDescriptor, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));
            assertEquals("file-data\n", Files.readString(tempDirectory.resolve("output.txt"), StandardCharsets.UTF_8));

            setSyscall(state, SYS_OPENAT, AT_FDCWD, pathAddress, O_RDWR, 0);
            state.syscalls().handle(state, TEST_PC);
            fileDescriptor = (int) state.register(10);
            assertEquals(3, fileDescriptor);

            setSyscall(state, SYS_READ, fileDescriptor, bufferAddress, 10);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(10, state.register(10));
            assertArrayEquals("file-data\n".getBytes(StandardCharsets.UTF_8), memory.readBytes(bufferAddress, 10));

            setSyscall(state, SYS_LSEEK, fileDescriptor, 4, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(4, state.register(10));

            memory.writeBytes(bufferAddress, "!".getBytes(StandardCharsets.UTF_8), 0, 1);
            setSyscall(state, SYS_WRITE, fileDescriptor, bufferAddress, 1);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(1, state.register(10));

            setSyscall(state, SYS_FSTAT, fileDescriptor, statAddress, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));
            assertEquals(READ_WRITE_REGULAR_FILE_STAT_MODE, memory.readInt(statAddress + STAT_MODE_OFFSET));

            setSyscall(state, SYS_CLOSE, fileDescriptor, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));
            assertEquals("file!data\n", Files.readString(tempDirectory.resolve("output.txt"), StandardCharsets.UTF_8));
        }
    }

    /// Verifies that `openat` keeps host access sandboxed and rejects unsupported modes.
    @Test
    public void openatRejectsUnsupportedHostAccess() throws Exception {
        Files.writeString(tempDirectory.resolve("message.txt"), "file-data", StandardCharsets.UTF_8);
        Files.createDirectory(tempDirectory.resolve("directory"));

        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 4096)) {
            MachineState state = state(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    memory.baseAddress(),
                    tempDirectory);
            long pathAddress = memory.baseAddress();

            writeGuestString(memory, pathAddress, "../message.txt");
            setSyscall(state, SYS_OPENAT, AT_FDCWD, pathAddress, O_RDONLY, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EACCES, state.register(10));

            writeGuestString(memory, pathAddress, "missing.txt");
            setSyscall(state, SYS_OPENAT, AT_FDCWD, pathAddress, O_RDONLY, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(ENOENT, state.register(10));

            writeGuestString(memory, pathAddress, "directory");
            setSyscall(state, SYS_OPENAT, AT_FDCWD, pathAddress, O_RDONLY, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EISDIR, state.register(10));

            writeGuestString(memory, pathAddress, "message.txt");
            setSyscall(state, SYS_OPENAT, AT_FDCWD, pathAddress, O_WRONLY | O_CREAT | O_EXCL, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EEXIST, state.register(10));

            setSyscall(state, SYS_OPENAT, AT_FDCWD, pathAddress, O_RDONLY | O_TRUNC, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EACCES, state.register(10));

            setSyscall(state, SYS_OPENAT, AT_FDCWD, pathAddress, O_RDONLY | O_APPEND, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EACCES, state.register(10));

            setSyscall(state, SYS_OPENAT, AT_FDCWD, pathAddress, O_RDONLY | O_WRONLY | O_RDWR, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EINVAL, state.register(10));

            setSyscall(state, SYS_OPENAT, 3, pathAddress, O_RDONLY, 0);
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

    /// Verifies stable process identity syscalls for the single-process simulator.
    @Test
    public void processIdentitySyscallsReturnStableIds() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(memory, new ByteArrayInputStream(new byte[0]));

            setSyscall(state, SYS_GETPID, 0, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(1, state.register(10));

            setSyscall(state, SYS_GETTID, 0, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(1, state.register(10));

            setSyscall(state, SYS_SET_TID_ADDRESS, memory.baseAddress() + 32, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(1, state.register(10));
        }
    }

    /// Verifies that robust futex list registration is accepted for single-threaded guests.
    @Test
    public void setRobustListAcceptsNonNegativeLength() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(memory, new ByteArrayInputStream(new byte[0]));

            setSyscall(state, SYS_SET_ROBUST_LIST, memory.baseAddress() + 64, 24, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));

            setSyscall(state, SYS_SET_ROBUST_LIST, memory.baseAddress() + 64, -1, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EINVAL, state.register(10));
        }
    }

    /// Verifies deterministic single-CPU affinity for static libc sysconf queries.
    @Test
    public void schedGetaffinityReportsSingleCpu() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(memory, new ByteArrayInputStream(new byte[0]));
            long maskAddress = memory.baseAddress() + 64;

            memory.writeByte(maskAddress + Long.BYTES, (byte) 0x7f);
            setSyscall(state, SYS_SCHED_GETAFFINITY, 0, 16, maskAddress);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));
            assertEquals(1, memory.readLong(maskAddress));
            assertEquals(0, memory.readUnsignedByte(maskAddress + Long.BYTES));

            setSyscall(state, SYS_SCHED_GETAFFINITY, 0, Long.BYTES - 1, maskAddress);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EINVAL, state.register(10));
        }
    }

    /// Verifies deterministic `getrandom` bytes and flag validation.
    @Test
    public void getrandomFillsDeterministicBytes() {
        byte[] firstBytes;
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(memory, new ByteArrayInputStream(new byte[0]));

            setSyscall(state, SYS_GETRANDOM, memory.baseAddress(), 8, 3);
            state.syscalls().handle(state, TEST_PC);

            assertEquals(8, state.register(10));
            firstBytes = memory.readBytes(memory.baseAddress(), 8);

            setSyscall(state, SYS_GETRANDOM, memory.endAddress() + 128, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));

            setSyscall(state, SYS_GETRANDOM, memory.baseAddress(), 1, 4);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EINVAL, state.register(10));
        }

        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(memory, new ByteArrayInputStream(new byte[0]));

            setSyscall(state, SYS_GETRANDOM, memory.baseAddress(), 8, 0);
            state.syscalls().handle(state, TEST_PC);

            assertArrayEquals(firstBytes, memory.readBytes(memory.baseAddress(), 8));
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

    /// Verifies deterministic signal action handling for runtimes that initialize signal handlers.
    @Test
    public void rtSigactionAcceptsRuntimeSetup() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(memory, new ByteArrayInputStream(new byte[0]));
            long actionAddress = memory.baseAddress() + 64;
            long oldActionAddress = memory.baseAddress() + 128;
            memory.writeLong(oldActionAddress, -1);

            setSyscall(state, SYS_RT_SIGACTION, 2, actionAddress, oldActionAddress, KERNEL_SIGSET_SIZE);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));
            assertEquals(0, memory.readLong(oldActionAddress));

            setSyscall(state, SYS_RT_SIGACTION, 0, 0, 0, KERNEL_SIGSET_SIZE);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EINVAL, state.register(10));

            setSyscall(state, SYS_RT_SIGACTION, 2, 0, 0, KERNEL_SIGSET_SIZE - 1);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EINVAL, state.register(10));
        }
    }

    /// Verifies deterministic signal-mask handling for single-threaded guests.
    @Test
    public void rtSigprocmaskReportsEmptyMask() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            MachineState state = state(memory, new ByteArrayInputStream(new byte[0]));
            long newSetAddress = memory.baseAddress() + 64;
            long oldSetAddress = memory.baseAddress() + 128;
            memory.writeLong(newSetAddress, 1);
            memory.writeLong(oldSetAddress, -1);

            setSyscall(state, SYS_RT_SIGPROCMASK, 2, newSetAddress, oldSetAddress, KERNEL_SIGSET_SIZE);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));
            assertEquals(0, memory.readLong(oldSetAddress));

            setSyscall(state, SYS_RT_SIGPROCMASK, 9, newSetAddress, 0, KERNEL_SIGSET_SIZE);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EINVAL, state.register(10));

            setSyscall(state, SYS_RT_SIGPROCMASK, 0, 0, 0, KERNEL_SIGSET_SIZE - 1);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EINVAL, state.register(10));
        }
    }

    /// Verifies that anonymous `mmap` returns zero-filled page-aligned guest memory.
    @Test
    public void mmapAllocatesAnonymousGuestPages() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 4 * PAGE_SIZE)) {
            long initialBreak = memory.baseAddress() + PAGE_SIZE;
            MachineState state = state(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    initialBreak);
            long expectedAddress = memory.baseAddress() + PAGE_SIZE;
            memory.writeByte(expectedAddress, (byte) 0x7f);

            setSyscall(
                    state,
                    SYS_MMAP,
                    0,
                    128,
                    PROT_READ | PROT_WRITE,
                    MAP_PRIVATE | MAP_ANONYMOUS,
                    -1,
                    0);
            state.syscalls().handle(state, TEST_PC);

            assertEquals(expectedAddress, state.register(10));
            assertEquals(0, memory.readUnsignedByte(expectedAddress));
            memory.writeLong(expectedAddress, 0x1020_3040_5060_7080L);
            assertEquals(0x1020_3040_5060_7080L, memory.readLong(expectedAddress));
        }
    }

    /// Verifies that released anonymous mappings can be reused by later `mmap` calls.
    @Test
    public void munmapReleasesAnonymousGuestPages() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 5 * PAGE_SIZE)) {
            long initialBreak = memory.baseAddress() + PAGE_SIZE;
            MachineState state = state(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    initialBreak);

            setSyscall(
                    state,
                    SYS_MMAP,
                    0,
                    PAGE_SIZE,
                    PROT_READ | PROT_WRITE,
                    MAP_PRIVATE | MAP_ANONYMOUS,
                    -1,
                    0);
            state.syscalls().handle(state, TEST_PC);
            long firstAddress = state.register(10);

            setSyscall(
                    state,
                    SYS_MMAP,
                    0,
                    PAGE_SIZE,
                    PROT_READ | PROT_WRITE,
                    MAP_PRIVATE | MAP_ANONYMOUS,
                    -1,
                    0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(firstAddress + PAGE_SIZE, state.register(10));

            memory.writeByte(firstAddress, (byte) 0x7f);
            setSyscall(state, SYS_MUNMAP, firstAddress, PAGE_SIZE, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(0, state.register(10));
            assertEquals(0, memory.readUnsignedByte(firstAddress));

            setSyscall(
                    state,
                    SYS_MMAP,
                    0,
                    PAGE_SIZE,
                    PROT_READ | PROT_WRITE,
                    MAP_PRIVATE | MAP_ANONYMOUS,
                    -1,
                    0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(firstAddress, state.register(10));
        }
    }

    /// Verifies validation and collision behavior for unsupported `mmap` requests.
    @Test
    public void mmapRejectsUnsupportedRequests() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 3 * PAGE_SIZE)) {
            long initialBreak = memory.baseAddress() + PAGE_SIZE;
            MachineState state = state(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    initialBreak);

            setSyscall(
                    state,
                    SYS_MMAP,
                    0,
                    0,
                    PROT_READ,
                    MAP_PRIVATE | MAP_ANONYMOUS,
                    -1,
                    0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EINVAL, state.register(10));

            setSyscall(state, SYS_MMAP, 0, PAGE_SIZE, PROT_READ, MAP_PRIVATE, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(ENODEV, state.register(10));

            setSyscall(
                    state,
                    SYS_MMAP,
                    initialBreak + 1,
                    PAGE_SIZE,
                    PROT_READ,
                    MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED,
                    -1,
                    0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(ENOMEM, state.register(10));

            setSyscall(
                    state,
                    SYS_MMAP,
                    initialBreak,
                    PAGE_SIZE,
                    PROT_READ,
                    MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED_NOREPLACE,
                    -1,
                    0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(initialBreak, state.register(10));

            setSyscall(
                    state,
                    SYS_MMAP,
                    initialBreak,
                    PAGE_SIZE,
                    PROT_READ,
                    MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED_NOREPLACE,
                    -1,
                    0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(EEXIST, state.register(10));
        }
    }

    /// Verifies that `brk` does not grow into active anonymous mappings.
    @Test
    public void brkDoesNotOverlapAnonymousMappings() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 4 * PAGE_SIZE)) {
            long initialBreak = memory.baseAddress() + PAGE_SIZE;
            MachineState state = state(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    initialBreak);

            setSyscall(
                    state,
                    SYS_MMAP,
                    0,
                    PAGE_SIZE,
                    PROT_READ | PROT_WRITE,
                    MAP_PRIVATE | MAP_ANONYMOUS,
                    -1,
                    0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(initialBreak, state.register(10));

            setSyscall(state, SYS_BRK, initialBreak + PAGE_SIZE, 0, 0);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(initialBreak, state.register(10));
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
        return state(memory, in, out, err, initialProgramBreak, Path.of("."));
    }

    /// Creates test machine state with a syscall handler and host file root.
    private static MachineState state(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            Path hostRoot) {
        GuestSyscalls syscalls = new GuestSyscalls(memory, in, out, err, initialProgramBreak, hostRoot);
        return new MachineState(
                memory,
                0,
                false,
                ElfImage.ABSENT_ADDRESS,
                ElfImage.ABSENT_ADDRESS,
                syscalls);
    }

    /// Writes a null-terminated UTF-8 string into guest memory.
    private static void writeGuestString(Memory memory, long address, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        memory.writeBytes(address, bytes, 0, bytes.length);
        memory.writeByte(address + bytes.length, (byte) 0);
    }

    /// Populates the syscall number and the first three argument registers.
    private static void setSyscall(MachineState state, long callNumber, long a0, long a1, long a2) {
        setSyscall(state, callNumber, a0, a1, a2, 0);
    }

    /// Populates the syscall number and the first four argument registers.
    private static void setSyscall(MachineState state, long callNumber, long a0, long a1, long a2, long a3) {
        setSyscall(state, callNumber, a0, a1, a2, a3, 0, 0);
    }

    /// Populates the syscall number and the first six argument registers.
    private static void setSyscall(
            MachineState state,
            long callNumber,
            long a0,
            long a1,
            long a2,
            long a3,
            long a4,
            long a5) {
        state.setRegister(10, a0);
        state.setRegister(11, a1);
        state.setRegister(12, a2);
        state.setRegister(13, a3);
        state.setRegister(14, a4);
        state.setRegister(15, a5);
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
