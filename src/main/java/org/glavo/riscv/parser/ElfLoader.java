// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.parser;

import net.fornwall.jelf.ElfException;
import net.fornwall.jelf.ElfFile;
import net.fornwall.jelf.ElfSegment;
import net.fornwall.jelf.ElfSymbol;
import org.glavo.riscv.exception.RiscVException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/// Parses and validates the ELF subset accepted by the bare-metal simulator.
@NotNullByDefault
public final class ElfLoader {
    /// ELF object type for position-independent executables and shared objects.
    private static final int ET_DYN = 3;

    /// Prevents construction of this utility class.
    private ElfLoader() {
    }

    /// Parses a RISC-V ELF64 executable image.
    public static ElfImage load(byte[] bytes) {
        try {
            ElfFile elfFile = ElfFile.from(bytes);
            validateHeader(elfFile);
            validateSections(elfFile, bytes);

            ArrayList<ElfImage.LoadSegment> loadSegments = new ArrayList<>();
            long programHeaderAddress = ElfImage.ABSENT_ADDRESS;
            int programHeaderEntrySize = Short.toUnsignedInt(elfFile.e_phentsize);
            int programHeaderCount = Short.toUnsignedInt(elfFile.e_phnum);
            long programHeaderTableSize = (long) programHeaderEntrySize * programHeaderCount;
            boolean entryPointIsExecutable = false;
            @Nullable String interpreterPath = null;
            for (int index = 0; index < programHeaderCount; index++) {
                ElfSegment segment = elfFile.getProgramHeader(index);
                validateProgramHeader(segment);
                if (segment.p_type == ElfSegment.PT_INTERP) {
                    if (interpreterPath != null) {
                        throw new RiscVException("ELF image contains more than one PT_INTERP segment");
                    }
                    interpreterPath = interpreterPath(bytes, segment);
                    continue;
                }
                if (segment.p_type != ElfSegment.PT_LOAD) {
                    continue;
                }

                validateLoadSegment(segment);
                long segmentEnd = segmentEnd(segment.p_vaddr, segment.p_memsz);
                for (ElfImage.LoadSegment existingSegment : loadSegments) {
                    if (rangesOverlap(
                            segment.p_vaddr,
                            segmentEnd,
                            existingSegment.virtualAddress(),
                            segmentEnd(existingSegment.virtualAddress(), existingSegment.memorySize()))) {
                        throw new RiscVException("Overlapping ELF PT_LOAD segments: address=0x"
                                + Long.toHexString(segment.p_vaddr)
                                + ", existing=0x"
                                + Long.toHexString(existingSegment.virtualAddress()));
                    }
                }

                requireRange(bytes, segment.p_offset, segment.p_filesz, "segment contents");
                if (segment.isExecutable() && containsAddress(segment.p_vaddr, segmentEnd, elfFile.e_entry)) {
                    entryPointIsExecutable = true;
                }
                if (programHeaderAddress == ElfImage.ABSENT_ADDRESS
                        && containsFileRange(segment.p_offset, segment.p_filesz, elfFile.e_phoff, programHeaderTableSize)) {
                    programHeaderAddress = segment.p_vaddr + (elfFile.e_phoff - segment.p_offset);
                }

                byte[] contents = new byte[(int) segment.p_filesz];
                System.arraycopy(bytes, (int) segment.p_offset, contents, 0, (int) segment.p_filesz);
                loadSegments.add(new ElfImage.LoadSegment(
                        segment.p_vaddr,
                        segment.p_offset,
                        contents,
                        segment.p_memsz,
                        segment.p_flags,
                        segment.p_align));
            }
            if (!entryPointIsExecutable) {
                throw new RiscVException("ELF entry point is not inside an executable PT_LOAD segment: 0x"
                        + Long.toHexString(elfFile.e_entry));
            }

            return new ElfImage(
                    elfFile.e_type,
                    elfFile.e_entry,
                    interpreterPath,
                    loadSegments,
                    symbolAddress(elfFile, "tohost"),
                    symbolAddress(elfFile, "fromhost"),
                    programHeaderAddress,
                    programHeaderEntrySize,
                    programHeaderCount);
        } catch (ElfException | IndexOutOfBoundsException exception) {
            throw new RiscVException("Invalid ELF file", exception);
        }
    }

    /// Validates program header metadata that does not directly map guest memory.
    private static void validateProgramHeader(ElfSegment segment) {
        if (segment.p_type == ElfSegment.PT_INTERP || segment.p_type == ElfSegment.PT_DYNAMIC) {
            if (segment.p_offset < 0 || segment.p_filesz < 0 || segment.p_filesz > Integer.MAX_VALUE) {
                throw new RiscVException("Invalid dynamic ELF segment range");
            }
        }
    }

    /// Extracts a null-terminated dynamic interpreter path from a `PT_INTERP` segment.
    private static String interpreterPath(byte[] bytes, ElfSegment segment) {
        requireRange(bytes, segment.p_offset, segment.p_filesz, "interpreter path");
        if (segment.p_filesz <= 1) {
            throw new RiscVException("ELF interpreter path is empty");
        }

        int offset = (int) segment.p_offset;
        int size = (int) segment.p_filesz;
        if (bytes[offset + size - 1] != 0) {
            throw new RiscVException("ELF interpreter path is not null-terminated");
        }

        String path = new String(bytes, offset, size - 1, StandardCharsets.UTF_8);
        if (!path.startsWith("/")) {
            throw new RiscVException("ELF interpreter path must be absolute: " + path);
        }
        return path;
    }

    /// Validates one `PT_LOAD` segment before its bytes are copied out of the ELF file.
    private static void validateLoadSegment(ElfSegment segment) {
        if (segment.p_offset < 0) {
            throw new RiscVException("Invalid ELF segment file offset: " + segment.p_offset);
        }
        if (segment.p_vaddr < 0) {
            throw new RiscVException("Invalid ELF segment virtual address: " + segment.p_vaddr);
        }
        if (segment.p_filesz < 0
                || segment.p_memsz < 0
                || segment.p_memsz < segment.p_filesz
                || segment.p_filesz > Integer.MAX_VALUE) {
            throw new RiscVException("Invalid ELF segment size");
        }
        segmentEnd(segment.p_vaddr, segment.p_memsz);
        validateLoadSegmentFlags(segment);
        validateLoadSegmentAlignment(segment);
    }

    /// Validates that a `PT_LOAD` segment uses supported access flags.
    private static void validateLoadSegmentFlags(ElfSegment segment) {
        if (!segment.isReadable()) {
            throw new RiscVException("ELF PT_LOAD segment must be readable: address=0x"
                    + Long.toHexString(segment.p_vaddr)
                    + ", flags=0x"
                    + Integer.toHexString(segment.p_flags));
        }
    }

    /// Validates the ELF `p_align` power-of-two and address congruence rules.
    private static void validateLoadSegmentAlignment(ElfSegment segment) {
        if (segment.p_align < 0) {
            throw new RiscVException("Invalid ELF segment alignment: " + segment.p_align);
        }
        if (segment.p_align <= 1) {
            return;
        }
        if ((segment.p_align & (segment.p_align - 1)) != 0) {
            throw new RiscVException("ELF segment alignment is not a power of two: " + segment.p_align);
        }
        if (segment.p_vaddr % segment.p_align != segment.p_offset % segment.p_align) {
            throw new RiscVException("ELF segment address and file offset are not congruent for alignment: address=0x"
                    + Long.toHexString(segment.p_vaddr)
                    + ", offset=0x"
                    + Long.toHexString(segment.p_offset)
                    + ", align=0x"
                    + Long.toHexString(segment.p_align));
        }
    }

    /// Validates the ELF header fields that define the accepted executable format.
    private static void validateHeader(ElfFile elfFile) {
        if (elfFile.ei_class != ElfFile.CLASS_64) {
            throw new RiscVException("Only ELF64 files are supported");
        }
        if (elfFile.ei_data != ElfFile.DATA_LSB) {
            throw new RiscVException("Only little-endian ELF files are supported");
        }
        if (elfFile.e_type != ElfFile.ET_EXEC && elfFile.e_type != ET_DYN) {
            throw new RiscVException("Unsupported ELF type: " + elfFile.e_type);
        }
        if (Short.toUnsignedInt(elfFile.e_machine) != ElfFile.ARCH_RISCV) {
            throw new RiscVException("Unsupported ELF machine type: " + Short.toUnsignedInt(elfFile.e_machine));
        }
    }

    /// Validates section header metadata ranges when an ELF section table is present.
    private static void validateSections(ElfFile elfFile, byte[] bytes) {
        int sectionCount = Short.toUnsignedInt(elfFile.e_shnum);
        if (sectionCount == 0) {
            return;
        }

        int sectionHeaderSize = Short.toUnsignedInt(elfFile.e_shentsize);
        if (sectionHeaderSize < 8) {
            throw new RiscVException("Invalid ELF section header size: " + sectionHeaderSize);
        }

        long sectionTableSize = (long) sectionHeaderSize * sectionCount;
        requireRange(bytes, elfFile.e_shoff, sectionTableSize, "section header table");
    }

    /// Returns a symbol's guest address, or `ABSENT_ADDRESS` when no such symbol exists.
    private static long symbolAddress(ElfFile elfFile, String name) {
        ElfSymbol symbol = elfFile.getELFSymbol(name);
        return symbol == null ? ElfImage.ABSENT_ADDRESS : symbol.st_value;
    }

    /// Validates that a byte range is present in the ELF input.
    private static void requireRange(byte[] bytes, long offset, long size, String description) {
        if (offset < 0 || size < 0 || offset > bytes.length || size > bytes.length - offset) {
            throw new RiscVException("ELF " + description + " is out of range");
        }
    }

    /// Computes the exclusive end of a guest memory range.
    private static long segmentEnd(long address, long size) {
        if (Long.MAX_VALUE - address < size) {
            throw new RiscVException("ELF segment address range overflows: address=0x"
                    + Long.toHexString(address)
                    + ", size="
                    + size);
        }
        return address + size;
    }

    /// Returns true if two half-open guest address ranges overlap.
    private static boolean rangesOverlap(long firstStart, long firstEnd, long secondStart, long secondEnd) {
        return firstStart < secondEnd && secondStart < firstEnd;
    }

    /// Returns true if the half-open address range contains the supplied guest address.
    private static boolean containsAddress(long start, long end, long address) {
        return start <= address && address < end;
    }

    /// Returns true if a file range contains another complete file range.
    private static boolean containsFileRange(long segmentOffset, long segmentSize, long requestedOffset, long requestedSize) {
        if (requestedSize < 0 || requestedOffset < segmentOffset) {
            return false;
        }
        long relativeOffset = requestedOffset - segmentOffset;
        return relativeOffset <= segmentSize && requestedSize <= segmentSize - relativeOffset;
    }
}
