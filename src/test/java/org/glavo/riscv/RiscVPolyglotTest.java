// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.exception.*;
import org.glavo.riscv.memory.*;
import org.glavo.riscv.parser.*;
import org.glavo.riscv.runtime.*;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests end-to-end execution through the GraalVM Polyglot API.
@NotNullByDefault
public final class RiscVPolyglotTest {
    /// Verifies that an RV64I program can exit through `ecall`.
    @Test
    public void executesEcallExitProgram() throws Exception {
        byte[] elf = ElfTestImages.executable(
                ElfTestImages.addi(10, 0, 42),
                ElfTestImages.addi(17, 0, 93),
                ElfTestImages.ecall());

        assertEquals(42, evaluate(elf));
    }

    /// Verifies that the M extension is executed.
    @Test
    public void executesMultiplyProgram() throws Exception {
        byte[] elf = ElfTestImages.executable(
                ElfTestImages.addi(5, 0, 6),
                ElfTestImages.addi(6, 0, 7),
                ElfTestImages.mul(10, 5, 6),
                ElfTestImages.addi(17, 0, 93),
                ElfTestImages.ecall());

        assertEquals(42, evaluate(elf));
    }

    /// Verifies that a guest program can query and grow the program break.
    @Test
    public void executesBrkProgram() throws Exception {
        byte[] elf = ElfTestImages.executable(
                ElfTestImages.addi(10, 0, 0),
                ElfTestImages.addi(17, 0, 214),
                ElfTestImages.ecall(),
                ElfTestImages.addi(10, 10, 16),
                ElfTestImages.addi(17, 0, 214),
                ElfTestImages.ecall(),
                ElfTestImages.andi(10, 10, 0xff),
                ElfTestImages.addi(17, 0, 93),
                ElfTestImages.ecall());

        assertEquals(64, evaluate(elf));
    }

    /// Verifies that a 32-bit instruction can be fetched from a 16-bit-aligned address after a compressed instruction.
    @Test
    public void executesCompressedThenUnalignedWideInstruction() throws Exception {
        ByteBuffer code = ByteBuffer.allocate(Short.BYTES + Integer.BYTES * 3).order(ByteOrder.LITTLE_ENDIAN);
        ElfTestImages.putShort(code, ElfTestImages.compressedNop());
        ElfTestImages.putInt(code, ElfTestImages.addi(10, 0, 7));
        ElfTestImages.putInt(code, ElfTestImages.addi(17, 0, 93));
        ElfTestImages.putInt(code, ElfTestImages.ecall());

        assertEquals(7, evaluate(ElfTestImages.executable(code.array())));
    }

    /// Verifies that a guest program can write `Hello World!` to stdout.
    @Test
    public void printsHelloWorld() throws Exception {
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

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(0, evaluate(ElfTestImages.executable(code.array()), out));
        assertEquals("Hello World!\n", out.toString(StandardCharsets.UTF_8));
    }

    /// Evaluates a RISC-V ELF through the registered Truffle language.
    private static long evaluate(byte[] elf) throws Exception {
        return evaluate(elf, new ByteArrayOutputStream());
    }

    /// Evaluates a RISC-V ELF with a captured stdout stream.
    private static long evaluate(byte[] elf, ByteArrayOutputStream out) throws Exception {
        try (Context context = Context.newBuilder(RiscVLanguage.ID)
                .option("riscv.maxInstructions", "1000")
                .out(out)
                .build()) {
            Source source = Source.newBuilder(
                            RiscVLanguage.ID,
                            ByteSequence.create(elf),
                            "program.elf")
                    .mimeType(RiscVLanguage.ELF_MIME_TYPE)
                    .build();
            Value value = context.eval(source);
            return value.asLong();
        }
    }
}
