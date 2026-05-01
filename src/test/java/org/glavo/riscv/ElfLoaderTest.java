// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.exception.*;
import org.glavo.riscv.memory.*;
import org.glavo.riscv.parser.*;
import org.glavo.riscv.runtime.*;
import net.fornwall.jelf.ElfSectionHeader;
import net.fornwall.jelf.ElfSegment;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

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

    /// Verifies that program-header dynamic metadata is accepted for runtime-loaded ELF images.
    @Test
    public void acceptsDynamicProgramSegment() {
        byte[] elf = withExtraProgramHeader(ElfTestImages.executable(ElfTestImages.ecall()), ElfSegment.PT_DYNAMIC);

        ElfImage image = ElfLoader.load(elf);

        assertEquals(2, image.programHeaderCount());
    }

    /// Verifies that program-header interpreter metadata is recorded.
    @Test
    public void recordsInterpreterProgramSegment() {
        byte[] elf = withInterpreter(ElfTestImages.executable(ElfTestImages.ecall()), "/lib/ld-linux-riscv64-lp64d.so.1");

        ElfImage image = ElfLoader.load(elf);

        assertEquals("/lib/ld-linux-riscv64-lp64d.so.1", image.interpreterPath());
    }

    /// Verifies that relocation section metadata does not block runtime ELF loading.
    @Test
    public void acceptsRelocationSections() {
        assertAcceptedSection(ElfSectionHeader.SHT_REL);
        assertAcceptedSection(ElfSectionHeader.SHT_RELA);
    }

    /// Verifies that dynamic section metadata does not block runtime ELF loading.
    @Test
    public void acceptsDynamicSections() {
        assertAcceptedSection(ElfSectionHeader.SHT_DYNAMIC);
    }

    /// Verifies that a specific section type is accepted as metadata.
    private static void assertAcceptedSection(int sectionType) {
        byte[] elf = ElfTestImages.executableWithSectionType(sectionType);

        ElfLoader.load(elf);
    }

    /// Returns an ELF image with one extra non-load program header.
    private static byte[] withExtraProgramHeader(byte[] executable, int programHeaderType) {
        byte[] elf = Arrays.copyOf(executable, executable.length);
        putHeaderShort(elf, 56, 2);
        putProgramHeaderInt(elf, 1, 0, programHeaderType);
        return elf;
    }

    /// Returns an ELF image with one extra `PT_INTERP` program header.
    private static byte[] withInterpreter(byte[] executable, String interpreterPath) {
        byte[] elf = withExtraProgramHeader(executable, ElfSegment.PT_INTERP);
        byte[] path = (interpreterPath + "\0").getBytes(StandardCharsets.UTF_8);
        int pathOffset = 0x1800;
        System.arraycopy(path, 0, elf, pathOffset, path.length);
        putProgramHeaderLong(elf, 1, 8, pathOffset);
        putProgramHeaderLong(elf, 1, 32, path.length);
        putProgramHeaderLong(elf, 1, 40, path.length);
        return elf;
    }

    /// Writes a 16-bit ELF header field in a generated ELF image.
    private static void putHeaderShort(byte[] elf, int fieldOffset, int value) {
        ByteBuffer.wrap(elf)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(fieldOffset, (short) value);
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
