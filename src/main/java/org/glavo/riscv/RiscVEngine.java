// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.nodes.RiscVRootNode;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Plain Java entry point for executing one RISC-V ELF image.
@NotNullByDefault
public final class RiscVEngine {
    private RiscVEngine() {
    }

    /// Executes the supplied ELF image or the executable selected by the runtime context.
    public static int run(byte @Unmodifiable [] sourceBytes, RiscVContext context) {
        return new RiscVRootNode(sourceBytes).execute(context);
    }
}
