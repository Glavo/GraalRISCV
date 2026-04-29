package org.glavo.riscv.runtime;

import org.jetbrains.annotations.NotNullByDefault;

/// Runs one guest thread state until that thread exits or requests process termination.
@FunctionalInterface
@NotNullByDefault
public interface GuestThreadRunner {
    /// Executes the supplied guest thread state.
    void runGuestThread(MachineState state);
}
