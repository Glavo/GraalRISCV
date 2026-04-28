package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Describes a loaded and validated RISC-V ELF image before it is copied into guest memory.
@NotNullByDefault
public final class ElfImage {
    /// Marks an optional guest address that is not present in the image.
    public static final long ABSENT_ADDRESS = -1L;

    /// The initial guest program counter.
    private final long entryPoint;

    /// The immutable loadable segment list.
    private final @Unmodifiable List<LoadSegment> loadSegments;

    /// The optional `tohost` symbol guest address.
    private final long tohostAddress;

    /// The optional `fromhost` symbol guest address.
    private final long fromhostAddress;

    /// The guest address of the loaded ELF program header table, or `ABSENT_ADDRESS` when absent.
    private final long programHeaderAddress;

    /// The byte size of one ELF program header entry.
    private final int programHeaderEntrySize;

    /// The number of ELF program header entries.
    private final int programHeaderCount;

    /// Creates a validated ELF image description.
    public ElfImage(
            long entryPoint,
            @Unmodifiable List<LoadSegment> loadSegments,
            long tohostAddress,
            long fromhostAddress,
            long programHeaderAddress,
            int programHeaderEntrySize,
            int programHeaderCount) {
        this.entryPoint = entryPoint;
        this.loadSegments = List.copyOf(loadSegments);
        this.tohostAddress = tohostAddress;
        this.fromhostAddress = fromhostAddress;
        this.programHeaderAddress = programHeaderAddress;
        this.programHeaderEntrySize = programHeaderEntrySize;
        this.programHeaderCount = programHeaderCount;
    }

    /// Returns the initial guest program counter.
    public long entryPoint() {
        return entryPoint;
    }

    /// Returns the immutable loadable segment list.
    public @Unmodifiable List<LoadSegment> loadSegments() {
        return loadSegments;
    }

    /// Returns true when the image defines a `tohost` symbol.
    public boolean hasTohostAddress() {
        return tohostAddress != ABSENT_ADDRESS;
    }

    /// Returns the `tohost` symbol guest address, or `ABSENT_ADDRESS` when absent.
    public long tohostAddress() {
        return tohostAddress;
    }

    /// Returns the `fromhost` symbol guest address, or `ABSENT_ADDRESS` when absent.
    public long fromhostAddress() {
        return fromhostAddress;
    }

    /// Returns the guest address of the loaded ELF program header table, or `ABSENT_ADDRESS` when absent.
    public long programHeaderAddress() {
        return programHeaderAddress;
    }

    /// Returns the byte size of one ELF program header entry.
    public int programHeaderEntrySize() {
        return programHeaderEntrySize;
    }

    /// Returns the number of ELF program header entries.
    public int programHeaderCount() {
        return programHeaderCount;
    }

    /// Describes a loadable ELF segment and its guest memory extent.
    ///
    /// @param virtualAddress the guest virtual address where the segment starts
    /// @param contents the bytes present in the ELF file for this segment
    /// @param memorySize the total guest memory size of the segment, including zero-filled bytes
    @NotNullByDefault
    public record LoadSegment(
            long virtualAddress,
            byte @Unmodifiable [] contents,
            long memorySize) {
        /// Creates a loadable segment description.
        public LoadSegment {
            if (memorySize < contents.length) {
                throw new RiscVException("ELF segment memory size is smaller than file size");
            }
            contents = contents.clone();
        }

        /// Returns a copy of the file-backed segment contents.
        @Override
        public byte @Unmodifiable [] contents() {
            return contents.clone();
        }
    }
}
