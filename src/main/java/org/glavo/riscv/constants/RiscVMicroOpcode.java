package org.glavo.riscv.constants;

import org.jetbrains.annotations.NotNullByDefault;

/// Defines the compact opcodes used by the custom RISC-V micro-bytecode interpreter.
@NotNullByDefault
public final class RiscVMicroOpcode {
    /// Executes an operation that is not yet expanded into a dedicated micro-op body.
    public static final byte EXECUTE_OPERATION = 0;

    /// Advances `pc` for no-op control instructions.
    public static final byte ADVANCE_PC = 1;

    /// Executes `lui`.
    public static final byte LUI = 2;

    /// Executes `auipc`.
    public static final byte AUIPC = 3;

    /// Executes `jal`.
    public static final byte JAL = 4;

    /// Executes `jalr`.
    public static final byte JALR = 5;

    /// Executes `beq`.
    public static final byte BEQ = 6;

    /// Executes `bne`.
    public static final byte BNE = 7;

    /// Executes `blt`.
    public static final byte BLT = 8;

    /// Executes `bge`.
    public static final byte BGE = 9;

    /// Executes `bltu`.
    public static final byte BLTU = 10;

    /// Executes `bgeu`.
    public static final byte BGEU = 11;

    /// Executes `lb`.
    public static final byte LB = 12;

    /// Executes `lh`.
    public static final byte LH = 13;

    /// Executes `lw`.
    public static final byte LW = 14;

    /// Executes `ld`.
    public static final byte LD = 15;

    /// Executes `lbu`.
    public static final byte LBU = 16;

    /// Executes `lhu`.
    public static final byte LHU = 17;

    /// Executes `lwu`.
    public static final byte LWU = 18;

    /// Executes `sb`.
    public static final byte SB = 19;

    /// Executes `sh`.
    public static final byte SH = 20;

    /// Executes `sw`.
    public static final byte SW = 21;

    /// Executes `sd`.
    public static final byte SD = 22;

    /// Executes `addi`.
    public static final byte ADDI = 23;

    /// Executes `slti`.
    public static final byte SLTI = 24;

    /// Executes `sltiu`.
    public static final byte SLTIU = 25;

    /// Executes `xori`.
    public static final byte XORI = 26;

    /// Executes `ori`.
    public static final byte ORI = 27;

    /// Executes `andi`.
    public static final byte ANDI = 28;

    /// Executes `slli`.
    public static final byte SLLI = 29;

    /// Executes `srli`.
    public static final byte SRLI = 30;

    /// Executes `srai`.
    public static final byte SRAI = 31;

    /// Executes `addiw`.
    public static final byte ADDIW = 32;

    /// Executes `slliw`.
    public static final byte SLLIW = 33;

    /// Executes `srliw`.
    public static final byte SRLIW = 34;

    /// Executes `sraiw`.
    public static final byte SRAIW = 35;

    /// Executes `add`.
    public static final byte ADD = 36;

    /// Executes `sub`.
    public static final byte SUB = 37;

    /// Executes `sll`.
    public static final byte SLL = 38;

    /// Executes `slt`.
    public static final byte SLT = 39;

    /// Executes `sltu`.
    public static final byte SLTU = 40;

    /// Executes `xor`.
    public static final byte XOR = 41;

    /// Executes `srl`.
    public static final byte SRL = 42;

    /// Executes `sra`.
    public static final byte SRA = 43;

    /// Executes `or`.
    public static final byte OR = 44;

    /// Executes `and`.
    public static final byte AND = 45;

    /// Executes `addw`.
    public static final byte ADDW = 46;

    /// Executes `subw`.
    public static final byte SUBW = 47;

    /// Executes `sllw`.
    public static final byte SLLW = 48;

    /// Executes `srlw`.
    public static final byte SRLW = 49;

    /// Executes `sraw`.
    public static final byte SRAW = 50;

    /// Executes `ecall`.
    public static final byte ECALL = 51;

    /// Executes `ebreak`.
    public static final byte EBREAK = 52;

    /// Executes `csrrw`.
    public static final byte CSRRW = 53;

    /// Executes `csrrs`.
    public static final byte CSRRS = 54;

    /// Executes `csrrc`.
    public static final byte CSRRC = 55;

    /// Executes `csrrwi`.
    public static final byte CSRRWI = 56;

    /// Executes `csrrsi`.
    public static final byte CSRRSI = 57;

    /// Executes `csrrci`.
    public static final byte CSRRCI = 58;

    /// Executes `mul`.
    public static final byte MUL = 59;

    /// Executes `mulh`.
    public static final byte MULH = 60;

    /// Executes `mulhsu`.
    public static final byte MULHSU = 61;

    /// Executes `mulhu`.
    public static final byte MULHU = 62;

    /// Executes `div`.
    public static final byte DIV = 63;

    /// Executes `divu`.
    public static final byte DIVU = 64;

    /// Executes `rem`.
    public static final byte REM = 65;

    /// Executes `remu`.
    public static final byte REMU = 66;

    /// Executes `mulw`.
    public static final byte MULW = 67;

    /// Executes `divw`.
    public static final byte DIVW = 68;

    /// Executes `divuw`.
    public static final byte DIVUW = 69;

    /// Executes `remw`.
    public static final byte REMW = 70;

    /// Executes `remuw`.
    public static final byte REMUW = 71;

    /// Executes `flw`.
    public static final byte FLW = 72;

    /// Executes `fld`.
    public static final byte FLD = 73;

    /// Executes `fsw`.
    public static final byte FSW = 74;

    /// Executes `fsd`.
    public static final byte FSD = 75;

    /// Executes `lr.w`.
    public static final byte LR_W = 76;

    /// Executes `lr.d`.
    public static final byte LR_D = 77;

    /// Executes `sc.w`.
    public static final byte SC_W = 78;

    /// Executes `sc.d`.
    public static final byte SC_D = 79;

    /// Executes `amoswap.w`.
    public static final byte AMOSWAP_W = 80;

    /// Executes `amoadd.w`.
    public static final byte AMOADD_W = 81;

    /// Executes `amoxor.w`.
    public static final byte AMOXOR_W = 82;

    /// Executes `amoand.w`.
    public static final byte AMOAND_W = 83;

    /// Executes `amoor.w`.
    public static final byte AMOOR_W = 84;

    /// Executes `amomin.w`.
    public static final byte AMOMIN_W = 85;

    /// Executes `amomax.w`.
    public static final byte AMOMAX_W = 86;

    /// Executes `amominu.w`.
    public static final byte AMOMINU_W = 87;

    /// Executes `amomaxu.w`.
    public static final byte AMOMAXU_W = 88;

    /// Executes `amoswap.d`.
    public static final byte AMOSWAP_D = 89;

    /// Executes `amoadd.d`.
    public static final byte AMOADD_D = 90;

    /// Executes `amoxor.d`.
    public static final byte AMOXOR_D = 91;

    /// Executes `amoand.d`.
    public static final byte AMOAND_D = 92;

    /// Executes `amoor.d`.
    public static final byte AMOOR_D = 93;

    /// Executes `amomin.d`.
    public static final byte AMOMIN_D = 94;

    /// Executes `amomax.d`.
    public static final byte AMOMAX_D = 95;

    /// Executes `amominu.d`.
    public static final byte AMOMINU_D = 96;

    /// Executes `amomaxu.d`.
    public static final byte AMOMAXU_D = 97;

    /// Prevents construction of this constants class.
    private RiscVMicroOpcode() {
    }
}
