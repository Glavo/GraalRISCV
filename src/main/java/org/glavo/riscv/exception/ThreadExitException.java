// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.exception;

import org.jetbrains.annotations.NotNullByDefault;

/// Transfers control from guest execution to the scheduler when one guest thread exits.
@NotNullByDefault
public final class ThreadExitException extends RuntimeException {
    /// The exit code supplied by the guest thread.
    private final long exitCode;

    /// Creates a thread-exit transfer with the supplied guest exit code.
    public ThreadExitException(long exitCode) {
        super(null, null, false, false);
        this.exitCode = exitCode;
    }

    /// Returns the guest thread exit code.
    public long exitCode() {
        return exitCode;
    }
}
