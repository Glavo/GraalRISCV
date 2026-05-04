// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.gui;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.runtime.FramebufferGeometry;
import org.glavo.riscv.runtime.FramebufferPixelFormat;
import org.glavo.riscv.runtime.FramebufferSnapshot;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests conversion from guest framebuffer pixels to host ARGB pixels.
@NotNullByDefault
public final class FramebufferArgbConverterTest {
    /// Verifies little-endian XRGB8888 pixels are converted while row padding is skipped.
    @Test
    public void convertsXrgb8888WithStridePadding() {
        FramebufferGeometry geometry = new FramebufferGeometry(2, 2, 12, FramebufferPixelFormat.XRGB8888);
        byte[] pixels = new byte[]{
                0x33, 0x22, 0x11, 0,
                (byte) 0xcc, (byte) 0xbb, (byte) 0xaa, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                (byte) 0xff, (byte) 0xff, (byte) 0xff, 0,
                0, 0, 0, 0
        };
        int[] argb = new int[4];

        FramebufferArgbConverter.convert(new FramebufferSnapshot(geometry, pixels, 1, null), argb);

        assertArrayEquals(new int[]{
                0xff11_2233,
                0xffaa_bbcc,
                0xff00_0000,
                0xffff_ffff
        }, argb);
    }

    /// Verifies packed RGB565 channels are expanded to eight-bit host channels.
    @Test
    public void convertsRgb565Pixels() {
        assertEquals(0xffff_0000, FramebufferArgbConverter.convertPixel(0xf800, FramebufferPixelFormat.RGB565));
        assertEquals(0xff00_ff00, FramebufferArgbConverter.convertPixel(0x07e0, FramebufferPixelFormat.RGB565));
        assertEquals(0xff00_00ff, FramebufferArgbConverter.convertPixel(0x001f, FramebufferPixelFormat.RGB565));
    }

    /// Verifies ARGB8888 transparency bits are used as the host alpha channel.
    @Test
    public void convertsArgb8888AlphaChannel() {
        assertEquals(0x8011_2233, FramebufferArgbConverter.convertPixel(0x8011_2233L, FramebufferPixelFormat.ARGB8888));
    }

    /// Verifies conversion rejects an undersized destination buffer.
    @Test
    public void rejectsUndersizedDestination() {
        FramebufferGeometry geometry = FramebufferGeometry.packed(2, 2, FramebufferPixelFormat.XRGB8888);
        FramebufferSnapshot snapshot = new FramebufferSnapshot(
                geometry,
                new byte[geometry.bufferSizeBytes()],
                0,
                null);

        assertThrows(RiscVException.class, () -> FramebufferArgbConverter.convert(snapshot, new int[3]));
    }
}
