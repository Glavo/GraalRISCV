// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.memory;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Stores one committed guest page with replaceable backing.
@NotNullByDefault
final class MemoryPage {
    /// The Unsafe base object, or null when baseOffset is an absolute native address.
    private final @Nullable Object baseObject;

    /// The Unsafe byte offset of the first byte in the page.
    private final long baseOffset;

    /// An object retained for the lifetime of the page backing.
    private final @Nullable Object owner;

    /// The optional release action for non-GC-managed backing.
    private final @Nullable Runnable closeAction;

    /// Creates a committed guest page with the supplied Unsafe access coordinates.
    private MemoryPage(
            @Nullable Object baseObject,
            long baseOffset,
            @Nullable Object owner,
            @Nullable Runnable closeAction) {
        this.baseObject = baseObject;
        this.baseOffset = baseOffset;
        this.owner = owner;
        this.closeAction = closeAction;
    }

    /// Creates a committed guest page backed by a zero-filled heap long array.
    static MemoryPage heap(int pageWords) {
        long[] data = new long[pageWords];
        return new MemoryPage(data, MemoryUnsafe.HEAP_LONG_ARRAY_BASE_OFFSET, data, null);
    }

    /// Returns the Unsafe base object, or null for absolute native-address backing.
    @Nullable Object baseObject() {
        return baseObject;
    }

    /// Returns the Unsafe byte offset of the first byte in this page.
    long baseOffset() {
        return baseOffset;
    }

    /// Returns the Unsafe byte offset for an access at the supplied page-relative byte offset.
    long byteOffset(long pageOffset) {
        return baseOffset + pageOffset;
    }

    /// Releases resources owned by this page backing.
    void close() {
        @Nullable Runnable action = this.closeAction;
        if (action != null) {
            action.run();
        }
    }
}
