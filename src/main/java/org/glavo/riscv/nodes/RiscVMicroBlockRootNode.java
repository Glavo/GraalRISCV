// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.RootNode;
import org.glavo.riscv.RiscVLanguage;
import org.glavo.riscv.constants.RiscVMicroOpcode;
import org.glavo.riscv.exception.ProgramExitException;
import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.memory.MemoryAccess;
import org.glavo.riscv.memory.MemoryLayout;
import org.glavo.riscv.parser.RiscVOperation;
import org.glavo.riscv.runtime.DataIndependent;
import org.glavo.riscv.runtime.MachineState;
import org.glavo.riscv.runtime.RiscVInstructionSemantics;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Executes one decoded RISC-V basic block through the custom micro-bytecode interpreter.
@NotNullByDefault
final class RiscVMicroBlockNode extends Node {
    /// Execution mode used when tracing or instruction-budget checks are active.
    static final byte CHECKED_MODE = 0;

    /// Execution mode used when the fast path still needs precise per-instruction state.
    static final byte PRECISE_FAST_MODE = 1;

    /// Execution mode used when retired instructions can be counted once after the block.
    static final byte BATCHED_FAST_MODE = 2;

    /// Five-bit mask for one packed RISC-V register index.
    private static final int REGISTER_MASK = 0x1f;

    /// Bit shift for the first source register in a packed operand.
    private static final int RS1_SHIFT = 5;

    /// Bit shift for the second source register in a packed operand.
    private static final int RS2_SHIFT = 10;

    /// The immediate bit offset used for the packed floating-point format field.
    private static final int FLOATING_POINT_FORMAT_SHIFT = 3;

    /// The packed format value for single-precision floating-point operations.
    private static final int SINGLE_FLOAT_FORMAT = 0;

    /// The packed format value for half-precision floating-point operations.
    private static final int HALF_FLOAT_FORMAT = 2;

    /// The mask for a NaN-boxed single-precision value in an RV64 floating-point register.
    private static final long SINGLE_NAN_BOX_MASK = 0xffff_ffff_0000_0000L;

    /// The mask for a NaN-boxed half-precision value in an RV64 floating-point register.
    private static final long HALF_NAN_BOX_MASK = 0xffff_ffff_ffff_0000L;

    /// The canonical single-precision quiet NaN bit pattern.
    private static final int CANONICAL_SINGLE_NAN = 0x7fc0_0000;

    /// The canonical half-precision quiet NaN bit pattern.
    private static final int CANONICAL_HALF_NAN = 0x7e00;

    /// The canonical double-precision quiet NaN bit pattern.
    private static final long CANONICAL_DOUBLE_NAN = 0x7ff8_0000_0000_0000L;

    /// The floating-point invalid-operation exception flag.
    private static final int FLOATING_POINT_INVALID_OPERATION = 0x10;

    /// Immutable page-layout constants captured by this block for Truffle partial evaluation.
    @CompilationFinal
    private final MemoryLayout memoryLayout;

    /// The compact opcode stream for this block.
    @CompilationFinal(dimensions = 1)
    private final byte @Unmodifiable [] opcodes;

    /// Canonical operations for opcodes that still share a generic execution body.
    @CompilationFinal(dimensions = 1)
    private final RiscVOperation @Unmodifiable [] operations;

    /// Packed register operands for each opcode.
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

    /// The sequential PC after the final decoded instruction.
    private final long fallThroughPc;

    /// Whether the final instruction explicitly transferred control out of the block.
    private final boolean endsWithTerminator;

    /// The concrete execution mode selected when this block was compiled.
    private final byte executionMode;

    /// Creates a micro-bytecode block node with a concrete execution mode.
    RiscVMicroBlockNode(
            MemoryLayout memoryLayout,
            byte @Unmodifiable [] opcodes,
            RiscVOperation @Unmodifiable [] operations,
            int @Unmodifiable [] operands,
            int @Unmodifiable [] raws,
            long @Unmodifiable [] addresses,
            long @Unmodifiable [] nextPcs,
            long @Unmodifiable [] immediates,
            long fallThroughPc,
            boolean endsWithTerminator,
            byte executionMode) {
        this.memoryLayout = memoryLayout;
        this.opcodes = opcodes.clone();
        this.operations = operations.clone();
        this.operands = operands.clone();
        this.raws = raws.clone();
        this.addresses = addresses.clone();
        this.nextPcs = nextPcs.clone();
        this.immediates = immediates.clone();
        this.fallThroughPc = fallThroughPc;
        this.endsWithTerminator = endsWithTerminator;
        this.executionMode = executionMode;
    }

    /// Executes the block with the supplied machine state and memory access facade.
    void execute(MachineState state, MemoryAccess access) {
        Memory memory = state.memory();
        long[] registers = state.decodedRegisters();
        long pointerMask = state.pointerMask();
        switch (executionMode) {
            case BATCHED_FAST_MODE -> executeBatchedFast(state, memory, access, registers, pointerMask);
            case PRECISE_FAST_MODE -> executePreciseFast(state, memory, access, registers, pointerMask);
            case CHECKED_MODE -> executeChecked(state, memory, access, registers, pointerMask);
            default -> throw new AssertionError("Unknown block execution mode: " + executionMode);
        }
    }

    /// Executes this block when every instruction can retire without per-instruction checks.
    @ExplodeLoop
    private void executeBatchedFast(
            MachineState state,
            Memory memory,
            MemoryAccess access,
            long[] registers,
            long pointerMask) {
        for (int index = 0; index < opcodes.length; index++) {
            executeInstruction(state, memory, access, registers, index, BATCHED_FAST_MODE, pointerMask);
        }
        state.retireBlock(opcodes.length);
        if (!endsWithTerminator) {
            state.setPc(fallThroughPc);
        }
    }

    /// Executes this block when every instruction can retire without per-instruction checks.
    @ExplodeLoop
    private void executePreciseFast(
            MachineState state,
            Memory memory,
            MemoryAccess access,
            long[] registers,
            long pointerMask) {
        for (int index = 0; index < opcodes.length; index++) {
            executeInstruction(state, memory, access, registers, index, PRECISE_FAST_MODE, pointerMask);
        }
    }

    /// Executes this block when tracing or instruction-budget checks are active.
    @ExplodeLoop
    private void executeChecked(
            MachineState state,
            Memory memory,
            MemoryAccess access,
            long[] registers,
            long pointerMask) {
        for (int index = 0; index < opcodes.length; index++) {
            executeInstruction(state, memory, access, registers, index, CHECKED_MODE, pointerMask);
        }
    }

    /// Executes one micro-op by decoding its packed operand.
    private void executeInstruction(
            MachineState state,
            Memory memory,
            MemoryAccess access,
            long[] registers,
            int index,
            byte mode,
            long pointerMask) {
        byte opcode = opcodes[index];
        int operand = operands[index];
        switch (opcode) {
            case RiscVMicroOpcode.EXECUTE_OPERATION -> executeOperation(state, index, operand);
            case RiscVMicroOpcode.ADVANCE_PC -> {
                beginInstruction(state, index, mode);
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.LUI -> {
                beginInstruction(state, index, mode);
                writeRegister(registers, rd(operand), immediates[index]);
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.AUIPC -> {
                beginInstruction(state, index, mode);
                writeRegister(registers, rd(operand), addresses[index] + immediates[index]);
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.JAL -> {
                beginInstruction(state, index, mode);
                writeRegister(registers, rd(operand), nextPcs[index]);
                state.setPcFromInstruction(addresses[index], addresses[index] + immediates[index]);
            }
            case RiscVMicroOpcode.JALR -> {
                beginInstruction(state, index, mode);
                long target = (registers[rs1(operand)] + immediates[index]) & ~1L;
                writeRegister(registers, rd(operand), nextPcs[index]);
                state.setPcFromInstruction(addresses[index], target);
            }
            case RiscVMicroOpcode.BEQ -> {
                beginInstruction(state, index, mode);
                branch(state, registers[rs1(operand)] == registers[rs2(operand)], index);
            }
            case RiscVMicroOpcode.BNE -> {
                beginInstruction(state, index, mode);
                branch(state, registers[rs1(operand)] != registers[rs2(operand)], index);
            }
            case RiscVMicroOpcode.BLT -> {
                beginInstruction(state, index, mode);
                branch(state, registers[rs1(operand)] < registers[rs2(operand)], index);
            }
            case RiscVMicroOpcode.BGE -> {
                beginInstruction(state, index, mode);
                branch(state, registers[rs1(operand)] >= registers[rs2(operand)], index);
            }
            case RiscVMicroOpcode.BLTU -> {
                beginInstruction(state, index, mode);
                branch(state, Long.compareUnsigned(registers[rs1(operand)], registers[rs2(operand)]) < 0, index);
            }
            case RiscVMicroOpcode.BGEU -> {
                beginInstruction(state, index, mode);
                branch(state, Long.compareUnsigned(registers[rs1(operand)], registers[rs2(operand)]) >= 0, index);
            }
            case RiscVMicroOpcode.LB -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                writeRegister(registers, rd(operand), access.readByte(address, memoryLayout));
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.LH -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                writeRegister(registers, rd(operand), access.readShort(address, memoryLayout));
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.LW -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                writeRegister(registers, rd(operand), access.readInt(address, memoryLayout));
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.LD -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                writeRegister(registers, rd(operand), access.readLong(address, memoryLayout));
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.LBU -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                writeRegister(registers, rd(operand), access.readUnsignedByte(address, memoryLayout));
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.LHU -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                writeRegister(registers, rd(operand), access.readUnsignedShort(address, memoryLayout));
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.LWU -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                writeRegister(registers, rd(operand), access.readUnsignedInt(address, memoryLayout));
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.SB -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                access.writeByte(address, (byte) registers[rs2(operand)], memoryLayout);
                afterStore(state, address, Byte.BYTES, mode);
                state.clearReservation();
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.SH -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                access.writeShort(address, (short) registers[rs2(operand)], memoryLayout);
                afterStore(state, address, Short.BYTES, mode);
                state.clearReservation();
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.SW -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                access.writeInt(address, (int) registers[rs2(operand)], memoryLayout);
                afterStore(state, address, Integer.BYTES, mode);
                state.clearReservation();
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.SD -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                access.writeLong(address, registers[rs2(operand)], memoryLayout);
                afterStore(state, address, Long.BYTES, mode);
                state.clearReservation();
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.ADDI -> binaryImmediate(state, registers, index, operand, mode, registers[rs1(operand)] + immediates[index]);
            case RiscVMicroOpcode.SLTI -> binaryImmediate(state, registers, index, operand, mode, DataIndependent.signedLessThan(registers[rs1(operand)], immediates[index]));
            case RiscVMicroOpcode.SLTIU -> binaryImmediate(state, registers, index, operand, mode, DataIndependent.unsignedLessThan(registers[rs1(operand)], immediates[index]));
            case RiscVMicroOpcode.XORI -> binaryImmediate(state, registers, index, operand, mode, registers[rs1(operand)] ^ immediates[index]);
            case RiscVMicroOpcode.ORI -> binaryImmediate(state, registers, index, operand, mode, registers[rs1(operand)] | immediates[index]);
            case RiscVMicroOpcode.ANDI -> binaryImmediate(state, registers, index, operand, mode, registers[rs1(operand)] & immediates[index]);
            case RiscVMicroOpcode.SLLI -> binaryImmediate(state, registers, index, operand, mode, registers[rs1(operand)] << immediates[index]);
            case RiscVMicroOpcode.SRLI -> binaryImmediate(state, registers, index, operand, mode, registers[rs1(operand)] >>> immediates[index]);
            case RiscVMicroOpcode.SRAI -> binaryImmediate(state, registers, index, operand, mode, registers[rs1(operand)] >> immediates[index]);
            case RiscVMicroOpcode.ADDIW -> binaryImmediate(state, registers, index, operand, mode, (int) (registers[rs1(operand)] + immediates[index]));
            case RiscVMicroOpcode.SLLIW -> binaryImmediate(state, registers, index, operand, mode, (int) registers[rs1(operand)] << immediates[index]);
            case RiscVMicroOpcode.SRLIW -> binaryImmediate(state, registers, index, operand, mode, (int) registers[rs1(operand)] >>> immediates[index]);
            case RiscVMicroOpcode.SRAIW -> binaryImmediate(state, registers, index, operand, mode, (int) registers[rs1(operand)] >> immediates[index]);
            case RiscVMicroOpcode.ADD -> binaryRegister(state, registers, index, operand, mode, registers[rs1(operand)] + registers[rs2(operand)]);
            case RiscVMicroOpcode.SUB -> binaryRegister(state, registers, index, operand, mode, registers[rs1(operand)] - registers[rs2(operand)]);
            case RiscVMicroOpcode.SLL -> binaryRegister(state, registers, index, operand, mode, registers[rs1(operand)] << (registers[rs2(operand)] & 0x3f));
            case RiscVMicroOpcode.SLT -> binaryRegister(state, registers, index, operand, mode, DataIndependent.signedLessThan(registers[rs1(operand)], registers[rs2(operand)]));
            case RiscVMicroOpcode.SLTU -> binaryRegister(state, registers, index, operand, mode, DataIndependent.unsignedLessThan(registers[rs1(operand)], registers[rs2(operand)]));
            case RiscVMicroOpcode.XOR -> binaryRegister(state, registers, index, operand, mode, registers[rs1(operand)] ^ registers[rs2(operand)]);
            case RiscVMicroOpcode.SRL -> binaryRegister(state, registers, index, operand, mode, registers[rs1(operand)] >>> (registers[rs2(operand)] & 0x3f));
            case RiscVMicroOpcode.SRA -> binaryRegister(state, registers, index, operand, mode, registers[rs1(operand)] >> (registers[rs2(operand)] & 0x3f));
            case RiscVMicroOpcode.OR -> binaryRegister(state, registers, index, operand, mode, registers[rs1(operand)] | registers[rs2(operand)]);
            case RiscVMicroOpcode.AND -> binaryRegister(state, registers, index, operand, mode, registers[rs1(operand)] & registers[rs2(operand)]);
            case RiscVMicroOpcode.ADDW -> binaryRegister(state, registers, index, operand, mode, (int) registers[rs1(operand)] + (int) registers[rs2(operand)]);
            case RiscVMicroOpcode.SUBW -> binaryRegister(state, registers, index, operand, mode, (int) registers[rs1(operand)] - (int) registers[rs2(operand)]);
            case RiscVMicroOpcode.SLLW -> binaryRegister(state, registers, index, operand, mode, (int) registers[rs1(operand)] << (registers[rs2(operand)] & 0x1f));
            case RiscVMicroOpcode.SRLW -> binaryRegister(state, registers, index, operand, mode, (int) registers[rs1(operand)] >>> (registers[rs2(operand)] & 0x1f));
            case RiscVMicroOpcode.SRAW -> binaryRegister(state, registers, index, operand, mode, (int) registers[rs1(operand)] >> (registers[rs2(operand)] & 0x1f));
            case RiscVMicroOpcode.ECALL -> {
                beginInstruction(state, index, mode);
                if (mode == BATCHED_FAST_MODE) {
                    state.setPc(addresses[index]);
                }
                state.syscalls().handle(state, addresses[index]);
                if (state.pc() == addresses[index]) {
                    finishInstruction(state, index, mode);
                }
            }
            case RiscVMicroOpcode.EBREAK -> {
                beginInstruction(state, index, mode);
                throw new ProgramExitException(0);
            }
            case RiscVMicroOpcode.CSRRW -> {
                beginInstruction(state, index, mode);
                writeControlStatusRegister(state, registers, index, operand, mode, registers[rs1(operand)]);
            }
            case RiscVMicroOpcode.CSRRS -> {
                beginInstruction(state, index, mode);
                setClearControlStatusRegister(state, registers, index, operand, mode, registers[rs1(operand)], true);
            }
            case RiscVMicroOpcode.CSRRC -> {
                beginInstruction(state, index, mode);
                setClearControlStatusRegister(state, registers, index, operand, mode, registers[rs1(operand)], false);
            }
            case RiscVMicroOpcode.CSRRWI -> {
                beginInstruction(state, index, mode);
                writeControlStatusRegister(state, registers, index, operand, mode, rs1(operand));
            }
            case RiscVMicroOpcode.CSRRSI -> {
                beginInstruction(state, index, mode);
                setClearControlStatusRegister(state, registers, index, operand, mode, rs1(operand), true);
            }
            case RiscVMicroOpcode.CSRRCI -> {
                beginInstruction(state, index, mode);
                setClearControlStatusRegister(state, registers, index, operand, mode, rs1(operand), false);
            }
            case RiscVMicroOpcode.MUL -> binaryRegister(state, registers, index, operand, mode, registers[rs1(operand)] * registers[rs2(operand)]);
            case RiscVMicroOpcode.MULH -> binaryRegister(state, registers, index, operand, mode, DataIndependent.multiplyHighSigned(registers[rs1(operand)], registers[rs2(operand)]));
            case RiscVMicroOpcode.MULHSU -> binaryRegister(state, registers, index, operand, mode, DataIndependent.multiplyHighSignedUnsigned(registers[rs1(operand)], registers[rs2(operand)]));
            case RiscVMicroOpcode.MULHU -> binaryRegister(state, registers, index, operand, mode, DataIndependent.multiplyHighUnsigned(registers[rs1(operand)], registers[rs2(operand)]));
            case RiscVMicroOpcode.DIV -> binaryRegister(state, registers, index, operand, mode, divideSigned(registers[rs1(operand)], registers[rs2(operand)]));
            case RiscVMicroOpcode.DIVU -> binaryRegister(state, registers, index, operand, mode, divideUnsigned(registers[rs1(operand)], registers[rs2(operand)]));
            case RiscVMicroOpcode.REM -> binaryRegister(state, registers, index, operand, mode, remainderSigned(registers[rs1(operand)], registers[rs2(operand)]));
            case RiscVMicroOpcode.REMU -> binaryRegister(state, registers, index, operand, mode, remainderUnsigned(registers[rs1(operand)], registers[rs2(operand)]));
            case RiscVMicroOpcode.MULW -> binaryRegister(state, registers, index, operand, mode, (int) registers[rs1(operand)] * (int) registers[rs2(operand)]);
            case RiscVMicroOpcode.DIVW -> binaryRegister(state, registers, index, operand, mode, divideSignedWord((int) registers[rs1(operand)], (int) registers[rs2(operand)]));
            case RiscVMicroOpcode.DIVUW -> binaryRegister(state, registers, index, operand, mode, divideUnsignedWord((int) registers[rs1(operand)], (int) registers[rs2(operand)]));
            case RiscVMicroOpcode.REMW -> binaryRegister(state, registers, index, operand, mode, remainderSignedWord((int) registers[rs1(operand)], (int) registers[rs2(operand)]));
            case RiscVMicroOpcode.REMUW -> binaryRegister(state, registers, index, operand, mode, remainderUnsignedWord((int) registers[rs1(operand)], (int) registers[rs2(operand)]));
            case RiscVMicroOpcode.FLW -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                state.setDecodedFloatingPointRegister(rd(operand), 0xffff_ffff_0000_0000L | access.readUnsignedInt(address, memoryLayout));
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.FLD -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                state.setDecodedFloatingPointRegister(rd(operand), access.readLong(address, memoryLayout));
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.FSW -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                access.writeInt(address, (int) state.decodedFloatingPointRegister(rs2(operand)), memoryLayout);
                afterStore(state, address, Integer.BYTES, mode);
                state.clearReservation();
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.FSD -> {
                beginMemoryInstruction(state, index, mode);
                long address = loadAddress(registers, operand, index, pointerMask);
                access.writeLong(address, state.decodedFloatingPointRegister(rs2(operand)), memoryLayout);
                afterStore(state, address, Long.BYTES, mode);
                state.clearReservation();
                finishInstruction(state, index, mode);
            }
            case RiscVMicroOpcode.FSGNJ -> floatingPointSignInjection(state, index, operand, mode, SignInjectionKind.COPY);
            case RiscVMicroOpcode.FSGNJN -> floatingPointSignInjection(state, index, operand, mode, SignInjectionKind.NEGATE);
            case RiscVMicroOpcode.FSGNJX -> floatingPointSignInjection(state, index, operand, mode, SignInjectionKind.XOR);
            case RiscVMicroOpcode.FCLASS -> floatingPointClassify(state, registers, index, operand, mode);
            case RiscVMicroOpcode.FMV_X_FP -> moveFloatingPointToInteger(state, registers, index, operand, mode);
            case RiscVMicroOpcode.FMV_FP_X -> moveIntegerToFloatingPoint(state, registers, index, operand, mode);
            case RiscVMicroOpcode.FEQ -> floatingPointCompare(state, registers, index, operand, mode, CompareKind.EQUAL);
            case RiscVMicroOpcode.FLT -> floatingPointCompare(state, registers, index, operand, mode, CompareKind.LESS_THAN);
            case RiscVMicroOpcode.FLE -> floatingPointCompare(state, registers, index, operand, mode, CompareKind.LESS_THAN_OR_EQUAL);
            case RiscVMicroOpcode.FMIN -> floatingPointMinimumMaximum(state, index, operand, mode, true);
            case RiscVMicroOpcode.FMAX -> floatingPointMinimumMaximum(state, index, operand, mode, false);
            case RiscVMicroOpcode.FMADD -> floatingPointFusedMultiplyAdd(state, index, operand, mode, false, false);
            case RiscVMicroOpcode.FMSUB -> floatingPointFusedMultiplyAdd(state, index, operand, mode, false, true);
            case RiscVMicroOpcode.FNMSUB -> floatingPointFusedMultiplyAdd(state, index, operand, mode, true, false);
            case RiscVMicroOpcode.FNMADD -> floatingPointFusedMultiplyAdd(state, index, operand, mode, true, true);
            case RiscVMicroOpcode.FADD -> floatingPointArithmetic(state, index, operand, mode, '+');
            case RiscVMicroOpcode.FSUB -> floatingPointArithmetic(state, index, operand, mode, '-');
            case RiscVMicroOpcode.FMUL -> floatingPointArithmetic(state, index, operand, mode, '*');
            case RiscVMicroOpcode.FDIV -> floatingPointArithmetic(state, index, operand, mode, '/');
            case RiscVMicroOpcode.FSQRT -> floatingPointSquareRoot(state, index, operand, mode);
            case RiscVMicroOpcode.FCVT_S_D -> convertDoubleToSingle(state, index, operand, mode);
            case RiscVMicroOpcode.FCVT_D_S -> convertSingleToDouble(state, index, operand, mode);
            case RiscVMicroOpcode.FCVT_INT_FP -> convertFloatingPointToInteger(state, index, operand, mode);
            case RiscVMicroOpcode.FCVT_FP_INT -> convertIntegerToFloatingPoint(state, index, operand, mode);
            case RiscVMicroOpcode.LR_W -> lrWord(state, memory, access, registers, index, operand, mode, pointerMask);
            case RiscVMicroOpcode.LR_D -> lrDouble(state, memory, access, registers, index, operand, mode, pointerMask);
            case RiscVMicroOpcode.SC_W -> scWord(state, memory, access, registers, index, operand, mode, pointerMask);
            case RiscVMicroOpcode.SC_D -> scDouble(state, memory, access, registers, index, operand, mode, pointerMask);
            case RiscVMicroOpcode.AMOSWAP_W -> amoWord(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.SWAP);
            case RiscVMicroOpcode.AMOADD_W -> amoWord(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.ADD);
            case RiscVMicroOpcode.AMOXOR_W -> amoWord(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.XOR);
            case RiscVMicroOpcode.AMOAND_W -> amoWord(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.AND);
            case RiscVMicroOpcode.AMOOR_W -> amoWord(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.OR);
            case RiscVMicroOpcode.AMOMIN_W -> amoWord(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.MIN);
            case RiscVMicroOpcode.AMOMAX_W -> amoWord(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.MAX);
            case RiscVMicroOpcode.AMOMINU_W -> amoWord(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.MINU);
            case RiscVMicroOpcode.AMOMAXU_W -> amoWord(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.MAXU);
            case RiscVMicroOpcode.AMOSWAP_D -> amoDouble(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.SWAP);
            case RiscVMicroOpcode.AMOADD_D -> amoDouble(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.ADD);
            case RiscVMicroOpcode.AMOXOR_D -> amoDouble(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.XOR);
            case RiscVMicroOpcode.AMOAND_D -> amoDouble(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.AND);
            case RiscVMicroOpcode.AMOOR_D -> amoDouble(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.OR);
            case RiscVMicroOpcode.AMOMIN_D -> amoDouble(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.MIN);
            case RiscVMicroOpcode.AMOMAX_D -> amoDouble(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.MAX);
            case RiscVMicroOpcode.AMOMINU_D -> amoDouble(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.MINU);
            case RiscVMicroOpcode.AMOMAXU_D -> amoDouble(state, memory, access, registers, index, operand, mode, pointerMask, AmoKind.MAXU);
            default -> throw new RiscVException("Unknown micro-bytecode opcode: " + opcode);
        }
    }

    /// Executes a canonical operation that does not yet need a dedicated opcode value.
    private void executeOperation(MachineState state, int index, int operand) {
        RiscVOperation operation = operations[index];
        RiscVInstructionSemantics.executeMicro(
                state,
                operation,
                addresses[index],
                raws[index],
                nextPcs[index],
                rd(operand),
                rs1(operand),
                rs2(operand),
                immediates[index]);
    }

    /// Records the current instruction before running a direct micro-op body.
    private void beginInstruction(MachineState state, int index, byte mode) {
        if (mode == BATCHED_FAST_MODE) {
            return;
        }
        long address = addresses[index];
        state.setPc(address);
        if (mode == PRECISE_FAST_MODE) {
            state.retireInstructionUnchecked();
        } else if (mode == CHECKED_MODE) {
            state.beforeInstructionChecked(address, raws[index]);
        }
    }

    /// Records the current instruction when a batched block instruction can still fault.
    private void beginMemoryInstruction(MachineState state, int index, byte mode) {
        beginInstruction(state, index, mode);
        if (mode == BATCHED_FAST_MODE) {
            state.setPc(addresses[index]);
        }
    }

    /// Writes the sequential PC unless the block fast path will materialize it at block exit.
    private void finishInstruction(MachineState state, int index, byte mode) {
        if (mode != BATCHED_FAST_MODE) {
            state.setPc(nextPcs[index]);
        }
    }

    /// Writes a decoded integer register and preserves the hardwired zero register.
    private static void writeRegister(long[] registers, int index, long value) {
        if (index != 0) {
            registers[index] = value;
        }
    }

    /// Writes a conditional branch target.
    private void branch(MachineState state, boolean taken, int index) {
        state.setPcFromInstruction(
                addresses[index],
                taken ? addresses[index] + immediates[index] : nextPcs[index]);
    }

    /// Computes a memory address from `rs1` and an immediate.
    private long loadAddress(long[] registers, int operand, int index, long pointerMask) {
        return (registers[rs1(operand)] + immediates[index]) & pointerMask;
    }

    /// Writes the result of an immediate integer operation.
    private void binaryImmediate(MachineState state, long[] registers, int index, int operand, byte mode, long value) {
        beginInstruction(state, index, mode);
        writeRegister(registers, rd(operand), value);
        finishInstruction(state, index, mode);
    }

    /// Writes the result of a register integer operation.
    private void binaryRegister(MachineState state, long[] registers, int index, int operand, byte mode, long value) {
        beginInstruction(state, index, mode);
        writeRegister(registers, rd(operand), value);
        finishInstruction(state, index, mode);
    }

    /// Writes a CSR and returns the old value when the destination register is not `x0`.
    private void writeControlStatusRegister(MachineState state, long[] registers, int index, int operand, byte mode, long value) {
        int rd = rd(operand);
        long oldValue = rd == 0 ? 0 : state.readControlStatusRegister((int) immediates[index]);
        state.writeControlStatusRegister((int) immediates[index], value);
        writeRegister(registers, rd, oldValue);
        finishInstruction(state, index, mode);
    }

    /// Sets or clears CSR bits using the supplied mask value.
    private void setClearControlStatusRegister(
            MachineState state,
            long[] registers,
            int index,
            int operand,
            byte mode,
            long mask,
            boolean setBits) {
        long oldValue = state.readControlStatusRegister((int) immediates[index]);
        if (mask != 0) {
            state.writeControlStatusRegister((int) immediates[index], setBits ? oldValue | mask : oldValue & ~mask);
        }
        writeRegister(registers, rd(operand), oldValue);
        finishInstruction(state, index, mode);
    }

    /// Executes a bit-level floating-point sign-injection instruction.
    private void floatingPointSignInjection(
            MachineState state,
            int index,
            int operand,
            byte mode,
            SignInjectionKind kind) {
        beginInstruction(state, index, mode);
        if (floatingPointFormat(index) == SINGLE_FLOAT_FORMAT) {
            int left = readSingleBits(state, rs1(operand));
            int right = readSingleBits(state, rs2(operand));
            int sign = switch (kind) {
                case COPY -> right & 0x8000_0000;
                case NEGATE -> ~right & 0x8000_0000;
                case XOR -> (left ^ right) & 0x8000_0000;
            };
            writeSingleBits(state, rd(operand), (left & 0x7fff_ffff) | sign);
        } else {
            long left = state.decodedFloatingPointRegister(rs1(operand));
            long right = state.decodedFloatingPointRegister(rs2(operand));
            long sign = switch (kind) {
                case COPY -> right & Long.MIN_VALUE;
                case NEGATE -> ~right & Long.MIN_VALUE;
                case XOR -> (left ^ right) & Long.MIN_VALUE;
            };
            state.setDecodedFloatingPointRegister(rd(operand), (left & Long.MAX_VALUE) | sign);
        }
        finishInstruction(state, index, mode);
    }

    /// Executes a floating-point classify instruction.
    private void floatingPointClassify(MachineState state, long[] registers, int index, int operand, byte mode) {
        beginInstruction(state, index, mode);
        long result = switch (floatingPointFormat(index)) {
            case HALF_FLOAT_FORMAT -> classifyHalf(readHalfBits(state, rs1(operand)));
            case SINGLE_FLOAT_FORMAT -> classifySingle(readSingleBits(state, rs1(operand)));
            default -> classifyDouble(state.decodedFloatingPointRegister(rs1(operand)));
        };
        writeRegister(registers, rd(operand), result);
        finishInstruction(state, index, mode);
    }

    /// Executes a raw floating-point-to-integer register move instruction.
    private void moveFloatingPointToInteger(MachineState state, long[] registers, int index, int operand, byte mode) {
        beginInstruction(state, index, mode);
        long value = switch (floatingPointFormat(index)) {
            case HALF_FLOAT_FORMAT -> (short) state.decodedFloatingPointRegister(rs1(operand));
            case SINGLE_FLOAT_FORMAT -> (int) state.decodedFloatingPointRegister(rs1(operand));
            default -> state.decodedFloatingPointRegister(rs1(operand));
        };
        writeRegister(registers, rd(operand), value);
        finishInstruction(state, index, mode);
    }

    /// Executes a raw integer-to-floating-point register move instruction.
    private void moveIntegerToFloatingPoint(MachineState state, long[] registers, int index, int operand, byte mode) {
        beginInstruction(state, index, mode);
        switch (floatingPointFormat(index)) {
            case HALF_FLOAT_FORMAT -> writeHalfBits(state, rd(operand), (int) registers[rs1(operand)]);
            case SINGLE_FLOAT_FORMAT -> writeSingleBits(state, rd(operand), (int) registers[rs1(operand)]);
            default -> state.setDecodedFloatingPointRegister(rd(operand), registers[rs1(operand)]);
        }
        finishInstruction(state, index, mode);
    }

    /// Executes a floating-point comparison instruction.
    private void floatingPointCompare(
            MachineState state,
            long[] registers,
            int index,
            int operand,
            byte mode,
            CompareKind kind) {
        beginInstruction(state, index, mode);
        if (floatingPointFormat(index) == SINGLE_FLOAT_FORMAT) {
            int leftBits = readSingleBits(state, rs1(operand));
            int rightBits = readSingleBits(state, rs2(operand));
            float left = Float.intBitsToFloat(leftBits);
            float right = Float.intBitsToFloat(rightBits);
            if (Float.isNaN(left) || Float.isNaN(right)) {
                if (kind != CompareKind.EQUAL || isSignalingSingleNaN(leftBits) || isSignalingSingleNaN(rightBits)) {
                    state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
                }
                writeRegister(registers, rd(operand), 0);
            } else {
                writeRegister(registers, rd(operand), compareFloatingPoint(left, right, kind) ? 1 : 0);
            }
        } else {
            long leftBits = state.decodedFloatingPointRegister(rs1(operand));
            long rightBits = state.decodedFloatingPointRegister(rs2(operand));
            double left = Double.longBitsToDouble(leftBits);
            double right = Double.longBitsToDouble(rightBits);
            if (Double.isNaN(left) || Double.isNaN(right)) {
                if (kind != CompareKind.EQUAL || isSignalingDoubleNaN(leftBits) || isSignalingDoubleNaN(rightBits)) {
                    state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
                }
                writeRegister(registers, rd(operand), 0);
            } else {
                writeRegister(registers, rd(operand), compareFloatingPoint(left, right, kind) ? 1 : 0);
            }
        }
        finishInstruction(state, index, mode);
    }

    /// Executes a floating-point minimum or maximum instruction.
    private void floatingPointMinimumMaximum(
            MachineState state,
            int index,
            int operand,
            byte mode,
            boolean minimum) {
        beginInstruction(state, index, mode);
        if (floatingPointFormat(index) == SINGLE_FLOAT_FORMAT) {
            writeSingleBits(state, rd(operand), minimum
                    ? minimumSingleBits(state, readSingleBits(state, rs1(operand)), readSingleBits(state, rs2(operand)))
                    : maximumSingleBits(state, readSingleBits(state, rs1(operand)), readSingleBits(state, rs2(operand))));
        } else {
            long left = state.decodedFloatingPointRegister(rs1(operand));
            long right = state.decodedFloatingPointRegister(rs2(operand));
            state.setDecodedFloatingPointRegister(rd(operand), minimum
                    ? minimumDoubleBits(state, left, right)
                    : maximumDoubleBits(state, left, right));
        }
        finishInstruction(state, index, mode);
    }

    /// Executes a fused floating-point multiply-add instruction.
    private void floatingPointFusedMultiplyAdd(
            MachineState state,
            int index,
            int operand,
            byte mode,
            boolean negateProduct,
            boolean subtractAddend) {
        beginInstruction(state, index, mode);
        RiscVInstructionSemantics.executeMicroFusedMultiplyAdd(
                state,
                rd(operand),
                rs1(operand),
                rs2(operand),
                immediates[index],
                negateProduct,
                subtractAddend);
        finishInstruction(state, index, mode);
    }

    /// Executes a binary floating-point arithmetic instruction.
    private void floatingPointArithmetic(
            MachineState state,
            int index,
            int operand,
            byte mode,
            char operator) {
        beginInstruction(state, index, mode);
        RiscVInstructionSemantics.executeMicroFloatingPointArithmetic(
                state,
                rd(operand),
                rs1(operand),
                rs2(operand),
                immediates[index],
                operator);
        finishInstruction(state, index, mode);
    }

    /// Executes a floating-point square-root instruction.
    private void floatingPointSquareRoot(MachineState state, int index, int operand, byte mode) {
        beginInstruction(state, index, mode);
        RiscVInstructionSemantics.executeMicroFloatingPointSquareRoot(
                state,
                rd(operand),
                rs1(operand),
                immediates[index]);
        finishInstruction(state, index, mode);
    }

    /// Executes `fcvt.s.d`.
    private void convertDoubleToSingle(MachineState state, int index, int operand, byte mode) {
        beginInstruction(state, index, mode);
        RiscVInstructionSemantics.executeMicroConvertDoubleToSingle(
                state,
                rd(operand),
                rs1(operand),
                immediates[index]);
        finishInstruction(state, index, mode);
    }

    /// Executes `fcvt.d.s`.
    private void convertSingleToDouble(MachineState state, int index, int operand, byte mode) {
        beginInstruction(state, index, mode);
        RiscVInstructionSemantics.executeMicroConvertSingleToDouble(
                state,
                rd(operand),
                rs1(operand),
                immediates[index]);
        finishInstruction(state, index, mode);
    }

    /// Executes a floating-point to integer conversion instruction.
    private void convertFloatingPointToInteger(MachineState state, int index, int operand, byte mode) {
        beginInstruction(state, index, mode);
        RiscVInstructionSemantics.executeMicroConvertFloatingPointToInteger(
                state,
                rd(operand),
                rs1(operand),
                rs2(operand),
                immediates[index]);
        finishInstruction(state, index, mode);
    }

    /// Executes an integer to floating-point conversion instruction.
    private void convertIntegerToFloatingPoint(MachineState state, int index, int operand, byte mode) {
        beginInstruction(state, index, mode);
        RiscVInstructionSemantics.executeMicroConvertIntegerToFloatingPoint(
                state,
                rd(operand),
                rs1(operand),
                rs2(operand),
                immediates[index]);
        finishInstruction(state, index, mode);
    }

    /// Loads and reserves a 32-bit memory word.
    private void lrWord(
            MachineState state,
            Memory memory,
            MemoryAccess access,
            long[] registers,
            int index,
            int operand,
            byte mode,
            long pointerMask) {
        beginMemoryInstruction(state, index, mode);
        synchronized (memory) {
            long address = registers[rs1(operand)] & pointerMask;
            requireAtomicAlignment(address, Integer.BYTES);
            int value = access.readInt(address, memoryLayout);
            writeRegister(registers, rd(operand), value);
            state.reserve(address, Integer.BYTES, value);
        }
        finishInstruction(state, index, mode);
    }

    /// Loads and reserves a 64-bit memory doubleword.
    private void lrDouble(
            MachineState state,
            Memory memory,
            MemoryAccess access,
            long[] registers,
            int index,
            int operand,
            byte mode,
            long pointerMask) {
        beginMemoryInstruction(state, index, mode);
        synchronized (memory) {
            long address = registers[rs1(operand)] & pointerMask;
            requireAtomicAlignment(address, Long.BYTES);
            long value = access.readLong(address, memoryLayout);
            writeRegister(registers, rd(operand), value);
            state.reserve(address, Long.BYTES, value);
        }
        finishInstruction(state, index, mode);
    }

    /// Conditionally stores a 32-bit memory word through an LR/SC reservation.
    private void scWord(
            MachineState state,
            Memory memory,
            MemoryAccess access,
            long[] registers,
            int index,
            int operand,
            byte mode,
            long pointerMask) {
        beginMemoryInstruction(state, index, mode);
        synchronized (memory) {
            long address = registers[rs1(operand)] & pointerMask;
            requireAtomicAlignment(address, Integer.BYTES);
            int currentValue = access.readInt(address, memoryLayout);
            if (state.hasReservation(address, Integer.BYTES, currentValue)) {
                access.writeInt(address, (int) registers[rs2(operand)], memoryLayout);
                afterStore(state, address, Integer.BYTES, mode);
                writeRegister(registers, rd(operand), 0);
            } else {
                writeRegister(registers, rd(operand), 1);
            }
            state.clearReservation();
        }
        finishInstruction(state, index, mode);
    }

    /// Conditionally stores a 64-bit memory doubleword through an LR/SC reservation.
    private void scDouble(
            MachineState state,
            Memory memory,
            MemoryAccess access,
            long[] registers,
            int index,
            int operand,
            byte mode,
            long pointerMask) {
        beginMemoryInstruction(state, index, mode);
        synchronized (memory) {
            long address = registers[rs1(operand)] & pointerMask;
            requireAtomicAlignment(address, Long.BYTES);
            long currentValue = access.readLong(address, memoryLayout);
            if (state.hasReservation(address, Long.BYTES, currentValue)) {
                access.writeLong(address, registers[rs2(operand)], memoryLayout);
                afterStore(state, address, Long.BYTES, mode);
                writeRegister(registers, rd(operand), 0);
            } else {
                writeRegister(registers, rd(operand), 1);
            }
            state.clearReservation();
        }
        finishInstruction(state, index, mode);
    }

    /// Executes a 32-bit AMO instruction.
    private void amoWord(
            MachineState state,
            Memory memory,
            MemoryAccess access,
            long[] registers,
            int index,
            int operand,
            byte mode,
            long pointerMask,
            AmoKind kind) {
        beginMemoryInstruction(state, index, mode);
        synchronized (memory) {
            long address = registers[rs1(operand)] & pointerMask;
            requireAtomicAlignment(address, Integer.BYTES);
            int oldValue = access.readInt(address, memoryLayout);
            int source = (int) registers[rs2(operand)];
            int newValue = switch (kind) {
                case SWAP -> source;
                case ADD -> oldValue + source;
                case XOR -> oldValue ^ source;
                case AND -> oldValue & source;
                case OR -> oldValue | source;
                case MIN -> (int) DataIndependent.signedMinimum(oldValue, source);
                case MAX -> (int) DataIndependent.signedMaximum(oldValue, source);
                case MINU -> (int) DataIndependent.unsignedMinimum(
                        Integer.toUnsignedLong(oldValue),
                        Integer.toUnsignedLong(source));
                case MAXU -> (int) DataIndependent.unsignedMaximum(
                        Integer.toUnsignedLong(oldValue),
                        Integer.toUnsignedLong(source));
            };
            access.writeInt(address, newValue, memoryLayout);
            writeRegister(registers, rd(operand), oldValue);
            afterStore(state, address, Integer.BYTES, mode);
            state.clearReservation();
        }
        finishInstruction(state, index, mode);
    }

    /// Executes a 64-bit AMO instruction.
    private void amoDouble(
            MachineState state,
            Memory memory,
            MemoryAccess access,
            long[] registers,
            int index,
            int operand,
            byte mode,
            long pointerMask,
            AmoKind kind) {
        beginMemoryInstruction(state, index, mode);
        synchronized (memory) {
            long address = registers[rs1(operand)] & pointerMask;
            requireAtomicAlignment(address, Long.BYTES);
            long oldValue = access.readLong(address, memoryLayout);
            long source = registers[rs2(operand)];
            long newValue = switch (kind) {
                case SWAP -> source;
                case ADD -> oldValue + source;
                case XOR -> oldValue ^ source;
                case AND -> oldValue & source;
                case OR -> oldValue | source;
                case MIN -> DataIndependent.signedMinimum(oldValue, source);
                case MAX -> DataIndependent.signedMaximum(oldValue, source);
                case MINU -> DataIndependent.unsignedMinimum(oldValue, source);
                case MAXU -> DataIndependent.unsignedMaximum(oldValue, source);
            };
            access.writeLong(address, newValue, memoryLayout);
            writeRegister(registers, rd(operand), oldValue);
            afterStore(state, address, Long.BYTES, mode);
            state.clearReservation();
        }
        finishInstruction(state, index, mode);
    }

    /// Verifies natural alignment for an atomic memory access.
    private static void requireAtomicAlignment(long address, int length) {
        if ((address & (length - 1L)) != 0) {
            throw new RiscVException("Misaligned atomic memory access: address=0x"
                    + Long.toUnsignedString(address, 16)
                    + ", size=" + length);
        }
    }

    /// Handles optional simulator side effects after a store.
    private static void afterStore(MachineState state, long address, int length, byte mode) {
        if (mode != BATCHED_FAST_MODE && state.hasStoreSideEffects()) {
            state.afterStoreWithSideEffects(address, length);
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

    /// Returns the packed floating-point format for a decoded instruction.
    private int floatingPointFormat(int index) {
        return (int) ((immediates[index] >>> FLOATING_POINT_FORMAT_SHIFT) & 0x3);
    }

    /// Reads a half-precision register as raw bits, applying NaN-boxing rules.
    private static int readHalfBits(MachineState state, int register) {
        long value = state.decodedFloatingPointRegister(register);
        return (value & HALF_NAN_BOX_MASK) == HALF_NAN_BOX_MASK ? (int) value & 0xffff : CANONICAL_HALF_NAN;
    }

    /// Reads a single-precision register as raw bits, applying NaN-boxing rules.
    private static int readSingleBits(MachineState state, int register) {
        long value = state.decodedFloatingPointRegister(register);
        return (value & SINGLE_NAN_BOX_MASK) == SINGLE_NAN_BOX_MASK ? (int) value : CANONICAL_SINGLE_NAN;
    }

    /// Writes raw half-precision bits to a NaN-boxed floating-point register.
    private static void writeHalfBits(MachineState state, int register, int bits) {
        state.setDecodedFloatingPointRegister(register, HALF_NAN_BOX_MASK | (bits & 0xffffL));
    }

    /// Writes raw single-precision bits to a NaN-boxed floating-point register.
    private static void writeSingleBits(MachineState state, int register, int bits) {
        state.setDecodedFloatingPointRegister(register, SINGLE_NAN_BOX_MASK | (bits & 0xffff_ffffL));
    }

    /// Classifies raw half-precision bits.
    private static int classifyHalf(int bits) {
        int exponent = bits & 0x7c00;
        int fraction = bits & 0x03ff;
        boolean negative = (bits & 0x8000) != 0;
        if (exponent == 0x7c00) {
            if (fraction == 0) {
                return negative ? 1 : 1 << 7;
            }
            return (fraction & 0x0200) == 0 ? 1 << 8 : 1 << 9;
        }
        if (exponent == 0) {
            if (fraction == 0) {
                return negative ? 1 << 3 : 1 << 4;
            }
            return negative ? 1 << 2 : 1 << 5;
        }
        return negative ? 1 << 1 : 1 << 6;
    }

    /// Classifies raw single-precision bits.
    private static int classifySingle(int bits) {
        int exponent = bits & 0x7f80_0000;
        int fraction = bits & 0x007f_ffff;
        boolean negative = bits < 0;
        if (exponent == 0x7f80_0000) {
            if (fraction == 0) {
                return negative ? 1 : 1 << 7;
            }
            return (fraction & 0x0040_0000) == 0 ? 1 << 8 : 1 << 9;
        }
        if (exponent == 0) {
            if (fraction == 0) {
                return negative ? 1 << 3 : 1 << 4;
            }
            return negative ? 1 << 2 : 1 << 5;
        }
        return negative ? 1 << 1 : 1 << 6;
    }

    /// Classifies raw double-precision bits.
    private static int classifyDouble(long bits) {
        long exponent = bits & 0x7ff0_0000_0000_0000L;
        long fraction = bits & 0x000f_ffff_ffff_ffffL;
        boolean negative = bits < 0;
        if (exponent == 0x7ff0_0000_0000_0000L) {
            if (fraction == 0) {
                return negative ? 1 : 1 << 7;
            }
            return (fraction & 0x0008_0000_0000_0000L) == 0 ? 1 << 8 : 1 << 9;
        }
        if (exponent == 0) {
            if (fraction == 0) {
                return negative ? 1 << 3 : 1 << 4;
            }
            return negative ? 1 << 2 : 1 << 5;
        }
        return negative ? 1 << 1 : 1 << 6;
    }

    /// Returns true for a signaling single-precision NaN bit pattern.
    private static boolean isSignalingSingleNaN(int bits) {
        return (bits & 0x7f80_0000) == 0x7f80_0000
                && (bits & 0x007f_ffff) != 0
                && (bits & 0x0040_0000) == 0;
    }

    /// Returns true for a signaling double-precision NaN bit pattern.
    private static boolean isSignalingDoubleNaN(long bits) {
        return (bits & 0x7ff0_0000_0000_0000L) == 0x7ff0_0000_0000_0000L
                && (bits & 0x000f_ffff_ffff_ffffL) != 0
                && (bits & 0x0008_0000_0000_0000L) == 0;
    }

    /// Compares two finite-or-infinite floating-point values.
    private static boolean compareFloatingPoint(double left, double right, CompareKind kind) {
        return switch (kind) {
            case EQUAL -> left == right;
            case LESS_THAN -> left < right;
            case LESS_THAN_OR_EQUAL -> left <= right;
        };
    }

    /// Computes RISC-V single-precision minimum bits.
    private static int minimumSingleBits(MachineState state, int leftBits, int rightBits) {
        if (isSignalingSingleNaN(leftBits) || isSignalingSingleNaN(rightBits)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
        float left = Float.intBitsToFloat(leftBits);
        float right = Float.intBitsToFloat(rightBits);
        if (Float.isNaN(left) && Float.isNaN(right)) {
            return CANONICAL_SINGLE_NAN;
        }
        if (Float.isNaN(left)) {
            return rightBits;
        }
        if (Float.isNaN(right)) {
            return leftBits;
        }
        if (left == 0.0f && right == 0.0f) {
            return (leftBits < 0 || rightBits < 0) ? 0x8000_0000 : 0;
        }
        return left <= right ? leftBits : rightBits;
    }

    /// Computes RISC-V single-precision maximum bits.
    private static int maximumSingleBits(MachineState state, int leftBits, int rightBits) {
        if (isSignalingSingleNaN(leftBits) || isSignalingSingleNaN(rightBits)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
        float left = Float.intBitsToFloat(leftBits);
        float right = Float.intBitsToFloat(rightBits);
        if (Float.isNaN(left) && Float.isNaN(right)) {
            return CANONICAL_SINGLE_NAN;
        }
        if (Float.isNaN(left)) {
            return rightBits;
        }
        if (Float.isNaN(right)) {
            return leftBits;
        }
        if (left == 0.0f && right == 0.0f) {
            return (leftBits >= 0 || rightBits >= 0) ? 0 : 0x8000_0000;
        }
        return left >= right ? leftBits : rightBits;
    }

    /// Computes RISC-V double-precision minimum bits.
    private static long minimumDoubleBits(MachineState state, long leftBits, long rightBits) {
        if (isSignalingDoubleNaN(leftBits) || isSignalingDoubleNaN(rightBits)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
        double left = Double.longBitsToDouble(leftBits);
        double right = Double.longBitsToDouble(rightBits);
        if (Double.isNaN(left) && Double.isNaN(right)) {
            return CANONICAL_DOUBLE_NAN;
        }
        if (Double.isNaN(left)) {
            return rightBits;
        }
        if (Double.isNaN(right)) {
            return leftBits;
        }
        if (left == 0.0d && right == 0.0d) {
            return (leftBits < 0 || rightBits < 0) ? Long.MIN_VALUE : 0;
        }
        return left <= right ? leftBits : rightBits;
    }

    /// Computes RISC-V double-precision maximum bits.
    private static long maximumDoubleBits(MachineState state, long leftBits, long rightBits) {
        if (isSignalingDoubleNaN(leftBits) || isSignalingDoubleNaN(rightBits)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
        double left = Double.longBitsToDouble(leftBits);
        double right = Double.longBitsToDouble(rightBits);
        if (Double.isNaN(left) && Double.isNaN(right)) {
            return CANONICAL_DOUBLE_NAN;
        }
        if (Double.isNaN(left)) {
            return rightBits;
        }
        if (Double.isNaN(right)) {
            return leftBits;
        }
        if (left == 0.0d && right == 0.0d) {
            return (leftBits >= 0 || rightBits >= 0) ? 0 : Long.MIN_VALUE;
        }
        return left >= right ? leftBits : rightBits;
    }

    /// The AMO operation selected by one atomic memory instruction.
    private enum AmoKind {
        /// Stores the source value.
        SWAP,
        /// Adds the source value.
        ADD,
        /// Xors the source value.
        XOR,
        /// Ands the source value.
        AND,
        /// Ors the source value.
        OR,
        /// Stores the signed minimum.
        MIN,
        /// Stores the signed maximum.
        MAX,
        /// Stores the unsigned minimum.
        MINU,
        /// Stores the unsigned maximum.
        MAXU
    }

    /// The sign source used by a floating-point sign-injection instruction.
    private enum SignInjectionKind {
        /// Copies the second operand sign bit.
        COPY,
        /// Copies the inverted second operand sign bit.
        NEGATE,
        /// Xors both operand sign bits.
        XOR
    }

    /// The relation tested by a floating-point comparison instruction.
    private enum CompareKind {
        /// Tests equality.
        EQUAL,
        /// Tests less-than.
        LESS_THAN,
        /// Tests less-than-or-equal.
        LESS_THAN_OR_EQUAL
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

/// Wraps one micro-bytecode block node in a Truffle root call target.
@NotNullByDefault
final class RiscVMicroBlockRootNode extends RootNode {
    /// The executable micro-bytecode block body.
    @Child private RiscVMicroBlockNode block;

    /// Creates a root node for one decoded RISC-V basic block.
    RiscVMicroBlockRootNode(RiscVLanguage language, RiscVMicroBlockNode block) {
        super(language);
        this.block = block;
    }

    /// Executes the block with the `MachineState` and `MemoryAccess` supplied as arguments.
    @Override
    public Object execute(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        block.execute((MachineState) arguments[0], (MemoryAccess) arguments[1]);
        return null;
    }
}
