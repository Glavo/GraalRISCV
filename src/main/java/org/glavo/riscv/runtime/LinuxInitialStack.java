// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.parser.ElfImage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/// Builds a deterministic Linux-style initial user stack for static user-mode programs.
@NotNullByDefault
public final class LinuxInitialStack {
    /// Linux auxv terminator type.
    private static final long AT_NULL = 0;

    /// Linux auxv platform string pointer type.
    private static final long AT_PLATFORM = 15;

    /// Linux auxv clock tick frequency type.
    private static final long AT_CLKTCK = 17;

    /// Linux auxv secure-execution marker type.
    private static final long AT_SECURE = 23;

    /// Linux auxv random bytes pointer type.
    private static final long AT_RANDOM = 25;

    /// Linux auxv page-size type.
    private static final long AT_PAGESZ = 6;

    /// Linux auxv program header table address type.
    private static final long AT_PHDR = 3;

    /// Linux auxv dynamic interpreter load base type.
    private static final long AT_BASE = 7;

    /// Linux auxv program header entry size type.
    private static final long AT_PHENT = 4;

    /// Linux auxv program header count type.
    private static final long AT_PHNUM = 5;

    /// Linux auxv entry-point type.
    private static final long AT_ENTRY = 9;

    /// Linux auxv executable filename pointer type.
    private static final long AT_EXECFN = 31;

    /// Linux auxv real user id type.
    private static final long AT_UID = 11;

    /// Linux auxv effective user id type.
    private static final long AT_EUID = 12;

    /// Linux auxv real group id type.
    private static final long AT_GID = 13;

    /// Linux auxv effective group id type.
    private static final long AT_EGID = 14;

    /// The deterministic Linux clock tick frequency exposed through auxv.
    private static final long CLOCK_TICKS_PER_SECOND = 100;

    /// The default environment passed to guest programs.
    private static final String @Unmodifiable [] DEFAULT_ENVIRONMENT = {
            "LANG=C",
            "PATH=/usr/bin:/bin",
            "PWD=/"
    };

    /// Deterministic bytes exposed through `AT_RANDOM`.
    private static final byte @Unmodifiable [] RANDOM_BYTES = {
            0x47, 0x52, 0x49, 0x53, 0x43, 0x56, 0x2d, 0x30,
            0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38
    };

    /// The Linux platform string for RV64.
    private static final String PLATFORM = "riscv64";

    /// Prevents construction of this utility class.
    private LinuxInitialStack() {
    }

    /// Writes the initial stack and returns the aligned guest stack pointer.
    public static long initialize(Memory memory, long stackTop, String[] arguments, ElfImage image) {
        return initialize(memory, stackTop, arguments, image, Memory.DEFAULT_PAGE_SIZE);
    }

    /// Writes the initial stack with an explicit guest page size and returns the aligned guest stack pointer.
    public static long initialize(Memory memory, long stackTop, String[] arguments, ElfImage image, long pageSize) {
        return initialize(memory, stackTop, arguments, image, 0, ElfImage.ABSENT_ADDRESS, null, pageSize);
    }

    /// Writes the initial stack with explicit load-bias metadata and returns the aligned guest stack pointer.
    public static long initialize(
            Memory memory,
            long stackTop,
            String[] arguments,
            ElfImage image,
            long loadBias,
            long interpreterBase,
            @Nullable String executablePath,
            long pageSize) {
        long cursor = stackTop & ~0xfL;
        String[] argv = arguments.clone();
        long[] argumentPointers = new long[argv.length];
        for (int index = argv.length - 1; index >= 0; index--) {
            cursor = writeString(memory, cursor, argv[index]);
            argumentPointers[index] = cursor;
        }

        long[] environmentPointers = new long[DEFAULT_ENVIRONMENT.length];
        for (int index = DEFAULT_ENVIRONMENT.length - 1; index >= 0; index--) {
            cursor = writeString(memory, cursor, DEFAULT_ENVIRONMENT[index]);
            environmentPointers[index] = cursor;
        }

        cursor = writeBytes(memory, cursor, RANDOM_BYTES);
        long randomAddress = cursor;
        cursor = writeString(memory, cursor, PLATFORM);
        long platformAddress = cursor;
        String execfn = executablePath == null ? (argv.length == 0 ? "" : argv[0]) : executablePath;
        cursor = writeString(memory, cursor, execfn);
        long execfnAddress = cursor;

        ArrayList<Long> auxv = new ArrayList<>();
        if (image.programHeaderAddress() != ElfImage.ABSENT_ADDRESS) {
            addAuxiliaryVector(auxv, AT_PHDR, image.programHeaderAddress() + loadBias);
            addAuxiliaryVector(auxv, AT_PHENT, image.programHeaderEntrySize());
            addAuxiliaryVector(auxv, AT_PHNUM, image.programHeaderCount());
        }
        addAuxiliaryVector(auxv, AT_PAGESZ, pageSize);
        if (interpreterBase != ElfImage.ABSENT_ADDRESS) {
            addAuxiliaryVector(auxv, AT_BASE, interpreterBase);
        }
        addAuxiliaryVector(auxv, AT_ENTRY, image.entryPoint() + loadBias);
        addAuxiliaryVector(auxv, AT_UID, 0);
        addAuxiliaryVector(auxv, AT_EUID, 0);
        addAuxiliaryVector(auxv, AT_GID, 0);
        addAuxiliaryVector(auxv, AT_EGID, 0);
        addAuxiliaryVector(auxv, AT_CLKTCK, CLOCK_TICKS_PER_SECOND);
        addAuxiliaryVector(auxv, AT_SECURE, 0);
        addAuxiliaryVector(auxv, AT_RANDOM, randomAddress);
        addAuxiliaryVector(auxv, AT_PLATFORM, platformAddress);
        addAuxiliaryVector(auxv, AT_EXECFN, execfnAddress);

        long frameWords = 1L
                + argumentPointers.length + 1L
                + environmentPointers.length + 1L
                + auxv.size() + 2L;
        long stackPointer = alignDown(cursor - (frameWords * Long.BYTES), 16);
        long writeAddress = stackPointer;
        memory.writeLong(writeAddress, argumentPointers.length);
        writeAddress += Long.BYTES;
        for (long pointer : argumentPointers) {
            memory.writeLong(writeAddress, pointer);
            writeAddress += Long.BYTES;
        }
        memory.writeLong(writeAddress, 0);
        writeAddress += Long.BYTES;
        for (long pointer : environmentPointers) {
            memory.writeLong(writeAddress, pointer);
            writeAddress += Long.BYTES;
        }
        memory.writeLong(writeAddress, 0);
        writeAddress += Long.BYTES;
        for (long value : auxv) {
            memory.writeLong(writeAddress, value);
            writeAddress += Long.BYTES;
        }
        memory.writeLong(writeAddress, AT_NULL);
        memory.writeLong(writeAddress + Long.BYTES, 0);
        return stackPointer;
    }

    /// Adds one auxiliary vector key/value pair to the serialized list.
    private static void addAuxiliaryVector(ArrayList<Long> auxv, long type, long value) {
        auxv.add(type);
        auxv.add(value);
    }

    /// Writes a null-terminated UTF-8 string below the supplied cursor.
    private static long writeString(Memory memory, long cursor, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        long address = cursor - bytes.length - 1L;
        memory.writeBytes(address, bytes, 0, bytes.length);
        memory.writeByte(address + bytes.length, (byte) 0);
        return address;
    }

    /// Writes bytes below the supplied cursor.
    private static long writeBytes(Memory memory, long cursor, byte[] bytes) {
        long address = cursor - bytes.length;
        memory.writeBytes(address, bytes, 0, bytes.length);
        return address;
    }

    /// Rounds an address down to the requested power-of-two alignment.
    private static long alignDown(long address, long alignment) {
        return address & -alignment;
    }
}
