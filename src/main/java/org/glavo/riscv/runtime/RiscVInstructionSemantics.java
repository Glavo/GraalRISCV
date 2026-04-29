// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.ProgramExitException;
import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.parser.DecodedInstruction;
import org.glavo.riscv.parser.RiscVOperation;
import org.jetbrains.annotations.NotNullByDefault;

import java.math.BigInteger;

/// Executes one decoded RV64GC guest instruction through shared semantic helpers.
@NotNullByDefault
public abstract sealed class RiscVInstructionSemantics {
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

    /// The floating-point underflow exception flag.
    private static final int FLOATING_POINT_UNDERFLOW = 0x02;

    /// The floating-point overflow exception flag.
    private static final int FLOATING_POINT_OVERFLOW = 0x04;

    /// The floating-point invalid-operation exception flag.
    private static final int FLOATING_POINT_INVALID_OPERATION = 0x10;

    /// The floating-point divide-by-zero exception flag.
    private static final int FLOATING_POINT_DIVIDE_BY_ZERO = 0x08;

    /// The `cycle` user counter CSR address.
    private static final int CYCLE_CSR = 0xc00;

    /// The `time` user counter CSR address.
    private static final int TIME_CSR = 0xc01;

    /// The `instret` user counter CSR address.
    private static final int INSTRET_CSR = 0xc02;

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

    /// Reusable execution context used by the custom micro-bytecode generic operation path.
    private static final ThreadLocal<ReusableInstructionSemantics> MICRO_EXECUTOR =
            ThreadLocal.withInitial(ReusableInstructionSemantics::new);

    /// The guest address of this instruction.
    protected long address;

    /// The original 16-bit or 32-bit instruction bits.
    protected int raw;

    /// The instruction length in bytes.
    protected int length;

    /// The sequential guest address following this instruction.
    protected long nextAddress;

    /// The decoded operation.
    protected RiscVOperation operation;

    /// The destination register index, or zero when unused.
    protected int rd;

    /// The first source register index, or zero when unused.
    protected int rs1;

    /// The second source register index, or zero when unused.
    protected int rs2;

    /// The decoded immediate or operation-specific small integer.
    protected long immediate;

    /// Whether this instruction ends the current basic block.
    protected boolean terminator;

    /// Creates a decoded instruction semantic helper.
    protected RiscVInstructionSemantics(
            long address,
            int raw,
            int length,
            RiscVOperation operation,
            int rd,
            int rs1,
            int rs2,
            long immediate,
            boolean terminator) {
        this.address = address;
        this.raw = raw;
        this.length = length;
        this.nextAddress = address + length;
        this.operation = operation;
        this.rd = rd;
        this.rs1 = rs1;
        this.rs2 = rs2;
        this.immediate = immediate;
        this.terminator = terminator;
    }

    /// Creates a decoded instruction semantic helper using the specialized class for the operation group.
    public static RiscVInstructionSemantics create(
            long address,
            int raw,
            int length,
            RiscVOperation operation,
            int rd,
            int rs1,
            int rs2,
            long immediate,
            boolean terminator) {
        return switch (operation) {
            case NOP, FENCE, FENCE_I ->
                    new AdvancePcInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case LUI -> new LuiInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case AUIPC -> new AuipcInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case JAL -> new JalInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case JALR -> new JalrInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case BEQ -> new BeqInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case BNE -> new BneInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case BLT -> new BltInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case BGE -> new BgeInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case BLTU -> new BltuInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case BGEU -> new BgeuInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case ECALL -> new EcallInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case EBREAK -> new EbreakInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case CSRRW, CSRRS, CSRRC, CSRRWI, CSRRSI, CSRRCI ->
                    new ControlInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case LB -> new LbInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case LH -> new LhInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case LW -> new LwInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case LD -> new LdInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case LBU -> new LbuInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case LHU -> new LhuInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case LWU -> new LwuInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case FLW, FLD ->
                    new FloatingPointLoadInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SB -> new SbInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SH -> new ShInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SW -> new SwInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SD -> new SdInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case FSW, FSD ->
                    new FloatingPointStoreInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case ADDI -> new AddiInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case XORI -> new XoriInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case ORI -> new OriInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case ANDI -> new AndiInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SLLI -> new SlliInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SRLI -> new SrliInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SRAI -> new SraiInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case ADDIW -> new AddiwInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SLTI -> new SltiInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SLTIU -> new SltiuInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SLLIW -> new SlliwInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SRLIW -> new SrliwInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SRAIW -> new SraiwInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case ADD -> new AddInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SUB -> new SubInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case XOR -> new XorInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case OR -> new OrInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case AND -> new AndInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SLL -> new SllInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SRL -> new SrlInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SRA -> new SraInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case ADDW -> new AddwInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SUBW -> new SubwInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SLT -> new SltInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SLTU -> new SltuInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SLLW -> new SllwInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SRLW -> new SrlwInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case SRAW -> new SrawInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case MUL -> new MulInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case MULW -> new MulwInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case MULH, MULHSU, MULHU, DIV, DIVU, REM, REMU, DIVW, DIVUW, REMW, REMUW ->
                    new MultiplyDivideInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case FMADD, FMSUB, FNMSUB, FNMADD, FADD, FSUB, FMUL, FDIV, FSQRT, FSGNJ, FSGNJN, FSGNJX,
                    FMIN, FMAX, FCVT_S_D, FCVT_D_S, FEQ, FLT, FLE, FCLASS, FCVT_INT_FP, FCVT_FP_INT,
                    FMV_X_FP, FMV_FP_X ->
                    new FloatingPointInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
            case LR_W, LR_D, SC_W, SC_D, AMOSWAP_W, AMOADD_W, AMOXOR_W, AMOAND_W, AMOOR_W, AMOMIN_W,
                    AMOMAX_W, AMOMINU_W, AMOMAXU_W, AMOSWAP_D, AMOADD_D, AMOXOR_D, AMOAND_D, AMOOR_D,
                    AMOMIN_D, AMOMAX_D, AMOMINU_D, AMOMAXU_D ->
                    new AtomicInstructionSemantics(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        };
    }

    /// Creates a semantic helper for an immutable decoded instruction.
    public static RiscVInstructionSemantics create(DecodedInstruction instruction) {
        return create(
                instruction.address(),
                instruction.raw(),
                instruction.length(),
                instruction.operation(),
                instruction.rd(),
                instruction.rs1(),
                instruction.rs2(),
                instruction.immediate(),
                instruction.terminator());
    }

    /// Executes an immutable decoded instruction through a temporary semantic helper.
    public static void execute(MachineState state, DecodedInstruction instruction) {
        create(instruction).execute(state);
    }

    /// Executes decoded metadata supplied by the custom micro-bytecode interpreter.
    public static void executeMicro(
            MachineState state,
            RiscVOperation operation,
            long address,
            int raw,
            long nextPc,
            int rd,
            int rs1,
            int rs2,
            long immediate) {
        ReusableInstructionSemantics executor = MICRO_EXECUTOR.get();
        executor.reset(address, raw, (int) (nextPc - address), operation, rd, rs1, rs2, immediate);
        executor.execute(state);
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
    public final void execute(MachineState state) {
        state.setPc(address);
        state.beforeInstruction(address, raw);
        executeInstruction(state, nextAddress);
        if (!terminator && !writesProgramCounterInBody()) {
            state.setPc(nextAddress);
        }
    }


    /// Executes the operation-specific instruction body.
    protected abstract void executeInstruction(MachineState state, long nextPc);


    /// Handles store side effects only when the loaded image exposes side-effect addresses.
    private static void afterStore(MachineState state, long address, int length) {
        if (state.hasStoreSideEffects()) {
            state.afterStoreWithSideEffects(address, length);
        }
    }

    /// Returns true when the instruction body writes `pc` itself.
    protected boolean writesProgramCounterInBody() {
        return true;
    }


    /// Reuses one helper object when generic micro-bytecode operations need complex shared semantics.
    private static final class ReusableInstructionSemantics extends RiscVInstructionSemantics {
        /// Creates an empty reusable instruction executor.
        private ReusableInstructionSemantics() {
            super(0, 0, Integer.BYTES, RiscVOperation.NOP, 0, 0, 0, 0, false);
        }

        /// Replaces the decoded metadata before executing one instruction.
        private void reset(
                long address,
                int raw,
                int length,
                RiscVOperation operation,
                int rd,
                int rs1,
                int rs2,
                long immediate) {
            this.address = address;
            this.raw = raw;
            this.length = length;
            this.nextAddress = address + length;
            this.operation = operation;
            this.rd = rd;
            this.rs1 = rs1;
            this.rs2 = rs2;
            this.immediate = immediate;
            this.terminator = false;
        }

        /// Executes the current decoded operation through the existing shared semantic groups.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            Memory memory = state.memory();
            switch (operation) {
                case NOP, FENCE, FENCE_I, LUI, AUIPC, JAL, JALR, BEQ, BNE, BLT, BGE, BLTU, BGEU,
                        CSRRW, CSRRS, CSRRC, CSRRWI, CSRRSI, CSRRCI, ECALL, EBREAK -> executeControl(state, nextPc);
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
    }

    /// Base class for decoded instructions with direct operation bodies.
    private abstract static sealed class DirectInstructionSemantics extends RiscVInstructionSemantics {
        /// Creates a direct decoded instruction semantic helper.
        private DirectInstructionSemantics(
                long address,
                int raw,
                int length,
                RiscVOperation operation,
                int rd,
                int rs1,
                int rs2,
                long immediate,
                boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Returns true for control-transfer bodies that still materialize `pc`.
        @Override
        protected boolean writesProgramCounterInBody() {
            return terminator;
        }
    }

    /// Advances the program counter for no-op control instructions.
    private static final class AdvancePcInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded no-op instruction semantic helper.
        private AdvancePcInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Advances the program counter.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
        }

    }

    /// Executes `lui`.
    private static final class LuiInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `lui` instruction semantic helper.
        private LuiInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Writes the upper immediate.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, immediate);
        }

    }

    /// Executes `auipc`.
    private static final class AuipcInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `auipc` instruction semantic helper.
        private AuipcInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Writes the PC-relative upper immediate.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, address + immediate);
        }

    }

    /// Executes `jal`.
    private static final class JalInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `jal` instruction semantic helper.
        private JalInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Writes the link register and jumps to the PC-relative target.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, nextPc);
            state.setPc(address + immediate);
        }


    }

    /// Executes `jalr`.
    private static final class JalrInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `jalr` instruction semantic helper.
        private JalrInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Writes the link register and jumps to the register-relative target.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            long target = (state.decodedRegister(rs1) + immediate) & ~1L;
            state.setDecodedRegister(rd, nextPc);
            state.setPc(target);
        }


    }

    /// Executes `beq`.
    private static final class BeqInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `beq` instruction semantic helper.
        private BeqInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Branches when both source registers are equal.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setPc(state.decodedRegister(rs1) == state.decodedRegister(rs2) ? address + immediate : nextPc);
        }


    }

    /// Executes `bne`.
    private static final class BneInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `bne` instruction semantic helper.
        private BneInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Branches when both source registers differ.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setPc(state.decodedRegister(rs1) != state.decodedRegister(rs2) ? address + immediate : nextPc);
        }


    }

    /// Executes `blt`.
    private static final class BltInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `blt` instruction semantic helper.
        private BltInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Branches when the first source register is signed-less-than the second.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setPc(state.decodedRegister(rs1) < state.decodedRegister(rs2) ? address + immediate : nextPc);
        }


    }

    /// Executes `bge`.
    private static final class BgeInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `bge` instruction semantic helper.
        private BgeInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Branches when the first source register is signed-greater-or-equal to the second.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setPc(state.decodedRegister(rs1) >= state.decodedRegister(rs2) ? address + immediate : nextPc);
        }


    }

    /// Executes `bltu`.
    private static final class BltuInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `bltu` instruction semantic helper.
        private BltuInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Branches when the first source register is unsigned-less-than the second.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setPc(Long.compareUnsigned(state.decodedRegister(rs1), state.decodedRegister(rs2)) < 0 ? address + immediate : nextPc);
        }


    }

    /// Executes `bgeu`.
    private static final class BgeuInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `bgeu` instruction semantic helper.
        private BgeuInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Branches when the first source register is unsigned-greater-or-equal to the second.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setPc(Long.compareUnsigned(state.decodedRegister(rs1), state.decodedRegister(rs2)) >= 0 ? address + immediate : nextPc);
        }


    }

    /// Executes `ecall`.
    private static final class EcallInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `ecall` instruction semantic helper.
        private EcallInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Dispatches the environment call through the syscall handler.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.syscalls().handle(state, address);
            state.setPc(nextPc);
        }

    }

    /// Executes `ebreak`.
    private static final class EbreakInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `ebreak` instruction semantic helper.
        private EbreakInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Terminates the program with a zero exit status.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            throw new ProgramExitException(0);
        }

    }

    /// Executes `addi`.
    private static final class AddiInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `addi` instruction semantic helper.
        private AddiInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Adds a sign-extended immediate to a register.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) + immediate);
        }

    }

    /// Executes `xori`.
    private static final class XoriInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `xori` instruction semantic helper.
        private XoriInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Xors a register with a sign-extended immediate.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) ^ immediate);
        }

    }

    /// Executes `ori`.
    private static final class OriInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `ori` instruction semantic helper.
        private OriInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Ors a register with a sign-extended immediate.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) | immediate);
        }

    }

    /// Executes `andi`.
    private static final class AndiInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `andi` instruction semantic helper.
        private AndiInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Ands a register with a sign-extended immediate.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) & immediate);
        }

    }

    /// Executes `slli`.
    private static final class SlliInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `slli` instruction semantic helper.
        private SlliInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Shifts a register left by the decoded immediate.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) << immediate);
        }

    }

    /// Executes `srli`.
    private static final class SrliInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `srli` instruction semantic helper.
        private SrliInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Shifts a register right logically by the decoded immediate.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) >>> immediate);
        }

    }

    /// Executes `srai`.
    private static final class SraiInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `srai` instruction semantic helper.
        private SraiInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Shifts a register right arithmetically by the decoded immediate.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) >> immediate);
        }

    }

    /// Executes `addiw`.
    private static final class AddiwInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `addiw` instruction semantic helper.
        private AddiwInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Adds an immediate in word width and sign-extends the result.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, (int) (state.decodedRegister(rs1) + immediate));
        }

    }

    /// Executes `slti`.
    private static final class SltiInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `slti` instruction semantic helper.
        private SltiInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Sets the destination when the source register is signed-less-than the immediate.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) < immediate ? 1 : 0);
        }

    }

    /// Executes `sltiu`.
    private static final class SltiuInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `sltiu` instruction semantic helper.
        private SltiuInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Sets the destination when the source register is unsigned-less-than the immediate.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, Long.compareUnsigned(state.decodedRegister(rs1), immediate) < 0 ? 1 : 0);
        }

    }

    /// Executes `slliw`.
    private static final class SlliwInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `slliw` instruction semantic helper.
        private SlliwInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Shifts a word left by the immediate and sign-extends the result.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) << immediate);
        }

    }

    /// Executes `srliw`.
    private static final class SrliwInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `srliw` instruction semantic helper.
        private SrliwInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Shifts a word right logically by the immediate and sign-extends the result.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) >>> immediate);
        }

    }

    /// Executes `sraiw`.
    private static final class SraiwInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `sraiw` instruction semantic helper.
        private SraiwInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Shifts a word right arithmetically by the immediate and sign-extends the result.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) >> immediate);
        }

    }

    /// Executes `add`.
    private static final class AddInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `add` instruction semantic helper.
        private AddInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Adds two source registers.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) + state.decodedRegister(rs2));
        }

    }

    /// Executes `sub`.
    private static final class SubInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `sub` instruction semantic helper.
        private SubInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Subtracts the second source register from the first.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) - state.decodedRegister(rs2));
        }

    }

    /// Executes `xor`.
    private static final class XorInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `xor` instruction semantic helper.
        private XorInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Xors two source registers.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) ^ state.decodedRegister(rs2));
        }

    }

    /// Executes `or`.
    private static final class OrInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `or` instruction semantic helper.
        private OrInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Ors two source registers.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) | state.decodedRegister(rs2));
        }

    }

    /// Executes `and`.
    private static final class AndInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `and` instruction semantic helper.
        private AndInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Ands two source registers.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) & state.decodedRegister(rs2));
        }

    }

    /// Executes `sll`.
    private static final class SllInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `sll` instruction semantic helper.
        private SllInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Shifts the first source register left by the masked second source register.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) << (state.decodedRegister(rs2) & 0x3f));
        }

    }

    /// Executes `srl`.
    private static final class SrlInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `srl` instruction semantic helper.
        private SrlInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Shifts the first source register right logically by the masked second source register.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) >>> (state.decodedRegister(rs2) & 0x3f));
        }

    }

    /// Executes `sra`.
    private static final class SraInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `sra` instruction semantic helper.
        private SraInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Shifts the first source register right arithmetically by the masked second source register.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) >> (state.decodedRegister(rs2) & 0x3f));
        }

    }

    /// Executes `addw`.
    private static final class AddwInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `addw` instruction semantic helper.
        private AddwInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Adds two source registers in word width and sign-extends the result.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) + (int) state.decodedRegister(rs2));
        }

    }

    /// Executes `subw`.
    private static final class SubwInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `subw` instruction semantic helper.
        private SubwInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Subtracts two source registers in word width and sign-extends the result.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) - (int) state.decodedRegister(rs2));
        }

    }

    /// Executes `slt`.
    private static final class SltInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `slt` instruction semantic helper.
        private SltInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Sets the destination when the first source register is signed-less-than the second.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) < state.decodedRegister(rs2) ? 1 : 0);
        }

    }

    /// Executes `sltu`.
    private static final class SltuInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `sltu` instruction semantic helper.
        private SltuInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Sets the destination when the first source register is unsigned-less-than the second.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, Long.compareUnsigned(state.decodedRegister(rs1), state.decodedRegister(rs2)) < 0 ? 1 : 0);
        }

    }

    /// Executes `sllw`.
    private static final class SllwInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `sllw` instruction semantic helper.
        private SllwInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Shifts a word left by the masked second source register and sign-extends the result.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) << (state.decodedRegister(rs2) & 0x1f));
        }

    }

    /// Executes `srlw`.
    private static final class SrlwInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `srlw` instruction semantic helper.
        private SrlwInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Shifts a word right logically by the masked second source register and sign-extends the result.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) >>> (state.decodedRegister(rs2) & 0x1f));
        }

    }

    /// Executes `sraw`.
    private static final class SrawInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `sraw` instruction semantic helper.
        private SrawInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Shifts a word right arithmetically by the masked second source register and sign-extends the result.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) >> (state.decodedRegister(rs2) & 0x1f));
        }

    }

    /// Executes `mul`.
    private static final class MulInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `mul` instruction semantic helper.
        private MulInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Multiplies two source registers and keeps the low 64 bits.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.decodedRegister(rs1) * state.decodedRegister(rs2));
        }

    }

    /// Executes `mulw`.
    private static final class MulwInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `mulw` instruction semantic helper.
        private MulwInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Multiplies two source registers in word width and sign-extends the result.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, (int) state.decodedRegister(rs1) * (int) state.decodedRegister(rs2));
        }

    }

    /// Executes `lb`.
    private static final class LbInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `lb` instruction semantic helper.
        private LbInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Loads a sign-extended byte.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.memory().readByte(state.decodedRegister(rs1) + immediate));
        }

    }

    /// Executes `lh`.
    private static final class LhInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `lh` instruction semantic helper.
        private LhInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Loads a sign-extended halfword.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.memory().readShort(state.decodedRegister(rs1) + immediate));
        }

    }

    /// Executes `lw`.
    private static final class LwInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `lw` instruction semantic helper.
        private LwInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Loads a sign-extended word.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.memory().readInt(state.decodedRegister(rs1) + immediate));
        }

    }

    /// Executes `ld`.
    private static final class LdInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `ld` instruction semantic helper.
        private LdInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Loads a doubleword.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.memory().readLong(state.decodedRegister(rs1) + immediate));
        }

    }

    /// Executes `lbu`.
    private static final class LbuInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `lbu` instruction semantic helper.
        private LbuInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Loads a zero-extended byte.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.memory().readUnsignedByte(state.decodedRegister(rs1) + immediate));
        }

    }

    /// Executes `lhu`.
    private static final class LhuInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `lhu` instruction semantic helper.
        private LhuInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Loads a zero-extended halfword.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.memory().readUnsignedShort(state.decodedRegister(rs1) + immediate));
        }

    }

    /// Executes `lwu`.
    private static final class LwuInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `lwu` instruction semantic helper.
        private LwuInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Loads a zero-extended word.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            state.setDecodedRegister(rd, state.memory().readUnsignedInt(state.decodedRegister(rs1) + immediate));
        }

    }

    /// Executes `sb`.
    private static final class SbInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `sb` instruction semantic helper.
        private SbInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Stores a byte.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            long storeAddress = state.decodedRegister(rs1) + immediate;
            state.memory().writeByte(storeAddress, (byte) state.decodedRegister(rs2));
            afterStore(state, storeAddress, Byte.BYTES);
            state.clearReservation();
        }

    }

    /// Executes `sh`.
    private static final class ShInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `sh` instruction semantic helper.
        private ShInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Stores a halfword.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            long storeAddress = state.decodedRegister(rs1) + immediate;
            state.memory().writeShort(storeAddress, (short) state.decodedRegister(rs2));
            afterStore(state, storeAddress, Short.BYTES);
            state.clearReservation();
        }

    }

    /// Executes `sw`.
    private static final class SwInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `sw` instruction semantic helper.
        private SwInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Stores a word.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            long storeAddress = state.decodedRegister(rs1) + immediate;
            state.memory().writeInt(storeAddress, (int) state.decodedRegister(rs2));
            afterStore(state, storeAddress, Integer.BYTES);
            state.clearReservation();
        }

    }

    /// Executes `sd`.
    private static final class SdInstructionSemantics extends DirectInstructionSemantics {
        /// Creates a decoded `sd` instruction semantic helper.
        private SdInstructionSemantics(long address, int raw, int length, RiscVOperation operation, int rd, int rs1, int rs2, long immediate, boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Stores a doubleword.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            long storeAddress = state.decodedRegister(rs1) + immediate;
            state.memory().writeLong(storeAddress, state.decodedRegister(rs2));
            afterStore(state, storeAddress, Long.BYTES);
            state.clearReservation();
        }

    }

    /// Executes control-flow and system operations as a specialized instruction semantic helper.
    private static final class ControlInstructionSemantics extends RiscVInstructionSemantics {
        /// Creates a decoded control-flow or system instruction semantic helper.
        private ControlInstructionSemantics(
                long address,
                int raw,
                int length,
                RiscVOperation operation,
                int rd,
                int rs1,
                int rs2,
                long immediate,
                boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Executes the decoded control-flow or system instruction.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            executeControl(state, nextPc);
        }

    }

    /// Executes integer load operations as a specialized instruction semantic helper.
    private static final class LoadInstructionSemantics extends RiscVInstructionSemantics {
        /// Creates a decoded integer load instruction semantic helper.
        private LoadInstructionSemantics(
                long address,
                int raw,
                int length,
                RiscVOperation operation,
                int rd,
                int rs1,
                int rs2,
                long immediate,
                boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Executes the decoded integer load instruction.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            executeLoad(state, state.memory(), nextPc);
        }

    }

    /// Executes floating-point load operations as a specialized instruction semantic helper.
    private static final class FloatingPointLoadInstructionSemantics extends RiscVInstructionSemantics {
        /// Creates a decoded floating-point load instruction semantic helper.
        private FloatingPointLoadInstructionSemantics(
                long address,
                int raw,
                int length,
                RiscVOperation operation,
                int rd,
                int rs1,
                int rs2,
                long immediate,
                boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Executes the decoded floating-point load instruction.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            executeFloatingPointLoad(state, state.memory(), nextPc);
        }

    }

    /// Executes integer store operations as a specialized instruction semantic helper.
    private static final class StoreInstructionSemantics extends RiscVInstructionSemantics {
        /// Creates a decoded integer store instruction semantic helper.
        private StoreInstructionSemantics(
                long address,
                int raw,
                int length,
                RiscVOperation operation,
                int rd,
                int rs1,
                int rs2,
                long immediate,
                boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Executes the decoded integer store instruction.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            executeStore(state, state.memory(), nextPc);
        }

    }

    /// Executes floating-point store operations as a specialized instruction semantic helper.
    private static final class FloatingPointStoreInstructionSemantics extends RiscVInstructionSemantics {
        /// Creates a decoded floating-point store instruction semantic helper.
        private FloatingPointStoreInstructionSemantics(
                long address,
                int raw,
                int length,
                RiscVOperation operation,
                int rd,
                int rs1,
                int rs2,
                long immediate,
                boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Executes the decoded floating-point store instruction.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            executeFloatingPointStore(state, state.memory(), nextPc);
        }

    }

    /// Executes register-immediate integer operations as a specialized instruction semantic helper.
    private static final class ImmediateIntegerInstructionSemantics extends RiscVInstructionSemantics {
        /// Creates a decoded register-immediate integer instruction semantic helper.
        private ImmediateIntegerInstructionSemantics(
                long address,
                int raw,
                int length,
                RiscVOperation operation,
                int rd,
                int rs1,
                int rs2,
                long immediate,
                boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Executes the decoded register-immediate integer instruction.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            executeImmediateInteger(state, nextPc);
        }

    }

    /// Executes register-register integer operations as a specialized instruction semantic helper.
    private static final class RegisterIntegerInstructionSemantics extends RiscVInstructionSemantics {
        /// Creates a decoded register-register integer instruction semantic helper.
        private RegisterIntegerInstructionSemantics(
                long address,
                int raw,
                int length,
                RiscVOperation operation,
                int rd,
                int rs1,
                int rs2,
                long immediate,
                boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Executes the decoded register-register integer instruction.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            executeRegisterInteger(state, nextPc);
        }

    }

    /// Executes RV64M multiply and divide operations as a specialized instruction semantic helper.
    private static final class MultiplyDivideInstructionSemantics extends RiscVInstructionSemantics {
        /// Creates a decoded multiply or divide instruction semantic helper.
        private MultiplyDivideInstructionSemantics(
                long address,
                int raw,
                int length,
                RiscVOperation operation,
                int rd,
                int rs1,
                int rs2,
                long immediate,
                boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Executes the decoded multiply or divide instruction.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            executeMultiplyDivide(state, nextPc);
        }

    }

    /// Executes floating-point arithmetic and conversion operations as a specialized instruction semantic helper.
    private static final class FloatingPointInstructionSemantics extends RiscVInstructionSemantics {
        /// Creates a decoded floating-point arithmetic or conversion instruction semantic helper.
        private FloatingPointInstructionSemantics(
                long address,
                int raw,
                int length,
                RiscVOperation operation,
                int rd,
                int rs1,
                int rs2,
                long immediate,
                boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Executes the decoded floating-point arithmetic or conversion instruction.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            executeFloatingPointOperation(state, nextPc);
        }

    }

    /// Executes RV64A atomic memory operations as a specialized instruction semantic helper.
    private static final class AtomicInstructionSemantics extends RiscVInstructionSemantics {
        /// Creates a decoded atomic memory instruction semantic helper.
        private AtomicInstructionSemantics(
                long address,
                int raw,
                int length,
                RiscVOperation operation,
                int rd,
                int rs1,
                int rs2,
                long immediate,
                boolean terminator) {
            super(address, raw, length, operation, rd, rs1, rs2, immediate, terminator);
        }

        /// Executes the decoded atomic memory instruction.
        @Override
        protected void executeInstruction(MachineState state, long nextPc) {
            executeAtomic(state, state.memory(), nextPc);
        }

    }

    /// Executes control-flow and system operations.
    protected final void executeControl(MachineState state, long nextPc) {
        switch (operation) {
            case NOP, FENCE, FENCE_I -> state.setPc(nextPc);
            case LUI -> {
                state.setDecodedRegister(rd, immediate);
                state.setPc(nextPc);
            }
            case AUIPC -> {
                state.setDecodedRegister(rd, address + immediate);
                state.setPc(nextPc);
            }
            case JAL -> {
                state.setDecodedRegister(rd, nextPc);
                state.setPc(address + immediate);
            }
            case JALR -> {
                long target = (state.decodedRegister(rs1) + immediate) & ~1L;
                state.setDecodedRegister(rd, nextPc);
                state.setPc(target);
            }
            case BEQ -> branch(state, state.decodedRegister(rs1) == state.decodedRegister(rs2), nextPc);
            case BNE -> branch(state, state.decodedRegister(rs1) != state.decodedRegister(rs2), nextPc);
            case BLT -> branch(state, state.decodedRegister(rs1) < state.decodedRegister(rs2), nextPc);
            case BGE -> branch(state, state.decodedRegister(rs1) >= state.decodedRegister(rs2), nextPc);
            case BLTU -> branch(state, Long.compareUnsigned(state.decodedRegister(rs1), state.decodedRegister(rs2)) < 0, nextPc);
            case BGEU -> branch(state, Long.compareUnsigned(state.decodedRegister(rs1), state.decodedRegister(rs2)) >= 0, nextPc);
            case CSRRW -> writeControlStatusRegister(state, nextPc, state.decodedRegister(rs1));
            case CSRRS -> setClearControlStatusRegister(state, nextPc, state.decodedRegister(rs1), true);
            case CSRRC -> setClearControlStatusRegister(state, nextPc, state.decodedRegister(rs1), false);
            case CSRRWI -> writeControlStatusRegister(state, nextPc, rs1);
            case CSRRSI -> setClearControlStatusRegister(state, nextPc, rs1, true);
            case CSRRCI -> setClearControlStatusRegister(state, nextPc, rs1, false);
            case ECALL -> ecall(state);
            case EBREAK -> throw new ProgramExitException(0);
            default -> throw unexpectedOperationGroup("control");
        }
    }


    /// Executes load operations.
    protected final void executeLoad(MachineState state, Memory memory, long nextPc) {
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
    protected final void executeFloatingPointLoad(MachineState state, Memory memory, long nextPc) {
        switch (operation) {
            case FLW -> loadFloatWord(state, memory, nextPc);
            case FLD -> loadFloatDouble(state, memory, nextPc);
            default -> throw unexpectedOperationGroup("floating-point load");
        }
    }


    /// Executes store operations.
    protected final void executeStore(MachineState state, Memory memory, long nextPc) {
        switch (operation) {
            case SB -> storeByte(state, memory, nextPc);
            case SH -> storeShort(state, memory, nextPc);
            case SW -> storeInt(state, memory, nextPc);
            case SD -> storeLong(state, memory, nextPc);
            default -> throw unexpectedOperationGroup("store");
        }
    }


    /// Executes floating-point store operations.
    protected final void executeFloatingPointStore(MachineState state, Memory memory, long nextPc) {
        switch (operation) {
            case FSW -> storeFloatWord(state, memory, nextPc);
            case FSD -> storeFloatDouble(state, memory, nextPc);
            default -> throw unexpectedOperationGroup("floating-point store");
        }
    }


    /// Executes integer register-immediate operations.
    protected final void executeImmediateInteger(MachineState state, long nextPc) {
        switch (operation) {
            case ADDI -> binaryImmediate(state, nextPc, state.decodedRegister(rs1) + immediate);
            case SLTI -> binaryImmediate(state, nextPc, state.decodedRegister(rs1) < immediate ? 1 : 0);
            case SLTIU -> binaryImmediate(state, nextPc, Long.compareUnsigned(state.decodedRegister(rs1), immediate) < 0 ? 1 : 0);
            case XORI -> binaryImmediate(state, nextPc, state.decodedRegister(rs1) ^ immediate);
            case ORI -> binaryImmediate(state, nextPc, state.decodedRegister(rs1) | immediate);
            case ANDI -> binaryImmediate(state, nextPc, state.decodedRegister(rs1) & immediate);
            case SLLI -> binaryImmediate(state, nextPc, state.decodedRegister(rs1) << immediate);
            case SRLI -> binaryImmediate(state, nextPc, state.decodedRegister(rs1) >>> immediate);
            case SRAI -> binaryImmediate(state, nextPc, state.decodedRegister(rs1) >> immediate);
            case ADDIW -> wordImmediate(state, nextPc, (int) (state.decodedRegister(rs1) + immediate));
            case SLLIW -> wordImmediate(state, nextPc, (int) state.decodedRegister(rs1) << immediate);
            case SRLIW -> wordImmediate(state, nextPc, (int) state.decodedRegister(rs1) >>> immediate);
            case SRAIW -> wordImmediate(state, nextPc, (int) state.decodedRegister(rs1) >> immediate);
            default -> throw unexpectedOperationGroup("immediate integer");
        }
    }


    /// Executes integer register-register operations.
    protected final void executeRegisterInteger(MachineState state, long nextPc) {
        switch (operation) {
            case ADD -> binaryRegister(state, nextPc, state.decodedRegister(rs1) + state.decodedRegister(rs2));
            case SUB -> binaryRegister(state, nextPc, state.decodedRegister(rs1) - state.decodedRegister(rs2));
            case SLL -> binaryRegister(state, nextPc, state.decodedRegister(rs1) << (state.decodedRegister(rs2) & 0x3f));
            case SLT -> binaryRegister(state, nextPc, state.decodedRegister(rs1) < state.decodedRegister(rs2) ? 1 : 0);
            case SLTU -> binaryRegister(state, nextPc, Long.compareUnsigned(state.decodedRegister(rs1), state.decodedRegister(rs2)) < 0 ? 1 : 0);
            case XOR -> binaryRegister(state, nextPc, state.decodedRegister(rs1) ^ state.decodedRegister(rs2));
            case SRL -> binaryRegister(state, nextPc, state.decodedRegister(rs1) >>> (state.decodedRegister(rs2) & 0x3f));
            case SRA -> binaryRegister(state, nextPc, state.decodedRegister(rs1) >> (state.decodedRegister(rs2) & 0x3f));
            case OR -> binaryRegister(state, nextPc, state.decodedRegister(rs1) | state.decodedRegister(rs2));
            case AND -> binaryRegister(state, nextPc, state.decodedRegister(rs1) & state.decodedRegister(rs2));
            case ADDW -> wordRegister(state, nextPc, (int) state.decodedRegister(rs1) + (int) state.decodedRegister(rs2));
            case SUBW -> wordRegister(state, nextPc, (int) state.decodedRegister(rs1) - (int) state.decodedRegister(rs2));
            case SLLW -> wordRegister(state, nextPc, (int) state.decodedRegister(rs1) << (state.decodedRegister(rs2) & 0x1f));
            case SRLW -> wordRegister(state, nextPc, (int) state.decodedRegister(rs1) >>> (state.decodedRegister(rs2) & 0x1f));
            case SRAW -> wordRegister(state, nextPc, (int) state.decodedRegister(rs1) >> (state.decodedRegister(rs2) & 0x1f));
            default -> throw unexpectedOperationGroup("register integer");
        }
    }


    /// Executes RV64M multiply and divide operations.
    protected final void executeMultiplyDivide(MachineState state, long nextPc) {
        switch (operation) {
            case MUL -> binaryRegister(state, nextPc, state.decodedRegister(rs1) * state.decodedRegister(rs2));
            case MULH -> binaryRegister(state, nextPc, Math.multiplyHigh(state.decodedRegister(rs1), state.decodedRegister(rs2)));
            case MULHSU -> binaryRegister(state, nextPc, multiplyHighSignedUnsigned(state.decodedRegister(rs1), state.decodedRegister(rs2)));
            case MULHU -> binaryRegister(state, nextPc, Math.unsignedMultiplyHigh(state.decodedRegister(rs1), state.decodedRegister(rs2)));
            case DIV -> binaryRegister(state, nextPc, divideSigned(state.decodedRegister(rs1), state.decodedRegister(rs2)));
            case DIVU -> binaryRegister(state, nextPc, divideUnsigned(state.decodedRegister(rs1), state.decodedRegister(rs2)));
            case REM -> binaryRegister(state, nextPc, remainderSigned(state.decodedRegister(rs1), state.decodedRegister(rs2)));
            case REMU -> binaryRegister(state, nextPc, remainderUnsigned(state.decodedRegister(rs1), state.decodedRegister(rs2)));
            case MULW -> wordRegister(state, nextPc, (int) state.decodedRegister(rs1) * (int) state.decodedRegister(rs2));
            case DIVW -> wordRegister(state, nextPc, divideSignedWord((int) state.decodedRegister(rs1), (int) state.decodedRegister(rs2)));
            case DIVUW -> wordRegister(state, nextPc, divideUnsignedWord((int) state.decodedRegister(rs1), (int) state.decodedRegister(rs2)));
            case REMW -> wordRegister(state, nextPc, remainderSignedWord((int) state.decodedRegister(rs1), (int) state.decodedRegister(rs2)));
            case REMUW -> wordRegister(state, nextPc, remainderUnsignedWord((int) state.decodedRegister(rs1), (int) state.decodedRegister(rs2)));
            default -> throw unexpectedOperationGroup("multiply/divide");
        }
    }


    /// Executes floating-point arithmetic, conversion, move, compare, and classify operations.
    protected final void executeFloatingPointOperation(MachineState state, long nextPc) {
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
                int roundingMode = effectiveRoundingMode(state);
                long bits = state.decodedFloatingPointRegister(rs1);
                if (isSignalingDoubleNaN(bits)) {
                    state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
                }
                double value = Double.longBitsToDouble(bits);
                writeSingleBits(state, rd, canonicalizeSingleBits(roundSingleResult(state, value, (float) value, roundingMode)));
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
                state.setDecodedRegister(rd, floatingPointFormat() == SINGLE_FLOAT_FORMAT
                        ? classifySingle(readSingleBits(state, rs1))
                        : classifyDouble(state.decodedFloatingPointRegister(rs1)));
                state.setPc(nextPc);
            }
            case FCVT_INT_FP -> convertFloatingPointToInteger(state, nextPc);
            case FCVT_FP_INT -> convertIntegerToFloatingPoint(state, nextPc);
            case FMV_X_FP -> {
                state.setDecodedRegister(rd, floatingPointFormat() == SINGLE_FLOAT_FORMAT
                        ? (int) readSingleBits(state, rs1)
                        : state.decodedFloatingPointRegister(rs1));
                state.setPc(nextPc);
            }
            case FMV_FP_X -> {
                if (floatingPointFormat() == SINGLE_FLOAT_FORMAT) {
                    writeSingleBits(state, rd, (int) state.decodedRegister(rs1));
                } else {
                    writeDoubleBits(state, rd, state.decodedRegister(rs1));
                }
                state.setPc(nextPc);
            }
            default -> throw unexpectedOperationGroup("floating point");
        }
    }


    /// Executes RV64A atomic operations.
    protected final void executeAtomic(MachineState state, Memory memory, long nextPc) {
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
        state.setDecodedRegister(rd, oldValue);
        state.setPc(nextPc);
    }


    /// Sets or clears CSR bits using the supplied mask value.
    private void setClearControlStatusRegister(MachineState state, long nextPc, long mask, boolean setBits) {
        long oldValue = state.readControlStatusRegister((int) immediate);
        if (mask != 0) {
            state.writeControlStatusRegister((int) immediate, setBits ? oldValue | mask : oldValue & ~mask);
        }
        state.setDecodedRegister(rd, oldValue);
        state.setPc(nextPc);
    }


    /// Writes an immediate arithmetic result and advances the program counter.
    private void binaryImmediate(MachineState state, long nextPc, long value) {
        state.setDecodedRegister(rd, value);
        state.setPc(nextPc);
    }

    /// Writes a sign-extended 32-bit immediate arithmetic result and advances the program counter.
    private void wordImmediate(MachineState state, long nextPc, int value) {
        state.setDecodedRegister(rd, value);
        state.setPc(nextPc);
    }

    /// Writes a register arithmetic result and advances the program counter.
    private void binaryRegister(MachineState state, long nextPc, long value) {
        state.setDecodedRegister(rd, value);
        state.setPc(nextPc);
    }

    /// Writes a sign-extended 32-bit register arithmetic result and advances the program counter.
    private void wordRegister(MachineState state, long nextPc, int value) {
        state.setDecodedRegister(rd, value);
        state.setPc(nextPc);
    }

    /// Loads a sign-extended byte value.
    private void loadByte(MachineState state, Memory memory, long nextPc) {
        state.setDecodedRegister(rd, memory.readByte(state.decodedRegister(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a sign-extended 16-bit value.
    private void loadShort(MachineState state, Memory memory, long nextPc) {
        state.setDecodedRegister(rd, memory.readShort(state.decodedRegister(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a sign-extended 32-bit value.
    private void loadInt(MachineState state, Memory memory, long nextPc) {
        state.setDecodedRegister(rd, memory.readInt(state.decodedRegister(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a 64-bit value.
    private void loadLong(MachineState state, Memory memory, long nextPc) {
        state.setDecodedRegister(rd, memory.readLong(state.decodedRegister(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a zero-extended byte value.
    private void loadUnsignedByte(MachineState state, Memory memory, long nextPc) {
        state.setDecodedRegister(rd, memory.readUnsignedByte(state.decodedRegister(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a zero-extended 16-bit value.
    private void loadUnsignedShort(MachineState state, Memory memory, long nextPc) {
        state.setDecodedRegister(rd, memory.readUnsignedShort(state.decodedRegister(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a zero-extended 32-bit value.
    private void loadUnsignedInt(MachineState state, Memory memory, long nextPc) {
        state.setDecodedRegister(rd, memory.readUnsignedInt(state.decodedRegister(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a 32-bit floating-point value and NaN-boxes it in a 64-bit FP register.
    private void loadFloatWord(MachineState state, Memory memory, long nextPc) {
        state.setDecodedFloatingPointRegister(rd, 0xffff_ffff_0000_0000L | memory.readUnsignedInt(state.decodedRegister(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Loads a 64-bit floating-point value as raw bits.
    private void loadFloatDouble(MachineState state, Memory memory, long nextPc) {
        state.setDecodedFloatingPointRegister(rd, memory.readLong(state.decodedRegister(rs1) + immediate));
        state.setPc(nextPc);
    }

    /// Stores a byte value.
    private void storeByte(MachineState state, Memory memory, long nextPc) {
        long address = state.decodedRegister(rs1) + immediate;
        memory.writeByte(address, (byte) state.decodedRegister(rs2));
        afterStore(state, address, Byte.BYTES);
        state.clearReservation();
        state.setPc(nextPc);
    }


    /// Stores a 16-bit value.
    private void storeShort(MachineState state, Memory memory, long nextPc) {
        long address = state.decodedRegister(rs1) + immediate;
        memory.writeShort(address, (short) state.decodedRegister(rs2));
        afterStore(state, address, Short.BYTES);
        state.clearReservation();
        state.setPc(nextPc);
    }


    /// Stores a 32-bit value.
    private void storeInt(MachineState state, Memory memory, long nextPc) {
        long address = state.decodedRegister(rs1) + immediate;
        memory.writeInt(address, (int) state.decodedRegister(rs2));
        afterStore(state, address, Integer.BYTES);
        state.clearReservation();
        state.setPc(nextPc);
    }


    /// Stores a 64-bit value.
    private void storeLong(MachineState state, Memory memory, long nextPc) {
        long address = state.decodedRegister(rs1) + immediate;
        memory.writeLong(address, state.decodedRegister(rs2));
        afterStore(state, address, Long.BYTES);
        state.clearReservation();
        state.setPc(nextPc);
    }


    /// Stores the low 32 bits of a floating-point register.
    private void storeFloatWord(MachineState state, Memory memory, long nextPc) {
        long address = state.decodedRegister(rs1) + immediate;
        memory.writeInt(address, (int) state.decodedFloatingPointRegister(rs2));
        afterStore(state, address, Integer.BYTES);
        state.clearReservation();
        state.setPc(nextPc);
    }


    /// Stores a 64-bit floating-point register as raw bits.
    private void storeFloatDouble(MachineState state, Memory memory, long nextPc) {
        long address = state.decodedRegister(rs1) + immediate;
        memory.writeLong(address, state.decodedFloatingPointRegister(rs2));
        afterStore(state, address, Long.BYTES);
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
        synchronized (memory) {
            long address = state.decodedRegister(rs1);
            state.setDecodedRegister(rd, memory.readInt(address));
            state.reserve(address);
        }
        state.setPc(nextPc);
    }


    /// Loads and reserves a 64-bit memory doubleword.
    private void lrDouble(MachineState state, Memory memory, long nextPc) {
        synchronized (memory) {
            long address = state.decodedRegister(rs1);
            state.setDecodedRegister(rd, memory.readLong(address));
            state.reserve(address);
        }
        state.setPc(nextPc);
    }


    /// Conditionally stores a 32-bit memory word through an LR/SC reservation.
    private void scWord(MachineState state, Memory memory, long nextPc) {
        synchronized (memory) {
            long address = state.decodedRegister(rs1);
            if (state.hasReservation(address)) {
                memory.writeInt(address, (int) state.decodedRegister(rs2));
                afterStore(state, address, Integer.BYTES);
                state.setDecodedRegister(rd, 0);
            } else {
                state.setDecodedRegister(rd, 1);
            }
            state.clearReservation();
        }
        state.setPc(nextPc);
    }


    /// Conditionally stores a 64-bit memory doubleword through an LR/SC reservation.
    private void scDouble(MachineState state, Memory memory, long nextPc) {
        synchronized (memory) {
            long address = state.decodedRegister(rs1);
            if (state.hasReservation(address)) {
                memory.writeLong(address, state.decodedRegister(rs2));
                afterStore(state, address, Long.BYTES);
                state.setDecodedRegister(rd, 0);
            } else {
                state.setDecodedRegister(rd, 1);
            }
            state.clearReservation();
        }
        state.setPc(nextPc);
    }


    /// Executes a 32-bit AMO instruction.
    private void amoWord(MachineState state, Memory memory, long nextPc, AmoKind kind) {
        synchronized (memory) {
            long address = state.decodedRegister(rs1);
            int oldValue = memory.readInt(address);
            int source = (int) state.decodedRegister(rs2);
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
            state.setDecodedRegister(rd, oldValue);
            afterStore(state, address, Integer.BYTES);
            state.clearReservation();
        }
        state.setPc(nextPc);
    }


    /// Executes a 64-bit AMO instruction.
    private void amoDouble(MachineState state, Memory memory, long nextPc, AmoKind kind) {
        synchronized (memory) {
            long address = state.decodedRegister(rs1);
            long oldValue = memory.readLong(address);
            long source = state.decodedRegister(rs2);
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
            state.setDecodedRegister(rd, oldValue);
            afterStore(state, address, Long.BYTES);
            state.clearReservation();
        }
        state.setPc(nextPc);
    }


    /// Executes an F or D fused multiply-add operation.
    private void fusedMultiplyAdd(MachineState state, long nextPc, boolean negateProduct, boolean subtractAddend) {
        int roundingMode = effectiveRoundingMode(state);
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
            float nearest = Math.fma(effectiveLeft, right, effectiveAddend);
            float result;
            if (Float.isFinite(left) && Float.isFinite(right) && Float.isFinite(addend)) {
                result = roundSingleExactBinaryResult(state,
                        exactSingleFusedMultiplyAdd(leftBits, rightBits, addendBits, negateProduct, subtractAddend),
                        nearest,
                        roundingMode);
            } else {
                result = roundSingleResult(state,
                        ((double) effectiveLeft * (double) right) + (double) effectiveAddend,
                        nearest,
                        roundingMode);
            }
            writeSingleBits(state, rd, canonicalizeSingleBits(result));
        } else {
            long leftBits = state.decodedFloatingPointRegister(rs1);
            long rightBits = state.decodedFloatingPointRegister(rs2);
            long addendBits = state.decodedFloatingPointRegister(rs3);
            updateInvalidFlagForSignalingDoubleNaNs(state, leftBits, rightBits, addendBits);
            double left = Double.longBitsToDouble(leftBits);
            double right = Double.longBitsToDouble(rightBits);
            double addend = Double.longBitsToDouble(addendBits);
            double effectiveLeft = negateProduct ? -left : left;
            double effectiveAddend = subtractAddend ? -addend : addend;
            updateInvalidFlagForFusedMultiplyAdd(state, effectiveLeft, right, effectiveAddend);
            double nearest = Math.fma(effectiveLeft, right, effectiveAddend);
            double result;
            if (Double.isFinite(left) && Double.isFinite(right) && Double.isFinite(addend)) {
                result = roundDoubleResult(state,
                        exactDoubleFusedMultiplyAdd(leftBits, rightBits, addendBits, negateProduct, subtractAddend),
                        nearest,
                        roundingMode);
            } else {
                result = nearest;
                updateDoubleArithmeticFlags(state, effectiveLeft, right, effectiveAddend, result);
            }
            writeDoubleBits(state, rd, canonicalizeDoubleBits(result));
        }
        state.setPc(nextPc);
    }

    /// Executes a basic binary floating-point arithmetic operation.
    private void floatingPointArithmetic(MachineState state, long nextPc, char operator) {
        int roundingMode = effectiveRoundingMode(state);
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
            double exact = switch (operator) {
                case '+' -> (double) left + (double) right;
                case '-' -> (double) left - (double) right;
                case '*' -> (double) left * (double) right;
                case '/' -> (double) left / (double) right;
                default -> throw unexpectedFloatingPointOperator(operator);
            };
            float nearest = switch (operator) {
                case '+' -> left + right;
                case '-' -> left - right;
                case '*' -> left * right;
                case '/' -> left / right;
                default -> throw unexpectedFloatingPointOperator(operator);
            };
            float result;
            if (Float.isFinite(left) && Float.isFinite(right) && (operator != '/' || right != 0.0f)) {
                result = operator == '/'
                        ? roundSingleRationalResult(state, exactSingleDivision(leftBits, rightBits), nearest, roundingMode)
                        : roundSingleExactBinaryResult(state, exactSingleArithmetic(leftBits, rightBits, operator), nearest, roundingMode);
            } else {
                result = roundSingleResult(state, exact, nearest, roundingMode);
            }
            writeSingleBits(state, rd, canonicalizeSingleBits(result));
        } else {
            long leftBits = state.decodedFloatingPointRegister(rs1);
            long rightBits = state.decodedFloatingPointRegister(rs2);
            updateInvalidFlagForSignalingDoubleNaNs(state, leftBits, rightBits);
            double left = Double.longBitsToDouble(leftBits);
            double right = Double.longBitsToDouble(rightBits);
            updateInvalidFlagForArithmetic(state, left, right, operator);
            if (operator == '/') {
                updateDivideByZeroFlag(state, left, right);
            }
            double nearest = switch (operator) {
                case '+' -> left + right;
                case '-' -> left - right;
                case '*' -> left * right;
                case '/' -> left / right;
                default -> throw unexpectedFloatingPointOperator(operator);
            };
            double result;
            if (Double.isFinite(left) && Double.isFinite(right) && (operator != '/' || right != 0.0d)) {
                result = operator == '/'
                        ? roundDoubleRationalResult(state, exactDoubleDivision(leftBits, rightBits), nearest, roundingMode)
                        : roundDoubleResult(state, exactDoubleArithmetic(leftBits, rightBits, operator), nearest, roundingMode);
            } else {
                result = nearest;
                updateDoubleArithmeticFlags(state, left, right, operator, result);
            }
            writeDoubleBits(state, rd, canonicalizeDoubleBits(result));
        }
        state.setPc(nextPc);
    }

    /// Executes a floating-point square-root operation.
    private void floatingPointSquareRoot(MachineState state, long nextPc) {
        int roundingMode = effectiveRoundingMode(state);
        if (floatingPointFormat() == SINGLE_FLOAT_FORMAT) {
            int bits = readSingleBits(state, rs1);
            if (isSignalingSingleNaN(bits)) {
                state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            }
            float value = Float.intBitsToFloat(bits);
            if (value < 0.0f) {
                state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            }
            float nearest = (float) Math.sqrt(value);
            float result = Float.isFinite(value) && value >= 0.0f
                    ? roundSingleSquareRootResult(state, exactSingleValue(bits), nearest, roundingMode)
                    : nearest;
            writeSingleBits(state, rd, canonicalizeSingleBits(result));
        } else {
            long bits = state.decodedFloatingPointRegister(rs1);
            if (isSignalingDoubleNaN(bits)) {
                state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            }
            double value = Double.longBitsToDouble(bits);
            if (value < 0.0d) {
                state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            }
            double nearest = Math.sqrt(value);
            double result = Double.isFinite(value) && value >= 0.0d
                    ? roundDoubleSquareRootResult(state, exactDoubleValue(bits), nearest, roundingMode)
                    : nearest;
            writeDoubleBits(state, rd, canonicalizeDoubleBits(result));
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
            long left = state.decodedFloatingPointRegister(rs1);
            long right = state.decodedFloatingPointRegister(rs2);
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
                    ? minimumDoubleBits(state, state.decodedFloatingPointRegister(rs1), state.decodedFloatingPointRegister(rs2))
                    : maximumDoubleBits(state, state.decodedFloatingPointRegister(rs1), state.decodedFloatingPointRegister(rs2)));
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
                state.setDecodedRegister(rd, 0);
            } else {
                state.setDecodedRegister(rd, compareFloatingPoint(left, right, kind) ? 1 : 0);
            }
        } else {
            long leftBits = state.decodedFloatingPointRegister(rs1);
            long rightBits = state.decodedFloatingPointRegister(rs2);
            double left = Double.longBitsToDouble(leftBits);
            double right = Double.longBitsToDouble(rightBits);
            if (Double.isNaN(left) || Double.isNaN(right)) {
                if (kind != CompareKind.EQUAL || isSignalingDoubleNaN(leftBits) || isSignalingDoubleNaN(rightBits)) {
                    state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
                }
                state.setDecodedRegister(rd, 0);
            } else {
                state.setDecodedRegister(rd, compareFloatingPoint(left, right, kind) ? 1 : 0);
            }
        }
        state.setPc(nextPc);
    }


    /// Converts a floating-point value to an integer register.
    private void convertFloatingPointToInteger(MachineState state, long nextPc) {
        int roundingMode = effectiveRoundingMode(state);
        double value = floatingPointFormat() == SINGLE_FLOAT_FORMAT ? readSingle(state, rs1) : readDouble(state, rs1);
        switch (rs2) {
            case 0 -> state.setDecodedRegister(rd, (int) convertToSignedInteger(state, value, roundingMode, Integer.MIN_VALUE, 0x1.0p31));
            case 1 -> state.setDecodedRegister(rd, (int) convertToUnsignedInteger(state, value, roundingMode, 0x1.0p32));
            case 2 -> state.setDecodedRegister(rd, convertToSignedInteger(state, value, roundingMode, Long.MIN_VALUE, 0x1.0p63));
            case 3 -> state.setDecodedRegister(rd, convertToUnsignedInteger(state, value, roundingMode, 0x1.0p64));
            default -> throw unexpectedConversionSelector();
        }
        state.setPc(nextPc);
    }


    /// Converts an integer register value to a floating-point register.
    private void convertIntegerToFloatingPoint(MachineState state, long nextPc) {
        int roundingMode = effectiveRoundingMode(state);
        long value = state.decodedRegister(rs1);
        ExactBinaryValue exact = switch (rs2) {
            case 0 -> exactIntegerValue(BigInteger.valueOf((int) value));
            case 1 -> exactIntegerValue(BigInteger.valueOf(value & 0xffff_ffffL));
            case 2 -> exactIntegerValue(BigInteger.valueOf(value));
            case 3 -> exactIntegerValue(unsignedLongToBigInteger(value));
            default -> throw unexpectedConversionSelector();
        };
        if (floatingPointFormat() == SINGLE_FLOAT_FORMAT) {
            float result = roundSingleExactBinaryResult(state, exact, exact.significand().floatValue(), roundingMode);
            writeSingleBits(state, rd, canonicalizeSingleBits(result));
        } else {
            double result = roundDoubleResult(state, exact, exact.significand().doubleValue(), roundingMode);
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
        if (Float.isInfinite(addend)
                && (Float.isInfinite(left) || Float.isInfinite(right))
                && productSign(left, right) != Math.copySign(1.0f, addend)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
    }

    /// Updates invalid-operation flags for fused multiply-add indeterminate forms.
    private static void updateInvalidFlagForFusedMultiplyAdd(MachineState state, double left, double right, double addend) {
        if ((left == 0.0d && Double.isInfinite(right)) || (Double.isInfinite(left) && right == 0.0d)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
            return;
        }
        if (Double.isInfinite(addend)
                && (Double.isInfinite(left) || Double.isInfinite(right))
                && productSign(left, right) != Math.copySign(1.0d, addend)) {
            state.addFloatingPointFlags(FLOATING_POINT_INVALID_OPERATION);
        }
    }

    /// Returns the sign of the exact product of two single-precision operands.
    private static float productSign(float left, float right) {
        return Math.copySign(1.0f, left) == Math.copySign(1.0f, right) ? 1.0f : -1.0f;
    }

    /// Returns the sign of the exact product of two double-precision operands.
    private static double productSign(double left, double right) {
        return Math.copySign(1.0d, left) == Math.copySign(1.0d, right) ? 1.0d : -1.0d;
    }

    /// Rounds a single-precision arithmetic result and records single-precision arithmetic flags.
    private static float roundSingleResult(MachineState state, double exact, float nearest, int roundingMode) {
        if (Double.isNaN(exact) || Double.isInfinite(exact)) {
            return nearest;
        }

        return roundSingleExactBinaryResult(state, exactDoubleValue(Double.doubleToRawLongBits(exact)), nearest, roundingMode);
    }

    /// Rounds a finite exact binary value to single precision and records arithmetic flags.
    private static float roundSingleExactBinaryResult(
            MachineState state,
            ExactBinaryValue exact,
            float nearest,
            int roundingMode) {
        if (exact.signum() == 0) {
            return nearest;
        }

        boolean overflow = Float.isInfinite(nearest);
        boolean inexact = true;
        float rounded;
        if (overflow) {
            rounded = roundSingleOverflow(exact.signum(), roundingMode);
        } else {
            int comparison = compareBinaryValues(exact, exactSingleValue(Float.floatToRawIntBits(nearest)));
            inexact = comparison != 0;
            rounded = comparison == 0 ? nearest : roundSingleInexact(exact, nearest, roundingMode, comparison);
        }

        updateSingleExactArithmeticFlags(state, exact.signum(), rounded, inexact, overflow);
        return rounded;
    }

    /// Rounds an inexact finite exact binary value to single precision.
    private static float roundSingleInexact(ExactBinaryValue exact, float nearest, int roundingMode, int comparison) {
        return switch (roundingMode) {
            case ROUND_NEAREST_EVEN -> nearest;
            case ROUND_TOWARD_ZERO -> {
                if (exact.signum() > 0 && comparison < 0) {
                    yield Math.nextDown(nearest);
                }
                if (exact.signum() < 0 && comparison > 0) {
                    yield Math.nextUp(nearest);
                }
                yield nearest;
            }
            case ROUND_DOWN -> comparison < 0 ? Math.nextDown(nearest) : nearest;
            case ROUND_UP -> comparison > 0 ? Math.nextUp(nearest) : nearest;
            case ROUND_NEAREST_MAX_MAGNITUDE -> roundSingleNearestMaxMagnitude(exact, nearest, comparison);
            default -> throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
        };
    }

    /// Rounds a single-precision overflow according to the requested rounding mode.
    private static float roundSingleOverflow(int exactSign, int roundingMode) {
        if (exactSign > 0) {
            return switch (roundingMode) {
                case ROUND_NEAREST_EVEN, ROUND_UP, ROUND_NEAREST_MAX_MAGNITUDE -> Float.POSITIVE_INFINITY;
                case ROUND_TOWARD_ZERO, ROUND_DOWN -> Float.MAX_VALUE;
                default -> throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
            };
        }
        return switch (roundingMode) {
            case ROUND_NEAREST_EVEN, ROUND_DOWN, ROUND_NEAREST_MAX_MAGNITUDE -> Float.NEGATIVE_INFINITY;
            case ROUND_TOWARD_ZERO, ROUND_UP -> -Float.MAX_VALUE;
            default -> throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
        };
    }

    /// Applies RMM tie handling for a single-precision result already rounded with RNE.
    private static float roundSingleNearestMaxMagnitude(ExactBinaryValue exact, float nearest, int comparison) {
        float lower = comparison > 0 ? nearest : Math.nextDown(nearest);
        float upper = comparison > 0 ? Math.nextUp(nearest) : nearest;
        if (Float.isInfinite(lower) || Float.isInfinite(upper)) {
            return nearest;
        }

        ExactBinaryValue lowerValue = exactSingleValue(Float.floatToRawIntBits(lower));
        ExactBinaryValue upperValue = exactSingleValue(Float.floatToRawIntBits(upper));
        ExactBinaryValue lowerDistance = subtractBinaryValues(exact, lowerValue);
        ExactBinaryValue upperDistance = subtractBinaryValues(upperValue, exact);
        if (compareBinaryMagnitudes(lowerDistance, upperDistance) == 0) {
            return exact.signum() < 0 ? lower : upper;
        }
        return nearest;
    }

    /// Rounds a finite exact rational value to single precision and records arithmetic flags.
    private static float roundSingleRationalResult(
            MachineState state,
            ExactRationalValue exact,
            float nearest,
            int roundingMode) {
        if (exact.signum() == 0) {
            return nearest;
        }

        boolean overflow = Float.isInfinite(nearest);
        boolean inexact = true;
        float rounded;
        if (overflow) {
            rounded = roundSingleOverflow(exact.signum(), roundingMode);
        } else {
            int comparison = compareRationalToBinary(exact, exactSingleValue(Float.floatToRawIntBits(nearest)));
            inexact = comparison != 0;
            rounded = comparison == 0 ? nearest : roundSingleRationalInexact(exact, nearest, roundingMode, comparison);
        }

        updateSingleExactArithmeticFlags(state, exact.signum(), rounded, inexact, overflow);
        return rounded;
    }

    /// Rounds an inexact finite exact rational value to single precision.
    private static float roundSingleRationalInexact(
            ExactRationalValue exact,
            float nearest,
            int roundingMode,
            int comparison) {
        return switch (roundingMode) {
            case ROUND_NEAREST_EVEN -> nearest;
            case ROUND_TOWARD_ZERO -> {
                if (exact.signum() > 0 && comparison < 0) {
                    yield Math.nextDown(nearest);
                }
                if (exact.signum() < 0 && comparison > 0) {
                    yield Math.nextUp(nearest);
                }
                yield nearest;
            }
            case ROUND_DOWN -> comparison < 0 ? Math.nextDown(nearest) : nearest;
            case ROUND_UP -> comparison > 0 ? Math.nextUp(nearest) : nearest;
            case ROUND_NEAREST_MAX_MAGNITUDE -> roundSingleRationalNearestMaxMagnitude(exact, nearest, comparison);
            default -> throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
        };
    }

    /// Applies RMM tie handling for an exact rational single-precision result already rounded with RNE.
    private static float roundSingleRationalNearestMaxMagnitude(
            ExactRationalValue exact,
            float nearest,
            int comparison) {
        float lower = comparison > 0 ? nearest : Math.nextDown(nearest);
        float upper = comparison > 0 ? Math.nextUp(nearest) : nearest;
        if (Float.isInfinite(lower) || Float.isInfinite(upper)) {
            return nearest;
        }

        ExactBinaryValue lowerValue = exactSingleValue(Float.floatToRawIntBits(lower));
        ExactBinaryValue upperValue = exactSingleValue(Float.floatToRawIntBits(upper));
        if (compareRationalDistances(exact, lowerValue, upperValue) == 0) {
            return exact.signum() < 0 ? lower : upper;
        }
        return nearest;
    }

    /// Records single-precision flags for an exact binary source value.
    private static void updateSingleExactArithmeticFlags(
            MachineState state,
            int exactSign,
            float rounded,
            boolean inexact,
            boolean overflow) {
        if (!inexact) {
            return;
        }
        if (overflow || Float.isInfinite(rounded)) {
            state.addFloatingPointFlags(FLOATING_POINT_OVERFLOW | FLOATING_POINT_INEXACT);
            return;
        }
        state.addFloatingPointFlags(FLOATING_POINT_INEXACT);
        if (exactSign != 0 && (rounded == 0.0f || Math.abs(rounded) < Float.MIN_NORMAL)) {
            state.addFloatingPointFlags(FLOATING_POINT_UNDERFLOW);
        }
    }

    /// Rounds a finite single-precision square root and records arithmetic flags.
    private static float roundSingleSquareRootResult(
            MachineState state,
            ExactBinaryValue radicand,
            float nearest,
            int roundingMode) {
        if (radicand.signum() == 0) {
            return nearest;
        }

        int comparison = compareSquareRootToBinary(radicand, exactSingleValue(Float.floatToRawIntBits(nearest)));
        if (comparison == 0) {
            return nearest;
        }

        float rounded = switch (roundingMode) {
            case ROUND_NEAREST_EVEN -> nearest;
            case ROUND_TOWARD_ZERO, ROUND_DOWN -> comparison < 0 ? Math.nextDown(nearest) : nearest;
            case ROUND_UP -> comparison > 0 ? Math.nextUp(nearest) : nearest;
            case ROUND_NEAREST_MAX_MAGNITUDE -> roundSingleSquareRootNearestMaxMagnitude(radicand, nearest, comparison);
            default -> throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
        };
        state.addFloatingPointFlags(FLOATING_POINT_INEXACT);
        return rounded;
    }

    /// Applies RMM tie handling for a single-precision square root already rounded with RNE.
    private static float roundSingleSquareRootNearestMaxMagnitude(
            ExactBinaryValue radicand,
            float nearest,
            int comparison) {
        float lower = comparison > 0 ? nearest : Math.nextDown(nearest);
        float upper = comparison > 0 ? Math.nextUp(nearest) : nearest;
        if (Float.isInfinite(lower) || Float.isInfinite(upper)) {
            return nearest;
        }

        ExactBinaryValue midpoint = halveBinaryValue(addBinaryValues(
                exactSingleValue(Float.floatToRawIntBits(lower)),
                exactSingleValue(Float.floatToRawIntBits(upper))));
        return compareSquareRootToBinary(radicand, midpoint) == 0 ? upper : nearest;
    }

    /// Rounds a finite exact binary value to double precision and records arithmetic flags.
    private static double roundDoubleResult(
            MachineState state,
            ExactBinaryValue exact,
            double nearest,
            int roundingMode) {
        if (exact.signum() == 0) {
            return nearest;
        }

        boolean overflow = Double.isInfinite(nearest);
        boolean inexact = true;
        double rounded;
        if (overflow) {
            rounded = roundDoubleOverflow(exact.signum(), roundingMode);
        } else {
            int comparison = compareBinaryValues(exact, exactDoubleValue(Double.doubleToRawLongBits(nearest)));
            inexact = comparison != 0;
            rounded = comparison == 0 ? nearest : roundDoubleInexact(exact, nearest, roundingMode, comparison);
        }

        updateDoubleExactArithmeticFlags(state, exact.signum(), rounded, inexact, overflow);
        return rounded;
    }

    /// Rounds an inexact finite exact binary value to double precision.
    private static double roundDoubleInexact(ExactBinaryValue exact, double nearest, int roundingMode, int comparison) {
        return switch (roundingMode) {
            case ROUND_NEAREST_EVEN -> nearest;
            case ROUND_TOWARD_ZERO -> {
                if (exact.signum() > 0 && comparison < 0) {
                    yield Math.nextDown(nearest);
                }
                if (exact.signum() < 0 && comparison > 0) {
                    yield Math.nextUp(nearest);
                }
                yield nearest;
            }
            case ROUND_DOWN -> comparison < 0 ? Math.nextDown(nearest) : nearest;
            case ROUND_UP -> comparison > 0 ? Math.nextUp(nearest) : nearest;
            case ROUND_NEAREST_MAX_MAGNITUDE -> roundDoubleNearestMaxMagnitude(exact, nearest, comparison);
            default -> throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
        };
    }

    /// Rounds a double-precision overflow according to the requested rounding mode.
    private static double roundDoubleOverflow(int exactSign, int roundingMode) {
        if (exactSign > 0) {
            return switch (roundingMode) {
                case ROUND_NEAREST_EVEN, ROUND_UP, ROUND_NEAREST_MAX_MAGNITUDE -> Double.POSITIVE_INFINITY;
                case ROUND_TOWARD_ZERO, ROUND_DOWN -> Double.MAX_VALUE;
                default -> throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
            };
        }
        return switch (roundingMode) {
            case ROUND_NEAREST_EVEN, ROUND_DOWN, ROUND_NEAREST_MAX_MAGNITUDE -> Double.NEGATIVE_INFINITY;
            case ROUND_TOWARD_ZERO, ROUND_UP -> -Double.MAX_VALUE;
            default -> throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
        };
    }

    /// Applies RMM tie handling for a double-precision result already rounded with RNE.
    private static double roundDoubleNearestMaxMagnitude(ExactBinaryValue exact, double nearest, int comparison) {
        double lower = comparison > 0 ? nearest : Math.nextDown(nearest);
        double upper = comparison > 0 ? Math.nextUp(nearest) : nearest;
        if (Double.isInfinite(lower) || Double.isInfinite(upper)) {
            return nearest;
        }

        ExactBinaryValue lowerValue = exactDoubleValue(Double.doubleToRawLongBits(lower));
        ExactBinaryValue upperValue = exactDoubleValue(Double.doubleToRawLongBits(upper));
        ExactBinaryValue lowerDistance = subtractBinaryValues(exact, lowerValue);
        ExactBinaryValue upperDistance = subtractBinaryValues(upperValue, exact);
        if (compareBinaryMagnitudes(lowerDistance, upperDistance) == 0) {
            return exact.signum() < 0 ? lower : upper;
        }
        return nearest;
    }

    /// Rounds a finite double-precision square root and records arithmetic flags.
    private static double roundDoubleSquareRootResult(
            MachineState state,
            ExactBinaryValue radicand,
            double nearest,
            int roundingMode) {
        if (radicand.signum() == 0) {
            return nearest;
        }

        int comparison = compareSquareRootToBinary(radicand, exactDoubleValue(Double.doubleToRawLongBits(nearest)));
        if (comparison == 0) {
            return nearest;
        }

        double rounded = switch (roundingMode) {
            case ROUND_NEAREST_EVEN -> nearest;
            case ROUND_TOWARD_ZERO, ROUND_DOWN -> comparison < 0 ? Math.nextDown(nearest) : nearest;
            case ROUND_UP -> comparison > 0 ? Math.nextUp(nearest) : nearest;
            case ROUND_NEAREST_MAX_MAGNITUDE -> roundDoubleSquareRootNearestMaxMagnitude(radicand, nearest, comparison);
            default -> throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
        };
        state.addFloatingPointFlags(FLOATING_POINT_INEXACT);
        return rounded;
    }

    /// Applies RMM tie handling for a double-precision square root already rounded with RNE.
    private static double roundDoubleSquareRootNearestMaxMagnitude(
            ExactBinaryValue radicand,
            double nearest,
            int comparison) {
        double lower = comparison > 0 ? nearest : Math.nextDown(nearest);
        double upper = comparison > 0 ? Math.nextUp(nearest) : nearest;
        if (Double.isInfinite(lower) || Double.isInfinite(upper)) {
            return nearest;
        }

        ExactBinaryValue midpoint = halveBinaryValue(addBinaryValues(
                exactDoubleValue(Double.doubleToRawLongBits(lower)),
                exactDoubleValue(Double.doubleToRawLongBits(upper))));
        return compareSquareRootToBinary(radicand, midpoint) == 0 ? upper : nearest;
    }

    /// Rounds a finite exact rational value to double precision and records arithmetic flags.
    private static double roundDoubleRationalResult(
            MachineState state,
            ExactRationalValue exact,
            double nearest,
            int roundingMode) {
        if (exact.signum() == 0) {
            return nearest;
        }

        boolean overflow = Double.isInfinite(nearest);
        boolean inexact = true;
        double rounded;
        if (overflow) {
            rounded = roundDoubleOverflow(exact.signum(), roundingMode);
        } else {
            int comparison = compareRationalToBinary(exact, exactDoubleValue(Double.doubleToRawLongBits(nearest)));
            inexact = comparison != 0;
            rounded = comparison == 0 ? nearest : roundDoubleRationalInexact(exact, nearest, roundingMode, comparison);
        }

        updateDoubleExactArithmeticFlags(state, exact.signum(), rounded, inexact, overflow);
        return rounded;
    }

    /// Rounds an inexact finite exact rational value to double precision.
    private static double roundDoubleRationalInexact(
            ExactRationalValue exact,
            double nearest,
            int roundingMode,
            int comparison) {
        return switch (roundingMode) {
            case ROUND_NEAREST_EVEN -> nearest;
            case ROUND_TOWARD_ZERO -> {
                if (exact.signum() > 0 && comparison < 0) {
                    yield Math.nextDown(nearest);
                }
                if (exact.signum() < 0 && comparison > 0) {
                    yield Math.nextUp(nearest);
                }
                yield nearest;
            }
            case ROUND_DOWN -> comparison < 0 ? Math.nextDown(nearest) : nearest;
            case ROUND_UP -> comparison > 0 ? Math.nextUp(nearest) : nearest;
            case ROUND_NEAREST_MAX_MAGNITUDE -> roundDoubleRationalNearestMaxMagnitude(exact, nearest, comparison);
            default -> throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
        };
    }

    /// Applies RMM tie handling for an exact rational double-precision result already rounded with RNE.
    private static double roundDoubleRationalNearestMaxMagnitude(
            ExactRationalValue exact,
            double nearest,
            int comparison) {
        double lower = comparison > 0 ? nearest : Math.nextDown(nearest);
        double upper = comparison > 0 ? Math.nextUp(nearest) : nearest;
        if (Double.isInfinite(lower) || Double.isInfinite(upper)) {
            return nearest;
        }

        ExactBinaryValue lowerValue = exactDoubleValue(Double.doubleToRawLongBits(lower));
        ExactBinaryValue upperValue = exactDoubleValue(Double.doubleToRawLongBits(upper));
        if (compareRationalDistances(exact, lowerValue, upperValue) == 0) {
            return exact.signum() < 0 ? lower : upper;
        }
        return nearest;
    }

    /// Records double-precision flags for an exact binary source value.
    private static void updateDoubleExactArithmeticFlags(
            MachineState state,
            int exactSign,
            double rounded,
            boolean inexact,
            boolean overflow) {
        if (!inexact) {
            return;
        }
        if (overflow || Double.isInfinite(rounded)) {
            state.addFloatingPointFlags(FLOATING_POINT_OVERFLOW | FLOATING_POINT_INEXACT);
            return;
        }
        state.addFloatingPointFlags(FLOATING_POINT_INEXACT);
        if (exactSign != 0 && (rounded == 0.0d || Math.abs(rounded) < Double.MIN_NORMAL)) {
            state.addFloatingPointFlags(FLOATING_POINT_UNDERFLOW);
        }
    }

    /// Records conservative double-precision arithmetic flags for binary operations.
    private static void updateDoubleArithmeticFlags(MachineState state, double left, double right, char operator, double result) {
        if (Double.isNaN(result)) {
            return;
        }
        if (operator == '/' && right == 0.0d) {
            return;
        }
        if (Double.isFinite(left) && Double.isFinite(right) && Double.isInfinite(result)) {
            state.addFloatingPointFlags(FLOATING_POINT_OVERFLOW | FLOATING_POINT_INEXACT);
            return;
        }
        if (Double.isFinite(result) && result != 0.0d && Math.abs(result) < Double.MIN_NORMAL) {
            state.addFloatingPointFlags(FLOATING_POINT_UNDERFLOW | FLOATING_POINT_INEXACT);
        }
        if (operator == '/' && Double.isFinite(left) && Double.isFinite(right) && right != 0.0d && result * right != left) {
            state.addFloatingPointFlags(FLOATING_POINT_INEXACT);
        }
    }

    /// Records conservative double-precision arithmetic flags for fused multiply-add operations.
    private static void updateDoubleArithmeticFlags(MachineState state, double left, double right, double addend, double result) {
        if (Double.isNaN(result)) {
            return;
        }
        if (Double.isFinite(left) && Double.isFinite(right) && Double.isFinite(addend) && Double.isInfinite(result)) {
            state.addFloatingPointFlags(FLOATING_POINT_OVERFLOW | FLOATING_POINT_INEXACT);
            return;
        }
        if (Double.isFinite(result) && result != 0.0d && Math.abs(result) < Double.MIN_NORMAL) {
            state.addFloatingPointFlags(FLOATING_POINT_UNDERFLOW | FLOATING_POINT_INEXACT);
        }
    }

    /// Returns the exact finite result of a single-precision add, subtract, or multiply operation.
    private static ExactBinaryValue exactSingleArithmetic(int leftBits, int rightBits, char operator) {
        ExactBinaryValue left = exactSingleValue(leftBits);
        ExactBinaryValue right = exactSingleValue(rightBits);
        return switch (operator) {
            case '+' -> addBinaryValues(left, right);
            case '-' -> addBinaryValues(left, right.negate());
            case '*' -> multiplyBinaryValues(left, right);
            default -> throw new AssertionError("Unexpected floating-point operator: " + operator);
        };
    }

    /// Returns the exact finite result of a single-precision division operation.
    private static ExactRationalValue exactSingleDivision(int leftBits, int rightBits) {
        ExactBinaryValue left = exactSingleValue(leftBits);
        ExactBinaryValue right = exactSingleValue(rightBits);
        BigInteger numerator = left.significand();
        BigInteger denominator = right.significand();
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        return new ExactRationalValue(numerator, denominator, left.exponent() - right.exponent());
    }

    /// Returns the exact finite result of a single-precision fused multiply-add operation.
    private static ExactBinaryValue exactSingleFusedMultiplyAdd(
            int leftBits,
            int rightBits,
            int addendBits,
            boolean negateProduct,
            boolean subtractAddend) {
        ExactBinaryValue product = multiplyBinaryValues(exactSingleValue(leftBits), exactSingleValue(rightBits));
        if (negateProduct) {
            product = product.negate();
        }
        ExactBinaryValue addend = exactSingleValue(addendBits);
        if (subtractAddend) {
            addend = addend.negate();
        }
        return addBinaryValues(product, addend);
    }

    /// Returns the exact finite result of a double-precision add, subtract, or multiply operation.
    private static ExactBinaryValue exactDoubleArithmetic(long leftBits, long rightBits, char operator) {
        ExactBinaryValue left = exactDoubleValue(leftBits);
        ExactBinaryValue right = exactDoubleValue(rightBits);
        return switch (operator) {
            case '+' -> addBinaryValues(left, right);
            case '-' -> addBinaryValues(left, right.negate());
            case '*' -> multiplyBinaryValues(left, right);
            default -> throw new AssertionError("Unexpected floating-point operator: " + operator);
        };
    }

    /// Returns the exact finite result of a double-precision division operation.
    private static ExactRationalValue exactDoubleDivision(long leftBits, long rightBits) {
        ExactBinaryValue left = exactDoubleValue(leftBits);
        ExactBinaryValue right = exactDoubleValue(rightBits);
        BigInteger numerator = left.significand();
        BigInteger denominator = right.significand();
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        return new ExactRationalValue(numerator, denominator, left.exponent() - right.exponent());
    }

    /// Returns the exact finite result of a double-precision fused multiply-add operation.
    private static ExactBinaryValue exactDoubleFusedMultiplyAdd(
            long leftBits,
            long rightBits,
            long addendBits,
            boolean negateProduct,
            boolean subtractAddend) {
        ExactBinaryValue product = multiplyBinaryValues(exactDoubleValue(leftBits), exactDoubleValue(rightBits));
        if (negateProduct) {
            product = product.negate();
        }
        ExactBinaryValue addend = exactDoubleValue(addendBits);
        if (subtractAddend) {
            addend = addend.negate();
        }
        return addBinaryValues(product, addend);
    }

    /// Returns the exact binary value of an integer conversion source.
    private static ExactBinaryValue exactIntegerValue(BigInteger value) {
        return new ExactBinaryValue(value, 0);
    }

    /// Returns the exact binary value represented by finite single-precision bits.
    private static ExactBinaryValue exactSingleValue(int bits) {
        int fraction = bits & 0x007f_ffff;
        int exponentBits = (bits >>> 23) & 0xff;
        BigInteger significand;
        int exponent;
        if (exponentBits == 0) {
            significand = BigInteger.valueOf(fraction);
            exponent = -149;
        } else {
            significand = BigInteger.valueOf((1 << 23) | fraction);
            exponent = exponentBits - 150;
        }
        return bits < 0 ? new ExactBinaryValue(significand.negate(), exponent) : new ExactBinaryValue(significand, exponent);
    }

    /// Returns the exact binary value represented by finite double-precision bits.
    private static ExactBinaryValue exactDoubleValue(long bits) {
        long fraction = bits & 0x000f_ffff_ffff_ffffL;
        int exponentBits = (int) ((bits >>> 52) & 0x7ff);
        BigInteger significand;
        int exponent;
        if (exponentBits == 0) {
            significand = BigInteger.valueOf(fraction);
            exponent = -1074;
        } else {
            significand = BigInteger.valueOf((1L << 52) | fraction);
            exponent = exponentBits - 1075;
        }
        return bits < 0 ? new ExactBinaryValue(significand.negate(), exponent) : new ExactBinaryValue(significand, exponent);
    }

    /// Adds two exact binary values.
    private static ExactBinaryValue addBinaryValues(ExactBinaryValue left, ExactBinaryValue right) {
        int exponent = Math.min(left.exponent(), right.exponent());
        BigInteger significand = scaleSignificand(left, exponent).add(scaleSignificand(right, exponent));
        return new ExactBinaryValue(significand, exponent);
    }

    /// Subtracts two exact binary values.
    private static ExactBinaryValue subtractBinaryValues(ExactBinaryValue left, ExactBinaryValue right) {
        int exponent = Math.min(left.exponent(), right.exponent());
        BigInteger significand = scaleSignificand(left, exponent).subtract(scaleSignificand(right, exponent));
        return new ExactBinaryValue(significand, exponent);
    }

    /// Multiplies two exact binary values.
    private static ExactBinaryValue multiplyBinaryValues(ExactBinaryValue left, ExactBinaryValue right) {
        return new ExactBinaryValue(left.significand().multiply(right.significand()), left.exponent() + right.exponent());
    }

    /// Halves an exact binary value.
    private static ExactBinaryValue halveBinaryValue(ExactBinaryValue value) {
        return new ExactBinaryValue(value.significand(), value.exponent() - 1);
    }

    /// Compares two exact binary values numerically.
    private static int compareBinaryValues(ExactBinaryValue left, ExactBinaryValue right) {
        int exponent = Math.min(left.exponent(), right.exponent());
        return scaleSignificand(left, exponent).compareTo(scaleSignificand(right, exponent));
    }

    /// Compares the magnitudes of two exact binary values.
    private static int compareBinaryMagnitudes(ExactBinaryValue left, ExactBinaryValue right) {
        return compareBinaryValues(left.abs(), right.abs());
    }

    /// Compares a square root of an exact binary value with a non-negative exact binary candidate.
    private static int compareSquareRootToBinary(ExactBinaryValue radicand, ExactBinaryValue candidate) {
        return -Integer.signum(compareBinaryValues(multiplyBinaryValues(candidate, candidate), radicand));
    }

    /// Compares an exact rational value with an exact binary value.
    private static int compareRationalToBinary(ExactRationalValue left, ExactBinaryValue right) {
        int exponent = Math.min(left.exponent(), right.exponent());
        BigInteger scaledLeft = left.numerator().shiftLeft(left.exponent() - exponent);
        BigInteger scaledRight = scaleSignificand(right, exponent).multiply(left.denominator());
        return scaledLeft.compareTo(scaledRight);
    }

    /// Compares the distances from an exact rational value to its neighboring binary values.
    private static int compareRationalDistances(
            ExactRationalValue exact,
            ExactBinaryValue lower,
            ExactBinaryValue upper) {
        int exponent = Math.min(exact.exponent(), Math.min(lower.exponent(), upper.exponent()));
        BigInteger scaledExact = exact.numerator().shiftLeft(exact.exponent() - exponent);
        BigInteger scaledLower = scaleSignificand(lower, exponent).multiply(exact.denominator());
        BigInteger scaledUpper = scaleSignificand(upper, exponent).multiply(exact.denominator());
        BigInteger lowerDistance = scaledExact.subtract(scaledLower).abs();
        BigInteger upperDistance = scaledUpper.subtract(scaledExact).abs();
        return lowerDistance.compareTo(upperDistance);
    }

    /// Scales a significand to the requested binary exponent.
    private static BigInteger scaleSignificand(ExactBinaryValue value, int exponent) {
        return value.significand().shiftLeft(value.exponent() - exponent);
    }

    /// An exact signed binary value represented as `significand * 2^exponent`.
    ///
    /// @param significand the signed integer significand
    /// @param exponent the binary exponent applied to the significand
    @NotNullByDefault
    private record ExactBinaryValue(
            BigInteger significand,
            int exponent) {
        /// Returns the sign of this exact value.
        private int signum() {
            return significand.signum();
        }

        /// Returns this value with its sign inverted.
        private ExactBinaryValue negate() {
            return new ExactBinaryValue(significand.negate(), exponent);
        }

        /// Returns this value with a non-negative significand.
        private ExactBinaryValue abs() {
            return significand.signum() < 0 ? negate() : this;
        }
    }

    /// An exact signed rational binary value represented as `numerator * 2^exponent / denominator`.
    ///
    /// @param numerator the signed integer numerator
    /// @param denominator the positive integer denominator
    /// @param exponent the binary exponent applied to the numerator
    @NotNullByDefault
    private record ExactRationalValue(
            BigInteger numerator,
            BigInteger denominator,
            int exponent) {
        /// Creates an exact rational binary value.
        private ExactRationalValue {
            if (denominator.signum() <= 0) {
                throw new AssertionError("Exact rational denominator must be positive");
            }
        }

        /// Returns the sign of this exact value.
        private int signum() {
            return numerator.signum();
        }
    }

    /// Reads a single-precision register as raw bits, applying NaN-boxing rules.
    private static int readSingleBits(MachineState state, int register) {
        long value = state.decodedFloatingPointRegister(register);
        return (value & SINGLE_NAN_BOX_MASK) == SINGLE_NAN_BOX_MASK ? (int) value : CANONICAL_SINGLE_NAN;
    }

    /// Reads a single-precision register as a Java float.
    private static float readSingle(MachineState state, int register) {
        return Float.intBitsToFloat(readSingleBits(state, register));
    }

    /// Reads a double-precision register as a Java double.
    private static double readDouble(MachineState state, int register) {
        return Double.longBitsToDouble(state.decodedFloatingPointRegister(register));
    }

    /// Writes raw single-precision bits to a NaN-boxed floating-point register.
    private static void writeSingleBits(MachineState state, int register, int bits) {
        state.setDecodedFloatingPointRegister(register, SINGLE_NAN_BOX_MASK | (bits & 0xffff_ffffL));
    }

    /// Writes raw double-precision bits to a floating-point register.
    private static void writeDoubleBits(MachineState state, int register, long bits) {
        state.setDecodedFloatingPointRegister(register, bits);
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
