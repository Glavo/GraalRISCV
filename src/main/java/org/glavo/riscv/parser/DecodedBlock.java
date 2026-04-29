package org.glavo.riscv.parser;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Stores immutable decode results for one straight-line guest basic block.
///
/// @param startPc the guest address used to start decoding this block
/// @param instructions the decoded instructions in execution order
/// @param fallThroughPc the next sequential PC after the final decoded instruction
/// @param endsWithTerminator whether the final instruction transfers control out of the block
@NotNullByDefault
public record DecodedBlock(
        long startPc,
        DecodedInstruction @Unmodifiable [] instructions,
        long fallThroughPc,
        boolean endsWithTerminator) {
    /// Creates a decoded block with an immutable instruction snapshot.
    public DecodedBlock {
        instructions = instructions.clone();
    }

    /// Returns a defensive copy of the decoded instruction array.
    @Override
    public DecodedInstruction @Unmodifiable [] instructions() {
        return instructions.clone();
    }
}
