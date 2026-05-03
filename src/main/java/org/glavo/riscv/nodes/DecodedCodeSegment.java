// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.nodes;

import org.glavo.riscv.constants.RiscVMicroOpcode;
import org.glavo.riscv.exception.MemoryAccessException;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.memory.MemoryAccess;
import org.glavo.riscv.memory.MemoryLayout;
import org.glavo.riscv.parser.DecodedBlock;
import org.glavo.riscv.parser.DecodedInstruction;
import org.glavo.riscv.parser.RiscVDecoder;
import org.glavo.riscv.runtime.DataIndependent;
import org.glavo.riscv.runtime.RiscVThreadState;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;

/// Stores decoded instructions for one executable guest page and runs integer hot blocks directly.
@NotNullByDefault
final class DecodedCodeSegment {
    /// Marker used before a PC-indexed instruction slot has been decoded.
    private static final byte UNDECODED_OPCODE = -1;

    /// Segment-local fast opcode for `addi rd, x0, immediate`.
    private static final byte LOAD_IMMEDIATE_OPCODE = -2;

    /// Segment-local fast opcode for integer register moves.
    private static final byte MOVE_REGISTER_OPCODE = -3;

    /// Five-bit mask for one packed RISC-V register index.
    private static final int REGISTER_MASK = 0x1f;

    /// Bit shift for the first source register in a packed operand.
    private static final int RS1_SHIFT = 5;

    /// Bit shift for the second source register in a packed operand.
    private static final int RS2_SHIFT = 10;

    /// Bit shift for the instruction length in halfwords in a packed operand.
    private static final int LENGTH_SHIFT = 15;

    /// Two-bit mask for the instruction length in halfwords.
    private static final int LENGTH_MASK = 0x3;

    /// Marks a slot that is the first instruction of a decoded block.
    private static final int FLAG_BLOCK_START = 1;

    /// Marks a decoded block that can run through the segment fast loop.
    private static final int FLAG_FAST_BLOCK = 1 << 1;

    /// Marks an instruction that terminates the decoded block.
    private static final int FLAG_TERMINATOR = 1 << 2;

    /// Bit shift for a block's instruction count in a packed decoder entry.
    private static final int BLOCK_INSTRUCTION_COUNT_SHIFT = 3;

    /// Eight-bit mask for a packed block instruction count.
    private static final int BLOCK_INSTRUCTION_COUNT_MASK = 0xff;

    /// Bit shift for a block's byte length in a packed decoder entry.
    private static final int BLOCK_BYTES_SHIFT = 11;

    /// Ten-bit mask for a packed block byte length.
    private static final int BLOCK_BYTES_MASK = 0x3ff;

    /// Inclusive first guest PC covered by this segment.
    private final long startPc;

    /// Exclusive first guest PC after this segment.
    private final long endPc;

    /// Immutable page-layout constants captured by this segment.
    private final MemoryLayout memoryLayout;

    /// Instruction-fetch generation that made this decoded segment visible.
    private final long instructionFetchGeneration;

    /// PC-indexed micro-opcode slots.
    private final byte[] opcodes;

    /// Packed register operands.
    private final int[] operands;

    /// Decoded immediate operands.
    private final long[] immediates;

    /// Packed per-slot decoder metadata.
    private final int[] metadata;

    /// Creates a decoded page segment for the supplied PC and generation.
    DecodedCodeSegment(Memory memory, long pc, long instructionFetchGeneration) {
        this.memoryLayout = memory.layout();
        this.startPc = pc & ~memoryLayout.pageMask();
        this.endPc = startPc + memoryLayout.pageSize();
        this.instructionFetchGeneration = instructionFetchGeneration;

        int slotCount = memoryLayout.pageSize() >>> 1;
        this.opcodes = new byte[slotCount];
        Arrays.fill(opcodes, UNDECODED_OPCODE);
        this.operands = new int[slotCount];
        this.immediates = new long[slotCount];
        this.metadata = new int[slotCount];
    }

    /// Returns true when this segment matches the supplied PC, layout, and instruction-fetch generation.
    boolean matches(long pc, MemoryLayout memoryLayout, long instructionFetchGeneration) {
        return contains(pc)
                && this.memoryLayout == memoryLayout
                && this.instructionFetchGeneration == instructionFetchGeneration;
    }

    /// Returns true when the supplied PC is inside this segment.
    private boolean contains(long pc) {
        // Memory windows are validated to stay inside the non-negative signed address range.
        return pc >= startPc && pc < endPc;
    }

    /// Ensures that a block starting at `pc` has been decoded and returns its start slot.
    int blockSlot(Memory memory, long pc) {
        int slot = slot(pc);
        if ((metadata[slot] & FLAG_BLOCK_START) == 0) {
            decodeBlock(memory, pc, slot);
        }
        return slot;
    }

    /// Returns true when the decoded block at `slot` can run through this segment.
    boolean isFastBlock(int slot) {
        return (metadata[slot] & FLAG_FAST_BLOCK) != 0;
    }

    /// Executes fast blocks inside this decoded segment until the slice is exhausted or dispatch must leave it.
    void executeFastSlice(
            Memory memory,
            long startPc,
            int maxBlocks,
            RiscVThreadState state,
            MemoryAccess access,
            long[] registers,
            long pointerMask,
            FastSliceResult result) {
        long pc = startPc;
        int executedBlocks = 0;
        int retiredInstructions = 0;
        boolean hitFallback = false;

        try {
            while (executedBlocks < maxBlocks && contains(pc)) {
                int slot = blockSlot(memory, pc);
                if (!isFastBlock(slot)) {
                    hitFallback = true;
                    break;
                }

                int blockMetadata = metadata[slot];
                pc = executeFastBlock(slot, blockMetadata, state, access, registers, pointerMask);
                retiredInstructions += blockInstructionCount(blockMetadata);
                executedBlocks++;
            }
        } finally {
            if (retiredInstructions != 0) {
                state.retireBlock(retiredInstructions);
            }
        }

        result.set(pc, executedBlocks, hitFallback);
    }

    /// Executes one previously decoded fast block and returns its next guest PC.
    private long executeFastBlock(
            int startSlot,
            int blockMetadata,
            RiscVThreadState state,
            MemoryAccess access,
            long[] registers,
            long pointerMask) {
        int instructionCount = blockInstructionCount(blockMetadata);
        long blockFallThroughPc = (address(startSlot) + blockBytes(blockMetadata)) & pointerMask;
        long pc = blockFallThroughPc;
        long faultPc = address(startSlot);

        try {
            for (int index = 0, slot = startSlot; index < instructionCount; index++) {
                byte opcode = opcodes[slot];
                int operand = operands[slot];
                int rd = operand & REGISTER_MASK;
                int rs1 = (operand >>> RS1_SHIFT) & REGISTER_MASK;
                int rs2 = (operand >>> RS2_SHIFT) & REGISTER_MASK;
                int lengthHalfwords = (operand >>> LENGTH_SHIFT) & LENGTH_MASK;
                int nextSlot = slot + lengthHalfwords;
                long instructionPc = address(slot);
                long sequentialPc = instructionPc + ((long) lengthHalfwords << 1);

                switch (opcode) {
                    case RiscVMicroOpcode.ADVANCE_PC -> slot = nextSlot;
                    case LOAD_IMMEDIATE_OPCODE -> {
                        writeRegister(registers, rd, immediates[slot]);
                        slot = nextSlot;
                    }
                    case MOVE_REGISTER_OPCODE -> {
                        writeRegister(registers, rd, registers[rs1]);
                        slot = nextSlot;
                    }
                    case RiscVMicroOpcode.LUI -> {
                        writeRegister(registers, rd, immediates[slot]);
                        slot = nextSlot;
                    }
                    case RiscVMicroOpcode.AUIPC -> {
                        writeRegister(registers, rd, instructionPc + immediates[slot]);
                        slot = nextSlot;
                    }
                    case RiscVMicroOpcode.JAL -> {
                        writeRegister(registers, rd, sequentialPc);
                        pc = (instructionPc + immediates[slot]) & pointerMask;
                    }
                    case RiscVMicroOpcode.JALR -> {
                        long target = (registers[rs1] + immediates[slot]) & ~1L;
                        writeRegister(registers, rd, sequentialPc);
                        pc = target & pointerMask;
                    }
                    case RiscVMicroOpcode.BEQ -> pc = branch(registers[rs1] == registers[rs2], instructionPc, immediates[slot], sequentialPc, pointerMask);
                    case RiscVMicroOpcode.BNE -> pc = branch(registers[rs1] != registers[rs2], instructionPc, immediates[slot], sequentialPc, pointerMask);
                    case RiscVMicroOpcode.BLT -> pc = branch(registers[rs1] < registers[rs2], instructionPc, immediates[slot], sequentialPc, pointerMask);
                    case RiscVMicroOpcode.BGE -> pc = branch(registers[rs1] >= registers[rs2], instructionPc, immediates[slot], sequentialPc, pointerMask);
                    case RiscVMicroOpcode.BLTU -> pc = branch(Long.compareUnsigned(registers[rs1], registers[rs2]) < 0, instructionPc, immediates[slot], sequentialPc, pointerMask);
                    case RiscVMicroOpcode.BGEU -> pc = branch(Long.compareUnsigned(registers[rs1], registers[rs2]) >= 0, instructionPc, immediates[slot], sequentialPc, pointerMask);
                    case RiscVMicroOpcode.LB -> {
                        faultPc = instructionPc;
                        writeRegister(registers, rd, access.readByte(loadAddress(registers, rs1, immediates[slot], pointerMask), memoryLayout));
                        slot = nextSlot;
                    }
                    case RiscVMicroOpcode.LH -> {
                        faultPc = instructionPc;
                        writeRegister(registers, rd, readShort(access, loadAddress(registers, rs1, immediates[slot], pointerMask)));
                        slot = nextSlot;
                    }
                    case RiscVMicroOpcode.LW -> {
                        faultPc = instructionPc;
                        writeRegister(registers, rd, readInt(access, loadAddress(registers, rs1, immediates[slot], pointerMask)));
                        slot = nextSlot;
                    }
                    case RiscVMicroOpcode.LD -> {
                        faultPc = instructionPc;
                        writeRegister(registers, rd, readLong(access, loadAddress(registers, rs1, immediates[slot], pointerMask)));
                        slot = nextSlot;
                    }
                    case RiscVMicroOpcode.LBU -> {
                        faultPc = instructionPc;
                        writeRegister(registers, rd, access.readUnsignedByte(loadAddress(registers, rs1, immediates[slot], pointerMask), memoryLayout));
                        slot = nextSlot;
                    }
                    case RiscVMicroOpcode.LHU -> {
                        faultPc = instructionPc;
                        writeRegister(registers, rd, readUnsignedShort(access, loadAddress(registers, rs1, immediates[slot], pointerMask)));
                        slot = nextSlot;
                    }
                    case RiscVMicroOpcode.LWU -> {
                        faultPc = instructionPc;
                        writeRegister(registers, rd, readUnsignedInt(access, loadAddress(registers, rs1, immediates[slot], pointerMask)));
                        slot = nextSlot;
                    }
                    case RiscVMicroOpcode.SB -> {
                        faultPc = instructionPc;
                        access.writeByte(loadAddress(registers, rs1, immediates[slot], pointerMask), (byte) registers[rs2], memoryLayout);
                        slot = nextSlot;
                    }
                    case RiscVMicroOpcode.SH -> {
                        faultPc = instructionPc;
                        writeShort(access, loadAddress(registers, rs1, immediates[slot], pointerMask), (short) registers[rs2]);
                        slot = nextSlot;
                    }
                    case RiscVMicroOpcode.SW -> {
                        faultPc = instructionPc;
                        writeInt(access, loadAddress(registers, rs1, immediates[slot], pointerMask), (int) registers[rs2]);
                        slot = nextSlot;
                    }
                    case RiscVMicroOpcode.SD -> {
                        faultPc = instructionPc;
                        writeLong(access, loadAddress(registers, rs1, immediates[slot], pointerMask), registers[rs2]);
                        slot = nextSlot;
                    }
                case RiscVMicroOpcode.ADDI -> {
                    writeRegister(registers, rd, registers[rs1] + immediates[slot]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SLTI -> {
                    writeRegister(registers, rd, DataIndependent.signedLessThan(registers[rs1], immediates[slot]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SLTIU -> {
                    writeRegister(registers, rd, DataIndependent.unsignedLessThan(registers[rs1], immediates[slot]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.XORI -> {
                    writeRegister(registers, rd, registers[rs1] ^ immediates[slot]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.ORI -> {
                    writeRegister(registers, rd, registers[rs1] | immediates[slot]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.ANDI -> {
                    writeRegister(registers, rd, registers[rs1] & immediates[slot]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SLLI -> {
                    writeRegister(registers, rd, registers[rs1] << immediates[slot]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SRLI -> {
                    writeRegister(registers, rd, registers[rs1] >>> immediates[slot]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SRAI -> {
                    writeRegister(registers, rd, registers[rs1] >> immediates[slot]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.ADDIW -> {
                    writeRegister(registers, rd, (int) (registers[rs1] + immediates[slot]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SLLIW -> {
                    writeRegister(registers, rd, (int) registers[rs1] << immediates[slot]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SRLIW -> {
                    writeRegister(registers, rd, (int) registers[rs1] >>> immediates[slot]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SRAIW -> {
                    writeRegister(registers, rd, (int) registers[rs1] >> immediates[slot]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.ADD -> {
                    writeRegister(registers, rd, registers[rs1] + registers[rs2]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SUB -> {
                    writeRegister(registers, rd, registers[rs1] - registers[rs2]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SLL -> {
                    writeRegister(registers, rd, registers[rs1] << (registers[rs2] & 0x3f));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SLT -> {
                    writeRegister(registers, rd, DataIndependent.signedLessThan(registers[rs1], registers[rs2]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SLTU -> {
                    writeRegister(registers, rd, DataIndependent.unsignedLessThan(registers[rs1], registers[rs2]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.XOR -> {
                    writeRegister(registers, rd, registers[rs1] ^ registers[rs2]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SRL -> {
                    writeRegister(registers, rd, registers[rs1] >>> (registers[rs2] & 0x3f));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SRA -> {
                    writeRegister(registers, rd, registers[rs1] >> (registers[rs2] & 0x3f));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.OR -> {
                    writeRegister(registers, rd, registers[rs1] | registers[rs2]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.AND -> {
                    writeRegister(registers, rd, registers[rs1] & registers[rs2]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.ADDW -> {
                    writeRegister(registers, rd, (int) registers[rs1] + (int) registers[rs2]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SUBW -> {
                    writeRegister(registers, rd, (int) registers[rs1] - (int) registers[rs2]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SLLW -> {
                    writeRegister(registers, rd, (int) registers[rs1] << (registers[rs2] & 0x1f));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SRLW -> {
                    writeRegister(registers, rd, (int) registers[rs1] >>> (registers[rs2] & 0x1f));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.SRAW -> {
                    writeRegister(registers, rd, (int) registers[rs1] >> (registers[rs2] & 0x1f));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.MUL -> {
                    writeRegister(registers, rd, registers[rs1] * registers[rs2]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.MULH -> {
                    writeRegister(registers, rd, DataIndependent.multiplyHighSigned(registers[rs1], registers[rs2]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.MULHSU -> {
                    writeRegister(registers, rd, DataIndependent.multiplyHighSignedUnsigned(registers[rs1], registers[rs2]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.MULHU -> {
                    writeRegister(registers, rd, DataIndependent.multiplyHighUnsigned(registers[rs1], registers[rs2]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.DIV -> {
                    writeRegister(registers, rd, divideSigned(registers[rs1], registers[rs2]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.DIVU -> {
                    writeRegister(registers, rd, divideUnsigned(registers[rs1], registers[rs2]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.REM -> {
                    writeRegister(registers, rd, remainderSigned(registers[rs1], registers[rs2]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.REMU -> {
                    writeRegister(registers, rd, remainderUnsigned(registers[rs1], registers[rs2]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.MULW -> {
                    writeRegister(registers, rd, (int) registers[rs1] * (int) registers[rs2]);
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.DIVW -> {
                    writeRegister(registers, rd, divideSignedWord((int) registers[rs1], (int) registers[rs2]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.DIVUW -> {
                    writeRegister(registers, rd, divideUnsignedWord((int) registers[rs1], (int) registers[rs2]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.REMW -> {
                    writeRegister(registers, rd, remainderSignedWord((int) registers[rs1], (int) registers[rs2]));
                    slot = nextSlot;
                }
                case RiscVMicroOpcode.REMUW -> {
                    writeRegister(registers, rd, remainderUnsignedWord((int) registers[rs1], (int) registers[rs2]));
                    slot = nextSlot;
                }
                default -> throw new AssertionError("Unsupported segment fast opcode: " + opcode);
            }
        }
        } catch (MemoryAccessException exception) {
            state.setPc(faultPc);
            throw exception;
        }

        if ((blockMetadata & FLAG_TERMINATOR) == 0) {
            return blockFallThroughPc;
        }
        return pc & pointerMask;
    }

    /// Reusable result container for one fast segment slice.
    static final class FastSliceResult {
        private long pc;
        private int executedBlocks;
        private boolean hitFallback;

        /// Returns the next guest PC after the fast slice.
        long pc() {
            return pc;
        }

        /// Returns the number of fast blocks completed by the slice.
        int executedBlocks() {
            return executedBlocks;
        }

        /// Returns true when execution stopped at a decoded block that needs the fallback dispatcher.
        boolean hitFallback() {
            return hitFallback;
        }

        /// Stores the latest fast slice result.
        private void set(long pc, int executedBlocks, boolean hitFallback) {
            this.pc = pc;
            this.executedBlocks = executedBlocks;
            this.hitFallback = hitFallback;
        }
    }

    /// Decodes and installs one block into this PC-indexed segment.
    private void decodeBlock(Memory memory, long pc, int startSlot) {
        DecodedBlock block = RiscVDecoder.decodeBlock(memory, pc);
        DecodedInstruction @Unmodifiable [] instructions = block.instructions();
        boolean fastBlock = true;
        int totalBlockBytes = 0;

        for (DecodedInstruction instruction : instructions) {
            totalBlockBytes += instruction.length();
            if (!contains(instruction.address())) {
                fastBlock = false;
                continue;
            }

            byte opcode = rewriteOpcode(RiscVMicroBlockCompiler.opcode(instruction.operation()), instruction);
            int slot = slot(instruction.address());
            opcodes[slot] = opcode;
            operands[slot] = packOperand(
                    instruction.rd(),
                    instruction.rs1(),
                    instruction.rs2(),
                    instruction.length());
            immediates[slot] = instruction.immediate();
            metadata[slot] = instruction.terminator() ? FLAG_TERMINATOR : 0;

            fastBlock &= isFastOpcode(opcode);
        }

        int blockMetadata = metadata[startSlot] | FLAG_BLOCK_START;
        if (block.endsWithTerminator()) {
            blockMetadata |= FLAG_TERMINATOR;
        }
        if (fastBlock) {
            blockMetadata |= FLAG_FAST_BLOCK;
        }
        metadata[startSlot] = blockMetadata
                | (instructions.length << BLOCK_INSTRUCTION_COUNT_SHIFT)
                | (totalBlockBytes << BLOCK_BYTES_SHIFT);
    }

    /// Converts a guest PC to a halfword-indexed segment slot.
    private int slot(long pc) {
        return (int) ((pc - startPc) >>> 1);
    }

    /// Reconstructs a guest instruction address from a segment slot.
    private long address(int slot) {
        return startPc + ((long) slot << 1);
    }

    /// Returns the instruction count stored in a decoded block-start entry.
    private static int blockInstructionCount(int metadata) {
        return (metadata >>> BLOCK_INSTRUCTION_COUNT_SHIFT) & BLOCK_INSTRUCTION_COUNT_MASK;
    }

    /// Returns the byte count stored in a decoded block-start entry.
    private static int blockBytes(int metadata) {
        return (metadata >>> BLOCK_BYTES_SHIFT) & BLOCK_BYTES_MASK;
    }

    /// Packs register operands and instruction length into one decoder entry word.
    private static int packOperand(int rd, int rs1, int rs2, int length) {
        return rd
                | (rs1 << RS1_SHIFT)
                | (rs2 << RS2_SHIFT)
                | ((length >>> 1) << LENGTH_SHIFT);
    }

    /// Returns true when this micro-op has a direct body in the segment fast loop.
    private static boolean isFastOpcode(byte opcode) {
        return switch (opcode) {
            case LOAD_IMMEDIATE_OPCODE,
                    MOVE_REGISTER_OPCODE,
                    RiscVMicroOpcode.ADVANCE_PC,
                    RiscVMicroOpcode.LUI,
                    RiscVMicroOpcode.AUIPC,
                    RiscVMicroOpcode.JAL,
                    RiscVMicroOpcode.JALR,
                    RiscVMicroOpcode.BEQ,
                    RiscVMicroOpcode.BNE,
                    RiscVMicroOpcode.BLT,
                    RiscVMicroOpcode.BGE,
                    RiscVMicroOpcode.BLTU,
                    RiscVMicroOpcode.BGEU,
                    RiscVMicroOpcode.LB,
                    RiscVMicroOpcode.LH,
                    RiscVMicroOpcode.LW,
                    RiscVMicroOpcode.LD,
                    RiscVMicroOpcode.LBU,
                    RiscVMicroOpcode.LHU,
                    RiscVMicroOpcode.LWU,
                    RiscVMicroOpcode.SB,
                    RiscVMicroOpcode.SH,
                    RiscVMicroOpcode.SW,
                    RiscVMicroOpcode.SD,
                    RiscVMicroOpcode.ADDI,
                    RiscVMicroOpcode.SLTI,
                    RiscVMicroOpcode.SLTIU,
                    RiscVMicroOpcode.XORI,
                    RiscVMicroOpcode.ORI,
                    RiscVMicroOpcode.ANDI,
                    RiscVMicroOpcode.SLLI,
                    RiscVMicroOpcode.SRLI,
                    RiscVMicroOpcode.SRAI,
                    RiscVMicroOpcode.ADDIW,
                    RiscVMicroOpcode.SLLIW,
                    RiscVMicroOpcode.SRLIW,
                    RiscVMicroOpcode.SRAIW,
                    RiscVMicroOpcode.ADD,
                    RiscVMicroOpcode.SUB,
                    RiscVMicroOpcode.SLL,
                    RiscVMicroOpcode.SLT,
                    RiscVMicroOpcode.SLTU,
                    RiscVMicroOpcode.XOR,
                    RiscVMicroOpcode.SRL,
                    RiscVMicroOpcode.SRA,
                    RiscVMicroOpcode.OR,
                    RiscVMicroOpcode.AND,
                    RiscVMicroOpcode.ADDW,
                    RiscVMicroOpcode.SUBW,
                    RiscVMicroOpcode.SLLW,
                    RiscVMicroOpcode.SRLW,
                    RiscVMicroOpcode.SRAW,
                    RiscVMicroOpcode.MUL,
                    RiscVMicroOpcode.MULH,
                    RiscVMicroOpcode.MULHSU,
                    RiscVMicroOpcode.MULHU,
                    RiscVMicroOpcode.DIV,
                    RiscVMicroOpcode.DIVU,
                    RiscVMicroOpcode.REM,
                    RiscVMicroOpcode.REMU,
                    RiscVMicroOpcode.MULW,
                    RiscVMicroOpcode.DIVW,
                    RiscVMicroOpcode.DIVUW,
                    RiscVMicroOpcode.REMW,
                    RiscVMicroOpcode.REMUW -> true;
            default -> false;
        };
    }

    /// Rewrites common decoded operations into cheaper segment-local fast opcodes.
    private static byte rewriteOpcode(byte opcode, DecodedInstruction instruction) {
        if (opcode == RiscVMicroOpcode.ADDI) {
            if (instruction.rs1() == 0) {
                return LOAD_IMMEDIATE_OPCODE;
            }
            if (instruction.immediate() == 0) {
                return MOVE_REGISTER_OPCODE;
            }
        }
        if (opcode == RiscVMicroOpcode.ADD) {
            if (instruction.rs2() == 0) {
                return MOVE_REGISTER_OPCODE;
            }
        }
        return opcode;
    }

    /// Writes a decoded integer register and preserves the hardwired zero register.
    private static void writeRegister(long[] registers, int index, long value) {
        if (index != 0) {
            registers[index] = value;
        }
    }

    /// Computes a conditional branch target.
    private static long branch(boolean taken, long address, long immediate, long nextPc, long pointerMask) {
        return taken ? (address + immediate) & pointerMask : nextPc;
    }

    /// Computes a memory address from `rs1` and an immediate.
    private static long loadAddress(long[] registers, int rs1, long immediate, long pointerMask) {
        return (registers[rs1] + immediate) & pointerMask;
    }

    /// Reads a 16-bit value, using the aligned single-page helper when possible.
    private short readShort(MemoryAccess access, long address) {
        return (address & (Short.BYTES - 1L)) == 0
                ? access.readAlignedShort(address, memoryLayout)
                : access.readShort(address, memoryLayout);
    }

    /// Reads an unsigned 16-bit value, using the aligned single-page helper when possible.
    private int readUnsignedShort(MemoryAccess access, long address) {
        return readShort(access, address) & 0xffff;
    }

    /// Reads a 32-bit value, using the aligned single-page helper when possible.
    private int readInt(MemoryAccess access, long address) {
        return (address & (Integer.BYTES - 1L)) == 0
                ? access.readAlignedInt(address, memoryLayout)
                : access.readInt(address, memoryLayout);
    }

    /// Reads an unsigned 32-bit value, using the aligned single-page helper when possible.
    private long readUnsignedInt(MemoryAccess access, long address) {
        return readInt(access, address) & 0xffff_ffffL;
    }

    /// Reads a 64-bit value, using the aligned single-page helper when possible.
    private long readLong(MemoryAccess access, long address) {
        return (address & (Long.BYTES - 1L)) == 0
                ? access.readAlignedLong(address, memoryLayout)
                : access.readLong(address, memoryLayout);
    }

    /// Writes a 16-bit value, using the aligned single-page helper when possible.
    private void writeShort(MemoryAccess access, long address, short value) {
        if ((address & (Short.BYTES - 1L)) == 0) {
            access.writeAlignedShort(address, value, memoryLayout);
        } else {
            access.writeShort(address, value, memoryLayout);
        }
    }

    /// Writes a 32-bit value, using the aligned single-page helper when possible.
    private void writeInt(MemoryAccess access, long address, int value) {
        if ((address & (Integer.BYTES - 1L)) == 0) {
            access.writeAlignedInt(address, value, memoryLayout);
        } else {
            access.writeInt(address, value, memoryLayout);
        }
    }

    /// Writes a 64-bit value, using the aligned single-page helper when possible.
    private void writeLong(MemoryAccess access, long address, long value) {
        if ((address & (Long.BYTES - 1L)) == 0) {
            access.writeAlignedLong(address, value, memoryLayout);
        } else {
            access.writeLong(address, value, memoryLayout);
        }
    }

    /// Divides signed 64-bit values using RISC-V division edge-case results.
    private static long divideSigned(long dividend, long divisor) {
        if (divisor == 0) {
            return -1L;
        }
        if (dividend == Long.MIN_VALUE && divisor == -1) {
            return dividend;
        }
        return dividend / divisor;
    }

    /// Divides unsigned 64-bit values using RISC-V division edge-case results.
    private static long divideUnsigned(long dividend, long divisor) {
        return divisor == 0 ? -1L : Long.divideUnsigned(dividend, divisor);
    }

    /// Computes signed 64-bit remainder using RISC-V division edge-case results.
    private static long remainderSigned(long dividend, long divisor) {
        if (divisor == 0) {
            return dividend;
        }
        if (dividend == Long.MIN_VALUE && divisor == -1) {
            return 0;
        }
        return dividend % divisor;
    }

    /// Computes unsigned 64-bit remainder using RISC-V division edge-case results.
    private static long remainderUnsigned(long dividend, long divisor) {
        return divisor == 0 ? dividend : Long.remainderUnsigned(dividend, divisor);
    }

    /// Divides signed 32-bit values using RISC-V word division edge-case results.
    private static int divideSignedWord(int dividend, int divisor) {
        if (divisor == 0) {
            return -1;
        }
        if (dividend == Integer.MIN_VALUE && divisor == -1) {
            return dividend;
        }
        return dividend / divisor;
    }

    /// Divides unsigned 32-bit values using RISC-V word division edge-case results.
    private static int divideUnsignedWord(int dividend, int divisor) {
        return divisor == 0 ? -1 : (int) Integer.divideUnsigned(dividend, divisor);
    }

    /// Computes signed 32-bit remainder using RISC-V word division edge-case results.
    private static int remainderSignedWord(int dividend, int divisor) {
        if (divisor == 0) {
            return dividend;
        }
        if (dividend == Integer.MIN_VALUE && divisor == -1) {
            return 0;
        }
        return dividend % divisor;
    }

    /// Computes unsigned 32-bit remainder using RISC-V word division edge-case results.
    private static int remainderUnsignedWord(int dividend, int divisor) {
        return divisor == 0 ? dividend : Integer.remainderUnsigned(dividend, divisor);
    }
}
