// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.gui;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.runtime.FramebufferDevice;
import org.glavo.riscv.runtime.FramebufferDirtyRegion;
import org.glavo.riscv.runtime.FramebufferGeometry;
import org.glavo.riscv.runtime.FramebufferSnapshot;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/// Presents a framebuffer device through a Swing window.
///
/// The backend samples immutable framebuffer snapshots and updates Swing state
/// only on the event dispatch thread.
@NotNullByDefault
public final class SwingFramebufferBackend implements AutoCloseable {
    /// The default window title.
    public static final String DEFAULT_TITLE = "JRISC-V Framebuffer";

    /// The default integer pixel scale used for the Swing window.
    public static final int DEFAULT_SCALE = 1;

    /// The default refresh interval in milliseconds.
    public static final int DEFAULT_REFRESH_MILLIS = 16;

    /// The framebuffer device to present.
    private final FramebufferDevice device;

    /// The host window title.
    private final String title;

    /// The integer scale factor used for display pixels.
    private final int scale;

    /// The Swing timer interval in milliseconds.
    private final int refreshMillis;

    /// The host Swing window, or null before open and after close.
    private @Nullable JFrame frame;

    /// The Swing repaint timer, or null before open and after close.
    private @Nullable Timer timer;

    /// The Swing panel that paints the framebuffer image, or null before open and after close.
    private @Nullable FramebufferPanel panel;

    /// The host ARGB image backing the Swing panel, or null before open and after close.
    private @Nullable BufferedImage image;

    /// The reusable ARGB conversion buffer, or null before open and after close.
    private int @Nullable [] argbPixels;

    /// The last framebuffer modification counter converted into the host image.
    private long renderedModificationCounter = Long.MIN_VALUE;

    /// Creates a Swing framebuffer backend with default presentation settings.
    public SwingFramebufferBackend(FramebufferDevice device) {
        this(device, DEFAULT_TITLE, DEFAULT_SCALE, DEFAULT_REFRESH_MILLIS);
    }

    /// Creates a Swing framebuffer backend.
    public SwingFramebufferBackend(FramebufferDevice device, String title, int scale, int refreshMillis) {
        this.device = Objects.requireNonNull(device, "device");
        this.title = Objects.requireNonNull(title, "title");
        if (scale <= 0) {
            throw new RiscVException("Swing framebuffer scale must be positive: " + scale);
        }
        if (refreshMillis <= 0) {
            throw new RiscVException("Swing framebuffer refresh interval must be positive: " + refreshMillis);
        }
        this.scale = scale;
        this.refreshMillis = refreshMillis;
    }

    /// Opens the Swing framebuffer window and starts periodic refreshes.
    public void open() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new RiscVException("Swing framebuffer backend cannot open in a headless graphics environment");
        }
        runOnEventDispatchThreadAndWait(this::openOnEventDispatchThread);
    }

    /// Refreshes the host image once from the current framebuffer snapshot.
    public void refresh() {
        runOnEventDispatchThreadAndWait(this::refreshOnEventDispatchThread);
    }

    /// Closes the Swing framebuffer window and stops periodic refreshes.
    @Override
    public void close() {
        runOnEventDispatchThreadAndWait(this::closeOnEventDispatchThread);
    }

    /// Opens Swing state on the event dispatch thread.
    private void openOnEventDispatchThread() {
        if (frame != null) {
            return;
        }

        FramebufferGeometry geometry = device.geometry();
        image = new BufferedImage(geometry.width(), geometry.height(), BufferedImage.TYPE_INT_ARGB);
        argbPixels = new int[Math.multiplyExact(geometry.width(), geometry.height())];
        panel = new FramebufferPanel(image, scale);

        JFrame newFrame = new JFrame(title);
        newFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        newFrame.addWindowListener(new WindowAdapter() {
            /// Clears backend state when the user closes the host window.
            @Override
            public void windowClosed(WindowEvent event) {
                clearSwingStateOnEventDispatchThread();
            }
        });
        newFrame.setContentPane(panel);
        newFrame.pack();
        newFrame.setLocationByPlatform(true);
        frame = newFrame;

        refreshOnEventDispatchThread();
        newFrame.setVisible(true);

        timer = new Timer(refreshMillis, event -> refreshOnEventDispatchThread());
        timer.start();
    }

    /// Refreshes Swing image state on the event dispatch thread.
    private void refreshOnEventDispatchThread() {
        BufferedImage currentImage = image;
        FramebufferPanel currentPanel = panel;
        int @Nullable [] currentArgbPixels = argbPixels;
        if (currentImage == null || currentPanel == null || currentArgbPixels == null) {
            return;
        }

        FramebufferSnapshot snapshot = device.takeSnapshot();
        FramebufferDirtyRegion dirtyRegion = snapshot.dirtyRegion();
        if (snapshot.modificationCounter() == renderedModificationCounter && dirtyRegion == null) {
            return;
        }

        FramebufferGeometry geometry = snapshot.geometry();
        FramebufferArgbConverter.convert(snapshot, currentArgbPixels);
        currentImage.setRGB(0, 0, geometry.width(), geometry.height(), currentArgbPixels, 0, geometry.width());
        renderedModificationCounter = snapshot.modificationCounter();

        if (dirtyRegion == null) {
            currentPanel.repaint();
        } else {
            currentPanel.repaint(
                    dirtyRegion.x() * scale,
                    dirtyRegion.y() * scale,
                    dirtyRegion.width() * scale,
                    dirtyRegion.height() * scale);
        }
    }

    /// Closes Swing state on the event dispatch thread.
    private void closeOnEventDispatchThread() {
        JFrame currentFrame = frame;
        clearSwingStateOnEventDispatchThread();

        if (currentFrame != null) {
            currentFrame.dispose();
        }
    }

    /// Clears Swing state on the event dispatch thread.
    private void clearSwingStateOnEventDispatchThread() {
        Timer currentTimer = timer;
        if (currentTimer != null) {
            currentTimer.stop();
        }
        timer = null;

        frame = null;
        panel = null;
        image = null;
        argbPixels = null;
        renderedModificationCounter = Long.MIN_VALUE;
    }

    /// Runs one action on the Swing event dispatch thread and waits for completion.
    private static void runOnEventDispatchThreadAndWait(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }

        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RiscVException("Interrupted while running Swing framebuffer action", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RiscVException("Swing framebuffer action failed", cause);
        }
    }

    /// Swing panel that paints a scaled framebuffer image.
    @NotNullByDefault
    private static final class FramebufferPanel extends JPanel {
        /// The serialization identifier for Swing compatibility.
        private static final long serialVersionUID = 1L;

        /// The host ARGB framebuffer image.
        private final BufferedImage image;

        /// The integer scale factor used for display pixels.
        private final int scale;

        /// Creates a framebuffer panel.
        private FramebufferPanel(BufferedImage image, int scale) {
            this.image = image;
            this.scale = scale;
        }

        /// Returns the natural panel size for the configured framebuffer scale.
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(image.getWidth() * scale, image.getHeight() * scale);
        }

        /// Paints the framebuffer image.
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                graphics2D.drawImage(image, 0, 0, image.getWidth() * scale, image.getHeight() * scale, null);
            } finally {
                graphics2D.dispose();
            }
        }
    }
}
