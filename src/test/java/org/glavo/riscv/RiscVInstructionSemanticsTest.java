// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.memory.*;
import org.glavo.riscv.parser.*;
import org.glavo.riscv.runtime.*;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests individual instruction execution against architectural edge cases.
@NotNullByDefault
public final class RiscVInstructionSemanticsTest {
    /// The guest address used for standalone instruction execution.
    private static final long TEST_PC = Memory.DEFAULT_BASE_ADDRESS;

    /// The destination register used by standalone instruction tests.
    private static final int RESULT_REGISTER = 10;

    /// The first source register used by standalone instruction tests.
    private static final int LEFT_REGISTER = 5;

    /// The second source register used by standalone instruction tests.
    private static final int RIGHT_REGISTER = 6;

    /// Verifies the RV64M division-by-zero result rules.
    @Test
    public void divisionByZeroFollowsRv64mRules() {
        assertEquals(-1L, executeRegisterOperation(RiscVOperation.DIV, 123, 0));
        assertEquals(-1L, executeRegisterOperation(RiscVOperation.DIVU, 123, 0));
        assertEquals(123, executeRegisterOperation(RiscVOperation.REM, 123, 0));
        assertEquals(123, executeRegisterOperation(RiscVOperation.REMU, 123, 0));

        assertEquals(-1L, executeRegisterOperation(RiscVOperation.DIVW, 123, 0));
        assertEquals(-1L, executeRegisterOperation(RiscVOperation.DIVUW, 123, 0));
        assertEquals(123, executeRegisterOperation(RiscVOperation.REMW, 123, 0));
        assertEquals(123, executeRegisterOperation(RiscVOperation.REMUW, 123, 0));
    }

    /// Verifies signed division overflow behavior for minimum integer values divided by negative one.
    @Test
    public void signedDivisionOverflowFollowsRv64mRules() {
        assertEquals(Long.MIN_VALUE, executeRegisterOperation(RiscVOperation.DIV, Long.MIN_VALUE, -1));
        assertEquals(0, executeRegisterOperation(RiscVOperation.REM, Long.MIN_VALUE, -1));

        assertEquals((long) Integer.MIN_VALUE, executeRegisterOperation(RiscVOperation.DIVW, Integer.MIN_VALUE, -1));
        assertEquals(0, executeRegisterOperation(RiscVOperation.REMW, Integer.MIN_VALUE, -1));
    }

    /// Verifies unsigned word division and remainder results are sign-extended to RV64.
    @Test
    public void unsignedWordDivisionSignExtendsResults() {
        assertEquals(-2L, executeRegisterOperation(RiscVOperation.DIVUW, 0xffff_fffEL, 1));
        assertEquals(1, executeRegisterOperation(RiscVOperation.REMUW, 0xffff_ffffL, 2));
    }

    /// Verifies the signedness of the RV64M high-half multiplication variants.
    @Test
    public void multiplyHighVariantsUseCorrectSignedness() {
        assertEquals(-1L, executeRegisterOperation(RiscVOperation.MULH, -2, 3));
        assertEquals(-1L, executeRegisterOperation(RiscVOperation.MULHSU, -2, 3));
        assertEquals(1L, executeRegisterOperation(RiscVOperation.MULHU, -1, 2));
        assertEquals(-1L, executeRegisterOperation(RiscVOperation.MULH, Long.MIN_VALUE, 2));
        assertEquals(-1L, executeRegisterOperation(RiscVOperation.MULHSU, Long.MIN_VALUE, 2));
        assertEquals(-2L, executeRegisterOperation(RiscVOperation.MULHU, -1, -1));
    }

    /// Verifies that `mulw` keeps the low word and sign-extends it to RV64.
    @Test
    public void wordMultiplicationSignExtendsLowWord() {
        assertEquals((long) Integer.MIN_VALUE, executeRegisterOperation(RiscVOperation.MULW, 0x4000_0000L, 2));
    }

    /// Verifies integer comparison instructions around signed and unsigned extremes.
    @Test
    public void comparisonOperationsUseArchitecturalSignedness() {
        assertEquals(1, executeRegisterOperation(RiscVOperation.SLT, Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(0, executeRegisterOperation(RiscVOperation.SLT, Long.MAX_VALUE, Long.MIN_VALUE));
        assertEquals(0, executeRegisterOperation(RiscVOperation.SLTU, Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(1, executeRegisterOperation(RiscVOperation.SLTU, Long.MAX_VALUE, Long.MIN_VALUE));
        assertEquals(1, executeImmediateOperation(RiscVOperation.SLTI, Long.MIN_VALUE, 0, -1));
        assertEquals(0, executeImmediateOperation(RiscVOperation.SLTI, Long.MAX_VALUE, 0, -1));
        assertEquals(1, executeImmediateOperation(RiscVOperation.SLTIU, 1, 0, -1));
        assertEquals(0, executeImmediateOperation(RiscVOperation.SLTIU, -1, 0, 1));
    }

    /// Verifies register shift operations mask counts and word shifts sign-extend their low-word result.
    @Test
    public void registerShiftsMaskCountsAndWordResultsSignExtend() {
        assertEquals(1, executeRegisterOperation(RiscVOperation.SLL, 1, 64));
        assertEquals(2, executeRegisterOperation(RiscVOperation.SLL, 1, 65));
        assertEquals(1, executeRegisterOperation(RiscVOperation.SRL, 1, 64));
        assertEquals(-1L, executeRegisterOperation(RiscVOperation.SRA, -1, 64));

        assertEquals(1, executeRegisterOperation(RiscVOperation.SLLW, 1, 32));
        assertEquals((long) Integer.MIN_VALUE, executeRegisterOperation(RiscVOperation.SLLW, 1, 31));
        assertEquals(1, executeRegisterOperation(RiscVOperation.SRLW, 0x8000_0000L, 31));
        assertEquals(-1L, executeRegisterOperation(RiscVOperation.SRAW, 0xffff_ffffL, 63));
    }

    /// Verifies Zba address-generation instructions.
    @Test
    public void zbaAddressGenerationOperationsUseExpectedOperands() {
        assertEquals(0x21, executeRegisterOperation(RiscVOperation.SH1ADD, 0x10, 1));
        assertEquals(0x41, executeRegisterOperation(RiscVOperation.SH2ADD, 0x10, 1));
        assertEquals(0x81, executeRegisterOperation(RiscVOperation.SH3ADD, 0x10, 1));
        assertEquals(0x1_0000_0001L, executeRegisterOperation(RiscVOperation.ADD_UW, 0xffff_ffffL, 2));
        assertEquals(0x1_ffff_ffffL, executeRegisterOperation(RiscVOperation.SH1ADD_UW, 0xffff_ffffL, 1));
        assertEquals(0xffff_ffff0L, executeImmediateOperation(RiscVOperation.SLLI_UW, 0xffff_ffffL, 0, 4));
    }

    /// Verifies Zbb logical, counting, min/max, sign-extension, byte, and rotation instructions.
    @Test
    public void zbbOperationsFollowBitManipulationRules() {
        assertEquals(0x30L, executeRegisterOperation(RiscVOperation.ANDN, 0x3f, 0x0f));
        assertEquals(~0x0cL, executeRegisterOperation(RiscVOperation.ORN, 0x03, 0x0c));
        assertEquals(~0xffL, executeRegisterOperation(RiscVOperation.XNOR, 0xf0, 0x0f));
        assertEquals(63, executeImmediateOperation(RiscVOperation.CLZ, 1, 0, 0));
        assertEquals(64, executeImmediateOperation(RiscVOperation.CLZ, 0, 0, 0));
        assertEquals(4, executeImmediateOperation(RiscVOperation.CTZ, 0x10, 0, 0));
        assertEquals(64, executeImmediateOperation(RiscVOperation.CTZ, 0, 0, 0));
        assertEquals(32, executeImmediateOperation(RiscVOperation.CPOP, 0xffff_ffffL, 0, 0));
        assertEquals(0, executeImmediateOperation(RiscVOperation.CPOP, 0, 0, 0));
        assertEquals(31, executeImmediateOperation(RiscVOperation.CLZW, 1, 0, 0));
        assertEquals(32, executeImmediateOperation(RiscVOperation.CLZW, 0, 0, 0));
        assertEquals(4, executeImmediateOperation(RiscVOperation.CTZW, 0x10, 0, 0));
        assertEquals(32, executeImmediateOperation(RiscVOperation.CTZW, 0, 0, 0));
        assertEquals(32, executeImmediateOperation(RiscVOperation.CPOPW, 0xffff_ffffL, 0, 0));
        assertEquals(0, executeImmediateOperation(RiscVOperation.CPOPW, 0, 0, 0));
        assertEquals(2, executeRegisterOperation(RiscVOperation.MAX, -1, 2));
        assertEquals(Long.MAX_VALUE, executeRegisterOperation(RiscVOperation.MAX, Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(-1L, executeRegisterOperation(RiscVOperation.MAXU, -1, 2));
        assertEquals(Long.MIN_VALUE, executeRegisterOperation(RiscVOperation.MAXU, Long.MAX_VALUE, Long.MIN_VALUE));
        assertEquals(-1L, executeRegisterOperation(RiscVOperation.MIN, -1, 2));
        assertEquals(Long.MIN_VALUE, executeRegisterOperation(RiscVOperation.MIN, Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(2, executeRegisterOperation(RiscVOperation.MINU, -1, 2));
        assertEquals(Long.MAX_VALUE, executeRegisterOperation(RiscVOperation.MINU, Long.MAX_VALUE, Long.MIN_VALUE));
        assertEquals(-128L, executeImmediateOperation(RiscVOperation.SEXT_B, 0x80, 0, 0));
        assertEquals(-32768L, executeImmediateOperation(RiscVOperation.SEXT_H, 0x8000, 0, 0));
        assertEquals(0xabcdL, executeImmediateOperation(RiscVOperation.ZEXT_H, 0xffff_ffff_ffff_abcdL, 0, 0));
        assertEquals(0xff00_ff00_00ff_00ffL, executeImmediateOperation(RiscVOperation.ORC_B, 0x1200_0100_0040_0080L, 0, 0));
        assertEquals(0x8877_6655_4433_2211L, executeImmediateOperation(RiscVOperation.REV8, 0x1122_3344_5566_7788L, 0, 0));
        assertEquals(0x3456_789a_bcde_f012L, executeRegisterOperation(RiscVOperation.ROL, 0x1234_5678_9abc_def0L, 8));
        assertEquals(0xdef0_1234_5678_9abcL, executeImmediateOperation(RiscVOperation.RORI, 0x1234_5678_9abc_def0L, 0, 16));
        assertEquals(0x0000_0000_3456_7812L, executeRegisterOperation(RiscVOperation.ROLW, 0x1234_5678L, 8));
        assertEquals(0x0000_0000_5678_1234L, executeImmediateOperation(RiscVOperation.RORIW, 0x1234_5678L, 0, 16));
    }

    /// Verifies Zbs single-bit instructions use the architectural six-bit index mask.
    @Test
    public void zbsOperationsMaskBitIndexes() {
        assertEquals(0, executeRegisterOperation(RiscVOperation.BCLR, 1, 64));
        assertEquals(1, executeRegisterOperation(RiscVOperation.BEXT, 1, 64));
        assertEquals(0, executeRegisterOperation(RiscVOperation.BINV, 1, 64));
        assertEquals(Long.MIN_VALUE, executeImmediateOperation(RiscVOperation.BSETI, 0, 0, 63));
    }

    /// Verifies `cbo.zero` clears the containing 64-byte block and preserves adjacent bytes.
    @Test
    public void cacheBlockZeroClearsContainingCacheBlock() {
        try (TestMachine machine = TestMachine.create()) {
            long base = TEST_PC + 256;
            for (int index = 0; index < 128; index++) {
                machine.memory().writeByte(base + index, (byte) 0x7f);
            }
            machine.state().setRegister(LEFT_REGISTER, base + 71);

            RiscVInstructionSemantics instruction = RiscVInstructionSemantics.create(
                    TEST_PC,
                    0,
                    Integer.BYTES,
                    RiscVOperation.CBO_ZERO,
                    0,
                    LEFT_REGISTER,
                    0,
                    0,
                    false);
            instruction.execute(machine.state());

            for (int index = 0; index < 64; index++) {
                assertEquals(0, machine.memory().readUnsignedByte(base + 64 + index));
            }
            assertEquals(0x7f, machine.memory().readUnsignedByte(base + 63));
        }
    }

    /// Executes one register-register instruction and returns its destination register value.
    private static long executeRegisterOperation(RiscVOperation operation, long leftValue, long rightValue) {
        return executeOperation(operation, leftValue, rightValue, 0);
    }

    /// Executes one register-immediate instruction and returns its destination register value.
    private static long executeImmediateOperation(RiscVOperation operation, long leftValue, long rightValue, long immediate) {
        return executeOperation(operation, leftValue, rightValue, immediate);
    }

    /// Executes one standalone instruction and returns its destination register value.
    private static long executeOperation(RiscVOperation operation, long leftValue, long rightValue, long immediate) {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 4096, null)) {
            GuestSyscalls syscalls = new GuestSyscalls(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    memory.baseAddress());
            try {
                MachineState state = new MachineState(
                        memory,
                        0,
                        false,
                        ElfImage.ABSENT_ADDRESS,
                        ElfImage.ABSENT_ADDRESS,
                        syscalls);
                state.setPc(TEST_PC);
                state.setRegister(LEFT_REGISTER, leftValue);
                state.setRegister(RIGHT_REGISTER, rightValue);

                RiscVInstructionSemantics instruction = RiscVInstructionSemantics.create(
                        TEST_PC,
                        0,
                        Integer.BYTES,
                        operation,
                        RESULT_REGISTER,
                        LEFT_REGISTER,
                        RIGHT_REGISTER,
                        immediate,
                        false);
                instruction.execute(state);

                assertEquals(TEST_PC + Integer.BYTES, state.pc());
                return state.register(RESULT_REGISTER);
            } finally {
                syscalls.close();
            }
        }
    }

    /// Owns a standalone instruction-test machine and its closeable resources.
    ///
    /// @param memory the guest memory under test
    /// @param syscalls the syscall handler attached to the machine state
    /// @param state the mutable architectural state under test
    @NotNullByDefault
    private record TestMachine(
            Memory memory,
            GuestSyscalls syscalls,
            MachineState state) implements AutoCloseable {
        /// Creates a test machine initialized at the standalone instruction test address.
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
