package org.glavo.riscv;

import org.glavo.riscv.exception.*;
import org.glavo.riscv.memory.*;
import org.glavo.riscv.parser.*;
import org.glavo.riscv.runtime.*;
import net.fornwall.jelf.ElfSectionHeader;
import net.fornwall.jelf.ElfSegment;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests ELF loading and validation behavior.
@NotNullByDefault
public final class ElfLoaderTest {
    /// Verifies that a minimal executable ELF is accepted.
    @Test
    public void loadsMinimalExecutable() {
        ElfImage image = ElfLoader.load(ElfTestImages.executable(ElfTestImages.ecall()));

        assertEquals(ElfTestImages.BASE_ADDRESS, image.entryPoint());
        assertEquals(1, image.loadSegments().size());
        assertEquals(ElfTestImages.BASE_ADDRESS, image.loadSegments().getFirst().virtualAddress());
        assertEquals(Integer.BYTES, image.loadSegments().getFirst().contents().length);
        assertEquals(ElfImage.ABSENT_ADDRESS, image.programHeaderAddress());
    }

    /// Verifies that the loader records program header metadata when it is present in a load segment.
    @Test
    public void recordsLoadedProgramHeaderMetadata() {
        byte[] elf = ElfTestImages.executable(ElfTestImages.ecall());
        long segmentAddress = ElfTestImages.BASE_ADDRESS - ElfTestImages.PROGRAM_OFFSET;
        putProgramHeaderLong(elf, 0, 8, 0);
        putProgramHeaderLong(elf, 0, 16, segmentAddress);
        putProgramHeaderLong(elf, 0, 24, segmentAddress);
        putProgramHeaderLong(elf, 0, 32, elf.length);
        putProgramHeaderLong(elf, 0, 40, elf.length);

        ElfImage image = ElfLoader.load(elf);

        assertEquals(segmentAddress + ElfTestImages.PROGRAM_HEADER_OFFSET, image.programHeaderAddress());
        assertEquals(ElfTestImages.PROGRAM_HEADER_SIZE, image.programHeaderEntrySize());
        assertEquals(1, image.programHeaderCount());
    }

    /// Verifies that non-ELF input is rejected.
    @Test
    public void rejectsInvalidElfMagic() {
        assertThrows(RiscVException.class, () -> ElfLoader.load(new byte[]{0, 1, 2, 3}));
    }

    /// Verifies that `PT_LOAD` file offsets must follow the ELF alignment congruence rule.
    @Test
    public void rejectsUnalignedLoadSegmentOffset() {
        byte[] elf = ElfTestImages.executable(ElfTestImages.ecall());
        putProgramHeaderLong(elf, 0, 8, 0x100);

        RiscVException exception = assertThrows(RiscVException.class, () -> ElfLoader.load(elf));

        assertTrue(exception.getMessage().contains("not congruent"));
    }

    /// Verifies that `PT_LOAD` alignment values must be powers of two.
    @Test
    public void rejectsNonPowerOfTwoLoadSegmentAlignment() {
        byte[] elf = ElfTestImages.executable(ElfTestImages.ecall());
        putProgramHeaderLong(elf, 0, 48, 24);

        RiscVException exception = assertThrows(RiscVException.class, () -> ElfLoader.load(elf));

        assertTrue(exception.getMessage().contains("not a power of two"));
    }

    /// Verifies that overlapping loadable segment memory ranges are rejected.
    @Test
    public void rejectsOverlappingLoadSegments() {
        byte[] elf = ElfTestImages.executableWithSegments(
                ElfTestImages.BASE_ADDRESS,
                new ElfTestImages.TestLoadSegment(
                        ElfTestImages.BASE_ADDRESS,
                        0x2000,
                        ElfTestImages.DEFAULT_LOAD_FLAGS,
                        ElfTestImages.DEFAULT_LOAD_ALIGNMENT,
                        rawCode(ElfTestImages.ecall())),
                new ElfTestImages.TestLoadSegment(
                        ElfTestImages.BASE_ADDRESS + 0x1000,
                        0x1000,
                        4,
                        ElfTestImages.DEFAULT_LOAD_ALIGNMENT,
                        new byte[]{1, 2, 3, 4}));

        RiscVException exception = assertThrows(RiscVException.class, () -> ElfLoader.load(elf));

        assertTrue(exception.getMessage().contains("Overlapping ELF PT_LOAD segments"));
    }

    /// Verifies that loadable segments must be readable before they are mapped.
    @Test
    public void rejectsUnreadableLoadSegment() {
        byte[] elf = ElfTestImages.executableWithSegments(
                ElfTestImages.BASE_ADDRESS,
                new ElfTestImages.TestLoadSegment(
                        ElfTestImages.BASE_ADDRESS,
                        Integer.BYTES,
                        1,
                        ElfTestImages.DEFAULT_LOAD_ALIGNMENT,
                        rawCode(ElfTestImages.ecall())));

        RiscVException exception = assertThrows(RiscVException.class, () -> ElfLoader.load(elf));

        assertTrue(exception.getMessage().contains("must be readable"));
    }

    /// Verifies that the entry point must fall inside an executable loadable segment.
    @Test
    public void rejectsEntryPointOutsideExecutableSegment() {
        byte[] elf = ElfTestImages.executableWithSegments(
                ElfTestImages.BASE_ADDRESS,
                new ElfTestImages.TestLoadSegment(
                        ElfTestImages.BASE_ADDRESS,
                        Integer.BYTES,
                        4,
                        ElfTestImages.DEFAULT_LOAD_ALIGNMENT,
                        rawCode(ElfTestImages.ecall())));

        RiscVException exception = assertThrows(RiscVException.class, () -> ElfLoader.load(elf));

        assertTrue(exception.getMessage().contains("not inside an executable PT_LOAD segment"));
    }

    /// Verifies that program-header dynamic metadata is rejected.
    @Test
    public void rejectsDynamicProgramSegment() {
        byte[] elf = ElfTestImages.executable(ElfTestImages.ecall());
        putProgramHeaderInt(elf, 0, 0, ElfSegment.PT_DYNAMIC);

        RiscVException exception = assertThrows(RiscVException.class, () -> ElfLoader.load(elf));

        assertTrue(exception.getMessage().contains("PT_DYNAMIC"));
    }

    /// Verifies that program-header interpreter metadata is rejected.
    @Test
    public void rejectsInterpreterProgramSegment() {
        byte[] elf = ElfTestImages.executable(ElfTestImages.ecall());
        putProgramHeaderInt(elf, 0, 0, ElfSegment.PT_INTERP);

        RiscVException exception = assertThrows(RiscVException.class, () -> ElfLoader.load(elf));

        assertTrue(exception.getMessage().contains("PT_INTERP"));
    }

    /// Verifies that relocation section metadata is rejected.
    @Test
    public void rejectsRelocationSections() {
        assertUnsupportedSection(ElfSectionHeader.SHT_REL);
        assertUnsupportedSection(ElfSectionHeader.SHT_RELA);
    }

    /// Verifies that dynamic section metadata is rejected.
    @Test
    public void rejectsDynamicSections() {
        assertUnsupportedSection(ElfSectionHeader.SHT_DYNAMIC);
    }

    /// Verifies that a specific section type is rejected as unsupported metadata.
    private static void assertUnsupportedSection(int sectionType) {
        byte[] elf = ElfTestImages.executableWithSectionType(sectionType);

        RiscVException exception = assertThrows(RiscVException.class, () -> ElfLoader.load(elf));

        assertTrue(exception.getMessage().contains("Relocatable or dynamic ELF inputs are not supported"));
    }

    /// Writes a 32-bit program header field in a generated ELF image.
    private static void putProgramHeaderInt(byte[] elf, int headerIndex, int fieldOffset, int value) {
        ByteBuffer.wrap(elf)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(programHeaderFieldOffset(headerIndex, fieldOffset), value);
    }

    /// Writes a 64-bit program header field in a generated ELF image.
    private static void putProgramHeaderLong(byte[] elf, int headerIndex, int fieldOffset, long value) {
        ByteBuffer.wrap(elf)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(programHeaderFieldOffset(headerIndex, fieldOffset), value);
    }

    /// Returns the absolute byte offset of a field inside a generated program header.
    private static int programHeaderFieldOffset(int headerIndex, int fieldOffset) {
        return ElfTestImages.PROGRAM_HEADER_OFFSET
                + (headerIndex * ElfTestImages.PROGRAM_HEADER_SIZE)
                + fieldOffset;
    }

    /// Encodes the supplied instruction words as raw little-endian code bytes.
    private static byte[] rawCode(int... instructions) {
        ByteBuffer code = ByteBuffer.allocate(instructions.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int instruction : instructions) {
            ElfTestImages.putInt(code, instruction);
        }
        return code.array();
    }
}
