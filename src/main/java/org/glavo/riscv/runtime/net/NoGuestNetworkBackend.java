// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime.net;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/// Rejects guest network sockets without opening host network resources.
@NotNullByDefault
public final class NoGuestNetworkBackend implements GuestNetworkBackend {
    /// The shared disabled network backend.
    public static final NoGuestNetworkBackend INSTANCE = new NoGuestNetworkBackend();

    /// Prevents external construction of the singleton backend.
    private NoGuestNetworkBackend() {
    }

    /// Returns false because this backend disables host networking.
    @Override
    public boolean enabled() {
        return false;
    }

    /// Throws because host TCP client channels are unavailable when networking is disabled.
    @Override
    public SocketChannel openSocketChannel(ProtocolFamily family) throws IOException {
        throw new IOException("Guest host networking is disabled");
    }

    /// Throws because host TCP server channels are unavailable when networking is disabled.
    @Override
    public ServerSocketChannel openServerSocketChannel(ProtocolFamily family) throws IOException {
        throw new IOException("Guest host networking is disabled");
    }

    /// Throws because host UDP channels are unavailable when networking is disabled.
    @Override
    public DatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
        throw new IOException("Guest host networking is disabled");
    }

    /// Throws because host Unix-domain socket channels are unavailable when networking is disabled.
    @Override
    public SocketChannel openUnixSocketChannel() throws IOException {
        throw new IOException("Guest host networking is disabled");
    }
}
