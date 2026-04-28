package org.glavo.riscv;

import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import org.jetbrains.annotations.NotNullByDefault;

/// Bytecode DSL root used to execute one decoded RISC-V basic block.
@GenerateBytecode(
        languageClass = RiscVLanguage.class,
        enableUncachedInterpreter = false,
        allowUnsafe = false)
@NotNullByDefault
public abstract class RiscVBytecodeBlockRootNode extends RootNode implements BytecodeRootNode {
    /// Creates a bytecode block root with the generated frame descriptor.
    protected RiscVBytecodeBlockRootNode(RiscVLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    /// Advances `pc` for no-op control instructions.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    public static final class ExecuteAdvancePc {
        /// Retires the instruction and writes the sequential PC.
        @Specialization
        static void execute(long address, int raw, long nextPc, MachineState state) {
            beginInstruction(state, address, raw);
            state.setPc(nextPc);
        }
    }

    /// Executes `lui`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteLui {
        /// Writes the decoded upper immediate.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, immediate);
            state.setPc(nextPc);
        }
    }

    /// Executes `auipc`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteAuipc {
        /// Writes the PC-relative upper immediate.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, address + immediate);
            state.setPc(nextPc);
        }
    }

    /// Executes `jal`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteJal {
        /// Writes the link register and jumps to the PC-relative target.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, nextPc);
            state.setPc(address + immediate);
        }
    }

    /// Executes `jalr`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteJalr {
        /// Writes the link register and jumps to the register-relative target.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            long target = (state.decodedRegister(rs1) + immediate) & ~1L;
            state.setDecodedRegister(rd, nextPc);
            state.setPc(target);
        }
    }

    /// Executes `beq`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteBeq {
        /// Branches when both source registers are equal.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rs1, int rs2, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            branch(state, state.decodedRegister(rs1) == state.decodedRegister(rs2), address, immediate, nextPc);
        }
    }

    /// Executes `bne`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteBne {
        /// Branches when both source registers differ.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rs1, int rs2, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            branch(state, state.decodedRegister(rs1) != state.decodedRegister(rs2), address, immediate, nextPc);
        }
    }

    /// Executes `blt`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteBlt {
        /// Branches when the first source register is signed-less-than the second.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rs1, int rs2, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            branch(state, state.decodedRegister(rs1) < state.decodedRegister(rs2), address, immediate, nextPc);
        }
    }

    /// Executes `bge`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteBge {
        /// Branches when the first source register is signed-greater-or-equal to the second.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rs1, int rs2, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            branch(state, state.decodedRegister(rs1) >= state.decodedRegister(rs2), address, immediate, nextPc);
        }
    }

    /// Executes `bltu`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteBltu {
        /// Branches when the first source register is unsigned-less-than the second.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rs1, int rs2, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            branch(state, Long.compareUnsigned(state.decodedRegister(rs1), state.decodedRegister(rs2)) < 0, address, immediate, nextPc);
        }
    }

    /// Executes `bgeu`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteBgeu {
        /// Branches when the first source register is unsigned-greater-or-equal to the second.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rs1, int rs2, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            branch(state, Long.compareUnsigned(state.decodedRegister(rs1), state.decodedRegister(rs2)) >= 0, address, immediate, nextPc);
        }
    }

    /// Executes `lb`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteLb {
        /// Loads a sign-extended byte into an integer register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.memory().readByte(state.decodedRegister(rs1) + immediate));
            state.setPc(nextPc);
        }
    }

    /// Executes `lh`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteLh {
        /// Loads a sign-extended halfword into an integer register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.memory().readShort(state.decodedRegister(rs1) + immediate));
            state.setPc(nextPc);
        }
    }

    /// Executes `lw`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteLw {
        /// Loads a sign-extended word into an integer register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.memory().readInt(state.decodedRegister(rs1) + immediate));
            state.setPc(nextPc);
        }
    }

    /// Executes `ld`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteLd {
        /// Loads a doubleword into an integer register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.memory().readLong(state.decodedRegister(rs1) + immediate));
            state.setPc(nextPc);
        }
    }

    /// Executes `lbu`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteLbu {
        /// Loads a zero-extended byte into an integer register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.memory().readUnsignedByte(state.decodedRegister(rs1) + immediate));
            state.setPc(nextPc);
        }
    }

    /// Executes `lhu`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteLhu {
        /// Loads a zero-extended halfword into an integer register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.memory().readUnsignedShort(state.decodedRegister(rs1) + immediate));
            state.setPc(nextPc);
        }
    }

    /// Executes `lwu`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteLwu {
        /// Loads a zero-extended word into an integer register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.memory().readUnsignedInt(state.decodedRegister(rs1) + immediate));
            state.setPc(nextPc);
        }
    }

    /// Executes `sb`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteSb {
        /// Stores the low byte of an integer register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rs1, int rs2, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            long storeAddress = state.decodedRegister(rs1) + immediate;
            state.memory().writeByte(storeAddress, (byte) state.decodedRegister(rs2));
            afterDirectStore(state, storeAddress, Byte.BYTES);
            state.clearReservation();
            state.setPc(nextPc);
        }
    }

    /// Executes `sh`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteSh {
        /// Stores the low halfword of an integer register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rs1, int rs2, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            long storeAddress = state.decodedRegister(rs1) + immediate;
            state.memory().writeShort(storeAddress, (short) state.decodedRegister(rs2));
            afterDirectStore(state, storeAddress, Short.BYTES);
            state.clearReservation();
            state.setPc(nextPc);
        }
    }

    /// Executes `sw`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteSw {
        /// Stores the low word of an integer register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rs1, int rs2, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            long storeAddress = state.decodedRegister(rs1) + immediate;
            state.memory().writeInt(storeAddress, (int) state.decodedRegister(rs2));
            afterDirectStore(state, storeAddress, Integer.BYTES);
            state.clearReservation();
            state.setPc(nextPc);
        }
    }

    /// Executes `sd`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteSd {
        /// Stores a doubleword from an integer register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rs1, int rs2, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            long storeAddress = state.decodedRegister(rs1) + immediate;
            state.memory().writeLong(storeAddress, state.decodedRegister(rs2));
            afterDirectStore(state, storeAddress, Long.BYTES);
            state.clearReservation();
            state.setPc(nextPc);
        }
    }

    /// Executes `addi`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteAddi {
        /// Adds a sign-extended immediate to a register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) + immediate);
            state.setPc(nextPc);
        }
    }

    /// Executes `slti`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteSlti {
        /// Writes one when the source is signed-less-than the immediate.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) < immediate ? 1 : 0);
            state.setPc(nextPc);
        }
    }

    /// Executes `sltiu`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteSltiu {
        /// Writes one when the source is unsigned-less-than the immediate.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, Long.compareUnsigned(state.decodedRegister(rs1), immediate) < 0 ? 1 : 0);
            state.setPc(nextPc);
        }
    }

    /// Executes `xori`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteXori {
        /// Xors a register with a sign-extended immediate.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) ^ immediate);
            state.setPc(nextPc);
        }
    }

    /// Executes `ori`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteOri {
        /// Ors a register with a sign-extended immediate.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) | immediate);
            state.setPc(nextPc);
        }
    }

    /// Executes `andi`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteAndi {
        /// Ands a register with a sign-extended immediate.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) & immediate);
            state.setPc(nextPc);
        }
    }

    /// Executes `slli`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteSlli {
        /// Shifts a register left by an immediate amount.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) << immediate);
            state.setPc(nextPc);
        }
    }

    /// Executes `srli`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteSrli {
        /// Shifts a register right logically by an immediate amount.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) >>> immediate);
            state.setPc(nextPc);
        }
    }

    /// Executes `srai`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteSrai {
        /// Shifts a register right arithmetically by an immediate amount.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) >> immediate);
            state.setPc(nextPc);
        }
    }

    /// Executes `addiw`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteAddiw {
        /// Adds an immediate as a 32-bit word and sign-extends the result.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, (int) (state.decodedRegister(rs1) + immediate));
            state.setPc(nextPc);
        }
    }

    /// Executes `slliw`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteSlliw {
        /// Shifts a word left by an immediate amount and sign-extends the result.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) << immediate);
            state.setPc(nextPc);
        }
    }

    /// Executes `srliw`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteSrliw {
        /// Shifts a word right logically by an immediate amount and sign-extends the result.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) >>> immediate);
            state.setPc(nextPc);
        }
    }

    /// Executes `sraiw`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "immediate", type = long.class)
    public static final class ExecuteSraiw {
        /// Shifts a word right arithmetically by an immediate amount and sign-extends the result.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, long immediate, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) >> immediate);
            state.setPc(nextPc);
        }
    }

    /// Executes `add`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteAdd {
        /// Adds two integer registers.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) + state.decodedRegister(rs2));
            state.setPc(nextPc);
        }
    }

    /// Executes `sub`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteSub {
        /// Subtracts two integer registers.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) - state.decodedRegister(rs2));
            state.setPc(nextPc);
        }
    }

    /// Executes `sll`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteSll {
        /// Shifts a register left by the low six bits of another register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) << (state.decodedRegister(rs2) & 0x3f));
            state.setPc(nextPc);
        }
    }

    /// Executes `slt`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteSlt {
        /// Writes one when the first source is signed-less-than the second.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) < state.decodedRegister(rs2) ? 1 : 0);
            state.setPc(nextPc);
        }
    }

    /// Executes `sltu`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteSltu {
        /// Writes one when the first source is unsigned-less-than the second.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, Long.compareUnsigned(state.decodedRegister(rs1), state.decodedRegister(rs2)) < 0 ? 1 : 0);
            state.setPc(nextPc);
        }
    }

    /// Executes `xor`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteXor {
        /// Xors two integer registers.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) ^ state.decodedRegister(rs2));
            state.setPc(nextPc);
        }
    }

    /// Executes `srl`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteSrl {
        /// Shifts a register right logically by the low six bits of another register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) >>> (state.decodedRegister(rs2) & 0x3f));
            state.setPc(nextPc);
        }
    }

    /// Executes `sra`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteSra {
        /// Shifts a register right arithmetically by the low six bits of another register.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) >> (state.decodedRegister(rs2) & 0x3f));
            state.setPc(nextPc);
        }
    }

    /// Executes `or`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteOr {
        /// Ors two integer registers.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) | state.decodedRegister(rs2));
            state.setPc(nextPc);
        }
    }

    /// Executes `and`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteAnd {
        /// Ands two integer registers.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, state.decodedRegister(rs1) & state.decodedRegister(rs2));
            state.setPc(nextPc);
        }
    }

    /// Executes `addw`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteAddw {
        /// Adds two 32-bit words and sign-extends the result.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) + (int) state.decodedRegister(rs2));
            state.setPc(nextPc);
        }
    }

    /// Executes `subw`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteSubw {
        /// Subtracts two 32-bit words and sign-extends the result.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) - (int) state.decodedRegister(rs2));
            state.setPc(nextPc);
        }
    }

    /// Executes `sllw`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteSllw {
        /// Shifts a word left by the low five bits of another register and sign-extends the result.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) << (state.decodedRegister(rs2) & 0x1f));
            state.setPc(nextPc);
        }
    }

    /// Executes `srlw`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteSrlw {
        /// Shifts a word right logically by the low five bits of another register and sign-extends the result.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) >>> (state.decodedRegister(rs2) & 0x1f));
            state.setPc(nextPc);
        }
    }

    /// Executes `sraw`.
    @Operation
    @ConstantOperand(name = "address", type = long.class)
    @ConstantOperand(name = "raw", type = int.class)
    @ConstantOperand(name = "nextPc", type = long.class)
    @ConstantOperand(name = "rd", type = int.class)
    @ConstantOperand(name = "rs1", type = int.class)
    @ConstantOperand(name = "rs2", type = int.class)
    public static final class ExecuteSraw {
        /// Shifts a word right arithmetically by the low five bits of another register and sign-extends the result.
        @Specialization
        static void execute(long address, int raw, long nextPc, int rd, int rs1, int rs2, MachineState state) {
            beginInstruction(state, address, raw);
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) >> (state.decodedRegister(rs2) & 0x1f));
            state.setPc(nextPc);
        }
    }

    /// Executes a control, CSR, ecall, or ebreak instruction.
    @Operation
    @ConstantOperand(type = RiscVInstructionSemantics.class)
    public static final class ExecuteControl {
        /// Applies the decoded instruction to the machine state.
        @Specialization
        static void execute(RiscVInstructionSemantics instruction, MachineState state) {
            instruction.execute(state);
        }
    }

    /// Executes an integer or floating-point store instruction.
    @Operation
    @ConstantOperand(type = RiscVInstructionSemantics.class)
    public static final class ExecuteStore {
        /// Applies the decoded instruction to the machine state.
        @Specialization
        static void execute(RiscVInstructionSemantics instruction, MachineState state) {
            instruction.execute(state);
        }
    }

    /// Executes an RV64M multiply or divide instruction.
    @Operation
    @ConstantOperand(type = RiscVInstructionSemantics.class)
    public static final class ExecuteMultiplyDivide {
        /// Applies the decoded instruction to the machine state.
        @Specialization
        static void execute(RiscVInstructionSemantics instruction, MachineState state) {
            instruction.execute(state);
        }
    }

    /// Executes a floating-point arithmetic, conversion, load, or move instruction.
    @Operation
    @ConstantOperand(type = RiscVInstructionSemantics.class)
    public static final class ExecuteFloatingPoint {
        /// Applies the decoded instruction to the machine state.
        @Specialization
        static void execute(RiscVInstructionSemantics instruction, MachineState state) {
            instruction.execute(state);
        }
    }

    /// Executes an RV64A atomic instruction.
    @Operation
    @ConstantOperand(type = RiscVInstructionSemantics.class)
    public static final class ExecuteAtomic {
        /// Applies the decoded instruction to the machine state.
        @Specialization
        static void execute(RiscVInstructionSemantics instruction, MachineState state) {
            instruction.execute(state);
        }
    }

    /// Reads the next PC materialized in the machine state by the block body.
    @Operation
    public static final class ReadPc {
        /// Returns the current guest program counter.
        @Specialization
        static long read(MachineState state) {
            return state.pc();
        }
    }

    /// Records the instruction PC and retirement before executing an instruction body.
    private static void beginInstruction(MachineState state, long address, int raw) {
        state.setPc(address);
        state.beforeInstruction(address, raw);
    }

    /// Writes the PC selected by a conditional branch.
    private static void branch(MachineState state, boolean taken, long address, long immediate, long nextPc) {
        state.setPc(taken ? address + immediate : nextPc);
    }

    /// Applies optional simulator side effects after a direct store.
    private static void afterDirectStore(MachineState state, long address, int length) {
        if (state.hasStoreSideEffects()) {
            state.afterStoreWithSideEffects(address, length);
        }
    }

}
