package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;

/// Executes decoded blocks in unit tests without using the production dispatch cache.
@NotNullByDefault
final class DecodedBlockTestExecutor {
    /// Prevents construction of this utility class.
    private DecodedBlockTestExecutor() {
    }

    /// Executes every instruction in the decoded block and returns the materialized PC.
    static long execute(MachineState state, DecodedBlock block) {
        for (DecodedInstruction instruction : block.instructions()) {
            RiscVInstructionSemantics.execute(state, instruction);
        }
        return state.pc();
    }
}
