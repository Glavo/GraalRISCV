// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.nodes;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.parser.ElfImage;
import org.glavo.riscv.parser.RiscVDecoder;
import org.glavo.riscv.runtime.GuestSyscalls;
import org.glavo.riscv.runtime.LinuxGuestSyscalls;
import org.glavo.riscv.runtime.RiscVThreadState;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests floating-point instructions through the production micro-bytecode block executor.
@NotNullByDefault
public final class RiscVMicroBlockFloatingPointTest {
    /// The guest address used for micro-block execution tests.
    private static final long TEST_PC = Memory.DEFAULT_BASE_ADDRESS;

    /// The `fflags` CSR address.
    private static final int FFLAGS_CSR = 0x001;

    /// The `frm` CSR address.
    private static final int FRM_CSR = 0x002;

    /// The canonical single-precision quiet NaN bit pattern.
    private static final int CANONICAL_SINGLE_NAN = 0x7fc0_0000;

    /// The canonical double-precision quiet NaN bit pattern.
    private static final long CANONICAL_DOUBLE_NAN = 0x7ff8_0000_0000_0000L;

    /// Executes dedicated fixed-rounding floating-point micro-opcodes in batched mode.
    @Test
    public void fixedRoundingFloatingPointOpcodesExecuteInBatchedMode() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    faddD(3, 1, 2),
                    fsqrtD(4, 3),
                    fcvtSD(5, 3),
                    fcvtDS(6, 5),
                    fcvtLD(7, 3),
                    fcvtDL(8, 7),
                    jal(0));
            machine.state().setFloatingPointRegister(1, Double.doubleToRawLongBits(1.5d));
            machine.state().setFloatingPointRegister(2, Double.doubleToRawLongBits(2.5d));

            executeMicroBlock(machine);

            assertEquals(Double.doubleToRawLongBits(4.0d), machine.state().floatingPointRegister(3));
            assertEquals(Double.doubleToRawLongBits(2.0d), machine.state().floatingPointRegister(4));
            assertEquals(boxedSingle(Float.floatToRawIntBits(4.0f)), machine.state().floatingPointRegister(5));
            assertEquals(Double.doubleToRawLongBits(4.0d), machine.state().floatingPointRegister(6));
            assertEquals(4, machine.state().register(7));
            assertEquals(Double.doubleToRawLongBits(4.0d), machine.state().floatingPointRegister(8));
        }
    }

    /// Verifies NaN edge behavior for dedicated floating-point micro-opcodes.
    @Test
    public void nanEdgeFloatingPointOpcodesExecuteInMicroBlock() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    fminS(3, 1, 2),
                    fmaxD(4, 5, 6),
                    feqD(10, 7, 8),
                    csrrs(11, FFLAGS_CSR, 0),
                    csrrwi(0, FFLAGS_CSR, 0),
                    fminD(9, 12, 13),
                    fltD(12, 7, 8),
                    fclassD(13, 12),
                    csrrs(14, FFLAGS_CSR, 0),
                    jal(0));
            machine.state().setFloatingPointRegister(1, boxedSingle(CANONICAL_SINGLE_NAN));
            machine.state().setFloatingPointRegister(2, boxedSingle(Float.floatToRawIntBits(2.0f)));
            machine.state().setFloatingPointRegister(5, CANONICAL_DOUBLE_NAN);
            machine.state().setFloatingPointRegister(6, Double.doubleToRawLongBits(-1.0d));
            machine.state().setFloatingPointRegister(7, CANONICAL_DOUBLE_NAN);
            machine.state().setFloatingPointRegister(8, Double.doubleToRawLongBits(1.0d));
            machine.state().setFloatingPointRegister(12, 0x7ff0_0000_0000_0001L);
            machine.state().setFloatingPointRegister(13, Double.doubleToRawLongBits(3.0d));

            executeMicroBlock(machine);

            assertEquals(boxedSingle(Float.floatToRawIntBits(2.0f)), machine.state().floatingPointRegister(3));
            assertEquals(Double.doubleToRawLongBits(-1.0d), machine.state().floatingPointRegister(4));
            assertEquals(0, machine.state().register(10));
            assertEquals(0, machine.state().register(11));
            assertEquals(Double.doubleToRawLongBits(3.0d), machine.state().floatingPointRegister(9));
            assertEquals(0, machine.state().register(12));
            assertEquals(1 << 8, machine.state().register(13));
            assertEquals(0x10, machine.state().register(14));
        }
    }

    /// Verifies dynamic-rounding floating-point micro-opcodes preserve the faulting PC for invalid `frm`.
    @Test
    public void dynamicRoundingFloatingPointOpcodeKeepsFaultingPc() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(machine.memory(), faddD(3, 1, 2, 7), jal(0));
            machine.state().setFloatingPointRegister(1, Double.doubleToRawLongBits(1.0d));
            machine.state().setFloatingPointRegister(2, Double.doubleToRawLongBits(1.0d));
            machine.state().writeControlStatusRegister(FRM_CSR, 5);
            machine.state().setPc(0x1234);

            assertThrows(RiscVException.class, () -> executeMicroBlock(machine));
            assertEquals(TEST_PC, machine.state().pc());
        }
    }

    /// Verifies that the micro-opcode for `fmv.x.w` copies raw low bits without NaN-boxing.
    @Test
    public void singleMoveToIntegerMicroOpcodeUsesRawLowWord() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(machine.memory(), fmvXW(10, 1), fmvXW(11, 2), jal(0));
            machine.state().setFloatingPointRegister(1, 0x7fff_ffff_1111_1111L);
            machine.state().setFloatingPointRegister(2, 0xffff_ffff_9234_5678L);

            executeMicroBlock(machine);

            assertEquals(0x1111_1111L, machine.state().register(10));
            assertEquals(0xffff_ffff_9234_5678L, machine.state().register(11));
        }
    }

    /// Verifies Zfhmin half-precision moves and conversions in micro-block execution.
    @Test
    public void minimalHalfPrecisionOperationsExecuteInMicroBlock() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    fmvHX(1, 10),
                    fmvXH(11, 1),
                    fcvtSH(2, 1),
                    fcvtHS(3, 2),
                    fmvXH(12, 3),
                    jal(0));
            machine.state().setRegister(10, 0x3c00);

            executeMicroBlock(machine);

            assertEquals(boxedHalf(0x3c00), machine.state().floatingPointRegister(1));
            assertEquals(0x3c00, machine.state().register(11));
            assertEquals(boxedSingle(Float.floatToRawIntBits(1.0f)), machine.state().floatingPointRegister(2));
            assertEquals(boxedHalf(0x3c00), machine.state().floatingPointRegister(3));
            assertEquals(0x3c00, machine.state().register(12));
        }
    }

    /// Executes one decoded block through the micro-bytecode node.
    private static void executeMicroBlock(TestMachine machine) {
        RiscVMicroBlockNode block = RiscVMicroBlockCompiler.compileNode(
                RiscVDecoder.decodeBlock(machine.memory(), TEST_PC),
                machine.memory().layout(),
                RiscVMicroBlockNode.BATCHED_FAST_MODE);
        block.execute(machine.state(), machine.memory().newAccess());
    }

    /// Stores little-endian instruction words into guest memory.
    private static void loadInstructions(Memory memory, int... instructions) {
        for (int index = 0; index < instructions.length; index++) {
            memory.writeInt(TEST_PC + (long) index * Integer.BYTES, instructions[index]);
        }
    }

    /// Encodes `fadd.d`.
    private static int faddD(int rd, int rs1, int rs2) {
        return faddD(rd, rs1, rs2, 0);
    }

    /// Encodes `fadd.d` with an explicit rounding mode.
    private static int faddD(int rd, int rs1, int rs2, int roundingMode) {
        return opFp(0x01, rd, roundingMode, rs1, rs2);
    }

    /// Encodes `fsqrt.d`.
    private static int fsqrtD(int rd, int rs1) {
        return opFp(0x2d, rd, 0, rs1, 0);
    }

    /// Encodes `fmin.s`.
    private static int fminS(int rd, int rs1, int rs2) {
        return opFp(0x14, rd, 0, rs1, rs2);
    }

    /// Encodes `fmin.d`.
    private static int fminD(int rd, int rs1, int rs2) {
        return opFp(0x15, rd, 0, rs1, rs2);
    }

    /// Encodes `fmax.d`.
    private static int fmaxD(int rd, int rs1, int rs2) {
        return opFp(0x15, rd, 1, rs1, rs2);
    }

    /// Encodes `feq.d`.
    private static int feqD(int rd, int rs1, int rs2) {
        return opFp(0x51, rd, 2, rs1, rs2);
    }

    /// Encodes `flt.d`.
    private static int fltD(int rd, int rs1, int rs2) {
        return opFp(0x51, rd, 1, rs1, rs2);
    }

    /// Encodes `fclass.d`.
    private static int fclassD(int rd, int rs1) {
        return opFp(0x71, rd, 1, rs1, 0);
    }

    /// Encodes `fmv.x.w`.
    private static int fmvXW(int rd, int rs1) {
        return opFp(0x70, rd, 0, rs1, 0);
    }

    /// Encodes `fmv.x.h`.
    private static int fmvXH(int rd, int rs1) {
        return opFp(0x72, rd, 0, rs1, 0);
    }

    /// Encodes `fmv.h.x`.
    private static int fmvHX(int rd, int rs1) {
        return opFp(0x7a, rd, 0, rs1, 0);
    }

    /// Encodes `fcvt.s.d`.
    private static int fcvtSD(int rd, int rs1) {
        return opFp(0x20, rd, 0, rs1, 1);
    }

    /// Encodes `fcvt.s.h`.
    private static int fcvtSH(int rd, int rs1) {
        return opFp(0x20, rd, 0, rs1, 2);
    }

    /// Encodes `fcvt.h.s`.
    private static int fcvtHS(int rd, int rs1) {
        return opFp(0x22, rd, 0, rs1, 0);
    }

    /// Encodes `fcvt.d.s`.
    private static int fcvtDS(int rd, int rs1) {
        return opFp(0x21, rd, 0, rs1, 0);
    }

    /// Encodes `fcvt.l.d`.
    private static int fcvtLD(int rd, int rs1) {
        return opFp(0x61, rd, 0, rs1, 2);
    }

    /// Encodes `fcvt.d.l`.
    private static int fcvtDL(int rd, int rs1) {
        return opFp(0x69, rd, 0, rs1, 2);
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

    /// Encodes `jal x0, offset`.
    private static int jal(int offset) {
        return (((offset >>> 20) & 0x1) << 31)
                | (((offset >>> 1) & 0x3ff) << 21)
                | (((offset >>> 11) & 0x1) << 20)
                | (((offset >>> 12) & 0xff) << 12)
                | 0x6f;
    }

    /// Returns an RV64 NaN-boxed single-precision bit pattern.
    private static long boxedSingle(int bits) {
        return 0xffff_ffff_0000_0000L | (bits & 0xffff_ffffL);
    }

    /// Returns an RV64 NaN-boxed half-precision bit pattern.
    private static long boxedHalf(int bits) {
        return 0xffff_ffff_ffff_0000L | (bits & 0xffffL);
    }

    /// Owns a micro-block test machine and its closeable resources.
    ///
    /// @param memory the guest memory under test
    /// @param syscalls the syscall handler attached to the machine state
    /// @param state the mutable architectural state under test
    @NotNullByDefault
    private record TestMachine(
            Memory memory,
            GuestSyscalls syscalls,
            RiscVThreadState state) implements AutoCloseable {
        /// Creates a test machine initialized at the decoder test address.
        private static TestMachine create() {
            Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 4096, null);
            GuestSyscalls syscalls = new LinuxGuestSyscalls(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    memory.baseAddress());
            RiscVThreadState state = new RiscVThreadState(
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
