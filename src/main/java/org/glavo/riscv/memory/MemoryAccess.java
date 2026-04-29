// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.memory;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Provides scalar guest memory access with a pre-resolved software TLB for one host thread.
@NotNullByDefault
public final class MemoryAccess {
    /// The memory object that owns page tables and VMA metadata.
    private final Memory memory;

    /// The software TLB for the current Truffle context and host thread.
    private final @Nullable MappedRegionCache cache;

    /// The access-local readable data-page cache entry.
    private @Nullable CachedPage cachedDataPage;

    /// The access-local writable data-page cache entry.
    private @Nullable CachedPage cachedWriteDataPage;

    /// Creates an access facade for a memory object and optional software TLB.
    MemoryAccess(Memory memory, @Nullable MappedRegionCache cache) {
        this.memory = memory;
        this.cache = cache;
    }

    /// Stores one access-local page cache entry.
    ///
    /// @param pageNumber the guest page number associated with the cached page
    /// @param rangeStart the inclusive guest address where the cached page is valid
    /// @param rangeEnd   the exclusive guest address where the cached page stops being valid
    /// @param protection the access protections available through the cached page
    /// @param generation the memory generation associated with the cached page
    /// @param baseObject the Unsafe base object for the cached page
    /// @param baseOffset the Unsafe byte offset of the cached page start
    @ValueType
    private record CachedPage(
            long pageNumber,
            long rangeStart,
            long rangeEnd,
            long protection,
            long generation,
            @Nullable Object baseObject,
            long baseOffset) {
    }

    /// Reads a signed byte from guest memory using explicit page-layout constants.
    public byte readByte(long address, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        CachedPage cached = ensureReadableDataPage(address, Byte.BYTES, layout);
        return MemoryUnsafe.UNSAFE.getByte(cached.baseObject(), cached.baseOffset() + pageOffset);
    }

    /// Reads an unsigned byte from guest memory using explicit page-layout constants.
    public int readUnsignedByte(long address, MemoryLayout layout) {
        return readByte(address, layout) & 0xff;
    }

    /// Reads a signed little-endian 16-bit value from guest memory using explicit page-layout constants.
    public short readShort(long address, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageShortOffset(pageOffset)) {
            CachedPage cached = ensureReadableDataPage(address, Short.BYTES, layout);
            return MemoryUnsafe.readShortLE(cached.baseObject(), cached.baseOffset() + pageOffset);
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
            CachedPage cached = ensureReadableDataPage(address, Integer.BYTES, layout);
            return MemoryUnsafe.readIntLE(cached.baseObject(), cached.baseOffset() + pageOffset);
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
            CachedPage cached = ensureReadableDataPage(address, Long.BYTES, layout);
            return MemoryUnsafe.readLongLE(cached.baseObject(), cached.baseOffset() + pageOffset);
        }

        return memory.readLittleEndianByBytes(address, Long.BYTES, cache, layout);
    }

    /// Writes a byte to guest memory using explicit page-layout constants.
    public void writeByte(long address, byte value, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        CachedPage cached = ensureWritableDataPage(address, Byte.BYTES, layout);
        MemoryUnsafe.UNSAFE.putByte(cached.baseObject(), cached.baseOffset() + pageOffset, value);
    }

    /// Writes a little-endian 16-bit value to guest memory using explicit page-layout constants.
    public void writeShort(long address, short value, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageShortOffset(pageOffset)) {
            CachedPage cached = ensureWritableDataPage(address, Short.BYTES, layout);
            MemoryUnsafe.writeShortLE(cached.baseObject(), cached.baseOffset() + pageOffset, value);
            return;
        }

        memory.writeLittleEndianByBytes(address, value, Short.BYTES, cache, layout);
    }

    /// Writes a little-endian 32-bit value to guest memory using explicit page-layout constants.
    public void writeInt(long address, int value, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageIntOffset(pageOffset)) {
            CachedPage cached = ensureWritableDataPage(address, Integer.BYTES, layout);
            MemoryUnsafe.writeIntLE(cached.baseObject(), cached.baseOffset() + pageOffset, value);
            return;
        }

        memory.writeLittleEndianByBytes(address, value, Integer.BYTES, cache, layout);
    }

    /// Writes a little-endian 64-bit value to guest memory using explicit page-layout constants.
    public void writeLong(long address, long value, MemoryLayout layout) {
        long pageOffset = layout.pageOffset(address);
        if (layout.isSinglePageLongOffset(pageOffset)) {
            CachedPage cached = ensureWritableDataPage(address, Long.BYTES, layout);
            MemoryUnsafe.writeLongLE(cached.baseObject(), cached.baseOffset() + pageOffset, value);
            return;
        }

        memory.writeLittleEndianByBytes(address, value, Long.BYTES, cache, layout);
    }

    /// Returns an access-local cache entry for the readable data page covering the supplied range.
    private CachedPage ensureReadableDataPage(long address, int length, MemoryLayout layout) {
        @Nullable CachedPage cached = cachedDataPage;
        if (cached == null || !hasCachedPage(cached, address, length, Memory.PROTECTION_READ, layout)) {
            memory.readPage(address, length, false, cache, this, layout);
            cached = cachedDataPage;
            assert cached != null;
        }
        return cached;
    }

    /// Returns an access-local cache entry for the writable data page covering the supplied range.
    private CachedPage ensureWritableDataPage(long address, int length, MemoryLayout layout) {
        @Nullable CachedPage cached = cachedWriteDataPage;
        if (cached == null || !hasCachedPage(cached, address, length, Memory.PROTECTION_WRITE, layout)) {
            memory.writePage(address, length, cache, this, layout);
            cached = cachedWriteDataPage;
            assert cached != null;
        }
        return cached;
    }

    /// Returns true when an access-local page cache entry satisfies the supplied range and protection.
    private boolean hasCachedPage(
            CachedPage cached,
            long address,
            int length,
            long requiredProtection,
            MemoryLayout layout) {
        long pageNumber = layout.pageNumber(address);
        return cached.pageNumber() == pageNumber
                && cached.generation() == memory.generation
                && address >= cached.rangeStart()
                && length <= cached.rangeEnd() - address
                && (cached.protection() & requiredProtection) == requiredProtection;
    }

    /// Stores one data-page lookup in the access-local cache.
    void setDataPage(
            long pageNumber,
            long rangeStart,
            long rangeEnd,
            long protection,
            long generation,
            MemoryPage page) {
        CachedPage cachedPage = new CachedPage(
                pageNumber,
                rangeStart,
                rangeEnd,
                protection,
                generation,
                page.baseObject(),
                page.baseOffset());
        if ((protection & Memory.PROTECTION_READ) != 0) {
            cachedDataPage = cachedPage;
        }
        if ((protection & Memory.PROTECTION_WRITE) != 0) {
            cachedWriteDataPage = cachedPage;
        }
    }

}
