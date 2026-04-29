// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.exception.*;
import org.glavo.riscv.memory.*;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests checked guest memory access through MemorySegment.
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

    /// Verifies that MemorySegment rejects unaligned data access.
    @Test
    public void rejectsMisalignedDataAccess() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024, null)) {
            assertThrows(IllegalArgumentException.class, () -> memory.readInt(Memory.DEFAULT_BASE_ADDRESS + 2));
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
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024, null)) {
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

    /// Verifies unmapping the middle of a sparse mapping keeps the remaining slices addressable.
    @Test
    public void unmapSplitsSparseMapping() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024, null)) {
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
}
