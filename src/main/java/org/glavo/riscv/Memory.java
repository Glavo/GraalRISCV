package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.ArrayList;

/// Provides checked little-endian access to the guest physical memory window.
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

    /// The sparse memory regions created by Linux user-mode memory syscalls.
    private final ArrayList<MappedRegion> mappedRegions = new ArrayList<>();

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
        this.arena = Arena.ofConfined();
        this.segment = arena.allocate(size, Long.BYTES);
    }

    /// Returns the inclusive base address of the memory window.
    public long baseAddress() {
        return baseAddress;
    }

    /// Returns the memory window size in bytes.
    public long size() {
        return segment.byteSize();
    }

    /// Returns the exclusive end address of the memory window.
    public long endAddress() {
        return baseAddress + segment.byteSize();
    }

    /// Copies bytes from a host array into guest memory.
    public void load(long address, byte[] source, int offset, int length) {
        MemoryAccess access = access(address, length);
        MemorySegment.copy(source, offset, access.segment(), ValueLayout.JAVA_BYTE, access.offset(), length);
    }

    /// Fills a guest memory range with zero bytes.
    public void clear(long address, long length) {
        MemoryAccess access = access(address, length);
        access.segment().asSlice(access.offset(), length).fill((byte) 0);
    }

    /// Reads a signed byte from guest memory.
    public byte readByte(long address) {
        MemoryAccess access = access(address, Byte.BYTES);
        return access.segment().get(ValueLayout.JAVA_BYTE, access.offset());
    }

    /// Reads an unsigned byte from guest memory.
    public int readUnsignedByte(long address) {
        return readByte(address) & 0xff;
    }

    /// Reads a signed little-endian 16-bit value from an aligned guest address.
    public short readShort(long address) {
        requireAligned(address, Short.BYTES);
        MemoryAccess access = access(address, Short.BYTES);
        return access.segment().get(SHORT_LE, access.offset());
    }

    /// Reads an unsigned little-endian 16-bit value from an aligned guest address.
    public int readUnsignedShort(long address) {
        return readShort(address) & 0xffff;
    }

    /// Reads a signed little-endian 32-bit value from an aligned guest address.
    public int readInt(long address) {
        requireAligned(address, Integer.BYTES);
        MemoryAccess access = access(address, Integer.BYTES);
        return access.segment().get(INT_LE, access.offset());
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
        MemoryAccess access = access(address, length);
        MemorySegment.copy(access.segment(), ValueLayout.JAVA_BYTE, access.offset(), result, 0, (int) length);
        return result;
    }

    /// Copies host bytes into guest memory.
    public void writeBytes(long address, byte[] source, int offset, int length) {
        MemoryAccess access = access(address, length);
        MemorySegment.copy(source, offset, access.segment(), ValueLayout.JAVA_BYTE, access.offset(), length);
    }

    /// Reads a little-endian 32-bit instruction from a 16-bit-aligned guest address.
    public int readInstructionInt(long address) {
        requireAligned(address, Short.BYTES);
        MemoryAccess access = access(address, Integer.BYTES);
        MemorySegment accessSegment = access.segment();
        long offset = access.offset();
        return (accessSegment.get(ValueLayout.JAVA_BYTE, offset) & 0xff)
                | ((accessSegment.get(ValueLayout.JAVA_BYTE, offset + 1) & 0xff) << 8)
                | ((accessSegment.get(ValueLayout.JAVA_BYTE, offset + 2) & 0xff) << 16)
                | (accessSegment.get(ValueLayout.JAVA_BYTE, offset + 3) << 24);
    }

    /// Reads a signed little-endian 64-bit value from an aligned guest address.
    public long readLong(long address) {
        requireAligned(address, Long.BYTES);
        MemoryAccess access = access(address, Long.BYTES);
        return access.segment().get(LONG_LE, access.offset());
    }

    /// Writes a byte to guest memory.
    public void writeByte(long address, byte value) {
        MemoryAccess access = access(address, Byte.BYTES);
        access.segment().set(ValueLayout.JAVA_BYTE, access.offset(), value);
    }

    /// Writes a little-endian 16-bit value to an aligned guest address.
    public void writeShort(long address, short value) {
        requireAligned(address, Short.BYTES);
        MemoryAccess access = access(address, Short.BYTES);
        access.segment().set(SHORT_LE, access.offset(), value);
    }

    /// Writes a little-endian 32-bit value to an aligned guest address.
    public void writeInt(long address, int value) {
        requireAligned(address, Integer.BYTES);
        MemoryAccess access = access(address, Integer.BYTES);
        access.segment().set(INT_LE, access.offset(), value);
    }

    /// Writes a little-endian 64-bit value to an aligned guest address.
    public void writeLong(long address, long value) {
        requireAligned(address, Long.BYTES);
        MemoryAccess access = access(address, Long.BYTES);
        access.segment().set(LONG_LE, access.offset(), value);
    }

    /// Adds a sparse guest memory region backed by a native memory segment.
    public boolean map(long address, long length) {
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
        return true;
    }

    /// Removes sparse guest memory backing overlapped by the supplied range.
    public void unmap(long address, long length) {
        if (length == 0) {
            return;
        }
        if (!isValidRange(address, length)) {
            throw new RiscVException("Invalid guest memory unmap range: address=0x"
                    + Long.toUnsignedString(address, 16) + ", length=" + length);
        }

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
    public boolean isBacked(long address, long length) {
        return findAccess(address, length) != null;
    }

    /// Releases the native memory segment backing this guest memory.
    @Override
    public void close() {
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

    /// Finds the host segment and offset backing a guest range, or null when absent.
    private @Nullable MemoryAccess findAccess(long address, long length) {
        if (length < 0) {
            throw new RiscVException("Negative memory access length: " + length);
        }
        if (!isValidRange(address, length)) {
            return null;
        }

        long offset = address - baseAddress;
        if (address >= baseAddress && offset >= 0 && offset <= segment.byteSize() - length) {
            return new MemoryAccess(segment, offset);
        }

        for (MappedRegion region : mappedRegions) {
            if (address >= region.address() && address <= region.endAddress()
                    && length <= region.endAddress() - address) {
                return new MemoryAccess(region.segment(), address - region.address());
            }
        }
        return null;
    }

    /// Returns true when the supplied guest range overlaps the initial memory segment.
    private boolean overlapsInitialMemory(long address, long length) {
        return rangesOverlap(address, address + length, baseAddress, endAddress());
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

    /// Requires an address to be naturally aligned for the supplied access width.
    private static void requireAligned(long address, int width) {
        if ((address & (width - 1L)) != 0) {
            throw new RiscVException("Misaligned guest memory access: address=0x"
                    + Long.toUnsignedString(address, 16) + ", width=" + width);
        }
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
