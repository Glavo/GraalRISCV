package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests the command-line entry point.
@NotNullByDefault
public final class MainTest {
    /// A temporary directory for generated ELF files.
    @TempDir
    private Path tempDirectory;

    /// Verifies that the CLI can execute an ELF program that writes to stdout.
    @Test
    public void runsHelloWorldProgram() throws Exception {
        Path elfPath = tempDirectory.resolve("hello.elf");
        Files.write(elfPath, ElfTestImages.executable(helloWorldCode()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{"--max-instructions", "1000", elfPath.toString()},
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        assertEquals(0, exitCode);
        assertEquals("Hello World!\n", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    /// Verifies that the CLI can execute an ELF whose load segment starts below `0x80000000`.
    @Test
    public void runsPageAlignedSegmentBelowDefaultMemoryBase() throws Exception {
        Path elfPath = tempDirectory.resolve("hello-low-segment.elf");
        int padding = 0x1000;
        byte[] program = helloWorldCode();
        ByteBuffer segment = ByteBuffer.allocate(padding + program.length).order(ByteOrder.LITTLE_ENDIAN);
        segment.position(padding);
        segment.put(program);
        Files.write(elfPath, ElfTestImages.executable(
                ElfTestImages.BASE_ADDRESS - padding,
                ElfTestImages.BASE_ADDRESS,
                segment.array()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{
                        "--memory-base", "0x7ffff000",
                        "--max-instructions", "1000",
                        elfPath.toString()
                },
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        assertEquals(0, exitCode);
        assertEquals("Hello World!\n", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    /// Verifies that the CLI reports missing program paths as usage errors.
    @Test
    public void reportsMissingProgramPath() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[0],
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        assertEquals(2, exitCode);
    }

    /// Builds a freestanding Hello World program using the same ABI as a compiled C program would use.
    private static byte[] helloWorldCode() {
        byte[] message = "Hello World!\n".getBytes(StandardCharsets.UTF_8);
        int messageOffset = Integer.BYTES * 9;
        ByteBuffer code = ByteBuffer.allocate(messageOffset + message.length).order(ByteOrder.LITTLE_ENDIAN);
        ElfTestImages.putInt(code, ElfTestImages.auipc(11, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(11, 11, messageOffset));
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 1));
        ElfTestImages.putInt(code, ElfTestImages.addi(12, 0, message.length));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 64));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 93));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        code.put(message);
        return code.array();
    }
}
