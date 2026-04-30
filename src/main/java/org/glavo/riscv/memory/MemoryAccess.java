// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.memory;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Provides scalar guest memory access with a pre-resolved software TLB for one host thread.
@NotNullByDefault
public final class MemoryAccess {
    /// The memory object that owns page tables and VMA metadata.
    private final Memory memory;

    /// The software TLB for the current Truffle context and host thread.
    private final @Nullable MappedRegionCache cache;

    /// The memory generation observed at the current guest block boundary.
    private long generation;

    /// The readable data page number cached by this access facade.
    private long cachedDataPageNumber;

    /// The inclusive readable guest address cached by this access facade.
    private long cachedDataRangeStart;

    /// The exclusive readable guest address cached by this access facade.
    private long cachedDataRangeEnd;

    /// The readable access protections cached by this access facade, or zero when absent.
    private long cachedDataProtection;

    /// The memory generation associated with the readable cache entry.
    private long cachedDataGeneration;

    /// The Unsafe base object for the readable cache entry.
    private @Nullable Object cachedDataBaseObject;

    /// The Unsafe byte offset of the readable cache entry's page start.
    private long cachedDataBaseOffset;

    /// The writable data page number cached by this access facade.
    private long cachedWriteDataPageNumber;

    /// The inclusive writable guest address cached by this access facade.
    private long cachedWriteDataRangeStart;

    /// The exclusive writable guest address cached by this access facade.
    private long cachedWriteDataRangeEnd;

    /// The writable access protections cached by this access facade, or zero when absent.
    private long cachedWriteDataProtection;

    /// The memory generation associated with the writable cache entry.
    private long cachedWriteDataGeneration;

    /// The Unsafe base object for the writable cache entry.
    private @Nullable Object cachedWriteDataBaseObject;

    /// The Unsafe byte offset of the writable cache entry's page start.
    private long cachedWriteDataBaseOffset;

    /// Creates an access facade for a memory object and optional software TLB.
    MemoryAccess(Memory memory, @Nullable MappedRegionCache cache) {
        this.memory = memory;
        this.cache = cache;
        this.generation = memory.generation;
    }

    /// Refreshes generation-sensitive access-local caches at a guest block boundary.
    public void refreshGeneration() {
        long currentGeneration = memory.generation;
        if (generation != currentGeneration) {
            generation = currentGeneration;
            cachedDataProtection = Memory.PROTECTION_NONE;
            cachedWriteDataProtection = Memory.PROTECTION_NONE;
        }
    }

    /// Reads a signed byte from guest memory using explicit page-layout constants.
    public byte readByte(long address, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        ensureReadableDataPage(address, Byte.BYTES, layout);
        return MemoryUnsafe.UNSAFE.getByte(cachedDataBaseObject, cachedDataBaseOffset + pageOffset);
    }

    /// Reads an unsigned byte from guest memory using explicit page-layout constants.
    public int readUnsignedByte(long address, MemoryLayout layout) {
        return readByte(address, layout) & 0xff;
    }

    /// Reads a signed little-endian 16-bit value from guest memory using explicit page-layout constants.
    public short readShort(long address, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageShortOffset(pageOffset)) {
            ensureReadableDataPage(address, Short.BYTES, layout);
            return MemoryUnsafe.readShortLE(cachedDataBaseObject, cachedDataBaseOffset + pageOffset);
        }

        return (short) memory.readLittleEndianByBytes(address, Short.BYTES, cache, layout);
    }

    /// Reads an unsigned little-endian 16-bit value from guest memory using explicit page-layout constants.
    public int readUnsignedShort(long address, MemoryLayout layout) {
        return readShort(address, layout) & 0xffff;
    }

    /// Reads a signed little-endian 32-bit value from guest memory using explicit page-layout constants.
    public int readInt(long address, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageIntOffset(pageOffset)) {
            ensureReadableDataPage(address, Integer.BYTES, layout);
            return MemoryUnsafe.readIntLE(cachedDataBaseObject, cachedDataBaseOffset + pageOffset);
        }

        return (int) memory.readLittleEndianByBytes(address, Integer.BYTES, cache, layout);
    }

    /// Reads an unsigned little-endian 32-bit value from guest memory using explicit page-layout constants.
    public long readUnsignedInt(long address, MemoryLayout layout) {
        return readInt(address, layout) & 0xffff_ffffL;
    }

    /// Reads a signed little-endian 64-bit value from guest memory using explicit page-layout constants.
    public long readLong(long address, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageLongOffset(pageOffset)) {
            ensureReadableDataPage(address, Long.BYTES, layout);
            return MemoryUnsafe.readLongLE(cachedDataBaseObject, cachedDataBaseOffset + pageOffset);
        }

        return memory.readLittleEndianByBytes(address, Long.BYTES, cache, layout);
    }

    /// Writes a byte to guest memory using explicit page-layout constants.
    public void writeByte(long address, byte value, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        ensureWritableDataPage(address, Byte.BYTES, layout);
        MemoryUnsafe.UNSAFE.putByte(cachedWriteDataBaseObject, cachedWriteDataBaseOffset + pageOffset, value);
    }

    /// Writes a little-endian 16-bit value to guest memory using explicit page-layout constants.
    public void writeShort(long address, short value, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageShortOffset(pageOffset)) {
            ensureWritableDataPage(address, Short.BYTES, layout);
            MemoryUnsafe.writeShortLE(cachedWriteDataBaseObject, cachedWriteDataBaseOffset + pageOffset, value);
            return;
        }

        memory.writeLittleEndianByBytes(address, value, Short.BYTES, cache, layout);
    }

    /// Writes a little-endian 32-bit value to guest memory using explicit page-layout constants.
    public void writeInt(long address, int value, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageIntOffset(pageOffset)) {
            ensureWritableDataPage(address, Integer.BYTES, layout);
            MemoryUnsafe.writeIntLE(cachedWriteDataBaseObject, cachedWriteDataBaseOffset + pageOffset, value);
            return;
        }

        memory.writeLittleEndianByBytes(address, value, Integer.BYTES, cache, layout);
    }

    /// Writes a little-endian 64-bit value to guest memory using explicit page-layout constants.
    public void writeLong(long address, long value, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageLongOffset(pageOffset)) {
            ensureWritableDataPage(address, Long.BYTES, layout);
            MemoryUnsafe.writeLongLE(cachedWriteDataBaseObject, cachedWriteDataBaseOffset + pageOffset, value);
            return;
        }

        memory.writeLittleEndianByBytes(address, value, Long.BYTES, cache, layout);
    }

    /// Ensures the readable data-page cache covers the supplied range.
    private void ensureReadableDataPage(long address, int length, MemoryLayout layout) {
        if (!hasCachedDataPage(address, length, layout)) {
            memory.readPage(address, length, false, cache, this, layout);
        }
    }

    /// Ensures the writable data-page cache covers the supplied range.
    private void ensureWritableDataPage(long address, int length, MemoryLayout layout) {
        if (!hasCachedWriteDataPage(address, length, layout)) {
            memory.writePage(address, length, cache, this, layout);
        }
    }

    /// Returns true when the readable data-page cache satisfies the supplied range.
    private boolean hasCachedDataPage(long address, int length, MemoryLayout layout) {
        long pageNumber = layout.pageNumber(address);
        return cachedDataPageNumber == pageNumber
                && cachedDataGeneration == generation
                && address >= cachedDataRangeStart
                && length <= cachedDataRangeEnd - address
                && (cachedDataProtection & Memory.PROTECTION_READ) != 0;
    }

    /// Returns true when the writable data-page cache satisfies the supplied range.
    private boolean hasCachedWriteDataPage(long address, int length, MemoryLayout layout) {
        long pageNumber = layout.pageNumber(address);
        return cachedWriteDataPageNumber == pageNumber
                && cachedWriteDataGeneration == generation
                && address >= cachedWriteDataRangeStart
                && length <= cachedWriteDataRangeEnd - address
                && (cachedWriteDataProtection & Memory.PROTECTION_WRITE) != 0;
    }

    /// Stores one data-page lookup in the access-local cache.
    void setDataPage(
            long pageNumber,
            long rangeStart,
            long rangeEnd,
            long protection,
            long generation,
            MemoryPage page) {
        this.generation = generation;
        if ((protection & Memory.PROTECTION_READ) != 0) {
            cachedDataPageNumber = pageNumber;
            cachedDataRangeStart = rangeStart;
            cachedDataRangeEnd = rangeEnd;
            cachedDataProtection = protection;
            cachedDataGeneration = generation;
            cachedDataBaseObject = page.baseObject();
            cachedDataBaseOffset = page.baseOffset();
        }
        if ((protection & Memory.PROTECTION_WRITE) != 0) {
            cachedWriteDataPageNumber = pageNumber;
            cachedWriteDataRangeStart = rangeStart;
            cachedWriteDataRangeEnd = rangeEnd;
            cachedWriteDataProtection = protection;
            cachedWriteDataGeneration = generation;
            cachedWriteDataBaseObject = page.baseObject();
            cachedWriteDataBaseOffset = page.baseOffset();
        }
    }

}
