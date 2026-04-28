package org.glavo.riscv;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Executes one decoded RISC-V basic block through the custom micro-bytecode interpreter.
@NotNullByDefault
final class RiscVMicroBlockRootNode extends RootNode {
    /// Five-bit mask for one packed RISC-V register index.
    private static final int REGISTER_MASK = 0x1f;

    /// Bit shift for the first source register in a packed operand.
    private static final int RS1_SHIFT = 5;

    /// Bit shift for the second source register in a packed operand.
    private static final int RS2_SHIFT = 10;

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

    /// Creates a micro-bytecode block root.
    RiscVMicroBlockRootNode(
            RiscVLanguage language,
            byte @Unmodifiable [] opcodes,
            RiscVOperation @Unmodifiable [] operations,
            int @Unmodifiable [] operands,
            int @Unmodifiable [] raws,
            long @Unmodifiable [] addresses,
            long @Unmodifiable [] nextPcs,
            long @Unmodifiable [] immediates) {
        super(language);
        this.opcodes = opcodes.clone();
        this.operations = operations.clone();
        this.operands = operands.clone();
        this.raws = raws.clone();
        this.addresses = addresses.clone();
        this.nextPcs = nextPcs.clone();
        this.immediates = immediates.clone();
    }

    /// Executes the block with the `MachineState` supplied as the first argument.
    @Override
    public Object execute(VirtualFrame frame) {
        return executeBlock((MachineState) frame.getArguments()[0]);
    }

    /// Runs all micro-ops in this block and returns the materialized next PC.
    @ExplodeLoop
    private long executeBlock(MachineState state) {
        for (int index = 0; index < opcodes.length; index++) {
            executeInstruction(state, index);
        }
        return state.pc();
    }

    /// Executes one micro-op by decoding its packed operand.
    private void executeInstruction(MachineState state, int index) {
        byte opcode = opcodes[index];
        int operand = operands[index];
        switch (opcode) {
            case RiscVMicroOpcode.EXECUTE_OPERATION -> executeOperation(state, index, operand);
            case RiscVMicroOpcode.ADVANCE_PC -> {
                beginInstruction(state, index);
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LUI -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), immediates[index]);
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.AUIPC -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), addresses[index] + immediates[index]);
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.JAL -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), nextPcs[index]);
                state.setPc(addresses[index] + immediates[index]);
            }
            case RiscVMicroOpcode.JALR -> {
                beginInstruction(state, index);
                long target = (state.decodedRegister(rs1(operand)) + immediates[index]) & ~1L;
                state.setDecodedRegister(rd(operand), nextPcs[index]);
                state.setPc(target);
            }
            case RiscVMicroOpcode.BEQ -> {
                beginInstruction(state, index);
                branch(state, state.decodedRegister(rs1(operand)) == state.decodedRegister(rs2(operand)), index);
            }
            case RiscVMicroOpcode.BNE -> {
                beginInstruction(state, index);
                branch(state, state.decodedRegister(rs1(operand)) != state.decodedRegister(rs2(operand)), index);
            }
            case RiscVMicroOpcode.BLT -> {
                beginInstruction(state, index);
                branch(state, state.decodedRegister(rs1(operand)) < state.decodedRegister(rs2(operand)), index);
            }
            case RiscVMicroOpcode.BGE -> {
                beginInstruction(state, index);
                branch(state, state.decodedRegister(rs1(operand)) >= state.decodedRegister(rs2(operand)), index);
            }
            case RiscVMicroOpcode.BLTU -> {
                beginInstruction(state, index);
                branch(state, Long.compareUnsigned(state.decodedRegister(rs1(operand)), state.decodedRegister(rs2(operand))) < 0, index);
            }
            case RiscVMicroOpcode.BGEU -> {
                beginInstruction(state, index);
                branch(state, Long.compareUnsigned(state.decodedRegister(rs1(operand)), state.decodedRegister(rs2(operand))) >= 0, index);
            }
            case RiscVMicroOpcode.LB -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readByte(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LH -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readShort(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LW -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readInt(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LD -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readLong(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LBU -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readUnsignedByte(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LHU -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readUnsignedShort(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.LWU -> {
                beginInstruction(state, index);
                state.setDecodedRegister(rd(operand), state.memory().readUnsignedInt(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.SB -> {
                beginInstruction(state, index);
                long address = loadAddress(state, operand, index);
                state.memory().writeByte(address, (byte) state.decodedRegister(rs2(operand)));
                afterStore(state, address, Byte.BYTES);
                state.clearReservation();
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.SH -> {
                beginInstruction(state, index);
                long address = loadAddress(state, operand, index);
                state.memory().writeShort(address, (short) state.decodedRegister(rs2(operand)));
                afterStore(state, address, Short.BYTES);
                state.clearReservation();
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.SW -> {
                beginInstruction(state, index);
                long address = loadAddress(state, operand, index);
                state.memory().writeInt(address, (int) state.decodedRegister(rs2(operand)));
                afterStore(state, address, Integer.BYTES);
                state.clearReservation();
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.SD -> {
                beginInstruction(state, index);
                long address = loadAddress(state, operand, index);
                state.memory().writeLong(address, state.decodedRegister(rs2(operand)));
                afterStore(state, address, Long.BYTES);
                state.clearReservation();
                state.setPc(nextPcs[index]);
            }
            case RiscVMicroOpcode.ADDI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) + immediates[index]);
            case RiscVMicroOpcode.SLTI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) < immediates[index] ? 1 : 0);
            case RiscVMicroOpcode.SLTIU -> binaryImmediate(state, index, operand, Long.compareUnsigned(state.decodedRegister(rs1(operand)), immediates[index]) < 0 ? 1 : 0);
            case RiscVMicroOpcode.XORI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) ^ immediates[index]);
            case RiscVMicroOpcode.ORI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) | immediates[index]);
            case RiscVMicroOpcode.ANDI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) & immediates[index]);
            case RiscVMicroOpcode.SLLI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) << immediates[index]);
            case RiscVMicroOpcode.SRLI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) >>> immediates[index]);
            case RiscVMicroOpcode.SRAI -> binaryImmediate(state, index, operand, state.decodedRegister(rs1(operand)) >> immediates[index]);
            case RiscVMicroOpcode.ADDIW -> binaryImmediate(state, index, operand, (int) (state.decodedRegister(rs1(operand)) + immediates[index]));
            case RiscVMicroOpcode.SLLIW -> binaryImmediate(state, index, operand, (int) state.decodedRegister(rs1(operand)) << immediates[index]);
            case RiscVMicroOpcode.SRLIW -> binaryImmediate(state, index, operand, (int) state.decodedRegister(rs1(operand)) >>> immediates[index]);
            case RiscVMicroOpcode.SRAIW -> binaryImmediate(state, index, operand, (int) state.decodedRegister(rs1(operand)) >> immediates[index]);
            case RiscVMicroOpcode.ADD -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) + state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.SUB -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) - state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.SLL -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) << (state.decodedRegister(rs2(operand)) & 0x3f));
            case RiscVMicroOpcode.SLT -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) < state.decodedRegister(rs2(operand)) ? 1 : 0);
            case RiscVMicroOpcode.SLTU -> binaryRegister(state, index, operand, Long.compareUnsigned(state.decodedRegister(rs1(operand)), state.decodedRegister(rs2(operand))) < 0 ? 1 : 0);
            case RiscVMicroOpcode.XOR -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) ^ state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.SRL -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) >>> (state.decodedRegister(rs2(operand)) & 0x3f));
            case RiscVMicroOpcode.SRA -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) >> (state.decodedRegister(rs2(operand)) & 0x3f));
            case RiscVMicroOpcode.OR -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) | state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.AND -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) & state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.ADDW -> binaryRegister(state, index, operand, (int) state.decodedRegister(rs1(operand)) + (int) state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.SUBW -> binaryRegister(state, index, operand, (int) state.decodedRegister(rs1(operand)) - (int) state.decodedRegister(rs2(operand)));
            case RiscVMicroOpcode.SLLW -> binaryRegister(state, index, operand, (int) state.decodedRegister(rs1(operand)) << (state.decodedRegister(rs2(operand)) & 0x1f));
            case RiscVMicroOpcode.SRLW -> binaryRegister(state, index, operand, (int) state.decodedRegister(rs1(operand)) >>> (state.decodedRegister(rs2(operand)) & 0x1f));
            case RiscVMicroOpcode.SRAW -> binaryRegister(state, index, operand, (int) state.decodedRegister(rs1(operand)) >> (state.decodedRegister(rs2(operand)) & 0x1f));
            default -> throw new RiscVException("Unknown micro-bytecode opcode: " + opcode);
        }
    }

    /// Executes a canonical operation that does not yet need a dedicated opcode value.
    private void executeOperation(MachineState state, int index, int operand) {
        RiscVOperation operation = operations[index];
        switch (operation) {
            case ECALL -> {
                beginInstruction(state, index);
                state.syscalls().handle(state, addresses[index]);
                state.setPc(nextPcs[index]);
            }
            case EBREAK -> {
                beginInstruction(state, index);
                throw new ProgramExitException(0);
            }
            case CSRRW -> {
                beginInstruction(state, index);
                writeControlStatusRegister(state, index, operand, state.decodedRegister(rs1(operand)));
            }
            case CSRRS -> {
                beginInstruction(state, index);
                setClearControlStatusRegister(state, index, operand, state.decodedRegister(rs1(operand)), true);
            }
            case CSRRC -> {
                beginInstruction(state, index);
                setClearControlStatusRegister(state, index, operand, state.decodedRegister(rs1(operand)), false);
            }
            case CSRRWI -> {
                beginInstruction(state, index);
                writeControlStatusRegister(state, index, operand, rs1(operand));
            }
            case CSRRSI -> {
                beginInstruction(state, index);
                setClearControlStatusRegister(state, index, operand, rs1(operand), true);
            }
            case CSRRCI -> {
                beginInstruction(state, index);
                setClearControlStatusRegister(state, index, operand, rs1(operand), false);
            }
            case MUL -> binaryRegister(state, index, operand, state.decodedRegister(rs1(operand)) * state.decodedRegister(rs2(operand)));
            case MULH -> binaryRegister(state, index, operand, Math.multiplyHigh(state.decodedRegister(rs1(operand)), state.decodedRegister(rs2(operand))));
            case MULHSU -> binaryRegister(state, index, operand, multiplyHighSignedUnsigned(state.decodedRegister(rs1(operand)), state.decodedRegister(rs2(operand))));
            case MULHU -> binaryRegister(state, index, operand, Math.unsignedMultiplyHigh(state.decodedRegister(rs1(operand)), state.decodedRegister(rs2(operand))));
            case DIV -> binaryRegister(state, index, operand, divideSigned(state.decodedRegister(rs1(operand)), state.decodedRegister(rs2(operand))));
            case DIVU -> binaryRegister(state, index, operand, divideUnsigned(state.decodedRegister(rs1(operand)), state.decodedRegister(rs2(operand))));
            case REM -> binaryRegister(state, index, operand, remainderSigned(state.decodedRegister(rs1(operand)), state.decodedRegister(rs2(operand))));
            case REMU -> binaryRegister(state, index, operand, remainderUnsigned(state.decodedRegister(rs1(operand)), state.decodedRegister(rs2(operand))));
            case MULW -> binaryRegister(state, index, operand, (int) state.decodedRegister(rs1(operand)) * (int) state.decodedRegister(rs2(operand)));
            case DIVW -> binaryRegister(state, index, operand, divideSignedWord((int) state.decodedRegister(rs1(operand)), (int) state.decodedRegister(rs2(operand))));
            case DIVUW -> binaryRegister(state, index, operand, divideUnsignedWord((int) state.decodedRegister(rs1(operand)), (int) state.decodedRegister(rs2(operand))));
            case REMW -> binaryRegister(state, index, operand, remainderSignedWord((int) state.decodedRegister(rs1(operand)), (int) state.decodedRegister(rs2(operand))));
            case REMUW -> binaryRegister(state, index, operand, remainderUnsignedWord((int) state.decodedRegister(rs1(operand)), (int) state.decodedRegister(rs2(operand))));
            case FLW -> {
                beginInstruction(state, index);
                state.setDecodedFloatingPointRegister(rd(operand), 0xffff_ffff_0000_0000L | state.memory().readUnsignedInt(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case FLD -> {
                beginInstruction(state, index);
                state.setDecodedFloatingPointRegister(rd(operand), state.memory().readLong(loadAddress(state, operand, index)));
                state.setPc(nextPcs[index]);
            }
            case FSW -> {
                beginInstruction(state, index);
                long address = loadAddress(state, operand, index);
                state.memory().writeInt(address, (int) state.decodedFloatingPointRegister(rs2(operand)));
                afterStore(state, address, Integer.BYTES);
                state.clearReservation();
                state.setPc(nextPcs[index]);
            }
            case FSD -> {
                beginInstruction(state, index);
                long address = loadAddress(state, operand, index);
                state.memory().writeLong(address, state.decodedFloatingPointRegister(rs2(operand)));
                afterStore(state, address, Long.BYTES);
                state.clearReservation();
                state.setPc(nextPcs[index]);
            }
            case LR_W -> lrWord(state, index, operand);
            case LR_D -> lrDouble(state, index, operand);
            case SC_W -> scWord(state, index, operand);
            case SC_D -> scDouble(state, index, operand);
            case AMOSWAP_W -> amoWord(state, index, operand, AmoKind.SWAP);
            case AMOADD_W -> amoWord(state, index, operand, AmoKind.ADD);
            case AMOXOR_W -> amoWord(state, index, operand, AmoKind.XOR);
            case AMOAND_W -> amoWord(state, index, operand, AmoKind.AND);
            case AMOOR_W -> amoWord(state, index, operand, AmoKind.OR);
            case AMOMIN_W -> amoWord(state, index, operand, AmoKind.MIN);
            case AMOMAX_W -> amoWord(state, index, operand, AmoKind.MAX);
            case AMOMINU_W -> amoWord(state, index, operand, AmoKind.MINU);
            case AMOMAXU_W -> amoWord(state, index, operand, AmoKind.MAXU);
            case AMOSWAP_D -> amoDouble(state, index, operand, AmoKind.SWAP);
            case AMOADD_D -> amoDouble(state, index, operand, AmoKind.ADD);
            case AMOXOR_D -> amoDouble(state, index, operand, AmoKind.XOR);
            case AMOAND_D -> amoDouble(state, index, operand, AmoKind.AND);
            case AMOOR_D -> amoDouble(state, index, operand, AmoKind.OR);
            case AMOMIN_D -> amoDouble(state, index, operand, AmoKind.MIN);
            case AMOMAX_D -> amoDouble(state, index, operand, AmoKind.MAX);
            case AMOMINU_D -> amoDouble(state, index, operand, AmoKind.MINU);
            case AMOMAXU_D -> amoDouble(state, index, operand, AmoKind.MAXU);
            default -> RiscVInstructionSemantics.executeMicro(
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
    }

    /// Records the current instruction before running a direct micro-op body.
    private void beginInstruction(MachineState state, int index) {
        long address = addresses[index];
        state.setPc(address);
        state.beforeInstruction(address, raws[index]);
    }

    /// Writes a conditional branch target.
    private void branch(MachineState state, boolean taken, int index) {
        state.setPc(taken ? addresses[index] + immediates[index] : nextPcs[index]);
    }

    /// Computes a memory address from `rs1` and an immediate.
    private long loadAddress(MachineState state, int operand, int index) {
        return state.decodedRegister(rs1(operand)) + immediates[index];
    }

    /// Writes the result of an immediate integer operation.
    private void binaryImmediate(MachineState state, int index, int operand, long value) {
        beginInstruction(state, index);
        state.setDecodedRegister(rd(operand), value);
        state.setPc(nextPcs[index]);
    }

    /// Writes the result of a register integer operation.
    private void binaryRegister(MachineState state, int index, int operand, long value) {
        beginInstruction(state, index);
        state.setDecodedRegister(rd(operand), value);
        state.setPc(nextPcs[index]);
    }

    /// Writes a CSR and returns the old value when the destination register is not `x0`.
    private void writeControlStatusRegister(MachineState state, int index, int operand, long value) {
        int rd = rd(operand);
        long oldValue = rd == 0 ? 0 : state.readControlStatusRegister((int) immediates[index]);
        state.writeControlStatusRegister((int) immediates[index], value);
        state.setDecodedRegister(rd, oldValue);
        state.setPc(nextPcs[index]);
    }

    /// Sets or clears CSR bits using the supplied mask value.
    private void setClearControlStatusRegister(MachineState state, int index, int operand, long mask, boolean setBits) {
        long oldValue = state.readControlStatusRegister((int) immediates[index]);
        if (mask != 0) {
            state.writeControlStatusRegister((int) immediates[index], setBits ? oldValue | mask : oldValue & ~mask);
        }
        state.setDecodedRegister(rd(operand), oldValue);
        state.setPc(nextPcs[index]);
    }

    /// Loads and reserves a 32-bit memory word.
    private void lrWord(MachineState state, int index, int operand) {
        beginInstruction(state, index);
        Memory memory = state.memory();
        synchronized (memory) {
            long address = state.decodedRegister(rs1(operand));
            state.setDecodedRegister(rd(operand), memory.readInt(address));
            state.reserve(address);
        }
        state.setPc(nextPcs[index]);
    }

    /// Loads and reserves a 64-bit memory doubleword.
    private void lrDouble(MachineState state, int index, int operand) {
        beginInstruction(state, index);
        Memory memory = state.memory();
        synchronized (memory) {
            long address = state.decodedRegister(rs1(operand));
            state.setDecodedRegister(rd(operand), memory.readLong(address));
            state.reserve(address);
        }
        state.setPc(nextPcs[index]);
    }

    /// Conditionally stores a 32-bit memory word through an LR/SC reservation.
    private void scWord(MachineState state, int index, int operand) {
        beginInstruction(state, index);
        Memory memory = state.memory();
        synchronized (memory) {
            long address = state.decodedRegister(rs1(operand));
            if (state.hasReservation(address)) {
                memory.writeInt(address, (int) state.decodedRegister(rs2(operand)));
                afterStore(state, address, Integer.BYTES);
                state.setDecodedRegister(rd(operand), 0);
            } else {
                state.setDecodedRegister(rd(operand), 1);
            }
            state.clearReservation();
        }
        state.setPc(nextPcs[index]);
    }

    /// Conditionally stores a 64-bit memory doubleword through an LR/SC reservation.
    private void scDouble(MachineState state, int index, int operand) {
        beginInstruction(state, index);
        Memory memory = state.memory();
        synchronized (memory) {
            long address = state.decodedRegister(rs1(operand));
            if (state.hasReservation(address)) {
                memory.writeLong(address, state.decodedRegister(rs2(operand)));
                afterStore(state, address, Long.BYTES);
                state.setDecodedRegister(rd(operand), 0);
            } else {
                state.setDecodedRegister(rd(operand), 1);
            }
            state.clearReservation();
        }
        state.setPc(nextPcs[index]);
    }

    /// Executes a 32-bit AMO instruction.
    private void amoWord(MachineState state, int index, int operand, AmoKind kind) {
        beginInstruction(state, index);
        Memory memory = state.memory();
        synchronized (memory) {
            long address = state.decodedRegister(rs1(operand));
            int oldValue = memory.readInt(address);
            int source = (int) state.decodedRegister(rs2(operand));
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
            state.setDecodedRegister(rd(operand), oldValue);
            afterStore(state, address, Integer.BYTES);
            state.clearReservation();
        }
        state.setPc(nextPcs[index]);
    }

    /// Executes a 64-bit AMO instruction.
    private void amoDouble(MachineState state, int index, int operand, AmoKind kind) {
        beginInstruction(state, index);
        Memory memory = state.memory();
        synchronized (memory) {
            long address = state.decodedRegister(rs1(operand));
            long oldValue = memory.readLong(address);
            long source = state.decodedRegister(rs2(operand));
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
            state.setDecodedRegister(rd(operand), oldValue);
            afterStore(state, address, Long.BYTES);
            state.clearReservation();
        }
        state.setPc(nextPcs[index]);
    }

    /// Handles optional simulator side effects after a store.
    private static void afterStore(MachineState state, long address, int length) {
        if (state.hasStoreSideEffects()) {
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

    /// Computes the high half of signed by unsigned 64-bit multiplication.
    private static long multiplyHighSignedUnsigned(long signed, long unsigned) {
        return Math.multiplyHigh(signed, unsigned) + (unsigned < 0 ? signed : 0);
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
