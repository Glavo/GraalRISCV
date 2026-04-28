package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests checked guest memory access through MemorySegment.
@NotNullByDefault
public final class MemoryTest {
    /// Verifies little-endian 64-bit guest memory access.
    @Test
    public void readsAndWritesLongValues() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            memory.writeLong(Memory.DEFAULT_BASE_ADDRESS + 16, 0x0102_0304_0506_0708L);

            assertEquals(0x0102_0304_0506_0708L, memory.readLong(Memory.DEFAULT_BASE_ADDRESS + 16));
            assertEquals(0x08, memory.readUnsignedByte(Memory.DEFAULT_BASE_ADDRESS + 16));
        }
    }

    /// Verifies that MemorySegment rejects unaligned data access.
    @Test
    public void rejectsMisalignedDataAccess() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            assertThrows(IllegalArgumentException.class, () -> memory.readInt(Memory.DEFAULT_BASE_ADDRESS + 2));
        }
    }

    /// Verifies that instruction fetches can read from 16-bit-aligned addresses.
    @Test
    public void readsInstructionIntFromHalfwordAlignedAddress() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 1024)) {
            memory.writeByte(Memory.DEFAULT_BASE_ADDRESS + 2, (byte) 0x13);
            memory.writeByte(Memory.DEFAULT_BASE_ADDRESS + 3, (byte) 0x05);
            memory.writeByte(Memory.DEFAULT_BASE_ADDRESS + 4, (byte) 0x00);
            memory.writeByte(Memory.DEFAULT_BASE_ADDRESS + 5, (byte) 0x00);

            assertEquals(0x0000_0513, memory.readInstructionInt(Memory.DEFAULT_BASE_ADDRESS + 2));
        }
    }
}
