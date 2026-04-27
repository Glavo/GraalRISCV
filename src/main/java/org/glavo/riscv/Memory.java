package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

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
        long index = index(address, length);
        MemorySegment.copy(source, offset, segment, ValueLayout.JAVA_BYTE, index, length);
    }

    /// Fills a guest memory range with zero bytes.
    public void clear(long address, long length) {
        segment.asSlice(index(address, length), length).fill((byte) 0);
    }

    /// Reads a signed byte from guest memory.
    public byte readByte(long address) {
        return segment.get(ValueLayout.JAVA_BYTE, index(address, Byte.BYTES));
    }

    /// Reads an unsigned byte from guest memory.
    public int readUnsignedByte(long address) {
        return readByte(address) & 0xff;
    }

    /// Reads a signed little-endian 16-bit value from an aligned guest address.
    public short readShort(long address) {
        requireAligned(address, Short.BYTES);
        return segment.get(SHORT_LE, index(address, Short.BYTES));
    }

    /// Reads an unsigned little-endian 16-bit value from an aligned guest address.
    public int readUnsignedShort(long address) {
        return readShort(address) & 0xffff;
    }

    /// Reads a signed little-endian 32-bit value from an aligned guest address.
    public int readInt(long address) {
        requireAligned(address, Integer.BYTES);
        return segment.get(INT_LE, index(address, Integer.BYTES));
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
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, index(address, length), result, 0, (int) length);
        return result;
    }

    /// Reads a little-endian 32-bit instruction from a 16-bit-aligned guest address.
    public int readInstructionInt(long address) {
        requireAligned(address, Short.BYTES);
        long offset = index(address, Integer.BYTES);
        return (segment.get(ValueLayout.JAVA_BYTE, offset) & 0xff)
                | ((segment.get(ValueLayout.JAVA_BYTE, offset + 1) & 0xff) << 8)
                | ((segment.get(ValueLayout.JAVA_BYTE, offset + 2) & 0xff) << 16)
                | (segment.get(ValueLayout.JAVA_BYTE, offset + 3) << 24);
    }

    /// Reads a signed little-endian 64-bit value from an aligned guest address.
    public long readLong(long address) {
        requireAligned(address, Long.BYTES);
        return segment.get(LONG_LE, index(address, Long.BYTES));
    }

    /// Writes a byte to guest memory.
    public void writeByte(long address, byte value) {
        segment.set(ValueLayout.JAVA_BYTE, index(address, Byte.BYTES), value);
    }

    /// Writes a little-endian 16-bit value to an aligned guest address.
    public void writeShort(long address, short value) {
        requireAligned(address, Short.BYTES);
        segment.set(SHORT_LE, index(address, Short.BYTES), value);
    }

    /// Writes a little-endian 32-bit value to an aligned guest address.
    public void writeInt(long address, int value) {
        requireAligned(address, Integer.BYTES);
        segment.set(INT_LE, index(address, Integer.BYTES), value);
    }

    /// Writes a little-endian 64-bit value to an aligned guest address.
    public void writeLong(long address, long value) {
        requireAligned(address, Long.BYTES);
        segment.set(LONG_LE, index(address, Long.BYTES), value);
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

    /// Converts a guest address range into a host array offset.
    private long index(long address, long length) {
        if (length < 0) {
            throw new RiscVException("Negative memory access length: " + length);
        }

        long offset = address - baseAddress;
        if (address < baseAddress || offset < 0 || offset > segment.byteSize() - length) {
            throw new RiscVException("Guest memory access out of range: address=0x"
                    + Long.toUnsignedString(address, 16) + ", length=" + length);
        }

        return offset;
    }

    /// Requires an address to be naturally aligned for the supplied access width.
    private static void requireAligned(long address, int width) {
        if ((address & (width - 1L)) != 0) {
            throw new RiscVException("Misaligned guest memory access: address=0x"
                    + Long.toUnsignedString(address, 16) + ", width=" + width);
        }
    }
}
