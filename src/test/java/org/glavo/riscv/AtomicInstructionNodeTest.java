package org.glavo.riscv;

import org.glavo.riscv.InstructionNode.Operation;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests single-threaded RV64A atomic instruction behavior.
@NotNullByDefault
public final class AtomicInstructionNodeTest {
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

            execute(machine, Operation.LR_W, RESULT_REGISTER, ADDRESS_REGISTER, 0, 0);
            assertEquals(41, machine.state().register(RESULT_REGISTER));

            machine.state().setRegister(VALUE_REGISTER, 42);
            execute(machine, Operation.SC_W, RESULT_REGISTER, ADDRESS_REGISTER, VALUE_REGISTER, 0);
            assertEquals(0, machine.state().register(RESULT_REGISTER));
            assertEquals(42, machine.memory().readInt(DATA_ADDRESS));

            machine.state().setRegister(VALUE_REGISTER, 43);
            execute(machine, Operation.SC_W, RESULT_REGISTER, ADDRESS_REGISTER, VALUE_REGISTER, 0);
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

            execute(machine, Operation.LR_D, RESULT_REGISTER, ADDRESS_REGISTER, 0, 0);
            assertEquals(41, machine.state().register(RESULT_REGISTER));

            machine.state().setRegister(VALUE_REGISTER, 99);
            execute(machine, Operation.SD, 0, ADDRESS_REGISTER, VALUE_REGISTER, Long.BYTES);

            machine.state().setRegister(VALUE_REGISTER, 42);
            execute(machine, Operation.SC_D, RESULT_REGISTER, ADDRESS_REGISTER, VALUE_REGISTER, 0);

            assertEquals(1, machine.state().register(RESULT_REGISTER));
            assertEquals(41, machine.memory().readLong(DATA_ADDRESS));
            assertEquals(99, machine.memory().readLong(DATA_ADDRESS + Long.BYTES));
        }
    }

    /// Verifies that word AMOs return the old sign-extended value and store the new word value.
    @Test
    public void wordAmoReturnsOldValueAndStoresNewWord() {
        try (TestMachine machine = TestMachine.create()) {
            machine.memory().writeInt(DATA_ADDRESS, -1);
            machine.state().setRegister(ADDRESS_REGISTER, DATA_ADDRESS);
            machine.state().setRegister(VALUE_REGISTER, 2);

            execute(machine, Operation.AMOADD_W, RESULT_REGISTER, ADDRESS_REGISTER, VALUE_REGISTER, 0);

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

            execute(machine, Operation.AMOMINU_D, RESULT_REGISTER, ADDRESS_REGISTER, VALUE_REGISTER, 0);

            assertEquals(-1L, machine.state().register(RESULT_REGISTER));
            assertEquals(1, machine.memory().readLong(DATA_ADDRESS));
        }
    }

    /// Executes one atomic test instruction against the supplied machine.
    private static void execute(
            TestMachine machine,
            Operation operation,
            int rd,
            int rs1,
            int rs2,
            long immediate) {
        long pc = machine.state().pc();
        InstructionNode instruction = InstructionNode.create(
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

    /// Owns an atomic-instruction test machine and its closeable resources.
    @NotNullByDefault
    private record TestMachine(
            /// The guest memory under test.
            Memory memory,

            /// The syscall handler attached to the machine state.
            GuestSyscalls syscalls,

            /// The mutable architectural state under test.
            MachineState state) implements AutoCloseable {
        /// Creates a test machine initialized at the atomic test address.
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
