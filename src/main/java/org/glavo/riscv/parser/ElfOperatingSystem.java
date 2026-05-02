// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.parser;

import org.jetbrains.annotations.NotNullByDefault;

/// Describes the guest operating-system ABI requested by an ELF image.
@NotNullByDefault
public enum ElfOperatingSystem {
    /// A System V or GNU/Linux-compatible ELF image.
    LINUX_COMPATIBLE,

    /// A FreeBSD ELF image.
    FREEBSD;

    /// The ELF `EI_OSABI` value used by System V images.
    private static final int ELFOSABI_NONE = 0;

    /// The ELF `EI_OSABI` value used by GNU/Linux images.
    private static final int ELFOSABI_LINUX = 3;

    /// The ELF `EI_OSABI` value used by FreeBSD images.
    private static final int ELFOSABI_FREEBSD = 9;

    /// Maps an ELF `EI_OSABI` byte to the runtime operating-system ABI.
    public static ElfOperatingSystem fromOsAbi(int osAbi) {
        return switch (osAbi) {
            case ELFOSABI_NONE, ELFOSABI_LINUX -> LINUX_COMPATIBLE;
            case ELFOSABI_FREEBSD -> FREEBSD;
            default -> LINUX_COMPATIBLE;
        };
    }
}
