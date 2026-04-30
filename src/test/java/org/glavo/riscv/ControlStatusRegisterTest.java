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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests user-mode CSR and instruction-fetch fence behavior.
@NotNullByDefault
public final class ControlStatusRegisterTest {
    /// The guest address used by CSR decoder tests.
    private static final long TEST_PC = Memory.DEFAULT_BASE_ADDRESS;

    /// The `fflags` CSR address.
    private static final int FFLAGS_CSR = 0x001;

    /// The `frm` CSR address.
    private static final int FRM_CSR = 0x002;

    /// The `fcsr` CSR address.
    private static final int FCSR_CSR = 0x003;

    /// The `mstatus` CSR address.
    private static final int MSTATUS_CSR = 0x300;

    /// The `mepc` CSR address.
    private static final int MEPC_CSR = 0x341;

    /// The `mhartid` CSR address.
    private static final int MHARTID_CSR = 0xf14;

    /// The `cycle` CSR address.
    private static final int CYCLE_CSR = 0xc00;

    /// The `time` CSR address.
    private static final int TIME_CSR = 0xc01;

    /// The `instret` CSR address.
    private static final int INSTRET_CSR = 0xc02;

    /// The first result register used by CSR tests.
    private static final int RESULT_REGISTER = 10;

    /// The second result register used by CSR tests.
    private static final int SECOND_RESULT_REGISTER = 11;

    /// The third result register used by CSR tests.
    private static final int THIRD_RESULT_REGISTER = 12;

    /// The source register used by CSR register-form tests.
    private static final int SOURCE_REGISTER = 5;

    /// The Linux syscall register.
    private static final int SYSCALL_REGISTER = 17;

    /// The Linux `exit` syscall number.
    private static final long EXIT_SYSCALL = 93;

    /// Verifies CSR register forms and floating-point CSR aliases.
    @Test
    public void registerCsrInstructionsUpdateFloatingPointControlStatus() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    csrrw(RESULT_REGISTER, FCSR_CSR, SOURCE_REGISTER),
                    csrrs(SECOND_RESULT_REGISTER, FFLAGS_CSR, 0),
                    csrrc(THIRD_RESULT_REGISTER, FCSR_CSR, SOURCE_REGISTER),
                    ElfTestImages.ecall());
            machine.state().setRegister(SOURCE_REGISTER, 0xaa);
            prepareExit(machine.state());

            runDecodedProgram(machine);

            assertEquals(0, machine.state().register(RESULT_REGISTER));
            assertEquals(0x0a, machine.state().register(SECOND_RESULT_REGISTER));
            assertEquals(0xaa, machine.state().register(THIRD_RESULT_REGISTER));
            assertEquals(0, machine.state().readControlStatusRegister(FCSR_CSR));
        }
    }

    /// Verifies CSR immediate forms for `fflags`, `frm`, and `fcsr`.
    @Test
    public void immediateCsrInstructionsUpdateFloatingPointControlStatus() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    csrrwi(0, FFLAGS_CSR, 0x1f),
                    csrrwi(0, FRM_CSR, 4),
                    csrrs(RESULT_REGISTER, FCSR_CSR, 0),
                    csrrci(0, FFLAGS_CSR, 1),
                    csrrs(SECOND_RESULT_REGISTER, FCSR_CSR, 0),
                    ElfTestImages.ecall());
            prepareExit(machine.state());

            runDecodedProgram(machine);

            assertEquals(0x9f, machine.state().register(RESULT_REGISTER));
            assertEquals(0x9e, machine.state().register(SECOND_RESULT_REGISTER));
        }
    }

    /// Verifies deterministic user counter CSR reads.
    @Test
    public void counterCsrsReadRetiredInstructionCount() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    csrrs(RESULT_REGISTER, CYCLE_CSR, 0),
                    csrrs(SECOND_RESULT_REGISTER, TIME_CSR, 0),
                    csrrs(THIRD_RESULT_REGISTER, INSTRET_CSR, 0),
                    ElfTestImages.ecall());
            prepareExit(machine.state());

            runDecodedProgram(machine);

            assertEquals(1, machine.state().register(RESULT_REGISTER));
            assertEquals(2, machine.state().register(SECOND_RESULT_REGISTER));
            assertEquals(3, machine.state().register(THIRD_RESULT_REGISTER));
        }
    }

    /// Verifies the minimal machine-mode CSR and `mret` behavior needed by `riscv-test-env`.
    @Test
    public void machineBootstrapCompatibilityCsrsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    csrrs(RESULT_REGISTER, MHARTID_CSR, 0),
                    csrrw(0, MSTATUS_CSR, 0),
                    csrrw(0, MEPC_CSR, SOURCE_REGISTER),
                    mret(),
                    ElfTestImages.addi(SECOND_RESULT_REGISTER, 0, 13),
                    ElfTestImages.ecall());
            machine.state().setRegister(SOURCE_REGISTER, TEST_PC + 4L * Integer.BYTES);
            prepareExit(machine.state());

            runDecodedProgram(machine);

            assertEquals(0, machine.state().register(RESULT_REGISTER));
            assertEquals(13, machine.state().register(SECOND_RESULT_REGISTER));
        }
    }

    /// Verifies that writing a read-only counter CSR fails with a useful diagnostic.
    @Test
    public void writingCounterCsrFails() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(machine.memory(), csrrw(0, CYCLE_CSR, 0), ElfTestImages.ecall());
            prepareExit(machine.state());

            RiscVException exception = assertThrows(RiscVException.class, () -> runDecodedProgram(machine, 1));

            assertTrue(exception.getMessage().contains("read-only"));
            assertTrue(exception.getMessage().contains("0xc00"));
        }
    }

    /// Verifies that unsupported user-mode CSR reads fail with a useful diagnostic.
    @Test
    public void unsupportedCsrFails() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(machine.memory(), csrrs(RESULT_REGISTER, 0x999, 0), ElfTestImages.ecall());
            prepareExit(machine.state());

            RiscVException exception = assertThrows(RiscVException.class, () -> runDecodedProgram(machine, 1));

            assertTrue(exception.getMessage().contains("Unsupported control status register"));
            assertTrue(exception.getMessage().contains("0x999"));
        }
    }

    /// Verifies that `fence.i` decodes and executes as a user-mode no-op.
    @Test
    public void fenceInstructionNoOpExecutes() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    ElfTestImages.addi(RESULT_REGISTER, 0, 42),
                    fenceI(),
                    ElfTestImages.ecall());
            prepareExit(machine.state());

            runDecodedProgram(machine);

            assertEquals(42, machine.state().register(RESULT_REGISTER));
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

    /// Runs decoded blocks until exit or until the supplied block budget is exhausted.
    private static void runDecodedProgram(TestMachine machine, int maxBlocks) {
        for (int index = 0; index < maxBlocks; index++) {
            try {
                DecodedBlockTestExecutor.execute(machine.state(), RiscVDecoder.decodeBlock(machine.memory(), machine.state().pc()));
            } catch (ProgramExitException exception) {
                return;
            }
        }
        throw new AssertionError("Program did not exit within " + maxBlocks + " decoded blocks");
    }

    /// Loads little-endian instruction words at the decoder test address.
    private static void loadInstructions(Memory memory, int... instructions) {
        ByteBuffer code = ByteBuffer.allocate(instructions.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int instruction : instructions) {
            code.putInt(instruction);
        }
        memory.load(TEST_PC, code.array(), 0, code.array().length);
    }

    /// Encodes `csrrw`.
    private static int csrrw(int rd, int csr, int rs1) {
        return csrInstruction(1, rd, csr, rs1);
    }

    /// Encodes `csrrs`.
    private static int csrrs(int rd, int csr, int rs1) {
        return csrInstruction(2, rd, csr, rs1);
    }

    /// Encodes `csrrc`.
    private static int csrrc(int rd, int csr, int rs1) {
        return csrInstruction(3, rd, csr, rs1);
    }

    /// Encodes `csrrwi`.
    private static int csrrwi(int rd, int csr, int immediate) {
        return csrInstruction(5, rd, csr, immediate);
    }

    /// Encodes `csrrci`.
    private static int csrrci(int rd, int csr, int immediate) {
        return csrInstruction(7, rd, csr, immediate);
    }

    /// Encodes a CSR instruction.
    private static int csrInstruction(int funct3, int rd, int csr, int source) {
        return (csr << 20) | (source << 15) | (funct3 << 12) | (rd << 7) | 0x73;
    }

    /// Encodes `fence.i`.
    private static int fenceI() {
        return 0x0000_100f;
    }

    /// Encodes `mret`.
    private static int mret() {
        return 0x3020_0073;
    }

    /// Owns a CSR test machine and its closeable resources.
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
