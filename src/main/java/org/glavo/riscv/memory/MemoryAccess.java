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
        long pageOffset = memory.pageOffset(address);
        ensureReadableDataPage(address, Byte.BYTES);
        return MemoryUnsafe.UNSAFE.getByte(cachedDataBaseObject, cachedDataBaseOffset + pageOffset);
    }

    /// Reads an unsigned byte from guest memory.
    public int readUnsignedByte(long address) {
        return readByte(address) & 0xff;
    }

    /// Reads a signed little-endian 16-bit value from guest memory.
    public short readShort(long address) {
        long pageOffset = memory.pageOffset(address);
        if (memory.isSinglePageOffset(pageOffset, Short.BYTES)) {
            ensureReadableDataPage(address, Short.BYTES);
            short value = MemoryUnsafe.UNSAFE.getShort(cachedDataBaseObject, cachedDataBaseOffset + pageOffset);
            return MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Short.reverseBytes(value);
        }

        return (short) memory.readLittleEndianByBytes(address, Short.BYTES, cache);
    }

    /// Reads an unsigned little-endian 16-bit value from guest memory.
    public int readUnsignedShort(long address) {
        return readShort(address) & 0xffff;
    }

    /// Reads a signed little-endian 32-bit value from guest memory.
    public int readInt(long address) {
        long pageOffset = memory.pageOffset(address);
        if (memory.isSinglePageOffset(pageOffset, Integer.BYTES)) {
            ensureReadableDataPage(address, Integer.BYTES);
            int value = MemoryUnsafe.UNSAFE.getInt(cachedDataBaseObject, cachedDataBaseOffset + pageOffset);
            return MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Integer.reverseBytes(value);
        }

        return (int) memory.readLittleEndianByBytes(address, Integer.BYTES, cache);
    }

    /// Reads an unsigned little-endian 32-bit value from guest memory.
    public long readUnsignedInt(long address) {
        return readInt(address) & 0xffff_ffffL;
    }

    /// Reads a little-endian 32-bit instruction from a guest address.
    public int readInstructionInt(long address) {
        long pageOffset = memory.pageOffset(address);
        if (memory.isSinglePageOffset(pageOffset, Integer.BYTES)) {
            MemoryPage page = memory.readPage(address, Integer.BYTES, true, cache);
            int value = MemoryUnsafe.UNSAFE.getInt(page.baseObject(), page.byteOffset(pageOffset));
            return MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Integer.reverseBytes(value);
        }

        return (int) memory.readLittleEndianByBytes(address, Integer.BYTES, cache);
    }

    /// Reads a signed little-endian 64-bit value from guest memory.
    public long readLong(long address) {
        long pageOffset = memory.pageOffset(address);
        if (memory.isSinglePageOffset(pageOffset, Long.BYTES)) {
            ensureReadableDataPage(address, Long.BYTES);
            long value = MemoryUnsafe.UNSAFE.getLong(cachedDataBaseObject, cachedDataBaseOffset + pageOffset);
            return MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Long.reverseBytes(value);
        }

        return memory.readLittleEndianByBytes(address, Long.BYTES, cache);
    }

    /// Writes a byte to guest memory.
    public void writeByte(long address, byte value) {
        long pageOffset = memory.pageOffset(address);
        ensureWritableDataPage(address, Byte.BYTES);
        MemoryUnsafe.UNSAFE.putByte(cachedWriteDataBaseObject, cachedWriteDataBaseOffset + pageOffset, value);
    }

    /// Writes a little-endian 16-bit value to guest memory.
    public void writeShort(long address, short value) {
        long pageOffset = memory.pageOffset(address);
        if (memory.isSinglePageOffset(pageOffset, Short.BYTES)) {
            ensureWritableDataPage(address, Short.BYTES);
            short stored = MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Short.reverseBytes(value);
            MemoryUnsafe.UNSAFE.putShort(cachedWriteDataBaseObject, cachedWriteDataBaseOffset + pageOffset, stored);
            return;
        }

        memory.writeLittleEndianByBytes(address, value, Short.BYTES, cache);
    }

    /// Writes a little-endian 32-bit value to guest memory.
    public void writeInt(long address, int value) {
        long pageOffset = memory.pageOffset(address);
        if (memory.isSinglePageOffset(pageOffset, Integer.BYTES)) {
            ensureWritableDataPage(address, Integer.BYTES);
            int stored = MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Integer.reverseBytes(value);
            MemoryUnsafe.UNSAFE.putInt(cachedWriteDataBaseObject, cachedWriteDataBaseOffset + pageOffset, stored);
            return;
        }

        memory.writeLittleEndianByBytes(address, value, Integer.BYTES, cache);
    }

    /// Writes a little-endian 64-bit value to guest memory.
    public void writeLong(long address, long value) {
        long pageOffset = memory.pageOffset(address);
        if (memory.isSinglePageOffset(pageOffset, Long.BYTES)) {
            ensureWritableDataPage(address, Long.BYTES);
            long stored = MemoryUnsafe.NATIVE_LITTLE_ENDIAN ? value : Long.reverseBytes(value);
            MemoryUnsafe.UNSAFE.putLong(cachedWriteDataBaseObject, cachedWriteDataBaseOffset + pageOffset, stored);
            return;
        }

        memory.writeLittleEndianByBytes(address, value, Long.BYTES, cache);
    }

    /// Ensures the access-local cache contains the readable data page for the supplied range.
    private void ensureReadableDataPage(long address, int length) {
        if (!hasCachedDataPage(address, length, Memory.PROTECTION_READ)) {
            memory.readPage(address, length, false, cache, this);
        }
    }

    /// Ensures the access-local cache contains the writable data page for the supplied range.
    private void ensureWritableDataPage(long address, int length) {
        if (!hasCachedWriteDataPage(address, length, Memory.PROTECTION_WRITE)) {
            memory.writePage(address, length, cache, this);
        }
    }

    /// Returns true when the access-local data-page cache satisfies the supplied range and protection.
    private boolean hasCachedDataPage(long address, int length, long requiredProtection) {
        long pageNumber = memory.pageNumber(address);
        return cachedDataPageValid
                && cachedDataPageNumber == pageNumber
                && cachedDataGeneration == memory.generation
                && address >= cachedDataRangeStart
                && length <= cachedDataRangeEnd - address
                && (cachedDataProtection & requiredProtection) == requiredProtection;
    }

    /// Returns true when the access-local write data-page cache satisfies the supplied range and protection.
    private boolean hasCachedWriteDataPage(long address, int length, long requiredProtection) {
        long pageNumber = memory.pageNumber(address);
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
