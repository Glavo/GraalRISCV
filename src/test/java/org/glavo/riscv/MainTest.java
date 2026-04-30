// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.exception.*;
import org.glavo.riscv.memory.*;
import org.glavo.riscv.parser.*;
import org.glavo.riscv.runtime.*;
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
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests the command-line entry point.
@NotNullByDefault
public final class MainTest {
    /// Linux `CLONE_VM`.
    private static final int CLONE_VM = 0x00000100;

    /// Linux `CLONE_FS`.
    private static final int CLONE_FS = 0x00000200;

    /// Linux `CLONE_FILES`.
    private static final int CLONE_FILES = 0x00000400;

    /// Linux `CLONE_SIGHAND`.
    private static final int CLONE_SIGHAND = 0x00000800;

    /// Linux `CLONE_THREAD`.
    private static final int CLONE_THREAD = 0x00010000;

    /// Linux `CLONE_SETTLS`.
    private static final int CLONE_SETTLS = 0x00080000;

    /// Linux `CLONE_PARENT_SETTID`.
    private static final int CLONE_PARENT_SETTID = 0x00100000;

    /// Linux `CLONE_CHILD_CLEARTID`.
    private static final int CLONE_CHILD_CLEARTID = 0x00200000;

    /// Linux `CLONE_CHILD_SETTID`.
    private static final int CLONE_CHILD_SETTID = 0x01000000;

    /// Clone flags used by Linux runtimes for shared-address-space threads.
    private static final int THREAD_CLONE_FLAGS = CLONE_VM
            | CLONE_FS
            | CLONE_FILES
            | CLONE_SIGHAND
            | CLONE_THREAD
            | CLONE_SETTLS
            | CLONE_PARENT_SETTID
            | CLONE_CHILD_CLEARTID
            | CLONE_CHILD_SETTID;

    /// Linux `FUTEX_WAIT | FUTEX_PRIVATE_FLAG`.
    private static final int FUTEX_WAIT_PRIVATE = 128;

    /// Linux `FUTEX_WAKE | FUTEX_PRIVATE_FLAG`.
    private static final int FUTEX_WAKE_PRIVATE = 129;

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

    /// Verifies that the default sparse memory window can run a page-aligned low ELF segment.
    @Test
    public void runsPageAlignedLowSegmentWithDefaultMemoryWindow() throws Exception {
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
                        "--memory-base", "auto",
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

    /// Verifies that performance diagnostics are printed only when explicitly requested.
    @Test
    public void writesPerformanceCountersToErrorStream() throws Exception {
        Path elfPath = tempDirectory.resolve("performance.elf");
        Files.write(elfPath, ElfTestImages.executable(helloWorldCode()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{"--debug-performance", elfPath.toString()},
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        String diagnostics = err.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertEquals("Hello World!\n", out.toString(StandardCharsets.UTF_8));
        assertTrue(diagnostics.contains("RISC-V performance counters:"));
        assertTrue(diagnostics.contains("memory.localPageCache.hits="));
        assertTrue(diagnostics.contains("traces.cache.hits="));
        assertTrue(diagnostics.contains("micro.genericOperations="));
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

    /// Verifies that programs with more loop blocks than the former inline dispatch cache keep making progress.
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void runsProgramsWithManyLoopBlocks() throws Exception {
        Path elfPath = tempDirectory.resolve("many-block-loop.elf");
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

    /// Verifies that the CLI can execute a thread-style Linux `clone` program.
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void runsThreadStyleCloneProgram() throws Exception {
        Path elfPath = tempDirectory.resolve("clone-thread.elf");
        Files.write(elfPath, ElfTestImages.executable(cloneThreadCode()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{"--max-instructions", "1000000", elfPath.toString()},
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        assertEquals(7, exitCode);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    /// Verifies that guest futex waits with relative timeouts report `ETIMEDOUT`.
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void runsFutexTimeoutProgram() throws Exception {
        Path elfPath = tempDirectory.resolve("futex-timeout.elf");
        Files.write(elfPath, ElfTestImages.executable(futexTimeoutCode()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{"--max-instructions", "10000", elfPath.toString()},
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        assertEquals(7, exitCode);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    /// Verifies that a child thread `exit_group` terminates the whole guest process.
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void cloneThreadExitGroupTerminatesProcess() throws Exception {
        Path elfPath = tempDirectory.resolve("clone-exit-group.elf");
        Files.write(elfPath, ElfTestImages.executable(cloneExitGroupCode()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{"--max-instructions", "1000000", elfPath.toString()},
                new ByteArrayInputStream(new byte[0]),
                out,
                err);

        assertEquals(29, exitCode);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
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

    /// Builds a program that starts one guest thread and validates futex wakeups and clear-child-TID completion.
    private static byte[] cloneThreadCode() {
        int resultOffset = 0x300;
        int parentTidOffset = 0x304;
        int childTidOffset = 0x308;
        int tlsOffset = 0x310;
        int readyOffset = 0x314;
        int releaseOffset = 0x318;
        int readyArmOffset = 0x31c;
        int childStackOffset = 0x7f0;
        ByteBuffer code = ByteBuffer.allocate(0x800).order(ByteOrder.LITTLE_ENDIAN);

        putLoadImmediate(code, 10, THREAD_CLONE_FLAGS);
        putLoadAddress(code, 11, childStackOffset);
        putLoadAddress(code, 12, parentTidOffset);
        putLoadAddress(code, 13, tlsOffset);
        putLoadAddress(code, 14, childTidOffset);
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 220));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        int childBranchPosition = reserveInstruction(code);

        ElfTestImages.putInt(code, ElfTestImages.addi(7, 10, 0));
        putLoadAddress(code, 5, parentTidOffset);
        ElfTestImages.putInt(code, lw(6, 0, 5));
        int parentTidFailBranchPosition = reserveInstruction(code);
        putLoadAddress(code, 5, childTidOffset);
        ElfTestImages.putInt(code, lw(6, 0, 5));
        int parentChildTidFailBranchPosition = reserveInstruction(code);
        putLoadAddress(code, 5, readyOffset);
        ElfTestImages.putInt(code, lw(6, 0, 5));
        int readyRaceBranchPosition = reserveInstruction(code);
        putLoadAddress(code, 5, readyArmOffset);
        ElfTestImages.putInt(code, ElfTestImages.addi(6, 0, 1));
        ElfTestImages.putInt(code, sw(6, 0, 5));
        putLoadAddress(code, 5, readyOffset);
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 5, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(11, 0, FUTEX_WAIT_PRIVATE));
        ElfTestImages.putInt(code, ElfTestImages.addi(12, 0, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(13, 0, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 98));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        int readyWaitFailBranchPosition = reserveInstruction(code);
        ElfTestImages.putInt(code, lw(6, 0, 5));
        int readyValueFailBranchPosition = reserveInstruction(code);
        putLoadAddress(code, 5, releaseOffset);
        ElfTestImages.putInt(code, ElfTestImages.addi(6, 0, 1));
        ElfTestImages.putInt(code, sw(6, 0, 5));
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 5, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(11, 0, FUTEX_WAKE_PRIVATE));
        ElfTestImages.putInt(code, ElfTestImages.addi(12, 0, 1));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 98));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        putLoadAddress(code, 5, childTidOffset);
        int waitLoopPosition = code.position();
        ElfTestImages.putInt(code, lw(6, 0, 5));
        int childTidRaceBranchPosition = reserveInstruction(code);
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 5, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(11, 0, FUTEX_WAIT_PRIVATE));
        ElfTestImages.putInt(code, ElfTestImages.addi(12, 6, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(13, 0, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 98));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        int childTidWaitFailBranchPosition = reserveInstruction(code);
        ElfTestImages.putInt(code, lw(6, 0, 5));
        int childTidValueFailBranchPosition = reserveInstruction(code);

        int waitDonePosition = code.position();
        putLoadAddress(code, 5, resultOffset);
        ElfTestImages.putInt(code, lw(10, 0, 5));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 93));
        ElfTestImages.putInt(code, ElfTestImages.ecall());

        int parentFailPosition = code.position();
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 21));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 93));
        ElfTestImages.putInt(code, ElfTestImages.ecall());

        int childPosition = code.position();
        putLoadAddress(code, 5, readyArmOffset);
        int readyArmLoopPosition = code.position();
        ElfTestImages.putInt(code, lw(6, 0, 5));
        ElfTestImages.putInt(code, beq(6, 0, readyArmLoopPosition - (readyArmLoopPosition + Integer.BYTES)));
        putCountdownLoop(code, 7, 50_000);
        putLoadAddress(code, 5, tlsOffset);
        int childTlsFailBranchPosition = reserveInstruction(code);
        putLoadAddress(code, 5, childTidOffset);
        ElfTestImages.putInt(code, lw(6, 0, 5));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 178));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        int childTidFailBranchPosition = reserveInstruction(code);
        putLoadAddress(code, 5, readyOffset);
        ElfTestImages.putInt(code, ElfTestImages.addi(6, 0, 1));
        ElfTestImages.putInt(code, sw(6, 0, 5));
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 5, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(11, 0, FUTEX_WAKE_PRIVATE));
        ElfTestImages.putInt(code, ElfTestImages.addi(12, 0, 1));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 98));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        putLoadAddress(code, 5, releaseOffset);
        int releaseLoopPosition = code.position();
        ElfTestImages.putInt(code, lw(6, 0, 5));
        int releaseDoneBranchPosition = reserveInstruction(code);
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 5, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(11, 0, FUTEX_WAIT_PRIVATE));
        ElfTestImages.putInt(code, ElfTestImages.addi(12, 0, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(13, 0, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 98));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        int releaseLoopJumpPosition = code.position();
        ElfTestImages.putInt(code, jal(0, releaseLoopPosition - releaseLoopJumpPosition));
        int releaseDonePosition = code.position();
        putCountdownLoop(code, 7, 50_000);
        putLoadAddress(code, 5, resultOffset);
        ElfTestImages.putInt(code, ElfTestImages.addi(6, 0, 7));
        ElfTestImages.putInt(code, sw(6, 0, 5));
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 93));
        ElfTestImages.putInt(code, ElfTestImages.ecall());

        int childFailPosition = code.position();
        putLoadAddress(code, 5, resultOffset);
        ElfTestImages.putInt(code, ElfTestImages.addi(6, 0, 13));
        ElfTestImages.putInt(code, sw(6, 0, 5));
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 13));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 94));
        ElfTestImages.putInt(code, ElfTestImages.ecall());

        patchInstruction(code, childBranchPosition, beq(10, 0, childPosition - childBranchPosition));
        patchInstruction(code, parentTidFailBranchPosition, bne(6, 7, parentFailPosition - parentTidFailBranchPosition));
        patchInstruction(code, parentChildTidFailBranchPosition, bne(6, 7, parentFailPosition - parentChildTidFailBranchPosition));
        patchInstruction(code, readyRaceBranchPosition, bne(6, 0, parentFailPosition - readyRaceBranchPosition));
        patchInstruction(code, readyWaitFailBranchPosition, bne(10, 0, parentFailPosition - readyWaitFailBranchPosition));
        patchInstruction(code, readyValueFailBranchPosition, beq(6, 0, parentFailPosition - readyValueFailBranchPosition));
        patchInstruction(code, childTidRaceBranchPosition, beq(6, 0, parentFailPosition - childTidRaceBranchPosition));
        patchInstruction(code, childTidWaitFailBranchPosition, bne(10, 0, parentFailPosition - childTidWaitFailBranchPosition));
        patchInstruction(code, childTidValueFailBranchPosition, bne(6, 0, parentFailPosition - childTidValueFailBranchPosition));
        patchInstruction(code, childTlsFailBranchPosition, bne(4, 5, childFailPosition - childTlsFailBranchPosition));
        patchInstruction(code, childTidFailBranchPosition, bne(10, 6, childFailPosition - childTidFailBranchPosition));
        patchInstruction(code, releaseDoneBranchPosition, bne(6, 0, releaseDonePosition - releaseDoneBranchPosition));

        code.position(resultOffset);
        code.putInt(99);
        code.putInt(0);
        code.putInt(0);
        code.putInt(0);
        code.putInt(0);
        code.putInt(0);
        return Arrays.copyOf(code.array(), childStackOffset);
    }

    /// Builds a program that expects `futex(FUTEX_WAIT, timeout)` to return `-ETIMEDOUT`.
    private static byte[] futexTimeoutCode() {
        int timespecOffset = 0x80;
        int futexOffset = 0x90;
        ByteBuffer code = ByteBuffer.allocate(0xa0).order(ByteOrder.LITTLE_ENDIAN);

        putLoadAddress(code, 5, futexOffset);
        ElfTestImages.putInt(code, ElfTestImages.addi(6, 0, 7));
        ElfTestImages.putInt(code, sw(6, 0, 5));
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 5, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(11, 0, FUTEX_WAIT_PRIVATE));
        ElfTestImages.putInt(code, ElfTestImages.addi(12, 0, 7));
        putLoadAddress(code, 13, timespecOffset);
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 98));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        ElfTestImages.putInt(code, ElfTestImages.addi(5, 0, -110));
        int failBranchPosition = reserveInstruction(code);
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 7));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 93));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        int failPosition = code.position();
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 21));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 93));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        patchInstruction(code, failBranchPosition, bne(10, 5, failPosition - failBranchPosition));

        code.position(timespecOffset);
        code.putLong(0);
        code.putLong(1);
        code.position(futexOffset);
        code.putInt(0);
        return code.array();
    }

    /// Builds a program where a child thread calls `exit_group` while the parent is in a futex wait.
    private static byte[] cloneExitGroupCode() {
        int parentTidOffset = 0x200;
        int childTidOffset = 0x204;
        int tlsOffset = 0x210;
        int waitWordOffset = 0x214;
        int armOffset = 0x218;
        int childStackOffset = 0x5f0;
        ByteBuffer code = ByteBuffer.allocate(0x600).order(ByteOrder.LITTLE_ENDIAN);

        putLoadImmediate(code, 10, THREAD_CLONE_FLAGS);
        putLoadAddress(code, 11, childStackOffset);
        putLoadAddress(code, 12, parentTidOffset);
        putLoadAddress(code, 13, tlsOffset);
        putLoadAddress(code, 14, childTidOffset);
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 220));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        int childBranchPosition = reserveInstruction(code);

        putLoadAddress(code, 5, armOffset);
        ElfTestImages.putInt(code, ElfTestImages.addi(6, 0, 1));
        ElfTestImages.putInt(code, sw(6, 0, 5));
        putLoadAddress(code, 5, waitWordOffset);
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 5, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(11, 0, FUTEX_WAIT_PRIVATE));
        ElfTestImages.putInt(code, ElfTestImages.addi(12, 0, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(13, 0, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 98));
        ElfTestImages.putInt(code, ElfTestImages.ecall());
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 21));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 93));
        ElfTestImages.putInt(code, ElfTestImages.ecall());

        int childPosition = code.position();
        putLoadAddress(code, 5, armOffset);
        int armLoopPosition = code.position();
        ElfTestImages.putInt(code, lw(6, 0, 5));
        ElfTestImages.putInt(code, beq(6, 0, armLoopPosition - (armLoopPosition + Integer.BYTES)));
        putCountdownLoop(code, 7, 50_000);
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 29));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 94));
        ElfTestImages.putInt(code, ElfTestImages.ecall());

        patchInstruction(code, childBranchPosition, beq(10, 0, childPosition - childBranchPosition));
        code.position(parentTidOffset);
        code.putInt(0);
        code.putInt(0);
        code.position(tlsOffset);
        code.putInt(0);
        code.putInt(0);
        code.putInt(0);
        return Arrays.copyOf(code.array(), childStackOffset);
    }

    /// Creates CLI arguments for the many-block loop stress program.
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

    /// Encodes `beq`.
    private static int beq(int rs1, int rs2, int offset) {
        return branch(0, rs1, rs2, offset);
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

    /// Encodes `lw`.
    private static int lw(int rd, int offset, int rs1) {
        return ElfTestImages.iType(0x03, rd, 2, rs1, offset);
    }

    /// Encodes `sw`.
    private static int sw(int rs2, int offset, int rs1) {
        return (((offset >>> 5) & 0x7f) << 25)
                | (rs2 << 20)
                | (rs1 << 15)
                | (2 << 12)
                | ((offset & 0x1f) << 7)
                | 0x23;
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

    /// Writes instructions that load a small PC-relative address into a register.
    private static void putLoadAddress(ByteBuffer code, int register, int targetOffset) {
        int instructionOffset = code.position();
        ElfTestImages.putInt(code, ElfTestImages.auipc(register, 0));
        ElfTestImages.putInt(code, ElfTestImages.addi(register, register, targetOffset - instructionOffset));
    }

    /// Writes instructions that load a 32-bit immediate into a register.
    private static void putLoadImmediate(ByteBuffer code, int register, int value) {
        int high = (value + 0x800) & 0xffff_f000;
        if (high == 0) {
            ElfTestImages.putInt(code, ElfTestImages.addi(register, 0, value));
            return;
        }

        ElfTestImages.putInt(code, lui(register, high));
        ElfTestImages.putInt(code, ElfTestImages.addi(register, register, value - high));
    }

    /// Writes a simple countdown loop using the supplied register as the counter.
    private static void putCountdownLoop(ByteBuffer code, int register, int count) {
        putLoadImmediate(code, register, count);
        int loopPosition = code.position();
        ElfTestImages.putInt(code, ElfTestImages.addi(register, register, -1));
        ElfTestImages.putInt(code, bne(register, 0, loopPosition - code.position()));
    }

    /// Reserves one 32-bit instruction slot and returns its byte position.
    private static int reserveInstruction(ByteBuffer code) {
        int position = code.position();
        ElfTestImages.putInt(code, 0);
        return position;
    }

    /// Replaces a previously reserved instruction slot.
    private static void patchInstruction(ByteBuffer code, int position, int instruction) {
        code.putInt(position, instruction);
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
