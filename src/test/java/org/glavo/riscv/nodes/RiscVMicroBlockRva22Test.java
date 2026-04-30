// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.nodes;

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

/// Tests RVA22U64 instructions through the production micro-bytecode block executor.
@NotNullByDefault
public final class RiscVMicroBlockRva22Test {
    /// The guest address used for micro-block execution tests.
    private static final long TEST_PC = Memory.DEFAULT_BASE_ADDRESS;

    /// Executes RVA22U64 bit-manipulation instructions through the generic micro-operation path.
    @Test
    public void bitManipulationOperationsExecuteInMicroBlock() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    rType(0x33, 10, 2, 5, 6, 0x10),
                    iType(0x13, 11, 1, 5, 0x600),
                    iType(0x13, 12, 1, 0, 0x2bf),
                    jal(0));
            machine.state().setRegister(5, 0x10);
            machine.state().setRegister(6, 1);

            executeMicroBlock(machine);

            assertEquals(0x21, machine.state().register(10));
            assertEquals(59, machine.state().register(11));
            assertEquals(Long.MIN_VALUE, machine.state().register(12));
        }
    }

    /// Executes `cbo.zero` through the generic micro-operation path.
    @Test
    public void cacheBlockZeroExecutesInMicroBlock() {
        try (TestMachine machine = TestMachine.create()) {
            long base = TEST_PC + 256;
            for (int index = 0; index < 128; index++) {
                machine.memory().writeByte(base + index, (byte) 0x33);
            }
            loadInstructions(machine.memory(), cbo(4, 5), jal(0));
            machine.state().setRegister(5, base + 71);

            executeMicroBlock(machine);

            for (int index = 0; index < 64; index++) {
                assertEquals(0, machine.memory().readUnsignedByte(base + 64 + index));
            }
            assertEquals(0x33, machine.memory().readUnsignedByte(base + 63));
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

    /// Encodes an I-type instruction.
    private static int iType(int opcode, int rd, int funct3, int rs1, int immediate) {
        return ((immediate & 0xfff) << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | opcode;
    }

    /// Encodes an R-type instruction.
    private static int rType(int opcode, int rd, int funct3, int rs1, int rs2, int funct7) {
        return (funct7 << 25) | (rs2 << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | opcode;
    }

    /// Encodes a cache-block operation.
    private static int cbo(int function, int rs1) {
        return (function << 20) | (rs1 << 15) | (2 << 12) | 0x0f;
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
            MachineState state) implements AutoCloseable {
        /// Creates a test machine initialized at the micro-block test address.
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
