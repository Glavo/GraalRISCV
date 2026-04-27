package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests Linux-style initial stack construction.
@NotNullByDefault
public final class LinuxInitialStackTest {
    /// The expected Linux auxv terminator type.
    private static final long AT_NULL = 0;

    /// The expected Linux auxv page-size type.
    private static final long AT_PAGESZ = 6;

    /// The expected Linux auxv entry-point type.
    private static final long AT_ENTRY = 9;

    /// The expected Linux auxv platform string pointer type.
    private static final long AT_PLATFORM = 15;

    /// The expected Linux auxv random bytes pointer type.
    private static final long AT_RANDOM = 25;

    /// Verifies argc, argv, envp, auxv, and alignment in the generated stack.
    @Test
    public void buildsAlignedLinuxInitialStack() {
        try (Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 8192)) {
            ElfImage image = ElfLoader.load(ElfTestImages.executable(ElfTestImages.ecall()));
            long stackPointer = LinuxInitialStack.initialize(
                    memory,
                    memory.endAddress(),
                    new String[]{"program.elf", "first", "--flag"},
                    image);

            assertEquals(0, stackPointer & 0xf);
            assertEquals(3, memory.readLong(stackPointer));

            long argv0 = memory.readLong(stackPointer + Long.BYTES);
            long argv1 = memory.readLong(stackPointer + 2L * Long.BYTES);
            long argv2 = memory.readLong(stackPointer + 3L * Long.BYTES);
            assertEquals("program.elf", readString(memory, argv0));
            assertEquals("first", readString(memory, argv1));
            assertEquals("--flag", readString(memory, argv2));
            assertEquals(0, memory.readLong(stackPointer + 4L * Long.BYTES));

            long envp = stackPointer + 5L * Long.BYTES;
            assertEquals("LANG=C", readString(memory, memory.readLong(envp)));
            assertEquals("PATH=/usr/bin:/bin", readString(memory, memory.readLong(envp + Long.BYTES)));
            assertEquals("PWD=/", readString(memory, memory.readLong(envp + 2L * Long.BYTES)));
            assertEquals(0, memory.readLong(envp + 3L * Long.BYTES));

            long auxv = envp + 4L * Long.BYTES;
            assertAuxvContains(memory, auxv, AT_PAGESZ, 4096);
            assertAuxvContains(memory, auxv, AT_ENTRY, image.entryPoint());
            long platform = auxvValue(memory, auxv, AT_PLATFORM);
            assertEquals("riscv64", readString(memory, platform));
            long random = auxvValue(memory, auxv, AT_RANDOM);
            assertEquals('G', memory.readUnsignedByte(random));
            assertEquals('8', memory.readUnsignedByte(random + 15));
        }
    }

    /// Asserts that an auxv pair exists.
    private static void assertAuxvContains(Memory memory, long auxv, long type, long expectedValue) {
        assertEquals(expectedValue, auxvValue(memory, auxv, type));
    }

    /// Finds an auxv value by type.
    private static long auxvValue(Memory memory, long auxv, long requestedType) {
        for (int index = 0; index < 32; index++) {
            long entry = auxv + (long) index * 2L * Long.BYTES;
            long type = memory.readLong(entry);
            long value = memory.readLong(entry + Long.BYTES);
            if (type == requestedType) {
                return value;
            }
            if (type == AT_NULL) {
                break;
            }
        }
        throw new AssertionError("Missing auxv type: " + requestedType);
    }

    /// Reads a null-terminated guest string.
    private static String readString(Memory memory, long address) {
        StringBuilder builder = new StringBuilder();
        long cursor = address;
        while (true) {
            int value = memory.readUnsignedByte(cursor);
            if (value == 0) {
                return builder.toString();
            }
            assertTrue(value >= 0x20 && value <= 0x7e);
            builder.append((char) value);
            cursor++;
        }
    }
}
