package org.glavo.riscv;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /// Verifies that the CLI can infer a memory base from an ELF segment below `0x80000000`.
    @Test
    public void runsPageAlignedSegmentBelowDefaultMemoryBase() throws Exception {
        Path elfPath = tempDirectory.resolve("hello-low-segment.elf");
        Files.write(elfPath, pageAlignedHelloWorldElf());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{
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

    /// Verifies that the CLI accepts an explicit `auto` memory base.
    @Test
    public void acceptsExplicitAutoMemoryBase() throws Exception {
        Path elfPath = tempDirectory.resolve("hello-auto-base.elf");
        Files.write(elfPath, pageAlignedHelloWorldElf());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{
                        "--memory-base", "auto",
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

    /// Verifies that arguments after the ELF path are accepted as guest program arguments.
    @Test
    public void acceptsGuestProgramArgumentsAfterElfPath() throws Exception {
        Path elfPath = tempDirectory.resolve("hello-with-args.elf");
        Files.write(elfPath, ElfTestImages.executable(helloWorldCode()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{
                        "--max-instructions", "1000",
                        elfPath.toString(),
                        "first",
                        "--guest-flag"
                },
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        assertEquals(0, exitCode);
        assertEquals("Hello World!\n", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    /// Verifies that an explicit memory base above the ELF load address reports a useful layout error.
    @Test
    public void reportsElfSegmentBelowMemoryBase() throws Exception {
        Path elfPath = tempDirectory.resolve("segment-below-memory-base.elf");
        Files.write(elfPath, ElfTestImages.executable(ElfTestImages.ecall()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{
                        "--memory-base", "0x80001000",
                        "--max-instructions", "1000",
                        elfPath.toString()
                },
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        String diagnostics = err.toString(StandardCharsets.UTF_8);
        assertEquals(1, exitCode);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertTrue(diagnostics.contains("Execution failed:"));
        assertTrue(diagnostics.contains("ELF segment is outside guest memory"));
        assertTrue(diagnostics.contains("segment=[0x80000000,"));
        assertTrue(diagnostics.contains("memory=[0x80001000,"));
        assertTrue(diagnostics.contains("--memory-base auto"));
        assertTrue(diagnostics.contains("--memory-base 0x80000000"));
    }

    /// Verifies that too little guest memory reports the minimum required memory size.
    @Test
    public void reportsElfSegmentAboveMemoryEnd() throws Exception {
        Path elfPath = tempDirectory.resolve("segment-above-memory-end.elf");
        byte[] segment = new byte[64];
        ByteBuffer.wrap(segment).order(ByteOrder.LITTLE_ENDIAN).putInt(ElfTestImages.ecall());
        Files.write(elfPath, ElfTestImages.executable(segment));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{
                        "--memory-size", "16",
                        "--max-instructions", "1000",
                        elfPath.toString()
                },
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        String diagnostics = err.toString(StandardCharsets.UTF_8);
        assertEquals(1, exitCode);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertTrue(diagnostics.contains("Execution failed:"));
        assertTrue(diagnostics.contains("ELF segment is outside guest memory"));
        assertTrue(diagnostics.contains("segment=[0x80000000,0x80000040)"));
        assertTrue(diagnostics.contains("memory=[0x80000000,0x80000010)"));
        assertTrue(diagnostics.contains("Increase --memory-size"));
        assertTrue(diagnostics.contains("at least 64 bytes"));
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

    /// Verifies that guest stdin is exposed through the `read` syscall.
    @Test
    public void readsFromStdinAndWritesToStdout() throws Exception {
        Path elfPath = tempDirectory.resolve("echo.elf");
        Files.write(elfPath, ElfTestImages.executable(echoOneByteCode()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{"--max-instructions", "1000", elfPath.toString()},
                new ByteArrayInputStream("Z".getBytes(StandardCharsets.UTF_8)),
                out,
                err);

        assertEquals(0, exitCode);
        assertEquals("Z", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    /// Verifies that unsupported syscalls include enough guest state for diagnosis.
    @Test
    public void reportsUnsupportedSyscallContext() throws Exception {
        Path elfPath = tempDirectory.resolve("unsupported-syscall.elf");
        Files.write(elfPath, ElfTestImages.executable(unsupportedSyscallCode()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{"--max-instructions", "1000", elfPath.toString()},
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        String diagnostics = err.toString(StandardCharsets.UTF_8);
        assertEquals(1, exitCode);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertTrue(diagnostics.contains("Unsupported ecall number: 2047"));
        assertTrue(diagnostics.contains("pc=0x80000020"));
        assertTrue(diagnostics.contains("a0=0x1"));
        assertTrue(diagnostics.contains("a1=0x2"));
        assertTrue(diagnostics.contains("a2=0x3"));
        assertTrue(diagnostics.contains("a3=0x4"));
        assertTrue(diagnostics.contains("a4=0x5"));
        assertTrue(diagnostics.contains("a5=0x6"));
        assertTrue(diagnostics.contains("a6=0x7"));
        assertTrue(diagnostics.contains("a7=0x7ff"));
    }

    /// Verifies that CLI tracing writes instruction lines to the configured error stream.
    @Test
    public void writesTraceToErrorStream() throws Exception {
        Path elfPath = tempDirectory.resolve("trace.elf");
        Files.write(elfPath, ElfTestImages.executable(
                ElfTestImages.addi(10, 0, 42),
                ElfTestImages.addi(17, 0, 93),
                ElfTestImages.ecall()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{"--trace", "--max-instructions", "1000", elfPath.toString()},
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        String diagnostics = err.toString(StandardCharsets.UTF_8);
        assertEquals(42, exitCode);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertTrue(diagnostics.contains("pc=0x80000000 raw=0x"));
        assertTrue(diagnostics.contains("pc=0x80000004 raw=0x"));
        assertTrue(diagnostics.contains("pc=0x80000008 raw=0x"));
    }

    /// Verifies that the CLI debug clock option is exposed to guest `clock_gettime`.
    @Test
    public void usesDebugFixedClockNanosOption() throws Exception {
        Path elfPath = tempDirectory.resolve("clock.elf");
        Files.write(elfPath, ElfTestImages.executable(clockRealtimeExitCode()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{
                        "--debug-fixed-clock-nanos", "42000000123",
                        "--max-instructions", "1000",
                        elfPath.toString()
                },
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        assertEquals(42, exitCode);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    /// Verifies that compiled dispatch misses can run cold block batches without repeated OSR deoptimization.
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void compiledDispatchRunsProgramsWithManyLoopBlocks() throws Exception {
        Path elfPath = tempDirectory.resolve("dispatch-stress.elf");
        Files.write(elfPath, ElfTestImages.executable(dispatchStressCode()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                dispatchStressArguments(elfPath),
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        String diagnostics = err.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertTrue(!diagnostics.contains("Execution failed:"));
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

    /// Builds a loop with more distinct basic blocks than the dispatch inline cache limit.
    private static byte[] dispatchStressCode() {
        int blockCount = 40;
        int loopIterations = 12_000;
        int loopStartOffset = Integer.BYTES * 2;
        int loopInstructionCount = (blockCount * 2) + 2;
        ByteBuffer code = ByteBuffer
                .allocate((Integer.BYTES * (2 + loopInstructionCount + 3)))
                .order(ByteOrder.LITTLE_ENDIAN);

        ElfTestImages.putInt(code, lui(5, 0x3000));
        ElfTestImages.putInt(code, ElfTestImages.addi(5, 5, loopIterations - 0x3000));
        for (int index = 0; index < blockCount; index++) {
            ElfTestImages.putInt(code, ElfTestImages.addi(6, 6, 1));
            ElfTestImages.putInt(code, jal(0, Integer.BYTES));
        }
        ElfTestImages.putInt(code, ElfTestImages.addi(5, 5, -1));
        ElfTestImages.putInt(code, bne(5, 0, loopStartOffset - code.position()));
        ElfTestImages.putInt(code, ElfTestImages.andi(10, 6, 0xff));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 93));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        return code.array();
    }

    /// Creates CLI arguments for the dispatch stress program.
    private static String[] dispatchStressArguments(Path elfPath) {
        if (runningOnGraalVm()) {
            return new String[]{
                    "--debug-trace-compilation",
                    "--max-instructions", "2000000",
                    elfPath.toString()
            };
        }

        return new String[]{
                "--max-instructions", "2000000",
                elfPath.toString()
        };
    }

    /// Returns true when the current VM is a GraalVM distribution with engine compilation options.
    private static boolean runningOnGraalVm() {
        String vendor = System.getProperty("java.vm.vendor", "");
        String name = System.getProperty("java.vm.name", "");
        return vendor.contains("GraalVM") || name.contains("GraalVM");
    }

    /// Builds a program that exits with the `CLOCK_REALTIME` seconds value.
    private static byte[] clockRealtimeExitCode() {
        int timespecOffset = Integer.BYTES * 8;
        ByteBuffer code = ByteBuffer.allocate(timespecOffset + Long.BYTES * 2).order(ByteOrder.LITTLE_ENDIAN);
        ElfTestImages.putInt(code, ElfTestImages.auipc(11, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(11, 11, timespecOffset));
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 113));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        ElfTestImages.putInt(code, ElfTestImages.iType(0x03, 10, 3, 11, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 93));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        return code.array();
    }

    /// Builds a Hello World ELF with a page-aligned load segment below the entry point.
    private static byte[] pageAlignedHelloWorldElf() {
        int padding = 0x1000;
        byte[] program = helloWorldCode();
        ByteBuffer segment = ByteBuffer.allocate(padding + program.length).order(ByteOrder.LITTLE_ENDIAN);
        segment.position(padding);
        segment.put(program);
        return ElfTestImages.executable(
                ElfTestImages.BASE_ADDRESS - padding,
                ElfTestImages.BASE_ADDRESS,
                segment.array());
    }

    /// Builds a freestanding program that reads one byte from stdin and writes it to stdout.
    private static byte[] echoOneByteCode() {
        int bufferOffset = Integer.BYTES * 13;
        ByteBuffer code = ByteBuffer.allocate(bufferOffset + 1).order(ByteOrder.LITTLE_ENDIAN);
        ElfTestImages.putInt(code, ElfTestImages.auipc(11, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(11, 11, bufferOffset));
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(12, 0, 1));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 63));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 1));
        ElfTestImages.putInt(code, ElfTestImages.addi(12, 0, 1));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 64));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 93));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        code.put((byte) 0);
        return code.array();
    }

    /// Builds a program that invokes an unsupported Linux syscall with recognizable arguments.
    private static byte[] unsupportedSyscallCode() {
        return rawCode(
                ElfTestImages.addi(10, 0, 1),
                ElfTestImages.addi(11, 0, 2),
                ElfTestImages.addi(12, 0, 3),
                ElfTestImages.addi(13, 0, 4),
                ElfTestImages.addi(14, 0, 5),
                ElfTestImages.addi(15, 0, 6),
                ElfTestImages.addi(16, 0, 7),
                ElfTestImages.addi(17, 0, 2047),
                ElfTestImages.ecall());
    }

    /// Encodes `lui`.
    private static int lui(int rd, int immediate) {
        return (immediate & 0xffff_f000) | (rd << 7) | 0x37;
    }

    /// Encodes `bne`.
    private static int bne(int rs1, int rs2, int offset) {
        return branch(1, rs1, rs2, offset);
    }

    /// Encodes a B-type branch.
    private static int branch(int funct3, int rs1, int rs2, int offset) {
        return (((offset >>> 12) & 0x1) << 31)
                | (((offset >>> 5) & 0x3f) << 25)
                | (rs2 << 20)
                | (rs1 << 15)
                | (funct3 << 12)
                | (((offset >>> 1) & 0xf) << 8)
                | (((offset >>> 11) & 0x1) << 7)
                | 0x63;
    }

    /// Encodes `jal`.
    private static int jal(int rd, int offset) {
        return (((offset >>> 20) & 0x1) << 31)
                | (((offset >>> 1) & 0x3ff) << 21)
                | (((offset >>> 11) & 0x1) << 20)
                | (((offset >>> 12) & 0xff) << 12)
                | (rd << 7)
                | 0x6f;
    }

    /// Encodes the supplied 32-bit instructions as raw little-endian code bytes.
    private static byte[] rawCode(int... instructions) {
        ByteBuffer code = ByteBuffer.allocate(instructions.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int instruction : instructions) {
            ElfTestImages.putInt(code, instruction);
        }
        return code.array();
    }
}
