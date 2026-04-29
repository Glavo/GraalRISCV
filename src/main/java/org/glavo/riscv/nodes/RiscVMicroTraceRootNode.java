// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.RootNode;
import org.glavo.riscv.RiscVLanguage;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.parser.DecodedBlock;
import org.glavo.riscv.runtime.MachineState;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Executes a hot linear sequence of decoded RISC-V basic blocks through embedded block nodes.
@NotNullByDefault
final class RiscVMicroTraceRootNode extends RootNode {
    /// Block nodes selected into this trace.
    @Children private final RiscVMicroBlockNode @Unmodifiable [] blocks;

    /// Expected successor PCs between adjacent trace blocks.
    @CompilationFinal(dimensions = 1)
    private final long @Unmodifiable [] expectedNextPcs;

    /// Creates a trace root from decoded blocks and side-exit guards.
    RiscVMicroTraceRootNode(
            RiscVLanguage language,
            DecodedBlock @Unmodifiable [] decodedBlocks,
            long @Unmodifiable [] expectedNextPcs) {
        super(language);
        this.blocks = new RiscVMicroBlockNode[decodedBlocks.length];
        for (int index = 0; index < decodedBlocks.length; index++) {
            this.blocks[index] = RiscVMicroBlockCompiler.compileNode(decodedBlocks[index]);
        }
        this.expectedNextPcs = expectedNextPcs.clone();
    }

    /// Executes the trace with the `MachineState` and `Memory.Access` supplied as block-call arguments.
    @Override
    public Object execute(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        executeTrace((MachineState) arguments[0], (Memory.Access) arguments[1]);
        return null;
    }

    /// Runs trace blocks until the trace ends or a side-exit guard fails.
    @ExplodeLoop
    private void executeTrace(MachineState state, Memory.Access access) {
        for (int index = 0; index < blocks.length; index++) {
            blocks[index].execute(state, access);
            if (index < expectedNextPcs.length && state.pc() != expectedNextPcs[index]) {
                return;
            }
        }
    }
}
