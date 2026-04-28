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

    /// Whether the last instruction transfers control out of this block.
    @CompilationFinal
    private final boolean endsWithTerminator;

    /// The number of instructions before the final terminator, or all instructions for fall-through blocks.
    @CompilationFinal
    private final int straightLineInstructionCount;

    /// The next sequential PC for fall-through blocks.
    @CompilationFinal
    private final long fallThroughPc;

    /// Creates a decoded basic block.
    public BlockNode(InstructionNode[] instructions) {
        this.instructions = instructions.clone();
        this.instructionCount = instructions.length;
        this.endsWithTerminator = instructions[instructions.length - 1].isTerminator();
        this.straightLineInstructionCount = endsWithTerminator ? instructions.length - 1 : instructions.length;
        this.fallThroughPc = instructions[instructions.length - 1].nextAddress;

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
        boolean blockRetired = state.canRetireBlock() && !requiresPreciseInstructionRetirement;
        if (blockRetired) {
            state.retireBlock(instructionCount);
            for (int index = 0; index < straightLineInstructionCount; index++) {
                instructions[index].executeInRetiredBlock(state);
            }
        } else {
            for (int index = 0; index < straightLineInstructionCount; index++) {
                instructions[index].executeInBlock(state);
            }
        }

        if (endsWithTerminator) {
            InstructionNode terminator = instructions[instructionCount - 1];
            long nextPc = blockRetired
                    ? terminator.executeTerminatorInRetiredBlock(state)
                    : terminator.executeTerminatorInBlock(state);
            state.setPc(nextPc);
            return nextPc;
        }

        long nextPc = fallThroughPc;
        state.setPc(nextPc);
        return nextPc;
    }

    /// Executes every instruction in this decoded block using frame-backed integer registers.
    @ExplodeLoop
    public long execute(VirtualFrame frame, MachineState state) {
        boolean blockRetired = state.canRetireBlock() && !requiresPreciseInstructionRetirement;
        if (blockRetired) {
            state.retireBlock(instructionCount);
            for (int index = 0; index < straightLineInstructionCount; index++) {
                instructions[index].executeInRetiredBlock(frame, state);
            }
        } else {
            for (int index = 0; index < straightLineInstructionCount; index++) {
                instructions[index].executeInBlock(frame, state);
            }
        }

        if (endsWithTerminator) {
            InstructionNode terminator = instructions[instructionCount - 1];
            return blockRetired
                    ? terminator.executeTerminatorInRetiredBlock(frame, state)
                    : terminator.executeTerminatorInBlock(frame, state);
        }

        return fallThroughPc;
    }
}
