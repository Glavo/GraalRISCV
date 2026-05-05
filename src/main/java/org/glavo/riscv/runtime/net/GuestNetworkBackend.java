// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime.net;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/// Creates host resources for guest Internet sockets.
@NotNullByDefault
public interface GuestNetworkBackend {
    /// Returns true when guest Internet sockets may access host networking.
    boolean enabled();

    /// Opens a nonblocking host TCP client channel for the requested protocol family.
    SocketChannel openSocketChannel(ProtocolFamily family) throws IOException;

    /// Opens a nonblocking host TCP server channel for the requested protocol family.
    ServerSocketChannel openServerSocketChannel(ProtocolFamily family) throws IOException;

    /// Opens a nonblocking host UDP channel for the requested protocol family.
    DatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException;
}
