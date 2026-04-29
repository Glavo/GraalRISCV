// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.memory;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Stores mutable software TLB state for one Truffle context and host thread.
@NotNullByDefault
public final class MappedRegionCache {
    /// The number of cached data pages.
    private static final int DATA_CACHE_SIZE = 64;

    /// The number of cached instruction pages.
    private static final int INSTRUCTION_CACHE_SIZE = 16;

    /// The entry offset storing the cached guest page number.
    private static final int PAGE_NUMBER_OFFSET = 0;

    /// The entry offset storing the inclusive valid guest address start.
    private static final int RANGE_START_OFFSET = 1;

    /// The entry offset storing the exclusive valid guest address end.
    private static final int RANGE_END_OFFSET = 2;

    /// The entry offset storing the access protection mask.
    private static final int PROTECTION_OFFSET = 3;

    /// The entry offset storing the memory generation.
    private static final int GENERATION_OFFSET = 4;

    /// The number of scalar metadata words stored for one direct-mapped cache entry.
    private static final int ENTRY_LONG_COUNT = 5;

    /// Packed direct-mapped cached data page metadata.
    private final long[] dataEntries = new long[DATA_CACHE_SIZE * ENTRY_LONG_COUNT];

    /// Direct-mapped cached committed data pages.
    private final @Nullable MemoryPage[] dataPages = new MemoryPage[DATA_CACHE_SIZE];

    /// Packed direct-mapped cached instruction page metadata.
    private final long[] instructionEntries = new long[INSTRUCTION_CACHE_SIZE * ENTRY_LONG_COUNT];

    /// Direct-mapped cached committed instruction pages.
    private final @Nullable MemoryPage[] instructionPages = new MemoryPage[INSTRUCTION_CACHE_SIZE];

    /// Creates an empty software TLB.
    public MappedRegionCache() {
    }

    /// Returns a cached page, or null when the lookup misses.
    @Nullable MemoryPage page(
            long pageNumber,
            long address,
            int length,
            long requiredProtection,
            long generation,
            boolean instruction,
            @Nullable MemoryAccess access) {
        return instruction
                ? page(
                        pageNumber,
                        address,
                        length,
                        requiredProtection,
                        generation,
                        instructionEntries,
                        instructionPages,
                        null)
                : page(
                        pageNumber,
                        address,
                        length,
                        requiredProtection,
                        generation,
                        dataEntries,
                        dataPages,
                        access);
    }

    /// Stores a cached committed page.
    void setPage(
            long pageNumber,
            long rangeStart,
            long rangeEnd,
            long protection,
            long generation,
            MemoryPage page,
            boolean instruction) {
        if (instruction) {
            setPage(
                    pageNumber,
                    rangeStart,
                    rangeEnd,
                    protection,
                    generation,
                    page,
                    instructionEntries,
                    instructionPages);
        } else {
            setPage(
                    pageNumber,
                    rangeStart,
                    rangeEnd,
                    protection,
                    generation,
                    page,
                    dataEntries,
                    dataPages);
        }
    }

    /// Returns a cached page from a direct-mapped page cache.
    private static @Nullable MemoryPage page(
            long pageNumber,
            long address,
            int length,
            long requiredProtection,
            long generation,
            long[] entries,
            @Nullable MemoryPage[] pages,
            @Nullable MemoryAccess access) {
        int index = cacheSlot(pageNumber, pages.length);
        @Nullable MemoryPage page = pages[index];
        int entryOffset = entryOffset(index);
        if (page == null || entries[entryOffset + PAGE_NUMBER_OFFSET] != pageNumber) {
            return null;
        }

        long entryGeneration = entries[entryOffset + GENERATION_OFFSET];
        long rangeStart = entries[entryOffset + RANGE_START_OFFSET];
        long rangeEnd = entries[entryOffset + RANGE_END_OFFSET];
        long protection = entries[entryOffset + PROTECTION_OFFSET];
        if (entryGeneration == generation
                && address >= rangeStart
                && length <= rangeEnd - address
                && (protection & requiredProtection) == requiredProtection) {
            if (access != null) {
                access.setDataPage(
                        pageNumber,
                        rangeStart,
                        rangeEnd,
                        protection,
                        entryGeneration,
                        page);
            }
            return page;
        }
        return null;
    }

    /// Stores a page in a direct-mapped page cache.
    private static void setPage(
            long pageNumber,
            long rangeStart,
            long rangeEnd,
            long protection,
            long generation,
            MemoryPage page,
            long[] entries,
            @Nullable MemoryPage[] pages) {
        int index = cacheSlot(pageNumber, pages.length);
        int entryOffset = entryOffset(index);
        entries[entryOffset + PAGE_NUMBER_OFFSET] = pageNumber;
        entries[entryOffset + RANGE_START_OFFSET] = rangeStart;
        entries[entryOffset + RANGE_END_OFFSET] = rangeEnd;
        entries[entryOffset + PROTECTION_OFFSET] = protection;
        entries[entryOffset + GENERATION_OFFSET] = generation;
        pages[index] = page;
    }

    /// Returns the first metadata word offset for a direct-mapped cache slot.
    private static int entryOffset(int index) {
        return index * ENTRY_LONG_COUNT;
    }

    /// Hashes a page number into a direct-mapped cache slot.
    private static int cacheSlot(long pageNumber, int length) {
        long hash = pageNumber ^ (pageNumber >>> 6) ^ (pageNumber >>> 12);
        return (int) hash & (length - 1);
    }
}
