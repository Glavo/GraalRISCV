// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.RiscVException;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Describes a rectangular portion of a framebuffer that changed.
///
/// @param x the left edge in pixels
/// @param y the top edge in pixels
/// @param width the changed rectangle width in pixels
/// @param height the changed rectangle height in pixels
@NotNullByDefault
public record FramebufferDirtyRegion(int x, int y, int width, int height) {
    /// Creates a dirty framebuffer rectangle.
    public FramebufferDirtyRegion {
        if (x < 0) {
            throw new RiscVException("Framebuffer dirty region x must be non-negative: " + x);
        }
        if (y < 0) {
            throw new RiscVException("Framebuffer dirty region y must be non-negative: " + y);
        }
        if (width <= 0) {
            throw new RiscVException("Framebuffer dirty region width must be positive: " + width);
        }
        if (height <= 0) {
            throw new RiscVException("Framebuffer dirty region height must be positive: " + height);
        }
        if ((long) x + width > Integer.MAX_VALUE || (long) y + height > Integer.MAX_VALUE) {
            throw new RiscVException("Framebuffer dirty region range is too large");
        }
    }

    /// Returns a rectangle that covers this region and the supplied region.
    public FramebufferDirtyRegion merge(FramebufferDirtyRegion other) {
        Objects.requireNonNull(other, "other");
        int left = Math.min(x, other.x);
        int top = Math.min(y, other.y);
        int right = Math.max(x + width, other.x + other.width);
        int bottom = Math.max(y + height, other.y + other.height);
        return new FramebufferDirtyRegion(left, top, right - left, bottom - top);
    }
}
