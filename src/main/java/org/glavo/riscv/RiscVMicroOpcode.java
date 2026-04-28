package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;

/// Defines the compact opcodes used by the custom RISC-V micro-bytecode interpreter.
@NotNullByDefault
final class RiscVMicroOpcode {
    /// Delegates a low-frequency or complex instruction to its semantic helper.
    static final byte EXECUTE_SEMANTICS = 0;

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

    /// Prevents construction of this constants class.
    private RiscVMicroOpcode() {
    }
}
