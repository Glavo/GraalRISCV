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

    /// Direct-mapped cached data page numbers.
    private final long[] dataPageNumbers = new long[DATA_CACHE_SIZE];

    /// Inclusive valid guest address starts for direct-mapped cached data pages.
    private final long[] dataRangeStarts = new long[DATA_CACHE_SIZE];

    /// Exclusive valid guest address ends for direct-mapped cached data pages.
    private final long[] dataRangeEnds = new long[DATA_CACHE_SIZE];

    /// Access protections associated with direct-mapped cached data pages.
    private final long[] dataProtections = new long[DATA_CACHE_SIZE];

    /// Memory generations associated with direct-mapped cached data pages.
    private final long[] dataGenerations = new long[DATA_CACHE_SIZE];

    /// Direct-mapped cached committed data pages.
    private final @Nullable MemoryPage[] dataPages = new MemoryPage[DATA_CACHE_SIZE];

    /// Direct-mapped cached instruction page numbers.
    private final long[] instructionPageNumbers = new long[INSTRUCTION_CACHE_SIZE];

    /// Inclusive valid guest address starts for direct-mapped cached instruction pages.
    private final long[] instructionRangeStarts = new long[INSTRUCTION_CACHE_SIZE];

    /// Exclusive valid guest address ends for direct-mapped cached instruction pages.
    private final long[] instructionRangeEnds = new long[INSTRUCTION_CACHE_SIZE];

    /// Access protections associated with direct-mapped cached instruction pages.
    private final long[] instructionProtections = new long[INSTRUCTION_CACHE_SIZE];

    /// Memory generations associated with direct-mapped cached instruction pages.
    private final long[] instructionGenerations = new long[INSTRUCTION_CACHE_SIZE];

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
                        instructionPageNumbers,
                        instructionRangeStarts,
                        instructionRangeEnds,
                        instructionProtections,
                        instructionGenerations,
                        instructionPages,
                        null)
                : page(
                        pageNumber,
                        address,
                        length,
                        requiredProtection,
                        generation,
                        dataPageNumbers,
                        dataRangeStarts,
                        dataRangeEnds,
                        dataProtections,
                        dataGenerations,
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
                    instructionPageNumbers,
                    instructionRangeStarts,
                    instructionRangeEnds,
                    instructionProtections,
                    instructionGenerations,
                    instructionPages);
        } else {
            setPage(
                    pageNumber,
                    rangeStart,
                    rangeEnd,
                    protection,
                    generation,
                    page,
                    dataPageNumbers,
                    dataRangeStarts,
                    dataRangeEnds,
                    dataProtections,
                    dataGenerations,
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
            long[] pageNumbers,
            long[] rangeStarts,
            long[] rangeEnds,
            long[] protections,
            long[] generations,
            @Nullable MemoryPage[] pages,
            @Nullable MemoryAccess access) {
        int index = cacheSlot(pageNumber, pages.length);
        @Nullable MemoryPage page = pages[index];
        if (page != null
                && pageNumbers[index] == pageNumber
                && generations[index] == generation
                && address >= rangeStarts[index]
                && length <= rangeEnds[index] - address
                && (protections[index] & requiredProtection) == requiredProtection) {
            if (access != null) {
                access.setDataPage(
                        pageNumber,
                        rangeStarts[index],
                        rangeEnds[index],
                        protections[index],
                        generations[index],
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
            long[] pageNumbers,
            long[] rangeStarts,
            long[] rangeEnds,
            long[] protections,
            long[] generations,
            @Nullable MemoryPage[] pages) {
        int index = cacheSlot(pageNumber, pages.length);
        pageNumbers[index] = pageNumber;
        rangeStarts[index] = rangeStart;
        rangeEnds[index] = rangeEnd;
        protections[index] = protection;
        generations[index] = generation;
        pages[index] = page;
    }

    /// Hashes a page number into a direct-mapped cache slot.
    private static int cacheSlot(long pageNumber, int length) {
        long hash = pageNumber ^ (pageNumber >>> 6) ^ (pageNumber >>> 12);
        return (int) hash & (length - 1);
    }
}
