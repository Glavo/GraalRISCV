/*
 * Copyright 2026 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glavo;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/// Generates a tiny static RV64 ELF used by package smoke tests.
@DisableCachingByDefault(because = "The generated ELF is a local verification fixture.")
@NotNullByDefault
public abstract class RiscVSmokeElfTask extends DefaultTask {
    /// The guest virtual address where the generated program is loaded.
    private static final long BASE_ADDRESS = 0x8000_0000L;

    /// The file offset where generated program bytes start.
    private static final int PROGRAM_OFFSET = 0x1000;

    /// The byte offset of the ELF program header table.
    private static final int PROGRAM_HEADER_OFFSET = 64;

    /// The byte size of one ELF64 program header.
    private static final int PROGRAM_HEADER_SIZE = 56;

    /// The loadable segment flags used by the generated fixture.
    private static final int LOAD_FLAGS = 7;

    /// The loadable segment alignment used by the generated fixture.
    private static final long LOAD_ALIGNMENT = 0x1000L;

    /// The message printed by the generated fixture.
    private static final byte @Unmodifiable [] MESSAGE = "Smoke OK\n".getBytes(StandardCharsets.UTF_8);

    /// Creates the smoke ELF generation task.
    public RiscVSmokeElfTask() {
    }

    /// Returns the generated ELF output file.
    ///
    /// @return the generated ELF output file property
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /// Generates the smoke-test ELF file.
    @TaskAction
    public void generate() {
        Path outputFile = getOutputFile().get().getAsFile().toPath();
        Path parent = outputFile.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(outputFile, executable(program()));
        } catch (IOException exception) {
            throw new GradleException("Failed to write RISC-V smoke ELF: " + outputFile, exception);
        }
    }

    /// Creates a minimal ELF64 executable containing the supplied program bytes.
    ///
    /// @param program the guest program bytes
    /// @return the encoded ELF file bytes
    private static byte[] executable(byte[] program) {
        byte[] bytes = new byte[PROGRAM_OFFSET + program.length];
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
        buffer.putLong(PROGRAM_HEADER_OFFSET);
        buffer.putLong(0);
        buffer.putInt(0);
        buffer.putShort((short) 64);
        buffer.putShort((short) PROGRAM_HEADER_SIZE);
        buffer.putShort((short) 1);
        buffer.putShort((short) 64);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);

        buffer.position(PROGRAM_HEADER_OFFSET);
        buffer.putInt(1);
        buffer.putInt(LOAD_FLAGS);
        buffer.putLong(PROGRAM_OFFSET);
        buffer.putLong(BASE_ADDRESS);
        buffer.putLong(BASE_ADDRESS);
        buffer.putLong(program.length);
        buffer.putLong(program.length);
        buffer.putLong(LOAD_ALIGNMENT);

        buffer.position(PROGRAM_OFFSET);
        buffer.put(program);
        return bytes;
    }

    /// Creates the smoke-test guest program bytes.
    ///
    /// @return the encoded guest program
    private static byte[] program() {
        int messageOffset = Integer.BYTES * 9;
        ByteBuffer code = ByteBuffer.allocate(messageOffset + MESSAGE.length).order(ByteOrder.LITTLE_ENDIAN);
        code.putInt(auipc(11, 0));
        code.putInt(addi(11, 11, messageOffset));
        code.putInt(addi(10, 0, 1));
        code.putInt(addi(12, 0, MESSAGE.length));
        code.putInt(addi(17, 0, 64));
        code.putInt(ecall());
        code.putInt(addi(10, 0, 0));
        code.putInt(addi(17, 0, 93));
        code.putInt(ecall());
        code.put(MESSAGE);
        return code.array();
    }

    /// Encodes `addi`.
    ///
    /// @param rd the destination register
    /// @param rs1 the source register
    /// @param immediate the signed 12-bit immediate
    /// @return the encoded instruction
    private static int addi(int rd, int rs1, int immediate) {
        return ((immediate & 0xfff) << 20) | (rs1 << 15) | (rd << 7) | 0x13;
    }

    /// Encodes `auipc`.
    ///
    /// @param rd the destination register
    /// @param immediate the upper immediate
    /// @return the encoded instruction
    private static int auipc(int rd, int immediate) {
        return (immediate & 0xffff_f000) | (rd << 7) | 0x17;
    }

    /// Encodes `ecall`.
    ///
    /// @return the encoded instruction
    private static int ecall() {
        return 0x0000_0073;
    }
}
