// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.parser;

import org.jetbrains.annotations.NotNullByDefault;

/// The decoded operation set implemented by the simulator.
@NotNullByDefault
public enum RiscVOperation {
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

