package org.glavo.riscv.parser;

import org.jetbrains.annotations.NotNullByDefault;

/// Stores the immutable decode result for one RV64GC guest instruction.
///
/// @param address the guest address of the instruction
/// @param raw the original 16-bit or 32-bit little-endian instruction bits
/// @param length the instruction length in bytes
/// @param operation the canonical decoded operation
/// @param rd the destination register index, or zero when unused
/// @param rs1 the first source register index, or zero when unused
/// @param rs2 the second source register index, or zero when unused
/// @param immediate the decoded immediate or operation-specific packed metadata
/// @param terminator whether the instruction ends the current basic block
@NotNullByDefault
public record DecodedInstruction(
        long address,
        int raw,
        int length,
        RiscVOperation operation,
        int rd,
        int rs1,
        int rs2,
        long immediate,
        boolean terminator) {
    /// Returns the sequential guest address following this instruction.
    public long nextAddress() {
        return address + length;
    }
}
