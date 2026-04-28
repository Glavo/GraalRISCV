package org.glavo.riscv;

import com.oracle.truffle.api.RootCallTarget;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Compiles decoded RISC-V basic blocks into custom micro-bytecode call targets.
@NotNullByDefault
final class RiscVMicroBlockCompiler {
    /// Prevents construction of this utility class.
    private RiscVMicroBlockCompiler() {
    }

    /// Builds a Truffle call target backed by the custom micro-bytecode interpreter.
    static RootCallTarget compile(RiscVLanguage language, DecodedBlock block) {
        DecodedInstruction @Unmodifiable [] instructions = block.instructions();
        int instructionCount = instructions.length;
        byte[] opcodes = new byte[instructionCount];
        RiscVOperation[] operations = new RiscVOperation[instructionCount];
        int[] operands = new int[instructionCount];
        int[] raws = new int[instructionCount];
        long[] addresses = new long[instructionCount];
        long[] nextPcs = new long[instructionCount];
        long[] immediates = new long[instructionCount];

        for (int index = 0; index < instructionCount; index++) {
            DecodedInstruction instruction = instructions[index];
            operations[index] = instruction.operation();
            opcodes[index] = opcode(operations[index]);
            operands[index] = RiscVMicroBlockRootNode.packRegisters(instruction.rd(), instruction.rs1(), instruction.rs2());
            raws[index] = instruction.raw();
            addresses[index] = instruction.address();
            nextPcs[index] = instruction.nextAddress();
            immediates[index] = instruction.immediate();
        }

        RiscVMicroBlockRootNode root = new RiscVMicroBlockRootNode(
                language,
                opcodes,
                operations,
                operands,
                raws,
                addresses,
                nextPcs,
                immediates);
        return root.getCallTarget();
    }

    /// Converts a decoded operation to a micro-bytecode opcode.
    private static byte opcode(RiscVOperation operation) {
        return switch (operation) {
            case NOP, FENCE, FENCE_I -> RiscVMicroOpcode.ADVANCE_PC;
            case LUI -> RiscVMicroOpcode.LUI;
            case AUIPC -> RiscVMicroOpcode.AUIPC;
            case JAL -> RiscVMicroOpcode.JAL;
            case JALR -> RiscVMicroOpcode.JALR;
            case BEQ -> RiscVMicroOpcode.BEQ;
            case BNE -> RiscVMicroOpcode.BNE;
            case BLT -> RiscVMicroOpcode.BLT;
            case BGE -> RiscVMicroOpcode.BGE;
            case BLTU -> RiscVMicroOpcode.BLTU;
            case BGEU -> RiscVMicroOpcode.BGEU;
            case LB -> RiscVMicroOpcode.LB;
            case LH -> RiscVMicroOpcode.LH;
            case LW -> RiscVMicroOpcode.LW;
            case LD -> RiscVMicroOpcode.LD;
            case LBU -> RiscVMicroOpcode.LBU;
            case LHU -> RiscVMicroOpcode.LHU;
            case LWU -> RiscVMicroOpcode.LWU;
            case SB -> RiscVMicroOpcode.SB;
            case SH -> RiscVMicroOpcode.SH;
            case SW -> RiscVMicroOpcode.SW;
            case SD -> RiscVMicroOpcode.SD;
            case ADDI -> RiscVMicroOpcode.ADDI;
            case SLTI -> RiscVMicroOpcode.SLTI;
            case SLTIU -> RiscVMicroOpcode.SLTIU;
            case XORI -> RiscVMicroOpcode.XORI;
            case ORI -> RiscVMicroOpcode.ORI;
            case ANDI -> RiscVMicroOpcode.ANDI;
            case SLLI -> RiscVMicroOpcode.SLLI;
            case SRLI -> RiscVMicroOpcode.SRLI;
            case SRAI -> RiscVMicroOpcode.SRAI;
            case ADDIW -> RiscVMicroOpcode.ADDIW;
            case SLLIW -> RiscVMicroOpcode.SLLIW;
            case SRLIW -> RiscVMicroOpcode.SRLIW;
            case SRAIW -> RiscVMicroOpcode.SRAIW;
            case ADD -> RiscVMicroOpcode.ADD;
            case SUB -> RiscVMicroOpcode.SUB;
            case SLL -> RiscVMicroOpcode.SLL;
            case SLT -> RiscVMicroOpcode.SLT;
            case SLTU -> RiscVMicroOpcode.SLTU;
            case XOR -> RiscVMicroOpcode.XOR;
            case SRL -> RiscVMicroOpcode.SRL;
            case SRA -> RiscVMicroOpcode.SRA;
            case OR -> RiscVMicroOpcode.OR;
            case AND -> RiscVMicroOpcode.AND;
            case ADDW -> RiscVMicroOpcode.ADDW;
            case SUBW -> RiscVMicroOpcode.SUBW;
            case SLLW -> RiscVMicroOpcode.SLLW;
            case SRLW -> RiscVMicroOpcode.SRLW;
            case SRAW -> RiscVMicroOpcode.SRAW;
            default -> RiscVMicroOpcode.EXECUTE_OPERATION;
        };
    }
}
