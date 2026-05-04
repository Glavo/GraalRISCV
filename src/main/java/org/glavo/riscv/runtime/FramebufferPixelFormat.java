// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.RiscVException;
import org.jetbrains.annotations.NotNullByDefault;

/// Describes a packed linear framebuffer pixel layout.
///
/// Channel offsets are measured from the least significant bit of one pixel word,
/// matching Linux framebuffer `fb_bitfield` conventions on little-endian guests.
///
/// @param bitsPerPixel the number of bits occupied by one pixel
/// @param redOffset the low bit offset of the red channel
/// @param redLength the bit length of the red channel
/// @param greenOffset the low bit offset of the green channel
/// @param greenLength the bit length of the green channel
/// @param blueOffset the low bit offset of the blue channel
/// @param blueLength the bit length of the blue channel
/// @param transparencyOffset the low bit offset of the transparency channel, or zero when absent
/// @param transparencyLength the bit length of the transparency channel, or zero when absent
@NotNullByDefault
public record FramebufferPixelFormat(
        int bitsPerPixel,
        int redOffset,
        int redLength,
        int greenOffset,
        int greenLength,
        int blueOffset,
        int blueLength,
        int transparencyOffset,
        int transparencyLength) {
    /// The largest channel width that host ARGB conversion accepts.
    private static final int MAX_CHANNEL_BITS = Integer.SIZE;

    /// Little-endian 32-bit RGB format with one ignored high byte.
    public static final FramebufferPixelFormat XRGB8888 =
            new FramebufferPixelFormat(32, 16, 8, 8, 8, 0, 8, 0, 0);

    /// Little-endian 32-bit ARGB format with eight transparency bits.
    public static final FramebufferPixelFormat ARGB8888 =
            new FramebufferPixelFormat(32, 16, 8, 8, 8, 0, 8, 24, 8);

    /// Little-endian 16-bit RGB format using five red bits, six green bits, and five blue bits.
    public static final FramebufferPixelFormat RGB565 =
            new FramebufferPixelFormat(16, 11, 5, 5, 6, 0, 5, 0, 0);

    /// Creates a packed framebuffer pixel format.
    public FramebufferPixelFormat {
        if (bitsPerPixel <= 0 || bitsPerPixel > Long.SIZE || bitsPerPixel % Byte.SIZE != 0) {
            throw new RiscVException("Framebuffer bits per pixel must be a positive byte multiple up to 64: "
                    + bitsPerPixel);
        }

        validateColorChannel("red", bitsPerPixel, redOffset, redLength);
        validateColorChannel("green", bitsPerPixel, greenOffset, greenLength);
        validateColorChannel("blue", bitsPerPixel, blueOffset, blueLength);
        validateOptionalChannel("transparency", bitsPerPixel, transparencyOffset, transparencyLength);

        if (overlaps(redOffset, redLength, greenOffset, greenLength)
                || overlaps(redOffset, redLength, blueOffset, blueLength)
                || overlaps(greenOffset, greenLength, blueOffset, blueLength)
                || overlaps(redOffset, redLength, transparencyOffset, transparencyLength)
                || overlaps(greenOffset, greenLength, transparencyOffset, transparencyLength)
                || overlaps(blueOffset, blueLength, transparencyOffset, transparencyLength)) {
            throw new RiscVException("Framebuffer pixel channels must not overlap");
        }
    }

    /// Returns the number of bytes occupied by one pixel.
    public int bytesPerPixel() {
        return bitsPerPixel / Byte.SIZE;
    }

    /// Validates a required color channel.
    private static void validateColorChannel(String name, int bitsPerPixel, int offset, int length) {
        if (length <= 0) {
            throw new RiscVException("Framebuffer " + name + " channel length must be positive: " + length);
        }
        validateOptionalChannel(name, bitsPerPixel, offset, length);
    }

    /// Validates an optional channel.
    private static void validateOptionalChannel(String name, int bitsPerPixel, int offset, int length) {
        if (length < 0) {
            throw new RiscVException("Framebuffer " + name + " channel length must be non-negative: " + length);
        }
        if (length > MAX_CHANNEL_BITS) {
            throw new RiscVException("Framebuffer " + name + " channel length is too large: " + length);
        }
        if (length == 0) {
            if (offset != 0) {
                throw new RiscVException("Framebuffer empty " + name + " channel offset must be zero: " + offset);
            }
            return;
        }
        if (offset < 0 || offset >= bitsPerPixel || length > bitsPerPixel - offset) {
            throw new RiscVException("Framebuffer " + name + " channel range is outside the pixel word");
        }
    }

    /// Returns true when two non-empty half-open bit ranges overlap.
    private static boolean overlaps(int firstOffset, int firstLength, int secondOffset, int secondLength) {
        if (firstLength == 0 || secondLength == 0) {
            return false;
        }
        return firstOffset < secondOffset + secondLength && secondOffset < firstOffset + firstLength;
    }
}
