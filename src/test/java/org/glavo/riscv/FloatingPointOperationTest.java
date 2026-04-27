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
                RiscVDecoder.decodeBlock(machine.memory(), machine.state().pc()).execute(machine.state());
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

    /// Encodes `fsub.d`.
    private static int fsubD(int rd, int rs1, int rs2) {
        return opFp(0x05, rd, 0, rs1, rs2);
    }

    /// Encodes `fmul.d`.
    private static int fmulD(int rd, int rs1, int rs2) {
        return opFp(0x09, rd, 0, rs1, rs2);
    }

    /// Encodes `fdiv.d`.
    private static int fdivD(int rd, int rs1, int rs2) {
        return opFp(0x0d, rd, 0, rs1, rs2);
    }

    /// Encodes `fsqrt.d`.
    private static int fsqrtD(int rd, int rs1) {
        return opFp(0x2d, rd, 0, rs1, 0);
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

    /// Encodes `fcvt.s.w`.
    private static int fcvtSW(int rd, int rs1) {
        return opFp(0x68, rd, 0, rs1, 0);
    }

    /// Encodes `fcvt.l.d`.
    private static int fcvtLD(int rd, int rs1) {
        return opFp(0x61, rd, 0, rs1, 2);
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

    /// Encodes `fmadd.d`.
    private static int fmaddD(int rd, int rs1, int rs2, int rs3) {
        return fma(0x43, rd, rs1, rs2, rs3, 1);
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

    /// Encodes an OP-FP instruction.
    private static int opFp(int funct7, int rd, int funct3, int rs1, int rs2) {
        return (funct7 << 25) | (rs2 << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | 0x53;
    }

    /// Encodes a fused floating-point instruction.
    private static int fma(int opcode, int rd, int rs1, int rs2, int rs3, int format) {
        return (rs3 << 27) | (format << 25) | (rs2 << 20) | (rs1 << 15) | (rd << 7) | opcode;
    }

    /// Owns a floating-point operation test machine and its closeable resources.
    @NotNullByDefault
    private record TestMachine(
            /// The guest memory under test.
            Memory memory,

            /// The syscall handler attached to the machine state.
            GuestSyscalls syscalls,

            /// The mutable architectural state under test.
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
