package org.glavo.riscv;

import net.fornwall.jelf.ElfException;
import net.fornwall.jelf.ElfFile;
import net.fornwall.jelf.ElfSectionHeader;
import net.fornwall.jelf.ElfSegment;
import net.fornwall.jelf.ElfSymbol;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/// Parses and validates the ELF subset accepted by the bare-metal simulator.
@NotNullByDefault
public final class ElfLoader {
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
            boolean entryPointIsExecutable = false;
            for (int index = 0; index < Short.toUnsignedInt(elfFile.e_phnum); index++) {
                ElfSegment segment = elfFile.getProgramHeader(index);
                if (segment.p_type == ElfSegment.PT_DYNAMIC) {
                    throw new RiscVException("Dynamic ELF files are not supported");
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

                byte[] contents = new byte[(int) segment.p_filesz];
                System.arraycopy(bytes, (int) segment.p_offset, contents, 0, (int) segment.p_filesz);
                loadSegments.add(new ElfImage.LoadSegment(segment.p_vaddr, contents, segment.p_memsz));
            }
            if (!entryPointIsExecutable) {
                throw new RiscVException("ELF entry point is not inside an executable PT_LOAD segment: 0x"
                        + Long.toHexString(elfFile.e_entry));
            }

            return new ElfImage(
                    elfFile.e_entry,
                    loadSegments,
                    symbolAddress(elfFile, "tohost"),
                    symbolAddress(elfFile, "fromhost"));
        } catch (ElfException | IndexOutOfBoundsException exception) {
            throw new RiscVException("Invalid ELF file", exception);
        }
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
        if (elfFile.e_type != ElfFile.ET_EXEC) {
            throw new RiscVException("Unsupported ELF type: " + elfFile.e_type);
        }
        if (Short.toUnsignedInt(elfFile.e_machine) != ElfFile.ARCH_RISCV) {
            throw new RiscVException("Unsupported ELF machine type: " + Short.toUnsignedInt(elfFile.e_machine));
        }
    }

    /// Rejects relocation and dynamic metadata that the bare-metal MVP does not process.
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

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < sectionCount; index++) {
            int sectionHeaderOffset = (int) (elfFile.e_shoff + ((long) index * sectionHeaderSize));
            int type = buffer.getInt(sectionHeaderOffset + 4);
            if (type == ElfSectionHeader.SHT_REL
                    || type == ElfSectionHeader.SHT_RELA
                    || type == ElfSectionHeader.SHT_DYNAMIC) {
                throw new RiscVException("Relocatable or dynamic ELF inputs are not supported");
            }
        }
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
}
