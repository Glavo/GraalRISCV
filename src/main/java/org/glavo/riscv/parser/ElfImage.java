// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.parser;

import org.glavo.riscv.exception.RiscVException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Describes a loaded and validated RISC-V ELF image before it is copied into guest memory.
@NotNullByDefault
public final class ElfImage {
    /// Marks an optional guest address that is not present in the image.
    public static final long ABSENT_ADDRESS = -1L;

    /// ELF object type for a fixed-address executable image.
    public static final int TYPE_EXECUTABLE = 2;

    /// ELF object type for a position-independent executable or shared object.
    public static final int TYPE_DYNAMIC = 3;

    /// The initial guest program counter.
    private final long entryPoint;

    /// The ELF object type.
    private final int type;

    /// The optional dynamic interpreter path from `PT_INTERP`.
    private final @Nullable String interpreterPath;

    /// The guest operating-system ABI requested by the ELF header.
    private final ElfOperatingSystem operatingSystem;

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
            int type,
            long entryPoint,
            ElfOperatingSystem operatingSystem,
            @Nullable String interpreterPath,
            @Unmodifiable List<LoadSegment> loadSegments,
            long tohostAddress,
            long fromhostAddress,
            long programHeaderAddress,
            int programHeaderEntrySize,
            int programHeaderCount) {
        this.type = type;
        this.entryPoint = entryPoint;
        this.operatingSystem = operatingSystem;
        this.interpreterPath = interpreterPath;
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

    /// Returns the ELF object type.
    public int type() {
        return type;
    }

    /// Returns true when this image uses position-independent `ET_DYN` addresses.
    public boolean isPositionIndependent() {
        return type == TYPE_DYNAMIC;
    }

    /// Returns the guest operating-system ABI requested by the ELF header.
    public ElfOperatingSystem operatingSystem() {
        return operatingSystem;
    }

    /// Returns the optional dynamic interpreter path from `PT_INTERP`.
    public @Nullable String interpreterPath() {
        return interpreterPath;
    }

    /// Returns true when the image requests a dynamic interpreter.
    public boolean hasInterpreter() {
        return interpreterPath != null;
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
    /// @param virtualAddress the ELF virtual address where the segment starts before load-bias relocation
    /// @param fileOffset the ELF file offset backing the segment contents
    /// @param contents the bytes present in the ELF file for this segment
    /// @param memorySize the total guest memory size of the segment, including zero-filled bytes
    /// @param flags the ELF `p_flags` access bits
    /// @param alignment the ELF `p_align` value
    @NotNullByDefault
    public record LoadSegment(
            long virtualAddress,
            long fileOffset,
            byte @Unmodifiable [] contents,
            long memorySize,
            int flags,
            long alignment) {
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
