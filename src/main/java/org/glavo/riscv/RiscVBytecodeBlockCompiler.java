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
        switch (decoded.operation()) {
            case NOP, FENCE, FENCE_I -> emitAdvancePc(builder, decoded);
            case LUI -> {
                builder.beginExecuteLui(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.immediate());
                builder.emitLoadArgument(0);
                builder.endExecuteLui();
            }
            case AUIPC -> {
                builder.beginExecuteAuipc(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.immediate());
                builder.emitLoadArgument(0);
                builder.endExecuteAuipc();
            }
            case JAL -> {
                builder.beginExecuteJal(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.immediate());
                builder.emitLoadArgument(0);
                builder.endExecuteJal();
            }
            case JALR -> {
                builder.beginExecuteJalr(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
                builder.emitLoadArgument(0);
                builder.endExecuteJalr();
            }
            case BEQ -> {
                builder.beginExecuteBeq(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rs1(), decoded.rs2(), decoded.immediate());
                builder.emitLoadArgument(0);
                builder.endExecuteBeq();
            }
            case BNE -> {
                builder.beginExecuteBne(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rs1(), decoded.rs2(), decoded.immediate());
                builder.emitLoadArgument(0);
                builder.endExecuteBne();
            }
            case BLT -> {
                builder.beginExecuteBlt(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rs1(), decoded.rs2(), decoded.immediate());
                builder.emitLoadArgument(0);
                builder.endExecuteBlt();
            }
            case BGE -> {
                builder.beginExecuteBge(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rs1(), decoded.rs2(), decoded.immediate());
                builder.emitLoadArgument(0);
                builder.endExecuteBge();
            }
            case BLTU -> {
                builder.beginExecuteBltu(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rs1(), decoded.rs2(), decoded.immediate());
                builder.emitLoadArgument(0);
                builder.endExecuteBltu();
            }
            case BGEU -> {
                builder.beginExecuteBgeu(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rs1(), decoded.rs2(), decoded.immediate());
                builder.emitLoadArgument(0);
                builder.endExecuteBgeu();
            }
            case CSRRW, CSRRS, CSRRC, CSRRWI, CSRRSI, CSRRCI, ECALL, EBREAK -> {
                RiscVInstructionSemantics instruction = RiscVInstructionSemantics.create(decoded);
                builder.beginExecuteControl(instruction);
                builder.emitLoadArgument(0);
                builder.endExecuteControl();
            }
            case LB -> emitLoadByte(builder, decoded);
            case LH -> emitLoadHalfword(builder, decoded);
            case LW -> emitLoadWord(builder, decoded);
            case LD -> emitLoadDoubleword(builder, decoded);
            case LBU -> emitLoadUnsignedByte(builder, decoded);
            case LHU -> emitLoadUnsignedHalfword(builder, decoded);
            case LWU -> emitLoadUnsignedWord(builder, decoded);
            case SB -> emitStoreByte(builder, decoded);
            case SH -> emitStoreHalfword(builder, decoded);
            case SW -> emitStoreWord(builder, decoded);
            case SD -> emitStoreDoubleword(builder, decoded);
            case FSW, FSD -> {
                RiscVInstructionSemantics instruction = RiscVInstructionSemantics.create(decoded);
                builder.beginExecuteStore(instruction);
                builder.emitLoadArgument(0);
                builder.endExecuteStore();
            }
            case ADDI -> emitAddImmediate(builder, decoded);
            case SLTI -> emitSetLessThanImmediate(builder, decoded);
            case SLTIU -> emitSetLessThanImmediateUnsigned(builder, decoded);
            case XORI -> emitXorImmediate(builder, decoded);
            case ORI -> emitOrImmediate(builder, decoded);
            case ANDI -> emitAndImmediate(builder, decoded);
            case SLLI -> emitShiftLeftImmediate(builder, decoded);
            case SRLI -> emitShiftRightImmediate(builder, decoded);
            case SRAI -> emitShiftRightArithmeticImmediate(builder, decoded);
            case ADDIW -> emitAddImmediateWord(builder, decoded);
            case SLLIW -> emitShiftLeftImmediateWord(builder, decoded);
            case SRLIW -> emitShiftRightImmediateWord(builder, decoded);
            case SRAIW -> emitShiftRightArithmeticImmediateWord(builder, decoded);
            case ADD -> emitAdd(builder, decoded);
            case SUB -> emitSubtract(builder, decoded);
            case SLL -> emitShiftLeft(builder, decoded);
            case SLT -> emitSetLessThan(builder, decoded);
            case SLTU -> emitSetLessThanUnsigned(builder, decoded);
            case XOR -> emitXor(builder, decoded);
            case SRL -> emitShiftRight(builder, decoded);
            case SRA -> emitShiftRightArithmetic(builder, decoded);
            case OR -> emitOr(builder, decoded);
            case AND -> emitAnd(builder, decoded);
            case ADDW -> emitAddWord(builder, decoded);
            case SUBW -> emitSubtractWord(builder, decoded);
            case SLLW -> emitShiftLeftWord(builder, decoded);
            case SRLW -> emitShiftRightWord(builder, decoded);
            case SRAW -> emitShiftRightArithmeticWord(builder, decoded);
            case MUL, MULH, MULHSU, MULHU, DIV, DIVU, REM, REMU, MULW, DIVW, DIVUW, REMW, REMUW -> {
                RiscVInstructionSemantics instruction = RiscVInstructionSemantics.create(decoded);
                builder.beginExecuteMultiplyDivide(instruction);
                builder.emitLoadArgument(0);
                builder.endExecuteMultiplyDivide();
            }
            case FLW, FLD, FMADD, FMSUB, FNMSUB, FNMADD, FADD, FSUB, FMUL, FDIV, FSQRT, FSGNJ, FSGNJN,
                    FSGNJX, FMIN, FMAX, FCVT_S_D, FCVT_D_S, FEQ, FLT, FLE, FCLASS, FCVT_INT_FP,
                    FCVT_FP_INT, FMV_X_FP, FMV_FP_X -> {
                RiscVInstructionSemantics instruction = RiscVInstructionSemantics.create(decoded);
                builder.beginExecuteFloatingPoint(instruction);
                builder.emitLoadArgument(0);
                builder.endExecuteFloatingPoint();
            }
            case LR_W, LR_D, SC_W, SC_D, AMOSWAP_W, AMOADD_W, AMOXOR_W, AMOAND_W, AMOOR_W,
                    AMOMIN_W, AMOMAX_W, AMOMINU_W, AMOMAXU_W, AMOSWAP_D, AMOADD_D, AMOXOR_D,
                    AMOAND_D, AMOOR_D, AMOMIN_D, AMOMAX_D, AMOMINU_D, AMOMAXU_D -> {
                RiscVInstructionSemantics instruction = RiscVInstructionSemantics.create(decoded);
                builder.beginExecuteAtomic(instruction);
                builder.emitLoadArgument(0);
                builder.endExecuteAtomic();
            }
        }
    }

    /// Emits the shared no-op PC advance operation.
    private static void emitAdvancePc(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteAdvancePc(decoded.address(), decoded.raw(), decoded.nextAddress());
        builder.emitLoadArgument(0);
        builder.endExecuteAdvancePc();
    }

    /// Emits `lb`.
    private static void emitLoadByte(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteLb(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteLb();
    }

    /// Emits `lh`.
    private static void emitLoadHalfword(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteLh(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteLh();
    }

    /// Emits `lw`.
    private static void emitLoadWord(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteLw(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteLw();
    }

    /// Emits `ld`.
    private static void emitLoadDoubleword(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteLd(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteLd();
    }

    /// Emits `lbu`.
    private static void emitLoadUnsignedByte(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteLbu(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteLbu();
    }

    /// Emits `lhu`.
    private static void emitLoadUnsignedHalfword(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteLhu(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteLhu();
    }

    /// Emits `lwu`.
    private static void emitLoadUnsignedWord(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteLwu(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteLwu();
    }

    /// Emits `sb`.
    private static void emitStoreByte(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSb(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rs1(), decoded.rs2(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteSb();
    }

    /// Emits `sh`.
    private static void emitStoreHalfword(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSh(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rs1(), decoded.rs2(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteSh();
    }

    /// Emits `sw`.
    private static void emitStoreWord(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSw(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rs1(), decoded.rs2(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteSw();
    }

    /// Emits `sd`.
    private static void emitStoreDoubleword(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSd(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rs1(), decoded.rs2(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteSd();
    }

    /// Emits `addi`.
    private static void emitAddImmediate(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteAddi(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteAddi();
    }

    /// Emits `slti`.
    private static void emitSetLessThanImmediate(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSlti(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteSlti();
    }

    /// Emits `sltiu`.
    private static void emitSetLessThanImmediateUnsigned(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSltiu(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteSltiu();
    }

    /// Emits `xori`.
    private static void emitXorImmediate(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteXori(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteXori();
    }

    /// Emits `ori`.
    private static void emitOrImmediate(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteOri(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteOri();
    }

    /// Emits `andi`.
    private static void emitAndImmediate(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteAndi(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteAndi();
    }

    /// Emits `slli`.
    private static void emitShiftLeftImmediate(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSlli(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteSlli();
    }

    /// Emits `srli`.
    private static void emitShiftRightImmediate(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSrli(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteSrli();
    }

    /// Emits `srai`.
    private static void emitShiftRightArithmeticImmediate(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSrai(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteSrai();
    }

    /// Emits `addiw`.
    private static void emitAddImmediateWord(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteAddiw(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteAddiw();
    }

    /// Emits `slliw`.
    private static void emitShiftLeftImmediateWord(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSlliw(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteSlliw();
    }

    /// Emits `srliw`.
    private static void emitShiftRightImmediateWord(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSrliw(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteSrliw();
    }

    /// Emits `sraiw`.
    private static void emitShiftRightArithmeticImmediateWord(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSraiw(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.immediate());
        builder.emitLoadArgument(0);
        builder.endExecuteSraiw();
    }

    /// Emits `add`.
    private static void emitAdd(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteAdd(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteAdd();
    }

    /// Emits `sub`.
    private static void emitSubtract(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSub(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteSub();
    }

    /// Emits `sll`.
    private static void emitShiftLeft(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSll(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteSll();
    }

    /// Emits `slt`.
    private static void emitSetLessThan(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSlt(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteSlt();
    }

    /// Emits `sltu`.
    private static void emitSetLessThanUnsigned(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSltu(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteSltu();
    }

    /// Emits `xor`.
    private static void emitXor(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteXor(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteXor();
    }

    /// Emits `srl`.
    private static void emitShiftRight(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSrl(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteSrl();
    }

    /// Emits `sra`.
    private static void emitShiftRightArithmetic(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSra(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteSra();
    }

    /// Emits `or`.
    private static void emitOr(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteOr(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteOr();
    }

    /// Emits `and`.
    private static void emitAnd(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteAnd(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteAnd();
    }

    /// Emits `addw`.
    private static void emitAddWord(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteAddw(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteAddw();
    }

    /// Emits `subw`.
    private static void emitSubtractWord(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSubw(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteSubw();
    }

    /// Emits `sllw`.
    private static void emitShiftLeftWord(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSllw(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteSllw();
    }

    /// Emits `srlw`.
    private static void emitShiftRightWord(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSrlw(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteSrlw();
    }

    /// Emits `sraw`.
    private static void emitShiftRightArithmeticWord(RiscVBytecodeBlockRootNodeGen.Builder builder, DecodedInstruction decoded) {
        builder.beginExecuteSraw(decoded.address(), decoded.raw(), decoded.nextAddress(), decoded.rd(), decoded.rs1(), decoded.rs2());
        builder.emitLoadArgument(0);
        builder.endExecuteSraw();
    }
}
