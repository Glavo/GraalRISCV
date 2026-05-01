// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Allocates deterministic Linux process and thread ids for one emulator run.
@NotNullByDefault
final class GuestProcessRegistry {
    /// The next synthetic Linux id available to child processes or threads.
    private long nextId = GuestProcess.PROCESS_ID + 1L;

    /// Creates a registry whose initial process id is already reserved.
    GuestProcessRegistry() {
    }

    /// Creates a child process that inherits its parent's process group.
    synchronized @Nullable GuestProcess createChildProcess(GuestProcess parent) {
        @Nullable Integer id = allocateId();
        if (id == null) {
            return null;
        }
        return new GuestProcess(id, parent.id(), parent.processGroupId());
    }

    /// Creates a child thread id in the supplied process.
    synchronized @Nullable GuestThread createChildThread(GuestProcess process) {
        @Nullable Integer id = allocateId();
        return id == null ? null : process.createChildThread(id);
    }

    /// Allocates one positive Linux id or null after the synthetic id space is exhausted.
    private @Nullable Integer allocateId() {
        long id = nextId;
        if (id <= GuestProcess.PROCESS_ID || id > Integer.MAX_VALUE) {
            return null;
        }
        nextId++;
        return (int) id;
    }
}
