// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.memory;

import com.oracle.truffle.api.ContextThreadLocal;
import org.glavo.riscv.exception.RiscVException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Arrays;

/// Provides MemorySegment-backed little-endian access to guest memory.
@NotNullByDefault
public final class Memory implements AutoCloseable {
    /// The default base address used by the bare-metal guest memory image.
    public static final long DEFAULT_BASE_ADDRESS = 0x8000_0000L;

    /// The little-endian 16-bit layout used for aligned guest accesses.
    private static final ValueLayout.OfShort SHORT_LE = ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN);

    /// The little-endian 32-bit layout used for aligned guest accesses.
    private static final ValueLayout.OfInt INT_LE = ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN);

    /// The little-endian 32-bit layout used for 16-bit-aligned instruction fetches.
    private static final ValueLayout.OfInt INSTRUCTION_INT_LE = INT_LE.withByteAlignment(1);

    /// The little-endian 64-bit layout used for aligned guest accesses.
    private static final ValueLayout.OfLong LONG_LE = ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN);

    /// The inclusive base address of the memory window.
    private final long baseAddress;

    /// The arena that owns the guest memory segment lifetime.
    private final Arena arena;

    /// The mutable guest memory segment.
    private final MemorySegment segment;

    /// The byte size of the initial contiguous guest memory segment.
    private final long size;

    /// The exclusive end address of the initial contiguous guest memory segment.
    private final long endAddress;

    /// The sorted immutable snapshot of every currently backed guest memory region.
    private volatile MemoryRegions regions;

    /// The current Truffle context and host thread's most recently accessed memory region.
    private final @Nullable ContextThreadLocal<MappedRegionCache> cachedMappedRegion;

    /// Creates a memory window with a Truffle context-thread-local region cache.
    public Memory(long baseAddress, long size, @Nullable ContextThreadLocal<MappedRegionCache> cachedMappedRegion) {
        if (baseAddress < 0) {
            throw new RiscVException("Guest memory base address must be non-negative: " + baseAddress);
        }
        if (size <= 0) {
            throw new RiscVException("Guest memory size must be positive: " + size);
        }
        if (baseAddress > Long.MAX_VALUE - size) {
            throw new RiscVException("Guest memory range overflows: base=" + baseAddress + ", size=" + size);
        }

        this.baseAddress = baseAddress;
        this.arena = Arena.ofShared();
        this.segment = arena.allocate(size, Long.BYTES);
        this.size = size;
        this.endAddress = baseAddress + size;
        this.regions = MemoryRegions.single(baseAddress, segment);
        this.cachedMappedRegion = cachedMappedRegion;
    }

    /// Returns the inclusive base address of the memory window.
    public long baseAddress() {
        return baseAddress;
    }

    /// Returns the memory window size in bytes.
    public long size() {
        return size;
    }

    /// Returns the exclusive end address of the memory window.
    public long endAddress() {
        return endAddress;
    }

    /// Copies bytes from a host array into guest memory.
    public void load(long address, byte[] source, int offset, int length) {
        long accessOffset = initialOffset(address, length);
        if (accessOffset >= 0) {
            MemorySegment.copy(source, offset, segment, ValueLayout.JAVA_BYTE, accessOffset, length);
            return;
        }

        MemoryAccess access = access(address, length);
        MemorySegment.copy(source, offset, access.segment(), ValueLayout.JAVA_BYTE, access.offset(), length);
    }

    /// Fills a guest memory range with zero bytes.
    public void clear(long address, long length) {
        long accessOffset = initialOffset(address, length);
        if (accessOffset >= 0) {
            segment.asSlice(accessOffset, length).fill((byte) 0);
            return;
        }

        MemoryAccess access = access(address, length);
        access.segment().asSlice(access.offset(), length).fill((byte) 0);
    }

    /// Reads a signed byte from guest memory.
    public byte readByte(long address) {
        long offset = initialScalarOffset(address);
        if (offset >= 0) {
            return segment.get(ValueLayout.JAVA_BYTE, offset);
        }

        MemoryRegion region = region(address, Byte.BYTES);
        return region.segment().get(ValueLayout.JAVA_BYTE, region.offset(address));
    }

    /// Reads an unsigned byte from guest memory.
    public int readUnsignedByte(long address) {
        return readByte(address) & 0xff;
    }

    /// Reads a signed little-endian 16-bit value from guest memory.
    public short readShort(long address) {
        long offset = initialScalarOffset(address);
        if (offset >= 0) {
            return segment.get(SHORT_LE, offset);
        }

        MemoryRegion region = region(address, Short.BYTES);
        return region.segment().get(SHORT_LE, region.offset(address));
    }

    /// Reads an unsigned little-endian 16-bit value from an aligned guest address.
    public int readUnsignedShort(long address) {
        return readShort(address) & 0xffff;
    }

    /// Reads a signed little-endian 32-bit value from guest memory.
    public int readInt(long address) {
        long offset = initialScalarOffset(address);
        if (offset >= 0) {
            return segment.get(INT_LE, offset);
        }

        MemoryRegion region = region(address, Integer.BYTES);
        return region.segment().get(INT_LE, region.offset(address));
    }

    /// Reads an unsigned little-endian 32-bit value from an aligned guest address.
    public long readUnsignedInt(long address) {
        return readInt(address) & 0xffff_ffffL;
    }

    /// Copies guest memory bytes into a new host byte array.
    public byte[] readBytes(long address, long length) {
        if (length > Integer.MAX_VALUE) {
            throw new RiscVException("Guest memory read range is too large: " + length);
        }

        byte[] result = new byte[(int) length];
        long offset = initialOffset(address, length);
        if (offset >= 0) {
            MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset, result, 0, (int) length);
            return result;
        }

        MemoryAccess access = access(address, length);
        MemorySegment.copy(access.segment(), ValueLayout.JAVA_BYTE, access.offset(), result, 0, (int) length);
        return result;
    }

    /// Copies host bytes into guest memory.
    public void writeBytes(long address, byte[] source, int offset, int length) {
        long accessOffset = initialOffset(address, length);
        if (accessOffset >= 0) {
            MemorySegment.copy(source, offset, segment, ValueLayout.JAVA_BYTE, accessOffset, length);
            return;
        }

        MemoryAccess access = access(address, length);
        MemorySegment.copy(source, offset, access.segment(), ValueLayout.JAVA_BYTE, access.offset(), length);
    }

    /// Reads a little-endian 32-bit instruction from a 16-bit-aligned guest address.
    public int readInstructionInt(long address) {
        long fastOffset = initialScalarOffset(address);
        if (fastOffset >= 0) {
            return segment.get(INSTRUCTION_INT_LE, fastOffset);
        }

        MemoryRegion region = region(address, Integer.BYTES);
        return region.segment().get(INSTRUCTION_INT_LE, region.offset(address));
    }

    /// Reads a signed little-endian 64-bit value from guest memory.
    public long readLong(long address) {
        long offset = initialScalarOffset(address);
        if (offset >= 0) {
            return segment.get(LONG_LE, offset);
        }

        MemoryRegion region = region(address, Long.BYTES);
        return region.segment().get(LONG_LE, region.offset(address));
    }

    /// Writes a byte to guest memory.
    public void writeByte(long address, byte value) {
        long offset = initialScalarOffset(address);
        if (offset >= 0) {
            segment.set(ValueLayout.JAVA_BYTE, offset, value);
            return;
        }

        MemoryRegion region = region(address, Byte.BYTES);
        region.segment().set(ValueLayout.JAVA_BYTE, region.offset(address), value);
    }

    /// Writes a little-endian 16-bit value to guest memory.
    public void writeShort(long address, short value) {
        long offset = initialScalarOffset(address);
        if (offset >= 0) {
            segment.set(SHORT_LE, offset, value);
            return;
        }

        MemoryRegion region = region(address, Short.BYTES);
        region.segment().set(SHORT_LE, region.offset(address), value);
    }

    /// Writes a little-endian 32-bit value to guest memory.
    public void writeInt(long address, int value) {
        long offset = initialScalarOffset(address);
        if (offset >= 0) {
            segment.set(INT_LE, offset, value);
            return;
        }

        MemoryRegion region = region(address, Integer.BYTES);
        region.segment().set(INT_LE, region.offset(address), value);
    }

    /// Writes a little-endian 64-bit value to guest memory.
    public void writeLong(long address, long value) {
        long offset = initialScalarOffset(address);
        if (offset >= 0) {
            segment.set(LONG_LE, offset, value);
            return;
        }

        MemoryRegion region = region(address, Long.BYTES);
        region.segment().set(LONG_LE, region.offset(address), value);
    }

    /// Adds a guest memory region backed by a native memory segment.
    public synchronized boolean map(long address, long length) {
        if (!canMap(address, length)) {
            return false;
        }

        MemorySegment mappedSegment;
        try {
            mappedSegment = arena.allocate(length, Long.BYTES);
        } catch (IllegalArgumentException | OutOfMemoryError exception) {
            return false;
        }

        mapSegment(address, mappedSegment.asSlice(0, length));
        return true;
    }

    /// Adds a guest memory region backed by a Java heap array.
    public synchronized boolean mapHeap(long address, int length) {
        if (length <= 0 || !canMap(address, length)) {
            return false;
        }

        long[] array;
        try {
            int wordCount = (int) (((long) length + Long.BYTES - 1) / Long.BYTES);
            array = new long[wordCount];
        } catch (OutOfMemoryError exception) {
            return false;
        }

        mapSegment(address, MemorySegment.ofArray(array).asSlice(0, length));
        return true;
    }

    /// Returns true when a new region may be inserted at the supplied guest range.
    private boolean canMap(long address, long length) {
        return isValidRange(address, length) && length != 0 && !regions.overlaps(address, length);
    }

    /// Adds an already allocated host segment to the memory region table.
    private void mapSegment(long address, MemorySegment regionSegment) {
        MemoryRegion region = MemoryRegion.of(address, regionSegment);
        MemoryRegions regions = this.regions;
        int insertionIndex = regions.insertionIndex(address);
        MemoryRegions newRegions = regions.insert(insertionIndex, region);
        this.regions = newRegions;
        setCachedMappedRegion(newRegions, region);
    }

    /// Removes non-initial guest memory backing overlapped by the supplied range.
    public synchronized void unmap(long address, long length) {
        if (length == 0) {
            return;
        }
        if (!isValidRange(address, length)) {
            throw new RiscVException("Invalid guest memory unmap range: address=0x"
                    + Long.toUnsignedString(address, 16) + ", length=" + length);
        }

        long endAddress = address + length;
        MemoryRegions regions = this.regions;
        long[] newAddresses = new long[regions.size() + 1];
        MemorySegment[] newSegments = new MemorySegment[regions.size() + 1];
        long[] newBaseOffsets = new long[regions.size() + 1];
        int newSize = 0;
        for (int index = 0; index < regions.size(); index++) {
            long regionAddress = regions.addresses[index];
            MemorySegment regionSegment = regions.segments[index];
            long regionBaseOffset = regions.baseOffsets[index];
            long regionEndAddress = regionAddress + regionSegment.byteSize();
            if (!rangesOverlap(address, endAddress, regionAddress, regionEndAddress)
                    || isInitialRegion(regionAddress, regionSegment)) {
                newAddresses[newSize] = regionAddress;
                newSegments[newSize] = regionSegment;
                newBaseOffsets[newSize] = regionBaseOffset;
                newSize++;
                continue;
            }

            if (regionAddress < address) {
                long prefixLength = address - regionAddress;
                newAddresses[newSize] = regionAddress;
                newSegments[newSize] = regionSegment.asSlice(0, prefixLength);
                newBaseOffsets[newSize] = -regionAddress;
                newSize++;
            }
            if (endAddress < regionEndAddress) {
                long suffixOffset = endAddress - regionAddress;
                long suffixLength = regionEndAddress - endAddress;
                newAddresses[newSize] = endAddress;
                newSegments[newSize] = regionSegment.asSlice(suffixOffset, suffixLength);
                newBaseOffsets[newSize] = -endAddress;
                newSize++;
            }
        }
        this.regions = MemoryRegions.copyOf(newAddresses, newSegments, newBaseOffsets, newSize);
        clearCachedMappedRegion();
    }

    /// Returns true when the supplied guest range has native backing.
    public synchronized boolean isBacked(long address, long length) {
        return findAccess(address, length) != null;
    }

    /// Releases the native memory segment backing this guest memory.
    @Override
    public synchronized void close() {
        arena.close();
    }

    /// Returns true when the supplied address range overlaps the supplied guest address.
    public static boolean overlaps(long address, int length, long pointAddress, int pointLength) {
        long end = address + length;
        long pointEnd = pointAddress + pointLength;
        return address < pointEnd && pointAddress < end;
    }

    /// Converts a guest address range into a concrete host memory access.
    private MemoryAccess access(long address, long length) {
        @Nullable MemoryAccess access = findAccess(address, length);
        if (access == null) {
            throw new RiscVException("Guest memory access out of range: address=0x"
                    + Long.toUnsignedString(address, 16) + ", length=" + length);
        }
        return access;
    }

    /// Returns the memory region backing a guest range, or throws when it is absent.
    private MemoryRegion region(long address, long length) {
        @Nullable MemoryRegion region = findRegion(address, length);
        if (region == null) {
            throw new RiscVException("Guest memory access out of range: address=0x"
                    + Long.toUnsignedString(address, 16) + ", length=" + length);
        }
        return region;
    }

    /// Returns the offset inside the initial segment for a scalar access, or `-1` when it starts outside.
    private long initialScalarOffset(long address) {
        if (address >= baseAddress && address < endAddress) {
            return address - baseAddress;
        }
        return -1;
    }

    /// Returns the offset inside the initial segment for a backed range, or `-1` when it is outside.
    private long initialOffset(long address, long length) {
        if (length < 0) {
            throw new RiscVException("Negative memory access length: " + length);
        }

        if (address >= baseAddress && address <= endAddress - length) {
            return address - baseAddress;
        }
        return -1;
    }

    /// Finds the host segment and offset backing a guest range, or null when absent.
    private @Nullable MemoryAccess findAccess(long address, long length) {
        if (length < 0) {
            throw new RiscVException("Negative memory access length: " + length);
        }

        long offset = initialOffset(address, length);
        if (offset >= 0) {
            return new MemoryAccess(segment, offset);
        }

        if (!isValidRange(address, length)) {
            return null;
        }

        @Nullable MemoryRegion region = findRegion(address, length);
        if (region != null) {
            return new MemoryAccess(region.segment(), region.offset(address));
        }
        return null;
    }

    /// Finds the memory region backing a guest range, or null when absent.
    private @Nullable MemoryRegion findRegion(long address, long length) {
        if (length < 0) {
            throw new RiscVException("Negative memory access length: " + length);
        }
        if (!isValidRange(address, length)) {
            return null;
        }

        MemoryRegions regions = this.regions;
        @Nullable MemoryRegion cachedRegion = cachedMappedRegion(regions, address, length);
        if (cachedRegion != null) {
            return cachedRegion;
        }

        @Nullable MemoryRegion region = regions.find(address, length);
        if (region != null && this.regions == regions) {
            setCachedMappedRegion(regions, region);
        }
        return region;
    }

    /// Returns the cached memory region for the current context and thread, or null when it misses.
    private @Nullable MemoryRegion cachedMappedRegion(MemoryRegions regions, long address, long length) {
        @Nullable ContextThreadLocal<MappedRegionCache> cache = cachedMappedRegion;
        return cache == null ? null : cache.get().region(regions, address, length);
    }

    /// Stores the cached memory region for the current context and thread when caching is available.
    private void setCachedMappedRegion(MemoryRegions regions, MemoryRegion region) {
        @Nullable ContextThreadLocal<MappedRegionCache> cache = cachedMappedRegion;
        if (cache != null) {
            cache.get().setRegion(regions, region);
        }
    }

    /// Clears the cached memory region for the current context and thread when caching is available.
    private void clearCachedMappedRegion() {
        @Nullable ContextThreadLocal<MappedRegionCache> cache = cachedMappedRegion;
        if (cache != null) {
            cache.get().clear();
        }
    }

    /// Returns true when a region describes the initial contiguous memory segment.
    private boolean isInitialRegion(long address, MemorySegment regionSegment) {
        return address == baseAddress && regionSegment == segment;
    }

    /// Returns true when the supplied address and length form a non-overflowing guest range.
    private static boolean isValidRange(long address, long length) {
        return address >= 0 && length >= 0 && address <= Long.MAX_VALUE - length;
    }

    /// Returns true when two half-open address ranges overlap.
    private static boolean rangesOverlap(long firstStart, long firstEnd, long secondStart, long secondEnd) {
        return firstStart < secondEnd && secondStart < firstEnd;
    }

    /// Returns true when the memory region fully contains the supplied guest range.
    private static boolean containsRange(MemoryRegion region, long address, long length) {
        long regionAddress = region.address();
        long regionEndAddress = region.endAddress();
        return address >= regionAddress && address <= regionEndAddress && length <= regionEndAddress - address;
    }

    /// Describes a concrete host segment access for a guest memory range.
    ///
    /// @param segment the host memory segment backing the guest range
    /// @param offset the byte offset inside the host memory segment
    private record MemoryAccess(
            MemorySegment segment,
            long offset) {
    }

    /// Stores mutable memory-region lookup state for one Truffle context and host thread.
    public static final class MappedRegionCache {
        /// The region snapshot that owns the cached region.
        private @Nullable MemoryRegions regions;

        /// The cached memory region.
        private @Nullable MemoryRegion region;

        /// Creates an empty memory-region lookup cache.
        public MappedRegionCache() {
        }

        /// Returns the cached memory region, or null when the cache misses.
        private @Nullable MemoryRegion region(MemoryRegions regions, long address, long length) {
            @Nullable MemoryRegion region = this.region;
            if (region != null && this.regions == regions && containsRange(region, address, length)) {
                return region;
            }
            return null;
        }

        /// Stores a memory-region cache entry.
        private void setRegion(MemoryRegions regions, MemoryRegion region) {
            this.regions = regions;
            this.region = region;
        }

        /// Clears this memory-region cache.
        private void clear() {
            this.regions = null;
            this.region = null;
        }
    }

    /// Stores sorted guest memory regions in parallel arrays.
    ///
    /// @param addresses the inclusive guest start addresses for the regions
    /// @param segments the host segments backing the regions
    /// @param baseOffsets the values added to guest addresses to produce host segment offsets
    private record MemoryRegions(
            long @Unmodifiable [] addresses,
            MemorySegment @Unmodifiable [] segments,
            long @Unmodifiable [] baseOffsets) {
        /// Creates a region snapshot backed by same-length sorted arrays.
        private MemoryRegions {
            if (addresses.length != segments.length || addresses.length != baseOffsets.length) {
                throw new IllegalArgumentException("Region arrays have different lengths");
            }
        }

        /// Creates a one-region snapshot.
        private static MemoryRegions single(long address, MemorySegment segment) {
            return new MemoryRegions(new long[]{address}, new MemorySegment[]{segment}, new long[]{-address});
        }

        /// Copies a prefix of the supplied arrays into a new region snapshot.
        private static MemoryRegions copyOf(
                long[] addresses,
                MemorySegment[] segments,
                long[] baseOffsets,
                int size) {
            return new MemoryRegions(
                    Arrays.copyOf(addresses, size),
                    Arrays.copyOf(segments, size),
                    Arrays.copyOf(baseOffsets, size));
        }

        /// Returns the number of regions in this snapshot.
        int size() {
            return addresses.length;
        }

        /// Returns a new snapshot with one region inserted at the supplied index.
        MemoryRegions insert(int insertionIndex, MemoryRegion region) {
            long[] newAddresses = new long[addresses.length + 1];
            MemorySegment[] newSegments = new MemorySegment[segments.length + 1];
            long[] newBaseOffsets = new long[baseOffsets.length + 1];
            System.arraycopy(addresses, 0, newAddresses, 0, insertionIndex);
            System.arraycopy(segments, 0, newSegments, 0, insertionIndex);
            System.arraycopy(baseOffsets, 0, newBaseOffsets, 0, insertionIndex);
            newAddresses[insertionIndex] = region.address();
            newSegments[insertionIndex] = region.segment();
            newBaseOffsets[insertionIndex] = region.baseOffset();
            int copiedEntries = addresses.length - insertionIndex;
            System.arraycopy(addresses, insertionIndex, newAddresses, insertionIndex + 1, copiedEntries);
            System.arraycopy(segments, insertionIndex, newSegments, insertionIndex + 1, copiedEntries);
            System.arraycopy(baseOffsets, insertionIndex, newBaseOffsets, insertionIndex + 1, copiedEntries);
            return new MemoryRegions(newAddresses, newSegments, newBaseOffsets);
        }

        /// Returns true when any region overlaps the supplied guest range.
        boolean overlaps(long address, long length) {
            long endAddress = address + length;
            int insertionIndex = insertionIndex(address);
            if (insertionIndex > 0) {
                int previousIndex = insertionIndex - 1;
                long previousAddress = addresses[previousIndex];
                long previousEndAddress = previousAddress + segments[previousIndex].byteSize();
                if (rangesOverlap(address, endAddress, previousAddress, previousEndAddress)) {
                    return true;
                }
            }
            if (insertionIndex >= addresses.length) {
                return false;
            }
            long nextAddress = addresses[insertionIndex];
            long nextEndAddress = nextAddress + segments[insertionIndex].byteSize();
            return rangesOverlap(address, endAddress, nextAddress, nextEndAddress);
        }

        /// Finds the region backing a guest range, or null when absent.
        private @Nullable MemoryRegion find(long address, long length) {
            int index = Arrays.binarySearch(addresses, address);
            if (index < 0) {
                index = -index - 2;
            }
            if (index < 0) {
                return null;
            }
            long regionAddress = addresses[index];
            MemorySegment regionSegment = segments[index];
            long regionEndAddress = regionAddress + regionSegment.byteSize();
            if (!containsRange(regionAddress, regionEndAddress, address, length)) {
                return null;
            }
            return new MemoryRegion(regionAddress, regionSegment, baseOffsets[index]);
        }

        /// Returns the insertion index for an address in this sorted snapshot.
        int insertionIndex(long address) {
            int index = Arrays.binarySearch(addresses, address);
            return index >= 0 ? index : -index - 1;
        }

        /// Returns true when a region fully contains the supplied guest range.
        private static boolean containsRange(long regionAddress, long regionEndAddress, long address, long length) {
            return address >= regionAddress && address <= regionEndAddress && length <= regionEndAddress - address;
        }
    }

    /// Describes a guest memory region.
    ///
    /// @param address the inclusive guest start address of the region
    /// @param segment the host segment backing exactly this region
    /// @param baseOffset the value added to a guest address to produce an offset in `segment`
    private record MemoryRegion(
            long address,
            MemorySegment segment,
            long baseOffset) {
        /// Creates a region whose host segment begins at the guest start address.
        private static MemoryRegion of(long address, MemorySegment segment) {
            return new MemoryRegion(address, segment, -address);
        }

        /// Returns the host segment offset for the supplied guest address.
        long offset(long address) {
            return address + baseOffset;
        }

        /// Returns the exclusive guest end address of the region.
        long endAddress() {
            return address + segment.byteSize();
        }
    }
}
