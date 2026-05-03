// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.exception;

import org.jetbrains.annotations.NotNullByDefault;

/// Transfers control from guest execution to the host runner with a guest exit code.
@NotNullByDefault
public final class ProgramExitException extends RuntimeException {
    /// The exit code produced by the guest program.
    private final long exitCode;

    /// Creates an exit transfer with the supplied guest exit code.
    public ProgramExitException(long exitCode) {
        super(null, null, false, false);
        this.exitCode = exitCode;
    }

    /// Returns the guest exit code.
    public long exitCode() {
        return exitCode;
    }
}
