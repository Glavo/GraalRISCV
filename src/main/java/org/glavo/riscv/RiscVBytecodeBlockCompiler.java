package org.glavo.riscv;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import org.jetbrains.annotations.NotNullByDefault;

/// Compiles decoded RISC-V basic blocks into Bytecode DSL call targets.
@NotNullByDefault
final class RiscVBytecodeBlockCompiler {
    /// Prevents construction of this utility class.
    private RiscVBytecodeBlockCompiler() {
    }

    /// Builds a Bytecode DSL call target for a decoded block.
    static RootCallTarget compile(RiscVLanguage language, DecodedBlock block) {
        RiscVBytecodeBlockRootNode root = RiscVBytecodeBlockRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> emitBlock(builder, block))
                .getNode(0);
        return root.getCallTarget();
    }

    /// Emits every decoded instruction and a final `pc` return.
    private static void emitBlock(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedBlock block) {
        builder.beginRoot();
        for (DecodedInstruction instruction : block.instructions()) {
            emitInstruction(builder, instruction);
        }
        builder.beginReturn();
        builder.beginReadPc();
        builder.emitLoadArgument(0);
        builder.endReadPc();
        builder.endReturn();
        builder.endRoot();
    }

    /// Emits one instruction through the operation family matching its decoded operation.
    private static void emitInstruction(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        RiscVInstructionSemantics instruction = RiscVInstructionSemantics.create(decoded);
        switch (decoded.operation()) {
            case NOP, FENCE, FENCE_I, LUI, AUIPC, JAL, JALR, BEQ, BNE, BLT, BGE, BLTU, BGEU,
                    CSRRW, CSRRS, CSRRC, CSRRWI, CSRRSI, CSRRCI, ECALL, EBREAK -> {
                builder.beginExecuteControl(instruction);
                builder.emitLoadArgument(0);
                builder.endExecuteControl();
            }
            case LB, LH, LW, LD, LBU, LHU, LWU -> {
                builder.beginExecuteLoad(instruction);
                builder.emitLoadArgument(0);
                builder.endExecuteLoad();
            }
            case SB, SH, SW, SD, FSW, FSD -> {
                builder.beginExecuteStore(instruction);
                builder.emitLoadArgument(0);
                builder.endExecuteStore();
            }
            case ADDI, SLTI, SLTIU, XORI, ORI, ANDI, SLLI, SRLI, SRAI, ADDIW, SLLIW, SRLIW, SRAIW,
                    ADD, SUB, SLL, SLT, SLTU, XOR, SRL, SRA, OR, AND, ADDW, SUBW, SLLW, SRLW, SRAW -> {
                builder.beginExecuteInteger(instruction);
                builder.emitLoadArgument(0);
                builder.endExecuteInteger();
            }
            case MUL, MULH, MULHSU, MULHU, DIV, DIVU, REM, REMU, MULW, DIVW, DIVUW, REMW, REMUW -> {
                builder.beginExecuteMultiplyDivide(instruction);
                builder.emitLoadArgument(0);
                builder.endExecuteMultiplyDivide();
            }
            case FLW, FLD, FMADD, FMSUB, FNMSUB, FNMADD, FADD, FSUB, FMUL, FDIV, FSQRT, FSGNJ, FSGNJN,
                    FSGNJX, FMIN, FMAX, FCVT_S_D, FCVT_D_S, FEQ, FLT, FLE, FCLASS, FCVT_INT_FP,
                    FCVT_FP_INT, FMV_X_FP, FMV_FP_X -> {
                builder.beginExecuteFloatingPoint(instruction);
                builder.emitLoadArgument(0);
                builder.endExecuteFloatingPoint();
            }
            case LR_W, LR_D, SC_W, SC_D, AMOSWAP_W, AMOADD_W, AMOXOR_W, AMOAND_W, AMOOR_W,
                    AMOMIN_W, AMOMAX_W, AMOMINU_W, AMOMAXU_W, AMOSWAP_D, AMOADD_D, AMOXOR_D,
                    AMOAND_D, AMOOR_D, AMOMIN_D, AMOMAX_D, AMOMINU_D, AMOMAXU_D -> {
                builder.beginExecuteAtomic(instruction);
                builder.emitLoadArgument(0);
                builder.endExecuteAtomic();
            }
        }
    }
}
