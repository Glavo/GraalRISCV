// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.exception;

import org.jetbrains.annotations.NotNullByDefault;

/// Transfers control from a completed `execve` syscall to the guest dispatch loop.
@NotNullByDefault
public final class ProcessImageReplacedException extends RuntimeException {
    /// Creates a process-image replacement transfer.
    public ProcessImageReplacedException() {
        super(null, null, false, false);
    }
}
