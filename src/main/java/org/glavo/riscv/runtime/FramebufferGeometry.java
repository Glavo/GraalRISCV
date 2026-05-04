// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.RiscVException;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Describes the dimensions and layout of one linear framebuffer.
///
/// @param width the visible framebuffer width in pixels
/// @param height the visible framebuffer height in pixels
/// @param strideBytes the byte distance between adjacent scanlines
/// @param pixelFormat the packed pixel format used by the framebuffer
@NotNullByDefault
public record FramebufferGeometry(
        int width,
        int height,
        int strideBytes,
        FramebufferPixelFormat pixelFormat) {
    /// Creates validated framebuffer geometry.
    public FramebufferGeometry {
        Objects.requireNonNull(pixelFormat, "pixelFormat");
        if (width <= 0) {
            throw new RiscVException("Framebuffer width must be positive: " + width);
        }
        if (height <= 0) {
            throw new RiscVException("Framebuffer height must be positive: " + height);
        }

        int minimumStrideBytes = minimumStrideBytes(width, pixelFormat);
        if (strideBytes < minimumStrideBytes) {
            throw new RiscVException("Framebuffer stride is smaller than one visible row: stride="
                    + strideBytes + ", minimum=" + minimumStrideBytes);
        }
        if ((long) strideBytes * height > Integer.MAX_VALUE) {
            throw new RiscVException("Framebuffer backing buffer is too large: stride="
                    + strideBytes + ", height=" + height);
        }
    }

    /// Creates tightly packed framebuffer geometry.
    public static FramebufferGeometry packed(int width, int height, FramebufferPixelFormat pixelFormat) {
        return new FramebufferGeometry(width, height, minimumStrideBytes(width, pixelFormat), pixelFormat);
    }

    /// Returns the minimum byte stride needed for the supplied width and pixel format.
    public static int minimumStrideBytes(int width, FramebufferPixelFormat pixelFormat) {
        Objects.requireNonNull(pixelFormat, "pixelFormat");
        if (width <= 0) {
            throw new RiscVException("Framebuffer width must be positive: " + width);
        }
        long stride = (long) width * pixelFormat.bytesPerPixel();
        if (stride > Integer.MAX_VALUE) {
            throw new RiscVException("Framebuffer row is too large: width=" + width);
        }
        return (int) stride;
    }

    /// Returns the minimum byte stride needed for this geometry.
    public int minimumStrideBytes() {
        return minimumStrideBytes(width, pixelFormat);
    }

    /// Returns the byte size of the full framebuffer backing store.
    public int bufferSizeBytes() {
        return strideBytes * height;
    }
}
