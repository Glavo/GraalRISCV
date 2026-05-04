// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.runtime.FramebufferDevice;
import org.glavo.riscv.runtime.FramebufferDirtyRegion;
import org.glavo.riscv.runtime.FramebufferGeometry;
import org.glavo.riscv.runtime.FramebufferPixelFormat;
import org.glavo.riscv.runtime.FramebufferSnapshot;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests the host-side framebuffer abstractions used by future graphical device support.
@NotNullByDefault
public final class FramebufferDeviceTest {
    /// Verifies tightly packed geometry derives stride and backing size from the pixel format.
    @Test
    public void createsPackedGeometry() {
        FramebufferGeometry geometry = FramebufferGeometry.packed(2, 3, FramebufferPixelFormat.XRGB8888);

        assertEquals(2, geometry.width());
        assertEquals(3, geometry.height());
        assertEquals(8, geometry.strideBytes());
        assertEquals(24, geometry.bufferSizeBytes());
        assertEquals(4, geometry.pixelFormat().bytesPerPixel());
    }

    /// Verifies framebuffer writes are readable and mark affected scanlines dirty.
    @Test
    public void copiesFramebufferBytesAndTracksDirtyRows() {
        FramebufferGeometry geometry = FramebufferGeometry.packed(4, 3, FramebufferPixelFormat.XRGB8888);
        FramebufferDevice device = new FramebufferDevice(geometry);
        byte[] source = new byte[]{1, 2, 3, 4};

        assertEquals(4, device.write(geometry.strideBytes() + 4, source, 0, source.length));
        assertEquals(1, device.modificationCounter());
        assertEquals(new FramebufferDirtyRegion(0, 1, 4, 1), device.dirtyRegion());

        byte[] destination = new byte[4];
        assertEquals(4, device.read(geometry.strideBytes() + 4, destination, 0, destination.length));
        assertArrayEquals(source, destination);
    }

    /// Verifies framebuffer snapshots copy pixel data and expose the current dirty state.
    @Test
    public void snapshotsFramebufferState() {
        FramebufferGeometry geometry = FramebufferGeometry.packed(2, 2, FramebufferPixelFormat.RGB565);
        FramebufferDevice device = new FramebufferDevice(geometry);

        device.write(0, new byte[]{10, 20}, 0, 2);
        FramebufferSnapshot snapshot = device.snapshot();
        byte[] snapshotPixels = snapshot.pixels();
        snapshotPixels[0] = 99;

        byte[] destination = new byte[2];
        device.read(0, destination, 0, destination.length);
        assertArrayEquals(new byte[]{10, 20}, destination);
        assertEquals(new FramebufferDirtyRegion(0, 0, 2, 1), snapshot.dirtyRegion());
        assertEquals(1, snapshot.modificationCounter());
    }

    /// Verifies dirty regions accumulate and can be consumed.
    @Test
    public void coalescesAndConsumesDirtyRegions() {
        FramebufferGeometry geometry = FramebufferGeometry.packed(3, 3, FramebufferPixelFormat.XRGB8888);
        FramebufferDevice device = new FramebufferDevice(geometry);

        device.markDirty(new FramebufferDirtyRegion(1, 0, 1, 1));
        device.markDirty(new FramebufferDirtyRegion(0, 2, 2, 1));

        assertEquals(new FramebufferDirtyRegion(0, 0, 2, 3), device.dirtyRegion());
        assertEquals(2, device.modificationCounter());
        assertEquals(new FramebufferDirtyRegion(0, 0, 2, 3), device.takeDirtyRegion());
        assertNull(device.dirtyRegion());
    }

    /// Verifies invalid framebuffer layouts are rejected.
    @Test
    public void rejectsInvalidGeometryAndPixelFormats() {
        assertThrows(RiscVException.class, () ->
                FramebufferGeometry.packed(0, 1, FramebufferPixelFormat.XRGB8888));
        assertThrows(RiscVException.class, () ->
                new FramebufferGeometry(2, 2, 4, FramebufferPixelFormat.XRGB8888));
        assertThrows(RiscVException.class, () ->
                new FramebufferPixelFormat(24, 16, 8, 8, 8, 8, 8, 0, 0));
    }
}
