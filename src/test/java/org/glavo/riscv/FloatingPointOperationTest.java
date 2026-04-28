package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/// Tests decoded floating-point arithmetic, conversion, comparison, classify, and move operations.
@NotNullByDefault
public final class FloatingPointOperationTest {
    /// The guest address used for decoded floating-point operation tests.
    private static final long TEST_PC = Memory.DEFAULT_BASE_ADDRESS;

    /// The first floating-point source register used by operation tests.
    private static final int LEFT_FLOAT_REGISTER = 1;

    /// The second floating-point source register used by operation tests.
    private static final int RIGHT_FLOAT_REGISTER = 2;

    /// The third floating-point source register used by fused operation tests.
    private static final int THIRD_FLOAT_REGISTER = 3;

    /// The first integer result register used by operation tests.
    private static final int RESULT_REGISTER = 10;

    /// The second integer result register used by operation tests.
    private static final int SECOND_RESULT_REGISTER = 11;

    /// The third integer result register used by operation tests.
    private static final int THIRD_RESULT_REGISTER = 12;

    /// The fourth integer result register used by operation tests.
    private static final int FOURTH_RESULT_REGISTER = 13;

    /// The fifth integer result register used by operation tests.
    private static final int FIFTH_RESULT_REGISTER = 14;

    /// The first integer source register used by conversion tests.
    private static final int LEFT_INTEGER_REGISTER = 5;

    /// The second integer source register used by conversion tests.
    private static final int RIGHT_INTEGER_REGISTER = 6;

    /// The `fflags` CSR address.
    private static final int FFLAGS_CSR = 0x001;

    /// The `frm` CSR address.
    private static final int FRM_CSR = 0x002;

    /// The canonical double-precision quiet NaN bit pattern.
    private static final long CANONICAL_DOUBLE_NAN = 0x7ff8_0000_0000_0000L;

    /// The canonical single-precision quiet NaN bit pattern.
    private static final int CANONICAL_SINGLE_NAN = 0x7fc0_0000;

    /// The Linux syscall register.
    private static final int SYSCALL_REGISTER = 17;

    /// The Linux `exit` syscall number.
    private static final long EXIT_SYSCALL = 93;

    /// Verifies common double-precision arithmetic, comparison, classify, and move operations.
    @Test
    public void doubleArithmeticCompareClassifyAndMoveExecute() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    faddD(10, LEFT_FLOAT_REGISTER, RIGHT_FLOAT_REGISTER),
                    fsubD(11, 10, LEFT_FLOAT_REGISTER),
                    fmulD(12, LEFT_FLOAT_REGISTER, RIGHT_FLOAT_REGISTER),
                    fdivD(13, 12, RIGHT_FLOAT_REGISTER),
                    fsqrtD(14, 12),
                    feqD(RESULT_REGISTER, 11, RIGHT_FLOAT_REGISTER),
                    fltD(SECOND_RESULT_REGISTER, LEFT_FLOAT_REGISTER, RIGHT_FLOAT_REGISTER),
                    fleD(THIRD_RESULT_REGISTER, LEFT_FLOAT_REGISTER, LEFT_FLOAT_REGISTER),
                    fclassD(FOURTH_RESULT_REGISTER, LEFT_FLOAT_REGISTER),
                    fmvXD(FIFTH_RESULT_REGISTER, 10),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            writeDouble(machine.state(), LEFT_FLOAT_REGISTER, 1.5d);
            writeDouble(machine.state(), RIGHT_FLOAT_REGISTER, 2.5d);

            runDecodedProgram(machine);

            assertEquals(Double.doubleToRawLongBits(4.0d), machine.state().floatingPointRegister(10));
            assertEquals(Double.doubleToRawLongBits(2.5d), machine.state().floatingPointRegister(11));
            assertEquals(Double.doubleToRawLongBits(3.75d), machine.state().floatingPointRegister(12));
            assertEquals(Double.doubleToRawLongBits(1.5d), machine.state().floatingPointRegister(13));
            assertEquals(Math.sqrt(3.75d), Double.longBitsToDouble(machine.state().floatingPointRegister(14)), 0.0d);
            assertEquals(1, machine.state().register(RESULT_REGISTER));
            assertEquals(1, machine.state().register(SECOND_RESULT_REGISTER));
            assertEquals(1, machine.state().register(THIRD_RESULT_REGISTER));
            assertEquals(1 << 6, machine.state().register(FOURTH_RESULT_REGISTER));
            assertEquals(Double.doubleToRawLongBits(4.0d), machine.state().register(FIFTH_RESULT_REGISTER));
        }
    }

    /// Verifies fused double-precision multiply-add operation variants.
    @Test
    public void fusedDoubleMultiplyAddVariantsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    fmaddD(10, LEFT_FLOAT_REGISTER, RIGHT_FLOAT_REGISTER, THIRD_FLOAT_REGISTER),
                    fmsubD(11, LEFT_FLOAT_REGISTER, RIGHT_FLOAT_REGISTER, THIRD_FLOAT_REGISTER),
                    fnmsubD(12, LEFT_FLOAT_REGISTER, RIGHT_FLOAT_REGISTER, THIRD_FLOAT_REGISTER),
                    fnmaddD(13, LEFT_FLOAT_REGISTER, RIGHT_FLOAT_REGISTER, THIRD_FLOAT_REGISTER),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            writeDouble(machine.state(), LEFT_FLOAT_REGISTER, 2.0d);
            writeDouble(machine.state(), RIGHT_FLOAT_REGISTER, 3.0d);
            writeDouble(machine.state(), THIRD_FLOAT_REGISTER, 4.0d);

            runDecodedProgram(machine);

            assertEquals(Double.doubleToRawLongBits(10.0d), machine.state().floatingPointRegister(10));
            assertEquals(Double.doubleToRawLongBits(2.0d), machine.state().floatingPointRegister(11));
            assertEquals(Double.doubleToRawLongBits(-2.0d), machine.state().floatingPointRegister(12));
            assertEquals(Double.doubleToRawLongBits(-10.0d), machine.state().floatingPointRegister(13));
        }
    }

    /// Verifies FMA invalid-operation flags distinguish exact infinite products from rounded overflow.
    @Test
    public void fusedMultiplyAddInvalidFlagUsesExactProduct() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    fmaddS(4, 1, 2, 3, 0),
                    fmaddD(8, 5, 6, 7, 0),
                    csrrs(RESULT_REGISTER, FFLAGS_CSR, 0),
                    fmaddS(9, 10, 11, 12, 0),
                    fmaddD(13, 14, 15, 16, 0),
                    csrrs(SECOND_RESULT_REGISTER, FFLAGS_CSR, 0),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            writeSingleBits(machine.state(), 1, Float.floatToRawIntBits(Float.MAX_VALUE));
            writeSingleBits(machine.state(), 2, Float.floatToRawIntBits(2.0f));
            writeSingleBits(machine.state(), 3, Float.floatToRawIntBits(Float.NEGATIVE_INFINITY));
            writeDouble(machine.state(), 5, Double.MAX_VALUE);
            writeDouble(machine.state(), 6, 2.0d);
            writeDouble(machine.state(), 7, Double.NEGATIVE_INFINITY);
            writeSingleBits(machine.state(), 10, Float.floatToRawIntBits(Float.POSITIVE_INFINITY));
            writeSingleBits(machine.state(), 11, Float.floatToRawIntBits(2.0f));
            writeSingleBits(machine.state(), 12, Float.floatToRawIntBits(Float.NEGATIVE_INFINITY));
            writeDouble(machine.state(), 14, Double.POSITIVE_INFINITY);
            writeDouble(machine.state(), 15, 2.0d);
            writeDouble(machine.state(), 16, Double.NEGATIVE_INFINITY);

            runDecodedProgram(machine);

            assertEquals(boxedSingle(Float.floatToRawIntBits(Float.NEGATIVE_INFINITY)), machine.state().floatingPointRegister(4));
            assertEquals(Double.doubleToRawLongBits(Double.NEGATIVE_INFINITY), machine.state().floatingPointRegister(8));
            assertEquals(0, machine.state().register(RESULT_REGISTER));
            assertEquals(boxedSingle(CANONICAL_SINGLE_NAN), machine.state().floatingPointRegister(9));
            assertEquals(CANONICAL_DOUBLE_NAN, machine.state().floatingPointRegister(13));
            assertEquals(0x10, machine.state().register(SECOND_RESULT_REGISTER));
        }
    }

    /// Verifies double-precision directed rounding and exact arithmetic exception flags.
    @Test
    public void doubleArithmeticRoundingModesAndExceptionFlagsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    faddD(3, 1, 2, 2),
                    faddD(4, 1, 2, 3),
                    faddD(5, 6, 7, 4),
                    fmulD(8, 9, 10, 2),
                    fmulD(15, 16, 17, 0),
                    fmaddD(18, 1, 1, 2, 3),
                    fdivD(19, 9, 17, 2),
                    fdivD(20, 1, 21, 3),
                    fsqrtD(22, 23, 3),
                    csrrs(RESULT_REGISTER, FFLAGS_CSR, 0),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            writeDouble(machine.state(), 1, 1.0d);
            writeDouble(machine.state(), 2, 0x1.0p-53d);
            writeDouble(machine.state(), 6, -1.0d);
            writeDouble(machine.state(), 7, -0x1.0p-53d);
            writeDouble(machine.state(), 9, Double.MAX_VALUE);
            writeDouble(machine.state(), 10, 2.0d);
            writeDouble(machine.state(), 16, Double.MIN_VALUE);
            writeDouble(machine.state(), 17, 0.5d);
            writeDouble(machine.state(), 21, 3.0d);
            writeDouble(machine.state(), 23, Math.nextUp(1.0d));

            runDecodedProgram(machine);

            assertEquals(Double.doubleToRawLongBits(1.0d), machine.state().floatingPointRegister(3));
            assertEquals(Double.doubleToRawLongBits(Math.nextUp(1.0d)), machine.state().floatingPointRegister(4));
            assertEquals(Double.doubleToRawLongBits(Math.nextDown(-1.0d)), machine.state().floatingPointRegister(5));
            assertEquals(Double.doubleToRawLongBits(Double.MAX_VALUE), machine.state().floatingPointRegister(8));
            assertEquals(Double.doubleToRawLongBits(0.0d), machine.state().floatingPointRegister(15));
            assertEquals(Double.doubleToRawLongBits(Math.nextUp(1.0d)), machine.state().floatingPointRegister(18));
            assertEquals(Double.doubleToRawLongBits(Double.MAX_VALUE), machine.state().floatingPointRegister(19));
            assertEquals(Double.doubleToRawLongBits(Math.nextUp(1.0d / 3.0d)), machine.state().floatingPointRegister(20));
            assertEquals(Double.doubleToRawLongBits(Math.nextUp(1.0d)), machine.state().floatingPointRegister(22));
            assertEquals(0x07, machine.state().register(RESULT_REGISTER));
        }
    }

    /// Verifies single-precision sign injection and signed zero min/max behavior.
    @Test
    public void singleSignInjectionAndMinimumMaximumUseRawBits() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    fminS(3, LEFT_FLOAT_REGISTER, RIGHT_FLOAT_REGISTER),
                    fmaxS(4, LEFT_FLOAT_REGISTER, RIGHT_FLOAT_REGISTER),
                    fsgnjxS(5, RIGHT_FLOAT_REGISTER, LEFT_FLOAT_REGISTER),
                    fmvXW(RESULT_REGISTER, 5),
                    fclassS(SECOND_RESULT_REGISTER, 5),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            writeSingleBits(machine.state(), LEFT_FLOAT_REGISTER, 0x8000_0000);
            writeSingleBits(machine.state(), RIGHT_FLOAT_REGISTER, 0);

            runDecodedProgram(machine);

            assertEquals(boxedSingle(0x8000_0000), machine.state().floatingPointRegister(3));
            assertEquals(boxedSingle(0), machine.state().floatingPointRegister(4));
            assertEquals(boxedSingle(0x8000_0000), machine.state().floatingPointRegister(5));
            assertEquals(0xffff_ffff_8000_0000L, machine.state().register(RESULT_REGISTER));
            assertEquals(1 << 3, machine.state().register(SECOND_RESULT_REGISTER));
        }
    }

    /// Verifies integer/floating-point conversion instructions in both directions.
    @Test
    public void integerAndFloatingPointConversionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    fcvtDL(1, LEFT_INTEGER_REGISTER),
                    fcvtSW(2, RIGHT_INTEGER_REGISTER),
                    fcvtLD(RESULT_REGISTER, 1),
                    fcvtWS(SECOND_RESULT_REGISTER, 2),
                    fcvtDS(3, 2),
                    fcvtSD(4, 1),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(LEFT_INTEGER_REGISTER, 42);
            machine.state().setRegister(RIGHT_INTEGER_REGISTER, -3);

            runDecodedProgram(machine);

            assertEquals(Double.doubleToRawLongBits(42.0d), machine.state().floatingPointRegister(1));
            assertEquals(boxedSingle(Float.floatToRawIntBits(-3.0f)), machine.state().floatingPointRegister(2));
            assertEquals(42, machine.state().register(RESULT_REGISTER));
            assertEquals(-3, machine.state().register(SECOND_RESULT_REGISTER));
            assertEquals(Double.doubleToRawLongBits(-3.0d), machine.state().floatingPointRegister(3));
            assertEquals(boxedSingle(Float.floatToRawIntBits(42.0f)), machine.state().floatingPointRegister(4));
        }
    }

    /// Verifies integer to floating-point conversions honor explicit rounding modes.
    @Test
    public void integerToFloatingPointConversionRoundingModesAndFlagsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    fcvtDL(1, LEFT_INTEGER_REGISTER, 2),
                    fcvtDL(2, LEFT_INTEGER_REGISTER, 3),
                    fcvtSL(3, RIGHT_INTEGER_REGISTER, 3),
                    csrrs(RESULT_REGISTER, FFLAGS_CSR, 0),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(LEFT_INTEGER_REGISTER, Long.MAX_VALUE);
            machine.state().setRegister(RIGHT_INTEGER_REGISTER, 0x0100_0001L);

            runDecodedProgram(machine);

            assertEquals(Double.doubleToRawLongBits(Math.nextDown(0x1.0p63d)), machine.state().floatingPointRegister(1));
            assertEquals(Double.doubleToRawLongBits(0x1.0p63d), machine.state().floatingPointRegister(2));
            assertEquals(boxedSingle(Float.floatToRawIntBits(Math.nextUp(0x1.0p24f))), machine.state().floatingPointRegister(3));
            assertEquals(0x01, machine.state().register(RESULT_REGISTER));
        }
    }

    /// Verifies that obvious floating-point exception flags are accumulated in `fflags`.
    @Test
    public void divideByZeroSetsFloatingPointFlag() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    fdivD(10, LEFT_FLOAT_REGISTER, RIGHT_FLOAT_REGISTER),
                    csrrs(RESULT_REGISTER, FFLAGS_CSR, 0),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            writeDouble(machine.state(), LEFT_FLOAT_REGISTER, 1.0d);
            writeDouble(machine.state(), RIGHT_FLOAT_REGISTER, 0.0d);

            runDecodedProgram(machine);

            assertEquals(0x08, machine.state().register(RESULT_REGISTER));
        }
    }

    /// Verifies canonical NaN results and invalid flags for indeterminate floating-point operations.
    @Test
    public void invalidFloatingPointOperationsSetFlagAndCanonicalNaN() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    fmulD(10, 1, 2),
                    fdivD(11, 3, 4),
                    fsqrtD(12, 5),
                    faddD(13, 6, 7),
                    csrrs(RESULT_REGISTER, FFLAGS_CSR, 0),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            writeDouble(machine.state(), 1, Double.POSITIVE_INFINITY);
            writeDouble(machine.state(), 2, 0.0d);
            writeDouble(machine.state(), 3, 0.0d);
            writeDouble(machine.state(), 4, 0.0d);
            writeDouble(machine.state(), 5, -1.0d);
            writeDoubleBits(machine.state(), 6, 0x7ff0_0000_0000_0001L);
            writeDouble(machine.state(), 7, 1.0d);

            runDecodedProgram(machine);

            assertEquals(CANONICAL_DOUBLE_NAN, machine.state().floatingPointRegister(10));
            assertEquals(CANONICAL_DOUBLE_NAN, machine.state().floatingPointRegister(11));
            assertEquals(CANONICAL_DOUBLE_NAN, machine.state().floatingPointRegister(12));
            assertEquals(CANONICAL_DOUBLE_NAN, machine.state().floatingPointRegister(13));
            assertEquals(0x10, machine.state().register(RESULT_REGISTER));
        }
    }

    /// Verifies floating-point to integer conversion boundaries and inexact flag accumulation.
    @Test
    public void floatingPointToIntegerConversionSetsInvalidAndInexactFlags() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    fcvtWD(RESULT_REGISTER, 1),
                    fcvtWUD(SECOND_RESULT_REGISTER, 2),
                    fcvtLD(THIRD_RESULT_REGISTER, 3),
                    csrrs(FOURTH_RESULT_REGISTER, FFLAGS_CSR, 0),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            writeDouble(machine.state(), 1, 1.5d);
            writeDouble(machine.state(), 2, -1.0d);
            writeDouble(machine.state(), 3, 0x1.0p63);

            runDecodedProgram(machine);

            assertEquals(2, machine.state().register(RESULT_REGISTER));
            assertEquals(0, machine.state().register(SECOND_RESULT_REGISTER));
            assertEquals(Long.MAX_VALUE, machine.state().register(THIRD_RESULT_REGISTER));
            assertEquals(0x11, machine.state().register(FOURTH_RESULT_REGISTER));
        }
    }

    /// Verifies that dynamic rounding uses the current `frm` value for conversion instructions.
    @Test
    public void dynamicRoundingModeControlsFloatingPointToIntegerConversion() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    csrrwi(0, FRM_CSR, 1),
                    fcvtWDynamic(RESULT_REGISTER, 1),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            writeDouble(machine.state(), 1, 1.9d);

            runDecodedProgram(machine);

            assertEquals(1, machine.state().register(RESULT_REGISTER));
        }
    }

    /// Verifies single-precision directed rounding and arithmetic exception flags.
    @Test
    public void singleArithmeticRoundingModesAndExceptionFlagsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    faddS(3, 1, 2, 2),
                    faddS(4, 1, 2, 3),
                    fmulS(5, 6, 7, 0),
                    fmulS(8, 9, 10, 0),
                    fcvtSD(11, 12, 2),
                    fcvtSD(13, 12, 3),
                    faddS(14, 1, 15, 4),
                    fsqrtS(16, 17, 3),
                    fdivS(18, 1, 19, 2),
                    fmaddS(20, 1, 1, 15, 4),
                    csrrs(RESULT_REGISTER, FFLAGS_CSR, 0),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            writeSingleBits(machine.state(), 1, Float.floatToRawIntBits(1.0f));
            writeSingleBits(machine.state(), 2, Float.floatToRawIntBits(0x1.0p-25f));
            writeSingleBits(machine.state(), 6, Float.floatToRawIntBits(Float.MAX_VALUE));
            writeSingleBits(machine.state(), 7, Float.floatToRawIntBits(2.0f));
            writeSingleBits(machine.state(), 9, 1);
            writeSingleBits(machine.state(), 10, Float.floatToRawIntBits(0.5f));
            writeSingleBits(machine.state(), 15, Float.floatToRawIntBits(0x1.0p-24f));
            writeSingleBits(machine.state(), 17, Float.floatToRawIntBits(Math.nextUp(1.0f)));
            writeSingleBits(machine.state(), 19, Float.floatToRawIntBits(3.0f));
            writeDouble(machine.state(), 12, 1.0d + 0x1.0p-25d);

            runDecodedProgram(machine);

            assertEquals(boxedSingle(Float.floatToRawIntBits(1.0f)), machine.state().floatingPointRegister(3));
            assertEquals(boxedSingle(Float.floatToRawIntBits(Math.nextUp(1.0f))), machine.state().floatingPointRegister(4));
            assertEquals(boxedSingle(Float.floatToRawIntBits(Float.POSITIVE_INFINITY)), machine.state().floatingPointRegister(5));
            assertEquals(boxedSingle(0), machine.state().floatingPointRegister(8));
            assertEquals(boxedSingle(Float.floatToRawIntBits(1.0f)), machine.state().floatingPointRegister(11));
            assertEquals(boxedSingle(Float.floatToRawIntBits(Math.nextUp(1.0f))), machine.state().floatingPointRegister(13));
            assertEquals(boxedSingle(Float.floatToRawIntBits(Math.nextUp(1.0f))), machine.state().floatingPointRegister(14));
            assertEquals(boxedSingle(Float.floatToRawIntBits(Math.nextUp(1.0f))), machine.state().floatingPointRegister(16));
            assertEquals(boxedSingle(Float.floatToRawIntBits(Math.nextDown(1.0f / 3.0f))), machine.state().floatingPointRegister(18));
            assertEquals(boxedSingle(Float.floatToRawIntBits(Math.nextUp(1.0f))), machine.state().floatingPointRegister(20));
            assertEquals(0x07, machine.state().register(RESULT_REGISTER));
        }
    }

    /// Verifies that unboxed single-precision registers are consumed as canonical NaN values.
    @Test
    public void unboxedSinglePrecisionInputBehavesAsCanonicalNaN() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    fclassS(RESULT_REGISTER, LEFT_FLOAT_REGISTER),
                    faddS(3, LEFT_FLOAT_REGISTER, RIGHT_FLOAT_REGISTER, 0),
                    csrrs(SECOND_RESULT_REGISTER, FFLAGS_CSR, 0),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setFloatingPointRegister(LEFT_FLOAT_REGISTER, Float.floatToRawIntBits(1.0f));
            writeSingleBits(machine.state(), RIGHT_FLOAT_REGISTER, Float.floatToRawIntBits(2.0f));

            runDecodedProgram(machine);

            assertEquals(1 << 9, machine.state().register(RESULT_REGISTER));
            assertEquals(boxedSingle(0x7fc0_0000), machine.state().floatingPointRegister(3));
            assertEquals(0, machine.state().register(SECOND_RESULT_REGISTER));
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
        memory.load(TEST_PC, code.array(), 0, code.array().length);
    }

    /// Writes a double-precision value to a floating-point register.
    private static void writeDouble(MachineState state, int register, double value) {
        state.setFloatingPointRegister(register, Double.doubleToRawLongBits(value));
    }

    /// Writes raw double-precision bits to a floating-point register.
    private static void writeDoubleBits(MachineState state, int register, long bits) {
        state.setFloatingPointRegister(register, bits);
    }

    /// Writes raw single-precision bits to a NaN-boxed floating-point register.
    private static void writeSingleBits(MachineState state, int register, int bits) {
        state.setFloatingPointRegister(register, boxedSingle(bits));
    }

    /// Returns an RV64 NaN-boxed single-precision bit pattern.
    private static long boxedSingle(int bits) {
        return 0xffff_ffff_0000_0000L | (bits & 0xffff_ffffL);
    }

    /// Encodes `fadd.d`.
    private static int faddD(int rd, int rs1, int rs2) {
        return opFp(0x01, rd, 0, rs1, rs2);
    }

    /// Encodes `fadd.d` with an explicit rounding mode.
    private static int faddD(int rd, int rs1, int rs2, int roundingMode) {
        return opFp(0x01, rd, roundingMode, rs1, rs2);
    }

    /// Encodes `fadd.s`.
    private static int faddS(int rd, int rs1, int rs2, int roundingMode) {
        return opFp(0x00, rd, roundingMode, rs1, rs2);
    }

    /// Encodes `fsub.d`.
    private static int fsubD(int rd, int rs1, int rs2) {
        return opFp(0x05, rd, 0, rs1, rs2);
    }

    /// Encodes `fmul.d`.
    private static int fmulD(int rd, int rs1, int rs2) {
        return opFp(0x09, rd, 0, rs1, rs2);
    }

    /// Encodes `fmul.d` with an explicit rounding mode.
    private static int fmulD(int rd, int rs1, int rs2, int roundingMode) {
        return opFp(0x09, rd, roundingMode, rs1, rs2);
    }

    /// Encodes `fmul.s`.
    private static int fmulS(int rd, int rs1, int rs2, int roundingMode) {
        return opFp(0x08, rd, roundingMode, rs1, rs2);
    }

    /// Encodes `fdiv.s` with an explicit rounding mode.
    private static int fdivS(int rd, int rs1, int rs2, int roundingMode) {
        return opFp(0x0c, rd, roundingMode, rs1, rs2);
    }

    /// Encodes `fdiv.d`.
    private static int fdivD(int rd, int rs1, int rs2) {
        return opFp(0x0d, rd, 0, rs1, rs2);
    }

    /// Encodes `fdiv.d` with an explicit rounding mode.
    private static int fdivD(int rd, int rs1, int rs2, int roundingMode) {
        return opFp(0x0d, rd, roundingMode, rs1, rs2);
    }

    /// Encodes `fsqrt.d`.
    private static int fsqrtD(int rd, int rs1) {
        return opFp(0x2d, rd, 0, rs1, 0);
    }

    /// Encodes `fsqrt.d` with an explicit rounding mode.
    private static int fsqrtD(int rd, int rs1, int roundingMode) {
        return opFp(0x2d, rd, roundingMode, rs1, 0);
    }

    /// Encodes `fsqrt.s` with an explicit rounding mode.
    private static int fsqrtS(int rd, int rs1, int roundingMode) {
        return opFp(0x2c, rd, roundingMode, rs1, 0);
    }

    /// Encodes `fmin.s`.
    private static int fminS(int rd, int rs1, int rs2) {
        return opFp(0x14, rd, 0, rs1, rs2);
    }

    /// Encodes `fmax.s`.
    private static int fmaxS(int rd, int rs1, int rs2) {
        return opFp(0x14, rd, 1, rs1, rs2);
    }

    /// Encodes `fsgnjx.s`.
    private static int fsgnjxS(int rd, int rs1, int rs2) {
        return opFp(0x10, rd, 2, rs1, rs2);
    }

    /// Encodes `feq.d`.
    private static int feqD(int rd, int rs1, int rs2) {
        return opFp(0x51, rd, 2, rs1, rs2);
    }

    /// Encodes `flt.d`.
    private static int fltD(int rd, int rs1, int rs2) {
        return opFp(0x51, rd, 1, rs1, rs2);
    }

    /// Encodes `fle.d`.
    private static int fleD(int rd, int rs1, int rs2) {
        return opFp(0x51, rd, 0, rs1, rs2);
    }

    /// Encodes `fclass.s`.
    private static int fclassS(int rd, int rs1) {
        return opFp(0x70, rd, 1, rs1, 0);
    }

    /// Encodes `fclass.d`.
    private static int fclassD(int rd, int rs1) {
        return opFp(0x71, rd, 1, rs1, 0);
    }

    /// Encodes `fmv.x.w`.
    private static int fmvXW(int rd, int rs1) {
        return opFp(0x70, rd, 0, rs1, 0);
    }

    /// Encodes `fmv.x.d`.
    private static int fmvXD(int rd, int rs1) {
        return opFp(0x71, rd, 0, rs1, 0);
    }

    /// Encodes `fcvt.d.l`.
    private static int fcvtDL(int rd, int rs1) {
        return opFp(0x69, rd, 0, rs1, 2);
    }

    /// Encodes `fcvt.d.l` with an explicit rounding mode.
    private static int fcvtDL(int rd, int rs1, int roundingMode) {
        return opFp(0x69, rd, roundingMode, rs1, 2);
    }

    /// Encodes `fcvt.s.l` with an explicit rounding mode.
    private static int fcvtSL(int rd, int rs1, int roundingMode) {
        return opFp(0x68, rd, roundingMode, rs1, 2);
    }

    /// Encodes `fcvt.s.w`.
    private static int fcvtSW(int rd, int rs1) {
        return opFp(0x68, rd, 0, rs1, 0);
    }

    /// Encodes `fcvt.l.d`.
    private static int fcvtLD(int rd, int rs1) {
        return opFp(0x61, rd, 0, rs1, 2);
    }

    /// Encodes `fcvt.w.d`.
    private static int fcvtWD(int rd, int rs1) {
        return opFp(0x61, rd, 0, rs1, 0);
    }

    /// Encodes `fcvt.w.d` with dynamic rounding mode.
    private static int fcvtWDynamic(int rd, int rs1) {
        return opFp(0x61, rd, 7, rs1, 0);
    }

    /// Encodes `fcvt.wu.d`.
    private static int fcvtWUD(int rd, int rs1) {
        return opFp(0x61, rd, 0, rs1, 1);
    }

    /// Encodes `fcvt.w.s`.
    private static int fcvtWS(int rd, int rs1) {
        return opFp(0x60, rd, 0, rs1, 0);
    }

    /// Encodes `fcvt.d.s`.
    private static int fcvtDS(int rd, int rs1) {
        return opFp(0x21, rd, 0, rs1, 0);
    }

    /// Encodes `fcvt.s.d`.
    private static int fcvtSD(int rd, int rs1) {
        return opFp(0x20, rd, 0, rs1, 1);
    }

    /// Encodes `fcvt.s.d` with an explicit rounding mode.
    private static int fcvtSD(int rd, int rs1, int roundingMode) {
        return opFp(0x20, rd, roundingMode, rs1, 1);
    }

    /// Encodes `fmadd.d`.
    private static int fmaddD(int rd, int rs1, int rs2, int rs3) {
        return fma(0x43, rd, rs1, rs2, rs3, 1);
    }

    /// Encodes `fmadd.s` with an explicit rounding mode.
    private static int fmaddS(int rd, int rs1, int rs2, int rs3, int roundingMode) {
        return fma(0x43, rd, rs1, rs2, rs3, 0, roundingMode);
    }

    /// Encodes `fmadd.d` with an explicit rounding mode.
    private static int fmaddD(int rd, int rs1, int rs2, int rs3, int roundingMode) {
        return fma(0x43, rd, rs1, rs2, rs3, 1, roundingMode);
    }

    /// Encodes `fmsub.d`.
    private static int fmsubD(int rd, int rs1, int rs2, int rs3) {
        return fma(0x47, rd, rs1, rs2, rs3, 1);
    }

    /// Encodes `fnmsub.d`.
    private static int fnmsubD(int rd, int rs1, int rs2, int rs3) {
        return fma(0x4b, rd, rs1, rs2, rs3, 1);
    }

    /// Encodes `fnmadd.d`.
    private static int fnmaddD(int rd, int rs1, int rs2, int rs3) {
        return fma(0x4f, rd, rs1, rs2, rs3, 1);
    }

    /// Encodes a CSR read-set instruction.
    private static int csrrs(int rd, int csr, int rs1) {
        return (csr << 20) | (rs1 << 15) | (2 << 12) | (rd << 7) | 0x73;
    }

    /// Encodes a CSR immediate write instruction.
    private static int csrrwi(int rd, int csr, int immediate) {
        return (csr << 20) | (immediate << 15) | (5 << 12) | (rd << 7) | 0x73;
    }

    /// Encodes an OP-FP instruction.
    private static int opFp(int funct7, int rd, int funct3, int rs1, int rs2) {
        return (funct7 << 25) | (rs2 << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | 0x53;
    }

    /// Encodes a fused floating-point instruction.
    private static int fma(int opcode, int rd, int rs1, int rs2, int rs3, int format) {
        return fma(opcode, rd, rs1, rs2, rs3, format, 0);
    }

    /// Encodes a fused floating-point instruction with an explicit rounding mode.
    private static int fma(int opcode, int rd, int rs1, int rs2, int rs3, int format, int roundingMode) {
        return (rs3 << 27) | (format << 25) | (rs2 << 20) | (rs1 << 15) | (roundingMode << 12) | (rd << 7) | opcode;
    }

    /// Owns a floating-point operation test machine and its closeable resources.
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
