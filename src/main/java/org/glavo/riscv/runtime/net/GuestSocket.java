// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime.net;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;

/// Describes a guest socket object stored in the shared descriptor table.
@NotNullByDefault
public interface GuestSocket extends AutoCloseable {
    /// Releases socket-local resources.
    @Override
    default void close() throws IOException {
    }
}
