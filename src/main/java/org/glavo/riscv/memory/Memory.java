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

    /// The sorted immutable snapshot of sparse memory regions created by Linux user-mode memory syscalls.
    private volatile MappedRegions mappedRegions = MappedRegions.EMPTY;

    /// The current Truffle context and host thread's most recently accessed sparse memory region.
    private final @Nullable ContextThreadLocal<MappedRegionCache> cachedMappedRegion;

    /// Creates a memory window with a Truffle context-thread-local sparse-region cache.
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

        MappedRegion region = mappedRegion(address, Byte.BYTES);
        return region.segment().get(ValueLayout.JAVA_BYTE, address - region.address());
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

        MappedRegion region = mappedRegion(address, Short.BYTES);
        return region.segment().get(SHORT_LE, address - region.address());
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

        MappedRegion region = mappedRegion(address, Integer.BYTES);
        return region.segment().get(INT_LE, address - region.address());
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

        MappedRegion region = mappedRegion(address, Integer.BYTES);
        return region.segment().get(INSTRUCTION_INT_LE, address - region.address());
    }

    /// Reads a signed little-endian 64-bit value from guest memory.
    public long readLong(long address) {
        long offset = initialScalarOffset(address);
        if (offset >= 0) {
            return segment.get(LONG_LE, offset);
        }

        MappedRegion region = mappedRegion(address, Long.BYTES);
        return region.segment().get(LONG_LE, address - region.address());
    }

    /// Writes a byte to guest memory.
    public void writeByte(long address, byte value) {
        long offset = initialScalarOffset(address);
        if (offset >= 0) {
            segment.set(ValueLayout.JAVA_BYTE, offset, value);
            return;
        }

        MappedRegion region = mappedRegion(address, Byte.BYTES);
        region.segment().set(ValueLayout.JAVA_BYTE, address - region.address(), value);
    }

    /// Writes a little-endian 16-bit value to guest memory.
    public void writeShort(long address, short value) {
        long offset = initialScalarOffset(address);
        if (offset >= 0) {
            segment.set(SHORT_LE, offset, value);
            return;
        }

        MappedRegion region = mappedRegion(address, Short.BYTES);
        region.segment().set(SHORT_LE, address - region.address(), value);
    }

    /// Writes a little-endian 32-bit value to guest memory.
    public void writeInt(long address, int value) {
        long offset = initialScalarOffset(address);
        if (offset >= 0) {
            segment.set(INT_LE, offset, value);
            return;
        }

        MappedRegion region = mappedRegion(address, Integer.BYTES);
        region.segment().set(INT_LE, address - region.address(), value);
    }

    /// Writes a little-endian 64-bit value to guest memory.
    public void writeLong(long address, long value) {
        long offset = initialScalarOffset(address);
        if (offset >= 0) {
            segment.set(LONG_LE, offset, value);
            return;
        }

        MappedRegion region = mappedRegion(address, Long.BYTES);
        region.segment().set(LONG_LE, address - region.address(), value);
    }

    /// Adds a sparse guest memory region backed by a native memory segment.
    public synchronized boolean map(long address, long length) {
        MappedRegions regions = mappedRegions;
        if (!isValidRange(address, length) || length == 0 || overlapsInitialMemory(address, length)
                || regions.overlaps(address, length)) {
            return false;
        }

        MemorySegment mappedSegment;
        try {
            mappedSegment = arena.allocate(length, Long.BYTES);
        } catch (IllegalArgumentException | OutOfMemoryError exception) {
            return false;
        }

        MemorySegment regionSegment = mappedSegment.asSlice(0, length);
        MappedRegion region = new MappedRegion(address, regionSegment);
        int insertionIndex = regions.insertionIndex(address);
        MappedRegions newRegions = regions.insert(insertionIndex, address, regionSegment);
        mappedRegions = newRegions;
        setCachedMappedRegion(new RegionCache(newRegions, region));
        return true;
    }

    /// Removes sparse guest memory backing overlapped by the supplied range.
    public synchronized void unmap(long address, long length) {
        if (length == 0) {
            return;
        }
        if (!isValidRange(address, length)) {
            throw new RiscVException("Invalid guest memory unmap range: address=0x"
                    + Long.toUnsignedString(address, 16) + ", length=" + length);
        }

        long endAddress = address + length;
        MappedRegions regions = mappedRegions;
        long[] newAddresses = new long[regions.size() + 1];
        MemorySegment[] newSegments = new MemorySegment[regions.size() + 1];
        int newSize = 0;
        for (int index = 0; index < regions.size(); index++) {
            long regionAddress = regions.addresses[index];
            MemorySegment regionSegment = regions.segments[index];
            long regionEndAddress = regionAddress + regionSegment.byteSize();
            if (!rangesOverlap(address, endAddress, regionAddress, regionEndAddress)) {
                newAddresses[newSize] = regionAddress;
                newSegments[newSize] = regionSegment;
                newSize++;
                continue;
            }

            if (regionAddress < address) {
                long prefixLength = address - regionAddress;
                newAddresses[newSize] = regionAddress;
                newSegments[newSize] = regionSegment.asSlice(0, prefixLength);
                newSize++;
            }
            if (endAddress < regionEndAddress) {
                long suffixOffset = endAddress - regionAddress;
                long suffixLength = regionEndAddress - endAddress;
                newAddresses[newSize] = endAddress;
                newSegments[newSize] = regionSegment.asSlice(suffixOffset, suffixLength);
                newSize++;
            }
        }
        mappedRegions = MappedRegions.copyOf(newAddresses, newSegments, newSize);
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

    /// Returns the sparse region backing a guest range, or throws when it is absent.
    private MappedRegion mappedRegion(long address, long length) {
        @Nullable MappedRegion region = findMappedRegion(address, length);
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

        @Nullable MappedRegion region = findMappedRegion(address, length);
        if (region != null) {
            return new MemoryAccess(region.segment(), address - region.address());
        }
        return null;
    }

    /// Finds the sparse region backing a guest range, or null when absent.
    private @Nullable MappedRegion findMappedRegion(long address, long length) {
        if (length < 0) {
            throw new RiscVException("Negative memory access length: " + length);
        }
        if (!isValidRange(address, length)) {
            return null;
        }

        MappedRegions regions = mappedRegions;
        @Nullable RegionCache cache = cachedMappedRegion();
        if (cache != null && cache.regions() == regions && containsRange(cache.region(), address, length)) {
            return cache.region();
        }

        @Nullable MappedRegion region = regions.find(address, length);
        if (region != null && mappedRegions == regions) {
            setCachedMappedRegion(new RegionCache(regions, region));
        }
        return region;
    }

    /// Returns the cached mapped region for the current context and thread, or null when caching is unavailable.
    private @Nullable RegionCache cachedMappedRegion() {
        @Nullable ContextThreadLocal<MappedRegionCache> cache = cachedMappedRegion;
        return cache == null ? null : cache.get().region();
    }

    /// Stores the cached mapped region for the current context and thread when caching is available.
    private void setCachedMappedRegion(RegionCache region) {
        @Nullable ContextThreadLocal<MappedRegionCache> cache = cachedMappedRegion;
        if (cache != null) {
            cache.get().setRegion(region);
        }
    }

    /// Clears the cached mapped region for the current context and thread when caching is available.
    private void clearCachedMappedRegion() {
        @Nullable ContextThreadLocal<MappedRegionCache> cache = cachedMappedRegion;
        if (cache != null) {
            cache.get().clear();
        }
    }

    /// Returns true when the supplied guest range overlaps the initial memory segment.
    private boolean overlapsInitialMemory(long address, long length) {
        return rangesOverlap(address, address + length, baseAddress, endAddress);
    }

    /// Returns true when the supplied address and length form a non-overflowing guest range.
    private static boolean isValidRange(long address, long length) {
        return address >= 0 && length >= 0 && address <= Long.MAX_VALUE - length;
    }

    /// Returns true when two half-open address ranges overlap.
    private static boolean rangesOverlap(long firstStart, long firstEnd, long secondStart, long secondEnd) {
        return firstStart < secondEnd && secondStart < firstEnd;
    }

    /// Returns true when the sparse region fully contains the supplied guest range.
    private static boolean containsRange(MappedRegion region, long address, long length) {
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

    /// Stores mutable sparse memory lookup state for one Truffle context and host thread.
    public static final class MappedRegionCache {
        /// The cached sparse region and the immutable snapshot it belongs to.
        private @Nullable RegionCache region;

        /// Creates an empty sparse memory lookup cache.
        public MappedRegionCache() {
        }

        /// Returns the cached sparse region, or null when the cache is empty.
        private @Nullable RegionCache region() {
            return region;
        }

        /// Stores a sparse region cache entry.
        private void setRegion(RegionCache region) {
            this.region = region;
        }

        /// Clears this sparse region cache.
        private void clear() {
            this.region = null;
        }
    }

    /// Caches a sparse region together with the immutable snapshot it belongs to.
    ///
    /// @param regions the sparse region snapshot that owns `region`
    /// @param region the cached sparse region view
    private record RegionCache(
            MappedRegions regions,
            MappedRegion region) {
    }

    /// Stores sorted sparse guest memory regions in parallel arrays.
    ///
    /// @param addresses the inclusive guest start addresses for the sparse regions
    /// @param segments the host segments backing the sparse regions
    private record MappedRegions(
            long @Unmodifiable [] addresses,
            MemorySegment @Unmodifiable [] segments) {
        /// The empty sparse region snapshot.
        private static final MappedRegions EMPTY = new MappedRegions(new long[0], new MemorySegment[0]);

        /// Creates a sparse region snapshot backed by same-length sorted arrays.
        private MappedRegions {
            if (addresses.length != segments.length) {
                throw new IllegalArgumentException("Region address and segment arrays have different lengths");
            }
        }

        /// Copies a prefix of the supplied arrays into a new sparse region snapshot.
        private static MappedRegions copyOf(long[] addresses, MemorySegment[] segments, int size) {
            return new MappedRegions(Arrays.copyOf(addresses, size), Arrays.copyOf(segments, size));
        }

        /// Returns the number of sparse regions in this snapshot.
        int size() {
            return addresses.length;
        }

        /// Returns a new snapshot with one region inserted at the supplied index.
        MappedRegions insert(int insertionIndex, long address, MemorySegment segment) {
            long[] newAddresses = new long[addresses.length + 1];
            MemorySegment[] newSegments = new MemorySegment[segments.length + 1];
            System.arraycopy(addresses, 0, newAddresses, 0, insertionIndex);
            System.arraycopy(segments, 0, newSegments, 0, insertionIndex);
            newAddresses[insertionIndex] = address;
            newSegments[insertionIndex] = segment;
            System.arraycopy(addresses, insertionIndex, newAddresses, insertionIndex + 1, addresses.length - insertionIndex);
            System.arraycopy(segments, insertionIndex, newSegments, insertionIndex + 1, segments.length - insertionIndex);
            return new MappedRegions(newAddresses, newSegments);
        }

        /// Returns true when any sparse region overlaps the supplied guest range.
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

        /// Finds the sparse region backing a guest range, or null when absent.
        private @Nullable MappedRegion find(long address, long length) {
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
            return new MappedRegion(regionAddress, regionSegment);
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

    /// Describes a sparse guest memory region created by a memory syscall.
    ///
    /// @param address the inclusive guest start address of the region
    /// @param segment the host segment backing exactly this region
    private record MappedRegion(
            long address,
            MemorySegment segment) {
        /// Returns the exclusive guest end address of the region.
        long endAddress() {
            return address + segment.byteSize();
        }
    }
}
