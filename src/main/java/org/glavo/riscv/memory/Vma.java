// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.memory;

import org.jetbrains.annotations.NotNullByDefault;

/// Describes a guest virtual memory area.
///
/// @param address the inclusive guest start address of the VMA
/// @param endAddress the exclusive guest end address of the VMA
/// @param huge whether this VMA reserves MAP_HUGETLB pages
/// @param hugePagePreference the transparent huge-page advice recorded for this VMA
/// @param protection the supported guest access-protection flags for this VMA
@NotNullByDefault
record Vma(
        long address,
        long endAddress,
        boolean huge,
        byte hugePagePreference,
        long protection) {
    /// No transparent huge-page preference has been recorded for a VMA.
    static final byte HUGE_PAGE_PREFERENCE_DEFAULT = 0;

    /// The VMA prefers transparent huge pages.
    static final byte HUGE_PAGE_PREFERENCE_ENABLED = 1;

    /// The VMA opts out of transparent huge pages.
    static final byte HUGE_PAGE_PREFERENCE_DISABLED = 2;
}
