// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.RiscVException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/// Stores the pixel backing for one guest-visible linear framebuffer.
///
/// The device owns only display memory state. Guest syscall integration and host
/// window presentation are layered above this class.
@NotNullByDefault
public final class FramebufferDevice {
    /// The framebuffer geometry.
    private final FramebufferGeometry geometry;

    /// The mutable pixel backing bytes.
    private final byte[] pixels;

    /// Incremented after each write, explicit clear, or explicit dirty-region mark.
    private long modificationCounter;

    /// The accumulated dirty region since the last dirty-region reset, or null when no region is pending.
    private @Nullable FramebufferDirtyRegion dirtyRegion;

    /// Creates a framebuffer device with zero-filled pixel backing.
    public FramebufferDevice(FramebufferGeometry geometry) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.pixels = new byte[geometry.bufferSizeBytes()];
    }

    /// Returns the framebuffer geometry.
    public FramebufferGeometry geometry() {
        return geometry;
    }

    /// Returns the number of writes and explicit clears applied to this framebuffer.
    public synchronized long modificationCounter() {
        return modificationCounter;
    }

    /// Returns the current accumulated dirty region without clearing it.
    public synchronized @Nullable FramebufferDirtyRegion dirtyRegion() {
        return dirtyRegion;
    }

    /// Returns and clears the current accumulated dirty region.
    public synchronized @Nullable FramebufferDirtyRegion takeDirtyRegion() {
        @Nullable FramebufferDirtyRegion result = dirtyRegion;
        dirtyRegion = null;
        return result;
    }

    /// Returns a copy of the current framebuffer pixels and dirty state.
    public synchronized FramebufferSnapshot snapshot() {
        return new FramebufferSnapshot(geometry, pixels, modificationCounter, dirtyRegion);
    }

    /// Copies bytes from the framebuffer into a host buffer.
    public synchronized int read(long offset, byte[] destination, int destinationOffset, int length) {
        checkArrayRange(destination, destinationOffset, length);
        int count = transferLength(offset, length);
        if (count == 0) {
            return 0;
        }
        System.arraycopy(pixels, (int) offset, destination, destinationOffset, count);
        return count;
    }

    /// Copies bytes from a host buffer into the framebuffer and marks affected rows dirty.
    public synchronized int write(long offset, byte[] source, int sourceOffset, int length) {
        checkArrayRange(source, sourceOffset, length);
        int count = transferLength(offset, length);
        if (count == 0) {
            return 0;
        }
        System.arraycopy(source, sourceOffset, pixels, (int) offset, count);
        modificationCounter++;
        markDirtyByteRange((int) offset, count);
        return count;
    }

    /// Clears all framebuffer bytes and marks the full visible area dirty.
    public synchronized void clear() {
        Arrays.fill(pixels, (byte) 0);
        modificationCounter++;
        dirtyRegion = new FramebufferDirtyRegion(0, 0, geometry.width(), geometry.height());
    }

    /// Marks a visible framebuffer region dirty.
    public synchronized void markDirty(FramebufferDirtyRegion region) {
        Objects.requireNonNull(region, "region");
        if ((long) region.x() + region.width() > geometry.width()
                || (long) region.y() + region.height() > geometry.height()) {
            throw new RiscVException("Framebuffer dirty region is outside the visible area");
        }
        modificationCounter++;
        mergeDirtyRegion(region);
    }

    /// Returns the bounded transfer length for a framebuffer offset and requested length.
    private int transferLength(long offset, int length) {
        if (offset < 0) {
            throw new RiscVException("Framebuffer offset must be non-negative: " + offset);
        }
        if (length < 0) {
            throw new RiscVException("Framebuffer transfer length must be non-negative: " + length);
        }
        if (offset >= pixels.length || length == 0) {
            return 0;
        }
        return (int) Math.min(length, pixels.length - offset);
    }

    /// Marks all visible pixels on the rows affected by a backing byte range.
    private void markDirtyByteRange(int offset, int length) {
        int startRow = offset / geometry.strideBytes();
        int endRow = (offset + length - 1) / geometry.strideBytes();
        if (startRow >= geometry.height()) {
            return;
        }
        if (endRow >= geometry.height()) {
            endRow = geometry.height() - 1;
        }
        mergeDirtyRegion(new FramebufferDirtyRegion(
                0,
                startRow,
                geometry.width(),
                endRow - startRow + 1));
    }

    /// Merges one region into the accumulated dirty region.
    private void mergeDirtyRegion(FramebufferDirtyRegion region) {
        dirtyRegion = dirtyRegion == null ? region : dirtyRegion.merge(region);
    }

    /// Checks that a host byte-array range can be read or written.
    private static void checkArrayRange(byte[] array, int offset, int length) {
        Objects.requireNonNull(array, "array");
        if (offset < 0 || length < 0 || offset > array.length || length > array.length - offset) {
            throw new IndexOutOfBoundsException("Invalid framebuffer host buffer range");
        }
    }
}
