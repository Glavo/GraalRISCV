// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.parser.ElfOperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;

/// Describes the guest syscall ABI used by a loaded process.
@NotNullByDefault
public enum GuestAbi {
    /// Linux RISC-V user-mode syscall numbers and return convention.
    LINUX,

    /// FreeBSD RISC-V user-mode syscall numbers and return convention.
    FREEBSD;

    /// Maps an ELF operating-system ABI to the guest syscall ABI.
    public static GuestAbi fromElfOperatingSystem(ElfOperatingSystem operatingSystem) {
        return switch (operatingSystem) {
            case LINUX_COMPATIBLE -> LINUX;
            case FREEBSD -> FREEBSD;
        };
    }
}
