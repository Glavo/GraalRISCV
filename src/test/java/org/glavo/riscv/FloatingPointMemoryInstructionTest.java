package org.glavo.riscv;

import org.glavo.riscv.exception.*;
import org.glavo.riscv.memory.*;
import org.glavo.riscv.parser.*;
import org.glavo.riscv.runtime.*;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/// Tests floating-point register state and floating-point memory instructions.
@NotNullByDefault
public final class FloatingPointMemoryInstructionTest {
    /// The guest address used for decoded floating-point instruction tests.
    private static final long TEST_PC = Memory.DEFAULT_BASE_ADDRESS;

    /// The aligned guest data address used by floating-point memory tests.
    private static final long DATA_ADDRESS = Memory.DEFAULT_BASE_ADDRESS + 512;

    /// The base-address source register used by floating-point memory tests.
    private static final int ADDRESS_REGISTER = 5;

    /// The Linux syscall register.
    private static final int SYSCALL_REGISTER = 17;

    /// The Linux `exit` syscall number.
    private static final long EXIT_SYSCALL = 93;

    /// Verifies that 64-bit floating-point loads and stores preserve raw register bits.
    @Test
    public void doubleLoadStorePreservesRawBits() {
        try (TestMachine machine = TestMachine.create()) {
            long value = 0x4009_21fb_5444_2d18L;
            machine.memory().writeLong(DATA_ADDRESS, value);
            loadInstructions(machine.memory(), fld(10, ADDRESS_REGISTER, 0), fsd(ADDRESS_REGISTER, 10, Long.BYTES), ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(ADDRESS_REGISTER, DATA_ADDRESS);

            runDecodedProgram(machine);

            assertEquals(value, machine.state().floatingPointRegister(10));
            assertEquals(value, machine.memory().readLong(DATA_ADDRESS + Long.BYTES));
        }
    }

    /// Verifies that 32-bit floating-point loads NaN-box values and stores the low word.
    @Test
    public void wordLoadNaNBoxesAndStoreWritesLowBits() {
        try (TestMachine machine = TestMachine.create()) {
            int value = 0x3f80_0000;
            machine.memory().writeInt(DATA_ADDRESS, value);
            loadInstructions(machine.memory(), flw(10, ADDRESS_REGISTER, 0), fsw(ADDRESS_REGISTER, 10, Integer.BYTES), ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(ADDRESS_REGISTER, DATA_ADDRESS);

            runDecodedProgram(machine);

            assertEquals(0xffff_ffff_3f80_0000L, machine.state().floatingPointRegister(10));
            assertEquals(value, machine.memory().readInt(DATA_ADDRESS + Integer.BYTES));
        }
    }

    /// Verifies compressed floating-point memory instructions that use compressed base registers.
    @Test
    public void compressedDoubleLoadStoreUsesCompressedBaseOffsets() {
        try (TestMachine machine = TestMachine.create()) {
            long value = 0x7ff8_0000_0000_1234L;
            machine.memory().writeLong(DATA_ADDRESS + 16, value);

            ByteBuffer code = ByteBuffer.allocate((2 * Short.BYTES) + Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            ElfTestImages.putShort(code, cFld(9, 8, 16));
            ElfTestImages.putShort(code, cFsd(8, 9, 24));
            ElfTestImages.putInt(code, ElfTestImages.ecall());
            loadCode(machine.memory(), code.array());
            prepareExit(machine.state());
            machine.state().setRegister(8, DATA_ADDRESS);

            runDecodedProgram(machine);

            assertEquals(value, machine.state().floatingPointRegister(9));
            assertEquals(value, machine.memory().readLong(DATA_ADDRESS + 24));
        }
    }

    /// Verifies compressed floating-point memory instructions that use the stack pointer.
    @Test
    public void compressedStackDoubleLoadStoreUsesStackOffsets() {
        try (TestMachine machine = TestMachine.create()) {
            long value = 0x0011_2233_4455_6677L;
            machine.memory().writeLong(DATA_ADDRESS + 32, value);

            ByteBuffer code = ByteBuffer.allocate((2 * Short.BYTES) + Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            ElfTestImages.putShort(code, cFldsp(12, 32));
            ElfTestImages.putShort(code, cFsdsp(12, 40));
            ElfTestImages.putInt(code, ElfTestImages.ecall());
            loadCode(machine.memory(), code.array());
            prepareExit(machine.state());
            machine.state().setRegister(2, DATA_ADDRESS);

            runDecodedProgram(machine);

            assertEquals(value, machine.state().floatingPointRegister(12));
            assertEquals(value, machine.memory().readLong(DATA_ADDRESS + 40));
        }
    }

    /// Sets the syscall register so a trailing `ecall` exits the decoded test program.
    private static void prepareExit(MachineState state) {
        state.setRegister(SYSCALL_REGISTER, EXIT_SYSCALL);
    }

    /// Runs decoded blocks until the test program exits.
    private static void runDecodedProgram(TestMachine machine) {
        runDecodedProgram(machine, 8);
    }

    /// Runs decoded blocks until exit or fails after the supplied block budget.
    private static void runDecodedProgram(TestMachine machine, int maxBlocks) {
        for (int index = 0; index < maxBlocks; index++) {
            try {
                DecodedBlockTestExecutor.execute(machine.state(), RiscVDecoder.decodeBlock(machine.memory(), machine.state().pc()));
            } catch (ProgramExitException exception) {
                return;
            }
        }
        fail("Decoded program did not exit within " + maxBlocks + " blocks");
    }

    /// Loads little-endian instruction words at the decoder test address.
    private static void loadInstructions(Memory memory, int... instructions) {
        ByteBuffer code = ByteBuffer.allocate(instructions.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int instruction : instructions) {
            code.putInt(instruction);
        }
        loadCode(memory, code.array());
    }

    /// Loads raw instruction bytes at the decoder test address.
    private static void loadCode(Memory memory, byte[] code) {
        memory.load(TEST_PC, code, 0, code.length);
    }

    /// Encodes `flw`.
    private static int flw(int rd, int rs1, int immediate) {
        return ElfTestImages.iType(0x07, rd, 2, rs1, immediate);
    }

    /// Encodes `fld`.
    private static int fld(int rd, int rs1, int immediate) {
        return ElfTestImages.iType(0x07, rd, 3, rs1, immediate);
    }

    /// Encodes `fsw`.
    private static int fsw(int rs1, int rs2, int immediate) {
        return floatingPointStore(2, rs1, rs2, immediate);
    }

    /// Encodes `fsd`.
    private static int fsd(int rs1, int rs2, long immediate) {
        return floatingPointStore(3, rs1, rs2, (int) immediate);
    }

    /// Encodes a floating-point S-format store instruction.
    private static int floatingPointStore(int funct3, int rs1, int rs2, int immediate) {
        return (((immediate >>> 5) & 0x7f) << 25)
                | (rs2 << 20)
                | (rs1 << 15)
                | (funct3 << 12)
                | ((immediate & 0x1f) << 7)
                | 0x27;
    }

    /// Encodes `c.fld`.
    private static int cFld(int rd, int rs1, int offset) {
        return compressedLoadStore(0b001, rd, rs1, offset);
    }

    /// Encodes `c.fsd`.
    private static int cFsd(int rs1, int rs2, int offset) {
        return compressedLoadStore(0b101, rs2, rs1, offset);
    }

    /// Encodes `c.fldsp`.
    private static int cFldsp(int rd, int offset) {
        return (0b001 << 13)
                | (((offset >>> 5) & 0x1) << 12)
                | (rd << 7)
                | (((offset >>> 3) & 0x3) << 5)
                | (((offset >>> 6) & 0x7) << 2)
                | 0b10;
    }

    /// Encodes `c.fsdsp`.
    private static int cFsdsp(int rs2, int offset) {
        return (0b101 << 13)
                | (((offset >>> 3) & 0x7) << 10)
                | (((offset >>> 6) & 0x7) << 7)
                | (rs2 << 2)
                | 0b10;
    }

    /// Encodes a compressed CL or CS-format floating-point instruction.
    private static int compressedLoadStore(int funct3, int dataRegister, int baseRegister, int offset) {
        return (funct3 << 13)
                | (((offset >>> 3) & 0x7) << 10)
                | (compressedRegister(baseRegister) << 7)
                | (((offset >>> 6) & 0x3) << 5)
                | (compressedRegister(dataRegister) << 2);
    }

    /// Converts a full register index to a compressed register field.
    private static int compressedRegister(int register) {
        return register - 8;
    }

    /// Owns a floating-point memory test machine and its closeable resources.
    ///
    /// @param memory the guest memory under test
    /// @param syscalls the syscall handler attached to the machine state
    /// @param state the mutable architectural state under test
    @NotNullByDefault
    private record TestMachine(
            Memory memory,
            GuestSyscalls syscalls,
            MachineState state) implements AutoCloseable {
        /// Creates a test machine initialized at the decoder test address.
        private static TestMachine create() {
            Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 4096);
            GuestSyscalls syscalls = new GuestSyscalls(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    memory.baseAddress());
            MachineState state = new MachineState(
                    memory,
                    0,
                    false,
                    ElfImage.ABSENT_ADDRESS,
                    ElfImage.ABSENT_ADDRESS,
                    syscalls);
            state.setPc(TEST_PC);
            return new TestMachine(memory, syscalls, state);
        }

        /// Closes the syscall handler and guest memory backing this test machine.
        @Override
        public void close() {
            try {
                syscalls.close();
            } finally {
                memory.close();
            }
        }
    }
}
