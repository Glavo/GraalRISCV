// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.memory;

import com.oracle.truffle.api.ContextThreadLocal;
import jdk.internal.misc.Unsafe;
import org.glavo.riscv.exception.RiscVException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

/// Provides Linux-like paged virtual memory for guest address-space accesses.
@NotNullByDefault
public final class Memory implements AutoCloseable {
    /// The default base address used by the bare-metal guest memory image.
    public static final long DEFAULT_BASE_ADDRESS = 0x8000_0000L;

    /// The default base page size.
    public static final int DEFAULT_PAGE_SIZE = 4096;

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

    /// The Unsafe instance used to access heap page backing without MemorySegment overhead.
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /// The byte offset of the first element in a Java long array used by the default heap page allocator.
    private static final long HEAP_LONG_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(long[].class);

    /// Whether the host CPU uses little-endian primitive layout.
    private static final boolean NATIVE_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    /// No transparent huge-page preference has been recorded for a VMA.
    private static final byte HUGE_PAGE_PREFERENCE_DEFAULT = 0;

    /// The VMA prefers transparent huge pages.
    private static final byte HUGE_PAGE_PREFERENCE_ENABLED = 1;

    /// The VMA opts out of transparent huge pages.
    private static final byte HUGE_PAGE_PREFERENCE_DISABLED = 2;

    /// The initial number of slots in the committed page table.
    private static final int INITIAL_PAGE_TABLE_CAPACITY = 64;

    /// The inclusive base address of the guest virtual address window.
    private final long baseAddress;

    /// The byte size of the guest virtual address window.
    private final long size;

    /// The exclusive end address of the guest virtual address window.
    private final long endAddress;

    /// The base page size in bytes.
    private final int pageSize;

    /// The right shift converting a guest address to a base-page number.
    private final int pageShift;

    /// The bit mask selecting the byte offset inside a base page.
    private final long pageMask;

    /// The number of long array elements in one base page.
    private final int pageWords;

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
    private final PageTable pages = new PageTable();

    /// The sorted immutable snapshot of mapped guest VMAs.
    private volatile VmaTable vmas;

    /// The generation used to invalidate per-thread software TLB entries.
    private volatile long generation;

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
        this.pageSize = validatedPageSize;
        this.pageShift = Integer.numberOfTrailingZeros(validatedPageSize);
        this.pageMask = validatedPageSize - 1L;
        this.pageWords = validatedPageSize / Long.BYTES;
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
                        HUGE_PAGE_PREFERENCE_DEFAULT,
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
        return pageSize;
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

            long pageEnd = pageEnd(cursor);
            int count = checkedPageByteCount(cursor, Math.min(end, Math.min(mappedEnd, pageEnd)) - cursor);
            @Nullable Page page = pages.get(pageNumber(cursor));
            if (page != null) {
                UNSAFE.setMemory(page.baseObject(), page.byteOffset(pageOffset(cursor)), count, (byte) 0);
            }
            cursor += count;
        }
    }

    /// Reads a signed byte from guest memory.
    public byte readByte(long address) {
        @Nullable Page page = readPage(address, Byte.BYTES, false);
        return page == null ? 0 : UNSAFE.getByte(page.baseObject(), page.byteOffset(pageOffset(address)));
    }

    /// Reads an unsigned byte from guest memory.
    public int readUnsignedByte(long address) {
        return readByte(address) & 0xff;
    }

    /// Reads a signed little-endian 16-bit value from guest memory.
    public short readShort(long address) {
        if (isSinglePageAccess(address, Short.BYTES)) {
            @Nullable Page page = readPage(address, Short.BYTES, false);
            if (page == null) {
                return 0;
            }

            short value = UNSAFE.getShort(page.baseObject(), page.byteOffset(pageOffset(address)));
            return NATIVE_LITTLE_ENDIAN ? value : Short.reverseBytes(value);
        }

        return (short) readLittleEndianByBytes(address, Short.BYTES);
    }

    /// Reads an unsigned little-endian 16-bit value from guest memory.
    public int readUnsignedShort(long address) {
        return readShort(address) & 0xffff;
    }

    /// Reads a signed little-endian 32-bit value from guest memory.
    public int readInt(long address) {
        if (isSinglePageAccess(address, Integer.BYTES)) {
            @Nullable Page page = readPage(address, Integer.BYTES, false);
            if (page == null) {
                return 0;
            }

            int value = UNSAFE.getInt(page.baseObject(), page.byteOffset(pageOffset(address)));
            return NATIVE_LITTLE_ENDIAN ? value : Integer.reverseBytes(value);
        }

        return (int) readLittleEndianByBytes(address, Integer.BYTES);
    }

    /// Reads an unsigned little-endian 32-bit value from guest memory.
    public long readUnsignedInt(long address) {
        return readInt(address) & 0xffff_ffffL;
    }

    /// Reads a little-endian 32-bit instruction from a guest address.
    public int readInstructionInt(long address) {
        if (isSinglePageAccess(address, Integer.BYTES)) {
            @Nullable Page page = readPage(address, Integer.BYTES, true);
            if (page == null) {
                return 0;
            }

            int value = UNSAFE.getInt(page.baseObject(), page.byteOffset(pageOffset(address)));
            return NATIVE_LITTLE_ENDIAN ? value : Integer.reverseBytes(value);
        }

        return (int) readLittleEndianByBytes(address, Integer.BYTES);
    }

    /// Reads a signed little-endian 64-bit value from guest memory.
    public long readLong(long address) {
        if (isSinglePageAccess(address, Long.BYTES)) {
            @Nullable Page page = readPage(address, Long.BYTES, false);
            if (page == null) {
                return 0;
            }

            long value = UNSAFE.getLong(page.baseObject(), page.byteOffset(pageOffset(address)));
            return NATIVE_LITTLE_ENDIAN ? value : Long.reverseBytes(value);
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

            long pageEnd = pageEnd(cursor);
            int count = checkedPageByteCount(cursor, Math.min(end, Math.min(mappedEnd, pageEnd)) - cursor);
            @Nullable Page page = readPage(cursor, count, false);
            if (page != null) {
                UNSAFE.copyMemory(
                        page.baseObject(),
                        page.byteOffset(pageOffset(cursor)),
                        result,
                        Unsafe.ARRAY_BYTE_BASE_OFFSET + (long) destinationOffset,
                        count);
            }
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
            long pageEnd = pageEnd(cursor);
            int count = checkedPageByteCount(cursor, Math.min(end, pageEnd) - cursor);
            Page page = writePage(cursor, count);
            UNSAFE.copyMemory(
                    source,
                    Unsafe.ARRAY_BYTE_BASE_OFFSET + (long) sourceOffset,
                    page.baseObject(),
                    page.byteOffset(pageOffset(cursor)),
                    count);
            cursor += count;
            sourceOffset += count;
        }
    }

    /// Writes a byte to guest memory.
    public void writeByte(long address, byte value) {
        Page page = writePage(address, Byte.BYTES);
        UNSAFE.putByte(page.baseObject(), page.byteOffset(pageOffset(address)), value);
    }

    /// Writes a little-endian 16-bit value to guest memory.
    public void writeShort(long address, short value) {
        if (isSinglePageAccess(address, Short.BYTES)) {
            Page page = writePage(address, Short.BYTES);
            short stored = NATIVE_LITTLE_ENDIAN ? value : Short.reverseBytes(value);
            UNSAFE.putShort(page.baseObject(), page.byteOffset(pageOffset(address)), stored);
            return;
        }

        writeLittleEndianByBytes(address, value, Short.BYTES);
    }

    /// Writes a little-endian 32-bit value to guest memory.
    public void writeInt(long address, int value) {
        if (isSinglePageAccess(address, Integer.BYTES)) {
            Page page = writePage(address, Integer.BYTES);
            int stored = NATIVE_LITTLE_ENDIAN ? value : Integer.reverseBytes(value);
            UNSAFE.putInt(page.baseObject(), page.byteOffset(pageOffset(address)), stored);
            return;
        }

        writeLittleEndianByBytes(address, value, Integer.BYTES);
    }

    /// Writes a little-endian 64-bit value to guest memory.
    public void writeLong(long address, long value) {
        if (isSinglePageAccess(address, Long.BYTES)) {
            Page page = writePage(address, Long.BYTES);
            long stored = NATIVE_LITTLE_ENDIAN ? value : Long.reverseBytes(value);
            UNSAFE.putLong(page.baseObject(), page.byteOffset(pageOffset(address)), stored);
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

        byte preference = enabled ? HUGE_PAGE_PREFERENCE_ENABLED : HUGE_PAGE_PREFERENCE_DISABLED;
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
        long value = 0;
        for (int index = 0; index < byteCount; index++) {
            value |= (long) readUnsignedByte(address + index) << (index * Byte.SIZE);
        }
        return value;
    }

    /// Writes a little-endian value byte-by-byte for cross-page accesses.
    private void writeLittleEndianByBytes(long address, long value, int byteCount) {
        for (int index = 0; index < byteCount; index++) {
            writeByte(address + index, (byte) (value >>> (index * Byte.SIZE)));
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

        vmas = vmas.insert(new Vma(address, address + length, huge, HUGE_PAGE_PREFERENCE_DEFAULT, protection));
        invalidateSoftwareTlb();
        return true;
    }

    /// Returns the committed page for a read access, or null for mapped zero-fill memory.
    private @Nullable Page readPage(long address, int length, boolean instruction) {
        long requiredProtection = instruction ? PROTECTION_EXECUTE : PROTECTION_READ;
        long pageNumber = pageNumber(address);
        @Nullable Page page = cachedPage(pageNumber, address, length, requiredProtection, instruction);
        if (page != null) {
            return page;
        }

        Vma vma = ensureValidMappedRange(address, length, requiredProtection);
        page = pages.get(pageNumber);
        if (page != null) {
            setCachedPage(pageNumber, page, vma, instruction);
        }
        return page;
    }

    /// Returns a committed page for a write access, allocating it on first write.
    private Page writePage(long address, int length) {
        long pageNumber = pageNumber(address);
        @Nullable Page page = cachedPage(pageNumber, address, length, PROTECTION_WRITE, false);
        if (page != null) {
            return page;
        }

        Vma vma = ensureValidMappedRange(address, length, PROTECTION_WRITE);
        page = pages.get(pageNumber);
        if (page != null) {
            setCachedPage(pageNumber, page, vma, false);
            return page;
        }

        page = commitPage(pageNumber);
        setCachedPage(pageNumber, page, vma, false);
        return page;
    }

    /// Allocates a heap long-array backing page after enforcing the committed-page limit.
    private synchronized Page commitPage(long pageNumber) {
        @Nullable Page page = pages.get(pageNumber);
        if (page != null) {
            return page;
        }
        if (maxCommittedPages != 0 && committedPages >= maxCommittedPages) {
            throw new RiscVException("Guest committed page limit exceeded: limit=" + maxCommittedPages);
        }

        page = Page.heap(pageWords);
        pages.put(pageNumber, page);
        committedPages++;
        return page;
    }

    /// Removes committed pages overlapped by the supplied guest range.
    private void removeCommittedPages(long startAddress, long endAddress) {
        long startPageNumber = pageNumber(startAddress);
        long endPageNumber = pageNumber(endAddress - 1L) + 1L;
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
    private @Nullable Page cachedPage(
            long pageNumber,
            long address,
            int length,
            long requiredProtection,
            boolean instruction) {
        @Nullable ContextThreadLocal<MappedRegionCache> cache = cachedMappedRegion;
        return cache == null
                ? null
                : cache.get().page(pageNumber, address, length, requiredProtection, generation, instruction);
    }

    /// Stores a committed page in the current context and thread software TLB.
    private void setCachedPage(long pageNumber, Page page, Vma vma, boolean instruction) {
        @Nullable ContextThreadLocal<MappedRegionCache> cache = cachedMappedRegion;
        if (cache != null) {
            long pageStart = pageNumber << pageShift;
            long rangeStart = Math.max(vma.address(), pageStart);
            long rangeEnd = Math.min(vma.endAddress(), pageStart + pageSize);
            cache.get().setPage(pageNumber, rangeStart, rangeEnd, vma.protection(), generation, page, instruction);
        }
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

    /// Returns the guest page number containing the supplied address.
    private long pageNumber(long address) {
        return address >>> pageShift;
    }

    /// Returns the byte offset inside the guest page containing the supplied address.
    private long pageOffset(long address) {
        return address & pageMask;
    }

    /// Returns the exclusive end address of the page containing the supplied address.
    private long pageEnd(long address) {
        return (address & ~pageMask) + pageSize;
    }

    /// Returns true when a scalar access stays within one base page.
    private boolean isSinglePageAccess(long address, int length) {
        return ((address & pageMask) + length) <= pageSize;
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
        if (pageSize < DEFAULT_PAGE_SIZE || pageSize > Integer.MAX_VALUE || !isPowerOfTwo(pageSize)) {
            throw new RiscVException("Guest " + name + " must be a power of two between "
                    + DEFAULT_PAGE_SIZE + " and " + Integer.MAX_VALUE + ": " + pageSize);
        }
        if ((pageSize & (Long.BYTES - 1L)) != 0) {
            throw new RiscVException("Guest " + name + " must be aligned to long elements: " + pageSize);
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

    /// Stores committed pages in a primitive long-key hash table.
    @NotNullByDefault
    private static final class PageTable {
        /// A page-table slot that has never contained a page.
        private static final byte EMPTY = 0;

        /// A page-table slot currently containing a page.
        private static final byte OCCUPIED = 1;

        /// A page-table slot that previously contained a page.
        private static final byte REMOVED = 2;

        /// Guest page numbers for occupied slots.
        private long[] pageNumbers = new long[INITIAL_PAGE_TABLE_CAPACITY];

        /// Slot states parallel to `pageNumbers`.
        private byte[] states = new byte[INITIAL_PAGE_TABLE_CAPACITY];

        /// Committed pages parallel to `pageNumbers`.
        private @Nullable Page[] pages = new Page[INITIAL_PAGE_TABLE_CAPACITY];

        /// The number of occupied slots.
        private int size;

        /// The number of occupied or removed slots.
        private int usedSlots;

        /// Returns the committed page for a guest page number, or null when absent.
        private synchronized @Nullable Page get(long pageNumber) {
            int index = lookupSlot(pageNumber);
            return index < 0 ? null : pages[index];
        }

        /// Stores a committed page for a guest page number.
        private synchronized void put(long pageNumber, Page page) {
            ensureInsertCapacity();

            int firstRemoved = -1;
            int index = hashIndex(pageNumber, pageNumbers.length);
            while (states[index] != EMPTY) {
                byte state = states[index];
                if (state == OCCUPIED && pageNumbers[index] == pageNumber) {
                    pages[index] = page;
                    return;
                }
                if (state == REMOVED && firstRemoved < 0) {
                    firstRemoved = index;
                }
                index = (index + 1) & (pageNumbers.length - 1);
            }

            int insertionIndex = firstRemoved >= 0 ? firstRemoved : index;
            if (states[insertionIndex] == EMPTY) {
                usedSlots++;
            }
            states[insertionIndex] = OCCUPIED;
            pageNumbers[insertionIndex] = pageNumber;
            pages[insertionIndex] = page;
            size++;
        }

        /// Removes, closes, and counts pages with guest page numbers in the supplied half-open range.
        private synchronized long removeRange(long startPageNumber, long endPageNumber) {
            if (startPageNumber >= endPageNumber) {
                return 0;
            }

            long removedPages = 0;
            for (int index = 0; index < pages.length; index++) {
                if (states[index] != OCCUPIED) {
                    continue;
                }

                long pageNumber = pageNumbers[index];
                if (pageNumber < startPageNumber || pageNumber >= endPageNumber) {
                    continue;
                }

                @Nullable Page page = pages[index];
                states[index] = REMOVED;
                pages[index] = null;
                size--;
                removedPages++;
                if (page != null) {
                    page.close();
                }
            }

            if (removedPages > 0 && usedSlots > Math.max(INITIAL_PAGE_TABLE_CAPACITY, size * 2)) {
                rehash(pageNumbers.length);
            }
            return removedPages;
        }

        /// Closes every committed page and resets the table to its initial empty state.
        private synchronized void closeAndClear() {
            for (int index = 0; index < pages.length; index++) {
                if (states[index] != OCCUPIED) {
                    continue;
                }

                @Nullable Page page = pages[index];
                if (page != null) {
                    page.close();
                }
            }

            pageNumbers = new long[INITIAL_PAGE_TABLE_CAPACITY];
            states = new byte[INITIAL_PAGE_TABLE_CAPACITY];
            pages = new Page[INITIAL_PAGE_TABLE_CAPACITY];
            size = 0;
            usedSlots = 0;
        }

        /// Returns the occupied slot for a page number, or -1 when absent.
        private int lookupSlot(long pageNumber) {
            int index = hashIndex(pageNumber, pageNumbers.length);
            while (states[index] != EMPTY) {
                if (states[index] == OCCUPIED && pageNumbers[index] == pageNumber) {
                    return index;
                }
                index = (index + 1) & (pageNumbers.length - 1);
            }
            return -1;
        }

        /// Ensures at least one more page can be inserted without excessive probing.
        private void ensureInsertCapacity() {
            if ((usedSlots + 1L) * 4L < pageNumbers.length * 3L) {
                return;
            }

            int newCapacity = size * 2 >= pageNumbers.length ? doubledCapacity(pageNumbers.length) : pageNumbers.length;
            rehash(newCapacity);
        }

        /// Rebuilds the table with the supplied power-of-two capacity.
        private void rehash(int newCapacity) {
            long[] oldPageNumbers = pageNumbers;
            byte[] oldStates = states;
            @Nullable Page[] oldPages = pages;

            pageNumbers = new long[newCapacity];
            states = new byte[newCapacity];
            pages = new Page[newCapacity];
            size = 0;
            usedSlots = 0;

            for (int index = 0; index < oldPages.length; index++) {
                @Nullable Page page = oldPages[index];
                if (oldStates[index] == OCCUPIED && page != null) {
                    insertRehashed(oldPageNumbers[index], page);
                }
            }
        }

        /// Inserts an existing page while rebuilding the table.
        private void insertRehashed(long pageNumber, Page page) {
            int index = hashIndex(pageNumber, pageNumbers.length);
            while (states[index] == OCCUPIED) {
                index = (index + 1) & (pageNumbers.length - 1);
            }
            states[index] = OCCUPIED;
            pageNumbers[index] = pageNumber;
            pages[index] = page;
            size++;
            usedSlots++;
        }

        /// Returns a doubled power-of-two capacity or fails when the table would exceed Java array limits.
        private static int doubledCapacity(int capacity) {
            if (capacity >= (1 << 30)) {
                throw new RiscVException("Guest committed page table is too large");
            }
            return capacity << 1;
        }

        /// Returns the hash-table index for a page number.
        private static int hashIndex(long pageNumber, int capacity) {
            return (int) mix64(pageNumber) & (capacity - 1);
        }

        /// Mixes a 64-bit page number for open-addressed probing.
        private static long mix64(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdL;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53L;
            value ^= value >>> 33;
            return value;
        }
    }

    /// Stores mutable software TLB state for one Truffle context and host thread.
    public static final class MappedRegionCache {
        /// The number of cached data pages.
        private static final int DATA_CACHE_SIZE = 4;

        /// The number of cached instruction pages.
        private static final int INSTRUCTION_CACHE_SIZE = 2;

        /// Cached data page numbers ordered from most to least recently used.
        private final long[] dataPageNumbers = new long[DATA_CACHE_SIZE];

        /// Inclusive valid guest address starts for cached data pages.
        private final long[] dataRangeStarts = new long[DATA_CACHE_SIZE];

        /// Exclusive valid guest address ends for cached data pages.
        private final long[] dataRangeEnds = new long[DATA_CACHE_SIZE];

        /// Access protections associated with cached data pages.
        private final long[] dataProtections = new long[DATA_CACHE_SIZE];

        /// Memory generations associated with cached data pages.
        private final long[] dataGenerations = new long[DATA_CACHE_SIZE];

        /// Cached committed data pages ordered from most to least recently used.
        private final @Nullable Page[] dataPages = new Page[DATA_CACHE_SIZE];

        /// Cached instruction page numbers ordered from most to least recently used.
        private final long[] instructionPageNumbers = new long[INSTRUCTION_CACHE_SIZE];

        /// Inclusive valid guest address starts for cached instruction pages.
        private final long[] instructionRangeStarts = new long[INSTRUCTION_CACHE_SIZE];

        /// Exclusive valid guest address ends for cached instruction pages.
        private final long[] instructionRangeEnds = new long[INSTRUCTION_CACHE_SIZE];

        /// Access protections associated with cached instruction pages.
        private final long[] instructionProtections = new long[INSTRUCTION_CACHE_SIZE];

        /// Memory generations associated with cached instruction pages.
        private final long[] instructionGenerations = new long[INSTRUCTION_CACHE_SIZE];

        /// Cached committed instruction pages ordered from most to least recently used.
        private final @Nullable Page[] instructionPages = new Page[INSTRUCTION_CACHE_SIZE];

        /// Creates an empty software TLB.
        public MappedRegionCache() {
        }

        /// Returns a cached page, or null when the lookup misses.
        private @Nullable Page page(
                long pageNumber,
                long address,
                int length,
                long requiredProtection,
                long generation,
                boolean instruction) {
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
                            instructionPages)
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
                            dataPages);
        }

        /// Stores a cached committed page.
        private void setPage(
                long pageNumber,
                long rangeStart,
                long rangeEnd,
                long protection,
                long generation,
                Page page,
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

        /// Returns a cached page from an LRU page cache.
        private static @Nullable Page page(
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
                @Nullable Page[] pages) {
            for (int index = 0; index < pages.length; index++) {
                @Nullable Page page = pages[index];
                if (page != null
                        && pageNumbers[index] == pageNumber
                        && generations[index] == generation
                        && address >= rangeStarts[index]
                        && length <= rangeEnds[index] - address
                        && (protections[index] & requiredProtection) == requiredProtection) {
                    promote(index, pageNumbers, rangeStarts, rangeEnds, protections, generations, pages);
                    return page;
                }
            }
            return null;
        }

        /// Stores a page in an LRU page cache.
        private static void setPage(
                long pageNumber,
                long rangeStart,
                long rangeEnd,
                long protection,
                long generation,
                Page page,
                long[] pageNumbers,
                long[] rangeStarts,
                long[] rangeEnds,
                long[] protections,
                long[] generations,
                @Nullable Page[] pages) {
            for (int index = 0; index < pages.length; index++) {
                if (pages[index] == page) {
                    pageNumbers[index] = pageNumber;
                    rangeStarts[index] = rangeStart;
                    rangeEnds[index] = rangeEnd;
                    protections[index] = protection;
                    generations[index] = generation;
                    promote(index, pageNumbers, rangeStarts, rangeEnds, protections, generations, pages);
                    return;
                }
            }

            for (int index = pages.length - 1; index > 0; index--) {
                pageNumbers[index] = pageNumbers[index - 1];
                rangeStarts[index] = rangeStarts[index - 1];
                rangeEnds[index] = rangeEnds[index - 1];
                protections[index] = protections[index - 1];
                generations[index] = generations[index - 1];
                pages[index] = pages[index - 1];
            }
            pageNumbers[0] = pageNumber;
            rangeStarts[0] = rangeStart;
            rangeEnds[0] = rangeEnd;
            protections[0] = protection;
            generations[0] = generation;
            pages[0] = page;
        }

        /// Moves a cache entry to the most recently used slot.
        private static void promote(
                int index,
                long[] pageNumbers,
                long[] rangeStarts,
                long[] rangeEnds,
                long[] protections,
                long[] generations,
                @Nullable Page[] pages) {
            if (index == 0) {
                return;
            }

            long pageNumber = pageNumbers[index];
            long rangeStart = rangeStarts[index];
            long rangeEnd = rangeEnds[index];
            long protection = protections[index];
            long generation = generations[index];
            @Nullable Page page = pages[index];
            for (int current = index; current > 0; current--) {
                pageNumbers[current] = pageNumbers[current - 1];
                rangeStarts[current] = rangeStarts[current - 1];
                rangeEnds[current] = rangeEnds[current - 1];
                protections[current] = protections[current - 1];
                generations[current] = generations[current - 1];
                pages[current] = pages[current - 1];
            }
            pageNumbers[0] = pageNumber;
            rangeStarts[0] = rangeStart;
            rangeEnds[0] = rangeEnd;
            protections[0] = protection;
            generations[0] = generation;
            pages[0] = page;
        }
    }

    /// Stores one committed guest page with replaceable backing.
    @NotNullByDefault
    private static final class Page {
        /// The Unsafe base object, or null when baseOffset is an absolute native address.
        private final @Nullable Object baseObject;

        /// The Unsafe byte offset of the first byte in the page.
        private final long baseOffset;

        /// An object retained for the lifetime of the page backing.
        private final @Nullable Object owner;

        /// The optional release action for non-GC-managed backing.
        private final @Nullable Runnable closeAction;

        /// Creates a committed guest page with the supplied Unsafe access coordinates.
        private Page(
                @Nullable Object baseObject,
                long baseOffset,
                @Nullable Object owner,
                @Nullable Runnable closeAction) {
            this.baseObject = baseObject;
            this.baseOffset = baseOffset;
            this.owner = owner;
            this.closeAction = closeAction;
        }

        /// Creates a committed guest page backed by a zero-filled heap long array.
        private static Page heap(int pageWords) {
            long[] data = new long[pageWords];
            return new Page(data, HEAP_LONG_ARRAY_BASE_OFFSET, data, null);
        }

        /// Returns the Unsafe base object, or null for absolute native-address backing.
        private @Nullable Object baseObject() {
            return baseObject;
        }

        /// Returns the Unsafe byte offset for an access at the supplied page-relative byte offset.
        private long byteOffset(long pageOffset) {
            return baseOffset + pageOffset;
        }

        /// Releases resources owned by this page backing.
        private void close() {
            @Nullable Runnable action = this.closeAction;
            if (action != null) {
                action.run();
            }
        }
    }

    /// Describes a guest virtual memory area.
    ///
    /// @param address the inclusive guest start address of the VMA
    /// @param endAddress the exclusive guest end address of the VMA
    /// @param huge whether this VMA reserves MAP_HUGETLB pages
    /// @param hugePagePreference the transparent huge-page advice recorded for this VMA
    /// @param protection the supported guest access-protection flags for this VMA
    private record Vma(
            long address,
            long endAddress,
            boolean huge,
            byte hugePagePreference,
            long protection) {
    }

    /// Stores sorted guest VMAs in parallel arrays.
    ///
    /// @param addresses the inclusive guest start addresses for the VMAs
    /// @param endAddresses the exclusive guest end addresses for the VMAs
    /// @param huge whether each VMA reserves MAP_HUGETLB pages
    /// @param hugePagePreferences the transparent huge-page advice for each VMA
    /// @param protections the supported guest access-protection flags for each VMA
    private record VmaTable(
            long @Unmodifiable [] addresses,
            long @Unmodifiable [] endAddresses,
            boolean @Unmodifiable [] huge,
            byte @Unmodifiable [] hugePagePreferences,
            long @Unmodifiable [] protections) {
        /// Creates a VMA snapshot backed by same-length sorted arrays.
        private VmaTable {
            if (addresses.length != endAddresses.length
                    || addresses.length != huge.length
                    || addresses.length != hugePagePreferences.length
                    || addresses.length != protections.length) {
                throw new IllegalArgumentException("VMA arrays have different lengths");
            }
        }

        /// Creates an empty VMA snapshot.
        private static VmaTable empty() {
            return new VmaTable(new long[0], new long[0], new boolean[0], new byte[0], new long[0]);
        }

        /// Creates a one-VMA snapshot.
        private static VmaTable single(
                long address,
                long endAddress,
                boolean huge,
                byte hugePagePreference,
                long protection) {
            return new VmaTable(
                    new long[]{address},
                    new long[]{endAddress},
                    new boolean[]{huge},
                    new byte[]{hugePagePreference},
                    new long[]{protection});
        }

        /// Copies a VMA list into a sorted immutable snapshot.
        private static VmaTable copyOf(ArrayList<Vma> vmas) {
            long[] addresses = new long[vmas.size()];
            long[] endAddresses = new long[vmas.size()];
            boolean[] huge = new boolean[vmas.size()];
            byte[] hugePagePreferences = new byte[vmas.size()];
            long[] protections = new long[vmas.size()];
            for (int index = 0; index < vmas.size(); index++) {
                Vma vma = vmas.get(index);
                addresses[index] = vma.address();
                endAddresses[index] = vma.endAddress();
                huge[index] = vma.huge();
                hugePagePreferences[index] = vma.hugePagePreference();
                protections[index] = vma.protection();
            }
            return new VmaTable(addresses, endAddresses, huge, hugePagePreferences, protections);
        }

        /// Returns the number of VMAs in this snapshot.
        private int size() {
            return addresses.length;
        }

        /// Returns the VMA at the supplied index.
        private Vma vma(int index) {
            return new Vma(
                    addresses[index],
                    endAddresses[index],
                    huge[index],
                    hugePagePreferences[index],
                    protections[index]);
        }

        /// Returns a new snapshot with the supplied non-overlapping VMA inserted.
        private VmaTable insert(Vma vma) {
            int insertionIndex = insertionIndex(vma.address());
            long[] newAddresses = new long[addresses.length + 1];
            long[] newEndAddresses = new long[endAddresses.length + 1];
            boolean[] newHuge = new boolean[huge.length + 1];
            byte[] newHugePagePreferences = new byte[hugePagePreferences.length + 1];
            long[] newProtections = new long[protections.length + 1];
            System.arraycopy(addresses, 0, newAddresses, 0, insertionIndex);
            System.arraycopy(endAddresses, 0, newEndAddresses, 0, insertionIndex);
            System.arraycopy(huge, 0, newHuge, 0, insertionIndex);
            System.arraycopy(hugePagePreferences, 0, newHugePagePreferences, 0, insertionIndex);
            System.arraycopy(protections, 0, newProtections, 0, insertionIndex);
            newAddresses[insertionIndex] = vma.address();
            newEndAddresses[insertionIndex] = vma.endAddress();
            newHuge[insertionIndex] = vma.huge();
            newHugePagePreferences[insertionIndex] = vma.hugePagePreference();
            newProtections[insertionIndex] = vma.protection();
            int copiedEntries = addresses.length - insertionIndex;
            System.arraycopy(addresses, insertionIndex, newAddresses, insertionIndex + 1, copiedEntries);
            System.arraycopy(endAddresses, insertionIndex, newEndAddresses, insertionIndex + 1, copiedEntries);
            System.arraycopy(huge, insertionIndex, newHuge, insertionIndex + 1, copiedEntries);
            System.arraycopy(
                    hugePagePreferences,
                    insertionIndex,
                    newHugePagePreferences,
                    insertionIndex + 1,
                    copiedEntries);
            System.arraycopy(protections, insertionIndex, newProtections, insertionIndex + 1, copiedEntries);
            return new VmaTable(newAddresses, newEndAddresses, newHuge, newHugePagePreferences, newProtections);
        }

        /// Returns true when any VMA overlaps the supplied guest range.
        private boolean overlaps(long address, long length) {
            return findOverlap(address, length) != null;
        }

        /// Finds the VMA fully containing the supplied guest range, or null when absent.
        private @Nullable Vma find(long address, long length) {
            int index = Arrays.binarySearch(addresses, address);
            if (index < 0) {
                index = -index - 2;
            }
            if (index < 0) {
                return null;
            }
            long vmaEndAddress = endAddresses[index];
            if (address > vmaEndAddress || length > vmaEndAddress - address) {
                return null;
            }
            return vma(index);
        }

        /// Finds the first VMA overlapping the supplied guest range, or null when absent.
        private @Nullable Vma findOverlap(long address, long length) {
            long endAddress = address + length;
            int insertionIndex = insertionIndex(address);
            if (insertionIndex > 0) {
                int previousIndex = insertionIndex - 1;
                if (rangesOverlap(address, endAddress, addresses[previousIndex], endAddresses[previousIndex])) {
                    return vma(previousIndex);
                }
            }
            if (insertionIndex >= addresses.length) {
                return null;
            }
            return rangesOverlap(address, endAddress, addresses[insertionIndex], endAddresses[insertionIndex])
                    ? vma(insertionIndex)
                    : null;
        }

        /// Returns the insertion index for an address in this sorted snapshot.
        private int insertionIndex(long address) {
            int index = Arrays.binarySearch(addresses, address);
            return index >= 0 ? index : -index - 1;
        }
    }
}
