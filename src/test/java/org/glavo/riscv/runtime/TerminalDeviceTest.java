// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.memory.Memory;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests terminal device line-discipline behavior.
@NotNullByDefault
public final class TerminalDeviceTest {
    /// The Linux generic tty `TCSETS` ioctl request number.
    private static final long TCSETS = 0x5402;

    /// The Linux generic `struct termios` size exposed to the guest.
    private static final int TERMIOS_SIZE = 36;

    /// The byte offset of `c_lflag` inside Linux generic `struct termios`.
    private static final int TERMIOS_LOCAL_FLAGS_OFFSET = 3 * Integer.BYTES;

    /// Linux generic `ICANON`.
    private static final int TERMIOS_LOCAL_CANONICAL = 0x00002;

    /// Linux generic `ECHO`.
    private static final int TERMIOS_LOCAL_ECHO = 0x00008;

    /// Linux generic `ECHOE`.
    private static final int TERMIOS_LOCAL_ECHO_ERASE = 0x00010;

    /// Linux generic `ECHOK`.
    private static final int TERMIOS_LOCAL_ECHO_KILL = 0x00020;

    /// Verifies raw local echo suppresses readline erase replay without crossing the input boundary.
    @Test
    public void rawLocalEchoSuppressesGuestEraseReplayWithoutCrossingLineStart() throws Exception {
        byte[] input = {'l', 's', 0x7f, 0x7f, 0x7f};
        byte[] visibleText = {'l', 's'};
        byte[] visibleErases = {'\b', ' ', '\b', '\b', ' ', '\b'};
        byte[] visibleLine = {'l', 's', '\b', ' ', '\b', '\b', ' ', '\b'};
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024);
             TerminalDevice terminal = TerminalDevice.openWithGuestLineDiscipline(
                     new ByteArrayInputStream(input),
                     out)) {
            long termiosAddress = memory.baseAddress();
            terminal.writeTermios(memory, termiosAddress);
            int localFlags = memory.readInt(termiosAddress + TERMIOS_LOCAL_FLAGS_OFFSET);
            localFlags &= ~(TERMIOS_LOCAL_CANONICAL | TERMIOS_LOCAL_ECHO);
            localFlags |= TERMIOS_LOCAL_ECHO_ERASE | TERMIOS_LOCAL_ECHO_KILL;
            memory.writeInt(termiosAddress + TERMIOS_LOCAL_FLAGS_OFFSET, localFlags);
            terminal.readTermios(memory, termiosAddress, TCSETS);

            byte[] text = new byte[visibleText.length];
            assertEquals(visibleText.length, terminal.read(text, text.length));
            assertArrayEquals(visibleText, text);
            assertArrayEquals(visibleText, out.toByteArray());

            terminal.write(visibleText, visibleText.length);
            assertArrayEquals(visibleText, out.toByteArray());

            byte[] erases = new byte[3];
            assertEquals(erases.length, terminal.read(erases, erases.length));
            assertArrayEquals(new byte[]{0x7f, 0x7f, 0x7f}, erases);
            assertArrayEquals(visibleLine, out.toByteArray());

            terminal.write(visibleErases, visibleErases.length);
            assertArrayEquals(visibleLine, out.toByteArray());
        }
    }
}
