package org.glavo.riscv;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Executes one decoded RISC-V basic block through the custom micro-bytecode interpreter.
@NotNullByDefault
final class RiscVMicroBlockRootNode extends RootNode {
    /// Five-bit mask for one packed RISC-V register index.
    private static final int REGISTER_MASK = 0x1f;

    /// Bit shift for the first source register in a packed operand.
    private static final int RS1_SHIFT = 5;

    /// Bit shift for the second source register in a packed operand.
    private static final int RS2_SHIFT = 10;

    /// The compact opcode stream for this block.
    @CompilationFinal(dimensions = 1)
    private final byte @Unmodifiable [] opcodes;

    /// Packed register operands or semantic indexes for each opcode.
    @CompilationFinal(dimensions = 1)
    private final int @Unmodifiable [] operands;

    /// Original raw instruction bits for direct opcodes.
    @CompilationFinal(dimensions = 1)
    private final int @Unmodifiable [] raws;

    /// Guest instruction addresses for direct opcodes.
    @CompilationFinal(dimensions = 1)
    private final long @Unmodifiable [] addresses;

    /// Sequential PCs for direct opcodes.
    @CompilationFinal(dimensions = 1)
    private final long @Unmodifiable [] nextPcs;

    /// Immediate operands for direct opcodes.
    @CompilationFinal(dimensions = 1)
    private final long @Unmodifiable [] immediates;

    /// Semantic helpers used by low-frequency or complex micro-ops.
    @CompilationFinal(dimensions = 1)
    private final RiscVInstructionSemantics @Unmodifiable [] semantics;

    /// Creates a micro-bytecode block root.
    RiscVMicroBlockRootNode(
            RiscVLanguage language,
            byte @Unmodifiable [] opcodes,
            int @Unmodifiable [] operands,
            int @Unmodifiable [] raws,
            long @Unmodifiable [] addresses,
            long @Unmodifiable [] nextPcs,
            long @Unmodifiable [] immediates,
            RiscVInstructionSemantics @Unmodifiable [] semantics) {
        super(language);
        this.opcodes = opcodes.clone();
        this.operands = operands.clone();
        this.raws = raws.clone();
        this.addresses = addresses.clone();
        this.nextPcs = nextPcs.clone();
        this.immediates = immediates.clone();
        this.semantics = semantics.clone();
    }

    /// Executes the block with the `MachineState` supplied as the first argument.
    @Override
    public Object execute(VirtualFrame frame) {
        return executeBlock((MachineState) frame.getArguments()[0]);
    }

    /// Runs all micro-ops in this block and returns the materialized next PC.
    @ExplodeLoop
    private long executeBlock(MachineState state) {
        for (int index = 0; index < opcodes.length; index++) {
            executeInstruction(state, index);
        }
        return state.pc();
    }

    /// Executes one micro-op by decoding its packed operand.
    private void executeInstruction(MachineState state, int index) {
        byte opcode = opcodes[index];
        int operand = operands[index];
        switch (opcode) {
            case RiscVMicroOpcode.EXECUTE_SEMANTICS -> semantics[operand].execute(state);
            case RiscVMicroOpcode.ADVANCE_PC -> {
                beginInstruction(state, index);
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LUI -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), immediates[index]);
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.AUIPC -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), addresses[index] + immediates[index]);
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.JAL -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), nextPcs[index]);
                state.setPc(addresses[index] + immediates[index]);
            }
            case RiscVMicroOpcode.JALR -> {
                beginInstruction(state, index);
                long target = (state.decodedRegister(rs1(operand)) + immediates[index]) & ~1L;
                state.setDecodedRegister(rd(operand), nextPcs[index]);
                state.setPc(target);
            }
            case RiscVMicroOpcode.BEQ -> {
                beginInstruction(state, index);
                branch(state, state.decodedRegister(rs1(operand)) == state.decodedRegister(rs2(operand)), index);
            }
            case RiscVMicroOpcode.BNE -> {
                beginInstruction(state, index);
                branch(state, state.decodedRegister(rs1(operand)) != state.decodedRegister(rs2(operand)), index);
            }
            case RiscVMicroOpcode.BLT -> {
                beginInstruction(state, index);
                branch(state, state.decodedRegister(rs1(operand)) < state.decodedRegister(rs2(operand)), index);
            }
            case RiscVMicroOpcode.BGE -> {
                beginInstruction(state, index);
                branch(state, state.decodedRegister(rs1(operand)) >= state.decodedRegister(rs2(operand)), index);
            }
            case RiscVMicroOpcode.BLTU -> {
                beginInstruction(state, index);
                branch(state, Long.compareUnsigned(state.decodedRegister(rs1(operand)), state.decodedRegister(rs2(operand))) < 0, index);
            }
            case RiscVMicroOpcode.BGEU -> {
                beginInstruction(state, index);
                branch(state, Long.compareUnsigned(state.decodedRegister(rs1(operand)), state.decodedRegister(rs2(operand))) >= 0, index);
            }
            case RiscVMicroOpcode.LB -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readByte(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LH -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readShort(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LW -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readInt(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LD -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readLong(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LBU -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readUnsignedByte(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LHU -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readUnsignedShort(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LWU -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readUnsignedInt(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.SB -> {
                beginInstruction(state, index);
                long address = loadAddress(state, operand, index);
                state.memory().writeByte(address, (byte) state.decodedRegister(rs2(operand)));
                afterStore(state, address, Byte.BYTES);
                state.clearReservation();
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.SH -> {
                beginInstruction(state, index);
                long address = loadAddress(state, operand, index);
                state.memory().writeShort(address, (short) state.decodedRegister(rs2(operand)));
                afterStore(state, address, Short.BYTES);
                state.clearReservation();
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.SW -> {
                beginInstruction(state, index);
                long address = loadAddress(state, operand, index);
                state.memory().writeInt(address, (int) state.decodedRegister(rs2(operand)));
                afterStore(state, address, Integer.BYTES);
                state.clearReservation();
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.SD -> {
                beginInstruction(state, index);
                long address = loadAddress(state, operand, index);
                state.memory().writeLong(address, state.decodedRegister(rs2(operand)));
                afterStore(state, address, Long.BYTES);
                state.clearReservation();
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.ADDI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) + immediates[index]);
            case RiscVMicroOpcode.SLTI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) < immediates[index] ? 1 : 0);
            case RiscVMicroOpcode.SLTIU -> binaryImmediate(state, index, operand, Long.compareUnsigned(state.decodedRegister(rs1(operand)), immediates[index]) < 0 ? 1 : 0);
            case RiscVMicroOpcode.XORI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) ^ immediates[index]);
            case RiscVMicroOpcode.ORI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) | immediates[index]);
            case RiscVMicroOpcode.ANDI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) & immediates[index]);
            case RiscVMicroOpcode.SLLI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) << immediates[index]);
            case RiscVMicroOpcode.SRLI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) >>> immediates[index]);
            case RiscVMicroOpcode.SRAI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) >> immediates[index]);
            case RiscVMicroOpcode.ADDIW -> binaryImmediate(state, index, operand, (int) (state.decodedRegister(rs1(operand)) + immediates[index]));
            case RiscVMicroOpcode.SLLIW -> binaryImmediate(state, index, operand, (int) state.decodedRegister(rs1(operand)) << immediates[index]);
            case RiscVMicroOpcode.SRLIW -> binaryImmediate(state, index, operand, (int) state.decodedRegister(rs1(operand)) >>> immediates[index]);
            case RiscVMicroOpcode.SRAIW -> binaryImmediate(state, index, operand, (int) state.decodedRegister(rs1(operand)) >> immediates[index]);
            case RiscVMicroOpcode.ADD -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) + state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.SUB -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) - state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.SLL -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) << (state.decodedRegister(rs2(operand)) & 0x3f));
            case RiscVMicroOpcode.SLT -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) < state.decodedRegister(rs2(operand)) ? 1 : 0);
            case RiscVMicroOpcode.SLTU -> binaryRegister(state, index, operand, Long.compareUnsigned(state.decodedRegister(rs1(operand)), state.decodedRegister(rs2(operand))) < 0 ? 1 : 0);
            case RiscVMicroOpcode.XOR -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) ^ state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.SRL -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) >>> (state.decodedRegister(rs2(operand)) & 0x3f));
            case RiscVMicroOpcode.SRA -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) >> (state.decodedRegister(rs2(operand)) & 0x3f));
            case RiscVMicroOpcode.OR -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) | state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.AND -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) & state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.ADDW -> binaryRegister(state, index, operand, (int) state.decodedRegister(rs1(operand)) + (int) state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.SUBW -> binaryRegister(state, index, operand, (int) state.decodedRegister(rs1(operand)) - (int) state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.SLLW -> binaryRegister(state, index, operand, (int) state.decodedRegister(rs1(operand)) << (state.decodedRegister(rs2(operand)) & 0x1f));
            case RiscVMicroOpcode.SRLW -> binaryRegister(state, index, operand, (int) state.decodedRegister(rs1(operand)) >>> (state.decodedRegister(rs2(operand)) & 0x1f));
            case RiscVMicroOpcode.SRAW -> binaryRegister(state, index, operand, (int) state.decodedRegister(rs1(operand)) >> (state.decodedRegister(rs2(operand)) & 0x1f));
            default -> throw new RiscVException("Unknown micro-bytecode opcode: " + opcode);
        }
    }

    /// Records the current instruction before running a direct micro-op body.
    private void beginInstruction(MachineState state, int index) {
        long address = addresses[index];
        state.setPc(address);
        state.beforeInstruction(address, raws[index]);
    }

    /// Writes a conditional branch target.
    private void branch(MachineState state, boolean taken, int index) {
        state.setPc(taken ? addresses[index] + immediates[index] : nextPcs[index]);
    }

    /// Computes a memory address from `rs1` and an immediate.
    private long loadAddress(MachineState state, int operand, int index) {
        return state.decodedRegister(rs1(operand)) + immediates[index];
    }

    /// Writes the result of an immediate integer operation.
    private void binaryImmediate(MachineState state, int index, int operand, long value) {
        beginInstruction(state, index);
        state.setDecodedRegister(rd(operand), value);
        state.setPc(nextPcs[index]);
    }

    /// Writes the result of a register integer operation.
    private void binaryRegister(MachineState state, int index, int operand, long value) {
        beginInstruction(state, index);
        state.setDecodedRegister(rd(operand), value);
        state.setPc(nextPcs[index]);
    }

    /// Handles optional simulator side effects after a store.
    private static void afterStore(MachineState state, long address, int length) {
        if (state.hasStoreSideEffects()) {
            state.afterStoreWithSideEffects(address, length);
        }
    }

    /// Packs decoded register indexes into one integer operand.
    static int packRegisters(int rd, int rs1, int rs2) {
        return rd | (rs1 << RS1_SHIFT) | (rs2 << RS2_SHIFT);
    }

    /// Extracts the destination register from a packed operand.
    private static int rd(int operand) {
        return operand & REGISTER_MASK;
    }

    /// Extracts the first source register from a packed operand.
    private static int rs1(int operand) {
        return (operand >>> RS1_SHIFT) & REGISTER_MASK;
    }

    /// Extracts the second source register from a packed operand.
    private static int rs2(int operand) {
        return (operand >>> RS2_SHIFT) & REGISTER_MASK;
    }
}
