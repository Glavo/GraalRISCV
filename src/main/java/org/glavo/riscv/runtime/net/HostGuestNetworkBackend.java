// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime.net;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/// Opens host Java NIO channels for guest Internet sockets.
@NotNullByDefault
public final class HostGuestNetworkBackend implements GuestNetworkBackend {
    /// The shared host passthrough network backend.
    public static final HostGuestNetworkBackend INSTANCE = new HostGuestNetworkBackend();

    /// Prevents external construction of the singleton backend.
    private HostGuestNetworkBackend() {
    }

    /// Returns true because this backend maps guest sockets to host networking.
    @Override
    public boolean enabled() {
        return true;
    }

    /// Opens a nonblocking host TCP client channel.
    @Override
    public SocketChannel openSocketChannel(ProtocolFamily family) throws IOException {
        SocketChannel channel = SocketChannel.open(family);
        channel.configureBlocking(false);
        return channel;
    }

    /// Opens a nonblocking host TCP server channel.
    @Override
    public ServerSocketChannel openServerSocketChannel(ProtocolFamily family) throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open(family);
        channel.configureBlocking(false);
        return channel;
    }

    /// Opens a nonblocking host UDP channel.
    @Override
    public DatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
        DatagramChannel channel = DatagramChannel.open(family);
        channel.configureBlocking(false);
        return channel;
    }
}
