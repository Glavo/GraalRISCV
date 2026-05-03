// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.exception;

import org.jetbrains.annotations.NotNullByDefault;

/// Reports a decoded illegal guest instruction with its faulting address and raw bits.
@NotNullByDefault
public final class IllegalInstructionException extends RiscVException {
    private final long address;
    private final int raw;

    /// Creates an illegal-instruction exception.
    public IllegalInstructionException(long address, int raw) {
        super("Illegal instruction at 0x"
                + Long.toUnsignedString(address, 16)
                + ": 0x"
                + Integer.toUnsignedString(raw, 16));
        this.address = address;
        this.raw = raw;
    }

    /// Returns the faulting guest instruction address.
    public long address() {
        return address;
    }

    /// Returns the raw 32-bit instruction bits.
    public int raw() {
        return raw;
    }
}
