// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.memory;

import com.oracle.truffle.api.ContextThreadLocal;
import org.glavo.riscv.exception.RiscVException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/// Provides Linux-like paged virtual memory for guest address-space accesses.
@NotNullByDefault
public final class Memory implements AutoCloseable {
    /// The default base address used by the bare-metal guest memory image.
    public static final long DEFAULT_BASE_ADDRESS = 0x8000_0000L;

    /// The default base page size.
    public static final int DEFAULT_PAGE_SIZE = 4096;

    /// The smallest supported base page size.
    public static final int MIN_PAGE_SIZE = 1 << 12;

    /// The default huge page size.
    public static final long DEFAULT_HUGE_PAGE_SIZE = 2L * 1024L * 1024L;

    /// The default committed-page limit, where zero means unlimited.
    public static final long DEFAULT_MAX_COMMITTED_PAGES = 0;

    /// The default huge-page pool size.
    public static final long DEFAULT_HUGE_PAGES = 0;

    /// Guest pages cannot be read, written, or executed.
    public static final long PROTECTION_NONE = 0;

    /// Guest pages can be read.
    public static final long PROTECTION_READ = 0x1;

    /// Guest pages can be written.
    public static final long PROTECTION_WRITE = 0x2;

    /// Guest pages can be used for instruction fetch.
    public static final long PROTECTION_EXECUTE = 0x4;

    /// Guest pages support every user-mode access type.
    public static final long PROTECTION_READ_WRITE_EXECUTE =
            PROTECTION_READ | PROTECTION_WRITE | PROTECTION_EXECUTE;

    /// The supported guest page-protection bit mask.
    public static final long SUPPORTED_PROTECTION_MASK = PROTECTION_READ_WRITE_EXECUTE;

    /// The inclusive base address of the guest virtual address window.
    private final long baseAddress;

    /// The byte size of the guest virtual address window.
    private final long size;

    /// The exclusive end address of the guest virtual address window.
    private final long endAddress;

    /// Immutable base page layout parameters.
    private final MemoryLayout layout;

    /// The number of long array elements in one base page.
    private final int pageWords;

    /// Read-only zero-fill page used to cache reads from uncommitted mapped pages.
    private final MemoryPage zeroPage;

    /// The maximum number of committed base pages, or zero when unlimited.
    private final long maxCommittedPages;

    /// The configured huge page size in bytes.
    private final long hugePageSize;

    /// The total number of huge pages available to MAP_HUGETLB mappings.
    private final long hugePageCapacity;

    /// Whether the initial virtual memory window is represented as one mapped VMA.
    private final boolean initialWindowMapped;

    /// The current Truffle context and host thread's most recently accessed memory pages.
    private final @Nullable ContextThreadLocal<MappedRegionCache> cachedMappedRegion;

    /// Committed base pages keyed by guest base-page number.
    private final PageTable pages;

    /// The sorted immutable snapshot of mapped guest VMAs.
    private volatile VmaTable vmas;

    /// The generation used to invalidate per-thread software TLB entries.
    volatile long generation;

    /// The number of currently committed base pages.
    private long committedPages;

    /// The number of reserved huge pages.
    private long reservedHugePages;

    /// Creates a memory window with one mapped VMA and lazy page commitment.
    public Memory(long baseAddress, long size, @Nullable ContextThreadLocal<MappedRegionCache> cachedMappedRegion) {
        this(
                baseAddress,
                size,
                DEFAULT_PAGE_SIZE,
                DEFAULT_MAX_COMMITTED_PAGES,
                DEFAULT_HUGE_PAGE_SIZE,
                DEFAULT_HUGE_PAGES,
                cachedMappedRegion,
                true);
    }

    /// Creates a sparse memory window with no initially mapped VMA.
    public static Memory sparse(
            long baseAddress,
            long size,
            @Nullable ContextThreadLocal<MappedRegionCache> cachedMappedRegion) {
        return sparse(
                baseAddress,
                size,
                DEFAULT_PAGE_SIZE,
                DEFAULT_MAX_COMMITTED_PAGES,
                DEFAULT_HUGE_PAGE_SIZE,
                DEFAULT_HUGE_PAGES,
                cachedMappedRegion);
    }

    /// Creates a sparse memory window with explicit paging limits.
    public static Memory sparse(
            long baseAddress,
            long size,
            long pageSize,
            long maxCommittedPages,
            long hugePageSize,
            long hugePages,
            @Nullable ContextThreadLocal<MappedRegionCache> cachedMappedRegion) {
        return new Memory(baseAddress, size, pageSize, maxCommittedPages, hugePageSize, hugePages, cachedMappedRegion, false);
    }

    /// Creates a memory window with explicit paging limits.
    private Memory(
            long baseAddress,
            long size,
            long pageSize,
            long maxCommittedPages,
            long hugePageSize,
            long hugePages,
            @Nullable ContextThreadLocal<MappedRegionCache> cachedMappedRegion,
            boolean initialWindowMapped) {
        if (baseAddress < 0) {
            throw new RiscVException("Guest memory base address must be non-negative: " + baseAddress);
        }
        if (size <= 0) {
            throw new RiscVException("Guest memory size must be positive: " + size);
        }
        if (baseAddress > Long.MAX_VALUE - size) {
            throw new RiscVException("Guest memory range overflows: base=" + baseAddress + ", size=" + size);
        }
        if (maxCommittedPages < 0) {
            throw new RiscVException("Maximum committed pages must be non-negative: " + maxCommittedPages);
        }
        if (hugePages < 0) {
            throw new RiscVException("Huge page pool size must be non-negative: " + hugePages);
        }

        int validatedPageSize = validatePageSize("page size", pageSize);
        long validatedHugePageSize = validateHugePageSize(validatedPageSize, hugePageSize);
        this.baseAddress = baseAddress;
        this.size = size;
        this.endAddress = baseAddress + size;
        this.layout = MemoryLayout.fromPageSize(validatedPageSize);
        this.pageWords = validatedPageSize >>> 3;
        this.zeroPage = MemoryPage.heap(this.pageWords);
        this.pages = new PageTable(this.layout.pageShift(), this.endAddress);
        this.maxCommittedPages = maxCommittedPages;
        this.hugePageSize = validatedHugePageSize;
        this.hugePageCapacity = hugePages;
        this.initialWindowMapped = initialWindowMapped;
        this.cachedMappedRegion = cachedMappedRegion;
        this.vmas = initialWindowMapped
                ? VmaTable.single(
                        baseAddress,
                        baseAddress + size,
                        false,
                        Vma.HUGE_PAGE_PREFERENCE_DEFAULT,
                        PROTECTION_READ_WRITE_EXECUTE)
                : VmaTable.empty();
    }

    /// Returns the inclusive base address of the guest virtual address window.
    public long baseAddress() {
        return baseAddress;
    }

    /// Returns the guest virtual address window size in bytes.
    public long size() {
        return size;
    }

    /// Returns the exclusive end address of the guest virtual address window.
    public long endAddress() {
        return endAddress;
    }

    /// Returns the base page size in bytes.
    public int pageSize() {
        return layout.pageSize();
    }

    /// Returns immutable base page layout parameters.
    public MemoryLayout layout() {
        return layout;
    }

    /// Returns the configured huge page size in bytes.
    public long hugePageSize() {
        return hugePageSize;
    }

    /// Returns the configured committed base-page limit, or zero when unlimited.
    public long maxCommittedPages() {
        return maxCommittedPages;
    }

    /// Returns the number of committed base pages.
    public synchronized long committedPages() {
        return committedPages;
    }

    /// Returns the configured huge-page pool size.
    public long hugePageCapacity() {
        return hugePageCapacity;
    }

    /// Returns the number of huge pages currently reserved by MAP_HUGETLB mappings.
    public synchronized long reservedHugePages() {
        return reservedHugePages;
    }

    /// Returns true when the initial guest memory window is represented as one mapped VMA.
    public boolean hasDenseInitialBacking() {
        return initialWindowMapped;
    }

    /// Creates a memory access facade bound to the current Truffle context and host thread.
    public MemoryAccess newAccess() {
        @Nullable ContextThreadLocal<MappedRegionCache> cache = cachedMappedRegion;
        return new MemoryAccess(this, cache == null ? null : cache.get());
    }

    /// Copies bytes from a host array into guest memory.
    public void load(long address, byte[] source, int offset, int length) {
        writeBytes(address, source, offset, length);
    }

    /// Fills a mapped guest memory range with zero bytes without committing absent pages.
    public void clear(long address, long length) {
        if (length < 0) {
            throw new RiscVException("Negative memory clear length: " + length);
        }
        if (length == 0) {
            return;
        }
        ensureMappedRange(address, length);

        long end = address + length;
        long cursor = address;
        while (cursor < end) {
            long mappedEnd = mappedRangeEnd(cursor);
            if (mappedEnd <= cursor) {
                throw accessFault(cursor, end - cursor);
            }

            long pageEnd = layout.pageEnd(cursor);
            int count = checkedPageByteCount(cursor, Math.min(end, Math.min(mappedEnd, pageEnd)) - cursor);
            @Nullable MemoryPage page = pages.get(layout.pageNumber(cursor));
            if (page != null) {
                MemoryUnsafe.UNSAFE.setMemory(
                        page.baseObject(),
                        page.byteOffset(layout.pageOffset(cursor)),
                        count,
                        (byte) 0);
            }
            cursor += count;
        }
    }

    /// Reads a signed byte from guest memory.
    public byte readByte(long address) {
        MemoryPage page = readPage(address, Byte.BYTES, false);
        return MemoryUnsafe.UNSAFE.getByte(page.baseObject(), page.byteOffset(layout.pageOffset(address)));
    }

    /// Reads an unsigned byte from guest memory.
    public int readUnsignedByte(long address) {
        return readByte(address) & 0xff;
    }

    /// Reads a signed little-endian 16-bit value from guest memory.
    public short readShort(long address) {
        if (layout.isSinglePageShortAccess(address)) {
            MemoryPage page = readPage(address, Short.BYTES, false);
            return MemoryUnsafe.readShortLE(page.baseObject(), page.byteOffset(layout.pageOffset(address)));
        }

        return (short) readLittleEndianByBytes(address, Short.BYTES);
    }

    /// Reads an unsigned little-endian 16-bit value from guest memory.
    public int readUnsignedShort(long address) {
        return readShort(address) & 0xffff;
    }

    /// Reads a signed little-endian 32-bit value from guest memory.
    public int readInt(long address) {
        if (layout.isSinglePageIntAccess(address)) {
            MemoryPage page = readPage(address, Integer.BYTES, false);
            return MemoryUnsafe.readIntLE(page.baseObject(), page.byteOffset(layout.pageOffset(address)));
        }

        return (int) readLittleEndianByBytes(address, Integer.BYTES);
    }

    /// Reads an unsigned little-endian 32-bit value from guest memory.
    public long readUnsignedInt(long address) {
        return readInt(address) & 0xffff_ffffL;
    }

    /// Reads a little-endian 32-bit instruction from a guest address.
    public int readInstructionInt(long address) {
        if (layout.isSinglePageIntAccess(address)) {
            MemoryPage page = readPage(address, Integer.BYTES, true);
            return MemoryUnsafe.readIntLE(page.baseObject(), page.byteOffset(layout.pageOffset(address)));
        }

        return (int) readLittleEndianByBytes(address, Integer.BYTES, true);
    }

    /// Reads a signed little-endian 64-bit value from guest memory.
    public long readLong(long address) {
        if (layout.isSinglePageLongAccess(address)) {
            MemoryPage page = readPage(address, Long.BYTES, false);
            return MemoryUnsafe.readLongLE(page.baseObject(), page.byteOffset(layout.pageOffset(address)));
        }

        return readLittleEndianByBytes(address, Long.BYTES);
    }

    /// Copies guest memory bytes into a new host byte array.
    public byte[] readBytes(long address, long length) {
        if (length > Integer.MAX_VALUE) {
            throw new RiscVException("Guest memory read range is too large: " + length);
        }
        if (length < 0) {
            throw new RiscVException("Negative memory read length: " + length);
        }

        byte[] result = new byte[(int) length];
        long end = checkedRangeEnd(address, length);
        long cursor = address;
        int destinationOffset = 0;
        while (cursor < end) {
            long mappedEnd = mappedRangeEnd(cursor);
            if (mappedEnd <= cursor) {
                throw accessFault(cursor, end - cursor);
            }

            long pageEnd = layout.pageEnd(cursor);
            int count = checkedPageByteCount(cursor, Math.min(end, Math.min(mappedEnd, pageEnd)) - cursor);
            MemoryPage page = readPage(cursor, count, false);
            MemoryUnsafe.UNSAFE.copyMemory(
                    page.baseObject(),
                    page.byteOffset(layout.pageOffset(cursor)),
                    result,
                    MemoryUnsafe.HEAP_BYTE_ARRAY_BASE_OFFSET + (long) destinationOffset,
                    count);
            cursor += count;
            destinationOffset += count;
        }
        return result;
    }

    /// Copies host bytes into guest memory.
    public void writeBytes(long address, byte[] source, int offset, int length) {
        if (offset < 0 || length < 0 || offset > source.length || length > source.length - offset) {
            throw new IndexOutOfBoundsException("Invalid source slice: offset=" + offset + ", length=" + length);
        }

        long end = checkedRangeEnd(address, length);
        long cursor = address;
        int sourceOffset = offset;
        while (cursor < end) {
            long pageEnd = layout.pageEnd(cursor);
            int count = checkedPageByteCount(cursor, Math.min(end, pageEnd) - cursor);
            MemoryPage page = writePage(cursor, count);
            MemoryUnsafe.UNSAFE.copyMemory(
                    source,
                    MemoryUnsafe.HEAP_BYTE_ARRAY_BASE_OFFSET + (long) sourceOffset,
                    page.baseObject(),
                    page.byteOffset(layout.pageOffset(cursor)),
                    count);
            cursor += count;
            sourceOffset += count;
        }
    }

    /// Writes a byte to guest memory.
    public void writeByte(long address, byte value) {
        MemoryPage page = writePage(address, Byte.BYTES);
        MemoryUnsafe.UNSAFE.putByte(page.baseObject(), page.byteOffset(layout.pageOffset(address)), value);
    }

    /// Writes a little-endian 16-bit value to guest memory.
    public void writeShort(long address, short value) {
        if (layout.isSinglePageShortAccess(address)) {
            MemoryPage page = writePage(address, Short.BYTES);
            MemoryUnsafe.writeShortLE(page.baseObject(), page.byteOffset(layout.pageOffset(address)), value);
            return;
        }

        writeLittleEndianByBytes(address, value, Short.BYTES);
    }

    /// Writes a little-endian 32-bit value to guest memory.
    public void writeInt(long address, int value) {
        if (layout.isSinglePageIntAccess(address)) {
            MemoryPage page = writePage(address, Integer.BYTES);
            MemoryUnsafe.writeIntLE(page.baseObject(), page.byteOffset(layout.pageOffset(address)), value);
            return;
        }

        writeLittleEndianByBytes(address, value, Integer.BYTES);
    }

    /// Writes a little-endian 64-bit value to guest memory.
    public void writeLong(long address, long value) {
        if (layout.isSinglePageLongAccess(address)) {
            MemoryPage page = writePage(address, Long.BYTES);
            MemoryUnsafe.writeLongLE(page.baseObject(), page.byteOffset(layout.pageOffset(address)), value);
            return;
        }

        writeLittleEndianByBytes(address, value, Long.BYTES);
    }

    /// Adds a lazily committed anonymous guest memory VMA.
    public synchronized boolean map(long address, long length) {
        return map(address, length, PROTECTION_READ_WRITE_EXECUTE);
    }

    /// Adds a lazily committed anonymous guest memory VMA with explicit access protection.
    public synchronized boolean map(long address, long length, long protection) {
        return mapInternal(address, length, false, protection);
    }

    /// Adds a lazily committed MAP_HUGETLB guest memory VMA.
    public synchronized boolean mapHuge(long address, long length) {
        return mapHuge(address, length, PROTECTION_READ_WRITE_EXECUTE);
    }

    /// Adds a lazily committed MAP_HUGETLB guest memory VMA with explicit access protection.
    public synchronized boolean mapHuge(long address, long length, long protection) {
        if (!isAligned(address, hugePageSize) || !isAligned(length, hugePageSize)) {
            return false;
        }
        return mapInternal(address, length, true, protection);
    }

    /// Adds a guest memory VMA whose committed pages are backed by Java heap long arrays.
    public synchronized boolean mapHeap(long address, int length) {
        return length > 0 && map(address, length);
    }

    /// Removes mapped VMAs and any committed pages overlapped by the supplied range.
    public synchronized void unmap(long address, long length) {
        if (length == 0) {
            return;
        }
        long end = checkedRangeEnd(address, length);
        if (!fitsWindow(address, length)) {
            throw new RiscVException("Invalid guest memory unmap range: address=0x"
                    + Long.toUnsignedString(address, 16) + ", length=" + length);
        }

        VmaTable oldVmas = vmas;
        ArrayList<Vma> newVmas = new ArrayList<>(oldVmas.size() + 2);
        long releasedHugePages = 0;
        for (int index = 0; index < oldVmas.size(); index++) {
            Vma vma = oldVmas.vma(index);
            if (!rangesOverlap(address, end, vma.address(), vma.endAddress())) {
                newVmas.add(vma);
                continue;
            }

            long removedStart = Math.max(address, vma.address());
            long removedEnd = Math.min(end, vma.endAddress());
            if (vma.huge()) {
                releasedHugePages += hugePagesForLength(removedEnd - removedStart);
            }
            if (vma.address() < removedStart) {
                newVmas.add(new Vma(
                        vma.address(),
                        removedStart,
                        vma.huge(),
                        vma.hugePagePreference(),
                        vma.protection()));
            }
            if (removedEnd < vma.endAddress()) {
                newVmas.add(new Vma(
                        removedEnd,
                        vma.endAddress(),
                        vma.huge(),
                        vma.hugePagePreference(),
                        vma.protection()));
            }
        }

        if (releasedHugePages > reservedHugePages) {
            reservedHugePages = 0;
        } else {
            reservedHugePages -= releasedHugePages;
        }
        removeCommittedPages(address, end);
        vmas = VmaTable.copyOf(newVmas);
        invalidateSoftwareTlb();
    }

    /// Returns true when the supplied guest range is currently mapped by a VMA.
    public boolean isBacked(long address, long length) {
        if (length < 0 || !isValidRange(address, length)) {
            return false;
        }
        return vmas.find(address, length) != null;
    }

    /// Returns the exclusive end address of the mapped VMA containing the supplied address, or the address itself.
    public long backedRangeEnd(long address) {
        @Nullable Vma vma = vmas.find(address, 1);
        return vma == null ? address : vma.endAddress();
    }

    /// Returns the exclusive end address of the first mapped VMA overlapping the supplied range, or the address itself.
    public long overlappingBackingEnd(long address, long length) {
        if (!isValidRange(address, length) || length == 0) {
            return address;
        }
        @Nullable Vma vma = vmas.findOverlap(address, length);
        return vma == null ? address : vma.endAddress();
    }

    /// Records transparent huge-page advice for mapped VMAs overlapped by the supplied range.
    public synchronized void adviseHugePagePreference(long address, long length, boolean enabled) {
        if (length == 0) {
            return;
        }
        long end = checkedRangeEnd(address, length);
        if (!fitsWindow(address, length)) {
            throw new RiscVException("Invalid guest memory advice range: address=0x"
                    + Long.toUnsignedString(address, 16) + ", length=" + length);
        }

        byte preference = enabled ? Vma.HUGE_PAGE_PREFERENCE_ENABLED : Vma.HUGE_PAGE_PREFERENCE_DISABLED;
        VmaTable oldVmas = vmas;
        ArrayList<Vma> newVmas = new ArrayList<>(oldVmas.size() + 2);
        for (int index = 0; index < oldVmas.size(); index++) {
            Vma vma = oldVmas.vma(index);
            if (!rangesOverlap(address, end, vma.address(), vma.endAddress())) {
                newVmas.add(vma);
                continue;
            }

            long adviceStart = Math.max(address, vma.address());
            long adviceEnd = Math.min(end, vma.endAddress());
            if (vma.address() < adviceStart) {
                newVmas.add(new Vma(
                        vma.address(),
                        adviceStart,
                        vma.huge(),
                        vma.hugePagePreference(),
                        vma.protection()));
            }
            newVmas.add(new Vma(adviceStart, adviceEnd, vma.huge(), preference, vma.protection()));
            if (adviceEnd < vma.endAddress()) {
                newVmas.add(new Vma(
                        adviceEnd,
                        vma.endAddress(),
                        vma.huge(),
                        vma.hugePagePreference(),
                        vma.protection()));
            }
        }
        vmas = VmaTable.copyOf(newVmas);
        invalidateSoftwareTlb();
    }

    /// Updates access protection for a mapped VMA range.
    public synchronized boolean protect(long address, long length, long protection) {
        if (length == 0) {
            return true;
        }
        if ((protection & ~SUPPORTED_PROTECTION_MASK) != 0
                || length < 0
                || !fitsWindow(address, length)
                || !isMappedRange(address, length)) {
            return false;
        }

        long end = address + length;
        VmaTable oldVmas = vmas;
        ArrayList<Vma> newVmas = new ArrayList<>(oldVmas.size() + 2);
        for (int index = 0; index < oldVmas.size(); index++) {
            Vma vma = oldVmas.vma(index);
            if (!rangesOverlap(address, end, vma.address(), vma.endAddress())) {
                newVmas.add(vma);
                continue;
            }

            long protectStart = Math.max(address, vma.address());
            long protectEnd = Math.min(end, vma.endAddress());
            if (vma.address() < protectStart) {
                newVmas.add(new Vma(
                        vma.address(),
                        protectStart,
                        vma.huge(),
                        vma.hugePagePreference(),
                        vma.protection()));
            }
            newVmas.add(new Vma(protectStart, protectEnd, vma.huge(), vma.hugePagePreference(), protection));
            if (protectEnd < vma.endAddress()) {
                newVmas.add(new Vma(
                        protectEnd,
                        vma.endAddress(),
                        vma.huge(),
                        vma.hugePagePreference(),
                        vma.protection()));
            }
        }
        vmas = VmaTable.copyOf(newVmas);
        invalidateSoftwareTlb();
        return true;
    }

    /// Releases all committed heap page references held by this memory object.
    @Override
    public synchronized void close() {
        pages.closeAndClear();
        committedPages = 0;
        reservedHugePages = 0;
        vmas = VmaTable.empty();
        invalidateSoftwareTlb();
    }

    /// Returns true when the supplied address range overlaps the supplied guest address.
    public static boolean overlaps(long address, int length, long pointAddress, int pointLength) {
        long end = address + length;
        long pointEnd = pointAddress + pointLength;
        return address < pointEnd && pointAddress < end;
    }

    /// Reads a little-endian value byte-by-byte for cross-page accesses.
    private long readLittleEndianByBytes(long address, int byteCount) {
        return readLittleEndianByBytes(address, byteCount, false);
    }

    /// Reads a little-endian value byte-by-byte for cross-page accesses.
    private long readLittleEndianByBytes(long address, int byteCount, boolean instruction) {
        return readLittleEndianByBytes(address, byteCount, instruction, currentMappedRegionCache(), layout);
    }

    /// Reads a little-endian value byte-by-byte using the supplied software TLB and page layout.
    long readLittleEndianByBytes(
            long address,
            int byteCount,
            @Nullable MappedRegionCache cache,
            MemoryLayout layout) {
        return readLittleEndianByBytes(address, byteCount, false, cache, layout);
    }

    /// Reads a little-endian value byte-by-byte using the supplied software TLB and page layout.
    private long readLittleEndianByBytes(
            long address,
            int byteCount,
            boolean instruction,
            @Nullable MappedRegionCache cache,
            MemoryLayout layout) {
        long value = 0;
        for (int index = 0; index < byteCount; index++) {
            MemoryPage page = readPage(address + index, Byte.BYTES, instruction, cache, null, layout);
            int b = MemoryUnsafe.UNSAFE.getByte(
                    page.baseObject(),
                    page.byteOffset(layout.pageOffset(address + index))) & 0xff;
            value |= (long) b << (index * Byte.SIZE);
        }
        return value;
    }

    /// Writes a little-endian value byte-by-byte for cross-page accesses.
    private void writeLittleEndianByBytes(long address, long value, int byteCount) {
        writeLittleEndianByBytes(address, value, byteCount, currentMappedRegionCache(), layout);
    }

    /// Writes a little-endian value byte-by-byte using the supplied software TLB and page layout.
    void writeLittleEndianByBytes(
            long address,
            long value,
            int byteCount,
            @Nullable MappedRegionCache cache,
            MemoryLayout layout) {
        for (int index = 0; index < byteCount; index++) {
            long currentAddress = address + index;
            MemoryPage page = writePage(currentAddress, Byte.BYTES, cache, null, layout);
            MemoryUnsafe.UNSAFE.putByte(
                    page.baseObject(),
                    page.byteOffset(layout.pageOffset(currentAddress)),
                    (byte) (value >>> (index * Byte.SIZE)));
        }
    }

    /// Adds a VMA to the sorted VMA table.
    private boolean mapInternal(long address, long length, boolean huge, long protection) {
        if (length <= 0
                || (protection & ~SUPPORTED_PROTECTION_MASK) != 0
                || !fitsWindow(address, length)
                || vmas.overlaps(address, length)) {
            return false;
        }
        if (huge) {
            long requestedHugePages = hugePagesForLength(length);
            if (requestedHugePages < 0 || requestedHugePages > hugePageCapacity - reservedHugePages) {
                return false;
            }
            reservedHugePages += requestedHugePages;
        }

        vmas = vmas.insert(new Vma(address, address + length, huge, Vma.HUGE_PAGE_PREFERENCE_DEFAULT, protection));
        invalidateSoftwareTlb();
        return true;
    }

    /// Returns the committed page for a read access, or the shared zero-fill page for uncommitted mapped memory.
    MemoryPage readPage(long address, int length, boolean instruction) {
        return readPage(address, length, instruction, currentMappedRegionCache(), null, layout);
    }

    /// Returns the committed page for a read access using explicit page-layout constants.
    MemoryPage readPage(
            long address,
            int length,
            boolean instruction,
            @Nullable MappedRegionCache cache,
            @Nullable MemoryAccess access,
            MemoryLayout layout) {
        long requiredProtection = instruction ? PROTECTION_EXECUTE : PROTECTION_READ;
        long pageNumber = layout.pageNumber(address);
        @Nullable MemoryPage page = cachedPage(pageNumber, address, length, requiredProtection, instruction, cache, access);
        if (page != null) {
            return page;
        }

        Vma vma = ensureValidMappedRange(address, length, requiredProtection);
        page = pages.get(pageNumber);
        if (page != null) {
            setCachedPage(pageNumber, page, vma, instruction, cache, access);
        } else {
            page = zeroPage;
            setCachedPage(pageNumber, page, vma, instruction, vma.protection() & ~PROTECTION_WRITE, cache, access);
        }
        return page;
    }

    /// Returns a committed page for a write access, allocating it on first write.
    MemoryPage writePage(long address, int length) {
        return writePage(address, length, currentMappedRegionCache(), null, layout);
    }

    /// Returns a committed page for a write access using explicit page-layout constants.
    MemoryPage writePage(
            long address,
            int length,
            @Nullable MappedRegionCache cache,
            @Nullable MemoryAccess access,
            MemoryLayout layout) {
        long pageNumber = layout.pageNumber(address);
        @Nullable MemoryPage page = cachedPage(pageNumber, address, length, PROTECTION_WRITE, false, cache, access);
        if (page != null) {
            return page;
        }

        Vma vma = ensureValidMappedRange(address, length, PROTECTION_WRITE);
        page = pages.get(pageNumber);
        if (page != null) {
            setCachedPage(pageNumber, page, vma, false, cache, access);
            return page;
        }

        page = commitPage(pageNumber);
        setCachedPage(pageNumber, page, vma, false, cache, access);
        return page;
    }

    /// Allocates a heap long-array backing page after enforcing the committed-page limit.
    private synchronized MemoryPage commitPage(long pageNumber) {
        @Nullable MemoryPage page = pages.get(pageNumber);
        if (page != null) {
            return page;
        }
        if (maxCommittedPages != 0 && committedPages >= maxCommittedPages) {
            throw new RiscVException("Guest committed page limit exceeded: limit=" + maxCommittedPages);
        }

        page = MemoryPage.heap(pageWords);
        pages.put(pageNumber, page);
        committedPages++;
        invalidateSoftwareTlb();
        return page;
    }

    /// Removes committed pages overlapped by the supplied guest range.
    private void removeCommittedPages(long startAddress, long endAddress) {
        long startPageNumber = layout.pageNumber(startAddress);
        long endPageNumber = layout.pageNumber(endAddress - 1L) + 1L;
        committedPages -= pages.removeRange(startPageNumber, endPageNumber);
    }

    /// Returns the VMA containing a valid guest access range with the required access protection.
    private Vma ensureValidMappedRange(long address, long length, long requiredProtection) {
        checkedRangeEnd(address, length);
        @Nullable Vma vma = length == 0 ? null : vmas.find(address, length);
        if (vma == null || (vma.protection() & requiredProtection) != requiredProtection) {
            throw accessFault(address, length);
        }
        return vma;
    }

    /// Ensures that a guest range is mapped without checking user-mode access protection.
    private void ensureMappedRange(long address, long length) {
        checkedRangeEnd(address, length);
        if (length == 0) {
            return;
        }
        if (!isMappedRange(address, length)) {
            throw accessFault(address, length);
        }
    }

    /// Returns true when every byte in the supplied range is covered by a mapped VMA.
    private boolean isMappedRange(long address, long length) {
        if (!isValidRange(address, length)) {
            return false;
        }

        long end = address + length;
        long cursor = address;
        while (cursor < end) {
            @Nullable Vma vma = vmas.find(cursor, 1);
            if (vma == null) {
                return false;
            }
            cursor = Math.min(end, vma.endAddress());
        }
        return true;
    }

    /// Returns the mapped range end for the VMA containing the supplied address, or the address itself.
    private long mappedRangeEnd(long address) {
        @Nullable Vma vma = vmas.find(address, 1);
        return vma == null ? address : vma.endAddress();
    }

    /// Returns the cached committed page for the current context and thread, or null on miss.
    private @Nullable MemoryPage cachedPage(
            long pageNumber,
            long address,
            int length,
            long requiredProtection,
            boolean instruction,
            @Nullable MappedRegionCache cache,
            @Nullable MemoryAccess access) {
        return cache == null ? null : cache.page(pageNumber, address, length, requiredProtection, generation, instruction, access);
    }

    /// Stores a committed page in the supplied software TLB and access-local cache.
    private void setCachedPage(
            long pageNumber,
            MemoryPage page,
            Vma vma,
            boolean instruction,
            @Nullable MappedRegionCache cache,
            @Nullable MemoryAccess access) {
        setCachedPage(pageNumber, page, vma, instruction, vma.protection(), cache, access);
    }

    /// Stores a committed page in the supplied software TLB and access-local cache with an explicit protection mask.
    private void setCachedPage(
            long pageNumber,
            MemoryPage page,
            Vma vma,
            boolean instruction,
            long protection,
            @Nullable MappedRegionCache cache,
            @Nullable MemoryAccess access) {
        long pageStart = pageNumber << layout.pageShift();
        long rangeStart = Math.max(vma.address(), pageStart);
        long rangeEnd = Math.min(vma.endAddress(), pageStart + layout.pageSize());
        if (cache != null) {
            cache.setPage(pageNumber, rangeStart, rangeEnd, protection, generation, page, instruction);
        }
        if (!instruction && access != null) {
            access.setDataPage(pageNumber, rangeStart, rangeEnd, protection, generation, page);
        }
    }

    /// Returns the current Truffle context and host thread's software TLB, or null outside a Truffle context.
    private @Nullable MappedRegionCache currentMappedRegionCache() {
        @Nullable ContextThreadLocal<MappedRegionCache> cache = cachedMappedRegion;
        return cache == null ? null : cache.get();
    }

    /// Invalidates every future software TLB lookup by advancing the global generation.
    private void invalidateSoftwareTlb() {
        generation++;
    }

    /// Returns a RISC-V memory fault for the supplied guest range.
    private static RiscVException accessFault(long address, long length) {
        return new RiscVException("Guest memory access out of range: address=0x"
                + Long.toUnsignedString(address, 16) + ", length=" + length);
    }

    /// Returns a checked byte count within one page.
    private static int checkedPageByteCount(long address, long length) {
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw new RiscVException("Invalid page access length: address=0x"
                    + Long.toUnsignedString(address, 16) + ", length=" + length);
        }
        return (int) length;
    }

    /// Returns the checked exclusive end address for a guest range.
    private static long checkedRangeEnd(long address, long length) {
        if (!isValidRange(address, length)) {
            throw new RiscVException("Invalid guest memory range: address=0x"
                    + Long.toUnsignedString(address, 16) + ", length=" + length);
        }
        return address + length;
    }

    /// Returns true when the supplied range is non-negative and does not overflow.
    private static boolean isValidRange(long address, long length) {
        return address >= 0 && length >= 0 && address <= Long.MAX_VALUE - length;
    }

    /// Returns true when the supplied guest range fits inside the configured virtual window.
    private boolean fitsWindow(long address, long length) {
        return address >= baseAddress
                && length >= 0
                && address <= endAddress
                && length <= endAddress - address;
    }

    /// Returns true when two half-open address ranges overlap.
    private static boolean rangesOverlap(long firstStart, long firstEnd, long secondStart, long secondEnd) {
        return firstStart < secondEnd && secondStart < firstEnd;
    }

    /// Returns true when value is aligned to a power-of-two alignment.
    private static boolean isAligned(long value, long alignment) {
        return (value & (alignment - 1L)) == 0;
    }

    /// Returns the number of huge pages needed to cover a length.
    private long hugePagesForLength(long length) {
        long mask = hugePageSize - 1L;
        if (length < 0 || length > Long.MAX_VALUE - mask) {
            return -1;
        }
        return ((length + mask) & ~mask) / hugePageSize;
    }

    /// Validates a power-of-two base page size that can back a single Java array.
    private static int validatePageSize(String name, long pageSize) {
        if (pageSize < MIN_PAGE_SIZE || pageSize > Integer.MAX_VALUE || !isPowerOfTwo(pageSize)) {
            throw new RiscVException("Guest " + name + " must be a power of two between "
                    + MIN_PAGE_SIZE + " and " + Integer.MAX_VALUE + ": " + pageSize);
        }
        return (int) pageSize;
    }

    /// Validates a huge page size against the base page size.
    private static long validateHugePageSize(int pageSize, long hugePageSize) {
        if (hugePageSize < pageSize || !isPowerOfTwo(hugePageSize)) {
            throw new RiscVException("Guest huge page size must be a power of two at least page size: "
                    + hugePageSize);
        }
        return hugePageSize;
    }

    /// Returns true when value is a positive power of two.
    private static boolean isPowerOfTwo(long value) {
        return value > 0 && (value & (value - 1L)) == 0;
    }

}
