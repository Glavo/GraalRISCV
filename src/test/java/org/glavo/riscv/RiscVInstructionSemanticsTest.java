package org.glavo.riscv;

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
    }

    /// Verifies that `mulw` keeps the low word and sign-extends it to RV64.
    @Test
    public void wordMultiplicationSignExtendsLowWord() {
        assertEquals((long) Integer.MIN_VALUE, executeRegisterOperation(RiscVOperation.MULW, 0x4000_0000L, 2));
    }

    /// Executes one register-register instruction and returns its destination register value.
    private static long executeRegisterOperation(RiscVOperation operation, long leftValue, long rightValue) {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 4096)) {
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
                        0,
                        false);
                instruction.execute(state);

                assertEquals(TEST_PC + Integer.BYTES, state.pc());
                return state.register(RESULT_REGISTER);
            } finally {
                syscalls.close();
            }
        }
    }
}
