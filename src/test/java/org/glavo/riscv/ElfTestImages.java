package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/// Builds small in-memory RISC-V ELF images for tests.
@NotNullByDefault
public final class ElfTestImages {
    /// The test image load address.
    public static final long BASE_ADDRESS = Memory.DEFAULT_BASE_ADDRESS;

    /// The offset of the single loadable segment in the generated ELF file.
    private static final int PROGRAM_OFFSET = 0x100;

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
        byte[] bytes = new byte[PROGRAM_OFFSET + code.length];
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
        buffer.putLong(BASE_ADDRESS);
        buffer.putLong(64);
        buffer.putLong(0);
        buffer.putInt(0);
        buffer.putShort((short) 64);
        buffer.putShort((short) 56);
        buffer.putShort((short) 1);
        buffer.putShort((short) 64);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);

        buffer.position(64);
        buffer.putInt(1);
        buffer.putInt(7);
        buffer.putLong(PROGRAM_OFFSET);
        buffer.putLong(BASE_ADDRESS);
        buffer.putLong(BASE_ADDRESS);
        buffer.putLong(code.length);
        buffer.putLong(code.length);
        buffer.putLong(0x1000);

        buffer.position(PROGRAM_OFFSET);
        buffer.put(code);
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
}
