// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.gui;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.runtime.FramebufferGeometry;
import org.glavo.riscv.runtime.FramebufferPixelFormat;
import org.glavo.riscv.runtime.FramebufferSnapshot;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Converts guest framebuffer pixels into host ARGB pixels for Java desktop rendering.
@NotNullByDefault
public final class FramebufferArgbConverter {
    /// Prevents construction of this utility class.
    private FramebufferArgbConverter() {
    }

    /// Converts one framebuffer snapshot into packed `0xAARRGGBB` pixels.
    public static void convert(FramebufferSnapshot snapshot, int[] destination) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(destination, "destination");

        FramebufferGeometry geometry = snapshot.geometry();
        int visiblePixels = Math.multiplyExact(geometry.width(), geometry.height());
        if (destination.length < visiblePixels) {
            throw new RiscVException("ARGB destination is smaller than the visible framebuffer area");
        }

        byte[] source = snapshot.pixels();
        FramebufferPixelFormat format = geometry.pixelFormat();
        int bytesPerPixel = format.bytesPerPixel();
        int outputIndex = 0;
        for (int y = 0; y < geometry.height(); y++) {
            int inputOffset = y * geometry.strideBytes();
            for (int x = 0; x < geometry.width(); x++) {
                long pixel = readLittleEndianPixel(source, inputOffset, bytesPerPixel);
                destination[outputIndex++] = convertPixel(pixel, format);
                inputOffset += bytesPerPixel;
            }
        }
    }

    /// Converts one packed guest pixel word into one `0xAARRGGBB` host pixel.
    public static int convertPixel(long pixel, FramebufferPixelFormat format) {
        Objects.requireNonNull(format, "format");
        int red = extractColorChannel(pixel, format.redOffset(), format.redLength());
        int green = extractColorChannel(pixel, format.greenOffset(), format.greenLength());
        int blue = extractColorChannel(pixel, format.blueOffset(), format.blueLength());
        int alpha = format.transparencyLength() == 0
                ? 0xff
                : extractColorChannel(pixel, format.transparencyOffset(), format.transparencyLength());
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /// Reads one little-endian packed pixel word.
    private static long readLittleEndianPixel(byte[] source, int offset, int bytesPerPixel) {
        long pixel = 0;
        for (int index = 0; index < bytesPerPixel; index++) {
            pixel |= Byte.toUnsignedLong(source[offset + index]) << (index * Byte.SIZE);
        }
        return pixel;
    }

    /// Extracts one color channel and expands it to eight bits.
    private static int extractColorChannel(long pixel, int offset, int length) {
        long mask = length == Long.SIZE ? -1L : (1L << length) - 1L;
        long value = (pixel >>> offset) & mask;
        long maximum = mask;
        return (int) ((value * 255L + maximum / 2L) / maximum);
    }
}
