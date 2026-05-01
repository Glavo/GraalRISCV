// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.parser;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.Memory;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/// Decodes RV64 guest instruction subsets from guest memory into immutable block data.
@NotNullByDefault
public final class RiscVDecoder {
    /// The maximum number of straight-line instructions decoded into one block.
    private static final int MAX_BLOCK_INSTRUCTIONS = 64;

    /// The fixed bits for all `mop.r.N` instructions.
    private static final int ZIMOP_MOP_R_VALUE = 0x81c0_4073;

    /// The bit mask used to recognize all `mop.r.N` instructions.
    private static final int ZIMOP_MOP_R_MASK = 0xb3c0_707f;

    /// The fixed bits for all `mop.rr.N` instructions.
    private static final int ZIMOP_MOP_RR_VALUE = 0x8200_4073;

    /// The bit mask used to recognize all `mop.rr.N` instructions.
    private static final int ZIMOP_MOP_RR_MASK = 0xb200_707f;

    /// Prevents construction of this utility class.
    private RiscVDecoder() {
    }

    /// Decodes a basic block starting at the supplied guest program counter.
    public static DecodedBlock decodeBlock(Memory memory, long startPc) {
        ArrayList<DecodedInstruction> instructions = new ArrayList<>();
        long pc = startPc;
        for (int count = 0; count < MAX_BLOCK_INSTRUCTIONS; count++) {
            DecodedInstruction instruction = decode(memory, pc);
            instructions.add(instruction);
            pc += instruction.length();
            if (instruction.terminator()) {
                break;
            }
        }

        DecodedInstruction[] decodedInstructions = instructions.toArray(DecodedInstruction[]::new);
        return new DecodedBlock(
                startPc,
                decodedInstructions,
                pc,
                decodedInstructions[decodedInstructions.length - 1].terminator());
    }

    /// Decodes one instruction at the supplied guest address.
    private static DecodedInstruction decode(Memory memory, long address) {
        int half = memory.readUnsignedShort(address);
        if ((half & 0b11) != 0b11) {
            return decodeCompressed(address, half);
        }

        return decodeInt(address, memory.readInstructionInt(address));
    }

    /// Decodes a 32-bit instruction.
    private static DecodedInstruction decodeInt(long address, int raw) {
        int opcode = raw & 0x7f;
        return switch (opcode) {
            case 0x37 -> instruction(address, raw, RiscVOperation.LUI, rd(raw), 0, 0, uImmediate(raw), false);
            case 0x17 -> instruction(address, raw, RiscVOperation.AUIPC, rd(raw), 0, 0, uImmediate(raw), false);
            case 0x6f -> instruction(address, raw, RiscVOperation.JAL, rd(raw), 0, 0, jalImmediate(raw), true);
            case 0x67 -> decodeJalr(address, raw);
            case 0x63 -> decodeBranch(address, raw);
            case 0x03 -> decodeLoad(address, raw);
            case 0x07 -> decodeFloatingPointLoad(address, raw);
            case 0x23 -> decodeStore(address, raw);
            case 0x27 -> decodeFloatingPointStore(address, raw);
            case 0x13 -> decodeOpImmediate(address, raw);
            case 0x1b -> decodeOpImmediateWord(address, raw);
            case 0x33 -> decodeOp(address, raw);
            case 0x3b -> decodeOpWord(address, raw);
            case 0x43 -> decodeFloatingPointFusedMultiplyAdd(address, raw, RiscVOperation.FMADD);
            case 0x47 -> decodeFloatingPointFusedMultiplyAdd(address, raw, RiscVOperation.FMSUB);
            case 0x4b -> decodeFloatingPointFusedMultiplyAdd(address, raw, RiscVOperation.FNMSUB);
            case 0x4f -> decodeFloatingPointFusedMultiplyAdd(address, raw, RiscVOperation.FNMADD);
            case 0x53 -> decodeFloatingPointOperation(address, raw);
            case 0x57 -> decodeVectorOperation(address, raw);
            case 0x0f -> decodeMiscMemory(address, raw);
            case 0x73 -> decodeSystem(address, raw);
            case 0x2f -> decodeAtomic(address, raw);
            default -> illegal(address, raw);
        };
    }

    /// Decodes memory-ordering instructions.
    private static DecodedInstruction decodeMiscMemory(long address, int raw) {
        int funct3 = funct3(raw);
        if (funct3 == 0) {
            return instruction(address, raw, RiscVOperation.FENCE, 0, 0, 0, 0, false);
        }
        if (funct3 == 1 && rd(raw) == 0 && rs1(raw) == 0 && (raw >>> 20) == 0) {
            return instruction(address, raw, RiscVOperation.FENCE_I, 0, 0, 0, 0, true);
        }
        if (funct3 == 2 && rd(raw) == 0 && ((raw >>> 25) & 0x7f) == 0) {
            RiscVOperation operation = switch (rs2(raw)) {
                case 0 -> RiscVOperation.CBO_INVAL;
                case 1 -> RiscVOperation.CBO_CLEAN;
                case 2 -> RiscVOperation.CBO_FLUSH;
                case 4 -> RiscVOperation.CBO_ZERO;
                default -> throw illegalException(address, raw);
            };
            return instruction(address, raw, operation, 0, rs1(raw), 0, 0, false);
        }
        throw illegalException(address, raw);
    }

    /// Decodes `jalr`.
    private static DecodedInstruction decodeJalr(long address, int raw) {
        requireFunct3(address, raw, 0);
        return instruction(address, raw, RiscVOperation.JALR, rd(raw), rs1(raw), 0, iImmediate(raw), true);
    }

    /// Decodes branch instructions.
    private static DecodedInstruction decodeBranch(long address, int raw) {
        RiscVOperation operation = switch (funct3(raw)) {
            case 0 -> RiscVOperation.BEQ;
            case 1 -> RiscVOperation.BNE;
            case 4 -> RiscVOperation.BLT;
            case 5 -> RiscVOperation.BGE;
            case 6 -> RiscVOperation.BLTU;
            case 7 -> RiscVOperation.BGEU;
            default -> throw illegalException(address, raw);
        };
        return instruction(address, raw, operation, 0, rs1(raw), rs2(raw), branchImmediate(raw), true);
    }

    /// Decodes load instructions.
    private static DecodedInstruction decodeLoad(long address, int raw) {
        RiscVOperation operation = switch (funct3(raw)) {
            case 0 -> RiscVOperation.LB;
            case 1 -> RiscVOperation.LH;
            case 2 -> RiscVOperation.LW;
            case 3 -> RiscVOperation.LD;
            case 4 -> RiscVOperation.LBU;
            case 5 -> RiscVOperation.LHU;
            case 6 -> RiscVOperation.LWU;
            default -> throw illegalException(address, raw);
        };
        return instruction(address, raw, operation, rd(raw), rs1(raw), 0, iImmediate(raw), false);
    }

    /// Decodes floating-point load instructions.
    private static DecodedInstruction decodeFloatingPointLoad(long address, int raw) {
        if (isVectorMemoryWidth(funct3(raw))) {
            return decodeVectorLoad(address, raw);
        }
        RiscVOperation operation = switch (funct3(raw)) {
            case 1 -> RiscVOperation.FLH;
            case 2 -> RiscVOperation.FLW;
            case 3 -> RiscVOperation.FLD;
            default -> throw illegalException(address, raw);
        };
        return instruction(address, raw, operation, rd(raw), rs1(raw), 0, iImmediate(raw), false);
    }

    /// Decodes store instructions.
    private static DecodedInstruction decodeStore(long address, int raw) {
        RiscVOperation operation = switch (funct3(raw)) {
            case 0 -> RiscVOperation.SB;
            case 1 -> RiscVOperation.SH;
            case 2 -> RiscVOperation.SW;
            case 3 -> RiscVOperation.SD;
            default -> throw illegalException(address, raw);
        };
        return instruction(address, raw, operation, 0, rs1(raw), rs2(raw), storeImmediate(raw), false);
    }

    /// Decodes floating-point store instructions.
    private static DecodedInstruction decodeFloatingPointStore(long address, int raw) {
        if (isVectorMemoryWidth(funct3(raw))) {
            return decodeVectorStore(address, raw);
        }
        RiscVOperation operation = switch (funct3(raw)) {
            case 1 -> RiscVOperation.FSH;
            case 2 -> RiscVOperation.FSW;
            case 3 -> RiscVOperation.FSD;
            default -> throw illegalException(address, raw);
        };
        return instruction(address, raw, operation, 0, rs1(raw), rs2(raw), storeImmediate(raw), false);
    }

    /// Decodes a supported vector load instruction.
    private static DecodedInstruction decodeVectorLoad(long address, int raw) {
        requireSupportedVectorMemory(address, raw);
        return instruction(address, raw, RiscVOperation.VECTOR_LOAD, rd(raw), rs1(raw), 0, raw, false);
    }

    /// Decodes a supported vector store instruction.
    private static DecodedInstruction decodeVectorStore(long address, int raw) {
        requireSupportedVectorMemory(address, raw);
        return instruction(address, raw, RiscVOperation.VECTOR_STORE, rd(raw), rs1(raw), 0, raw, false);
    }

    /// Decodes floating-point fused multiply-add instructions.
    private static DecodedInstruction decodeFloatingPointFusedMultiplyAdd(long address, int raw, RiscVOperation operation) {
        int format = requireFloatingPointFormat(address, raw, (raw >>> 25) & 0x3);
        int roundingMode = requireFloatingPointRoundingMode(address, raw, funct3(raw));
        return instruction(
                address,
                raw,
                operation,
                rd(raw),
                rs1(raw),
                rs2(raw),
                floatingPointImmediate(format, roundingMode, (raw >>> 27) & 0x1f),
                false);
    }

    /// Decodes floating-point arithmetic, conversion, move, compare, and classify instructions.
    private static DecodedInstruction decodeFloatingPointOperation(long address, int raw) {
        int funct7 = (raw >>> 25) & 0x7f;
        int format = funct7 & 0x3;
        int roundingMode = funct3(raw);
        return switch (funct7) {
            case 0x00, 0x01 -> roundedFloatingPointInstruction(
                    address,
                    raw,
                    RiscVOperation.FADD,
                    requireFloatingPointFormat(address, raw, format),
                    roundingMode);
            case 0x04, 0x05 -> roundedFloatingPointInstruction(
                    address,
                    raw,
                    RiscVOperation.FSUB,
                    requireFloatingPointFormat(address, raw, format),
                    roundingMode);
            case 0x08, 0x09 -> roundedFloatingPointInstruction(
                    address,
                    raw,
                    RiscVOperation.FMUL,
                    requireFloatingPointFormat(address, raw, format),
                    roundingMode);
            case 0x0c, 0x0d -> roundedFloatingPointInstruction(
                    address,
                    raw,
                    RiscVOperation.FDIV,
                    requireFloatingPointFormat(address, raw, format),
                    roundingMode);
            case 0x2c, 0x2d -> {
                if (rs2(raw) != 0) {
                    throw illegalException(address, raw);
                }
                yield roundedFloatingPointInstruction(
                        address,
                        raw,
                        RiscVOperation.FSQRT,
                        requireFloatingPointFormat(address, raw, format),
                        roundingMode);
            }
            case 0x10, 0x11 -> {
                RiscVOperation operation = switch (funct3(raw)) {
                    case 0 -> RiscVOperation.FSGNJ;
                    case 1 -> RiscVOperation.FSGNJN;
                    case 2 -> RiscVOperation.FSGNJX;
                    default -> throw illegalException(address, raw);
                };
                yield instruction(
                        address,
                        raw,
                        operation,
                        rd(raw),
                        rs1(raw),
                        rs2(raw),
                        floatingPointImmediate(requireFloatingPointFormat(address, raw, format)),
                        false);
            }
            case 0x14, 0x15 -> {
                RiscVOperation operation = switch (funct3(raw)) {
                    case 0 -> RiscVOperation.FMIN;
                    case 1 -> RiscVOperation.FMAX;
                    case 2 -> RiscVOperation.FMINM;
                    case 3 -> RiscVOperation.FMAXM;
                    default -> throw illegalException(address, raw);
                };
                yield instruction(
                        address,
                        raw,
                        operation,
                        rd(raw),
                        rs1(raw),
                        rs2(raw),
                        floatingPointImmediate(requireFloatingPointFormat(address, raw, format)),
                        false);
            }
            case 0x20 -> {
                RiscVOperation operation = switch (rs2(raw)) {
                    case 1 -> RiscVOperation.FCVT_S_D;
                    case 2 -> RiscVOperation.FCVT_S_H;
                    case 4 -> RiscVOperation.FROUND;
                    case 5 -> RiscVOperation.FROUNDNX;
                    default -> throw illegalException(address, raw);
                };
                yield roundedFloatingPointInstruction(address, raw, operation, 0, roundingMode);
            }
            case 0x21 -> {
                RiscVOperation operation = switch (rs2(raw)) {
                    case 0 -> RiscVOperation.FCVT_D_S;
                    case 2 -> RiscVOperation.FCVT_D_H;
                    case 4 -> RiscVOperation.FROUND;
                    case 5 -> RiscVOperation.FROUNDNX;
                    default -> throw illegalException(address, raw);
                };
                yield roundedFloatingPointInstruction(address, raw, operation, 1, roundingMode);
            }
            case 0x22 -> {
                RiscVOperation operation = switch (rs2(raw)) {
                    case 0 -> RiscVOperation.FCVT_H_S;
                    case 1 -> RiscVOperation.FCVT_H_D;
                    default -> throw illegalException(address, raw);
                };
                yield roundedFloatingPointInstruction(address, raw, operation, 2, roundingMode);
            }
            case 0x50, 0x51 -> {
                RiscVOperation operation = switch (funct3(raw)) {
                    case 0 -> RiscVOperation.FLE;
                    case 1 -> RiscVOperation.FLT;
                    case 2 -> RiscVOperation.FEQ;
                    case 4 -> RiscVOperation.FLEQ;
                    case 5 -> RiscVOperation.FLTQ;
                    default -> throw illegalException(address, raw);
                };
                yield instruction(
                        address,
                        raw,
                        operation,
                        rd(raw),
                        rs1(raw),
                        rs2(raw),
                        floatingPointImmediate(requireFloatingPointFormat(address, raw, format)),
                        false);
            }
            case 0x60, 0x61 -> {
                if (funct7 == 0x61 && rs2(raw) == 8 && roundingMode == 1) {
                    yield roundedFloatingPointInstruction(address, raw, RiscVOperation.FCVTMOD_W_D, 1, roundingMode);
                }
                requireConversionSelector(address, raw, rs2(raw));
                yield roundedFloatingPointInstruction(
                        address,
                        raw,
                        RiscVOperation.FCVT_INT_FP,
                        requireFloatingPointFormat(address, raw, format),
                        roundingMode);
            }
            case 0x68, 0x69 -> {
                requireConversionSelector(address, raw, rs2(raw));
                yield roundedFloatingPointInstruction(
                        address,
                        raw,
                        RiscVOperation.FCVT_FP_INT,
                        requireFloatingPointFormat(address, raw, format),
                        roundingMode);
            }
            case 0x70, 0x71, 0x72 -> {
                if (rs2(raw) != 0) {
                    throw illegalException(address, raw);
                }
                RiscVOperation operation = switch (funct3(raw)) {
                    case 0 -> RiscVOperation.FMV_X_FP;
                    case 1 -> {
                        if (format == 2) {
                            throw illegalException(address, raw);
                        }
                        yield RiscVOperation.FCLASS;
                    }
                    default -> throw illegalException(address, raw);
                };
                yield instruction(
                        address,
                        raw,
                        operation,
                        rd(raw),
                        rs1(raw),
                        0,
                        floatingPointImmediate(requireStorageFloatingPointFormat(address, raw, format)),
                        false);
            }
            case 0x78, 0x79, 0x7a -> {
                if (funct3(raw) == 0 && rs2(raw) == 1) {
                    yield instruction(
                            address,
                            raw,
                            RiscVOperation.FLI,
                            rd(raw),
                            rs1(raw),
                            0,
                            floatingPointImmediate(requireFloatingPointFormat(address, raw, format)),
                            false);
                }
                if (funct3(raw) != 0 || rs2(raw) != 0) {
                    throw illegalException(address, raw);
                }
                yield instruction(
                        address,
                        raw,
                        RiscVOperation.FMV_FP_X,
                        rd(raw),
                        rs1(raw),
                        0,
                        floatingPointImmediate(requireStorageFloatingPointFormat(address, raw, format)),
                        false);
            }
            default -> throw illegalException(address, raw);
        };
    }

    /// Creates a rounded floating-point decoded instruction.
    private static DecodedInstruction roundedFloatingPointInstruction(
            long address,
            int raw,
            RiscVOperation operation,
            int format,
            int roundingMode) {
        requireFloatingPointRoundingMode(address, raw, roundingMode);
        return instruction(address, raw, operation, rd(raw), rs1(raw), rs2(raw), floatingPointImmediate(format, roundingMode), false);
    }

    /// Decodes integer immediate arithmetic instructions.
    private static DecodedInstruction decodeOpImmediate(long address, int raw) {
        int funct3 = funct3(raw);
        RiscVOperation operation = switch (funct3) {
            case 0 -> RiscVOperation.ADDI;
            case 2 -> RiscVOperation.SLTI;
            case 3 -> RiscVOperation.SLTIU;
            case 4 -> RiscVOperation.XORI;
            case 6 -> RiscVOperation.ORI;
            case 7 -> RiscVOperation.ANDI;
            case 1 -> {
                int imm = raw >>> 20;
                RiscVOperation bitManipulation = decodeShiftLeftImmediateBitManipulation(raw, imm);
                if (bitManipulation != null) {
                    yield bitManipulation;
                }
                if ((imm & ~0x3f) != 0) {
                    throw illegalException(address, raw);
                }
                yield RiscVOperation.SLLI;
            }
            case 5 -> {
                int imm = raw >>> 20;
                RiscVOperation bitManipulation = decodeShiftRightImmediateBitManipulation(raw, imm);
                if (bitManipulation != null) {
                    yield bitManipulation;
                }
                int funct6 = (raw >>> 26) & 0x3f;
                if (funct6 == 0) {
                    yield RiscVOperation.SRLI;
                }
                if (funct6 == 0x10) {
                    yield RiscVOperation.SRAI;
                }
                throw illegalException(address, raw);
            }
            default -> throw illegalException(address, raw);
        };
        long immediate = (funct3 == 1 || funct3 == 5) ? ((raw >>> 20) & 0x3f) : iImmediate(raw);
        return instruction(address, raw, operation, rd(raw), rs1(raw), 0, immediate, false);
    }

    /// Decodes `funct3=001` OP-IMM bit-manipulation instructions.
    private static @Nullable RiscVOperation decodeShiftLeftImmediateBitManipulation(int raw, int imm) {
        return switch (imm) {
            case 0x600 -> RiscVOperation.CLZ;
            case 0x601 -> RiscVOperation.CTZ;
            case 0x602 -> RiscVOperation.CPOP;
            case 0x604 -> RiscVOperation.SEXT_B;
            case 0x605 -> RiscVOperation.SEXT_H;
            default -> switch ((raw >>> 26) & 0x3f) {
                case 0x0a -> RiscVOperation.BSETI;
                case 0x12 -> RiscVOperation.BCLRI;
                case 0x1a -> RiscVOperation.BINVI;
                default -> null;
            };
        };
    }

    /// Decodes `funct3=101` OP-IMM bit-manipulation instructions.
    private static @Nullable RiscVOperation decodeShiftRightImmediateBitManipulation(int raw, int imm) {
        return switch (imm) {
            case 0x287 -> RiscVOperation.ORC_B;
            case 0x6b8 -> RiscVOperation.REV8;
            default -> switch ((raw >>> 26) & 0x3f) {
                case 0x12 -> RiscVOperation.BEXTI;
                case 0x18 -> RiscVOperation.RORI;
                default -> null;
            };
        };
    }

    /// Decodes word-width immediate arithmetic instructions.
    private static DecodedInstruction decodeOpImmediateWord(long address, int raw) {
        RiscVOperation operation = switch (funct3(raw)) {
            case 0 -> RiscVOperation.ADDIW;
            case 1 -> {
                int imm = raw >>> 20;
                RiscVOperation bitManipulation = switch (imm) {
                    case 0x600 -> RiscVOperation.CLZW;
                    case 0x601 -> RiscVOperation.CTZW;
                    case 0x602 -> RiscVOperation.CPOPW;
                    default -> null;
                };
                if (bitManipulation != null) {
                    yield bitManipulation;
                }
                if (((raw >>> 26) & 0x3f) == 0x02) {
                    yield RiscVOperation.SLLI_UW;
                }
                if (((raw >>> 25) & 0x7f) != 0) {
                    throw illegalException(address, raw);
                }
                yield RiscVOperation.SLLIW;
            }
            case 5 -> {
                int funct7 = (raw >>> 25) & 0x7f;
                if (funct7 == 0) {
                    yield RiscVOperation.SRLIW;
                }
                if (funct7 == 0x20) {
                    yield RiscVOperation.SRAIW;
                }
                if (funct7 == 0x30) {
                    yield RiscVOperation.RORIW;
                }
                throw illegalException(address, raw);
            }
            default -> throw illegalException(address, raw);
        };
        long immediate = funct3(raw) == 0 ? iImmediate(raw) : ((raw >>> 20) & 0x3f);
        return instruction(address, raw, operation, rd(raw), rs1(raw), 0, immediate, false);
    }

    /// Decodes register arithmetic instructions.
    private static DecodedInstruction decodeOp(long address, int raw) {
        int funct3 = funct3(raw);
        int funct7 = (raw >>> 25) & 0x7f;
        RiscVOperation operation = switch (funct7) {
            case 0x00 -> switch (funct3) {
                case 0 -> RiscVOperation.ADD;
                case 1 -> RiscVOperation.SLL;
                case 2 -> RiscVOperation.SLT;
                case 3 -> RiscVOperation.SLTU;
                case 4 -> RiscVOperation.XOR;
                case 5 -> RiscVOperation.SRL;
                case 6 -> RiscVOperation.OR;
                case 7 -> RiscVOperation.AND;
                default -> throw illegalException(address, raw);
            };
            case 0x20 -> switch (funct3) {
                case 0 -> RiscVOperation.SUB;
                case 4 -> RiscVOperation.XNOR;
                case 5 -> RiscVOperation.SRA;
                case 6 -> RiscVOperation.ORN;
                case 7 -> RiscVOperation.ANDN;
                default -> throw illegalException(address, raw);
            };
            case 0x01 -> switch (funct3) {
                case 0 -> RiscVOperation.MUL;
                case 1 -> RiscVOperation.MULH;
                case 2 -> RiscVOperation.MULHSU;
                case 3 -> RiscVOperation.MULHU;
                case 4 -> RiscVOperation.DIV;
                case 5 -> RiscVOperation.DIVU;
                case 6 -> RiscVOperation.REM;
                case 7 -> RiscVOperation.REMU;
                default -> throw illegalException(address, raw);
            };
            case 0x05 -> switch (funct3) {
                case 4 -> RiscVOperation.MIN;
                case 5 -> RiscVOperation.MINU;
                case 6 -> RiscVOperation.MAX;
                case 7 -> RiscVOperation.MAXU;
                default -> throw illegalException(address, raw);
            };
            case 0x07 -> switch (funct3) {
                case 5 -> RiscVOperation.CZERO_EQZ;
                case 7 -> RiscVOperation.CZERO_NEZ;
                default -> throw illegalException(address, raw);
            };
            case 0x10 -> switch (funct3) {
                case 2 -> RiscVOperation.SH1ADD;
                case 4 -> RiscVOperation.SH2ADD;
                case 6 -> RiscVOperation.SH3ADD;
                default -> throw illegalException(address, raw);
            };
            case 0x14 -> switch (funct3) {
                case 1 -> RiscVOperation.BSET;
                default -> throw illegalException(address, raw);
            };
            case 0x24 -> switch (funct3) {
                case 1 -> RiscVOperation.BCLR;
                case 5 -> RiscVOperation.BEXT;
                default -> throw illegalException(address, raw);
            };
            case 0x30 -> switch (funct3) {
                case 1 -> RiscVOperation.ROL;
                case 5 -> RiscVOperation.ROR;
                default -> throw illegalException(address, raw);
            };
            case 0x34 -> switch (funct3) {
                case 1 -> RiscVOperation.BINV;
                default -> throw illegalException(address, raw);
            };
            default -> throw illegalException(address, raw);
        };
        return instruction(address, raw, operation, rd(raw), rs1(raw), rs2(raw), 0, false);
    }

    /// Decodes word-width register arithmetic instructions.
    private static DecodedInstruction decodeOpWord(long address, int raw) {
        int funct3 = funct3(raw);
        int funct7 = (raw >>> 25) & 0x7f;
        RiscVOperation operation = switch (funct7) {
            case 0x00 -> switch (funct3) {
                case 0 -> RiscVOperation.ADDW;
                case 1 -> RiscVOperation.SLLW;
                case 5 -> RiscVOperation.SRLW;
                default -> throw illegalException(address, raw);
            };
            case 0x04 -> switch (funct3) {
                case 0 -> RiscVOperation.ADD_UW;
                case 4 -> {
                    if (rs2(raw) != 0) {
                        throw illegalException(address, raw);
                    }
                    yield RiscVOperation.ZEXT_H;
                }
                default -> throw illegalException(address, raw);
            };
            case 0x10 -> switch (funct3) {
                case 2 -> RiscVOperation.SH1ADD_UW;
                case 4 -> RiscVOperation.SH2ADD_UW;
                case 6 -> RiscVOperation.SH3ADD_UW;
                default -> throw illegalException(address, raw);
            };
            case 0x20 -> switch (funct3) {
                case 0 -> RiscVOperation.SUBW;
                case 5 -> RiscVOperation.SRAW;
                default -> throw illegalException(address, raw);
            };
            case 0x30 -> switch (funct3) {
                case 1 -> RiscVOperation.ROLW;
                case 5 -> RiscVOperation.RORW;
                default -> throw illegalException(address, raw);
            };
            case 0x01 -> switch (funct3) {
                case 0 -> RiscVOperation.MULW;
                case 4 -> RiscVOperation.DIVW;
                case 5 -> RiscVOperation.DIVUW;
                case 6 -> RiscVOperation.REMW;
                case 7 -> RiscVOperation.REMUW;
                default -> throw illegalException(address, raw);
            };
            default -> throw illegalException(address, raw);
        };
        return instruction(address, raw, operation, rd(raw), rs1(raw), rs2(raw), 0, false);
    }

    /// Decodes system instructions.
    private static DecodedInstruction decodeSystem(long address, int raw) {
        if (raw == 0x0000_0073) {
            return instruction(address, raw, RiscVOperation.ECALL, 0, 0, 0, 0, true);
        }
        if (raw == 0x0010_0073) {
            return instruction(address, raw, RiscVOperation.EBREAK, 0, 0, 0, 0, true);
        }
        if (raw == 0x3020_0073) {
            return instruction(address, raw, RiscVOperation.MRET, 0, 0, 0, 0, true);
        }
        if (raw == 0x00d0_0073) {
            return instruction(address, raw, RiscVOperation.WRS_NTO, 0, 0, 0, 0, false);
        }
        if (raw == 0x01d0_0073) {
            return instruction(address, raw, RiscVOperation.WRS_STO, 0, 0, 0, 0, false);
        }
        if ((raw & ZIMOP_MOP_R_MASK) == ZIMOP_MOP_R_VALUE) {
            return instruction(address, raw, RiscVOperation.MOP_R, rd(raw), rs1(raw), 0, 0, false);
        }
        if ((raw & ZIMOP_MOP_RR_MASK) == ZIMOP_MOP_RR_VALUE) {
            return instruction(address, raw, RiscVOperation.MOP_RR, rd(raw), rs1(raw), rs2(raw), 0, false);
        }

        RiscVOperation operation = switch (funct3(raw)) {
            case 1 -> RiscVOperation.CSRRW;
            case 2 -> RiscVOperation.CSRRS;
            case 3 -> RiscVOperation.CSRRC;
            case 5 -> RiscVOperation.CSRRWI;
            case 6 -> RiscVOperation.CSRRSI;
            case 7 -> RiscVOperation.CSRRCI;
            default -> throw illegalException(address, raw);
        };
        return instruction(address, raw, operation, rd(raw), rs1(raw), 0, raw >>> 20, false);
    }

    /// Decodes vector configuration and arithmetic instructions.
    private static DecodedInstruction decodeVectorOperation(long address, int raw) {
        int funct3 = funct3(raw);
        if (funct3 == 7) {
            if ((raw >>> 31) == 0) {
                return instruction(address, raw, RiscVOperation.VSETVLI, rd(raw), rs1(raw), 0, (raw >>> 20) & 0x7ff, false);
            }
            if (((raw >>> 30) & 0x1) != 0) {
                return instruction(address, raw, RiscVOperation.VSETIVLI, rd(raw), rs1(raw), 0, (raw >>> 20) & 0x3ff, false);
            }
            if (((raw >>> 25) & 0x3f) == 0) {
                return instruction(address, raw, RiscVOperation.VSETVL, rd(raw), rs1(raw), rs2(raw), 0, false);
            }
            throw illegalException(address, raw);
        }

        if (funct3 >= 0 && funct3 <= 6) {
            return instruction(address, raw, RiscVOperation.VECTOR_INTEGER, rd(raw), rs1(raw), rs2(raw), raw, false);
        }
        throw illegalException(address, raw);
    }

    /// Decodes atomic memory instructions.
    private static DecodedInstruction decodeAtomic(long address, int raw) {
        int width = funct3(raw);
        int funct5 = (raw >>> 27) & 0x1f;
        RiscVOperation operation = switch (width) {
            case 2 -> atomicWordOperation(address, raw, funct5);
            case 3 -> atomicDoubleOperation(address, raw, funct5);
            default -> throw illegalException(address, raw);
        };
        if ((operation == RiscVOperation.LR_W || operation == RiscVOperation.LR_D) && rs2(raw) != 0) {
            throw illegalException(address, raw);
        }
        return instruction(address, raw, operation, rd(raw), rs1(raw), rs2(raw), 0, false);
    }

    /// Decodes the word-width atomic operation field.
    private static RiscVOperation atomicWordOperation(long address, int raw, int funct5) {
        return switch (funct5) {
            case 0x02 -> RiscVOperation.LR_W;
            case 0x03 -> RiscVOperation.SC_W;
            case 0x01 -> RiscVOperation.AMOSWAP_W;
            case 0x00 -> RiscVOperation.AMOADD_W;
            case 0x04 -> RiscVOperation.AMOXOR_W;
            case 0x0c -> RiscVOperation.AMOAND_W;
            case 0x08 -> RiscVOperation.AMOOR_W;
            case 0x10 -> RiscVOperation.AMOMIN_W;
            case 0x14 -> RiscVOperation.AMOMAX_W;
            case 0x18 -> RiscVOperation.AMOMINU_W;
            case 0x1c -> RiscVOperation.AMOMAXU_W;
            default -> throw illegalException(address, raw);
        };
    }

    /// Decodes the doubleword-width atomic operation field.
    private static RiscVOperation atomicDoubleOperation(long address, int raw, int funct5) {
        return switch (funct5) {
            case 0x02 -> RiscVOperation.LR_D;
            case 0x03 -> RiscVOperation.SC_D;
            case 0x01 -> RiscVOperation.AMOSWAP_D;
            case 0x00 -> RiscVOperation.AMOADD_D;
            case 0x04 -> RiscVOperation.AMOXOR_D;
            case 0x0c -> RiscVOperation.AMOAND_D;
            case 0x08 -> RiscVOperation.AMOOR_D;
            case 0x10 -> RiscVOperation.AMOMIN_D;
            case 0x14 -> RiscVOperation.AMOMAX_D;
            case 0x18 -> RiscVOperation.AMOMINU_D;
            case 0x1c -> RiscVOperation.AMOMAXU_D;
            default -> throw illegalException(address, raw);
        };
    }

    /// Decodes a 16-bit compressed instruction.
    private static DecodedInstruction decodeCompressed(long address, int raw) {
        int quadrant = raw & 0x3;
        int funct3 = (raw >>> 13) & 0x7;
        return switch (quadrant) {
            case 0 -> decodeCompressedQuadrant0(address, raw, funct3);
            case 1 -> decodeCompressedQuadrant1(address, raw, funct3);
            case 2 -> decodeCompressedQuadrant2(address, raw, funct3);
            default -> illegal(address, raw);
        };
    }

    /// Decodes compressed quadrant 0 instructions.
    private static DecodedInstruction decodeCompressedQuadrant0(long address, int raw, int funct3) {
        int rd = compressedRegister(raw >>> 2);
        int rs1 = compressedRegister(raw >>> 7);
        int rs2 = compressedRegister(raw >>> 2);
        return switch (funct3) {
            case 0 -> {
                int immediate = cAddi4spnImmediate(raw);
                if (immediate == 0) {
                    throw illegalException(address, raw);
                }
                yield compressed(address, raw, RiscVOperation.ADDI, rd, 2, 0, immediate, false);
            }
            case 1 -> compressed(address, raw, RiscVOperation.FLD, rd, rs1, 0, cLdImmediate(raw), false);
            case 2 -> compressed(address, raw, RiscVOperation.LW, rd, rs1, 0, cLwImmediate(raw), false);
            case 3 -> compressed(address, raw, RiscVOperation.LD, rd, rs1, 0, cLdImmediate(raw), false);
            case 4 -> decodeCompressedZcbMemory(address, raw, rd, rs1, rs2);
            case 5 -> compressed(address, raw, RiscVOperation.FSD, 0, rs1, rs2, cLdImmediate(raw), false);
            case 6 -> compressed(address, raw, RiscVOperation.SW, 0, rs1, rs2, cLwImmediate(raw), false);
            case 7 -> compressed(address, raw, RiscVOperation.SD, 0, rs1, rs2, cLdImmediate(raw), false);
            default -> throw illegalException(address, raw);
        };
    }

    /// Decodes compressed quadrant 1 instructions.
    private static DecodedInstruction decodeCompressedQuadrant1(long address, int raw, int funct3) {
        int rd = (raw >>> 7) & 0x1f;
        long immediate = cImmediate(raw);
        return switch (funct3) {
            case 0 -> rd == 0
                    ? compressed(address, raw, RiscVOperation.NOP, 0, 0, 0, 0, false)
                    : compressed(address, raw, RiscVOperation.ADDI, rd, rd, 0, immediate, false);
            case 1 -> {
                if (rd == 0) {
                    throw illegalException(address, raw);
                }
                yield compressed(address, raw, RiscVOperation.ADDIW, rd, rd, 0, immediate, false);
            }
            case 2 -> rd == 0
                    ? compressed(address, raw, RiscVOperation.NOP, 0, 0, 0, 0, false)
                    : compressed(address, raw, RiscVOperation.ADDI, rd, 0, 0, immediate, false);
            case 3 -> decodeCompressedLuiOrAddi16Sp(address, raw, rd);
            case 4 -> decodeCompressedAlu(address, raw);
            case 5 -> compressed(address, raw, RiscVOperation.JAL, 0, 0, 0, cJumpImmediate(raw), true);
            case 6 -> compressed(address, raw, RiscVOperation.BEQ, 0, compressedRegister(raw >>> 7), 0, cBranchImmediate(raw), true);
            case 7 -> compressed(address, raw, RiscVOperation.BNE, 0, compressedRegister(raw >>> 7), 0, cBranchImmediate(raw), true);
            default -> throw illegalException(address, raw);
        };
    }

    /// Decodes compressed quadrant 2 instructions.
    private static DecodedInstruction decodeCompressedQuadrant2(long address, int raw, int funct3) {
        int rd = (raw >>> 7) & 0x1f;
        int rs2 = (raw >>> 2) & 0x1f;
        return switch (funct3) {
            case 0 -> rd == 0
                    ? compressed(address, raw, RiscVOperation.NOP, 0, 0, 0, 0, false)
                    : compressed(address, raw, RiscVOperation.SLLI, rd, rd, 0, cShiftImmediate(raw), false);
            case 1 -> compressed(address, raw, RiscVOperation.FLD, rd, 2, 0, cLdspImmediate(raw), false);
            case 2 -> {
                if (rd == 0) {
                    throw illegalException(address, raw);
                }
                yield compressed(address, raw, RiscVOperation.LW, rd, 2, 0, cLwspImmediate(raw), false);
            }
            case 3 -> {
                if (rd == 0) {
                    throw illegalException(address, raw);
                }
                yield compressed(address, raw, RiscVOperation.LD, rd, 2, 0, cLdspImmediate(raw), false);
            }
            case 4 -> decodeCompressedJumpMoveAdd(address, raw, rd, rs2);
            case 5 -> compressed(address, raw, RiscVOperation.FSD, 0, 2, rs2, cSdspImmediate(raw), false);
            case 6 -> compressed(address, raw, RiscVOperation.SW, 0, 2, rs2, cSwspImmediate(raw), false);
            case 7 -> compressed(address, raw, RiscVOperation.SD, 0, 2, rs2, cSdspImmediate(raw), false);
            default -> throw illegalException(address, raw);
        };
    }

    /// Decodes compressed LUI and ADDI16SP.
    private static DecodedInstruction decodeCompressedLuiOrAddi16Sp(long address, int raw, int rd) {
        if (rd == 0) {
            throw illegalException(address, raw);
        }
        if (rd == 2) {
            long immediate = cAddi16SpImmediate(raw);
            if (immediate == 0) {
                throw illegalException(address, raw);
            }
            return compressed(address, raw, RiscVOperation.ADDI, 2, 2, 0, immediate, false);
        }

        long immediate = cLuiImmediate(raw);
        if (immediate == 0) {
            if (isCompressedMopRegister(rd)) {
                return compressed(address, raw, RiscVOperation.C_MOP, 0, 0, 0, 0, false);
            }
            throw illegalException(address, raw);
        }
        return compressed(address, raw, RiscVOperation.LUI, rd, 0, 0, immediate, false);
    }

    /// Decodes compressed ALU instructions.
    private static DecodedInstruction decodeCompressedAlu(long address, int raw) {
        int rd = compressedRegister(raw >>> 7);
        int mode = (raw >>> 10) & 0x3;
        if (mode == 0) {
            return compressed(address, raw, RiscVOperation.SRLI, rd, rd, 0, cShiftImmediate(raw), false);
        }
        if (mode == 1) {
            return compressed(address, raw, RiscVOperation.SRAI, rd, rd, 0, cShiftImmediate(raw), false);
        }
        if (mode == 2) {
            return compressed(address, raw, RiscVOperation.ANDI, rd, rd, 0, cImmediate(raw), false);
        }

        int rs2 = compressedRegister(raw >>> 2);
        boolean word = ((raw >>> 12) & 1) != 0;
        int subop = (raw >>> 5) & 0x3;
        if (word && subop == 2) {
            return compressed(address, raw, RiscVOperation.MUL, rd, rd, rs2, 0, false);
        }
        if (word && subop == 3) {
            return decodeCompressedZcbUnary(address, raw, rd);
        }
        RiscVOperation operation = word
                ? switch (subop) {
                    case 0 -> RiscVOperation.SUBW;
                    case 1 -> RiscVOperation.ADDW;
                    default -> throw illegalException(address, raw);
                }
                : switch (subop) {
                    case 0 -> RiscVOperation.SUB;
                    case 1 -> RiscVOperation.XOR;
                    case 2 -> RiscVOperation.OR;
                    case 3 -> RiscVOperation.AND;
                    default -> throw illegalException(address, raw);
                };
        return compressed(address, raw, operation, rd, rd, rs2, 0, false);
    }

    /// Decodes Zcb compressed load and store instructions in quadrant 0.
    private static DecodedInstruction decodeCompressedZcbMemory(long address, int raw, int rd, int rs1, int rs2) {
        return switch ((raw >>> 10) & 0x7) {
            case 0 -> compressed(address, raw, RiscVOperation.LBU, rd, rs1, 0, cZcbByteImmediate(raw), false);
            case 1 -> {
                int immediate = cZcbHalfImmediate(raw);
                yield ((raw >>> 6) & 1) == 0
                        ? compressed(address, raw, RiscVOperation.LHU, rd, rs1, 0, immediate, false)
                        : compressed(address, raw, RiscVOperation.LH, rd, rs1, 0, immediate, false);
            }
            case 2 -> compressed(address, raw, RiscVOperation.SB, 0, rs1, rs2, cZcbByteImmediate(raw), false);
            case 3 -> {
                if (((raw >>> 6) & 1) != 0) {
                    throw illegalException(address, raw);
                }
                yield compressed(address, raw, RiscVOperation.SH, 0, rs1, rs2, cZcbHalfImmediate(raw), false);
            }
            default -> throw illegalException(address, raw);
        };
    }

    /// Decodes Zcb compressed unary arithmetic instructions.
    private static DecodedInstruction decodeCompressedZcbUnary(long address, int raw, int rd) {
        return switch ((raw >>> 2) & 0x7) {
            case 0 -> compressed(address, raw, RiscVOperation.ANDI, rd, rd, 0, 0xff, false);
            case 1 -> compressed(address, raw, RiscVOperation.SEXT_B, rd, rd, 0, 0, false);
            case 2 -> compressed(address, raw, RiscVOperation.ZEXT_H, rd, rd, 0, 0, false);
            case 3 -> compressed(address, raw, RiscVOperation.SEXT_H, rd, rd, 0, 0, false);
            case 4 -> compressed(address, raw, RiscVOperation.ADD_UW, rd, rd, 0, 0, false);
            case 5 -> compressed(address, raw, RiscVOperation.XNOR, rd, rd, 0, 0, false);
            default -> throw illegalException(address, raw);
        };
    }

    /// Decodes compressed JR, MV, EBREAK, JALR, and ADD instructions.
    private static DecodedInstruction decodeCompressedJumpMoveAdd(long address, int raw, int rd, int rs2) {
        boolean highBit = ((raw >>> 12) & 1) != 0;
        if (!highBit && rs2 == 0) {
            if (rd == 0) {
                throw illegalException(address, raw);
            }
            return compressed(address, raw, RiscVOperation.JALR, 0, rd, 0, 0, true);
        }
        if (!highBit) {
            if (rd == 0) {
                throw illegalException(address, raw);
            }
            return compressed(address, raw, RiscVOperation.ADD, rd, 0, rs2, 0, false);
        }
        if (rd == 0 && rs2 == 0) {
            return compressed(address, raw, RiscVOperation.EBREAK, 0, 0, 0, 0, true);
        }
        if (rs2 == 0) {
            return compressed(address, raw, RiscVOperation.JALR, 1, rd, 0, 0, true);
        }
        return compressed(address, raw, RiscVOperation.ADD, rd, rd, rs2, 0, false);
    }

    /// Creates a 32-bit decoded instruction.
    private static DecodedInstruction instruction(
            long address,
            int raw,
            RiscVOperation operation,
            int rd,
            int rs1,
            int rs2,
            long immediate,
            boolean terminator) {
        return new DecodedInstruction(address, raw, Integer.BYTES, operation, rd, rs1, rs2, immediate, terminator);
    }

    /// Creates a 16-bit compressed decoded instruction.
    private static DecodedInstruction compressed(
            long address,
            int raw,
            RiscVOperation operation,
            int rd,
            int rs1,
            int rs2,
            long immediate,
            boolean terminator) {
        return new DecodedInstruction(address, raw, Short.BYTES, operation, rd, rs1, rs2, immediate, terminator);
    }

    /// Extracts the destination register field.
    private static int rd(int raw) {
        return (raw >>> 7) & 0x1f;
    }

    /// Extracts the first source register field.
    private static int rs1(int raw) {
        return (raw >>> 15) & 0x1f;
    }

    /// Extracts the second source register field.
    private static int rs2(int raw) {
        return (raw >>> 20) & 0x1f;
    }

    /// Extracts the funct3 field.
    private static int funct3(int raw) {
        return (raw >>> 12) & 0x7;
    }

    /// Validates an F or D floating-point format field.
    private static int requireFloatingPointFormat(long address, int raw, int format) {
        if (format == 0 || format == 1) {
            return format;
        }
        throw illegalException(address, raw);
    }

    /// Validates an F, D, or H floating-point storage format field.
    private static int requireStorageFloatingPointFormat(long address, int raw, int format) {
        if (format == 0 || format == 1 || format == 2) {
            return format;
        }
        throw illegalException(address, raw);
    }

    /// Returns true when a LOAD-FP/STORE-FP width field denotes a vector memory instruction.
    private static boolean isVectorMemoryWidth(int width) {
        return width == 0 || width == 5 || width == 6 || width == 7;
    }

    /// Validates the vector memory subset implemented by the current decoder.
    private static void requireSupportedVectorMemory(long address, int raw) {
        int mop = (raw >>> 26) & 0x3;
        int mew = (raw >>> 28) & 0x1;
        int nf = (raw >>> 29) & 0x7;
        int lumopOrSumop = (raw >>> 20) & 0x1f;
        if (mew != 0 || nf != 0 || mop == 0 && lumopOrSumop != 0) {
            throw illegalException(address, raw);
        }
    }

    /// Validates a static floating-point rounding mode field.
    private static int requireFloatingPointRoundingMode(long address, int raw, int roundingMode) {
        if (roundingMode <= 4 || roundingMode == 7) {
            return roundingMode;
        }
        throw illegalException(address, raw);
    }

    /// Validates an integer/floating-point conversion selector.
    private static void requireConversionSelector(long address, int raw, int selector) {
        if (selector > 3) {
            throw illegalException(address, raw);
        }
    }

    /// Packs a floating-point format with the default rounding-mode slot.
    private static long floatingPointImmediate(int format) {
        return floatingPointImmediate(format, 0);
    }

    /// Packs a floating-point format and rounding mode.
    private static long floatingPointImmediate(int format, int roundingMode) {
        return ((long) format << 3) | roundingMode;
    }

    /// Packs a floating-point format, rounding mode, and third source register.
    private static long floatingPointImmediate(int format, int roundingMode, int thirdSource) {
        return floatingPointImmediate(format, roundingMode) | ((long) thirdSource << 5);
    }

    /// Maps a compressed register field to its full register index.
    private static int compressedRegister(int shiftedRaw) {
        return 8 + (shiftedRaw & 0x7);
    }

    /// Returns true when a zero-immediate compressed LUI encoding is a Zcmop instruction.
    private static boolean isCompressedMopRegister(int register) {
        return register < 16 && (register & 1) != 0;
    }

    /// Extracts and sign-extends an I-format immediate.
    private static long iImmediate(int raw) {
        return signExtend(raw >>> 20, 12);
    }

    /// Extracts and sign-extends a U-format immediate.
    private static long uImmediate(int raw) {
        return signExtend(raw & 0xffff_f000L, 32);
    }

    /// Extracts and sign-extends an S-format immediate.
    private static long storeImmediate(int raw) {
        int value = ((raw >>> 7) & 0x1f) | (((raw >>> 25) & 0x7f) << 5);
        return signExtend(value, 12);
    }

    /// Extracts and sign-extends a B-format immediate.
    private static long branchImmediate(int raw) {
        int value = (((raw >>> 31) & 0x1) << 12)
                | (((raw >>> 7) & 0x1) << 11)
                | (((raw >>> 25) & 0x3f) << 5)
                | (((raw >>> 8) & 0xf) << 1);
        return signExtend(value, 13);
    }

    /// Extracts and sign-extends a J-format immediate.
    private static long jalImmediate(int raw) {
        int value = (((raw >>> 31) & 0x1) << 20)
                | (((raw >>> 12) & 0xff) << 12)
                | (((raw >>> 20) & 0x1) << 11)
                | (((raw >>> 21) & 0x3ff) << 1);
        return signExtend(value, 21);
    }

    /// Extracts a compressed CI-format sign-extended immediate.
    private static long cImmediate(int raw) {
        return signExtend((((raw >>> 12) & 0x1) << 5) | ((raw >>> 2) & 0x1f), 6);
    }

    /// Extracts a compressed shift amount.
    private static long cShiftImmediate(int raw) {
        return (((raw >>> 12) & 0x1) << 5) | ((raw >>> 2) & 0x1f);
    }

    /// Extracts a compressed ADDI4SPN immediate.
    private static int cAddi4spnImmediate(int raw) {
        return (((raw >>> 7) & 0xf) << 6)
                | (((raw >>> 11) & 0x3) << 4)
                | (((raw >>> 5) & 0x1) << 3)
                | (((raw >>> 6) & 0x1) << 2);
    }

    /// Extracts a compressed LW or SW immediate.
    private static int cLwImmediate(int raw) {
        return (((raw >>> 10) & 0x7) << 3)
                | (((raw >>> 6) & 0x1) << 2)
                | (((raw >>> 5) & 0x1) << 6);
    }

    /// Extracts a compressed LD or SD immediate.
    private static int cLdImmediate(int raw) {
        return (((raw >>> 10) & 0x7) << 3) | (((raw >>> 5) & 0x3) << 6);
    }

    /// Extracts a Zcb compressed byte load or store immediate.
    private static int cZcbByteImmediate(int raw) {
        return (((raw >>> 5) & 0x1) << 1) | ((raw >>> 6) & 0x1);
    }

    /// Extracts a Zcb compressed halfword load or store immediate.
    private static int cZcbHalfImmediate(int raw) {
        return ((raw >>> 5) & 0x1) << 1;
    }

    /// Extracts a compressed ADDI16SP immediate.
    private static long cAddi16SpImmediate(int raw) {
        int value = (((raw >>> 12) & 0x1) << 9)
                | (((raw >>> 6) & 0x1) << 4)
                | (((raw >>> 5) & 0x1) << 6)
                | (((raw >>> 3) & 0x3) << 7)
                | (((raw >>> 2) & 0x1) << 5);
        return signExtend(value, 10);
    }

    /// Extracts a compressed LUI immediate.
    private static long cLuiImmediate(int raw) {
        int value = (((raw >>> 12) & 0x1) << 17) | (((raw >>> 2) & 0x1f) << 12);
        return signExtend(value, 18);
    }

    /// Extracts a compressed jump immediate.
    private static long cJumpImmediate(int raw) {
        int value = (((raw >>> 12) & 0x1) << 11)
                | (((raw >>> 11) & 0x1) << 4)
                | (((raw >>> 9) & 0x3) << 8)
                | (((raw >>> 8) & 0x1) << 10)
                | (((raw >>> 7) & 0x1) << 6)
                | (((raw >>> 6) & 0x1) << 7)
                | (((raw >>> 3) & 0x7) << 1)
                | (((raw >>> 2) & 0x1) << 5);
        return signExtend(value, 12);
    }

    /// Extracts a compressed branch immediate.
    private static long cBranchImmediate(int raw) {
        int value = (((raw >>> 12) & 0x1) << 8)
                | (((raw >>> 10) & 0x3) << 3)
                | (((raw >>> 5) & 0x3) << 6)
                | (((raw >>> 3) & 0x3) << 1)
                | (((raw >>> 2) & 0x1) << 5);
        return signExtend(value, 9);
    }

    /// Extracts a compressed LWSP immediate.
    private static int cLwspImmediate(int raw) {
        return (((raw >>> 12) & 0x1) << 5)
                | (((raw >>> 4) & 0x7) << 2)
                | (((raw >>> 2) & 0x3) << 6);
    }

    /// Extracts a compressed LDSP immediate.
    private static int cLdspImmediate(int raw) {
        return (((raw >>> 12) & 0x1) << 5)
                | (((raw >>> 5) & 0x3) << 3)
                | (((raw >>> 2) & 0x7) << 6);
    }

    /// Extracts a compressed SWSP immediate.
    private static int cSwspImmediate(int raw) {
        return (((raw >>> 9) & 0xf) << 2) | (((raw >>> 7) & 0x3) << 6);
    }

    /// Extracts a compressed SDSP immediate.
    private static int cSdspImmediate(int raw) {
        return (((raw >>> 10) & 0x7) << 3) | (((raw >>> 7) & 0x7) << 6);
    }

    /// Sign-extends a value with the supplied source bit width.
    private static long signExtend(long value, int bits) {
        return (value << (Long.SIZE - bits)) >> (Long.SIZE - bits);
    }

    /// Requires a specific funct3 value.
    private static void requireFunct3(long address, int raw, int expected) {
        if (funct3(raw) != expected) {
            throw illegalException(address, raw);
        }
    }

    /// Throws an illegal instruction exception.
    private static DecodedInstruction illegal(long address, int raw) {
        throw illegalException(address, raw);
    }

    /// Creates an illegal instruction exception.
    private static RiscVException illegalException(long address, int raw) {
        return new RiscVException("Illegal instruction at 0x"
                + Long.toUnsignedString(address, 16)
                + ": 0x"
                + Integer.toUnsignedString(raw, 16));
    }
}
