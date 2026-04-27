package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests guest syscall behavior directly against architectural state.
@NotNullByDefault
public final class GuestSyscallsTest {
    /// The guest program counter supplied to direct syscall tests.
    private static final long TEST_PC = Memory.DEFAULT_BASE_ADDRESS;

    /// The Linux RISC-V syscall number for `read`.
    private static final long SYS_READ = 63;

    /// The Linux RISC-V syscall number for `write`.
    private static final long SYS_WRITE = 64;

    /// The Linux RISC-V syscall number for `brk`.
    private static final long SYS_BRK = 214;

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

    /// Verifies that invalid file descriptors return `-1` and do not touch host output streams.
    @Test
    public void invalidFileDescriptorsReturnMinusOne() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            MachineState state = state(
                    memory,
                    new ByteArrayInputStream("A".getBytes(StandardCharsets.UTF_8)),
                    out,
                    err,
                    memory.baseAddress());

            setSyscall(state, SYS_READ, 9, memory.baseAddress(), 1);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(-1L, state.register(10));
            assertEquals(0, memory.readUnsignedByte(memory.baseAddress()));

            memory.writeByte(memory.baseAddress(), (byte) 'B');
            setSyscall(state, SYS_WRITE, 9, memory.baseAddress(), 1);
            state.syscalls().handle(state, TEST_PC);
            assertEquals(-1L, state.register(10));
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
            ByteArrayInputStream in,
            ByteArrayOutputStream out,
            ByteArrayOutputStream err,
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
}
