package org.glavo.riscv;

import com.oracle.truffle.api.nodes.Node;
import org.jetbrains.annotations.NotNullByDefault;

import java.math.BigInteger;

/// Executes one decoded RV64GC guest instruction subset.
@NotNullByDefault
public final class InstructionNode extends Node {
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
}
