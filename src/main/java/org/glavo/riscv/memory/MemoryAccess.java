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

    /// Whether this execution facade has a read data-page cache entry.
    private boolean cachedDataPageValid;

    /// The guest page number associated with the cached read data page.
    private long cachedDataPageNumber;

    /// The inclusive guest address where the cached read data page is valid.
    private long cachedDataRangeStart;

    /// The exclusive guest address where the cached read data page stops being valid.
    private long cachedDataRangeEnd;

    /// The access protections available through the cached read data page.
    private long cachedDataProtection;

    /// The memory generation associated with the cached read data page.
    private long cachedDataGeneration;

    /// The Unsafe base object for the cached read data page.
    private @Nullable Object cachedDataBaseObject;

    /// The Unsafe byte offset of the cached read data page start.
    private long cachedDataBaseOffset;

    /// Whether this execution facade has a write data-page cache entry.
    private boolean cachedWriteDataPageValid;

    /// The guest page number associated with the cached write data page.
    private long cachedWriteDataPageNumber;

    /// The inclusive guest address where the cached write data page is valid.
    private long cachedWriteDataRangeStart;

    /// The exclusive guest address where the cached write data page stops being valid.
    private long cachedWriteDataRangeEnd;

    /// The access protections available through the cached write data page.
    private long cachedWriteDataProtection;

    /// The memory generation associated with the cached write data page.
    private long cachedWriteDataGeneration;

    /// The Unsafe base object for the cached write data page.
    private @Nullable Object cachedWriteDataBaseObject;

    /// The Unsafe byte offset of the cached write data page start.
    private long cachedWriteDataBaseOffset;

    /// Creates an access facade for a memory object and optional software TLB.
    MemoryAccess(Memory memory, @Nullable MappedRegionCache cache) {
        this.memory = memory;
        this.cache = cache;
    }

    /// Reads a signed byte from guest memory.
    public byte readByte(long address) {
        return readByte(address, memory.layout());
    }

    /// Reads a signed byte from guest memory using explicit page-layout constants.
    public byte readByte(long address, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        ensureReadableDataPage(address, Byte.BYTES, layout);
        return MemoryUnsafe.UNSAFE.getByte(cachedDataBaseObject, cachedDataBaseOffset + pageOffset);
    }

    /// Reads an unsigned byte from guest memory.
    public int readUnsignedByte(long address) {
        return readByte(address) & 0xff;
    }

    /// Reads an unsigned byte from guest memory using explicit page-layout constants.
    public int readUnsignedByte(long address, MemoryLayout layout) {
        return readByte(address, layout) & 0xff;
    }

    /// Reads a signed little-endian 16-bit value from guest memory.
    public short readShort(long address) {
        return readShort(address, memory.layout());
    }

    /// Reads a signed little-endian 16-bit value from guest memory using explicit page-layout constants.
    public short readShort(long address, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageShortOffset(pageOffset)) {
            ensureReadableDataPage(address, Short.BYTES, layout);
            short value = MemoryUnsafe.UNSAFE.getShort(cachedDataBaseObject, cachedDataBaseOffset + pageOffset);
            return MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Short.reverseBytes(value);
        }

        return (short) memory.readLittleEndianByBytes(address, Short.BYTES, cache, layout);
    }

    /// Reads an unsigned little-endian 16-bit value from guest memory.
    public int readUnsignedShort(long address) {
        return readShort(address) & 0xffff;
    }

    /// Reads an unsigned little-endian 16-bit value from guest memory using explicit page-layout constants.
    public int readUnsignedShort(long address, MemoryLayout layout) {
        return readShort(address, layout) & 0xffff;
    }

    /// Reads a signed little-endian 32-bit value from guest memory.
    public int readInt(long address) {
        return readInt(address, memory.layout());
    }

    /// Reads a signed little-endian 32-bit value from guest memory using explicit page-layout constants.
    public int readInt(long address, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageIntOffset(pageOffset)) {
            ensureReadableDataPage(address, Integer.BYTES, layout);
            int value = MemoryUnsafe.UNSAFE.getInt(cachedDataBaseObject, cachedDataBaseOffset + pageOffset);
            return MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Integer.reverseBytes(value);
        }

        return (int) memory.readLittleEndianByBytes(address, Integer.BYTES, cache, layout);
    }

    /// Reads an unsigned little-endian 32-bit value from guest memory.
    public long readUnsignedInt(long address) {
        return readInt(address) & 0xffff_ffffL;
    }

    /// Reads an unsigned little-endian 32-bit value from guest memory using explicit page-layout constants.
    public long readUnsignedInt(long address, MemoryLayout layout) {
        return readInt(address, layout) & 0xffff_ffffL;
    }

    /// Reads a little-endian 32-bit instruction from a guest address.
    public int readInstructionInt(long address) {
        return readInstructionInt(address, memory.layout());
    }

    /// Reads a little-endian 32-bit instruction from a guest address using explicit page-layout constants.
    public int readInstructionInt(long address, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageIntOffset(pageOffset)) {
            MemoryPage page = memory.readPage(address, Integer.BYTES, true, cache, null, layout);
            int value = MemoryUnsafe.UNSAFE.getInt(page.baseObject(), page.byteOffset(pageOffset));
            return MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Integer.reverseBytes(value);
        }

        return (int) memory.readLittleEndianByBytes(address, Integer.BYTES, cache, layout);
    }

    /// Reads a signed little-endian 64-bit value from guest memory.
    public long readLong(long address) {
        return readLong(address, memory.layout());
    }

    /// Reads a signed little-endian 64-bit value from guest memory using explicit page-layout constants.
    public long readLong(long address, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageLongOffset(pageOffset)) {
            ensureReadableDataPage(address, Long.BYTES, layout);
            long value = MemoryUnsafe.UNSAFE.getLong(cachedDataBaseObject, cachedDataBaseOffset + pageOffset);
            return MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Long.reverseBytes(value);
        }

        return memory.readLittleEndianByBytes(address, Long.BYTES, cache, layout);
    }

    /// Writes a byte to guest memory.
    public void writeByte(long address, byte value) {
        writeByte(address, value, memory.layout());
    }

    /// Writes a byte to guest memory using explicit page-layout constants.
    public void writeByte(long address, byte value, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        ensureWritableDataPage(address, Byte.BYTES, layout);
        MemoryUnsafe.UNSAFE.putByte(cachedWriteDataBaseObject, cachedWriteDataBaseOffset + pageOffset, value);
    }

    /// Writes a little-endian 16-bit value to guest memory.
    public void writeShort(long address, short value) {
        writeShort(address, value, memory.layout());
    }

    /// Writes a little-endian 16-bit value to guest memory using explicit page-layout constants.
    public void writeShort(long address, short value, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageShortOffset(pageOffset)) {
            ensureWritableDataPage(address, Short.BYTES, layout);
            short stored = MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Short.reverseBytes(value);
            MemoryUnsafe.UNSAFE.putShort(cachedWriteDataBaseObject, cachedWriteDataBaseOffset + pageOffset, stored);
            return;
        }

        memory.writeLittleEndianByBytes(address, value, Short.BYTES, cache, layout);
    }

    /// Writes a little-endian 32-bit value to guest memory.
    public void writeInt(long address, int value) {
        writeInt(address, value, memory.layout());
    }

    /// Writes a little-endian 32-bit value to guest memory using explicit page-layout constants.
    public void writeInt(long address, int value, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageIntOffset(pageOffset)) {
            ensureWritableDataPage(address, Integer.BYTES, layout);
            int stored = MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Integer.reverseBytes(value);
            MemoryUnsafe.UNSAFE.putInt(cachedWriteDataBaseObject, cachedWriteDataBaseOffset + pageOffset, stored);
            return;
        }

        memory.writeLittleEndianByBytes(address, value, Integer.BYTES, cache, layout);
    }

    /// Writes a little-endian 64-bit value to guest memory.
    public void writeLong(long address, long value) {
        writeLong(address, value, memory.layout());
    }

    /// Writes a little-endian 64-bit value to guest memory using explicit page-layout constants.
    public void writeLong(long address, long value, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageLongOffset(pageOffset)) {
            ensureWritableDataPage(address, Long.BYTES, layout);
            long stored = MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Long.reverseBytes(value);
            MemoryUnsafe.UNSAFE.putLong(cachedWriteDataBaseObject, cachedWriteDataBaseOffset + pageOffset, stored);
            return;
        }

        memory.writeLittleEndianByBytes(address, value, Long.BYTES, cache, layout);
    }

    /// Ensures the access-local cache contains the readable data page for the supplied range.
    private void ensureReadableDataPage(long address, int length, MemoryLayout layout) {
        if (!hasCachedDataPage(address, length, Memory.PROTECTION_READ, layout)) {
            memory.readPage(address, length, false, cache, this, layout);
        }
    }

    /// Ensures the access-local cache contains the writable data page for the supplied range.
    private void ensureWritableDataPage(long address, int length, MemoryLayout layout) {
        if (!hasCachedWriteDataPage(address, length, Memory.PROTECTION_WRITE, layout)) {
            memory.writePage(address, length, cache, this, layout);
        }
    }

    /// Returns true when the access-local data-page cache satisfies the supplied range and protection.
    private boolean hasCachedDataPage(long address, int length, long requiredProtection, MemoryLayout layout) {
        long pageNumber = layout.pageNumber(address);
        return cachedDataPageValid
                && cachedDataPageNumber == pageNumber
                && cachedDataGeneration == memory.generation
                && address >= cachedDataRangeStart
                && length <= cachedDataRangeEnd - address
                && (cachedDataProtection & requiredProtection) == requiredProtection;
    }

    /// Returns true when the access-local write data-page cache satisfies the supplied range and protection.
    private boolean hasCachedWriteDataPage(long address, int length, long requiredProtection, MemoryLayout layout) {
        long pageNumber = layout.pageNumber(address);
        return cachedWriteDataPageValid
                && cachedWriteDataPageNumber == pageNumber
                && cachedWriteDataGeneration == memory.generation
                && address >= cachedWriteDataRangeStart
                && length <= cachedWriteDataRangeEnd - address
                && (cachedWriteDataProtection & requiredProtection) == requiredProtection;
    }

    /// Stores one data-page lookup in the access-local cache.
    void setDataPage(
            long pageNumber,
            long rangeStart,
            long rangeEnd,
            long protection,
            long generation,
            MemoryPage page) {
        if ((protection & Memory.PROTECTION_READ) != 0) {
            cachedDataPageNumber = pageNumber;
            cachedDataRangeStart = rangeStart;
            cachedDataRangeEnd = rangeEnd;
            cachedDataProtection = protection;
            cachedDataGeneration = generation;
            cachedDataBaseObject = page.baseObject();
            cachedDataBaseOffset = page.baseOffset();
            cachedDataPageValid = true;
        }
        if ((protection & Memory.PROTECTION_WRITE) != 0) {
            cachedWriteDataPageNumber = pageNumber;
            cachedWriteDataRangeStart = rangeStart;
            cachedWriteDataRangeEnd = rangeEnd;
            cachedWriteDataProtection = protection;
            cachedWriteDataGeneration = generation;
            cachedWriteDataBaseObject = page.baseObject();
            cachedWriteDataBaseOffset = page.baseOffset();
            cachedWriteDataPageValid = true;
        }
    }
}
