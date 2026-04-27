package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/// Handles the small Linux-compatible syscall subset exposed by the simulator.
@NotNullByDefault
public final class GuestSyscalls {
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

    /// The Linux RISC-V syscall number for `exit`.
    private static final long SYS_EXIT = 93;

    /// The Linux RISC-V syscall number for `exit_group`.
    private static final long SYS_EXIT_GROUP = 94;

    /// The Linux RISC-V syscall number for `set_tid_address`.
    private static final long SYS_SET_TID_ADDRESS = 96;

    /// The Linux RISC-V syscall number for `set_robust_list`.
    private static final long SYS_SET_ROBUST_LIST = 99;

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

    /// The Linux RISC-V syscall number for `getrandom`.
    private static final long SYS_GETRANDOM = 278;

    /// Linux `EBADF` as a raw negative syscall result.
    private static final long EBADF = -9;

    /// Linux `EINVAL` as a raw negative syscall result.
    private static final long EINVAL = -22;

    /// Linux `ENOTTY` as a raw negative syscall result.
    private static final long ENOTTY = -25;

    /// Linux `ESPIPE` as a raw negative syscall result.
    private static final long ESPIPE = -29;

    /// The maximum Linux `iovcnt` accepted by `readv` and `writev`.
    private static final long IOV_MAX = 1024;

    /// The byte size of one Linux RISC-V 64-bit `struct iovec`.
    private static final int IOVEC_SIZE = 16;

    /// The byte offset of `iov_base` inside `struct iovec`.
    private static final int IOVEC_BASE_OFFSET = 0;

    /// The byte offset of `iov_len` inside `struct iovec`.
    private static final int IOVEC_LENGTH_OFFSET = 8;

    /// The Linux generic tty `TCGETS` ioctl request number.
    private static final long TCGETS = 0x5401;

    /// The Linux generic tty `TIOCGWINSZ` ioctl request number.
    private static final long TIOCGWINSZ = 0x5413;

    /// The byte size of Linux generic `struct termios`.
    private static final int TERMIOS_SIZE = 36;

    /// The byte size of Linux generic `struct winsize`.
    private static final int WINSIZE_SIZE = 8;

    /// The default terminal row count returned by `TIOCGWINSZ`.
    private static final short DEFAULT_TERMINAL_ROWS = 24;

    /// The default terminal column count returned by `TIOCGWINSZ`.
    private static final short DEFAULT_TERMINAL_COLUMNS = 80;

    /// The byte size of Linux generic 64-bit `struct stat`.
    private static final int STAT_SIZE = 128;

    /// The byte size of Linux generic 64-bit kernel `sigset_t`.
    private static final long KERNEL_SIGSET_SIZE = 8;

    /// The byte size of Linux generic 64-bit kernel `struct sigaction`.
    private static final long KERNEL_SIGACTION_SIZE = 32;

    /// The lowest regular Linux signal number accepted by signal syscalls.
    private static final long MIN_SIGNAL_NUMBER = 1;

    /// The highest regular Linux signal number accepted by signal syscalls.
    private static final long MAX_SIGNAL_NUMBER = 64;

    /// Linux `SIG_BLOCK` signal-mask operation.
    private static final long SIG_BLOCK = 0;

    /// Linux `SIG_UNBLOCK` signal-mask operation.
    private static final long SIG_UNBLOCK = 1;

    /// Linux `SIG_SETMASK` signal-mask operation.
    private static final long SIG_SETMASK = 2;

    /// The `st_ino` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_INODE_OFFSET = 8;

    /// The `st_mode` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_MODE_OFFSET = 16;

    /// The `st_nlink` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_LINK_COUNT_OFFSET = 20;

    /// The `st_blksize` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_BLOCK_SIZE_OFFSET = 56;

    /// The Linux `S_IFCHR` file type bit used for character devices.
    private static final int STAT_MODE_CHARACTER_DEVICE = 0020000;

    /// The file permission bits exposed for standard streams.
    private static final int STAT_MODE_READ_WRITE_ALL = 0666;

    /// The `st_mode` value exposed for the simulator standard streams.
    private static final int STANDARD_STREAM_STAT_MODE = STAT_MODE_CHARACTER_DEVICE | STAT_MODE_READ_WRITE_ALL;

    /// The block size exposed for standard streams.
    private static final int STANDARD_STREAM_BLOCK_SIZE = 4096;

    /// The fixed guest process id returned by process identity syscalls.
    private static final long GUEST_PROCESS_ID = 1;

    /// The non-cryptographic seed used for deterministic `getrandom` bytes.
    private static final long RANDOM_SEED = 0x4752_4953_4356_0001L;

    /// Linux `GRND_NONBLOCK`; accepted because simulator randomness never blocks.
    private static final long GRND_NONBLOCK = 0x0001;

    /// Linux `GRND_RANDOM`; accepted and mapped to the same deterministic source.
    private static final long GRND_RANDOM = 0x0002;

    /// The supported Linux `getrandom` flags mask.
    private static final long GETRANDOM_SUPPORTED_FLAGS = GRND_NONBLOCK | GRND_RANDOM;

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

    /// The address supplied by `set_tid_address`, or zero when unset.
    private long clearChildTidAddress;

    /// The robust futex list head address supplied by `set_robust_list`.
    private long robustListHeadAddress;

    /// The robust futex list structure length supplied by `set_robust_list`.
    private long robustListLength;

    /// The next deterministic random state used by `getrandom`.
    private long randomState = RANDOM_SEED;

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
        if (callNumber == SYS_IOCTL) {
            state.setRegister(10, ioctl((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_CLOSE) {
            state.setRegister(10, close((int) state.register(10)));
            return;
        }
        if (callNumber == SYS_LSEEK) {
            state.setRegister(10, lseek((int) state.register(10), state.register(11), (int) state.register(12)));
            return;
        }
        if (callNumber == SYS_READ) {
            state.setRegister(10, read((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_WRITE) {
            state.setRegister(10, write((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_READV) {
            state.setRegister(10, readv((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_WRITEV) {
            state.setRegister(10, writev((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_FSTAT) {
            state.setRegister(10, fstat((int) state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_EXIT || callNumber == SYS_EXIT_GROUP) {
            throw new ProgramExitException(state.register(10));
        }
        if (callNumber == SYS_SET_TID_ADDRESS) {
            state.setRegister(10, setTidAddress(state.register(10)));
            return;
        }
        if (callNumber == SYS_SET_ROBUST_LIST) {
            state.setRegister(10, setRobustList(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_RT_SIGACTION) {
            state.setRegister(10, rtSigaction(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            return;
        }
        if (callNumber == SYS_RT_SIGPROCMASK) {
            state.setRegister(10, rtSigprocmask(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            return;
        }
        if (callNumber == SYS_GETPID || callNumber == SYS_GETTID) {
            state.setRegister(10, GUEST_PROCESS_ID);
            return;
        }
        if (callNumber == SYS_BRK) {
            state.setRegister(10, brk(state.register(10)));
            return;
        }
        if (callNumber == SYS_GETRANDOM) {
            state.setRegister(10, getrandom(state.register(10), state.register(11), state.register(12)));
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
            return EBADF;
        }
        if (length < 0) {
            return EINVAL;
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
            return EINVAL;
        }

        OutputStream stream = outputStreamFor(fileDescriptor);
        if (stream == null) {
            return EBADF;
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

    /// Reads bytes from guest stdin into a guest `struct iovec` array.
    private long readv(int fileDescriptor, long iovecAddress, long iovecCount) {
        if (fileDescriptor != 0) {
            return EBADF;
        }
        if (iovecCount < 0 || iovecCount > IOV_MAX) {
            return EINVAL;
        }

        long total = 0;
        try {
            for (long index = 0; index < iovecCount; index++) {
                long entryAddress = iovecAddress + index * IOVEC_SIZE;
                long baseAddress = memory.readLong(entryAddress + IOVEC_BASE_OFFSET);
                long length = memory.readLong(entryAddress + IOVEC_LENGTH_OFFSET);
                if (length < 0) {
                    return EINVAL;
                }
                if (length > Integer.MAX_VALUE) {
                    throw new RiscVException("Guest readv syscall buffer is too large: " + length);
                }
                if (length == 0) {
                    continue;
                }

                byte[] buffer = new byte[(int) length];
                int count = in.read(buffer);
                if (count < 0) {
                    return total;
                }

                memory.writeBytes(baseAddress, buffer, 0, count);
                total += count;
                if (count < length) {
                    return total;
                }
            }
            return total;
        } catch (IOException exception) {
            throw new RiscVException("Guest readv syscall failed", exception);
        }
    }

    /// Writes buffers from a guest `struct iovec` array to stdout or stderr.
    private long writev(int fileDescriptor, long iovecAddress, long iovecCount) {
        if (iovecCount < 0 || iovecCount > IOV_MAX) {
            return EINVAL;
        }

        OutputStream stream = outputStreamFor(fileDescriptor);
        if (stream == null) {
            return EBADF;
        }

        long total = 0;
        try {
            for (long index = 0; index < iovecCount; index++) {
                long entryAddress = iovecAddress + index * IOVEC_SIZE;
                long baseAddress = memory.readLong(entryAddress + IOVEC_BASE_OFFSET);
                long length = memory.readLong(entryAddress + IOVEC_LENGTH_OFFSET);
                if (length < 0) {
                    return EINVAL;
                }
                if (length > Integer.MAX_VALUE) {
                    throw new RiscVException("Guest writev syscall buffer is too large: " + length);
                }

                byte[] bytes = memory.readBytes(baseAddress, length);
                stream.write(bytes);
                total += bytes.length;
            }
            stream.flush();
            return total;
        } catch (IOException exception) {
            throw new RiscVException("Guest writev syscall failed", exception);
        }
    }

    /// Handles `close` for the simulator standard streams without closing host streams.
    private static long close(int fileDescriptor) {
        return isStandardFileDescriptor(fileDescriptor) ? 0 : EBADF;
    }

    /// Handles `lseek` for standard streams, which are not seekable.
    private static long lseek(int fileDescriptor, long offset, int whence) {
        if (!isStandardFileDescriptor(fileDescriptor)) {
            return EBADF;
        }
        if (whence < 0 || whence > 2) {
            return EINVAL;
        }
        return ESPIPE;
    }

    /// Writes a minimal Linux generic 64-bit `struct stat` for standard streams.
    private long fstat(int fileDescriptor, long statAddress) {
        if (!isStandardFileDescriptor(fileDescriptor)) {
            return EBADF;
        }

        memory.clear(statAddress, STAT_SIZE);
        memory.writeLong(statAddress + STAT_INODE_OFFSET, fileDescriptor + 1L);
        memory.writeInt(statAddress + STAT_MODE_OFFSET, STANDARD_STREAM_STAT_MODE);
        memory.writeInt(statAddress + STAT_LINK_COUNT_OFFSET, 1);
        memory.writeInt(statAddress + STAT_BLOCK_SIZE_OFFSET, STANDARD_STREAM_BLOCK_SIZE);
        return 0;
    }

    /// Handles tty-related ioctls used by common `isatty` and stdio setup paths.
    private long ioctl(int fileDescriptor, long request, long argument) {
        if (!isStandardFileDescriptor(fileDescriptor)) {
            return EBADF;
        }
        if (request == TCGETS) {
            memory.clear(argument, TERMIOS_SIZE);
            return 0;
        }
        if (request == TIOCGWINSZ) {
            memory.clear(argument, WINSIZE_SIZE);
            memory.writeShort(argument, DEFAULT_TERMINAL_ROWS);
            memory.writeShort(argument + Short.BYTES, DEFAULT_TERMINAL_COLUMNS);
            return 0;
        }
        return ENOTTY;
    }

    /// Stores the guest clear-child-TID pointer and returns the fixed guest thread id.
    private long setTidAddress(long address) {
        clearChildTidAddress = address;
        return GUEST_PROCESS_ID;
    }

    /// Accepts a single-threaded robust futex list registration.
    private long setRobustList(long headAddress, long length) {
        if (length < 0) {
            return EINVAL;
        }
        robustListHeadAddress = headAddress;
        robustListLength = length;
        return 0;
    }

    /// Accepts signal action setup for a guest that never delivers host signals.
    private long rtSigaction(long signalNumber, long actionAddress, long oldActionAddress, long sigsetSize) {
        if (sigsetSize != KERNEL_SIGSET_SIZE) {
            return EINVAL;
        }
        if (signalNumber < MIN_SIGNAL_NUMBER || signalNumber > MAX_SIGNAL_NUMBER) {
            return EINVAL;
        }

        if (oldActionAddress != 0) {
            memory.clear(oldActionAddress, KERNEL_SIGACTION_SIZE);
        }
        return 0;
    }

    /// Accepts signal-mask queries and no-op updates for a single-threaded guest.
    private long rtSigprocmask(long how, long setAddress, long oldSetAddress, long sigsetSize) {
        if (sigsetSize != KERNEL_SIGSET_SIZE) {
            return EINVAL;
        }
        if (setAddress != 0 && how != SIG_BLOCK && how != SIG_UNBLOCK && how != SIG_SETMASK) {
            return EINVAL;
        }

        if (oldSetAddress != 0) {
            memory.writeLong(oldSetAddress, 0);
        }
        return 0;
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

    /// Fills a guest buffer with deterministic bytes for the Linux `getrandom` syscall.
    private long getrandom(long address, long length, long flags) {
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

        byte[] bytes = new byte[(int) length];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = nextRandomByte();
        }
        memory.writeBytes(address, bytes, 0, bytes.length);
        return bytes.length;
    }

    /// Returns the next deterministic pseudo-random byte.
    private byte nextRandomByte() {
        randomState = randomState * 6364136223846793005L + 1442695040888963407L;
        return (byte) (randomState >>> 56);
    }

    /// Returns the host output stream mapped to a guest file descriptor.
    private @Nullable OutputStream outputStreamFor(int fileDescriptor) {
        if (fileDescriptor == 1) {
            return out;
        }
        if (fileDescriptor == 2) {
            return err;
        }
        return null;
    }

    /// Returns true when the file descriptor is one of stdin, stdout, or stderr.
    private static boolean isStandardFileDescriptor(int fileDescriptor) {
        return fileDescriptor >= 0 && fileDescriptor <= 2;
    }
}
