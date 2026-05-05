// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime.net;

import org.jetbrains.annotations.NotNullByDefault;

/// Identifies the configured guest networking mode.
@NotNullByDefault
public enum GuestNetworkMode {
    /// Disable guest Internet sockets.
    NONE("none", NoGuestNetworkBackend.INSTANCE),

    /// Map guest Internet sockets to host Java NIO channels.
    HOST("host", HostGuestNetworkBackend.INSTANCE);

    /// The CLI spelling for this mode.
    private final String optionName;

    /// The backend used by this mode.
    private final GuestNetworkBackend backend;

    /// Creates a network mode with its CLI spelling and backend.
    GuestNetworkMode(String optionName, GuestNetworkBackend backend) {
        this.optionName = optionName;
        this.backend = backend;
    }

    /// Returns the CLI spelling for this mode.
    public String optionName() {
        return optionName;
    }

    /// Returns the network backend used by this mode.
    public GuestNetworkBackend backend() {
        return backend;
    }

    /// Parses a CLI network mode.
    public static GuestNetworkMode parse(String value) {
        for (GuestNetworkMode mode : values()) {
            if (mode.optionName.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown guest network mode: " + value);
    }
}
