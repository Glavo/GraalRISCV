// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.nodes;

import org.glavo.riscv.memory.MemoryLayout;
import org.glavo.riscv.parser.DecodedBlock;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Compiles decoded traces to executable trace targets.
@NotNullByDefault
final class TraceCompiler {
    private TraceCompiler() {
    }

    /// Returns the interpreter-backed trace target used until the JVM bytecode compiler is implemented.
    static ExecutableTrace compile(
            MemoryLayout memoryLayout,
            byte executionPolicy,
            DecodedBlock @Unmodifiable [] decodedBlocks,
            long @Unmodifiable [] expectedNextPcs) {
        return new RiscVMicroTraceRootNode(memoryLayout, executionPolicy, decodedBlocks, expectedNextPcs);
    }
}
