package org.glavo.riscv;

import net.fornwall.jelf.ElfException;
import net.fornwall.jelf.ElfFile;
import net.fornwall.jelf.ElfSection;
import net.fornwall.jelf.ElfSectionHeader;
import net.fornwall.jelf.ElfSegment;
import net.fornwall.jelf.ElfSymbol;
import org.jetbrains.annotations.NotNullByDefault;

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
            validateSections(elfFile);

            ArrayList<ElfImage.LoadSegment> loadSegments = new ArrayList<>();
            for (int index = 0; index < Short.toUnsignedInt(elfFile.e_phnum); index++) {
                ElfSegment segment = elfFile.getProgramHeader(index);
                if (segment.p_type == ElfSegment.PT_DYNAMIC) {
                    throw new RiscVException("Dynamic ELF files are not supported");
                }
                if (segment.p_type != ElfSegment.PT_LOAD) {
                    continue;
                }

                if (segment.p_filesz < 0
                        || segment.p_memsz < 0
                        || segment.p_memsz < segment.p_filesz
                        || segment.p_filesz > Integer.MAX_VALUE) {
                    throw new RiscVException("Invalid ELF segment size");
                }
                requireRange(bytes, segment.p_offset, segment.p_filesz, "segment contents");

                byte[] contents = new byte[(int) segment.p_filesz];
                System.arraycopy(bytes, (int) segment.p_offset, contents, 0, (int) segment.p_filesz);
                loadSegments.add(new ElfImage.LoadSegment(segment.p_vaddr, contents, segment.p_memsz));
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
    private static void validateSections(ElfFile elfFile) {
        for (int index = 0; index < Short.toUnsignedInt(elfFile.e_shnum); index++) {
            ElfSection section = elfFile.getSection(index);
            int type = section.header.sh_type;
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
}
