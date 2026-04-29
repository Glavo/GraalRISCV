// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.exception;

import org.jetbrains.annotations.NotNullByDefault;

/// Reports an invalid guest program, unsupported guest operation, or simulator configuration error.
@NotNullByDefault
public class RiscVException extends RuntimeException {
    /// Creates an exception with a diagnostic message.
    public RiscVException(String message) {
        super(message);
    }

    /// Creates an exception with a diagnostic message and a lower-level cause.
    public RiscVException(String message, Throwable cause) {
        super(message, cause);
    }
}
