// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.nodes;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.parser.ElfImage;
import org.glavo.riscv.parser.RiscVDecoder;
import org.glavo.riscv.runtime.GuestSyscalls;
import org.glavo.riscv.runtime.MachineState;
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

    /// The `frm` CSR address.
    private static final int FRM_CSR = 0x002;

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

    /// Encodes `fcvt.s.d`.
    private static int fcvtSD(int rd, int rs1) {
        return opFp(0x20, rd, 0, rs1, 1);
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

    /// Owns a micro-block test machine and its closeable resources.
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
