package org.glavo.riscv;

import org.glavo.riscv.InstructionNode.Operation;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;

/// Decodes RV64IMAC instructions from guest memory into executable nodes.
@NotNullByDefault
public final class RiscVDecoder {
    /// The maximum number of straight-line instructions decoded into one block.
    private static final int MAX_BLOCK_INSTRUCTIONS = 64;

    /// Prevents construction of this utility class.
    private RiscVDecoder() {
    }

    /// Decodes a basic block starting at the supplied guest program counter.
    public static BlockNode decodeBlock(Memory memory, long startPc) {
        ArrayList<InstructionNode> instructions = new ArrayList<>();
        long pc = startPc;
        for (int count = 0; count < MAX_BLOCK_INSTRUCTIONS; count++) {
            InstructionNode instruction = decode(memory, pc);
            instructions.add(instruction);
            pc += instruction.length();
            if (instruction.isTerminator()) {
                break;
            }
        }

        return new BlockNode(instructions.toArray(InstructionNode[]::new));
    }

    /// Decodes one instruction at the supplied guest address.
    private static InstructionNode decode(Memory memory, long address) {
        int half = memory.readUnsignedShort(address);
        if ((half & 0b11) != 0b11) {
            return decodeCompressed(address, half);
        }

        return decodeInt(address, memory.readInstructionInt(address));
    }

    /// Decodes a 32-bit instruction.
    private static InstructionNode decodeInt(long address, int raw) {
        int opcode = raw & 0x7f;
        return switch (opcode) {
            case 0x37 -> instruction(address, raw, Operation.LUI, rd(raw), 0, 0, uImmediate(raw), false);
            case 0x17 -> instruction(address, raw, Operation.AUIPC, rd(raw), 0, 0, uImmediate(raw), false);
            case 0x6f -> instruction(address, raw, Operation.JAL, rd(raw), 0, 0, jalImmediate(raw), true);
            case 0x67 -> decodeJalr(address, raw);
            case 0x63 -> decodeBranch(address, raw);
            case 0x03 -> decodeLoad(address, raw);
            case 0x23 -> decodeStore(address, raw);
            case 0x13 -> decodeOpImmediate(address, raw);
            case 0x1b -> decodeOpImmediateWord(address, raw);
            case 0x33 -> decodeOp(address, raw);
            case 0x3b -> decodeOpWord(address, raw);
            case 0x0f -> instruction(address, raw, Operation.FENCE, 0, 0, 0, 0, false);
            case 0x73 -> decodeSystem(address, raw);
            case 0x2f -> decodeAtomic(address, raw);
            default -> illegal(address, raw);
        };
    }

    /// Decodes `jalr`.
    private static InstructionNode decodeJalr(long address, int raw) {
        requireFunct3(address, raw, 0);
        return instruction(address, raw, Operation.JALR, rd(raw), rs1(raw), 0, iImmediate(raw), true);
    }

    /// Decodes branch instructions.
    private static InstructionNode decodeBranch(long address, int raw) {
        Operation operation = switch (funct3(raw)) {
            case 0 -> Operation.BEQ;
            case 1 -> Operation.BNE;
            case 4 -> Operation.BLT;
            case 5 -> Operation.BGE;
            case 6 -> Operation.BLTU;
            case 7 -> Operation.BGEU;
            default -> throw illegalException(address, raw);
        };
        return instruction(address, raw, operation, 0, rs1(raw), rs2(raw), branchImmediate(raw), true);
    }

    /// Decodes load instructions.
    private static InstructionNode decodeLoad(long address, int raw) {
        Operation operation = switch (funct3(raw)) {
            case 0 -> Operation.LB;
            case 1 -> Operation.LH;
            case 2 -> Operation.LW;
            case 3 -> Operation.LD;
            case 4 -> Operation.LBU;
            case 5 -> Operation.LHU;
            case 6 -> Operation.LWU;
            default -> throw illegalException(address, raw);
        };
        return instruction(address, raw, operation, rd(raw), rs1(raw), 0, iImmediate(raw), false);
    }

    /// Decodes store instructions.
    private static InstructionNode decodeStore(long address, int raw) {
        Operation operation = switch (funct3(raw)) {
            case 0 -> Operation.SB;
            case 1 -> Operation.SH;
            case 2 -> Operation.SW;
            case 3 -> Operation.SD;
            default -> throw illegalException(address, raw);
        };
        return instruction(address, raw, operation, 0, rs1(raw), rs2(raw), storeImmediate(raw), false);
    }

    /// Decodes integer immediate arithmetic instructions.
    private static InstructionNode decodeOpImmediate(long address, int raw) {
        int funct3 = funct3(raw);
        Operation operation = switch (funct3) {
            case 0 -> Operation.ADDI;
            case 2 -> Operation.SLTI;
            case 3 -> Operation.SLTIU;
            case 4 -> Operation.XORI;
            case 6 -> Operation.ORI;
            case 7 -> Operation.ANDI;
            case 1 -> {
                int imm = raw >>> 20;
                if ((imm & ~0x3f) != 0) {
                    throw illegalException(address, raw);
                }
                yield Operation.SLLI;
            }
            case 5 -> {
                int funct6 = (raw >>> 26) & 0x3f;
                if (funct6 == 0) {
                    yield Operation.SRLI;
                }
                if (funct6 == 0x10) {
                    yield Operation.SRAI;
                }
                throw illegalException(address, raw);
            }
            default -> throw illegalException(address, raw);
        };
        long immediate = (funct3 == 1 || funct3 == 5) ? ((raw >>> 20) & 0x3f) : iImmediate(raw);
        return instruction(address, raw, operation, rd(raw), rs1(raw), 0, immediate, false);
    }

    /// Decodes word-width immediate arithmetic instructions.
    private static InstructionNode decodeOpImmediateWord(long address, int raw) {
        int shift = (raw >>> 20) & 0x1f;
        Operation operation = switch (funct3(raw)) {
            case 0 -> Operation.ADDIW;
            case 1 -> {
                if (((raw >>> 25) & 0x7f) != 0) {
                    throw illegalException(address, raw);
                }
                yield Operation.SLLIW;
            }
            case 5 -> {
                int funct7 = (raw >>> 25) & 0x7f;
                if (funct7 == 0) {
                    yield Operation.SRLIW;
                }
                if (funct7 == 0x20) {
                    yield Operation.SRAIW;
                }
                throw illegalException(address, raw);
            }
            default -> throw illegalException(address, raw);
        };
        long immediate = funct3(raw) == 0 ? iImmediate(raw) : shift;
        return instruction(address, raw, operation, rd(raw), rs1(raw), 0, immediate, false);
    }

    /// Decodes register arithmetic instructions.
    private static InstructionNode decodeOp(long address, int raw) {
        int funct3 = funct3(raw);
        int funct7 = (raw >>> 25) & 0x7f;
        Operation operation = switch (funct7) {
            case 0x00 -> switch (funct3) {
                case 0 -> Operation.ADD;
                case 1 -> Operation.SLL;
                case 2 -> Operation.SLT;
                case 3 -> Operation.SLTU;
                case 4 -> Operation.XOR;
                case 5 -> Operation.SRL;
                case 6 -> Operation.OR;
                case 7 -> Operation.AND;
                default -> throw illegalException(address, raw);
            };
            case 0x20 -> switch (funct3) {
                case 0 -> Operation.SUB;
                case 5 -> Operation.SRA;
                default -> throw illegalException(address, raw);
            };
            case 0x01 -> switch (funct3) {
                case 0 -> Operation.MUL;
                case 1 -> Operation.MULH;
                case 2 -> Operation.MULHSU;
                case 3 -> Operation.MULHU;
                case 4 -> Operation.DIV;
                case 5 -> Operation.DIVU;
                case 6 -> Operation.REM;
                case 7 -> Operation.REMU;
                default -> throw illegalException(address, raw);
            };
            default -> throw illegalException(address, raw);
        };
        return instruction(address, raw, operation, rd(raw), rs1(raw), rs2(raw), 0, false);
    }

    /// Decodes word-width register arithmetic instructions.
    private static InstructionNode decodeOpWord(long address, int raw) {
        int funct3 = funct3(raw);
        int funct7 = (raw >>> 25) & 0x7f;
        Operation operation = switch (funct7) {
            case 0x00 -> switch (funct3) {
                case 0 -> Operation.ADDW;
                case 1 -> Operation.SLLW;
                case 5 -> Operation.SRLW;
                default -> throw illegalException(address, raw);
            };
            case 0x20 -> switch (funct3) {
                case 0 -> Operation.SUBW;
                case 5 -> Operation.SRAW;
                default -> throw illegalException(address, raw);
            };
            case 0x01 -> switch (funct3) {
                case 0 -> Operation.MULW;
                case 4 -> Operation.DIVW;
                case 5 -> Operation.DIVUW;
                case 6 -> Operation.REMW;
                case 7 -> Operation.REMUW;
                default -> throw illegalException(address, raw);
            };
            default -> throw illegalException(address, raw);
        };
        return instruction(address, raw, operation, rd(raw), rs1(raw), rs2(raw), 0, false);
    }

    /// Decodes system instructions.
    private static InstructionNode decodeSystem(long address, int raw) {
        if (raw == 0x0000_0073) {
            return instruction(address, raw, Operation.ECALL, 0, 0, 0, 0, true);
        }
        if (raw == 0x0010_0073) {
            return instruction(address, raw, Operation.EBREAK, 0, 0, 0, 0, true);
        }
        throw illegalException(address, raw);
    }

    /// Decodes atomic memory instructions.
    private static InstructionNode decodeAtomic(long address, int raw) {
        int width = funct3(raw);
        int funct5 = (raw >>> 27) & 0x1f;
        Operation operation = switch (width) {
            case 2 -> atomicWordOperation(address, raw, funct5);
            case 3 -> atomicDoubleOperation(address, raw, funct5);
            default -> throw illegalException(address, raw);
        };
        if ((operation == Operation.LR_W || operation == Operation.LR_D) && rs2(raw) != 0) {
            throw illegalException(address, raw);
        }
        return instruction(address, raw, operation, rd(raw), rs1(raw), rs2(raw), 0, false);
    }

    /// Decodes the word-width atomic operation field.
    private static Operation atomicWordOperation(long address, int raw, int funct5) {
        return switch (funct5) {
            case 0x02 -> Operation.LR_W;
            case 0x03 -> Operation.SC_W;
            case 0x01 -> Operation.AMOSWAP_W;
            case 0x00 -> Operation.AMOADD_W;
            case 0x04 -> Operation.AMOXOR_W;
            case 0x0c -> Operation.AMOAND_W;
            case 0x08 -> Operation.AMOOR_W;
            case 0x10 -> Operation.AMOMIN_W;
            case 0x14 -> Operation.AMOMAX_W;
            case 0x18 -> Operation.AMOMINU_W;
            case 0x1c -> Operation.AMOMAXU_W;
            default -> throw illegalException(address, raw);
        };
    }

    /// Decodes the doubleword-width atomic operation field.
    private static Operation atomicDoubleOperation(long address, int raw, int funct5) {
        return switch (funct5) {
            case 0x02 -> Operation.LR_D;
            case 0x03 -> Operation.SC_D;
            case 0x01 -> Operation.AMOSWAP_D;
            case 0x00 -> Operation.AMOADD_D;
            case 0x04 -> Operation.AMOXOR_D;
            case 0x0c -> Operation.AMOAND_D;
            case 0x08 -> Operation.AMOOR_D;
            case 0x10 -> Operation.AMOMIN_D;
            case 0x14 -> Operation.AMOMAX_D;
            case 0x18 -> Operation.AMOMINU_D;
            case 0x1c -> Operation.AMOMAXU_D;
            default -> throw illegalException(address, raw);
        };
    }

    /// Decodes a 16-bit compressed instruction.
    private static InstructionNode decodeCompressed(long address, int raw) {
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
    private static InstructionNode decodeCompressedQuadrant0(long address, int raw, int funct3) {
        int rd = compressedRegister(raw >>> 2);
        int rs1 = compressedRegister(raw >>> 7);
        int rs2 = compressedRegister(raw >>> 2);
        return switch (funct3) {
            case 0 -> {
                int immediate = cAddi4spnImmediate(raw);
                if (immediate == 0) {
                    throw illegalException(address, raw);
                }
                yield compressed(address, raw, Operation.ADDI, rd, 2, 0, immediate, false);
            }
            case 2 -> compressed(address, raw, Operation.LW, rd, rs1, 0, cLwImmediate(raw), false);
            case 3 -> compressed(address, raw, Operation.LD, rd, rs1, 0, cLdImmediate(raw), false);
            case 6 -> compressed(address, raw, Operation.SW, 0, rs1, rs2, cLwImmediate(raw), false);
            case 7 -> compressed(address, raw, Operation.SD, 0, rs1, rs2, cLdImmediate(raw), false);
            default -> throw illegalException(address, raw);
        };
    }

    /// Decodes compressed quadrant 1 instructions.
    private static InstructionNode decodeCompressedQuadrant1(long address, int raw, int funct3) {
        int rd = (raw >>> 7) & 0x1f;
        long immediate = cImmediate(raw);
        return switch (funct3) {
            case 0 -> rd == 0
                    ? compressed(address, raw, Operation.NOP, 0, 0, 0, 0, false)
                    : compressed(address, raw, Operation.ADDI, rd, rd, 0, immediate, false);
            case 1 -> {
                if (rd == 0) {
                    throw illegalException(address, raw);
                }
                yield compressed(address, raw, Operation.ADDIW, rd, rd, 0, immediate, false);
            }
            case 2 -> rd == 0
                    ? compressed(address, raw, Operation.NOP, 0, 0, 0, 0, false)
                    : compressed(address, raw, Operation.ADDI, rd, 0, 0, immediate, false);
            case 3 -> decodeCompressedLuiOrAddi16Sp(address, raw, rd);
            case 4 -> decodeCompressedAlu(address, raw);
            case 5 -> compressed(address, raw, Operation.JAL, 0, 0, 0, cJumpImmediate(raw), true);
            case 6 -> compressed(address, raw, Operation.BEQ, 0, compressedRegister(raw >>> 7), 0, cBranchImmediate(raw), true);
            case 7 -> compressed(address, raw, Operation.BNE, 0, compressedRegister(raw >>> 7), 0, cBranchImmediate(raw), true);
            default -> throw illegalException(address, raw);
        };
    }

    /// Decodes compressed quadrant 2 instructions.
    private static InstructionNode decodeCompressedQuadrant2(long address, int raw, int funct3) {
        int rd = (raw >>> 7) & 0x1f;
        int rs2 = (raw >>> 2) & 0x1f;
        return switch (funct3) {
            case 0 -> rd == 0
                    ? compressed(address, raw, Operation.NOP, 0, 0, 0, 0, false)
                    : compressed(address, raw, Operation.SLLI, rd, rd, 0, cShiftImmediate(raw), false);
            case 2 -> {
                if (rd == 0) {
                    throw illegalException(address, raw);
                }
                yield compressed(address, raw, Operation.LW, rd, 2, 0, cLwspImmediate(raw), false);
            }
            case 3 -> {
                if (rd == 0) {
                    throw illegalException(address, raw);
                }
                yield compressed(address, raw, Operation.LD, rd, 2, 0, cLdspImmediate(raw), false);
            }
            case 4 -> decodeCompressedJumpMoveAdd(address, raw, rd, rs2);
            case 6 -> compressed(address, raw, Operation.SW, 0, 2, rs2, cSwspImmediate(raw), false);
            case 7 -> compressed(address, raw, Operation.SD, 0, 2, rs2, cSdspImmediate(raw), false);
            default -> throw illegalException(address, raw);
        };
    }

    /// Decodes compressed LUI and ADDI16SP.
    private static InstructionNode decodeCompressedLuiOrAddi16Sp(long address, int raw, int rd) {
        if (rd == 0) {
            throw illegalException(address, raw);
        }
        if (rd == 2) {
            long immediate = cAddi16SpImmediate(raw);
            if (immediate == 0) {
                throw illegalException(address, raw);
            }
            return compressed(address, raw, Operation.ADDI, 2, 2, 0, immediate, false);
        }

        long immediate = cLuiImmediate(raw);
        if (immediate == 0) {
            throw illegalException(address, raw);
        }
        return compressed(address, raw, Operation.LUI, rd, 0, 0, immediate, false);
    }

    /// Decodes compressed ALU instructions.
    private static InstructionNode decodeCompressedAlu(long address, int raw) {
        int rd = compressedRegister(raw >>> 7);
        int mode = (raw >>> 10) & 0x3;
        if (mode == 0) {
            return compressed(address, raw, Operation.SRLI, rd, rd, 0, cShiftImmediate(raw), false);
        }
        if (mode == 1) {
            return compressed(address, raw, Operation.SRAI, rd, rd, 0, cShiftImmediate(raw), false);
        }
        if (mode == 2) {
            return compressed(address, raw, Operation.ANDI, rd, rd, 0, cImmediate(raw), false);
        }

        int rs2 = compressedRegister(raw >>> 2);
        boolean word = ((raw >>> 12) & 1) != 0;
        int subop = (raw >>> 5) & 0x3;
        Operation operation = word
                ? switch (subop) {
                    case 0 -> Operation.SUBW;
                    case 1 -> Operation.ADDW;
                    default -> throw illegalException(address, raw);
                }
                : switch (subop) {
                    case 0 -> Operation.SUB;
                    case 1 -> Operation.XOR;
                    case 2 -> Operation.OR;
                    case 3 -> Operation.AND;
                    default -> throw illegalException(address, raw);
                };
        return compressed(address, raw, operation, rd, rd, rs2, 0, false);
    }

    /// Decodes compressed JR, MV, EBREAK, JALR, and ADD instructions.
    private static InstructionNode decodeCompressedJumpMoveAdd(long address, int raw, int rd, int rs2) {
        boolean highBit = ((raw >>> 12) & 1) != 0;
        if (!highBit && rs2 == 0) {
            if (rd == 0) {
                throw illegalException(address, raw);
            }
            return compressed(address, raw, Operation.JALR, 0, rd, 0, 0, true);
        }
        if (!highBit) {
            if (rd == 0) {
                throw illegalException(address, raw);
            }
            return compressed(address, raw, Operation.ADD, rd, 0, rs2, 0, false);
        }
        if (rd == 0 && rs2 == 0) {
            return compressed(address, raw, Operation.EBREAK, 0, 0, 0, 0, true);
        }
        if (rs2 == 0) {
            return compressed(address, raw, Operation.JALR, 1, rd, 0, 0, true);
        }
        return compressed(address, raw, Operation.ADD, rd, rd, rs2, 0, false);
    }

    /// Creates a 32-bit decoded instruction.
    private static InstructionNode instruction(
            long address,
            int raw,
            Operation operation,
            int rd,
            int rs1,
            int rs2,
            long immediate,
            boolean terminator) {
        return new InstructionNode(address, raw, Integer.BYTES, operation, rd, rs1, rs2, immediate, terminator);
    }

    /// Creates a 16-bit compressed decoded instruction.
    private static InstructionNode compressed(
            long address,
            int raw,
            Operation operation,
            int rd,
            int rs1,
            int rs2,
            long immediate,
            boolean terminator) {
        return new InstructionNode(address, raw, Short.BYTES, operation, rd, rs1, rs2, immediate, terminator);
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

    /// Maps a compressed register field to its full register index.
    private static int compressedRegister(int shiftedRaw) {
        return 8 + (shiftedRaw & 0x7);
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
    private static InstructionNode illegal(long address, int raw) {
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
