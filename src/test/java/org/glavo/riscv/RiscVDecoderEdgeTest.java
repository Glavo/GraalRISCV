// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

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

/// Tests decoded RV64I edge cases through executable decoder output.
@NotNullByDefault
public final class RiscVDecoderEdgeTest {
    /// The guest address used for standalone decoder tests.
    private static final long TEST_PC = Memory.DEFAULT_BASE_ADDRESS;

    /// The destination register inspected by most decoder tests.
    private static final int RESULT_REGISTER = 10;

    /// The first source register used by register-operation decoder tests.
    private static final int LEFT_REGISTER = 5;

    /// The second source register used by register-operation decoder tests.
    private static final int RIGHT_REGISTER = 6;

    /// The return-address register written by jump-and-link instructions.
    private static final int LINK_REGISTER = 1;

    /// The Linux syscall register.
    private static final int SYSCALL_REGISTER = 17;

    /// The Linux `exit` syscall number.
    private static final long EXIT_SYSCALL = 93;

    /// Verifies that decoded immediates follow RV64 sign-extension rules.
    @Test
    public void decodedImmediatesSignExtendToRv64() {
        assertDecodedResult(-1L, ElfTestImages.addi(RESULT_REGISTER, 0, -1));
        assertDecodedResult(-1L, addiw(RESULT_REGISTER, 0, -1));
        assertDecodedResult(-4096L, lui(RESULT_REGISTER, 0xffff_f000));
        assertDecodedResult(TEST_PC - 4096L, ElfTestImages.auipc(RESULT_REGISTER, 0xffff_f000));
    }

    /// Verifies that decoded shift instructions use the architectural shift masks.
    @Test
    public void decodedShiftsUseArchitecturalMasks() {
        assertDecodedRegisterResult(Long.MIN_VALUE, slli(RESULT_REGISTER, LEFT_REGISTER, 63), 1, 0);
        assertDecodedRegisterResult(1, sll(RESULT_REGISTER, LEFT_REGISTER, RIGHT_REGISTER), 1, 64);
        assertDecodedRegisterResult(2, sll(RESULT_REGISTER, LEFT_REGISTER, RIGHT_REGISTER), 1, 65);
        assertDecodedRegisterResult(1, sllw(RESULT_REGISTER, LEFT_REGISTER, RIGHT_REGISTER), 1, 32);
        assertDecodedRegisterResult(-1L, sraw(RESULT_REGISTER, LEFT_REGISTER, RIGHT_REGISTER), 0xffff_ffffL, 63);
    }

    /// Verifies branch decoding, signedness, and taken versus fall-through control flow.
    @Test
    public void decodedBranchesUseExpectedControlFlow() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    beq(LEFT_REGISTER, RIGHT_REGISTER, 12),
                    ElfTestImages.addi(RESULT_REGISTER, 0, 1),
                    ElfTestImages.ecall(),
                    ElfTestImages.addi(RESULT_REGISTER, 0, 2),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(LEFT_REGISTER, 7);
            machine.state().setRegister(RIGHT_REGISTER, 7);

            runDecodedProgram(machine);

            assertEquals(2, machine.state().register(RESULT_REGISTER));
        }

        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    bne(LEFT_REGISTER, RIGHT_REGISTER, 12),
                    ElfTestImages.addi(RESULT_REGISTER, 0, 1),
                    ElfTestImages.ecall(),
                    ElfTestImages.addi(RESULT_REGISTER, 0, 2),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(LEFT_REGISTER, 7);
            machine.state().setRegister(RIGHT_REGISTER, 7);

            runDecodedProgram(machine);

            assertEquals(1, machine.state().register(RESULT_REGISTER));
        }

        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    bltu(LEFT_REGISTER, RIGHT_REGISTER, 12),
                    ElfTestImages.addi(RESULT_REGISTER, 0, 1),
                    ElfTestImages.ecall(),
                    ElfTestImages.addi(RESULT_REGISTER, 0, 2),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(LEFT_REGISTER, 1);
            machine.state().setRegister(RIGHT_REGISTER, -1L);

            runDecodedProgram(machine);

            assertEquals(2, machine.state().register(RESULT_REGISTER));
        }
    }

    /// Verifies decoded jump behavior, link values, and low-bit clearing for `jalr` targets.
    @Test
    public void decodedJumpsUseExpectedTargetsAndLinks() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    jal(LINK_REGISTER, 12),
                    ElfTestImages.addi(RESULT_REGISTER, 0, 1),
                    ElfTestImages.ecall(),
                    ElfTestImages.addi(RESULT_REGISTER, 0, 2),
                    ElfTestImages.ecall());
            prepareExit(machine.state());

            runDecodedProgram(machine);

            assertEquals(TEST_PC + Integer.BYTES, machine.state().register(LINK_REGISTER));
            assertEquals(2, machine.state().register(RESULT_REGISTER));
        }

        try (TestMachine machine = TestMachine.create()) {
            ByteBuffer code = ByteBuffer.allocate((3 * Integer.BYTES) + Short.BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN);
            ElfTestImages.putInt(code, jalr(LINK_REGISTER, LEFT_REGISTER, 0));
            ElfTestImages.putShort(code, ElfTestImages.compressedNop());
            ElfTestImages.putInt(code, ElfTestImages.addi(RESULT_REGISTER, 0, 5));
            ElfTestImages.putInt(code, ElfTestImages.ecall());

            loadCode(machine.memory(), code.array());
            prepareExit(machine.state());
            machine.state().setRegister(LEFT_REGISTER, TEST_PC + 7);

            runDecodedProgram(machine);

            assertEquals(TEST_PC + Integer.BYTES, machine.state().register(LINK_REGISTER));
            assertEquals(5, machine.state().register(RESULT_REGISTER));
        }
    }

    /// Verifies mixed compressed and wide integer instructions in one decoded stream.
    @Test
    public void decodedCompressedIntegerStreamMixesInstructionWidths() {
        try (TestMachine machine = TestMachine.create()) {
            ByteBuffer code = ByteBuffer.allocate((5 * Short.BYTES) + (3 * Integer.BYTES))
                    .order(ByteOrder.LITTLE_ENDIAN);
            putCompressed(code, cLi(8, 6));
            putCompressed(code, cAddi(8, -1));
            putCompressed(code, cMv(RESULT_REGISTER, 8));
            putCompressed(code, cSlli(RESULT_REGISTER, 1));
            ElfTestImages.putInt(code, ElfTestImages.addi(9, 0, 32));
            putCompressed(code, cAdd(RESULT_REGISTER, 9));
            ElfTestImages.putInt(code, ElfTestImages.addi(SYSCALL_REGISTER, 0, (int) EXIT_SYSCALL));
            ElfTestImages.putInt(code, ElfTestImages.ecall());

            loadCode(machine.memory(), code.array());

            runDecodedProgram(machine);

            assertEquals(42, machine.state().register(RESULT_REGISTER));
        }
    }

    /// Verifies mixed compressed memory instructions with 32-bit instructions at 16-bit-aligned addresses.
    @Test
    public void decodedCompressedMemoryStreamMixesInstructionWidths() {
        try (TestMachine machine = TestMachine.create()) {
            ByteBuffer code = ByteBuffer.allocate((3 * Short.BYTES) + (3 * Integer.BYTES))
                    .order(ByteOrder.LITTLE_ENDIAN);
            putCompressed(code, cAddi4spn(8, 16));
            ElfTestImages.putInt(code, ElfTestImages.addi(9, 0, 42));
            putCompressed(code, cSd(8, 9, 0));
            putCompressed(code, cLd(RESULT_REGISTER, 8, 0));
            ElfTestImages.putInt(code, ElfTestImages.addi(SYSCALL_REGISTER, 0, (int) EXIT_SYSCALL));
            ElfTestImages.putInt(code, ElfTestImages.ecall());

            loadCode(machine.memory(), code.array());
            machine.state().setRegister(2, TEST_PC + 128);

            runDecodedProgram(machine);

            assertEquals(42, machine.state().register(RESULT_REGISTER));
        }
    }

    /// Verifies compressed branch and jump targets inside a mixed-width instruction stream.
    @Test
    public void decodedCompressedControlFlowStreamMixesInstructionWidths() {
        try (TestMachine machine = TestMachine.create()) {
            ByteBuffer code = ByteBuffer.allocate((4 * Short.BYTES) + (5 * Integer.BYTES))
                    .order(ByteOrder.LITTLE_ENDIAN);
            putCompressed(code, cLi(8, 0));
            putCompressed(code, cBeqz(8, 10));
            ElfTestImages.putInt(code, ElfTestImages.addi(RESULT_REGISTER, 0, 1));
            ElfTestImages.putInt(code, ElfTestImages.ecall());
            putCompressed(code, cLi(RESULT_REGISTER, 2));
            putCompressed(code, cJ(6));
            ElfTestImages.putInt(code, ElfTestImages.addi(RESULT_REGISTER, 0, 3));
            ElfTestImages.putInt(code, ElfTestImages.addi(SYSCALL_REGISTER, 0, (int) EXIT_SYSCALL));
            ElfTestImages.putInt(code, ElfTestImages.ecall());

            loadCode(machine.memory(), code.array());
            prepareExit(machine.state());

            runDecodedProgram(machine);

            assertEquals(2, machine.state().register(RESULT_REGISTER));
        }
    }

    /// Executes one decoded instruction and asserts the destination register result.
    private static void assertDecodedResult(long expected, int instruction) {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(machine.memory(), instruction, ElfTestImages.ecall());
            prepareExit(machine.state());

            runDecodedProgram(machine);

            assertEquals(expected, machine.state().register(RESULT_REGISTER));
        }
    }

    /// Executes one decoded register instruction and asserts the destination register result.
    private static void assertDecodedRegisterResult(long expected, int instruction, long leftValue, long rightValue) {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(machine.memory(), instruction, ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(LEFT_REGISTER, leftValue);
            machine.state().setRegister(RIGHT_REGISTER, rightValue);

            runDecodedProgram(machine);

            assertEquals(expected, machine.state().register(RESULT_REGISTER));
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

    /// Writes a little-endian compressed instruction into a code buffer.
    private static void putCompressed(ByteBuffer buffer, int instruction) {
        ElfTestImages.putShort(buffer, instruction);
    }

    /// Encodes `addiw`.
    private static int addiw(int rd, int rs1, int immediate) {
        return ElfTestImages.iType(0x1b, rd, 0, rs1, immediate);
    }

    /// Encodes `slli`.
    private static int slli(int rd, int rs1, int shiftAmount) {
        return ElfTestImages.iType(0x13, rd, 1, rs1, shiftAmount);
    }

    /// Encodes `sll`.
    private static int sll(int rd, int rs1, int rs2) {
        return ElfTestImages.rType(0x33, rd, 1, rs1, rs2, 0);
    }

    /// Encodes `sllw`.
    private static int sllw(int rd, int rs1, int rs2) {
        return ElfTestImages.rType(0x3b, rd, 1, rs1, rs2, 0);
    }

    /// Encodes `sraw`.
    private static int sraw(int rd, int rs1, int rs2) {
        return ElfTestImages.rType(0x3b, rd, 5, rs1, rs2, 0x20);
    }

    /// Encodes `lui`.
    private static int lui(int rd, int immediate) {
        return (immediate & 0xffff_f000) | (rd << 7) | 0x37;
    }

    /// Encodes `beq`.
    private static int beq(int rs1, int rs2, int offset) {
        return branch(0, rs1, rs2, offset);
    }

    /// Encodes `bne`.
    private static int bne(int rs1, int rs2, int offset) {
        return branch(1, rs1, rs2, offset);
    }

    /// Encodes `bltu`.
    private static int bltu(int rs1, int rs2, int offset) {
        return branch(6, rs1, rs2, offset);
    }

    /// Encodes a B-type branch.
    private static int branch(int funct3, int rs1, int rs2, int offset) {
        return (((offset >>> 12) & 0x1) << 31)
                | (((offset >>> 5) & 0x3f) << 25)
                | (rs2 << 20)
                | (rs1 << 15)
                | (funct3 << 12)
                | (((offset >>> 1) & 0xf) << 8)
                | (((offset >>> 11) & 0x1) << 7)
                | 0x63;
    }

    /// Encodes `jal`.
    private static int jal(int rd, int offset) {
        return (((offset >>> 20) & 0x1) << 31)
                | (((offset >>> 1) & 0x3ff) << 21)
                | (((offset >>> 11) & 0x1) << 20)
                | (((offset >>> 12) & 0xff) << 12)
                | (rd << 7)
                | 0x6f;
    }

    /// Encodes `jalr`.
    private static int jalr(int rd, int rs1, int offset) {
        return ElfTestImages.iType(0x67, rd, 0, rs1, offset);
    }

    /// Encodes `c.li`.
    private static int cLi(int rd, int immediate) {
        return compressedImmediate(0b010, rd, immediate);
    }

    /// Encodes `c.addi`.
    private static int cAddi(int rd, int immediate) {
        return compressedImmediate(0b000, rd, immediate);
    }

    /// Encodes `c.slli`.
    private static int cSlli(int rd, int shiftAmount) {
        return (rd << 7)
                | (((shiftAmount >>> 5) & 0x1) << 12)
                | ((shiftAmount & 0x1f) << 2)
                | 0b10;
    }

    /// Encodes `c.mv`.
    private static int cMv(int rd, int rs2) {
        return (0b100 << 13) | (rd << 7) | (rs2 << 2) | 0b10;
    }

    /// Encodes `c.add`.
    private static int cAdd(int rd, int rs2) {
        return (0b100 << 13) | (1 << 12) | (rd << 7) | (rs2 << 2) | 0b10;
    }

    /// Encodes `c.addi4spn`.
    private static int cAddi4spn(int rd, int immediate) {
        return (((immediate >>> 6) & 0xf) << 7)
                | (((immediate >>> 4) & 0x3) << 11)
                | (((immediate >>> 3) & 0x1) << 5)
                | (((immediate >>> 2) & 0x1) << 6)
                | ((compressedRegister(rd)) << 2);
    }

    /// Encodes `c.ld`.
    private static int cLd(int rd, int rs1, int offset) {
        return compressedLoadStore(0b011, rd, rs1, offset);
    }

    /// Encodes `c.sd`.
    private static int cSd(int rs1, int rs2, int offset) {
        return compressedLoadStore(0b111, rs2, rs1, offset);
    }

    /// Encodes `c.beqz`.
    private static int cBeqz(int rs1, int offset) {
        return compressedBranch(0b110, rs1, offset);
    }

    /// Encodes `c.j`.
    private static int cJ(int offset) {
        return (0b101 << 13)
                | (((offset >>> 11) & 0x1) << 12)
                | (((offset >>> 4) & 0x1) << 11)
                | (((offset >>> 8) & 0x3) << 9)
                | (((offset >>> 10) & 0x1) << 8)
                | (((offset >>> 6) & 0x1) << 7)
                | (((offset >>> 7) & 0x1) << 6)
                | (((offset >>> 1) & 0x7) << 3)
                | (((offset >>> 5) & 0x1) << 2)
                | 0b01;
    }

    /// Encodes a compressed CI-format immediate instruction.
    private static int compressedImmediate(int funct3, int rd, int immediate) {
        return (funct3 << 13)
                | (((immediate >>> 5) & 0x1) << 12)
                | (rd << 7)
                | ((immediate & 0x1f) << 2)
                | 0b01;
    }

    /// Encodes a compressed CL or CS-format instruction.
    private static int compressedLoadStore(int funct3, int dataRegister, int baseRegister, int offset) {
        return (funct3 << 13)
                | (((offset >>> 3) & 0x7) << 10)
                | (compressedRegister(baseRegister) << 7)
                | (((offset >>> 6) & 0x3) << 5)
                | (compressedRegister(dataRegister) << 2);
    }

    /// Encodes a compressed CB-format branch instruction.
    private static int compressedBranch(int funct3, int rs1, int offset) {
        return (funct3 << 13)
                | (((offset >>> 8) & 0x1) << 12)
                | (((offset >>> 3) & 0x3) << 10)
                | (compressedRegister(rs1) << 7)
                | (((offset >>> 6) & 0x3) << 5)
                | (((offset >>> 1) & 0x3) << 3)
                | (((offset >>> 5) & 0x1) << 2)
                | 0b01;
    }

    /// Converts a full register index to a compressed register field.
    private static int compressedRegister(int register) {
        return register - 8;
    }

    /// Owns a decoder-test machine and its closeable resources.
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
            Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 4096, null);
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
