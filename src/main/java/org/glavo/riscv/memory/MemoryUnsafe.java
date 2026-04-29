// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.memory;

import jdk.internal.misc.Unsafe;
import org.jetbrains.annotations.NotNullByDefault;

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
}
