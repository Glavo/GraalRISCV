package org.glavo.riscv;

import com.oracle.truffle.api.nodes.Node;
import org.jetbrains.annotations.NotNullByDefault;

/// Executes a decoded straight-line guest basic block.
@NotNullByDefault
public final class BlockNode extends Node {
    /// The decoded instruction nodes in execution order.
    @Children
    private final InstructionNode[] instructions;

    /// Creates a decoded basic block.
    public BlockNode(InstructionNode[] instructions) {
        this.instructions = instructions.clone();
    }

    /// Executes this block until it reaches the block terminator.
    public void execute(MachineState state) {
        for (InstructionNode instruction : instructions) {
            instruction.execute(state);
            if (instruction.isTerminator()) {
                return;
            }
        }
    }
}
