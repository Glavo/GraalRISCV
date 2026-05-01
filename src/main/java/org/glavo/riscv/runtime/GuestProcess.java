// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/// Stores Linux user-mode process state that is shared by all guest threads.
@NotNullByDefault
final class GuestProcess {
    /// The deterministic guest process id returned by process identity syscalls.
    static final int PROCESS_ID = 1;

    /// The fixed guest parent process id returned by `getppid`.
    static final int PARENT_PROCESS_ID = 0;

    /// The fixed guest process group id used by process-group syscalls.
    static final int PROCESS_GROUP_ID = PROCESS_ID;

    /// The process leader represented by the initial guest thread.
    private final GuestThread initialThread = new GuestThread(PROCESS_ID);

    /// Live guest threads in this process.
    private final ArrayList<GuestThread> threads = new ArrayList<>();

    /// The next synthetic guest thread id returned by accepted `clone` calls.
    private long nextThreadId = PROCESS_ID + 1L;

    /// Creates a guest process with its initial thread registered.
    GuestProcess() {
        threads.add(initialThread);
    }

    /// Returns the process leader thread state used by the initial architectural state.
    GuestThread initialThread() {
        return initialThread;
    }

    /// Creates an unregistered child thread state with a fresh guest thread id.
    @Nullable GuestThread createChildThread() {
        long id = nextThreadId;
        if (id <= PROCESS_ID || id > Integer.MAX_VALUE) {
            return null;
        }
        nextThreadId++;
        return new GuestThread((int) id);
    }

    /// Registers a live guest thread in this process.
    void registerThread(GuestThread thread) {
        threads.add(thread);
    }

    /// Removes a guest thread from the live process registry.
    void unregisterThread(GuestThread thread) {
        threads.remove(thread);
    }

    /// Returns the number of currently live guest threads in this process.
    int threadCount() {
        return threads.size();
    }

    /// Returns the live guest thread with the supplied id, or null when none is known.
    @Nullable GuestThread thread(long threadId) {
        if (threadId != (int) threadId) {
            return null;
        }

        int id = (int) threadId;
        for (GuestThread thread : threads) {
            if (thread.id() == id) {
                return thread;
            }
        }
        return null;
    }
}
