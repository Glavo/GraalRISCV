package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.ArrayList;

/// Provides MemorySegment-backed little-endian access to guest memory.
@NotNullByDefault
public final class Memory implements AutoCloseable {
    /// The default base address used by the bare-metal guest memory image.
    public static final long DEFAULT_BASE_ADDRESS = 0x8000_0000L;

    /// The little-endian 16-bit layout used for aligned guest accesses.
    private static final ValueLayout.OfShort SHORT_LE = ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN);

    /// The little-endian 32-bit layout used for aligned guest accesses.
    private static final ValueLayout.OfInt INT_LE = ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN);

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

    /// The sparse memory regions created by Linux user-mode memory syscalls.
    private final ArrayList<MappedRegion> mappedRegions = new ArrayList<>();

    /// The most recently accessed sparse memory region.
    private @Nullable MappedRegion cachedMappedRegion;

    /// Creates a memory window at the supplied guest base address.
    public Memory(long baseAddress, long size) {
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
            return (segment.get(ValueLayout.JAVA_BYTE, fastOffset) & 0xff)
                    | ((segment.get(ValueLayout.JAVA_BYTE, fastOffset + 1) & 0xff) << 8)
                    | ((segment.get(ValueLayout.JAVA_BYTE, fastOffset + 2) & 0xff) << 16)
                    | (segment.get(ValueLayout.JAVA_BYTE, fastOffset + 3) << 24);
        }

        MappedRegion region = mappedRegion(address, Integer.BYTES);
        MemorySegment accessSegment = region.segment();
        long offset = address - region.address();
        return (accessSegment.get(ValueLayout.JAVA_BYTE, offset) & 0xff)
                | ((accessSegment.get(ValueLayout.JAVA_BYTE, offset + 1) & 0xff) << 8)
                | ((accessSegment.get(ValueLayout.JAVA_BYTE, offset + 2) & 0xff) << 16)
                | (accessSegment.get(ValueLayout.JAVA_BYTE, offset + 3) << 24);
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
        if (!isValidRange(address, length) || length == 0 || overlapsInitialMemory(address, length)
                || overlappingMappedRegion(address, length) != null) {
            return false;
        }

        MemorySegment mappedSegment;
        try {
            mappedSegment = arena.allocate(length, Long.BYTES);
        } catch (IllegalArgumentException | OutOfMemoryError exception) {
            return false;
        }

        MappedRegion region = new MappedRegion(address, length, mappedSegment);
        int insertionIndex = 0;
        while (insertionIndex < mappedRegions.size()
                && Long.compareUnsigned(mappedRegions.get(insertionIndex).address(), address) < 0) {
            insertionIndex++;
        }
        mappedRegions.add(insertionIndex, region);
        cachedMappedRegion = region;
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

        cachedMappedRegion = null;
        long endAddress = address + length;
        for (int index = 0; index < mappedRegions.size(); ) {
            MappedRegion region = mappedRegions.get(index);
            if (!rangesOverlap(address, endAddress, region.address(), region.endAddress())) {
                index++;
                continue;
            }

            mappedRegions.remove(index);
            if (region.address() < address) {
                long prefixLength = address - region.address();
                mappedRegions.add(index, new MappedRegion(
                        region.address(),
                        prefixLength,
                        region.segment().asSlice(0, prefixLength)));
                index++;
            }
            if (endAddress < region.endAddress()) {
                long suffixOffset = endAddress - region.address();
                long suffixLength = region.endAddress() - endAddress;
                mappedRegions.add(index, new MappedRegion(
                        endAddress,
                        suffixLength,
                        region.segment().asSlice(suffixOffset, suffixLength)));
                index++;
            }
        }
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
    private synchronized @Nullable MappedRegion findMappedRegion(long address, long length) {
        if (length < 0) {
            throw new RiscVException("Negative memory access length: " + length);
        }
        if (!isValidRange(address, length)) {
            return null;
        }

        @Nullable MappedRegion cached = cachedMappedRegion;
        if (cached != null && containsRange(cached, address, length)) {
            return cached;
        }

        for (MappedRegion region : mappedRegions) {
            if (containsRange(region, address, length)) {
                cachedMappedRegion = region;
                return region;
            }
        }
        return null;
    }

    /// Returns true when the supplied guest range overlaps the initial memory segment.
    private boolean overlapsInitialMemory(long address, long length) {
        return rangesOverlap(address, address + length, baseAddress, endAddress);
    }

    /// Returns the first sparse region overlapped by the supplied guest range.
    private @Nullable MappedRegion overlappingMappedRegion(long address, long length) {
        long endAddress = address + length;
        for (MappedRegion region : mappedRegions) {
            if (rangesOverlap(address, endAddress, region.address(), region.endAddress())) {
                return region;
            }
        }
        return null;
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
        return address >= region.address() && address <= region.endAddress() && length <= region.endAddress() - address;
    }

    /// Describes a concrete host segment access for a guest memory range.
    private record MemoryAccess(
            /// The host memory segment backing the guest range.
            MemorySegment segment,

            /// The byte offset inside the host memory segment.
            long offset) {
    }

    /// Describes a sparse guest memory region created by a memory syscall.
    private record MappedRegion(
            /// The inclusive guest start address of the region.
            long address,

            /// The byte length of the region.
            long length,

            /// The host segment backing the region.
            MemorySegment segment) {
        /// Returns the exclusive guest end address of the region.
        long endAddress() {
            return address + length;
        }
    }
}
