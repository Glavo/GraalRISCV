// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.exception.*;
import org.glavo.riscv.memory.*;
import org.glavo.riscv.parser.*;
import org.glavo.riscv.runtime.*;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/// Builds small in-memory RISC-V ELF images for tests.
@NotNullByDefault
public final class ElfTestImages {
    /// The test image load address.
    public static final long BASE_ADDRESS = Memory.DEFAULT_BASE_ADDRESS;

    /// The first file offset available for generated loadable segment contents.
    public static final int PROGRAM_OFFSET = 0x1000;

    /// The byte offset of the ELF program header table in generated images.
    public static final int PROGRAM_HEADER_OFFSET = 64;

    /// The size in bytes of one generated ELF64 program header.
    public static final int PROGRAM_HEADER_SIZE = 56;

    /// The size in bytes of one generated ELF64 section header.
    public static final int SECTION_HEADER_SIZE = 64;

    /// The byte offset of `EI_OSABI` in generated ELF images.
    public static final int OS_ABI_OFFSET = 7;

    /// The ELF `EI_OSABI` value for FreeBSD images.
    public static final int OS_ABI_FREEBSD = 9;

    /// The default generated `PT_LOAD` segment flags.
    public static final int DEFAULT_LOAD_FLAGS = 7;

    /// The default generated `PT_LOAD` segment alignment.
    public static final long DEFAULT_LOAD_ALIGNMENT = 0x1000;

    /// Prevents construction of this utility class.
    private ElfTestImages() {
    }

    /// Creates an ELF64 executable containing the supplied instruction words.
    public static byte[] executable(int... instructions) {
        ByteBuffer code = ByteBuffer.allocate(instructions.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int instruction : instructions) {
            code.putInt(instruction);
        }
        return executable(code.array());
    }

    /// Creates an ELF64 executable containing the supplied raw code bytes.
    public static byte[] executable(byte[] code) {
        return executable(BASE_ADDRESS, BASE_ADDRESS, code);
    }

    /// Creates an ELF64 executable containing one loadable segment at the supplied guest address.
    public static byte[] executable(long segmentVirtualAddress, long entryPoint, byte[] segmentContents) {
        return executableWithSegments(
                entryPoint,
                new TestLoadSegment(
                        segmentVirtualAddress,
                        segmentContents.length,
                        DEFAULT_LOAD_FLAGS,
                        DEFAULT_LOAD_ALIGNMENT,
                        segmentContents));
    }

    /// Creates an ELF64 executable containing the supplied loadable segments.
    public static byte[] executableWithSegments(long entryPoint, TestLoadSegment... segments) {
        int[] offsets = new int[segments.length];
        int fileSize = PROGRAM_OFFSET;
        for (int index = 0; index < segments.length; index++) {
            offsets[index] = fileSize;
            fileSize = alignUp(fileSize + segments[index].contents().length, PROGRAM_OFFSET);
        }

        byte[] bytes = new byte[fileSize];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 0x7f);
        buffer.put((byte) 'E');
        buffer.put((byte) 'L');
        buffer.put((byte) 'F');
        buffer.put((byte) 2);
        buffer.put((byte) 1);
        buffer.put((byte) 1);
        buffer.put((byte) 0);
        buffer.position(16);
        buffer.putShort((short) 2);
        buffer.putShort((short) 243);
        buffer.putInt(1);
        buffer.putLong(entryPoint);
        buffer.putLong(64);
        buffer.putLong(0);
        buffer.putInt(0);
        buffer.putShort((short) 64);
        buffer.putShort((short) 56);
        buffer.putShort((short) segments.length);
        buffer.putShort((short) 64);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);

        for (int index = 0; index < segments.length; index++) {
            TestLoadSegment segment = segments[index];
            buffer.position(PROGRAM_HEADER_OFFSET + (index * PROGRAM_HEADER_SIZE));
            buffer.putInt(1);
            buffer.putInt(segment.flags());
            buffer.putLong(offsets[index]);
            buffer.putLong(segment.virtualAddress());
            buffer.putLong(segment.virtualAddress());
            buffer.putLong(segment.contents().length);
            buffer.putLong(segment.memorySize());
            buffer.putLong(segment.alignment());

            buffer.position(offsets[index]);
            buffer.put(segment.contents());
        }
        return bytes;
    }

    /// Creates an ELF64 executable with one section header of the supplied type.
    public static byte[] executableWithSectionType(int sectionType) {
        byte[] executable = executable(ecall());
        int sectionHeaderOffset = alignUp(executable.length, Long.BYTES);
        byte[] bytes = Arrays.copyOf(executable, sectionHeaderOffset + SECTION_HEADER_SIZE);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        buffer.putLong(40, sectionHeaderOffset);
        buffer.putShort(58, (short) SECTION_HEADER_SIZE);
        buffer.putShort(60, (short) 1);
        buffer.putShort(62, (short) 0);

        buffer.position(sectionHeaderOffset);
        buffer.putInt(0);
        buffer.putInt(sectionType);
        return bytes;
    }

    /// Returns a copy of the supplied ELF image with the requested `EI_OSABI` value.
    public static byte[] withOsAbi(byte[] executable, int osAbi) {
        byte[] bytes = executable.clone();
        bytes[OS_ABI_OFFSET] = (byte) osAbi;
        return bytes;
    }

    /// Encodes an I-type instruction.
    public static int iType(int opcode, int rd, int funct3, int rs1, int immediate) {
        return ((immediate & 0xfff) << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | opcode;
    }

    /// Encodes an R-type instruction.
    public static int rType(int opcode, int rd, int funct3, int rs1, int rs2, int funct7) {
        return (funct7 << 25) | (rs2 << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | opcode;
    }

    /// Encodes `addi`.
    public static int addi(int rd, int rs1, int immediate) {
        return iType(0x13, rd, 0, rs1, immediate);
    }

    /// Encodes `andi`.
    public static int andi(int rd, int rs1, int immediate) {
        return iType(0x13, rd, 7, rs1, immediate);
    }

    /// Encodes `auipc`.
    public static int auipc(int rd, int immediate) {
        return (immediate & 0xffff_f000) | (rd << 7) | 0x17;
    }

    /// Encodes `mul`.
    public static int mul(int rd, int rs1, int rs2) {
        return rType(0x33, rd, 0, rs1, rs2, 0x01);
    }

    /// Encodes `ecall`.
    public static int ecall() {
        return 0x0000_0073;
    }

    /// Encodes `c.nop`.
    public static short compressedNop() {
        return 0x0001;
    }

    /// Writes a little-endian compressed instruction into a code buffer.
    public static void putShort(ByteBuffer buffer, int value) {
        buffer.putShort((short) value);
    }

    /// Writes a little-endian 32-bit instruction into a code buffer.
    public static void putInt(ByteBuffer buffer, int value) {
        buffer.putInt(value);
    }

    /// Aligns the supplied value up to the requested power-of-two alignment.
    private static int alignUp(int value, int alignment) {
        return (value + alignment - 1) & -alignment;
    }

    /// Describes one generated `PT_LOAD` test segment.
    ///
    /// @param virtualAddress the guest virtual address where the segment starts
    /// @param memorySize the total guest memory size of the segment
    /// @param flags the ELF `p_flags` value
    /// @param alignment the ELF `p_align` value
    /// @param contents the bytes present in the ELF file for this segment
    @NotNullByDefault
    public record TestLoadSegment(
            long virtualAddress,
            long memorySize,
            int flags,
            long alignment,
            byte @Unmodifiable [] contents) {
        /// Creates a generated `PT_LOAD` segment.
        public TestLoadSegment {
            if (memorySize < contents.length) {
                throw new IllegalArgumentException("Segment memory size is smaller than file size");
            }
            contents = contents.clone();
        }

        /// Returns a copy of the segment contents.
        @Override
        public byte @Unmodifiable [] contents() {
            return contents.clone();
        }
    }
}
