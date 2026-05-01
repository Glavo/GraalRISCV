// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/// Stores Linux user-mode process state that is shared by all guest threads.
@NotNullByDefault
final class GuestProcess {
    /// The deterministic initial guest process id returned by process identity syscalls.
    static final int PROCESS_ID = 1;

    /// The fixed initial parent process id returned by `getppid`.
    static final int PARENT_PROCESS_ID = 0;

    /// The fixed initial process group id used by process-group syscalls.
    static final int PROCESS_GROUP_ID = PROCESS_ID;

    /// The Linux process id represented by this process.
    private final int id;

    /// The Linux parent process id represented by this process.
    private final int parentId;

    /// The Linux process group id represented by this process.
    private final int processGroupId;

    /// The process leader represented by the initial guest thread.
    private final GuestThread initialThread;

    /// Live guest threads in this process.
    private final ArrayList<GuestThread> threads = new ArrayList<>();

    /// Creates a guest process with its initial thread registered.
    GuestProcess(int id, int parentId, int processGroupId) {
        this.id = id;
        this.parentId = parentId;
        this.processGroupId = processGroupId;
        this.initialThread = new GuestThread(id);
        threads.add(initialThread);
    }

    /// Creates the initial guest process.
    static GuestProcess initial() {
        return new GuestProcess(PROCESS_ID, PARENT_PROCESS_ID, PROCESS_GROUP_ID);
    }

    /// Returns the Linux process id.
    int id() {
        return id;
    }

    /// Returns the Linux parent process id.
    int parentId() {
        return parentId;
    }

    /// Returns the Linux process group id.
    int processGroupId() {
        return processGroupId;
    }

    /// Returns the process leader thread state used by the initial architectural state.
    GuestThread initialThread() {
        return initialThread;
    }

    /// Creates an unregistered child thread state with a fresh guest thread id.
    GuestThread createChildThread(int threadId) {
        return new GuestThread(threadId);
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
