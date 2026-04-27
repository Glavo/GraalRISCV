package org.glavo.riscv;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

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

    /// Evaluates a RISC-V ELF through the registered Truffle language.
    private static long evaluate(byte[] elf) throws Exception {
        try (Context context = Context.newBuilder(RiscVLanguage.ID)
                .option("riscv.maxInstructions", "1000")
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
