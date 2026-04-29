// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.RootNode;
import org.glavo.riscv.RiscVLanguage;
import org.glavo.riscv.runtime.MachineState;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Executes a hot linear sequence of decoded RISC-V basic blocks through direct calls.
@NotNullByDefault
final class RiscVMicroTraceRootNode extends RootNode {
    /// Direct calls to the block targets selected into this trace.
    @Children private final DirectCallNode @Unmodifiable [] blockCalls;

    /// Expected successor PCs between adjacent trace blocks.
    @CompilationFinal(dimensions = 1)
    private final long @Unmodifiable [] expectedNextPcs;

    /// Creates a trace root from decoded block targets and side-exit guards.
    RiscVMicroTraceRootNode(
            RiscVLanguage language,
            RootCallTarget @Unmodifiable [] blockTargets,
            long @Unmodifiable [] expectedNextPcs) {
        super(language);
        this.blockCalls = new DirectCallNode[blockTargets.length];
        for (int index = 0; index < blockTargets.length; index++) {
            this.blockCalls[index] = DirectCallNode.create(blockTargets[index]);
        }
        this.expectedNextPcs = expectedNextPcs.clone();
    }

    /// Executes the trace with the `MachineState` supplied as the first argument.
    @Override
    public Object execute(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        executeTrace(arguments, (MachineState) arguments[0]);
        return null;
    }

    /// Runs trace blocks until the trace ends or a side-exit guard fails.
    @ExplodeLoop
    private void executeTrace(Object[] arguments, MachineState state) {
        for (int index = 0; index < blockCalls.length; index++) {
            blockCalls[index].call(arguments);
            if (index < expectedNextPcs.length && state.pc() != expectedNextPcs[index]) {
                return;
            }
        }
    }
}
