// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Objects;

/// Stores an immutable snapshot of one framebuffer at a point in time.
///
/// @param geometry the framebuffer geometry used by the pixel data
/// @param pixels a copy of the full framebuffer backing bytes
/// @param modificationCounter the framebuffer modification counter when the snapshot was created
/// @param dirtyRegion the pending dirty region when the snapshot was created, or null when no region is pending
@NotNullByDefault
public record FramebufferSnapshot(
        FramebufferGeometry geometry,
        byte @Unmodifiable [] pixels,
        long modificationCounter,
        @Nullable FramebufferDirtyRegion dirtyRegion) {
    /// Creates a framebuffer snapshot.
    public FramebufferSnapshot {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(pixels, "pixels");
        if (pixels.length != geometry.bufferSizeBytes()) {
            throw new IllegalArgumentException("Snapshot pixel buffer length does not match framebuffer geometry");
        }
        pixels = pixels.clone();
    }

    /// Returns a copy of the snapshot pixel bytes.
    @Override
    public byte @Unmodifiable [] pixels() {
        return pixels.clone();
    }
}
