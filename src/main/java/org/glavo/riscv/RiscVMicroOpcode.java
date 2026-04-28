package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;

/// Defines the compact opcodes used by the custom RISC-V micro-bytecode interpreter.
@NotNullByDefault
final class RiscVMicroOpcode {
    /// Executes an operation that is not yet expanded into a dedicated micro-op body.
    static final byte EXECUTE_OPERATION = 0;

    /// Advances `pc` for no-op control instructions.
    static final byte ADVANCE_PC = 1;

    /// Executes `lui`.
    static final byte LUI = 2;

    /// Executes `auipc`.
    static final byte AUIPC = 3;

    /// Executes `jal`.
    static final byte JAL = 4;

    /// Executes `jalr`.
    static final byte JALR = 5;

    /// Executes `beq`.
    static final byte BEQ = 6;

    /// Executes `bne`.
    static final byte BNE = 7;

    /// Executes `blt`.
    static final byte BLT = 8;

    /// Executes `bge`.
    static final byte BGE = 9;

    /// Executes `bltu`.
    static final byte BLTU = 10;

    /// Executes `bgeu`.
    static final byte BGEU = 11;

    /// Executes `lb`.
    static final byte LB = 12;

    /// Executes `lh`.
    static final byte LH = 13;

    /// Executes `lw`.
    static final byte LW = 14;

    /// Executes `ld`.
    static final byte LD = 15;

    /// Executes `lbu`.
    static final byte LBU = 16;

    /// Executes `lhu`.
    static final byte LHU = 17;

    /// Executes `lwu`.
    static final byte LWU = 18;

    /// Executes `sb`.
    static final byte SB = 19;

    /// Executes `sh`.
    static final byte SH = 20;

    /// Executes `sw`.
    static final byte SW = 21;

    /// Executes `sd`.
    static final byte SD = 22;

    /// Executes `addi`.
    static final byte ADDI = 23;

    /// Executes `slti`.
    static final byte SLTI = 24;

    /// Executes `sltiu`.
    static final byte SLTIU = 25;

    /// Executes `xori`.
    static final byte XORI = 26;

    /// Executes `ori`.
    static final byte ORI = 27;

    /// Executes `andi`.
    static final byte ANDI = 28;

    /// Executes `slli`.
    static final byte SLLI = 29;

    /// Executes `srli`.
    static final byte SRLI = 30;

    /// Executes `srai`.
    static final byte SRAI = 31;

    /// Executes `addiw`.
    static final byte ADDIW = 32;

    /// Executes `slliw`.
    static final byte SLLIW = 33;

    /// Executes `srliw`.
    static final byte SRLIW = 34;

    /// Executes `sraiw`.
    static final byte SRAIW = 35;

    /// Executes `add`.
    static final byte ADD = 36;

    /// Executes `sub`.
    static final byte SUB = 37;

    /// Executes `sll`.
    static final byte SLL = 38;

    /// Executes `slt`.
    static final byte SLT = 39;

    /// Executes `sltu`.
    static final byte SLTU = 40;

    /// Executes `xor`.
    static final byte XOR = 41;

    /// Executes `srl`.
    static final byte SRL = 42;

    /// Executes `sra`.
    static final byte SRA = 43;

    /// Executes `or`.
    static final byte OR = 44;

    /// Executes `and`.
    static final byte AND = 45;

    /// Executes `addw`.
    static final byte ADDW = 46;

    /// Executes `subw`.
    static final byte SUBW = 47;

    /// Executes `sllw`.
    static final byte SLLW = 48;

    /// Executes `srlw`.
    static final byte SRLW = 49;

    /// Executes `sraw`.
    static final byte SRAW = 50;

    /// Executes `ecall`.
    static final byte ECALL = 51;

    /// Executes `ebreak`.
    static final byte EBREAK = 52;

    /// Executes `csrrw`.
    static final byte CSRRW = 53;

    /// Executes `csrrs`.
    static final byte CSRRS = 54;

    /// Executes `csrrc`.
    static final byte CSRRC = 55;

    /// Executes `csrrwi`.
    static final byte CSRRWI = 56;

    /// Executes `csrrsi`.
    static final byte CSRRSI = 57;

    /// Executes `csrrci`.
    static final byte CSRRCI = 58;

    /// Executes `mul`.
    static final byte MUL = 59;

    /// Executes `mulh`.
    static final byte MULH = 60;

    /// Executes `mulhsu`.
    static final byte MULHSU = 61;

    /// Executes `mulhu`.
    static final byte MULHU = 62;

    /// Executes `div`.
    static final byte DIV = 63;

    /// Executes `divu`.
    static final byte DIVU = 64;

    /// Executes `rem`.
    static final byte REM = 65;

    /// Executes `remu`.
    static final byte REMU = 66;

    /// Executes `mulw`.
    static final byte MULW = 67;

    /// Executes `divw`.
    static final byte DIVW = 68;

    /// Executes `divuw`.
    static final byte DIVUW = 69;

    /// Executes `remw`.
    static final byte REMW = 70;

    /// Executes `remuw`.
    static final byte REMUW = 71;

    /// Executes `flw`.
    static final byte FLW = 72;

    /// Executes `fld`.
    static final byte FLD = 73;

    /// Executes `fsw`.
    static final byte FSW = 74;

    /// Executes `fsd`.
    static final byte FSD = 75;

    /// Executes `lr.w`.
    static final byte LR_W = 76;

    /// Executes `lr.d`.
    static final byte LR_D = 77;

    /// Executes `sc.w`.
    static final byte SC_W = 78;

    /// Executes `sc.d`.
    static final byte SC_D = 79;

    /// Executes `amoswap.w`.
    static final byte AMOSWAP_W = 80;

    /// Executes `amoadd.w`.
    static final byte AMOADD_W = 81;

    /// Executes `amoxor.w`.
    static final byte AMOXOR_W = 82;

    /// Executes `amoand.w`.
    static final byte AMOAND_W = 83;

    /// Executes `amoor.w`.
    static final byte AMOOR_W = 84;

    /// Executes `amomin.w`.
    static final byte AMOMIN_W = 85;

    /// Executes `amomax.w`.
    static final byte AMOMAX_W = 86;

    /// Executes `amominu.w`.
    static final byte AMOMINU_W = 87;

    /// Executes `amomaxu.w`.
    static final byte AMOMAXU_W = 88;

    /// Executes `amoswap.d`.
    static final byte AMOSWAP_D = 89;

    /// Executes `amoadd.d`.
    static final byte AMOADD_D = 90;

    /// Executes `amoxor.d`.
    static final byte AMOXOR_D = 91;

    /// Executes `amoand.d`.
    static final byte AMOAND_D = 92;

    /// Executes `amoor.d`.
    static final byte AMOOR_D = 93;

    /// Executes `amomin.d`.
    static final byte AMOMIN_D = 94;

    /// Executes `amomax.d`.
    static final byte AMOMAX_D = 95;

    /// Executes `amominu.d`.
    static final byte AMOMINU_D = 96;

    /// Executes `amomaxu.d`.
    static final byte AMOMAXU_D = 97;

    /// Prevents construction of this constants class.
    private RiscVMicroOpcode() {
    }
}
