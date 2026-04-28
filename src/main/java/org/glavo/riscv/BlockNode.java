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

    /// The number of instructions in this decoded block.
    @CompilationFinal
    private final int instructionCount;

    /// Whether any instruction needs exact per-instruction retirement.
    @CompilationFinal
    private final boolean requiresPreciseInstructionRetirement;

    /// Creates a decoded basic block.
    public BlockNode(InstructionNode[] instructions) {
        this.instructions = instructions.clone();
        this.instructionCount = instructions.length;

        boolean preciseRetirement = false;
        for (InstructionNode instruction : instructions) {
            if (instruction.requiresPreciseInstructionRetirement()) {
                preciseRetirement = true;
                break;
            }
        }
        this.requiresPreciseInstructionRetirement = preciseRetirement;
    }

    /// Executes every instruction in this decoded block.
    @ExplodeLoop
    public long execute(MachineState state) {
        if (state.canRetireBlock() && !requiresPreciseInstructionRetirement) {
            state.retireBlock(instructionCount);
            for (InstructionNode instruction : instructions) {
                instruction.executeInRetiredBlock(state);
            }
        } else {
            for (InstructionNode instruction : instructions) {
                instruction.executeInBlock(state);
            }
        }

        long nextPc;
        if (!instructions[instructions.length - 1].isTerminator()) {
            nextPc = instructions[instructions.length - 1].nextAddress;
            state.setPc(nextPc);
        } else {
            nextPc = state.pc();
        }
        return nextPc;
    }

    /// Executes every instruction in this decoded block using frame-backed integer registers.
    @ExplodeLoop
    public long execute(VirtualFrame frame, MachineState state) {
        if (state.canRetireBlock() && !requiresPreciseInstructionRetirement) {
            state.retireBlock(instructionCount);
            for (InstructionNode instruction : instructions) {
                instruction.executeInRetiredBlock(frame, state);
            }
        } else {
            for (InstructionNode instruction : instructions) {
                instruction.executeInBlock(frame, state);
            }
        }

        long nextPc;
        if (!instructions[instructions.length - 1].isTerminator()) {
            nextPc = instructions[instructions.length - 1].nextAddress;
            state.setPc(nextPc);
        } else {
            nextPc = state.pc();
        }
        return nextPc;
    }
}
