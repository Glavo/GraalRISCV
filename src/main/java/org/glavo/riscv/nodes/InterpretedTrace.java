// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.nodes;

import org.glavo.riscv.memory.MemoryAccess;
import org.glavo.riscv.memory.MemoryLayout;
import org.glavo.riscv.parser.DecodedBlock;
import org.glavo.riscv.runtime.RiscVThreadState;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Executes a hot linear sequence of decoded RISC-V basic blocks through embedded block nodes.
@NotNullByDefault
final class InterpretedTrace implements ExecutableTrace {
    /// Block nodes selected into this trace.
    private final RiscVMicroBlockNode @Unmodifiable [] blocks;

    /// Expected successor PCs between adjacent trace blocks.
    private final long @Unmodifiable [] expectedNextPcs;

    /// Creates an interpreter trace from decoded blocks, side-exit guards, and a stable execution policy.
    InterpretedTrace(
            MemoryLayout memoryLayout,
            byte executionPolicy,
            DecodedBlock @Unmodifiable [] decodedBlocks,
            long @Unmodifiable [] expectedNextPcs) {
        this.blocks = new RiscVMicroBlockNode[decodedBlocks.length];
        for (int index = 0; index < decodedBlocks.length; index++) {
            this.blocks[index] = RiscVMicroBlockCompiler.compileNode(decodedBlocks[index], memoryLayout, executionPolicy);
        }
        this.expectedNextPcs = expectedNextPcs.clone();
    }

    /// Executes the trace with the supplied machine state and memory access facade.
    @Override
    public void execute(RiscVThreadState state, MemoryAccess access) {
        executeTrace(state, access);
    }

    /// Runs trace blocks until the trace ends or a side-exit guard fails.
    private void executeTrace(RiscVThreadState state, MemoryAccess access) {
        for (int index = 0; index < blocks.length; index++) {
            blocks[index].execute(state, access);
            if (index < expectedNextPcs.length && state.pc() != expectedNextPcs[index]) {
                return;
            }
        }
    }
}
