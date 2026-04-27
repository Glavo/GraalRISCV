package org.glavo.riscv;

import com.oracle.truffle.api.nodes.Node;
import org.jetbrains.annotations.NotNullByDefault;

import java.math.BigInteger;

/// Executes one decoded RV64GC guest instruction subset.
@NotNullByDefault
public final class InstructionNode extends Node {
    /// The immediate bit offset used for the packed floating-point format field.
    private static final int FLOATING_POINT_FORMAT_SHIFT = 3;

    /// The immediate bit offset used for the packed third floating-point source register.
    private static final int THIRD_FLOATING_POINT_SOURCE_SHIFT = 5;

    /// The packed format value for single-precision floating-point operations.
    private static final int SINGLE_FLOAT_FORMAT = 0;

    /// The packed format value for double-precision floating-point operations.
    private static final int DOUBLE_FLOAT_FORMAT = 1;

    /// The mask for a NaN-boxed single-precision value in an RV64 floating-point register.
    private static final long SINGLE_NAN_BOX_MASK = 0xffff_ffff_0000_0000L;

    /// The canonical single-precision quiet NaN bit pattern.
    private static final int CANONICAL_SINGLE_NAN = 0x7fc0_0000;

    /// The canonical double-precision quiet NaN bit pattern.
    private static final long CANONICAL_DOUBLE_NAN = 0x7ff8_0000_0000_0000L;

    /// The floating-point inexact exception flag.
    private static final int FLOATING_POINT_INEXACT = 0x01;

    /// The floating-point invalid-operation exception flag.
    private static final int FLOATING_POINT_INVALID_OPERATION = 0x10;

    /// The floating-point divide-by-zero exception flag.
    private static final int FLOATING_POINT_DIVIDE_BY_ZERO = 0x08;

    /// Round to nearest, ties to even.
    private static final int ROUND_NEAREST_EVEN = 0;

    /// Round toward zero.
    private static final int ROUND_TOWARD_ZERO = 1;

    /// Round down.
    private static final int ROUND_DOWN = 2;

    /// Round up.
    private static final int ROUND_UP = 3;

    /// Round to nearest, ties to maximum magnitude.
    private static final int ROUND_NEAREST_MAX_MAGNITUDE = 4;

    /// Use the dynamic rounding mode from `frm`.
    private static final int ROUND_DYNAMIC = 7;

    /// The guest address of this instruction.
    private final long address;

    /// The original 16-bit or 32-bit instruction bits.
    private final int raw;

    /// The instruction length in bytes.
    private final int length;

    /// The decoded operation.
    private final Operation operation;

    /// The destination register index, or zero when unused.
    private final int rd;

    /// The first source register index, or zero when unused.
    private final int rs1;

    /// The second source register index, or zero when unused.
    private final int rs2;

    /// The decoded immediate or operation-specific small integer.
    private final long immediate;

    /// Whether this instruction ends the current basic block.
    private final boolean terminator;

    /// Creates a decoded instruction node.
    public InstructionNode(
            long address,
            int raw,
            int length,
            Operation operation,
            int rd,
            int rs1,
            int rs2,
            long immediate,
            boolean terminator) {
        this.address = address;
        this.raw = raw;
        this.length = length;
        this.operation = operation;
        this.rd = rd;
        this.rs1 = rs1;
        this.rs2 = rs2;
        this.immediate = immediate;
        this.terminator = terminator;
    }

    /// Returns the instruction length in bytes.
    public int length() {
        return length;
    }

    /// Returns true when this instruction ends the current basic block.
    public boolean isTerminator() {
        return terminator;
    }

    /// Executes this instruction against the supplied architectural state.
    public void execute(MachineState state) {
        state.beforeInstruction(address, raw);
        long nextPc = address + length;
        Memory memory = state.memory();

        switch (operation) {
            case NOP, LUI, AUIPC, JAL, JALR, BEQ, BNE, BLT, BGE, BLTU, BGEU, FENCE, FENCE_I,
                    ECALL, EBREAK, CSRRW, CSRRS, CSRRC, CSRRWI, CSRRSI, CSRRCI ->
                    executeControl(state, nextPc);
            case LB, LH, LW, LD, LBU, LHU, LWU -> executeLoad(state, memory, nextPc);
            case FLW, FLD -> executeFloatingPointLoad(state, memory, nextPc);
            case SB, SH, SW, SD -> executeStore(state, memory, nextPc);
            case FSW, FSD -> executeFloatingPointStore(state, memory, nextPc);
            case ADDI, SLTI, SLTIU, XORI, ORI, ANDI, SLLI, SRLI, SRAI, ADDIW, SLLIW, SRLIW, SRAIW ->
                    executeImmediateInteger(state, nextPc);
            case ADD, SUB, SLL, SLT, SLTU, XOR, SRL, SRA, OR, AND, ADDW, SUBW, SLLW, SRLW, SRAW ->
                    executeRegisterInteger(state, nextPc);
            case MUL, MULH, MULHSU, MULHU, DIV, DIVU, REM, REMU, MULW, DIVW, DIVUW, REMW, REMUW ->
                    executeMultiplyDivide(state, nextPc);
            case FMADD, FMSUB, FNMSUB, FNMADD, FADD, FSUB, FMUL, FDIV, FSQRT, FSGNJ, FSGNJN, FSGNJX,
                    FMIN, FMAX, FCVT_S_D, FCVT_D_S, FEQ, FLT, FLE, FCLASS, FCVT_INT_FP, FCVT_FP_INT,
                    FMV_X_FP, FMV_FP_X -> executeFloatingPointOperation(state, nextPc);
            case LR_W, LR_D, SC_W, SC_D, AMOSWAP_W, AMOADD_W, AMOXOR_W, AMOAND_W, AMOOR_W, AMOMIN_W,
                    AMOMAX_W, AMOMINU_W, AMOMAXU_W, AMOSWAP_D, AMOADD_D, AMOXOR_D, AMOAND_D, AMOOR_D,
                    AMOMIN_D, AMOMAX_D, AMOMINU_D, AMOMAXU_D -> executeAtomic(state, memory, nextPc);
        }
    }

    /// Executes control-flow and system operations.
    private void executeControl(MachineState state, long nextPc) {
        switch (operation) {
            case NOP, FENCE, FENCE_I -> state.setPc(nextPc);
            case LUI -> {
                state.setRegister(rd, immediate);
                state.setPc(nextPc);
            }
            case AUIPC -> {
                state.setRegister(rd, address + immediate);
                state.setPc(nextPc);
            }
            case JAL -> {
                state.setRegister(rd, nextPc);
                state.setPc(address + immediate);
            }
            case JALR -> {
                long target = (state.register(rs1) + immediate) & ~1L;
                state.setRegister(rd, nextPc);
                state.setPc(target);
            }
            case BEQ -> branch(state, state.register(rs1) == state.register(rs2), nextPc);
            case BNE -> branch(state, state.register(rs1) != state.register(rs2), nextPc);
            case BLT -> branch(state, state.register(rs1) < state.register(rs2), nextPc);
            case BGE -> branch(state, state.register(rs1) >= state.register(rs2), nextPc);
            case BLTU -> branch(state, Long.compareUnsigned(state.register(rs1), state.register(rs2)) < 0, nextPc);
            case BGEU -> branch(state, Long.compareUnsigned(state.register(rs1), state.register(rs2)) >= 0, nextPc);
            case CSRRW -> writeControlStatusRegister(state, nextPc, state.register(rs1));
            case CSRRS -> setClearControlStatusRegister(state, nextPc, state.register(rs1), true);
            case CSRRC -> setClearControlStatusRegister(state, nextPc, state.register(rs1), false);
            case CSRRWI -> writeControlStatusRegister(state, nextPc, rs1);
            case CSRRSI -> setClearControlStatusRegister(state, nextPc, rs1, true);
            case CSRRCI -> setClearControlStatusRegister(state, nextPc, rs1, false);
            case ECALL -> ecall(state);
            case EBREAK -> throw new ProgramExitException(0);
            default -> throw unexpectedOperationGroup("control");
        }
    }

    /// Executes load operations.
    private void executeLoad(MachineState state, Memory memory, long nextPc) {
        switch (operation) {
            case LB -> loadByte(state, memory, nextPc);
            case LH -> loadShort(state, memory, nextPc);
            case LW -> loadInt(state, memory, nextPc);
            case LD -> loadLong(state, memory, nextPc);
            case LBU -> loadUnsignedByte(state, memory, nextPc);
            case LHU -> loadUnsignedShort(state, memory, nextPc);
            case LWU -> loadUnsignedInt(state, memory, nextPc);
            default -> throw unexpectedOperationGroup("load");
        }
    }

    /// Executes floating-point load operations.
    private void executeFloatingPointLoad(MachineState state, Memory memory, long nextPc) {
        switch (operation) {
            case FLW -> loadFloatWord(state, memory, nextPc);
            case FLD -> loadFloatDouble(state, memory, nextPc);
            default -> throw unexpectedOperationGroup("floating-point load");
        }
    }

    /// Executes store operations.
    private void executeStore(MachineState state, Memory memory, long nextPc) {
        switch (operation) {
            case SB -> storeByte(state, memory, nextPc);
            case SH -> storeShort(state, memory, nextPc);
            case SW -> storeInt(state, memory, nextPc);
            case SD -> storeLong(state, memory, nextPc);
            default -> throw unexpectedOperationGroup("store");
        }
    }

    /// Executes floating-point store operations.
    private void executeFloatingPointStore(MachineState state, Memory memory, long nextPc) {
        switch (operation) {
            case FSW -> storeFloatWord(state, memory, nextPc);
            case FSD -> storeFloatDouble(state, memory, nextPc);
            default -> throw unexpectedOperationGroup("floating-point store");
        }
    }

    /// Executes integer register-immediate operations.
    private void executeImmediateInteger(MachineState state, long nextPc) {
        switch (operation) {
            case ADDI -> binaryImmediate(state, nextPc, state.register(rs1) + immediate);
            case SLTI -> binaryImmediate(state, nextPc, state.register(rs1) < immediate ? 1 : 0);
            case SLTIU -> binaryImmediate(state, nextPc, Long.compareUnsigned(state.register(rs1), immediate) < 0 ? 1 : 0);
            case XORI -> binaryImmediate(state, nextPc, state.register(rs1) ^ immediate);
            case ORI -> binaryImmediate(state, nextPc, state.register(rs1) | immediate);
            case ANDI -> binaryImmediate(state, nextPc, state.register(rs1) & immediate);
            case SLLI -> binaryImmediate(state, nextPc, state.register(rs1) << immediate);
            case SRLI -> binaryImmediate(state, nextPc, state.register(rs1) >>> immediate);
            case SRAI -> binaryImmediate(state, nextPc, state.register(rs1) >> immediate);
            case ADDIW -> wordImmediate(state, nextPc, (int) (state.register(rs1) + immediate));
            case SLLIW -> wordImmediate(state, nextPc, (int) state.register(rs1) << immediate);
            case SRLIW -> wordImmediate(state, nextPc, (int) state.register(rs1) >>> immediate);
            case SRAIW -> wordImmediate(state, nextPc, (int) state.register(rs1) >> immediate);
            default -> throw unexpectedOperationGroup("immediate integer");
        }
    }

    /// Executes integer register-register operations.
    private void executeRegisterInteger(MachineState state, long nextPc) {
        switch (operation) {
            case ADD -> binaryRegister(state, nextPc, state.register(rs1) + state.register(rs2));
            case SUB -> binaryRegister(state, nextPc, state.register(rs1) - state.register(rs2));
            case SLL -> binaryRegister(state, nextPc, state.register(rs1) << (state.register(rs2) & 0x3f));
            case SLT -> binaryRegister(state, nextPc, state.register(rs1) < state.register(rs2) ? 1 : 0);
            case SLTU -> binaryRegister(state, nextPc, Long.compareUnsigned(state.register(rs1), state.register(rs2)) < 0 ? 1 : 0);
            case XOR -> binaryRegister(state, nextPc, state.register(rs1) ^ state.register(rs2));
            case SRL -> binaryRegister(state, nextPc, state.register(rs1) >>> (state.register(rs2) & 0x3f));
            case SRA -> binaryRegister(state, nextPc, state.register(rs1) >> (state.register(rs2) & 0x3f));
            case OR -> binaryRegister(state, nextPc, state.register(rs1) | state.register(rs2));
            case AND -> binaryRegister(state, nextPc, state.register(rs1) & state.register(rs2));
            case ADDW -> wordRegister(state, nextPc, (int) state.register(rs1) + (int) state.register(rs2));
            case SUBW -> wordRegister(state, nextPc, (int) state.register(rs1) - (int) state.register(rs2));
            case SLLW -> wordRegister(state, nextPc, (int) state.register(rs1) << (state.register(rs2) & 0x1f));
            case SRLW -> wordRegister(state, nextPc, (int) state.register(rs1) >>> (state.register(rs2) & 0x1f));
            case SRAW -> wordRegister(state, nextPc, (int) state.register(rs1) >> (state.register(rs2) & 0x1f));
            default -> throw unexpectedOperationGroup("register integer");
        }
    }

    /// Executes RV64M multiply and divide operations.
    private void executeMultiplyDivide(MachineState state, long nextPc) {
        switch (operation) {
            case MUL -> binaryRegister(state, nextPc, state.register(rs1) * state.register(rs2));
            case MULH -> binaryRegister(state, nextPc, Math.multiplyHigh(state.register(rs1), state.register(rs2)));
            case MULHSU -> binaryRegister(state, nextPc, multiplyHighSignedUnsigned(state.register(rs1), state.register(rs2)));
            case MULHU -> binaryRegister(state, nextPc, Math.unsignedMultiplyHigh(state.register(rs1), state.register(rs2)));
            case DIV -> binaryRegister(state, nextPc, divideSigned(state.register(rs1), state.register(rs2)));
            case DIVU -> binaryRegister(state, nextPc, divideUnsigned(state.register(rs1), state.register(rs2)));
            case REM -> binaryRegister(state, nextPc, remainderSigned(state.register(rs1), state.register(rs2)));
            case REMU -> binaryRegister(state, nextPc, remainderUnsigned(state.register(rs1), state.register(rs2)));
            case MULW -> wordRegister(state, nextPc, (int) state.register(rs1) * (int) state.register(rs2));
            case DIVW -> wordRegister(state, nextPc, divideSignedWord((int) state.register(rs1), (int) state.register(rs2)));
            case DIVUW -> wordRegister(state, nextPc, divideUnsignedWord((int) state.register(rs1), (int) state.register(rs2)));
            case REMW -> wordRegister(state, nextPc, remainderSignedWord((int) state.register(rs1), (int) state.register(rs2)));
            case REMUW -> wordRegister(state, nextPc, remainderUnsignedWord((int) state.register(rs1), (int) state.register(rs2)));
            default -> throw unexpectedOperationGroup("multiply/divide");
        }
    }

    /// Executes floating-point arithmetic, conversion, move, compare, and classify operations.
    private void executeFloatingPointOperation(MachineState state, long nextPc) {
        switch (operation) {
            case FMADD -> fusedMultiplyAdd(state, nextPc, false, false);
            case FMSUB -> fusedMultiplyAdd(state, nextPc, false, true);
            case FNMSUB -> fusedMultiplyAdd(state, nextPc, true, false);
            case FNMADD -> fusedMultiplyAdd(state, nextPc, true, true);
            case FADD -> floatingPointArithmetic(state, nextPc, '+');
            case FSUB -> floatingPointArithmetic(state, nextPc, '-');
            case FMUL -> floatingPointArithmetic(state, nextPc, '*');
            case FDIV -> floatingPointArithmetic(state, nextPc, '/');
            case FSQRT -> floatingPointSquareRoot(state, nextPc);
            case FSGNJ -> floatingPointSignInjection(state, nextPc, SignInjectionKind.COPY);
            case FSGNJN -> floatingPointSignInjection(state, nextPc, SignInjectionKind.NEGATE);
            case FSGNJX -> floatingPointSignInjection(state, nextPc, SignInjectionKind.XOR);
            case FMIN -> floatingPointMinimumMaximum(state, nextPc, true);
            case FMAX -> floatingPointMinimumMaximum(state, nextPc, false);
            case FCVT_S_D -> {
                checkEffectiveRoundingMode(state);
                long bits = state.floatingPointRegister(rs1);
                if (isSignalingDoubleNaN(bits)) {
                    state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
                }
                writeSingleBits(state, rd, canonicalizeSingleBits((float) Double.longBitsToDouble(bits)));
                state.setPc(nextPc);
            }
            case FCVT_D_S -> {
                checkEffectiveRoundingMode(state);
                int bits = readSingleBits(state, rs1);
                if (isSignalingSingleNaN(bits)) {
                    state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
                }
                writeDoubleBits(state, rd, canonicalizeDoubleBits(Float.intBitsToFloat(bits)));
                state.setPc(nextPc);
            }
            case FEQ -> floatingPointCompare(state, nextPc, CompareKind.EQUAL);
            case FLT -> floatingPointCompare(state, nextPc, CompareKind.LESS_THAN);
            case FLE -> floatingPointCompare(state, nextPc, CompareKind.LESS_THAN_OR_EQUAL);
            case FCLASS -> {
                state.setRegister(rd, floatingPointFormat() == SINGLE_FLOAT_FORMAT
                        ? classifySingle(readSingleBits(state, rs1))
                        : classifyDouble(state.floatingPointRegister(rs1)));
                state.setPc(nextPc);
            }
            case FCVT_INT_FP -> convertFloatingPointToInteger(state, nextPc);
            case FCVT_FP_INT -> convertIntegerToFloatingPoint(state, nextPc);
            case FMV_X_FP -> {
                state.setRegister(rd, floatingPointFormat() == SINGLE_FLOAT_FORMAT
                        ? (int) readSingleBits(state, rs1)
                        : state.floatingPointRegister(rs1));
                state.setPc(nextPc);
            }
            case FMV_FP_X -> {
                if (floatingPointFormat() == SINGLE_FLOAT_FORMAT) {
                    writeSingleBits(state, rd, (int) state.register(rs1));
                } else {
                    writeDoubleBits(state, rd, state.register(rs1));
                }
                state.setPc(nextPc);
            }
            default -> throw unexpectedOperationGroup("floating point");
        }
    }

    /// Executes RV64A atomic operations.
    private void executeAtomic(MachineState state, Memory memory, long nextPc) {
        switch (operation) {
            case LR_W -> lrWord(state, memory, nextPc);
            case LR_D -> lrDouble(state, memory, nextPc);
            case SC_W -> scWord(state, memory, nextPc);
            case SC_D -> scDouble(state, memory, nextPc);
            case AMOSWAP_W -> amoWord(state, memory, nextPc, AmoKind.SWAP);
            case AMOADD_W -> amoWord(state, memory, nextPc, AmoKind.ADD);
            case AMOXOR_W -> amoWord(state, memory, nextPc, AmoKind.XOR);
            case AMOAND_W -> amoWord(state, memory, nextPc, AmoKind.AND);
            case AMOOR_W -> amoWord(state, memory, nextPc, AmoKind.OR);
            case AMOMIN_W -> amoWord(state, memory, nextPc, AmoKind.MIN);
            case AMOMAX_W -> amoWord(state, memory, nextPc, AmoKind.MAX);
            case AMOMINU_W -> amoWord(state, memory, nextPc, AmoKind.MINU);
            case AMOMAXU_W -> amoWord(state, memory, nextPc, AmoKind.MAXU);
            case AMOSWAP_D -> amoDouble(state, memory, nextPc, AmoKind.SWAP);
            case AMOADD_D -> amoDouble(state, memory, nextPc, AmoKind.ADD);
            case AMOXOR_D -> amoDouble(state, memory, nextPc, AmoKind.XOR);
            case AMOAND_D -> amoDouble(state, memory, nextPc, AmoKind.AND);
            case AMOOR_D -> amoDouble(state, memory, nextPc, AmoKind.OR);
            case AMOMIN_D -> amoDouble(state, memory, nextPc, AmoKind.MIN);
            case AMOMAX_D -> amoDouble(state, memory, nextPc, AmoKind.MAX);
            case AMOMINU_D -> amoDouble(state, memory, nextPc, AmoKind.MINU);
            case AMOMAXU_D -> amoDouble(state, memory, nextPc, AmoKind.MAXU);
            default -> throw unexpectedOperationGroup("atomic");
        }
    }

    /// Creates an assertion error for an impossible operation group dispatch.
    private AssertionError unexpectedOperationGroup(String group) {
        return new AssertionError("Unexpected " + group + " operation: " + operation);
    }

    /// Sets the next program counter for a conditional branch.
    private void branch(MachineState state, boolean taken, long nextPc) {
        state.setPc(taken ? address + immediate : nextPc);
    }

    /// Writes a CSR and returns its old value when the destination register is not `x0`.
    private void writeControlStatusRegister(MachineState state, long nextPc, long value) {
        long oldValue = rd == 0 ? 0 : state.readControlStatusRegister((int) immediate);
        state.writeControlStatusRegister((int) immediate, value);
        state.setRegister(rd, oldValue);
        state.setPc(nextPc);
    }

    /// Sets or clears CSR bits using the supplied mask value.
    private void setClearControlStatusRegister(MachineState state, long nextPc, long mask, boolean setBits) {
        long oldValue = state.readControlStatusRegister((int) immediate);
        if (mask != 0) {
            state.writeControlStatusRegister((int) immediate, setBits ? oldValue | mask : oldValue & ~mask);
        }
        state.setRegister(rd, oldValue);
        state.setPc(nextPc);
    }

    /// Writes an immediate arithmetic result and advances the program counter.
    private void binaryImmediate(MachineState state, long nextPc, long value) {
        state.setRegister(rd, value);
        state.setPc(nextPc);
    }

    /// Writes a sign-extended 32-bit immediate arithmetic result and advances the program counter.
    private void wordImmediate(MachineState state, long nextPc, int value) {
        state.setRegister(rd, value);
        state.setPc(nextPc);
    }

    /// Writes a register arithmetic result and advances the program counter.
    private void binaryRegister(MachineState state, long nextPc, long value) {
        state.setRegister(rd, value);
        state.setPc(nextPc);
    }

    /// Writes a sign-extended 32-bit register arithmetic result and advances the program counter.
    private void wordRegister(MachineState state, long nextPc, int value) {
        state.setRegister(rd, value);
        state.setPc(nextPc);
    }

    /// Loads a sign-extended byte value.
    private void loadByte(MachineState state, Memory memory, long nextPc) {
        state.setRegister(rd, memory.readByte(state.register(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a sign-extended 16-bit value.
    private void loadShort(MachineState state, Memory memory, long nextPc) {
        state.setRegister(rd, memory.readShort(state.register(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a sign-extended 32-bit value.
    private void loadInt(MachineState state, Memory memory, long nextPc) {
        state.setRegister(rd, memory.readInt(state.register(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a 64-bit value.
    private void loadLong(MachineState state, Memory memory, long nextPc) {
        state.setRegister(rd, memory.readLong(state.register(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a zero-extended byte value.
    private void loadUnsignedByte(MachineState state, Memory memory, long nextPc) {
        state.setRegister(rd, memory.readUnsignedByte(state.register(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a zero-extended 16-bit value.
    private void loadUnsignedShort(MachineState state, Memory memory, long nextPc) {
        state.setRegister(rd, memory.readUnsignedShort(state.register(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a zero-extended 32-bit value.
    private void loadUnsignedInt(MachineState state, Memory memory, long nextPc) {
        state.setRegister(rd, memory.readUnsignedInt(state.register(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a 32-bit floating-point value and NaN-boxes it in a 64-bit FP register.
    private void loadFloatWord(MachineState state, Memory memory, long nextPc) {
        state.setFloatingPointRegister(rd, 0xffff_ffff_0000_0000L | memory.readUnsignedInt(state.register(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a 64-bit floating-point value as raw bits.
    private void loadFloatDouble(MachineState state, Memory memory, long nextPc) {
        state.setFloatingPointRegister(rd, memory.readLong(state.register(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Stores a byte value.
    private void storeByte(MachineState state, Memory memory, long nextPc) {
        long address = state.register(rs1) + immediate;
        memory.writeByte(address, (byte) state.register(rs2));
        state.afterStore(address, Byte.BYTES);
        state.clearReservation();
        state.setPc(nextPc);
    }

    /// Stores a 16-bit value.
    private void storeShort(MachineState state, Memory memory, long nextPc) {
        long address = state.register(rs1) + immediate;
        memory.writeShort(address, (short) state.register(rs2));
        state.afterStore(address, Short.BYTES);
        state.clearReservation();
        state.setPc(nextPc);
    }

    /// Stores a 32-bit value.
    private void storeInt(MachineState state, Memory memory, long nextPc) {
        long address = state.register(rs1) + immediate;
        memory.writeInt(address, (int) state.register(rs2));
        state.afterStore(address, Integer.BYTES);
        state.clearReservation();
        state.setPc(nextPc);
    }

    /// Stores a 64-bit value.
    private void storeLong(MachineState state, Memory memory, long nextPc) {
        long address = state.register(rs1) + immediate;
        memory.writeLong(address, state.register(rs2));
        state.afterStore(address, Long.BYTES);
        state.clearReservation();
        state.setPc(nextPc);
    }

    /// Stores the low 32 bits of a floating-point register.
    private void storeFloatWord(MachineState state, Memory memory, long nextPc) {
        long address = state.register(rs1) + immediate;
        memory.writeInt(address, (int) state.floatingPointRegister(rs2));
        state.afterStore(address, Integer.BYTES);
        state.clearReservation();
        state.setPc(nextPc);
    }

    /// Stores a 64-bit floating-point register as raw bits.
    private void storeFloatDouble(MachineState state, Memory memory, long nextPc) {
        long address = state.register(rs1) + immediate;
        memory.writeLong(address, state.floatingPointRegister(rs2));
        state.afterStore(address, Long.BYTES);
        state.clearReservation();
        state.setPc(nextPc);
    }

    /// Executes an environment call through the configured syscall handler.
    private void ecall(MachineState state) {
        state.syscalls().handle(state, address);
        state.setPc(address + length);
    }

    /// Loads and reserves a 32-bit memory word.
    private void lrWord(MachineState state, Memory memory, long nextPc) {
        long address = state.register(rs1);
        state.setRegister(rd, memory.readInt(address));
        state.reserve(address);
        state.setPc(nextPc);
    }

    /// Loads and reserves a 64-bit memory doubleword.
    private void lrDouble(MachineState state, Memory memory, long nextPc) {
        long address = state.register(rs1);
        state.setRegister(rd, memory.readLong(address));
        state.reserve(address);
        state.setPc(nextPc);
    }

    /// Conditionally stores a 32-bit memory word through an LR/SC reservation.
    private void scWord(MachineState state, Memory memory, long nextPc) {
        long address = state.register(rs1);
        if (state.hasReservation(address)) {
            memory.writeInt(address, (int) state.register(rs2));
            state.afterStore(address, Integer.BYTES);
            state.setRegister(rd, 0);
        } else {
            state.setRegister(rd, 1);
        }
        state.clearReservation();
        state.setPc(nextPc);
    }

    /// Conditionally stores a 64-bit memory doubleword through an LR/SC reservation.
    private void scDouble(MachineState state, Memory memory, long nextPc) {
        long address = state.register(rs1);
        if (state.hasReservation(address)) {
            memory.writeLong(address, state.register(rs2));
            state.afterStore(address, Long.BYTES);
            state.setRegister(rd, 0);
        } else {
            state.setRegister(rd, 1);
        }
        state.clearReservation();
        state.setPc(nextPc);
    }

    /// Executes a 32-bit AMO instruction.
    private void amoWord(MachineState state, Memory memory, long nextPc, AmoKind kind) {
        long address = state.register(rs1);
        int oldValue = memory.readInt(address);
        int source = (int) state.register(rs2);
        int newValue = switch (kind) {
            case SWAP -> source;
            case ADD -> oldValue + source;
            case XOR -> oldValue ^ source;
            case AND -> oldValue & source;
            case OR -> oldValue | source;
            case MIN -> oldValue < source ? oldValue : source;
            case MAX -> oldValue > source ? oldValue : source;
            case MINU -> Integer.compareUnsigned(oldValue, source) < 0 ? oldValue : source;
            case MAXU -> Integer.compareUnsigned(oldValue, source) > 0 ? oldValue : source;
        };
        memory.writeInt(address, newValue);
        state.setRegister(rd, oldValue);
        state.afterStore(address, Integer.BYTES);
        state.clearReservation();
        state.setPc(nextPc);
    }

    /// Executes a 64-bit AMO instruction.
    private void amoDouble(MachineState state, Memory memory, long nextPc, AmoKind kind) {
        long address = state.register(rs1);
        long oldValue = memory.readLong(address);
        long source = state.register(rs2);
        long newValue = switch (kind) {
            case SWAP -> source;
            case ADD -> oldValue + source;
            case XOR -> oldValue ^ source;
            case AND -> oldValue & source;
            case OR -> oldValue | source;
            case MIN -> oldValue < source ? oldValue : source;
            case MAX -> oldValue > source ? oldValue : source;
            case MINU -> Long.compareUnsigned(oldValue, source) < 0 ? oldValue : source;
            case MAXU -> Long.compareUnsigned(oldValue, source) > 0 ? oldValue : source;
        };
        memory.writeLong(address, newValue);
        state.setRegister(rd, oldValue);
        state.afterStore(address, Long.BYTES);
        state.clearReservation();
        state.setPc(nextPc);
    }

    /// Executes an F or D fused multiply-add operation.
    private void fusedMultiplyAdd(MachineState state, long nextPc, boolean negateProduct, boolean subtractAddend) {
        checkEffectiveRoundingMode(state);
        int rs3 = thirdFloatingPointSource();
        if (floatingPointFormat() == SINGLE_FLOAT_FORMAT) {
            int leftBits = readSingleBits(state, rs1);
            int rightBits = readSingleBits(state, rs2);
            int addendBits = readSingleBits(state, rs3);
            updateInvalidFlagForSignalingSingleNaNs(state, leftBits, rightBits, addendBits);
            float left = Float.intBitsToFloat(leftBits);
            float right = Float.intBitsToFloat(rightBits);
            float addend = Float.intBitsToFloat(addendBits);
            float effectiveLeft = negateProduct ? -left : left;
            float effectiveAddend = subtractAddend ? -addend : addend;
            updateInvalidFlagForFusedMultiplyAdd(state, effectiveLeft, right, effectiveAddend);
            float result = Math.fma(effectiveLeft, right, effectiveAddend);
            writeSingleBits(state, rd, canonicalizeSingleBits(result));
        } else {
            long leftBits = state.floatingPointRegister(rs1);
            long rightBits = state.floatingPointRegister(rs2);
            long addendBits = state.floatingPointRegister(rs3);
            updateInvalidFlagForSignalingDoubleNaNs(state, leftBits, rightBits, addendBits);
            double left = Double.longBitsToDouble(leftBits);
            double right = Double.longBitsToDouble(rightBits);
            double addend = Double.longBitsToDouble(addendBits);
            double effectiveLeft = negateProduct ? -left : left;
            double effectiveAddend = subtractAddend ? -addend : addend;
            updateInvalidFlagForFusedMultiplyAdd(state, effectiveLeft, right, effectiveAddend);
            double result = Math.fma(effectiveLeft, right, effectiveAddend);
            writeDoubleBits(state, rd, canonicalizeDoubleBits(result));
        }
        state.setPc(nextPc);
    }

    /// Executes a basic binary floating-point arithmetic operation.
    private void floatingPointArithmetic(MachineState state, long nextPc, char operator) {
        checkEffectiveRoundingMode(state);
        if (floatingPointFormat() == SINGLE_FLOAT_FORMAT) {
            int leftBits = readSingleBits(state, rs1);
            int rightBits = readSingleBits(state, rs2);
            updateInvalidFlagForSignalingSingleNaNs(state, leftBits, rightBits);
            float left = Float.intBitsToFloat(leftBits);
            float right = Float.intBitsToFloat(rightBits);
            updateInvalidFlagForArithmetic(state, left, right, operator);
            if (operator == '/') {
                updateDivideByZeroFlag(state, left, right);
            }
            float result = switch (operator) {
                case '+' -> left + right;
                case '-' -> left - right;
                case '*' -> left * right;
                case '/' -> left / right;
                default -> throw unexpectedFloatingPointOperator(operator);
            };
            writeSingleBits(state, rd, canonicalizeSingleBits(result));
        } else {
            long leftBits = state.floatingPointRegister(rs1);
            long rightBits = state.floatingPointRegister(rs2);
            updateInvalidFlagForSignalingDoubleNaNs(state, leftBits, rightBits);
            double left = Double.longBitsToDouble(leftBits);
            double right = Double.longBitsToDouble(rightBits);
            updateInvalidFlagForArithmetic(state, left, right, operator);
            if (operator == '/') {
                updateDivideByZeroFlag(state, left, right);
            }
            double result = switch (operator) {
                case '+' -> left + right;
                case '-' -> left - right;
                case '*' -> left * right;
                case '/' -> left / right;
                default -> throw unexpectedFloatingPointOperator(operator);
            };
            writeDoubleBits(state, rd, canonicalizeDoubleBits(result));
        }
        state.setPc(nextPc);
    }

    /// Executes a floating-point square-root operation.
    private void floatingPointSquareRoot(MachineState state, long nextPc) {
        checkEffectiveRoundingMode(state);
        if (floatingPointFormat() == SINGLE_FLOAT_FORMAT) {
            int bits = readSingleBits(state, rs1);
            if (isSignalingSingleNaN(bits)) {
                state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            }
            float value = Float.intBitsToFloat(bits);
            if (value < 0.0f) {
                state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            }
            writeSingleBits(state, rd, canonicalizeSingleBits((float) Math.sqrt(value)));
        } else {
            long bits = state.floatingPointRegister(rs1);
            if (isSignalingDoubleNaN(bits)) {
                state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            }
            double value = Double.longBitsToDouble(bits);
            if (value < 0.0d) {
                state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            }
            writeDoubleBits(state, rd, canonicalizeDoubleBits(Math.sqrt(value)));
        }
        state.setPc(nextPc);
    }

    /// Executes a floating-point sign-injection operation.
    private void floatingPointSignInjection(MachineState state, long nextPc, SignInjectionKind kind) {
        if (floatingPointFormat() == SINGLE_FLOAT_FORMAT) {
            int left = readSingleBits(state, rs1);
            int right = readSingleBits(state, rs2);
            int sign = switch (kind) {
                case COPY -> right & 0x8000_0000;
                case NEGATE -> ~right & 0x8000_0000;
                case XOR -> (left ^ right) & 0x8000_0000;
            };
            writeSingleBits(state, rd, (left & 0x7fff_ffff) | sign);
        } else {
            long left = state.floatingPointRegister(rs1);
            long right = state.floatingPointRegister(rs2);
            long sign = switch (kind) {
                case COPY -> right & Long.MIN_VALUE;
                case NEGATE -> ~right & Long.MIN_VALUE;
                case XOR -> (left ^ right) & Long.MIN_VALUE;
            };
            writeDoubleBits(state, rd, (left & Long.MAX_VALUE) | sign);
        }
        state.setPc(nextPc);
    }

    /// Executes a floating-point minimum or maximum operation.
    private void floatingPointMinimumMaximum(MachineState state, long nextPc, boolean minimum) {
        if (floatingPointFormat() == SINGLE_FLOAT_FORMAT) {
            writeSingleBits(state, rd, minimum
                    ? minimumSingleBits(state, readSingleBits(state, rs1), readSingleBits(state, rs2))
                    : maximumSingleBits(state, readSingleBits(state, rs1), readSingleBits(state, rs2)));
        } else {
            writeDoubleBits(state, rd, minimum
                    ? minimumDoubleBits(state, state.floatingPointRegister(rs1), state.floatingPointRegister(rs2))
                    : maximumDoubleBits(state, state.floatingPointRegister(rs1), state.floatingPointRegister(rs2)));
        }
        state.setPc(nextPc);
    }

    /// Executes a floating-point comparison operation.
    private void floatingPointCompare(MachineState state, long nextPc, CompareKind kind) {
        if (floatingPointFormat() == SINGLE_FLOAT_FORMAT) {
            int leftBits = readSingleBits(state, rs1);
            int rightBits = readSingleBits(state, rs2);
            float left = Float.intBitsToFloat(leftBits);
            float right = Float.intBitsToFloat(rightBits);
            if (Float.isNaN(left) || Float.isNaN(right)) {
                if (kind != CompareKind.EQUAL || isSignalingSingleNaN(leftBits) || isSignalingSingleNaN(rightBits)) {
                    state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
                }
                state.setRegister(rd, 0);
            } else {
                state.setRegister(rd, compareFloatingPoint(left, right, kind) ? 1 : 0);
            }
        } else {
            long leftBits = state.floatingPointRegister(rs1);
            long rightBits = state.floatingPointRegister(rs2);
            double left = Double.longBitsToDouble(leftBits);
            double right = Double.longBitsToDouble(rightBits);
            if (Double.isNaN(left) || Double.isNaN(right)) {
                if (kind != CompareKind.EQUAL || isSignalingDoubleNaN(leftBits) || isSignalingDoubleNaN(rightBits)) {
                    state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
                }
                state.setRegister(rd, 0);
            } else {
                state.setRegister(rd, compareFloatingPoint(left, right, kind) ? 1 : 0);
            }
        }
        state.setPc(nextPc);
    }

    /// Converts a floating-point value to an integer register.
    private void convertFloatingPointToInteger(MachineState state, long nextPc) {
        int roundingMode = effectiveRoundingMode(state);
        double value = floatingPointFormat() == SINGLE_FLOAT_FORMAT ? readSingle(state, rs1) : readDouble(state, rs1);
        switch (rs2) {
            case 0 -> state.setRegister(rd, (int) convertToSignedInteger(state, value, roundingMode, Integer.MIN_VALUE, 0x1.0p31));
            case 1 -> state.setRegister(rd, (int) convertToUnsignedInteger(state, value, roundingMode, 0x1.0p32));
            case 2 -> state.setRegister(rd, convertToSignedInteger(state, value, roundingMode, Long.MIN_VALUE, 0x1.0p63));
            case 3 -> state.setRegister(rd, convertToUnsignedInteger(state, value, roundingMode, 0x1.0p64));
            default -> throw unexpectedConversionSelector();
        }
        state.setPc(nextPc);
    }

    /// Converts an integer register value to a floating-point register.
    private void convertIntegerToFloatingPoint(MachineState state, long nextPc) {
        checkEffectiveRoundingMode(state);
        long value = state.register(rs1);
        if (floatingPointFormat() == SINGLE_FLOAT_FORMAT) {
            float result = switch (rs2) {
                case 0 -> (float) (int) value;
                case 1 -> (float) (value & 0xffff_ffffL);
                case 2 -> (float) value;
                case 3 -> unsignedLongToBigInteger(value).floatValue();
                default -> throw unexpectedConversionSelector();
            };
            writeSingleBits(state, rd, canonicalizeSingleBits(result));
        } else {
            double result = switch (rs2) {
                case 0 -> (double) (int) value;
                case 1 -> (double) (value & 0xffff_ffffL);
                case 2 -> (double) value;
                case 3 -> unsignedLongToBigInteger(value).doubleValue();
                default -> throw unexpectedConversionSelector();
            };
            writeDoubleBits(state, rd, canonicalizeDoubleBits(result));
        }
        state.setPc(nextPc);
    }

    /// Updates invalid-operation flags for signaling single-precision NaN inputs.
    private static void updateInvalidFlagForSignalingSingleNaNs(MachineState state, int first, int second) {
        if (isSignalingSingleNaN(first) || isSignalingSingleNaN(second)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
    }

    /// Updates invalid-operation flags for signaling single-precision NaN inputs.
    private static void updateInvalidFlagForSignalingSingleNaNs(MachineState state, int first, int second, int third) {
        if (isSignalingSingleNaN(first) || isSignalingSingleNaN(second) || isSignalingSingleNaN(third)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
    }

    /// Updates invalid-operation flags for signaling double-precision NaN inputs.
    private static void updateInvalidFlagForSignalingDoubleNaNs(MachineState state, long first, long second) {
        if (isSignalingDoubleNaN(first) || isSignalingDoubleNaN(second)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
    }

    /// Updates invalid-operation flags for signaling double-precision NaN inputs.
    private static void updateInvalidFlagForSignalingDoubleNaNs(MachineState state, long first, long second, long third) {
        if (isSignalingDoubleNaN(first) || isSignalingDoubleNaN(second) || isSignalingDoubleNaN(third)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
    }

    /// Updates invalid-operation flags for arithmetic indeterminate forms.
    private static void updateInvalidFlagForArithmetic(MachineState state, float left, float right, char operator) {
        if (switch (operator) {
            case '+', '-' -> Float.isInfinite(left) && Float.isInfinite(right)
                    && Math.copySign(1.0f, left) != Math.copySign(1.0f, operator == '-' ? -right : right);
            case '*' -> (left == 0.0f && Float.isInfinite(right)) || (Float.isInfinite(left) && right == 0.0f);
            case '/' -> (left == 0.0f && right == 0.0f) || (Float.isInfinite(left) && Float.isInfinite(right));
            default -> throw new AssertionError("Unexpected floating-point operator: " + operator);
        }) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
    }

    /// Updates invalid-operation flags for arithmetic indeterminate forms.
    private static void updateInvalidFlagForArithmetic(MachineState state, double left, double right, char operator) {
        if (switch (operator) {
            case '+', '-' -> Double.isInfinite(left) && Double.isInfinite(right)
                    && Math.copySign(1.0d, left) != Math.copySign(1.0d, operator == '-' ? -right : right);
            case '*' -> (left == 0.0d && Double.isInfinite(right)) || (Double.isInfinite(left) && right == 0.0d);
            case '/' -> (left == 0.0d && right == 0.0d) || (Double.isInfinite(left) && Double.isInfinite(right));
            default -> throw new AssertionError("Unexpected floating-point operator: " + operator);
        }) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
    }

    /// Updates invalid-operation flags for fused multiply-add indeterminate forms.
    private static void updateInvalidFlagForFusedMultiplyAdd(MachineState state, float left, float right, float addend) {
        if ((left == 0.0f && Float.isInfinite(right)) || (Float.isInfinite(left) && right == 0.0f)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            return;
        }
        float product = left * right;
        if (Float.isInfinite(product) && Float.isInfinite(addend)
                && Math.copySign(1.0f, product) != Math.copySign(1.0f, addend)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
    }

    /// Updates invalid-operation flags for fused multiply-add indeterminate forms.
    private static void updateInvalidFlagForFusedMultiplyAdd(MachineState state, double left, double right, double addend) {
        if ((left == 0.0d && Double.isInfinite(right)) || (Double.isInfinite(left) && right == 0.0d)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            return;
        }
        double product = left * right;
        if (Double.isInfinite(product) && Double.isInfinite(addend)
                && Math.copySign(1.0d, product) != Math.copySign(1.0d, addend)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
    }

    /// Reads a single-precision register as raw bits, applying NaN-boxing rules.
    private static int readSingleBits(MachineState state, int register) {
        long value = state.floatingPointRegister(register);
        return (value & SINGLE_NAN_BOX_MASK) == SINGLE_NAN_BOX_MASK ? (int) value : CANONICAL_SINGLE_NAN;
    }

    /// Reads a single-precision register as a Java float.
    private static float readSingle(MachineState state, int register) {
        return Float.intBitsToFloat(readSingleBits(state, register));
    }

    /// Reads a double-precision register as a Java double.
    private static double readDouble(MachineState state, int register) {
        return Double.longBitsToDouble(state.floatingPointRegister(register));
    }

    /// Writes raw single-precision bits to a NaN-boxed floating-point register.
    private static void writeSingleBits(MachineState state, int register, int bits) {
        state.setFloatingPointRegister(register, SINGLE_NAN_BOX_MASK | (bits & 0xffff_ffffL));
    }

    /// Writes raw double-precision bits to a floating-point register.
    private static void writeDoubleBits(MachineState state, int register, long bits) {
        state.setFloatingPointRegister(register, bits);
    }

    /// Canonicalizes a single-precision NaN result.
    private static int canonicalizeSingleBits(float value) {
        return Float.isNaN(value) ? CANONICAL_SINGLE_NAN : Float.floatToRawIntBits(value);
    }

    /// Canonicalizes a double-precision NaN result.
    private static long canonicalizeDoubleBits(double value) {
        return Double.isNaN(value) ? CANONICAL_DOUBLE_NAN : Double.doubleToRawLongBits(value);
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

    /// Compares two finite-or-infinite floating-point values.
    private static boolean compareFloatingPoint(double left, double right, CompareKind kind) {
        return switch (kind) {
            case EQUAL -> left == right;
            case LESS_THAN -> left < right;
            case LESS_THAN_OR_EQUAL -> left <= right;
        };
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

    /// Updates divide-by-zero flags for single-precision division.
    private static void updateDivideByZeroFlag(MachineState state, float dividend, float divisor) {
        if (divisor == 0.0f && !Float.isNaN(dividend) && !Float.isInfinite(dividend) && dividend != 0.0f) {
            state.addFloatingPointFlags(FLOATING_POINT_DIVIDE_BY_ZERO);
        }
    }

    /// Updates divide-by-zero flags for double-precision division.
    private static void updateDivideByZeroFlag(MachineState state, double dividend, double divisor) {
        if (divisor == 0.0d && !Double.isNaN(dividend) && !Double.isInfinite(dividend) && dividend != 0.0d) {
            state.addFloatingPointFlags(FLOATING_POINT_DIVIDE_BY_ZERO);
        }
    }

    /// Converts a floating-point value to a signed integer with saturation.
    private static long convertToSignedInteger(
            MachineState state,
            double value,
            int roundingMode,
            long minimum,
            double exclusiveUpperBound) {
        double rounded = roundToInteger(value, roundingMode);
        if (Double.isNaN(value) || rounded >= exclusiveUpperBound) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            return exclusiveUpperBound == 0x1.0p63 ? Long.MAX_VALUE : (long) exclusiveUpperBound - 1;
        }
        if (rounded < minimum) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            return minimum;
        }
        if (rounded != value) {
            state.addFloatingPointFlags(FLOATING_POINT_INEXACT);
        }
        return (long) rounded;
    }

    /// Converts a floating-point value to an unsigned integer with saturation.
    private static long convertToUnsignedInteger(MachineState state, double value, int roundingMode, double exclusiveUpperBound) {
        double rounded = roundToInteger(value, roundingMode);
        if (Double.isNaN(value) || rounded >= exclusiveUpperBound) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            return -1L;
        }
        if (rounded < 0.0d) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            return 0;
        }
        if (rounded != value) {
            state.addFloatingPointFlags(FLOATING_POINT_INEXACT);
        }
        if (rounded >= 0x1.0p63) {
            return ((long) (rounded - 0x1.0p63)) | Long.MIN_VALUE;
        }
        return (long) rounded;
    }

    /// Rounds a floating-point value to an integral double using an RISC-V rounding mode.
    private static double roundToInteger(double value, int roundingMode) {
        return switch (roundingMode) {
            case ROUND_NEAREST_EVEN -> Math.rint(value);
            case ROUND_TOWARD_ZERO -> value < 0.0d ? Math.ceil(value) : Math.floor(value);
            case ROUND_DOWN -> Math.floor(value);
            case ROUND_UP -> Math.ceil(value);
            case ROUND_NEAREST_MAX_MAGNITUDE -> Math.copySign(Math.floor(Math.abs(value) + 0.5d), value);
            default -> throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
        };
    }

    /// Returns the effective rounding mode for this instruction.
    private int effectiveRoundingMode(MachineState state) {
        int roundingMode = (int) (immediate & 0x7);
        if (roundingMode == ROUND_DYNAMIC) {
            roundingMode = state.floatingPointRoundingMode();
        }
        if (roundingMode > ROUND_NEAREST_MAX_MAGNITUDE) {
            throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
        }
        return roundingMode;
    }

    /// Validates and discards the effective rounding mode.
    private void checkEffectiveRoundingMode(MachineState state) {
        effectiveRoundingMode(state);
    }

    /// Returns the packed floating-point format.
    private int floatingPointFormat() {
        return (int) ((immediate >>> FLOATING_POINT_FORMAT_SHIFT) & 0x3);
    }

    /// Returns the packed third floating-point source register.
    private int thirdFloatingPointSource() {
        return (int) ((immediate >>> THIRD_FLOATING_POINT_SOURCE_SHIFT) & 0x1f);
    }

    /// Creates an assertion error for an impossible conversion selector.
    private AssertionError unexpectedConversionSelector() {
        return new AssertionError("Unexpected conversion selector: " + rs2);
    }

    /// Creates an assertion error for an impossible floating-point operator.
    private AssertionError unexpectedFloatingPointOperator(char operator) {
        return new AssertionError("Unexpected floating-point operator: " + operator);
    }

    /// Computes RV64 signed division.
    private static long divideSigned(long dividend, long divisor) {
        if (divisor == 0) {
            return -1L;
        }
        if (dividend == Long.MIN_VALUE && divisor == -1L) {
            return dividend;
        }
        return dividend / divisor;
    }

    /// Computes RV64 unsigned division.
    private static long divideUnsigned(long dividend, long divisor) {
        return divisor == 0 ? -1L : Long.divideUnsigned(dividend, divisor);
    }

    /// Computes RV64 signed remainder.
    private static long remainderSigned(long dividend, long divisor) {
        if (divisor == 0) {
            return dividend;
        }
        if (dividend == Long.MIN_VALUE && divisor == -1L) {
            return 0;
        }
        return dividend % divisor;
    }

    /// Computes RV64 unsigned remainder.
    private static long remainderUnsigned(long dividend, long divisor) {
        return divisor == 0 ? dividend : Long.remainderUnsigned(dividend, divisor);
    }

    /// Computes RV64 signed 32-bit division.
    private static int divideSignedWord(int dividend, int divisor) {
        if (divisor == 0) {
            return -1;
        }
        if (dividend == Integer.MIN_VALUE && divisor == -1) {
            return dividend;
        }
        return dividend / divisor;
    }

    /// Computes RV64 unsigned 32-bit division.
    private static int divideUnsignedWord(int dividend, int divisor) {
        return divisor == 0 ? -1 : Integer.divideUnsigned(dividend, divisor);
    }

    /// Computes RV64 signed 32-bit remainder.
    private static int remainderSignedWord(int dividend, int divisor) {
        if (divisor == 0) {
            return dividend;
        }
        if (dividend == Integer.MIN_VALUE && divisor == -1) {
            return 0;
        }
        return dividend % divisor;
    }

    /// Computes RV64 unsigned 32-bit remainder.
    private static int remainderUnsignedWord(int dividend, int divisor) {
        return divisor == 0 ? dividend : Integer.remainderUnsigned(dividend, divisor);
    }

    /// Computes the high half of a signed-by-unsigned 64-bit product.
    private static long multiplyHighSignedUnsigned(long signed, long unsigned) {
        BigInteger product = BigInteger.valueOf(signed).multiply(unsignedLongToBigInteger(unsigned));
        return product.shiftRight(Long.SIZE).longValue();
    }

    /// Converts a Java long to a non-negative unsigned BigInteger.
    private static BigInteger unsignedLongToBigInteger(long value) {
        BigInteger result = BigInteger.valueOf(value & Long.MAX_VALUE);
        return value < 0 ? result.setBit(Long.SIZE - 1) : result;
    }

    /// The decoded operation set implemented by the simulator.
    @NotNullByDefault
    public enum Operation {
        /// No operation.
        NOP,
        /// Load upper immediate.
        LUI,
        /// Add upper immediate to PC.
        AUIPC,
        /// Jump and link.
        JAL,
        /// Jump and link register.
        JALR,
        /// Branch if equal.
        BEQ,
        /// Branch if not equal.
        BNE,
        /// Branch if less than signed.
        BLT,
        /// Branch if greater than or equal signed.
        BGE,
        /// Branch if less than unsigned.
        BLTU,
        /// Branch if greater than or equal unsigned.
        BGEU,
        /// Load signed byte.
        LB,
        /// Load signed halfword.
        LH,
        /// Load signed word.
        LW,
        /// Load doubleword.
        LD,
        /// Load unsigned byte.
        LBU,
        /// Load unsigned halfword.
        LHU,
        /// Load unsigned word.
        LWU,
        /// Load 32-bit floating-point value.
        FLW,
        /// Load 64-bit floating-point value.
        FLD,
        /// Store byte.
        SB,
        /// Store halfword.
        SH,
        /// Store word.
        SW,
        /// Store doubleword.
        SD,
        /// Store 32-bit floating-point value.
        FSW,
        /// Store 64-bit floating-point value.
        FSD,
        /// Fused floating-point multiply-add.
        FMADD,
        /// Fused floating-point multiply-subtract.
        FMSUB,
        /// Fused negated floating-point multiply-subtract.
        FNMSUB,
        /// Fused negated floating-point multiply-add.
        FNMADD,
        /// Floating-point add.
        FADD,
        /// Floating-point subtract.
        FSUB,
        /// Floating-point multiply.
        FMUL,
        /// Floating-point divide.
        FDIV,
        /// Floating-point square root.
        FSQRT,
        /// Floating-point sign injection.
        FSGNJ,
        /// Floating-point negated sign injection.
        FSGNJN,
        /// Floating-point xor sign injection.
        FSGNJX,
        /// Floating-point minimum.
        FMIN,
        /// Floating-point maximum.
        FMAX,
        /// Convert double-precision value to single-precision value.
        FCVT_S_D,
        /// Convert single-precision value to double-precision value.
        FCVT_D_S,
        /// Floating-point equal comparison.
        FEQ,
        /// Floating-point less-than comparison.
        FLT,
        /// Floating-point less-than-or-equal comparison.
        FLE,
        /// Floating-point classify.
        FCLASS,
        /// Convert floating-point value to integer register value.
        FCVT_INT_FP,
        /// Convert integer register value to floating-point value.
        FCVT_FP_INT,
        /// Move floating-point bits to integer register.
        FMV_X_FP,
        /// Move integer register bits to floating-point register.
        FMV_FP_X,
        /// Add immediate.
        ADDI,
        /// Set less than immediate signed.
        SLTI,
        /// Set less than immediate unsigned.
        SLTIU,
        /// Exclusive-or immediate.
        XORI,
        /// Or immediate.
        ORI,
        /// And immediate.
        ANDI,
        /// Shift left logical immediate.
        SLLI,
        /// Shift right logical immediate.
        SRLI,
        /// Shift right arithmetic immediate.
        SRAI,
        /// Add immediate word.
        ADDIW,
        /// Shift left logical immediate word.
        SLLIW,
        /// Shift right logical immediate word.
        SRLIW,
        /// Shift right arithmetic immediate word.
        SRAIW,
        /// Add registers.
        ADD,
        /// Subtract registers.
        SUB,
        /// Shift left logical registers.
        SLL,
        /// Set less than signed registers.
        SLT,
        /// Set less than unsigned registers.
        SLTU,
        /// Exclusive-or registers.
        XOR,
        /// Shift right logical registers.
        SRL,
        /// Shift right arithmetic registers.
        SRA,
        /// Or registers.
        OR,
        /// And registers.
        AND,
        /// Add word registers.
        ADDW,
        /// Subtract word registers.
        SUBW,
        /// Shift left logical word registers.
        SLLW,
        /// Shift right logical word registers.
        SRLW,
        /// Shift right arithmetic word registers.
        SRAW,
        /// Multiply low 64 bits.
        MUL,
        /// Multiply high signed.
        MULH,
        /// Multiply high signed by unsigned.
        MULHSU,
        /// Multiply high unsigned.
        MULHU,
        /// Divide signed.
        DIV,
        /// Divide unsigned.
        DIVU,
        /// Remainder signed.
        REM,
        /// Remainder unsigned.
        REMU,
        /// Multiply low word.
        MULW,
        /// Divide signed word.
        DIVW,
        /// Divide unsigned word.
        DIVUW,
        /// Remainder signed word.
        REMW,
        /// Remainder unsigned word.
        REMUW,
        /// Memory fence no-op for the single-threaded MVP.
        FENCE,
        /// Instruction-fetch fence no-op for the single-threaded MVP.
        FENCE_I,
        /// Environment call.
        ECALL,
        /// Environment break.
        EBREAK,
        /// Atomic read/write CSR.
        CSRRW,
        /// Atomic read and set bits in CSR.
        CSRRS,
        /// Atomic read and clear bits in CSR.
        CSRRC,
        /// Atomic write immediate CSR.
        CSRRWI,
        /// Atomic read and set immediate bits in CSR.
        CSRRSI,
        /// Atomic read and clear immediate bits in CSR.
        CSRRCI,
        /// Load-reserved word.
        LR_W,
        /// Load-reserved doubleword.
        LR_D,
        /// Store-conditional word.
        SC_W,
        /// Store-conditional doubleword.
        SC_D,
        /// Atomic swap word.
        AMOSWAP_W,
        /// Atomic add word.
        AMOADD_W,
        /// Atomic xor word.
        AMOXOR_W,
        /// Atomic and word.
        AMOAND_W,
        /// Atomic or word.
        AMOOR_W,
        /// Atomic signed minimum word.
        AMOMIN_W,
        /// Atomic signed maximum word.
        AMOMAX_W,
        /// Atomic unsigned minimum word.
        AMOMINU_W,
        /// Atomic unsigned maximum word.
        AMOMAXU_W,
        /// Atomic swap doubleword.
        AMOSWAP_D,
        /// Atomic add doubleword.
        AMOADD_D,
        /// Atomic xor doubleword.
        AMOXOR_D,
        /// Atomic and doubleword.
        AMOAND_D,
        /// Atomic or doubleword.
        AMOOR_D,
        /// Atomic signed minimum doubleword.
        AMOMIN_D,
        /// Atomic signed maximum doubleword.
        AMOMAX_D,
        /// Atomic unsigned minimum doubleword.
        AMOMINU_D,
        /// Atomic unsigned maximum doubleword.
        AMOMAXU_D
    }

    /// The operation variants shared by AMO instructions.
    @NotNullByDefault
    private enum AmoKind {
        /// Atomic swap.
        SWAP,
        /// Atomic add.
        ADD,
        /// Atomic xor.
        XOR,
        /// Atomic and.
        AND,
        /// Atomic or.
        OR,
        /// Atomic signed minimum.
        MIN,
        /// Atomic signed maximum.
        MAX,
        /// Atomic unsigned minimum.
        MINU,
        /// Atomic unsigned maximum.
        MAXU
    }

    /// The sign-selection variants used by floating-point sign-injection instructions.
    @NotNullByDefault
    private enum SignInjectionKind {
        /// Copy the second operand sign.
        COPY,
        /// Copy the inverted second operand sign.
        NEGATE,
        /// Xor both operand signs.
        XOR
    }

    /// The comparison variants used by floating-point compare instructions.
    @NotNullByDefault
    private enum CompareKind {
        /// Equality comparison.
        EQUAL,
        /// Less-than comparison.
        LESS_THAN,
        /// Less-than-or-equal comparison.
        LESS_THAN_OR_EQUAL
    }
}
