// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.memory;

import jdk.internal.misc.Unsafe;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteOrder;

/// Provides shared Unsafe access constants for the memory package.
@NotNullByDefault
final class MemoryUnsafe {
    /// The Unsafe instance used to access heap page backing without MemorySegment overhead.
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /// The byte offset of the first element in a Java long array used by the default heap page allocator.
    static final long HEAP_LONG_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(long[].class);

    /// The byte offset of the first element in a Java byte array used by host buffer copies.
    static final long HEAP_BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);

    /// Whether the host CPU uses little-endian primitive layout.
    static final boolean NATIVE_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    /// Prevents instantiation.
    private MemoryUnsafe() {
    }

    /// Reads a little-endian 16-bit value from an Unsafe coordinate.
    static short readShortLE(@Nullable Object baseObject, long byteOffset) {
        short value = UNSAFE.getShort(baseObject, byteOffset);
        return NATIVE_LITTLE_ENDIAN ? value : Short.reverseBytes(value);
    }

    /// Reads a little-endian 32-bit value from an Unsafe coordinate.
    static int readIntLE(@Nullable Object baseObject, long byteOffset) {
        int value = UNSAFE.getInt(baseObject, byteOffset);
        return NATIVE_LITTLE_ENDIAN ? value : Integer.reverseBytes(value);
    }

    /// Reads a little-endian 64-bit value from an Unsafe coordinate.
    static long readLongLE(@Nullable Object baseObject, long byteOffset) {
        long value = UNSAFE.getLong(baseObject, byteOffset);
        return NATIVE_LITTLE_ENDIAN ? value : Long.reverseBytes(value);
    }

    /// Writes a little-endian 16-bit value to an Unsafe coordinate.
    static void writeShortLE(@Nullable Object baseObject, long byteOffset, short value) {
        UNSAFE.putShort(baseObject, byteOffset, NATIVE_LITTLE_ENDIAN ? value : Short.reverseBytes(value));
    }

    /// Writes a little-endian 32-bit value to an Unsafe coordinate.
    static void writeIntLE(@Nullable Object baseObject, long byteOffset, int value) {
        UNSAFE.putInt(baseObject, byteOffset, NATIVE_LITTLE_ENDIAN ? value : Integer.reverseBytes(value));
    }

    /// Writes a little-endian 64-bit value to an Unsafe coordinate.
    static void writeLongLE(@Nullable Object baseObject, long byteOffset, long value) {
        UNSAFE.putLong(baseObject, byteOffset, NATIVE_LITTLE_ENDIAN ? value : Long.reverseBytes(value));
    }
}
