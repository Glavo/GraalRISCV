// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.nodes;

import org.glavo.riscv.memory.MemoryAccess;
import org.glavo.riscv.runtime.RiscVThreadState;
import org.jetbrains.annotations.NotNullByDefault;

/// Executes one decoded guest basic block.
@NotNullByDefault
interface ExecutableBlock {
    /// Executes the block with the supplied machine state and memory access facade.
    void execute(RiscVThreadState state, MemoryAccess access);
}
