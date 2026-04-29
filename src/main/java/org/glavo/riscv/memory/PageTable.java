// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.memory;

import org.glavo.riscv.exception.RiscVException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/// Stores committed pages in a lazily allocated radix page table.
@NotNullByDefault
final class PageTable {
    /// Number of guest page-number bits consumed at each radix level.
    private static final int LEVEL_BITS = 9;

    /// Number of entries in every radix node.
    private static final int LEVEL_SIZE = 1 << LEVEL_BITS;

    /// Bit mask selecting an entry inside one radix node.
    private static final int LEVEL_MASK = LEVEL_SIZE - 1;

    /// VarHandle used to publish and read radix node entries.
    private static final VarHandle ENTRY_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);

    /// The root radix array.
    private final Object[] root = new Object[LEVEL_SIZE];

    /// The bit shift used by the root radix level for this memory window.
    private final int rootShift;

    /// Creates a page table sized for the configured guest virtual memory window.
    PageTable(int pageShift, long endAddress) {
        long maximumPageNumber = endAddress == 0 ? 0 : (endAddress - 1L) >>> pageShift;
        int pageNumberBits = Long.SIZE - Long.numberOfLeadingZeros(maximumPageNumber);
        int levels = Math.max(1, (pageNumberBits + LEVEL_BITS - 1) / LEVEL_BITS);
        this.rootShift = (levels - 1) * LEVEL_BITS;
    }

    /// Returns the committed page for a guest page number, or null when absent.
    @Nullable MemoryPage get(long pageNumber) {
        Object[] node = root;
        if (!fitsTable(pageNumber)) {
            return null;
        }
        for (int shift = rootShift; shift > 0; shift -= LEVEL_BITS) {
            @Nullable Object child = ENTRY_HANDLE.getAcquire(node, levelIndex(pageNumber, shift));
            if (child == null) {
                return null;
            }
            node = (Object[]) child;
        }
        return (MemoryPage) ENTRY_HANDLE.getAcquire(node, levelIndex(pageNumber, 0));
    }

    /// Stores a committed page for a guest page number.
    void put(long pageNumber, MemoryPage page) {
        Object[] node = root;
        if (!fitsTable(pageNumber)) {
            throw new RiscVException("Guest page number is outside the configured page table: " + pageNumber);
        }
        for (int shift = rootShift; shift > 0; shift -= LEVEL_BITS) {
            int index = levelIndex(pageNumber, shift);
            @Nullable Object child = ENTRY_HANDLE.getAcquire(node, index);
            if (child == null) {
                child = new Object[LEVEL_SIZE];
                ENTRY_HANDLE.setRelease(node, index, child);
            }
            node = (Object[]) child;
        }
        ENTRY_HANDLE.setRelease(node, levelIndex(pageNumber, 0), page);
    }

    /// Removes, closes, and counts pages with guest page numbers in the supplied half-open range.
    long removeRange(long startPageNumber, long endPageNumber) {
        if (startPageNumber >= endPageNumber) {
            return 0;
        }
        return removeRange(root, rootShift, 0, startPageNumber, endPageNumber);
    }

    /// Closes every committed page and resets the table to its initial empty state.
    void closeAndClear() {
        closeAndClear(root, rootShift);
    }

    /// Returns true when a page number is representable in this table's root level.
    private boolean fitsTable(long pageNumber) {
        return (pageNumber >>> (rootShift + LEVEL_BITS)) == 0;
    }

    /// Removes pages from a radix subtree intersecting the supplied page range.
    private static long removeRange(
            Object[] node,
            int shift,
            long nodeBase,
            long startPageNumber,
            long endPageNumber) {
        if (shift == 0) {
            int startIndex = leafStartIndex(nodeBase, startPageNumber);
            int endIndex = leafEndIndex(nodeBase, endPageNumber);
            long removedPages = 0;
            for (int index = startIndex; index < endIndex; index++) {
                @Nullable Object entry = ENTRY_HANDLE.getAndSet(node, index, null);
                if (entry instanceof MemoryPage page) {
                    page.close();
                    removedPages++;
                }
            }
            return removedPages;
        }

        int startIndex = subtreeStartIndex(shift, nodeBase, startPageNumber);
        int endIndex = subtreeEndIndex(shift, nodeBase, endPageNumber);
        long removedPages = 0;
        for (int index = startIndex; index < endIndex; index++) {
            @Nullable Object entry = ENTRY_HANDLE.getAcquire(node, index);
            if (!(entry instanceof Object[] child)) {
                continue;
            }
            long childBase = nodeBase + ((long) index << shift);
            removedPages += removeRange(child, shift - LEVEL_BITS, childBase, startPageNumber, endPageNumber);
            if (isEmpty(child)) {
                ENTRY_HANDLE.setRelease(node, index, null);
            }
        }
        return removedPages;
    }

    /// Closes and clears every committed page in a radix subtree.
    private static void closeAndClear(Object[] node, int shift) {
        for (int index = 0; index < LEVEL_SIZE; index++) {
            @Nullable Object entry = ENTRY_HANDLE.getAndSet(node, index, null);
            if (entry == null) {
                continue;
            }
            if (shift == 0) {
                if (entry instanceof MemoryPage page) {
                    page.close();
                }
            } else {
                closeAndClear((Object[]) entry, shift - LEVEL_BITS);
            }
        }
    }

    /// Returns true when a radix array contains no published entries.
    private static boolean isEmpty(Object[] node) {
        for (int index = 0; index < LEVEL_SIZE; index++) {
            if (ENTRY_HANDLE.getAcquire(node, index) != null) {
                return false;
            }
        }
        return true;
    }

    /// Returns the radix index at the supplied page-number bit shift.
    private static int levelIndex(long pageNumber, int shift) {
        return (int) (pageNumber >>> shift) & LEVEL_MASK;
    }

    /// Returns the first leaf index that can overlap a page range.
    private static int leafStartIndex(long nodeBase, long startPageNumber) {
        return (int) Math.max(0L, startPageNumber - nodeBase);
    }

    /// Returns the exclusive leaf index that can overlap a page range.
    private static int leafEndIndex(long nodeBase, long endPageNumber) {
        return (int) Math.min(LEVEL_SIZE, endPageNumber - nodeBase);
    }

    /// Returns the first child index that can overlap a page range.
    private static int subtreeStartIndex(int shift, long nodeBase, long startPageNumber) {
        long offset = Math.max(0L, startPageNumber - nodeBase);
        return (int) Math.min(LEVEL_SIZE, offset >>> shift);
    }

    /// Returns the exclusive child index that can overlap a page range.
    private static int subtreeEndIndex(int shift, long nodeBase, long endPageNumber) {
        long offset = Math.max(0L, endPageNumber - nodeBase);
        if (offset == 0) {
            return 0;
        }
        long index = ((offset - 1L) >>> shift) + 1L;
        return (int) Math.min(LEVEL_SIZE, index);
    }
}
