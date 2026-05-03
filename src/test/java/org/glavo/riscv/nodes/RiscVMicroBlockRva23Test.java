// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.nodes;

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

/// Tests RVA23U64 scalar instructions through the production micro-bytecode block executor.
@NotNullByDefault
public final class RiscVMicroBlockRva23Test {
    /// The guest address used for micro-block execution tests.
    private static final long TEST_PC = Memory.DEFAULT_BASE_ADDRESS;

    /// Executes Zicond and Zimop instructions through the generic micro-operation path.
    @Test
    public void scalarOperationsExecuteInMicroBlock() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    czeroEqz(10, 5, 6),
                    czeroEqz(11, 5, 7),
                    czeroNez(12, 5, 6),
                    czeroNez(13, 5, 7),
                    mopR(14, 5),
                    mopRr(15, 5, 6),
                    jal(0));
            machine.state().setRegister(5, 0x1234);
            machine.state().setRegister(6, 0);
            machine.state().setRegister(7, 1);
            machine.state().setRegister(14, 99);
            machine.state().setRegister(15, 99);

            executeMicroBlock(machine);

            assertEquals(0, machine.state().register(10));
            assertEquals(0x1234, machine.state().register(11));
            assertEquals(0x1234, machine.state().register(12));
            assertEquals(0, machine.state().register(13));
            assertEquals(0, machine.state().register(14));
            assertEquals(0, machine.state().register(15));
        }
    }

    /// Executes Zawrs wait instructions as no-op hints in micro-block execution.
    @Test
    public void waitOnReservationHintsAdvanceInMicroBlock() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    wrsNto(),
                    wrsSto(),
                    addi(10, 0, 7),
                    jal(0));

            executeMicroBlock(machine);

            assertEquals(7, machine.state().register(10));
        }
    }

    /// Executes a compressed may-be-operation hint before an unaligned 32-bit instruction.
    @Test
    public void compressedMopAdvancesInMicroBlock() {
        try (TestMachine machine = TestMachine.create()) {
            machine.memory().writeShort(TEST_PC, (short) cMop(1));
            machine.memory().writeInt(TEST_PC + Short.BYTES, addi(10, 0, 7));
            machine.memory().writeInt(TEST_PC + Short.BYTES + Integer.BYTES, jal(0));

            executeMicroBlock(machine);

            assertEquals(7, machine.state().register(10));
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

    /// Encodes `addi`.
    private static int addi(int rd, int rs1, int immediate) {
        return ((immediate & 0xfff) << 20) | (rs1 << 15) | (rd << 7) | 0x13;
    }

    /// Encodes `czero.eqz`.
    private static int czeroEqz(int rd, int rs1, int rs2) {
        return rType(0x33, rd, 5, rs1, rs2, 0x07);
    }

    /// Encodes `czero.nez`.
    private static int czeroNez(int rd, int rs1, int rs2) {
        return rType(0x33, rd, 7, rs1, rs2, 0x07);
    }

    /// Encodes `mop.r.0`.
    private static int mopR(int rd, int rs1) {
        return 0x81c0_4073 | (rs1 << 15) | (rd << 7);
    }

    /// Encodes `mop.rr.0`.
    private static int mopRr(int rd, int rs1, int rs2) {
        return 0x8200_4073 | (rs2 << 20) | (rs1 << 15) | (rd << 7);
    }

    /// Encodes `wrs.nto`.
    private static int wrsNto() {
        return 0x00d0_0073;
    }

    /// Encodes `wrs.sto`.
    private static int wrsSto() {
        return 0x01d0_0073;
    }

    /// Encodes `c.mop.n`.
    private static int cMop(int function) {
        return (3 << 13) | (function << 7) | 1;
    }

    /// Encodes an R-type instruction.
    private static int rType(int opcode, int rd, int funct3, int rs1, int rs2, int funct7) {
        return (funct7 << 25) | (rs2 << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | opcode;
    }

    /// Encodes `jal x0, offset`.
    private static int jal(int offset) {
        return (((offset >>> 20) & 0x1) << 31)
                | (((offset >>> 1) & 0x3ff) << 21)
                | (((offset >>> 11) & 0x1) << 20)
                | (((offset >>> 12) & 0xff) << 12)
                | 0x6f;
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
        /// Creates a test machine initialized at the micro-block test address.
        private static TestMachine create() {
            Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 4096);
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
