// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.exception;

import org.jetbrains.annotations.NotNullByDefault;

/// Reports a guest memory access fault with its faulting address and Linux signal code.
@NotNullByDefault
public final class MemoryAccessException extends RiscVException {
    private final long address;
    private final long length;
    private final int signalCode;

    /// Creates a guest memory access fault.
    public MemoryAccessException(String message, long address, long length, int signalCode) {
        super(message);
        this.address = address;
        this.length = length;
        this.signalCode = signalCode;
    }

    /// Returns the faulting guest address.
    public long address() {
        return address;
    }

    /// Returns the attempted access byte length.
    public long length() {
        return length;
    }

    /// Returns the Linux `SIGSEGV` signal code for this fault.
    public int signalCode() {
        return signalCode;
    }
}
