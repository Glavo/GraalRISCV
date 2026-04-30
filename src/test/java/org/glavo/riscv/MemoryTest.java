// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.exception.*;
import org.glavo.riscv.memory.*;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests checked guest memory access through paged virtual memory.
@NotNullByDefault
public final class MemoryTest {
    /// Verifies little-endian 64-bit guest memory access.
    @Test
    public void readsAndWritesLongValues() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024, null)) {
            memory.writeLong(Memory.DEFAULT_BASE_ADDRESS + 16, 0x0102_0304_0506_0708L);

            assertEquals(0x0102_0304_0506_0708L, memory.readLong(Memory.DEFAULT_BASE_ADDRESS + 16));
            assertEquals(0x08, memory.readUnsignedByte(Memory.DEFAULT_BASE_ADDRESS + 16));
        }
    }

    /// Verifies that paged memory supports unaligned data access.
    @Test
    public void supportsMisalignedDataAccess() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024, null)) {
            memory.writeInt(Memory.DEFAULT_BASE_ADDRESS + 2, 0x1122_3344);

            assertEquals(0x1122_3344, memory.readInt(Memory.DEFAULT_BASE_ADDRESS + 2));
            assertEquals(0x44, memory.readUnsignedByte(Memory.DEFAULT_BASE_ADDRESS + 2));
        }
    }

    /// Verifies that instruction fetches can read from 16-bit-aligned addresses.
    @Test
    public void readsInstructionIntFromHalfwordAlignedAddress() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024, null)) {
            memory.writeByte(Memory.DEFAULT_BASE_ADDRESS + 2, (byte) 0x13);
            memory.writeByte(Memory.DEFAULT_BASE_ADDRESS + 3, (byte) 0x05);
            memory.writeByte(Memory.DEFAULT_BASE_ADDRESS + 4, (byte) 0x00);
            memory.writeByte(Memory.DEFAULT_BASE_ADDRESS + 5, (byte) 0x00);

            assertEquals(0x0000_0513, memory.readInstructionInt(Memory.DEFAULT_BASE_ADDRESS + 2));
        }
    }

    /// Verifies sparse mappings can be accessed after out-of-order insertion.
    @Test
    public void accessesMultipleSparseMappings() {
        try (Memory memory = Memory.sparse(Memory.DEFAULT_BASE_ADDRESS, 0x20_000, null)) {
            long firstAddress = Memory.DEFAULT_BASE_ADDRESS + 0x4000;
            long secondAddress = Memory.DEFAULT_BASE_ADDRESS + 0x8000;
            long thirdAddress = Memory.DEFAULT_BASE_ADDRESS + 0xc000;

            assertTrue(memory.map(secondAddress, 4096));
            assertTrue(memory.map(firstAddress, 4096));
            assertTrue(memory.map(thirdAddress, 4096));

            memory.writeInt(firstAddress + 16, 0x1122_3344);
            memory.writeLong(secondAddress + 32, 0x0102_0304_0506_0708L);
            memory.writeByte(thirdAddress + 48, (byte) 0x5a);

            assertEquals(0x1122_3344, memory.readInt(firstAddress + 16));
            assertEquals(0x0102_0304_0506_0708L, memory.readLong(secondAddress + 32));
            assertEquals(0x5a, memory.readUnsignedByte(thirdAddress + 48));
        }
    }

    /// Verifies heap-backed regions share the same sparse access path as native mappings.
    @Test
    public void accessesHeapBackedMapping() {
        try (Memory memory = Memory.sparse(Memory.DEFAULT_BASE_ADDRESS, 0x20_000, null)) {
            long mappingAddress = Memory.DEFAULT_BASE_ADDRESS + 0x4000;

            assertTrue(memory.mapHeap(mappingAddress, 4096));
            memory.writeLong(mappingAddress + 24, 0x1020_3040_5060_7080L);

            assertEquals(0x1020_3040_5060_7080L, memory.readLong(mappingAddress + 24));
        }
    }

    /// Verifies mapped pages are committed lazily on first write.
    @Test
    public void commitsPagesLazily() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 2L * Memory.DEFAULT_PAGE_SIZE, null)) {
            assertEquals(0, memory.committedPages());
            assertEquals(0, memory.readLong(Memory.DEFAULT_BASE_ADDRESS));
            assertEquals(0, memory.committedPages());

            memory.writeByte(Memory.DEFAULT_BASE_ADDRESS, (byte) 1);

            assertEquals(1, memory.committedPages());
            memory.clear(Memory.DEFAULT_BASE_ADDRESS + Memory.DEFAULT_PAGE_SIZE, Memory.DEFAULT_PAGE_SIZE);
            assertEquals(1, memory.committedPages());
        }
    }

    /// Verifies the configured committed-page limit is enforced before allocating new backing pages.
    @Test
    public void enforcesCommittedPageLimit() {
        try (Memory memory = Memory.sparse(
                Memory.DEFAULT_BASE_ADDRESS,
                2L * Memory.DEFAULT_PAGE_SIZE,
                Memory.DEFAULT_PAGE_SIZE,
                1,
                Memory.DEFAULT_HUGE_PAGE_SIZE,
                0,
                null)) {
            assertTrue(memory.map(Memory.DEFAULT_BASE_ADDRESS, 2L * Memory.DEFAULT_PAGE_SIZE));
            memory.writeByte(Memory.DEFAULT_BASE_ADDRESS, (byte) 1);

            assertThrows(
                    RiscVException.class,
                    () -> memory.writeByte(Memory.DEFAULT_BASE_ADDRESS + Memory.DEFAULT_PAGE_SIZE, (byte) 2));
        }
    }

    /// Verifies sparse memory can use a configured base page size.
    @Test
    public void supportsConfiguredBasePageSize() {
        int pageSize = 8192;
        try (Memory memory = Memory.sparse(
                Memory.DEFAULT_BASE_ADDRESS,
                2L * pageSize,
                pageSize,
                0,
                Memory.DEFAULT_HUGE_PAGE_SIZE,
                0,
                null)) {
            assertEquals(pageSize, memory.pageSize());
            assertTrue(memory.map(Memory.DEFAULT_BASE_ADDRESS, 2L * pageSize));

            memory.writeLong(Memory.DEFAULT_BASE_ADDRESS + pageSize, 0x1122_3344_5566_7788L);

            assertEquals(0x1122_3344_5566_7788L, memory.readLong(Memory.DEFAULT_BASE_ADDRESS + pageSize));
            assertEquals(1, memory.committedPages());
        }
    }

    /// Verifies the minimum power-of-two base page size keeps page masking behavior correct.
    @Test
    public void supportsMinimumPowerOfTwoBasePageSize() {
        int pageSize = Memory.MIN_PAGE_SIZE;
        try (Memory memory = Memory.sparse(
                Memory.DEFAULT_BASE_ADDRESS,
                2L * pageSize,
                pageSize,
                0,
                Memory.DEFAULT_HUGE_PAGE_SIZE,
                0,
                null)) {
            assertEquals(pageSize, memory.pageSize());
            assertTrue(memory.map(Memory.DEFAULT_BASE_ADDRESS, pageSize));

            long value = 0x1122_3344_5566_7788L;
            memory.writeLong(Memory.DEFAULT_BASE_ADDRESS, value);
            memory.writeLong(Memory.DEFAULT_BASE_ADDRESS + pageSize - Long.BYTES, value);

            assertEquals(value, memory.readLong(Memory.DEFAULT_BASE_ADDRESS));
            assertEquals(value, memory.readLong(Memory.DEFAULT_BASE_ADDRESS + pageSize - Long.BYTES));
            assertEquals(1, memory.committedPages());
            assertThrows(RiscVException.class, () -> memory.readLong(Memory.DEFAULT_BASE_ADDRESS + pageSize - 1L));
        }
    }

    /// Verifies base page sizes below 4 KiB are rejected even when they are powers of two.
    @Test
    public void rejectsSubMinimumPowerOfTwoBasePageSize() {
        assertThrows(
                RiscVException.class,
                () -> Memory.sparse(
                        Memory.DEFAULT_BASE_ADDRESS,
                        2L * Memory.MIN_PAGE_SIZE,
                        Memory.MIN_PAGE_SIZE >>> 1,
                        0,
                        Memory.DEFAULT_HUGE_PAGE_SIZE,
                        0,
                        null));
    }

    /// Verifies MAP_HUGETLB-style mappings reserve and release the configured huge-page pool.
    @Test
    public void accountsHugePagePoolReservations() {
        long hugePageSize = Memory.DEFAULT_HUGE_PAGE_SIZE;
        try (Memory memory = Memory.sparse(
                Memory.DEFAULT_BASE_ADDRESS,
                2L * hugePageSize,
                Memory.DEFAULT_PAGE_SIZE,
                0,
                hugePageSize,
                1,
                null)) {
            assertTrue(memory.mapHuge(Memory.DEFAULT_BASE_ADDRESS, hugePageSize));
            assertEquals(1, memory.reservedHugePages());
            assertFalse(memory.mapHuge(Memory.DEFAULT_BASE_ADDRESS + hugePageSize, hugePageSize));

            memory.unmap(Memory.DEFAULT_BASE_ADDRESS, hugePageSize);

            assertEquals(0, memory.reservedHugePages());
            assertTrue(memory.mapHuge(Memory.DEFAULT_BASE_ADDRESS + hugePageSize, hugePageSize));
        }
    }

    /// Verifies the initial contiguous memory is part of the unified region table.
    @Test
    public void rejectsMappingsOverInitialRegion() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024, null)) {
            assertFalse(memory.map(Memory.DEFAULT_BASE_ADDRESS + 16, 4096));
            assertFalse(memory.mapHeap(Memory.DEFAULT_BASE_ADDRESS + 16, 4096));
        }
    }

    /// Verifies sparse memory starts without backing for the configured virtual address window.
    @Test
    public void sparseMemoryRequiresExplicitBacking() {
        try (Memory memory = Memory.sparse(Memory.DEFAULT_BASE_ADDRESS, 1024, null)) {
            assertFalse(memory.isBacked(Memory.DEFAULT_BASE_ADDRESS, Long.BYTES));
            assertThrows(RiscVException.class, () -> memory.readLong(Memory.DEFAULT_BASE_ADDRESS));

            assertTrue(memory.map(Memory.DEFAULT_BASE_ADDRESS, 1024));
            memory.writeLong(Memory.DEFAULT_BASE_ADDRESS + 8, 0x1122_3344_5566_7788L);

            assertEquals(0x1122_3344_5566_7788L, memory.readLong(Memory.DEFAULT_BASE_ADDRESS + 8));
            assertTrue(memory.isBacked(Memory.DEFAULT_BASE_ADDRESS, Long.BYTES));
        }
    }

    /// Verifies sparse memory reports the backed range containing an address.
    @Test
    public void reportsSparseBackedRangeEnd() {
        try (Memory memory = Memory.sparse(Memory.DEFAULT_BASE_ADDRESS, 0x20_000, null)) {
            long firstAddress = Memory.DEFAULT_BASE_ADDRESS + 0x4000;
            long secondAddress = Memory.DEFAULT_BASE_ADDRESS + 0x8000;

            assertTrue(memory.map(firstAddress, 4096));
            assertTrue(memory.map(secondAddress, 4096));

            assertEquals(firstAddress + 4096, memory.backedRangeEnd(firstAddress + 16));
            assertEquals(Memory.DEFAULT_BASE_ADDRESS, memory.backedRangeEnd(Memory.DEFAULT_BASE_ADDRESS));
            assertEquals(secondAddress + 4096, memory.overlappingBackingEnd(secondAddress - 16, 32));
        }
    }

    /// Verifies unmapping the middle of a sparse mapping keeps the remaining slices addressable.
    @Test
    public void unmapSplitsSparseMapping() {
        try (Memory memory = Memory.sparse(Memory.DEFAULT_BASE_ADDRESS, 0x20_000, null)) {
            long mappingAddress = Memory.DEFAULT_BASE_ADDRESS + 0x4000;

            assertTrue(memory.map(mappingAddress, 3 * 4096L));
            memory.writeLong(mappingAddress + 16, 0x1111_2222_3333_4444L);
            memory.writeLong(mappingAddress + 4096 + 16, 0x5555_6666_7777_8888L);
            memory.writeLong(mappingAddress + 8192 + 16, 0x9999_aaaa_bbbb_ccccL);

            memory.unmap(mappingAddress + 4096, 4096);

            assertEquals(0x1111_2222_3333_4444L, memory.readLong(mappingAddress + 16));
            assertThrows(RiscVException.class, () -> memory.readLong(mappingAddress + 4096 + 16));
            assertEquals(0x9999_aaaa_bbbb_ccccL, memory.readLong(mappingAddress + 8192 + 16));
        }
    }

    /// Verifies mprotect-style VMA splitting preserves the unprotected outer ranges.
    @Test
    public void protectSplitsSparseMappingWithoutDroppingOuterRanges() {
        long pageSize = Memory.DEFAULT_PAGE_SIZE;
        try (Memory memory = Memory.sparse(Memory.DEFAULT_BASE_ADDRESS, 4L * pageSize, null)) {
            long baseAddress = Memory.DEFAULT_BASE_ADDRESS;

            assertTrue(memory.map(
                    baseAddress,
                    4L * pageSize,
                    Memory.PROTECTION_READ | Memory.PROTECTION_WRITE));
            memory.writeByte(baseAddress, (byte) 0x11);
            memory.writeByte(baseAddress + pageSize, (byte) 0x22);
            memory.writeByte(baseAddress + 3L * pageSize, (byte) 0x33);

            assertTrue(memory.protect(baseAddress + pageSize, 2L * pageSize, Memory.PROTECTION_READ));

            assertEquals(baseAddress + pageSize, memory.backedRangeEnd(baseAddress));
            assertEquals(baseAddress + 3L * pageSize, memory.backedRangeEnd(baseAddress + pageSize));
            assertEquals(baseAddress + 4L * pageSize, memory.backedRangeEnd(baseAddress + 3L * pageSize));
            assertEquals(0x11, memory.readUnsignedByte(baseAddress));
            assertEquals(0x22, memory.readUnsignedByte(baseAddress + pageSize));
            assertEquals(0x33, memory.readUnsignedByte(baseAddress + 3L * pageSize));
            assertThrows(RiscVException.class, () -> memory.writeByte(baseAddress + pageSize, (byte) 0x44));

            memory.writeByte(baseAddress, (byte) 0x55);
            memory.writeByte(baseAddress + 3L * pageSize, (byte) 0x66);

            assertEquals(0x55, memory.readUnsignedByte(baseAddress));
            assertEquals(0x66, memory.readUnsignedByte(baseAddress + 3L * pageSize));

            assertTrue(memory.protect(
                    baseAddress + pageSize,
                    2L * pageSize,
                    Memory.PROTECTION_READ | Memory.PROTECTION_WRITE));
            assertTrue(memory.isBacked(baseAddress, 4L * pageSize));
            assertEquals(baseAddress + 4L * pageSize, memory.backedRangeEnd(baseAddress));
        }
    }

    /// Verifies transparent huge-page advice splits VMAs without changing normal access rights.
    @Test
    public void adviceSplitsSparseMappingWithoutChangingAccess() {
        long pageSize = Memory.DEFAULT_PAGE_SIZE;
        try (Memory memory = Memory.sparse(Memory.DEFAULT_BASE_ADDRESS, 4L * pageSize, null)) {
            long baseAddress = Memory.DEFAULT_BASE_ADDRESS;

            assertTrue(memory.map(
                    baseAddress,
                    4L * pageSize,
                    Memory.PROTECTION_READ | Memory.PROTECTION_WRITE));
            memory.writeInt(baseAddress, 0x1111_2222);
            memory.writeInt(baseAddress + pageSize, 0x3333_4444);
            memory.writeInt(baseAddress + 3L * pageSize, 0x5555_6666);

            memory.adviseHugePagePreference(baseAddress + pageSize, 2L * pageSize, true);

            assertTrue(memory.isBacked(baseAddress, pageSize));
            assertTrue(memory.isBacked(baseAddress + pageSize, 2L * pageSize));
            assertTrue(memory.isBacked(baseAddress + 3L * pageSize, pageSize));
            assertEquals(baseAddress + pageSize, memory.backedRangeEnd(baseAddress));
            assertEquals(baseAddress + 3L * pageSize, memory.backedRangeEnd(baseAddress + pageSize));
            assertEquals(baseAddress + 4L * pageSize, memory.backedRangeEnd(baseAddress + 3L * pageSize));
            assertEquals(0x1111_2222, memory.readInt(baseAddress));
            assertEquals(0x3333_4444, memory.readInt(baseAddress + pageSize));
            assertEquals(0x5555_6666, memory.readInt(baseAddress + 3L * pageSize));

            memory.writeByte(baseAddress + pageSize, (byte) 0x77);

            assertEquals(0x77, memory.readUnsignedByte(baseAddress + pageSize));

            memory.adviseHugePagePreference(baseAddress, 4L * pageSize, false);

            assertTrue(memory.isBacked(baseAddress, 4L * pageSize));
            assertEquals(baseAddress + 4L * pageSize, memory.backedRangeEnd(baseAddress));
        }
    }

    /// Verifies adjacent mappings with matching attributes are represented as one VMA.
    @Test
    public void adjacentSparseMappingsWithSameAttributesAreMerged() {
        long pageSize = Memory.DEFAULT_PAGE_SIZE;
        try (Memory memory = Memory.sparse(Memory.DEFAULT_BASE_ADDRESS, 2L * pageSize, null)) {
            long baseAddress = Memory.DEFAULT_BASE_ADDRESS;

            assertTrue(memory.map(baseAddress, pageSize, Memory.PROTECTION_READ | Memory.PROTECTION_WRITE));
            assertTrue(memory.map(
                    baseAddress + pageSize,
                    pageSize,
                    Memory.PROTECTION_READ | Memory.PROTECTION_WRITE));

            assertTrue(memory.isBacked(baseAddress, 2L * pageSize));
            assertEquals(baseAddress + 2L * pageSize, memory.backedRangeEnd(baseAddress));
        }
    }

    /// Verifies unmapping across split VMAs clears them so the range can be mapped again contiguously.
    @Test
    public void unmapAcrossSplitVmasAllowsContiguousRemap() {
        long pageSize = Memory.DEFAULT_PAGE_SIZE;
        try (Memory memory = Memory.sparse(Memory.DEFAULT_BASE_ADDRESS, 4L * pageSize, null)) {
            long baseAddress = Memory.DEFAULT_BASE_ADDRESS;

            assertTrue(memory.map(
                    baseAddress,
                    4L * pageSize,
                    Memory.PROTECTION_READ | Memory.PROTECTION_WRITE));
            assertTrue(memory.protect(baseAddress + pageSize, 2L * pageSize, Memory.PROTECTION_READ));

            memory.unmap(baseAddress, 4L * pageSize);

            assertFalse(memory.isBacked(baseAddress, pageSize));
            assertTrue(memory.map(baseAddress, 4L * pageSize, Memory.PROTECTION_READ | Memory.PROTECTION_WRITE));
            memory.writeLong(baseAddress + 2L * pageSize, 0x0102_0304_0506_0708L);

            assertTrue(memory.isBacked(baseAddress, 4L * pageSize));
            assertEquals(0x0102_0304_0506_0708L, memory.readLong(baseAddress + 2L * pageSize));
        }
    }

    /// Verifies the sparse page table grows and removes committed pages by page-number range.
    @Test
    public void unmapRemovesCommittedPagesFromGrownPageTable() {
        long pageSize = Memory.DEFAULT_PAGE_SIZE;
        try (Memory memory = Memory.sparse(Memory.DEFAULT_BASE_ADDRESS, 128 * pageSize, null)) {
            assertTrue(memory.map(Memory.DEFAULT_BASE_ADDRESS, 128 * pageSize));
            for (int page = 0; page < 80; page++) {
                memory.writeByte(Memory.DEFAULT_BASE_ADDRESS + page * pageSize, (byte) page);
            }

            assertEquals(80, memory.committedPages());

            memory.unmap(Memory.DEFAULT_BASE_ADDRESS + 20 * pageSize, 40 * pageSize);

            assertEquals(40, memory.committedPages());
            assertEquals(19, memory.readUnsignedByte(Memory.DEFAULT_BASE_ADDRESS + 19 * pageSize));
            assertThrows(RiscVException.class, () -> memory.readByte(Memory.DEFAULT_BASE_ADDRESS + 20 * pageSize));
            assertEquals(60, memory.readUnsignedByte(Memory.DEFAULT_BASE_ADDRESS + 60 * pageSize));
        }
    }

    /// Verifies that VMA access protection controls guest data reads and writes.
    @Test
    public void enforcesDataAccessProtection() {
        try (Memory memory = Memory.sparse(Memory.DEFAULT_BASE_ADDRESS, Memory.DEFAULT_PAGE_SIZE, null)) {
            assertTrue(memory.map(
                    Memory.DEFAULT_BASE_ADDRESS,
                    Memory.DEFAULT_PAGE_SIZE,
                    Memory.PROTECTION_READ | Memory.PROTECTION_WRITE));
            memory.writeLong(Memory.DEFAULT_BASE_ADDRESS, 0x0102_0304_0506_0708L);

            assertTrue(memory.protect(Memory.DEFAULT_BASE_ADDRESS, Memory.DEFAULT_PAGE_SIZE, Memory.PROTECTION_READ));
            assertEquals(0x0102_0304_0506_0708L, memory.readLong(Memory.DEFAULT_BASE_ADDRESS));
            assertThrows(RiscVException.class, () -> memory.writeByte(Memory.DEFAULT_BASE_ADDRESS, (byte) 0x7f));

            assertTrue(memory.protect(
                    Memory.DEFAULT_BASE_ADDRESS,
                    Memory.DEFAULT_PAGE_SIZE,
                    Memory.PROTECTION_READ | Memory.PROTECTION_WRITE));
            memory.writeByte(Memory.DEFAULT_BASE_ADDRESS, (byte) 0x7f);
            assertEquals(0x7f, memory.readUnsignedByte(Memory.DEFAULT_BASE_ADDRESS));

            assertTrue(memory.protect(Memory.DEFAULT_BASE_ADDRESS, Memory.DEFAULT_PAGE_SIZE, Memory.PROTECTION_NONE));
            assertThrows(RiscVException.class, () -> memory.readByte(Memory.DEFAULT_BASE_ADDRESS));
        }
    }

    /// Verifies that instruction fetches require execute permission.
    @Test
    public void enforcesInstructionFetchProtection() {
        try (Memory memory = Memory.sparse(Memory.DEFAULT_BASE_ADDRESS, Memory.DEFAULT_PAGE_SIZE, null)) {
            assertTrue(memory.map(
                    Memory.DEFAULT_BASE_ADDRESS,
                    Memory.DEFAULT_PAGE_SIZE,
                    Memory.PROTECTION_READ | Memory.PROTECTION_WRITE));
            memory.writeInt(Memory.DEFAULT_BASE_ADDRESS, 0x0000_0513);
            assertThrows(RiscVException.class, () -> memory.readInstructionInt(Memory.DEFAULT_BASE_ADDRESS));

            assertTrue(memory.protect(
                    Memory.DEFAULT_BASE_ADDRESS,
                    Memory.DEFAULT_PAGE_SIZE,
                    Memory.PROTECTION_READ | Memory.PROTECTION_EXECUTE));
            assertEquals(0x0000_0513, memory.readInstructionInt(Memory.DEFAULT_BASE_ADDRESS));
        }
    }

    /// Verifies that cross-page instruction fetches require execute permission on every touched page.
    @Test
    public void crossPageInstructionFetchRequiresExecuteProtectionOnBothPages() {
        try (Memory memory = Memory.sparse(Memory.DEFAULT_BASE_ADDRESS, 2L * Memory.DEFAULT_PAGE_SIZE, null)) {
            long instructionAddress = Memory.DEFAULT_BASE_ADDRESS + Memory.DEFAULT_PAGE_SIZE - Short.BYTES;
            assertTrue(memory.map(
                    Memory.DEFAULT_BASE_ADDRESS,
                    2L * Memory.DEFAULT_PAGE_SIZE,
                    Memory.PROTECTION_READ_WRITE_EXECUTE));
            memory.writeInt(instructionAddress, 0x0000_0513);

            assertTrue(memory.protect(
                    Memory.DEFAULT_BASE_ADDRESS,
                    Memory.DEFAULT_PAGE_SIZE,
                    Memory.PROTECTION_READ | Memory.PROTECTION_EXECUTE));
            assertTrue(memory.protect(
                    Memory.DEFAULT_BASE_ADDRESS + Memory.DEFAULT_PAGE_SIZE,
                    Memory.DEFAULT_PAGE_SIZE,
                    Memory.PROTECTION_READ));
            assertThrows(RiscVException.class, () -> memory.readInstructionInt(instructionAddress));

            assertTrue(memory.protect(
                    Memory.DEFAULT_BASE_ADDRESS + Memory.DEFAULT_PAGE_SIZE,
                    Memory.DEFAULT_PAGE_SIZE,
                    Memory.PROTECTION_READ | Memory.PROTECTION_EXECUTE));
            assertEquals(0x0000_0513, memory.readInstructionInt(instructionAddress));
        }
    }
}
