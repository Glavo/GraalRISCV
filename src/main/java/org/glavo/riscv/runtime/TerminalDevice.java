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
import java.util.Arrays;

/// Provides the guest controlling terminal exposed through `/dev/tty`.
@NotNullByDefault
final class TerminalDevice implements AutoCloseable {
    /// Linux `O_RDWR`.
    private static final int POSIX_OPEN_READ_WRITE = 2;

    /// Linux `O_NOCTTY`.
    private static final int POSIX_OPEN_NO_CONTROL_TTY = 0x100;

    /// The Linux generic tty `TCSETS` ioctl request number.
    private static final long TERMIOS_SET_NOW_REQUEST = 0x5402;

    /// The Linux generic `struct termios` size exposed to the guest.
    private static final int TERMIOS_SIZE = 36;

    /// The Linux generic `struct termios2` size exposed to the guest.
    private static final int TERMIOS2_SIZE = 44;

    /// The Linux generic `struct winsize` size exposed to the guest.
    private static final int WINDOW_SIZE_SIZE = 8;

    /// The byte offset of `c_iflag` inside Linux generic `struct termios`.
    private static final int TERMIOS_INPUT_FLAGS_OFFSET = 0;

    /// The byte offset of `c_oflag` inside Linux generic `struct termios`.
    private static final int TERMIOS_OUTPUT_FLAGS_OFFSET = Integer.BYTES;

    /// The byte offset of `c_cflag` inside Linux generic `struct termios`.
    private static final int TERMIOS_CONTROL_FLAGS_OFFSET = 2 * Integer.BYTES;

    /// The byte offset of `c_lflag` inside Linux generic `struct termios`.
    private static final int TERMIOS_LOCAL_FLAGS_OFFSET = 3 * Integer.BYTES;

    /// The byte offset of `c_cc` inside Linux generic `struct termios`.
    private static final int TERMIOS_CONTROL_CHARS_OFFSET = 4 * Integer.BYTES + Byte.BYTES;

    /// The byte offset of `c_ispeed` inside Linux generic `struct termios2`.
    private static final int TERMIOS2_INPUT_SPEED_OFFSET = TERMIOS_SIZE;

    /// The byte offset of `c_ospeed` inside Linux generic `struct termios2`.
    private static final int TERMIOS2_OUTPUT_SPEED_OFFSET = TERMIOS2_INPUT_SPEED_OFFSET + Integer.BYTES;

    /// Linux generic `ICRNL`.
    private static final int TERMIOS_INPUT_CARRIAGE_RETURN_TO_NEWLINE = 0x00100;

    /// Linux generic `IXON`.
    private static final int TERMIOS_INPUT_START_STOP_OUTPUT_CONTROL = 0x00400;

    /// Linux generic `OPOST`.
    private static final int TERMIOS_OUTPUT_POST_PROCESSING = 0x00001;

    /// Linux generic `ONLCR`.
    private static final int TERMIOS_OUTPUT_NEWLINE_TO_CARRIAGE_RETURN_NEWLINE = 0x00004;

    /// Linux generic `B38400`.
    private static final int TERMIOS_CONTROL_BAUD_38400 = 0x0000f;

    /// Linux termios speed value for 38400 baud.
    private static final int TERMIOS_SPEED_38400 = 38_400;

    /// Linux generic `CS8`.
    private static final int TERMIOS_CONTROL_CHARACTER_SIZE_8 = 0x00030;

    /// Linux generic `CREAD`.
    private static final int TERMIOS_CONTROL_ENABLE_RECEIVER = 0x00080;

    /// Linux generic `ISIG`.
    private static final int TERMIOS_LOCAL_SIGNALS = 0x00001;

    /// Linux generic `ICANON`.
    private static final int TERMIOS_LOCAL_CANONICAL = 0x00002;

    /// Linux generic `ECHO`.
    private static final int TERMIOS_LOCAL_ECHO = 0x00008;

    /// Linux generic `ECHOE`.
    private static final int TERMIOS_LOCAL_ECHO_ERASE = 0x00010;

    /// Linux generic `ECHOK`.
    private static final int TERMIOS_LOCAL_ECHO_KILL = 0x00020;

    /// Linux generic `ECHOCTL`.
    private static final int TERMIOS_LOCAL_ECHO_CONTROL = 0x00200;

    /// Linux generic `ECHOKE`.
    private static final int TERMIOS_LOCAL_ECHO_KILL_ERASE = 0x00800;

    /// Linux generic `IEXTEN`.
    private static final int TERMIOS_LOCAL_EXTENDED_INPUT = 0x08000;

    /// Linux generic `VINTR`.
    private static final int TERMIOS_INTERRUPT_INDEX = 0;

    /// Linux generic `VQUIT`.
    private static final int TERMIOS_QUIT_INDEX = 1;

    /// Linux generic `VERASE`.
    private static final int TERMIOS_ERASE_INDEX = 2;

    /// Linux generic `VKILL`.
    private static final int TERMIOS_KILL_INDEX = 3;

    /// Linux generic `VEOF`.
    private static final int TERMIOS_END_OF_FILE_INDEX = 4;

    /// Linux generic `VTIME`.
    private static final int TERMIOS_READ_TIMEOUT_INDEX = 5;

    /// Linux generic `VMIN`.
    private static final int TERMIOS_MINIMUM_READ_INDEX = 6;

    /// Linux generic `VSTART`.
    private static final int TERMIOS_START_INDEX = 8;

    /// Linux generic `VSTOP`.
    private static final int TERMIOS_STOP_INDEX = 9;

    /// Linux generic `VSUSP`.
    private static final int TERMIOS_SUSPEND_INDEX = 10;

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

    /// Canonical input bytes buffered by guest-side line discipline.
    private byte[] pendingInput = new byte[0];

    /// The next buffered canonical input byte returned to the guest.
    private int pendingInputOffset = 0;

    /// Raw-mode input bytes already echoed by the guest-side Windows line discipline.
    private byte[] rawEchoedInput = new byte[0];

    /// The next raw-mode replay byte to suppress from guest output.
    private int rawEchoedInputOffset = 0;

    /// The current raw-mode escape sequence parsing state for local echo suppression.
    private int rawEscapeState = 0;

    /// The number of guest process syscall handlers sharing this terminal.
    private int references = 1;

    /// Creates a terminal device backed by the supplied backend.
    private TerminalDevice(Backend backend) {
        this.backend = backend;
        initializeDefaultTermios(termios);
        backend.readTermios(termios);
        if (backend.usesGuestLineDiscipline()) {
            backend.writeTermios(termios, TERMIOS_SET_NOW_REQUEST);
        }
    }

    /// Creates a terminal device using a host tty when requested and available.
    static TerminalDevice open(InputStream in, OutputStream out, boolean useHostTerminal) {
        if (useHostTerminal) {
            @Nullable Backend hostBackend = PosixBackend.open();
            if (hostBackend != null) {
                return new TerminalDevice(hostBackend);
            }
            return new TerminalDevice(new StreamBackend(in, out, HostTerminalMode.openStandardInput()));
        }
        return new TerminalDevice(new StreamBackend(in, out, WindowsConsoleMode.openStandardInput()));
    }

    /// Adds one process-level reference to this shared terminal.
    synchronized TerminalDevice retain() {
        references++;
        return this;
    }

    /// Reads bytes from the terminal input backend.
    int read(byte[] buffer, int length) throws IOException {
        if (backend.usesGuestLineDiscipline()) {
            return readWithGuestLineDiscipline(buffer, length);
        }

        int count = backend.read(buffer, length);
        normalizeInput(buffer, count);
        return count;
    }

    /// Writes bytes to the terminal output backend.
    void write(byte[] buffer, int length) throws IOException {
        int offset = suppressRawEchoReplay(buffer, length);
        if (offset < length) {
            byte[] bytes = offset == 0 ? buffer : Arrays.copyOfRange(buffer, offset, length);
            backend.write(bytes, bytes.length);
            if (containsLineBreak(bytes, bytes.length)) {
                clearRawEchoReplay();
            }
        }
    }

    /// Returns true when the original standard descriptors should be visible as tty descriptors.
    boolean supportsStandardFileDescriptors() {
        return backend.supportsStandardFileDescriptors();
    }

    /// Writes the current guest-visible `struct termios`.
    void writeTermios(Memory memory, long address) {
        memory.writeBytes(address, termios, 0, termios.length);
    }

    /// Writes the current guest-visible `struct termios2`.
    void writeTermios2(Memory memory, long address) {
        memory.writeBytes(address, termios, 0, termios.length);
        memory.writeInt(address + TERMIOS2_INPUT_SPEED_OFFSET, TERMIOS_SPEED_38400);
        memory.writeInt(address + TERMIOS2_OUTPUT_SPEED_OFFSET, TERMIOS_SPEED_38400);
    }

    /// Replaces the current guest-visible `struct termios`.
    void readTermios(Memory memory, long address, long request) {
        byte[] bytes = memory.readBytes(address, TERMIOS_SIZE);
        System.arraycopy(bytes, 0, termios, 0, termios.length);
        backend.writeTermios(termios, request);
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

    /// Initializes a stream-backed terminal with a sane Linux default state.
    private static void initializeDefaultTermios(byte[] buffer) {
        ByteBuffer bytes = ByteBuffer.wrap(buffer).order(ByteOrder.nativeOrder());
        bytes.putInt(TERMIOS_INPUT_FLAGS_OFFSET,
                TERMIOS_INPUT_CARRIAGE_RETURN_TO_NEWLINE
                        | TERMIOS_INPUT_START_STOP_OUTPUT_CONTROL);
        bytes.putInt(TERMIOS_OUTPUT_FLAGS_OFFSET,
                TERMIOS_OUTPUT_POST_PROCESSING
                        | TERMIOS_OUTPUT_NEWLINE_TO_CARRIAGE_RETURN_NEWLINE);
        bytes.putInt(TERMIOS_CONTROL_FLAGS_OFFSET,
                TERMIOS_CONTROL_BAUD_38400
                        | TERMIOS_CONTROL_CHARACTER_SIZE_8
                        | TERMIOS_CONTROL_ENABLE_RECEIVER);
        bytes.putInt(TERMIOS_LOCAL_FLAGS_OFFSET,
                TERMIOS_LOCAL_SIGNALS
                        | TERMIOS_LOCAL_CANONICAL
                        | TERMIOS_LOCAL_ECHO
                        | TERMIOS_LOCAL_ECHO_ERASE
                        | TERMIOS_LOCAL_ECHO_KILL
                        | TERMIOS_LOCAL_ECHO_CONTROL
                        | TERMIOS_LOCAL_ECHO_KILL_ERASE
                        | TERMIOS_LOCAL_EXTENDED_INPUT);

        buffer[TERMIOS_CONTROL_CHARS_OFFSET + TERMIOS_INTERRUPT_INDEX] = 3;
        buffer[TERMIOS_CONTROL_CHARS_OFFSET + TERMIOS_QUIT_INDEX] = 28;
        buffer[TERMIOS_CONTROL_CHARS_OFFSET + TERMIOS_ERASE_INDEX] = 127;
        buffer[TERMIOS_CONTROL_CHARS_OFFSET + TERMIOS_KILL_INDEX] = 21;
        buffer[TERMIOS_CONTROL_CHARS_OFFSET + TERMIOS_END_OF_FILE_INDEX] = 4;
        buffer[TERMIOS_CONTROL_CHARS_OFFSET + TERMIOS_READ_TIMEOUT_INDEX] = 0;
        buffer[TERMIOS_CONTROL_CHARS_OFFSET + TERMIOS_MINIMUM_READ_INDEX] = 1;
        buffer[TERMIOS_CONTROL_CHARS_OFFSET + TERMIOS_START_INDEX] = 17;
        buffer[TERMIOS_CONTROL_CHARS_OFFSET + TERMIOS_STOP_INDEX] = 19;
        buffer[TERMIOS_CONTROL_CHARS_OFFSET + TERMIOS_SUSPEND_INDEX] = 26;
    }

    /// Reads `c_lflag` from a Linux guest `struct termios` image.
    private static int readLocalFlags(byte[] buffer) {
        return ByteBuffer.wrap(buffer).order(ByteOrder.nativeOrder()).getInt(TERMIOS_LOCAL_FLAGS_OFFSET);
    }

    /// Reads `c_iflag` from a Linux guest `struct termios` image.
    private static int readInputFlags(byte[] buffer) {
        return ByteBuffer.wrap(buffer).order(ByteOrder.nativeOrder()).getInt(TERMIOS_INPUT_FLAGS_OFFSET);
    }

    /// Writes `c_lflag` to a Linux guest `struct termios` image.
    private static void writeLocalFlags(byte[] buffer, int localFlags) {
        ByteBuffer.wrap(buffer).order(ByteOrder.nativeOrder()).putInt(TERMIOS_LOCAL_FLAGS_OFFSET, localFlags);
    }

    /// Reads input through the guest-side canonical mode and echo implementation.
    private int readWithGuestLineDiscipline(byte[] buffer, int length) throws IOException {
        int pendingCount = drainPendingInput(buffer, length);
        if (pendingCount > 0) {
            return pendingCount;
        }

        if ((readLocalFlags(termios) & TERMIOS_LOCAL_CANONICAL) == 0) {
            int count = backend.read(buffer, length);
            normalizeInput(buffer, count);
            echoRawInput(buffer, count);
            return count;
        }

        byte[] line = new byte[Math.max(16, Math.min(length, 256))];
        int lineLength = 0;
        while (true) {
            byte[] oneByte = new byte[1];
            int count = backend.read(oneByte, oneByte.length);
            if (count == 0) {
                if (lineLength == 0) {
                    return 0;
                }
                break;
            }

            normalizeInput(oneByte, count);
            byte input = oneByte[0];
            int unsignedInput = Byte.toUnsignedInt(input);
            if (unsignedInput == controlChar(TERMIOS_END_OF_FILE_INDEX)) {
                if (lineLength == 0) {
                    return 0;
                }
                break;
            }
            if (unsignedInput == controlChar(TERMIOS_ERASE_INDEX)) {
                if (lineLength > 0) {
                    lineLength--;
                    echoErase();
                }
                continue;
            }

            if (lineLength == line.length) {
                line = Arrays.copyOf(line, line.length * 2);
            }
            line[lineLength] = input;
            lineLength++;
            echoInput(input);
            if (input == '\n') {
                break;
            }
        }

        pendingInput = Arrays.copyOf(line, lineLength);
        pendingInputOffset = 0;
        return drainPendingInput(buffer, length);
    }

    /// Returns buffered canonical input bytes to the guest.
    private int drainPendingInput(byte[] buffer, int length) {
        int available = pendingInput.length - pendingInputOffset;
        if (available <= 0) {
            return 0;
        }

        int count = Math.min(length, available);
        System.arraycopy(pendingInput, pendingInputOffset, buffer, 0, count);
        pendingInputOffset += count;
        if (pendingInputOffset == pendingInput.length) {
            pendingInput = new byte[0];
            pendingInputOffset = 0;
        }
        return count;
    }

    /// Applies guest input translations that are independent of canonical buffering.
    private void normalizeInput(byte[] buffer, int length) {
        if ((readInputFlags(termios) & TERMIOS_INPUT_CARRIAGE_RETURN_TO_NEWLINE) == 0) {
            return;
        }
        for (int index = 0; index < length; index++) {
            if (buffer[index] == '\r') {
                buffer[index] = '\n';
            }
        }
    }

    /// Echoes one input byte when guest `ECHO` is enabled.
    private void echoInput(byte input) throws IOException {
        int localFlags = readLocalFlags(termios);
        if ((localFlags & TERMIOS_LOCAL_ECHO) == 0) {
            return;
        }
        byte[] bytes = {input};
        backend.write(bytes, bytes.length);
    }

    /// Echoes one erase operation when guest erase echoing is enabled.
    private void echoErase() throws IOException {
        int localFlags = readLocalFlags(termios);
        if ((localFlags & (TERMIOS_LOCAL_ECHO | TERMIOS_LOCAL_ECHO_ERASE)) !=
                (TERMIOS_LOCAL_ECHO | TERMIOS_LOCAL_ECHO_ERASE)) {
            return;
        }
        byte[] bytes = {'\b', ' ', '\b'};
        backend.write(bytes, bytes.length);
    }

    /// Echoes printable raw-mode bytes when Windows-backed readline defers redisplay.
    private void echoRawInput(byte[] buffer, int length) throws IOException {
        int localFlags = readLocalFlags(termios);
        if ((localFlags & TERMIOS_LOCAL_CANONICAL) != 0
                || (localFlags & TERMIOS_LOCAL_ECHO) != 0
                || (localFlags & (TERMIOS_LOCAL_ECHO_ERASE | TERMIOS_LOCAL_ECHO_KILL)) == 0) {
            return;
        }

        for (int index = 0; index < length; index++) {
            byte input = buffer[index];
            int unsignedInput = Byte.toUnsignedInt(input);
            if (isRawEscapeByte(unsignedInput)) {
                continue;
            }
            if (unsignedInput == controlChar(TERMIOS_ERASE_INDEX)) {
                if (rawEchoedInput.length > 0) {
                    rawEchoedInput = Arrays.copyOf(rawEchoedInput, rawEchoedInput.length - 1);
                    rawEchoedInputOffset = Math.min(rawEchoedInputOffset, rawEchoedInput.length);
                    byte[] bytes = {'\b', ' ', '\b'};
                    backend.write(bytes, bytes.length);
                }
            } else if (input >= 0x20 && input != 0x7f) {
                byte[] bytes = {input};
                backend.write(bytes, bytes.length);
                appendRawEchoReplay(input);
            }
        }
    }

    /// Returns true when one raw input byte belongs to an escape sequence that should not be locally echoed.
    private boolean isRawEscapeByte(int input) {
        if (rawEscapeState == 0) {
            if (input == 0x1b) {
                rawEscapeState = 1;
                return true;
            }
            return false;
        }

        if (rawEscapeState == 1) {
            if (input == '[' || input == 'O') {
                rawEscapeState = 2;
                return true;
            }
            rawEscapeState = 0;
            return true;
        }

        if (input >= 0x40 && input <= 0x7e) {
            rawEscapeState = 0;
        }
        return true;
    }

    /// Appends one locally echoed raw input byte to the replay suppression buffer.
    private void appendRawEchoReplay(byte input) {
        int length = rawEchoedInput.length;
        rawEchoedInput = Arrays.copyOf(rawEchoedInput, length + 1);
        rawEchoedInput[length] = input;
    }

    /// Suppresses guest output that replays raw input already locally echoed by this terminal.
    private int suppressRawEchoReplay(byte[] buffer, int length) {
        if (rawEchoedInputOffset >= rawEchoedInput.length) {
            return 0;
        }

        int offset = 0;
        while (offset < length
                && rawEchoedInputOffset < rawEchoedInput.length
                && buffer[offset] == rawEchoedInput[rawEchoedInputOffset]) {
            offset++;
            rawEchoedInputOffset++;
        }
        if (offset == 0) {
            clearRawEchoReplay();
        }
        return offset;
    }

    /// Clears raw input replay suppression state.
    private void clearRawEchoReplay() {
        rawEchoedInput = new byte[0];
        rawEchoedInputOffset = 0;
        rawEscapeState = 0;
    }

    /// Returns true when the byte prefix contains a line break.
    private static boolean containsLineBreak(byte[] buffer, int length) {
        for (int index = 0; index < length; index++) {
            if (buffer[index] == '\n' || buffer[index] == '\r') {
                return true;
            }
        }
        return false;
    }

    /// Reads one unsigned control character value from the guest termios image.
    private int controlChar(int index) {
        return Byte.toUnsignedInt(termios[TERMIOS_CONTROL_CHARS_OFFSET + index]);
    }

    /// Provides terminal I/O and optional host terminal metadata.
    private interface Backend extends AutoCloseable {
        /// Reads up to `length` bytes into the destination buffer.
        int read(byte[] buffer, int length) throws IOException;

        /// Writes exactly `length` bytes from the source buffer.
        void write(byte[] buffer, int length) throws IOException;

        /// Returns the host terminal window size, or null when unavailable.
        @Nullable WindowSize windowSize();

        /// Reads a native terminal `struct termios` image into `buffer` when supported.
        default void readTermios(byte[] buffer) {
        }

        /// Applies a native terminal `struct termios` image when supported.
        default void writeTermios(byte[] buffer, long request) {
        }

        /// Returns true when this backend can safely expose original standard descriptors as a tty.
        default boolean supportsStandardFileDescriptors() {
            return false;
        }

        /// Returns true when guest-side terminal line discipline should process input.
        default boolean usesGuestLineDiscipline() {
            return false;
        }

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
    /// @param terminalMode the optional native terminal mode controller for host stdin
    private record StreamBackend(
            InputStream in,
            OutputStream out,
            @Nullable HostTerminalMode terminalMode) implements Backend {
        /// Reads bytes from the configured input stream.
        @Override
        public int read(byte[] buffer, int length) throws IOException {
            if (terminalMode != null) {
                return terminalMode.read(buffer, length);
            }
            int count = in.read(buffer, 0, length);
            return Math.max(count, 0);
        }

        /// Writes bytes to the configured output stream.
        @Override
        public void write(byte[] buffer, int length) throws IOException {
            out.write(buffer, 0, length);
            out.flush();
        }

        /// Returns the native terminal window size when the stream backend has a controller.
        @Override
        public @Nullable WindowSize windowSize() {
            return terminalMode == null ? null : terminalMode.windowSize();
        }

        /// Reads host standard-input termios when native access is available.
        @Override
        public void readTermios(byte[] buffer) {
            if (terminalMode != null) {
                terminalMode.readTermios(buffer);
            }
        }

        /// Applies guest termios to host standard input when native access is available.
        @Override
        public void writeTermios(byte[] buffer, long request) {
            if (terminalMode != null) {
                terminalMode.writeTermios(buffer, request);
            }
        }

        /// Returns true when a native terminal mode controller is available for host stdin.
        @Override
        public boolean supportsStandardFileDescriptors() {
            return terminalMode != null;
        }

        /// Returns true when the native mode controller requires guest-side line discipline.
        @Override
        public boolean usesGuestLineDiscipline() {
            return terminalMode != null && terminalMode.usesGuestLineDiscipline();
        }

        /// Restores host terminal mode and leaves externally owned streams open.
        @Override
        public void close() throws IOException {
            if (terminalMode != null) {
                terminalMode.close();
            }
        }
    }

    /// Controls the host terminal mode used by stream-backed standard input.
    private interface HostTerminalMode extends AutoCloseable {
        /// Opens a native controller for host standard input, or null when unavailable.
        static @Nullable HostTerminalMode openStandardInput() {
            @Nullable HostTerminalMode windowsMode = WindowsConsoleMode.openStandardInput();
            return windowsMode != null ? windowsMode : PosixTerminalMode.openStandardInput();
        }

        /// Reads a host terminal mode into the Linux guest `struct termios` image.
        void readTermios(byte[] buffer);

        /// Reads bytes from the host terminal input.
        int read(byte[] buffer, int length) throws IOException;

        /// Applies a Linux guest `struct termios` image to the host terminal mode.
        void writeTermios(byte[] buffer, long request);

        /// Returns the host terminal window size, or null when unavailable.
        default @Nullable WindowSize windowSize() {
            return null;
        }

        /// Returns true when this host input mode cannot provide visible canonical echo itself.
        default boolean usesGuestLineDiscipline() {
            return false;
        }

        /// Restores the original host terminal mode.
        @Override
        void close() throws IOException;
    }

    /// Applies guest termios updates to one host POSIX terminal file descriptor.
    ///
    /// @param fileDescriptor the host terminal file descriptor
    /// @param originalTermios the host terminal settings captured before guest changes
    private record PosixTerminalMode(int fileDescriptor, byte[] originalTermios) implements HostTerminalMode {
        /// Opens a terminal-mode controller for host standard input, or null when unavailable.
        static @Nullable PosixTerminalMode openStandardInput() {
            byte @Nullable [] originalTermios = PosixBackend.nativeTermios(0);
            return originalTermios == null ? null : new PosixTerminalMode(0, originalTermios);
        }

        /// Reads the current native terminal `struct termios` byte image.
        @Override
        public void readTermios(byte[] buffer) {
            byte @Nullable [] bytes = PosixBackend.nativeTermios(fileDescriptor);
            if (bytes == null) {
                return;
            }
            System.arraycopy(bytes, 0, buffer, 0, Math.min(bytes.length, buffer.length));
        }

        /// Reads bytes from the host terminal file descriptor.
        @Override
        public int read(byte[] buffer, int length) throws IOException {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(length);
                long count = (long) PosixHandles.READ.invokeExact(fileDescriptor, segment, (long) length);
                if (count < 0) {
                    throw new IOException("read(stdin) failed");
                }
                segment.asByteBuffer().get(buffer, 0, (int) count);
                return (int) count;
            } catch (IOException exception) {
                throw exception;
            } catch (Throwable exception) {
                throw new IOException("read(stdin) failed", exception);
            }
        }

        /// Applies a native terminal `struct termios` byte image.
        @Override
        public void writeTermios(byte[] buffer, long request) {
            if (request != PosixBackend.TCSETS && request != PosixBackend.TCSETSW && request != PosixBackend.TCSETSF) {
                return;
            }
            PosixBackend.writeNativeTermios(fileDescriptor, request, buffer);
        }

        /// Restores the original host terminal mode.
        @Override
        public void close() throws IOException {
            if (!PosixBackend.writeNativeTermios(fileDescriptor, PosixBackend.TCSETS, originalTermios)) {
                throw new IOException("restore(stdin) failed");
            }
        }
    }

    /// Applies guest termios updates to a Windows console input handle.
    ///
    /// @param inputHandle the host console input handle
    /// @param originalInputMode the input console mode captured before guest changes
    /// @param outputHandle the host console output handle
    /// @param originalOutputMode the output console mode captured before guest changes, or a negative value when unavailable
    /// @param shutdownHook the JVM shutdown hook that restores the captured console modes
    private record WindowsConsoleMode(
            MemorySegment inputHandle,
            int originalInputMode,
            MemorySegment outputHandle,
            int originalOutputMode,
            Thread shutdownHook) implements HostTerminalMode {
        /// Windows `STD_INPUT_HANDLE`.
        private static final int STANDARD_INPUT_HANDLE = -10;

        /// Windows `STD_OUTPUT_HANDLE`.
        private static final int STANDARD_OUTPUT_HANDLE = -11;

        /// Windows `ENABLE_PROCESSED_INPUT`.
        private static final int WINDOWS_ENABLE_PROCESSED_INPUT = 0x0001;

        /// Windows `ENABLE_LINE_INPUT`.
        private static final int WINDOWS_ENABLE_LINE_INPUT = 0x0002;

        /// Windows `ENABLE_ECHO_INPUT`.
        private static final int WINDOWS_ENABLE_ECHO_INPUT = 0x0004;

        /// Windows `ENABLE_VIRTUAL_TERMINAL_INPUT`.
        private static final int WINDOWS_ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;

        /// Windows `ENABLE_PROCESSED_OUTPUT`.
        private static final int WINDOWS_ENABLE_PROCESSED_OUTPUT = 0x0001;

        /// Windows `ENABLE_VIRTUAL_TERMINAL_PROCESSING`.
        private static final int WINDOWS_ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;

        /// The byte size of Windows `CONSOLE_SCREEN_BUFFER_INFO`.
        private static final long WINDOWS_CONSOLE_SCREEN_BUFFER_INFO_SIZE = 22;

        /// The byte offset of `srWindow.Left` inside Windows `CONSOLE_SCREEN_BUFFER_INFO`.
        private static final long WINDOWS_CONSOLE_SCREEN_BUFFER_WINDOW_LEFT_OFFSET = 10;

        /// The byte offset of `srWindow.Top` inside Windows `CONSOLE_SCREEN_BUFFER_INFO`.
        private static final long WINDOWS_CONSOLE_SCREEN_BUFFER_WINDOW_TOP_OFFSET = 12;

        /// The byte offset of `srWindow.Right` inside Windows `CONSOLE_SCREEN_BUFFER_INFO`.
        private static final long WINDOWS_CONSOLE_SCREEN_BUFFER_WINDOW_RIGHT_OFFSET = 14;

        /// The byte offset of `srWindow.Bottom` inside Windows `CONSOLE_SCREEN_BUFFER_INFO`.
        private static final long WINDOWS_CONSOLE_SCREEN_BUFFER_WINDOW_BOTTOM_OFFSET = 16;

        /// Opens a Windows console mode controller for host standard input and output.
        static @Nullable HostTerminalMode openStandardInput() {
            String osName = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            if (!osName.contains("win")) {
                return null;
            }

            try {
                MemorySegment handle = (MemorySegment) WindowsConsoleHandles.GET_STD_HANDLE.invokeExact(
                        STANDARD_INPUT_HANDLE);
                if (handle.equals(MemorySegment.NULL)) {
                    return null;
                }
                int mode = readConsoleMode(handle);
                if (mode < 0) {
                    return null;
                }

                MemorySegment outputHandle = (MemorySegment) WindowsConsoleHandles.GET_STD_HANDLE.invokeExact(
                        STANDARD_OUTPUT_HANDLE);
                int outputMode = readConsoleMode(outputHandle);
                if (outputMode >= 0) {
                    writeConsoleMode(outputHandle, outputMode
                            | WINDOWS_ENABLE_PROCESSED_OUTPUT
                            | WINDOWS_ENABLE_VIRTUAL_TERMINAL_PROCESSING);
                }
                Thread shutdownHook = new Thread(
                        () -> restoreConsoleModes(handle, mode, outputHandle, outputMode),
                        "GraalRISCV Windows console restore");
                Runtime.getRuntime().addShutdownHook(shutdownHook);
                return new WindowsConsoleMode(handle, mode, outputHandle, outputMode, shutdownHook);
            } catch (Throwable exception) {
                return null;
            }
        }

        /// Reads the current Windows console mode into the guest termios local flags.
        @Override
        public void readTermios(byte[] buffer) {
            int mode = readConsoleMode(inputHandle);
            if (mode < 0) {
                return;
            }
            int localFlags = readLocalFlags(buffer);
            localFlags &= ~(TERMIOS_LOCAL_SIGNALS | TERMIOS_LOCAL_CANONICAL | TERMIOS_LOCAL_ECHO);
            if ((mode & WINDOWS_ENABLE_PROCESSED_INPUT) != 0) {
                localFlags |= TERMIOS_LOCAL_SIGNALS;
            }
            if ((mode & WINDOWS_ENABLE_LINE_INPUT) != 0) {
                localFlags |= TERMIOS_LOCAL_CANONICAL;
            }
            if ((mode & WINDOWS_ENABLE_ECHO_INPUT) != 0) {
                localFlags |= TERMIOS_LOCAL_ECHO;
            }
            writeLocalFlags(buffer, localFlags);
        }

        /// Reads bytes from the Windows console input handle.
        @Override
        public int read(byte[] buffer, int length) throws IOException {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(length);
                MemorySegment countAddress = arena.allocate(ValueLayout.JAVA_INT);
                int result = (int) WindowsConsoleHandles.READ_FILE.invokeExact(
                        inputHandle,
                        segment,
                        length,
                        countAddress,
                        MemorySegment.NULL);
                if (result == 0) {
                    throw new IOException("read(console input) failed");
                }
                int count = countAddress.get(ValueLayout.JAVA_INT, 0);
                segment.asByteBuffer().get(buffer, 0, count);
                return count;
            } catch (IOException exception) {
                throw exception;
            } catch (Throwable exception) {
                throw new IOException("read(console input) failed", exception);
            }
        }

        /// Applies guest local flags to the Windows console input mode.
        @Override
        public void writeTermios(byte[] buffer, long request) {
            if (request != PosixBackend.TCSETS && request != PosixBackend.TCSETSW && request != PosixBackend.TCSETSF) {
                return;
            }

            int mode = readConsoleMode(inputHandle);
            if (mode < 0) {
                return;
            }

            int localFlags = readLocalFlags(buffer);
            mode &= ~(WINDOWS_ENABLE_PROCESSED_INPUT | WINDOWS_ENABLE_LINE_INPUT | WINDOWS_ENABLE_ECHO_INPUT);
            mode |= WINDOWS_ENABLE_VIRTUAL_TERMINAL_INPUT;
            if ((localFlags & TERMIOS_LOCAL_SIGNALS) != 0) {
                mode |= WINDOWS_ENABLE_PROCESSED_INPUT;
            }
            writeConsoleMode(inputHandle, mode);
        }

        /// Reads the visible Windows console window size.
        @Override
        public @Nullable WindowSize windowSize() {
            if (originalOutputMode < 0) {
                return null;
            }

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment info = arena.allocate(WINDOWS_CONSOLE_SCREEN_BUFFER_INFO_SIZE);
                int result = (int) WindowsConsoleHandles.GET_CONSOLE_SCREEN_BUFFER_INFO.invokeExact(
                        outputHandle,
                        info);
                if (result == 0) {
                    return null;
                }

                short left = info.get(ValueLayout.JAVA_SHORT, WINDOWS_CONSOLE_SCREEN_BUFFER_WINDOW_LEFT_OFFSET);
                short top = info.get(ValueLayout.JAVA_SHORT, WINDOWS_CONSOLE_SCREEN_BUFFER_WINDOW_TOP_OFFSET);
                short right = info.get(ValueLayout.JAVA_SHORT, WINDOWS_CONSOLE_SCREEN_BUFFER_WINDOW_RIGHT_OFFSET);
                short bottom = info.get(ValueLayout.JAVA_SHORT, WINDOWS_CONSOLE_SCREEN_BUFFER_WINDOW_BOTTOM_OFFSET);
                int columns = right - left + 1;
                int rows = bottom - top + 1;
                if (columns <= 0 || rows <= 0 || columns > Short.MAX_VALUE || rows > Short.MAX_VALUE) {
                    return null;
                }
                return new WindowSize((short) rows, (short) columns);
            } catch (Throwable exception) {
                return null;
            }
        }

        /// Returns true because Windows console input echo is not written through the guest output stream.
        @Override
        public boolean usesGuestLineDiscipline() {
            return true;
        }

        /// Restores the original Windows console modes.
        @Override
        public void close() throws IOException {
            removeShutdownHook();
            @Nullable IOException failure = restoreConsoleModes(
                    inputHandle,
                    originalInputMode,
                    outputHandle,
                    originalOutputMode);
            if (failure != null) {
                throw failure;
            }
        }

        /// Removes the shutdown hook when normal close restores the console modes first.
        private void removeShutdownHook() {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException exception) {
                // The JVM is already shutting down, so the hook will handle best-effort restoration.
            }
        }

        /// Restores Windows console modes and returns the first failure, if any.
        private static @Nullable IOException restoreConsoleModes(
                MemorySegment inputHandle,
                int inputMode,
                MemorySegment outputHandle,
                int outputMode) {
            @Nullable IOException failure = null;
            if (outputMode >= 0 && !writeConsoleMode(outputHandle, outputMode)) {
                failure = new IOException("restore(console output) failed");
            }
            if (!writeConsoleMode(inputHandle, inputMode)) {
                IOException inputFailure = new IOException("restore(console input) failed");
                if (failure != null) {
                    inputFailure.addSuppressed(failure);
                }
                return inputFailure;
            }
            return failure;
        }

        /// Reads a Windows console mode, or returns a negative value on failure.
        private static int readConsoleMode(MemorySegment handle) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment mode = arena.allocate(ValueLayout.JAVA_INT);
                int result = (int) WindowsConsoleHandles.GET_CONSOLE_MODE.invokeExact(handle, mode);
                return result == 0 ? -1 : mode.get(ValueLayout.JAVA_INT, 0);
            } catch (Throwable exception) {
                return -1;
            }
        }

        /// Writes a Windows console mode.
        private static boolean writeConsoleMode(MemorySegment handle, int mode) {
            try {
                int result = (int) WindowsConsoleHandles.SET_CONSOLE_MODE.invokeExact(handle, mode);
                return result != 0;
            } catch (Throwable exception) {
                return false;
            }
        }
    }

    /// Implements terminal I/O by calling POSIX terminal functions through the foreign-function API.
    ///
    /// @param fileDescriptor the host file descriptor for `/dev/tty`
    /// @param originalTermios the host terminal settings captured before guest changes
    private record PosixBackend(int fileDescriptor, byte @Nullable [] originalTermios) implements Backend {
        /// The Linux generic tty `TCGETS` ioctl request.
        private static final long TCGETS = 0x5401;

        /// The Linux generic tty `TCSETS` ioctl request.
        private static final long TCSETS = 0x5402;

        /// The Linux generic tty `TCSETSW` ioctl request.
        private static final long TCSETSW = 0x5403;

        /// The Linux generic tty `TCSETSF` ioctl request.
        private static final long TCSETSF = 0x5404;

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
                if (fileDescriptor < 0) {
                    return null;
                }
                return new PosixBackend(fileDescriptor, nativeTermios(fileDescriptor));
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

        /// Reads the host terminal `struct termios` byte image.
        @Override
        public void readTermios(byte[] buffer) {
            byte @Nullable [] bytes = nativeTermios(fileDescriptor);
            if (bytes == null) {
                return;
            }
            System.arraycopy(bytes, 0, buffer, 0, Math.min(bytes.length, buffer.length));
        }

        /// Applies the supplied `struct termios` byte image to the host terminal.
        @Override
        public void writeTermios(byte[] buffer, long request) {
            if (request != TCSETS && request != TCSETSW && request != TCSETSF) {
                return;
            }
            writeNativeTermios(fileDescriptor, request, buffer);
        }

        /// Returns true because `/dev/tty` supplies native terminal mode control.
        @Override
        public boolean supportsStandardFileDescriptors() {
            return true;
        }

        /// Closes the host terminal file descriptor.
        @Override
        public void close() throws IOException {
            @Nullable IOException failure = null;
            if (originalTermios != null && !writeNativeTermios(fileDescriptor, TCSETS, originalTermios)) {
                failure = new IOException("restore(/dev/tty) failed");
            }
            try {
                int result = (int) PosixHandles.CLOSE.invokeExact(fileDescriptor);
                if (result != 0) {
                    IOException closeFailure = new IOException("close(/dev/tty) failed");
                    if (failure != null) {
                        closeFailure.addSuppressed(failure);
                    }
                    throw closeFailure;
                }
            } catch (IOException exception) {
                throw exception;
            } catch (Throwable exception) {
                throw new IOException("close(/dev/tty) failed", exception);
            }
            if (failure != null) {
                throw failure;
            }
        }

        /// Reads a native `struct termios` byte image from a host terminal descriptor.
        private static byte @Nullable [] nativeTermios(int fileDescriptor) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(TERMIOS_SIZE);
                int result = (int) PosixHandles.IOCTL.invokeExact(fileDescriptor, TCGETS, segment);
                if (result != 0) {
                    return null;
                }
                byte[] bytes = new byte[TERMIOS_SIZE];
                segment.asByteBuffer().get(bytes);
                return bytes;
            } catch (Throwable exception) {
                return null;
            }
        }

        /// Writes a native `struct termios` byte image to a host terminal descriptor.
        private static boolean writeNativeTermios(int fileDescriptor, long request, byte[] bytes) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(TERMIOS_SIZE);
                segment.asByteBuffer().put(bytes, 0, Math.min(bytes.length, TERMIOS_SIZE));
                int result = (int) PosixHandles.IOCTL.invokeExact(fileDescriptor, request, segment);
                return result == 0;
            } catch (Throwable exception) {
                return false;
            }
        }
    }

    /// Holds Windows console downcall handles.
    private static final class WindowsConsoleHandles {
        /// The native linker used for Windows console calls.
        private static final Linker LINKER = Linker.nativeLinker();

        /// The Windows Kernel32 symbol lookup.
        private static final SymbolLookup LOOKUP = kernel32Lookup();

        /// Downcall handle for `GetStdHandle`.
        private static final MethodHandle GET_STD_HANDLE = downcall(
                "GetStdHandle",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        /// Downcall handle for `GetConsoleMode`.
        private static final MethodHandle GET_CONSOLE_MODE = downcall(
                "GetConsoleMode",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        /// Downcall handle for `ReadFile`.
        private static final MethodHandle READ_FILE = downcall(
                "ReadFile",
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));

        /// Downcall handle for `SetConsoleMode`.
        private static final MethodHandle SET_CONSOLE_MODE = downcall(
                "SetConsoleMode",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        /// Downcall handle for `GetConsoleScreenBufferInfo`.
        private static final MethodHandle GET_CONSOLE_SCREEN_BUFFER_INFO = downcall(
                "GetConsoleScreenBufferInfo",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        /// Prevents construction.
        private WindowsConsoleHandles() {
        }

        /// Returns a lookup for Windows Kernel32 symbols.
        private static SymbolLookup kernel32Lookup() {
            try {
                return SymbolLookup.libraryLookup("kernel32", Arena.global());
            } catch (IllegalArgumentException exception) {
                return SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
            }
        }

        /// Creates a downcall handle for one Windows console symbol.
        private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
            MemorySegment symbol = LOOKUP.find(name).orElseThrow(() -> new UnsatisfiedLinkError(name));
            return LINKER.downcallHandle(symbol, descriptor);
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
