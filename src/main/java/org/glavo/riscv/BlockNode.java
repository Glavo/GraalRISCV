package org.glavo.riscv;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import org.jetbrains.annotations.NotNullByDefault;

/// Executes a decoded straight-line guest basic block.
@NotNullByDefault
public final class BlockNode extends Node {
    /// The decoded instruction nodes in execution order.
    @Children
    @CompilationFinal(dimensions = 1)
    private final InstructionNode[] instructions;

    /// Creates a decoded basic block.
    public BlockNode(InstructionNode[] instructions) {
        this.instructions = instructions.clone();
    }

    /// Executes every instruction in this decoded block.
    @ExplodeLoop
    public void execute(MachineState state) {
        for (InstructionNode instruction : instructions) {
            instruction.executeInBlock(state);
        }
        if (!instructions[instructions.length - 1].isTerminator()) {
            state.setPc(instructions[instructions.length - 1].nextAddress);
        }
    }

    /// Executes every instruction in this decoded block using frame-backed integer registers.
    @ExplodeLoop
    public void execute(VirtualFrame frame, MachineState state) {
        for (InstructionNode instruction : instructions) {
            instruction.executeInBlock(frame, state);
        }
        if (!instructions[instructions.length - 1].isTerminator()) {
            state.setPc(instructions[instructions.length - 1].nextAddress);
        }
    }
}
