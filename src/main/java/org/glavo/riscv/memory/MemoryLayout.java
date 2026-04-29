// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.memory;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Stores immutable page-layout parameters that can be captured by Truffle nodes.
///
/// @param pageSize the base page size in bytes
/// @param pageShift the right shift converting a guest address to a base-page number
/// @param pageMask the bit mask selecting the byte offset inside a base page
/// @param maxShortPageOffset the largest offset where a 16-bit access stays inside one page
/// @param maxIntPageOffset the largest offset where a 32-bit access stays inside one page
/// @param maxLongPageOffset the largest offset where a 64-bit access stays inside one page
@NotNullByDefault
public record MemoryLayout(
        int pageSize,
        int pageShift,
        long pageMask,
        long maxShortPageOffset,
        long maxIntPageOffset,
        long maxLongPageOffset) {
    /// Canonical layouts for all positive power-of-two `int` page sizes.
    private static final MemoryLayout @Unmodifiable [] CANONICAL_LAYOUTS = createCanonicalLayouts();

    /// Creates the canonical layout table indexed by page shift.
    private static MemoryLayout @Unmodifiable [] createCanonicalLayouts() {
        MemoryLayout[] layouts = new MemoryLayout[Integer.SIZE - 1];
        for (int shift = 0; shift < layouts.length; shift++) {
            int pageSize = 1 << shift;
            layouts[shift] = new MemoryLayout(
                    pageSize,
                    shift,
                    pageSize - 1L,
                    pageSize - Short.BYTES,
                    pageSize - Integer.BYTES,
                    pageSize - Long.BYTES);
        }
        return layouts;
    }

    /// Creates a layout from a validated power-of-two base page size.
    public static MemoryLayout fromPageSize(int pageSize) {
        int shift = Integer.numberOfTrailingZeros(pageSize);
        if (shift < CANONICAL_LAYOUTS.length && (1 << shift) == pageSize) {
            return CANONICAL_LAYOUTS[shift];
        }

        throw new IllegalArgumentException("Page size must be a positive power of two int: " + pageSize);
    }

    /// Returns the guest page number containing the supplied address.
    public long pageNumber(long address) {
        return address >>> pageShift;
    }

    /// Returns the byte offset inside the guest page containing the supplied address.
    public long pageOffset(long address) {
        return address & pageMask;
    }

    /// Returns the exclusive end address of the page containing the supplied address.
    public long pageEnd(long address) {
        return (address & ~pageMask) + pageSize;
    }

    /// Returns true when a 16-bit scalar access stays within one base page.
    public boolean isSinglePageShortAccess(long address) {
        return pageOffset(address) <= maxShortPageOffset;
    }

    /// Returns true when a 32-bit scalar access stays within one base page.
    public boolean isSinglePageIntAccess(long address) {
        return pageOffset(address) <= maxIntPageOffset;
    }

    /// Returns true when a 64-bit scalar access stays within one base page.
    public boolean isSinglePageLongAccess(long address) {
        return pageOffset(address) <= maxLongPageOffset;
    }

    /// Returns true when a 16-bit scalar access at a page-relative offset stays within one base page.
    public boolean isSinglePageShortOffset(long pageOffset) {
        return pageOffset <= maxShortPageOffset;
    }

    /// Returns true when a 32-bit scalar access at a page-relative offset stays within one base page.
    public boolean isSinglePageIntOffset(long pageOffset) {
        return pageOffset <= maxIntPageOffset;
    }

    /// Returns true when a 64-bit scalar access at a page-relative offset stays within one base page.
    public boolean isSinglePageLongOffset(long pageOffset) {
        return pageOffset <= maxLongPageOffset;
    }
}
