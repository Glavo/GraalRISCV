// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.*;
import org.glavo.riscv.parser.*;
import org.glavo.riscv.runtime.*;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests single-threaded RV64A atomic instruction behavior.
@NotNullByDefault
public final class AtomicInstructionSemanticsTest {
    /// The guest address used for standalone atomic instruction execution.
    private static final long TEST_PC = Memory.DEFAULT_BASE_ADDRESS;

    /// The aligned guest data address used by atomic instruction tests.
    private static final long DATA_ADDRESS = Memory.DEFAULT_BASE_ADDRESS + 512;

    /// The destination register used by atomic instruction tests.
    private static final int RESULT_REGISTER = 10;

    /// The base-address source register used by atomic instruction tests.
    private static final int ADDRESS_REGISTER = 5;

    /// The value source register used by atomic instruction tests.
    private static final int VALUE_REGISTER = 6;

    /// Verifies that word LR/SC succeeds once and then fails without a fresh reservation.
    @Test
    public void wordLoadReservedStoreConditionalSucceedsOnce() {
        try (TestMachine machine = TestMachine.create()) {
            machine.memory().writeInt(DATA_ADDRESS, 41);
            machine.state().setRegister(ADDRESS_REGISTER, DATA_ADDRESS);

            execute(machine, RiscVOperation.LR_W, RESULT_REGISTER, ADDRESS_REGISTER, 0, 0);
            assertEquals(41, machine.state().register(RESULT_REGISTER));

            machine.state().setRegister(VALUE_REGISTER, 42);
            execute(machine, RiscVOperation.SC_W, RESULT_REGISTER, ADDRESS_REGISTER, VALUE_REGISTER, 0);
            assertEquals(0, machine.state().register(RESULT_REGISTER));
            assertEquals(42, machine.memory().readInt(DATA_ADDRESS));

            machine.state().setRegister(VALUE_REGISTER, 43);
            execute(machine, RiscVOperation.SC_W, RESULT_REGISTER, ADDRESS_REGISTER, VALUE_REGISTER, 0);
            assertEquals(1, machine.state().register(RESULT_REGISTER));
            assertEquals(42, machine.memory().readInt(DATA_ADDRESS));
        }
    }

    /// Verifies that regular stores clear an active LR/SC reservation in this single-threaded model.
    @Test
    public void regularStoreClearsDoublewordReservation() {
        try (TestMachine machine = TestMachine.create()) {
            machine.memory().writeLong(DATA_ADDRESS, 41);
            machine.memory().writeLong(DATA_ADDRESS + Long.BYTES, 0);
            machine.state().setRegister(ADDRESS_REGISTER, DATA_ADDRESS);

            execute(machine, RiscVOperation.LR_D, RESULT_REGISTER, ADDRESS_REGISTER, 0, 0);
            assertEquals(41, machine.state().register(RESULT_REGISTER));

            machine.state().setRegister(VALUE_REGISTER, 99);
            execute(machine, RiscVOperation.SD, 0, ADDRESS_REGISTER, VALUE_REGISTER, Long.BYTES);

            machine.state().setRegister(VALUE_REGISTER, 42);
            execute(machine, RiscVOperation.SC_D, RESULT_REGISTER, ADDRESS_REGISTER, VALUE_REGISTER, 0);

            assertEquals(1, machine.state().register(RESULT_REGISTER));
            assertEquals(41, machine.memory().readLong(DATA_ADDRESS));
            assertEquals(99, machine.memory().readLong(DATA_ADDRESS + Long.BYTES));
        }
    }

    /// Verifies that store-conditional requires the same data width as the latest load-reserved.
    @Test
    public void storeConditionalRequiresMatchingLoadReservedWidth() {
        try (TestMachine machine = TestMachine.create()) {
            machine.memory().writeLong(DATA_ADDRESS, 0x1122_3344_5566_7788L);
            machine.state().setRegister(ADDRESS_REGISTER, DATA_ADDRESS);

            execute(machine, RiscVOperation.LR_W, RESULT_REGISTER, ADDRESS_REGISTER, 0, 0);

            machine.state().setRegister(VALUE_REGISTER, 0x0102_0304_0506_0708L);
            execute(machine, RiscVOperation.SC_D, RESULT_REGISTER, ADDRESS_REGISTER, VALUE_REGISTER, 0);

            assertEquals(1, machine.state().register(RESULT_REGISTER));
            assertEquals(0x1122_3344_5566_7788L, machine.memory().readLong(DATA_ADDRESS));
        }
    }

    /// Verifies that atomic memory instructions require natural alignment.
    @Test
    public void atomicMemoryInstructionsRequireNaturalAlignment() {
        try (TestMachine machine = TestMachine.create()) {
            machine.state().setRegister(ADDRESS_REGISTER, DATA_ADDRESS + 1);
            machine.state().setRegister(VALUE_REGISTER, 2);

            assertMisalignedAtomicAccess(machine, RiscVOperation.LR_W, 0);
            assertMisalignedAtomicAccess(machine, RiscVOperation.SC_D, VALUE_REGISTER);
            assertMisalignedAtomicAccess(machine, RiscVOperation.AMOADD_W, VALUE_REGISTER);
        }
    }

    /// Verifies that word AMOs return the old sign-extended value and store the new word value.
    @Test
    public void wordAmoReturnsOldValueAndStoresNewWord() {
        try (TestMachine machine = TestMachine.create()) {
            machine.memory().writeInt(DATA_ADDRESS, -1);
            machine.state().setRegister(ADDRESS_REGISTER, DATA_ADDRESS);
            machine.state().setRegister(VALUE_REGISTER, 2);

            execute(machine, RiscVOperation.AMOADD_W, RESULT_REGISTER, ADDRESS_REGISTER, VALUE_REGISTER, 0);

            assertEquals(-1L, machine.state().register(RESULT_REGISTER));
            assertEquals(1, machine.memory().readInt(DATA_ADDRESS));
        }
    }

    /// Verifies unsigned ordering for doubleword AMO minimum.
    @Test
    public void doublewordAmoUsesUnsignedOrdering() {
        try (TestMachine machine = TestMachine.create()) {
            machine.memory().writeLong(DATA_ADDRESS, -1L);
            machine.state().setRegister(ADDRESS_REGISTER, DATA_ADDRESS);
            machine.state().setRegister(VALUE_REGISTER, 1);

            execute(machine, RiscVOperation.AMOMINU_D, RESULT_REGISTER, ADDRESS_REGISTER, VALUE_REGISTER, 0);

            assertEquals(-1L, machine.state().register(RESULT_REGISTER));
            assertEquals(1, machine.memory().readLong(DATA_ADDRESS));
        }
    }

    /// Executes one atomic test instruction against the supplied machine.
    private static void execute(
            TestMachine machine,
            RiscVOperation operation,
            int rd,
            int rs1,
            int rs2,
            long immediate) {
        long pc = machine.state().pc();
        RiscVInstructionSemantics instruction = RiscVInstructionSemantics.create(
                pc,
                0,
                Integer.BYTES,
                operation,
                rd,
                rs1,
                rs2,
                immediate,
                false);
        instruction.execute(machine.state());
        assertEquals(pc + Integer.BYTES, machine.state().pc());
    }

    /// Verifies that one misaligned atomic operation throws the expected diagnostic.
    private static void assertMisalignedAtomicAccess(TestMachine machine, RiscVOperation operation, int rs2) {
        RiscVException exception = assertThrows(RiscVException.class, () ->
                execute(machine, operation, RESULT_REGISTER, ADDRESS_REGISTER, rs2, 0));
        assertTrue(exception.getMessage().contains("Misaligned atomic memory access"));
    }

    /// Owns an atomic-instruction test machine and its closeable resources.
    ///
    /// @param memory the guest memory under test
    /// @param syscalls the syscall handler attached to the machine state
    /// @param state the mutable architectural state under test
    @NotNullByDefault
    private record TestMachine(
            Memory memory,
            GuestSyscalls syscalls,
            MachineState state) implements AutoCloseable {
        /// Creates a test machine initialized at the atomic test address.
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
