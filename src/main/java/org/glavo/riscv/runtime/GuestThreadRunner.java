// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.memory.Memory;
import org.jetbrains.annotations.NotNullByDefault;

/// Runs one guest thread state until that thread exits or requests process termination.
@FunctionalInterface
@NotNullByDefault
public interface GuestThreadRunner {
    /// Executes the supplied guest thread state with its selected address space.
    void runGuestThread(Memory memory, RiscVThreadState state);
}
