// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.nodes;

import com.oracle.truffle.api.RootCallTarget;
import org.glavo.riscv.RiscVLanguage;
import org.glavo.riscv.constants.RiscVMicroOpcode;
import org.glavo.riscv.parser.DecodedBlock;
import org.glavo.riscv.parser.DecodedInstruction;
import org.glavo.riscv.parser.RiscVOperation;
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
        RiscVMicroBlockRootNode root = new RiscVMicroBlockRootNode(language, compileNode(block));
        return root.getCallTarget();
    }

    /// Builds an executable micro-bytecode block node for direct embedding in a root.
    static RiscVMicroBlockNode compileNode(DecodedBlock block) {
        DecodedInstruction @Unmodifiable [] instructions = block.instructions();
        int instructionCount = instructions.length;
        byte[] opcodes = new byte[instructionCount];
        RiscVOperation[] operations = new RiscVOperation[instructionCount];
        int[] operands = new int[instructionCount];
        int[] raws = new int[instructionCount];
        long[] addresses = new long[instructionCount];
        long[] nextPcs = new long[instructionCount];
        long[] immediates = new long[instructionCount];
        boolean requiresPreciseFastState = false;

        for (int index = 0; index < instructionCount; index++) {
            DecodedInstruction instruction = instructions[index];
            operations[index] = instruction.operation();
            opcodes[index] = opcode(operations[index]);
            requiresPreciseFastState |= requiresPreciseFastState(opcodes[index]);
            operands[index] = RiscVMicroBlockNode.packRegisters(instruction.rd(), instruction.rs1(), instruction.rs2());
            raws[index] = instruction.raw();
            addresses[index] = instruction.address();
            nextPcs[index] = instruction.nextAddress();
            immediates[index] = instruction.immediate();
        }

        return new RiscVMicroBlockNode(
                opcodes,
                operations,
                operands,
                raws,
                addresses,
                nextPcs,
                immediates,
                block.fallThroughPc(),
                block.endsWithTerminator(),
                requiresPreciseFastState);
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
            case ECALL -> RiscVMicroOpcode.ECALL;
            case EBREAK -> RiscVMicroOpcode.EBREAK;
            case CSRRW -> RiscVMicroOpcode.CSRRW;
            case CSRRS -> RiscVMicroOpcode.CSRRS;
            case CSRRC -> RiscVMicroOpcode.CSRRC;
            case CSRRWI -> RiscVMicroOpcode.CSRRWI;
            case CSRRSI -> RiscVMicroOpcode.CSRRSI;
            case CSRRCI -> RiscVMicroOpcode.CSRRCI;
            case MUL -> RiscVMicroOpcode.MUL;
            case MULH -> RiscVMicroOpcode.MULH;
            case MULHSU -> RiscVMicroOpcode.MULHSU;
            case MULHU -> RiscVMicroOpcode.MULHU;
            case DIV -> RiscVMicroOpcode.DIV;
            case DIVU -> RiscVMicroOpcode.DIVU;
            case REM -> RiscVMicroOpcode.REM;
            case REMU -> RiscVMicroOpcode.REMU;
            case MULW -> RiscVMicroOpcode.MULW;
            case DIVW -> RiscVMicroOpcode.DIVW;
            case DIVUW -> RiscVMicroOpcode.DIVUW;
            case REMW -> RiscVMicroOpcode.REMW;
            case REMUW -> RiscVMicroOpcode.REMUW;
            case FLW -> RiscVMicroOpcode.FLW;
            case FLD -> RiscVMicroOpcode.FLD;
            case FSW -> RiscVMicroOpcode.FSW;
            case FSD -> RiscVMicroOpcode.FSD;
            case FSGNJ -> RiscVMicroOpcode.FSGNJ;
            case FSGNJN -> RiscVMicroOpcode.FSGNJN;
            case FSGNJX -> RiscVMicroOpcode.FSGNJX;
            case FCLASS -> RiscVMicroOpcode.FCLASS;
            case FMV_X_FP -> RiscVMicroOpcode.FMV_X_FP;
            case FMV_FP_X -> RiscVMicroOpcode.FMV_FP_X;
            case FEQ -> RiscVMicroOpcode.FEQ;
            case FLT -> RiscVMicroOpcode.FLT;
            case FLE -> RiscVMicroOpcode.FLE;
            case LR_W -> RiscVMicroOpcode.LR_W;
            case LR_D -> RiscVMicroOpcode.LR_D;
            case SC_W -> RiscVMicroOpcode.SC_W;
            case SC_D -> RiscVMicroOpcode.SC_D;
            case AMOSWAP_W -> RiscVMicroOpcode.AMOSWAP_W;
            case AMOADD_W -> RiscVMicroOpcode.AMOADD_W;
            case AMOXOR_W -> RiscVMicroOpcode.AMOXOR_W;
            case AMOAND_W -> RiscVMicroOpcode.AMOAND_W;
            case AMOOR_W -> RiscVMicroOpcode.AMOOR_W;
            case AMOMIN_W -> RiscVMicroOpcode.AMOMIN_W;
            case AMOMAX_W -> RiscVMicroOpcode.AMOMAX_W;
            case AMOMINU_W -> RiscVMicroOpcode.AMOMINU_W;
            case AMOMAXU_W -> RiscVMicroOpcode.AMOMAXU_W;
            case AMOSWAP_D -> RiscVMicroOpcode.AMOSWAP_D;
            case AMOADD_D -> RiscVMicroOpcode.AMOADD_D;
            case AMOXOR_D -> RiscVMicroOpcode.AMOXOR_D;
            case AMOAND_D -> RiscVMicroOpcode.AMOAND_D;
            case AMOOR_D -> RiscVMicroOpcode.AMOOR_D;
            case AMOMIN_D -> RiscVMicroOpcode.AMOMIN_D;
            case AMOMAX_D -> RiscVMicroOpcode.AMOMAX_D;
            case AMOMINU_D -> RiscVMicroOpcode.AMOMINU_D;
            case AMOMAXU_D -> RiscVMicroOpcode.AMOMAXU_D;
            default -> RiscVMicroOpcode.EXECUTE_OPERATION;
        };
    }

    /// Returns true when an opcode needs exact per-instruction fast-path state updates.
    private static boolean requiresPreciseFastState(byte opcode) {
        return switch (opcode) {
            case RiscVMicroOpcode.EXECUTE_OPERATION,
                    RiscVMicroOpcode.ECALL,
                    RiscVMicroOpcode.EBREAK,
                    RiscVMicroOpcode.CSRRW,
                    RiscVMicroOpcode.CSRRS,
                    RiscVMicroOpcode.CSRRC,
                    RiscVMicroOpcode.CSRRWI,
                    RiscVMicroOpcode.CSRRSI,
                    RiscVMicroOpcode.CSRRCI -> true;
            default -> false;
        };
    }
}
