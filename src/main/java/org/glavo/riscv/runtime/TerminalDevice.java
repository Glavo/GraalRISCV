// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.memory.Memory;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/// Provides the guest controlling terminal exposed through `/dev/tty`.
@NotNullByDefault
final class TerminalDevice implements AutoCloseable {
    /// Linux `O_RDWR`.
    private static final int POSIX_OPEN_READ_WRITE = 2;

    /// Linux `O_NOCTTY`.
    private static final int POSIX_OPEN_NO_CONTROL_TTY = 0x100;

    /// The Linux generic `struct termios` size exposed to the guest.
    private static final int TERMIOS_SIZE = 36;

    /// The Linux generic `struct winsize` size exposed to the guest.
    private static final int WINDOW_SIZE_SIZE = 8;

    /// The default terminal row count exposed before a host value is available.
    private static final short DEFAULT_ROWS = 24;

    /// The default terminal column count exposed before a host value is available.
    private static final short DEFAULT_COLUMNS = 80;

    /// The backend used for terminal reads, writes, and optional host metadata.
    private final Backend backend;

    /// The current guest-visible termios byte image.
    private final byte[] termios = new byte[TERMIOS_SIZE];

    /// The current guest-visible terminal row count.
    private short rows = DEFAULT_ROWS;

    /// The current guest-visible terminal column count.
    private short columns = DEFAULT_COLUMNS;

    /// The number of guest process syscall handlers sharing this terminal.
    private int references = 1;

    /// Creates a terminal device backed by the supplied backend.
    private TerminalDevice(Backend backend) {
        this.backend = backend;
    }

    /// Creates a terminal device using a host tty when requested and available.
    static TerminalDevice open(InputStream in, OutputStream out, boolean useHostTerminal) {
        if (useHostTerminal) {
            @Nullable Backend hostBackend = PosixBackend.open();
            if (hostBackend != null) {
                return new TerminalDevice(hostBackend);
            }
        }
        return new TerminalDevice(new StreamBackend(in, out));
    }

    /// Adds one process-level reference to this shared terminal.
    synchronized TerminalDevice retain() {
        references++;
        return this;
    }

    /// Reads bytes from the terminal input backend.
    int read(byte[] buffer, int length) throws IOException {
        return backend.read(buffer, length);
    }

    /// Writes bytes to the terminal output backend.
    void write(byte[] buffer, int length) throws IOException {
        backend.write(buffer, length);
    }

    /// Writes the current guest-visible `struct termios`.
    void writeTermios(Memory memory, long address) {
        memory.writeBytes(address, termios, 0, termios.length);
    }

    /// Replaces the current guest-visible `struct termios`.
    void readTermios(Memory memory, long address) {
        byte[] bytes = memory.readBytes(address, TERMIOS_SIZE);
        System.arraycopy(bytes, 0, termios, 0, termios.length);
    }

    /// Writes the current guest-visible `struct winsize`.
    void writeWindowSize(Memory memory, long address) {
        @Nullable WindowSize hostWindowSize = backend.windowSize();
        if (hostWindowSize != null) {
            rows = hostWindowSize.rows();
            columns = hostWindowSize.columns();
        }

        memory.clear(address, WINDOW_SIZE_SIZE);
        memory.writeShort(address, rows);
        memory.writeShort(address + Short.BYTES, columns);
    }

    /// Replaces the current guest-visible `struct winsize`.
    void readWindowSize(Memory memory, long address) {
        rows = (short) memory.readUnsignedShort(address);
        columns = (short) memory.readUnsignedShort(address + Short.BYTES);
    }

    /// Releases one process-level reference and closes the backend after the final release.
    @Override
    public synchronized void close() throws IOException {
        references--;
        if (references < 0) {
            throw new AssertionError("terminal reference count became negative");
        }
        if (references == 0) {
            backend.close();
        }
    }

    /// Provides terminal I/O and optional host terminal metadata.
    private interface Backend extends AutoCloseable {
        /// Reads up to `length` bytes into the destination buffer.
        int read(byte[] buffer, int length) throws IOException;

        /// Writes exactly `length` bytes from the source buffer.
        void write(byte[] buffer, int length) throws IOException;

        /// Returns the host terminal window size, or null when unavailable.
        @Nullable WindowSize windowSize();

        /// Closes any backend-owned resources.
        @Override
        void close() throws IOException;
    }

    /// Stores a terminal window size.
    ///
    /// @param rows the row count
    /// @param columns the column count
    private record WindowSize(short rows, short columns) {
    }

    /// Implements terminal I/O using the Java streams configured for the guest process.
    ///
    /// @param in the input stream used for terminal reads
    /// @param out the output stream used for terminal writes
    private record StreamBackend(InputStream in, OutputStream out) implements Backend {
        /// Reads bytes from the configured input stream.
        @Override
        public int read(byte[] buffer, int length) throws IOException {
            int count = in.read(buffer, 0, length);
            return Math.max(count, 0);
        }

        /// Writes bytes to the configured output stream.
        @Override
        public void write(byte[] buffer, int length) throws IOException {
            out.write(buffer, 0, length);
            out.flush();
        }

        /// Returns null because stream-backed terminals do not expose host window metadata.
        @Override
        public @Nullable WindowSize windowSize() {
            return null;
        }

        /// Leaves externally owned streams open.
        @Override
        public void close() {
        }
    }

    /// Implements terminal I/O by calling POSIX terminal functions through the foreign-function API.
    ///
    /// @param fileDescriptor the host file descriptor for `/dev/tty`
    private record PosixBackend(int fileDescriptor) implements Backend {
        /// The Linux `TIOCGWINSZ` ioctl request.
        private static final long TIOCGWINSZ = 0x5413;

        /// Opens `/dev/tty` on Linux hosts, or returns null when unavailable.
        static @Nullable Backend open() {
            String osName = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            if (!osName.contains("linux")) {
                return null;
            }

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment path = arena.allocateFrom("/dev/tty", StandardCharsets.UTF_8);
                int fileDescriptor = (int) PosixHandles.OPEN.invokeExact(
                        path,
                        POSIX_OPEN_READ_WRITE | POSIX_OPEN_NO_CONTROL_TTY);
                return fileDescriptor < 0 ? null : new PosixBackend(fileDescriptor);
            } catch (Throwable exception) {
                return null;
            }
        }

        /// Reads bytes from the host terminal file descriptor.
        @Override
        public int read(byte[] buffer, int length) throws IOException {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(length);
                long count = (long) PosixHandles.READ.invokeExact(fileDescriptor, segment, (long) length);
                if (count < 0) {
                    throw new IOException("read(/dev/tty) failed");
                }
                segment.asByteBuffer().get(buffer, 0, (int) count);
                return (int) count;
            } catch (IOException exception) {
                throw exception;
            } catch (Throwable exception) {
                throw new IOException("read(/dev/tty) failed", exception);
            }
        }

        /// Writes bytes to the host terminal file descriptor.
        @Override
        public void write(byte[] buffer, int length) throws IOException {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(length);
                segment.asByteBuffer().put(buffer, 0, length);
                long written = 0;
                while (written < length) {
                    long count = (long) PosixHandles.WRITE.invokeExact(
                            fileDescriptor,
                            segment.asSlice(written),
                            (long) length - written);
                    if (count <= 0) {
                        throw new IOException("write(/dev/tty) failed");
                    }
                    written += count;
                }
            } catch (IOException exception) {
                throw exception;
            } catch (Throwable exception) {
                throw new IOException("write(/dev/tty) failed", exception);
            }
        }

        /// Reads the host terminal window size through `ioctl(TIOCGWINSZ)`.
        @Override
        public @Nullable WindowSize windowSize() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(WINDOW_SIZE_SIZE);
                int result = (int) PosixHandles.IOCTL.invokeExact(fileDescriptor, TIOCGWINSZ, segment);
                if (result != 0) {
                    return null;
                }
                ByteBuffer buffer = segment.asByteBuffer().order(ByteOrder.nativeOrder());
                return new WindowSize(buffer.getShort(0), buffer.getShort(Short.BYTES));
            } catch (Throwable exception) {
                return null;
            }
        }

        /// Closes the host terminal file descriptor.
        @Override
        public void close() throws IOException {
            try {
                int result = (int) PosixHandles.CLOSE.invokeExact(fileDescriptor);
                if (result != 0) {
                    throw new IOException("close(/dev/tty) failed");
                }
            } catch (IOException exception) {
                throw exception;
            } catch (Throwable exception) {
                throw new IOException("close(/dev/tty) failed", exception);
            }
        }
    }

    /// Holds native POSIX downcall handles.
    private static final class PosixHandles {
        /// The native linker used for libc calls.
        private static final Linker LINKER = Linker.nativeLinker();

        /// The default native symbol lookup.
        private static final SymbolLookup LOOKUP = LINKER.defaultLookup();

        /// Downcall handle for `open`.
        private static final MethodHandle OPEN = downcall(
                "open",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        /// Downcall handle for `read`.
        private static final MethodHandle READ = downcall(
                "read",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

        /// Downcall handle for `write`.
        private static final MethodHandle WRITE = downcall(
                "write",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

        /// Downcall handle for `ioctl`.
        private static final MethodHandle IOCTL = downcall(
                "ioctl",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

        /// Downcall handle for `close`.
        private static final MethodHandle CLOSE = downcall(
                "close",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

        /// Prevents construction.
        private PosixHandles() {
        }

        /// Creates a downcall handle for one native symbol.
        private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
            MemorySegment symbol = LOOKUP.find(name).orElseThrow(() -> new UnsatisfiedLinkError(name));
            return LINKER.downcallHandle(symbol, descriptor);
        }
    }
}
