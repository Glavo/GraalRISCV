// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.gui;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.runtime.FramebufferDevice;
import org.glavo.riscv.runtime.FramebufferGeometry;
import org.glavo.riscv.runtime.FramebufferPixelFormat;
import org.jetbrains.annotations.NotNullByDefault;

/// Provides a minimal host-side framebuffer animation demo.
///
/// The demo writes complete XRGB8888 frames through `FramebufferDevice.write`,
/// matching the path that a future `/dev/fb0` implementation will use.
@NotNullByDefault
public final class SwingFramebufferDemo {
    /// The demo framebuffer width in pixels.
    private static final int WIDTH = 320;

    /// The demo framebuffer height in pixels.
    private static final int HEIGHT = 180;

    /// The integer host display scale.
    private static final int SCALE = 3;

    /// The target interval between animation frames in milliseconds.
    private static final int FRAME_MILLIS = 16;

    /// The number of bytes in one XRGB8888 pixel.
    private static final int BYTES_PER_PIXEL = 4;

    /// Prevents construction of this utility class.
    private SwingFramebufferDemo() {
    }

    /// Runs the Swing framebuffer demo.
    public static void main(String[] args) {
        if (args.length != 0) {
            throw new RiscVException("SwingFramebufferDemo does not accept command-line arguments");
        }

        FramebufferGeometry geometry = FramebufferGeometry.packed(WIDTH, HEIGHT, FramebufferPixelFormat.XRGB8888);
        FramebufferDevice device = new FramebufferDevice(geometry);
        SwingFramebufferBackend backend = new SwingFramebufferBackend(
                device,
                "JRISC-V Swing Framebuffer Demo",
                SCALE,
                FRAME_MILLIS);

        backend.open();
        Thread animationThread = new Thread(
                () -> runAnimation(device, geometry),
                "jriscv-swing-framebuffer-demo");
        animationThread.setDaemon(true);
        animationThread.start();
    }

    /// Runs the animation loop that writes frames to the framebuffer device.
    private static void runAnimation(FramebufferDevice device, FramebufferGeometry geometry) {
        byte[] frame = new byte[geometry.bufferSizeBytes()];
        long frameIndex = 0;
        while (!Thread.currentThread().isInterrupted()) {
            renderFrame(frame, geometry, frameIndex++);
            device.write(0, frame, 0, frame.length);
            sleepFrameInterval();
        }
    }

    /// Renders one demo frame into the supplied framebuffer byte buffer.
    private static void renderFrame(byte[] frame, FramebufferGeometry geometry, long frameIndex) {
        int stride = geometry.strideBytes();
        int barOffset = (int) (frameIndex * 3L % geometry.width());
        int pulse = (int) ((frameIndex * 5L) & 0xff);

        for (int y = 0; y < geometry.height(); y++) {
            int rowOffset = y * stride;
            for (int x = 0; x < geometry.width(); x++) {
                int checker = ((x >> 4) ^ (y >> 4)) & 1;
                int base = checker == 0 ? 0x22 : 0x38;
                int red = base + ((x * 96) / geometry.width());
                int green = base + ((y * 112) / geometry.height());
                int blue = 0x48 + ((x + y + pulse) & 0x3f);

                int movingX = Math.floorMod(x - barOffset, geometry.width());
                if (movingX < 34) {
                    red = 0xf0;
                    green = 0xc8 - movingX * 2;
                    blue = 0x20 + movingX * 3;
                }

                int centerX = geometry.width() / 2;
                int centerY = geometry.height() / 2;
                int distance = Math.abs(x - centerX) + Math.abs(y - centerY);
                int ring = Math.floorMod(distance - (int) frameIndex, 48);
                if (ring < 3) {
                    red = 0x30;
                    green = 0xd8;
                    blue = 0xff;
                }

                writeXrgb8888(frame, rowOffset + x * BYTES_PER_PIXEL, red, green, blue);
            }
        }
    }

    /// Writes one little-endian XRGB8888 pixel.
    private static void writeXrgb8888(byte[] frame, int offset, int red, int green, int blue) {
        frame[offset] = (byte) clampColor(blue);
        frame[offset + 1] = (byte) clampColor(green);
        frame[offset + 2] = (byte) clampColor(red);
        frame[offset + 3] = 0;
    }

    /// Clamps a color channel to the unsigned byte range.
    private static int clampColor(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 0xff) {
            return 0xff;
        }
        return value;
    }

    /// Sleeps until the next animation frame should be produced.
    private static void sleepFrameInterval() {
        try {
            Thread.sleep(FRAME_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
