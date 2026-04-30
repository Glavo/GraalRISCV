// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.jetbrains.annotations.NotNullByDefault;

/// Stores Linux user-mode state that belongs to one guest thread rather than the whole process.
@NotNullByDefault
final class GuestThread {
    /// The Linux `SS_DISABLE` flag used for a thread without an alternate signal stack.
    private static final long SIGNAL_STACK_DISABLED = 2L;

    /// The Linux thread id represented by this guest thread.
    private final int id;

    /// The guest clear-child-TID address used by thread exit wakeups, or zero when unset.
    private long clearChildTidAddress;

    /// The robust futex list head address supplied by `set_robust_list`.
    private long robustListHeadAddress;

    /// The robust futex list structure length supplied by `set_robust_list`.
    private long robustListLength;

    /// The guest alternate signal stack pointer registered by `sigaltstack`.
    private long alternateSignalStackPointer;

    /// The guest alternate signal stack size registered by `sigaltstack`.
    private long alternateSignalStackSize;

    /// The guest alternate signal stack flags reported by `sigaltstack`.
    private long alternateSignalStackFlags = SIGNAL_STACK_DISABLED;

    /// Creates a guest thread with the supplied Linux thread id.
    GuestThread(int id) {
        this.id = id;
    }

    /// Returns the Linux thread id represented by this guest thread.
    int id() {
        return id;
    }

    /// Returns the guest clear-child-TID address used by this thread, or zero when unset.
    long clearChildTidAddress() {
        return clearChildTidAddress;
    }

    /// Updates the guest clear-child-TID address used by this thread.
    void setClearChildTidAddress(long clearChildTidAddress) {
        this.clearChildTidAddress = clearChildTidAddress;
    }

    /// Returns the robust futex list head address registered by this thread.
    long robustListHeadAddress() {
        return robustListHeadAddress;
    }

    /// Returns the robust futex list structure length registered by this thread.
    long robustListLength() {
        return robustListLength;
    }

    /// Updates the robust futex list registration for this thread.
    void setRobustList(long headAddress, long length) {
        this.robustListHeadAddress = headAddress;
        this.robustListLength = length;
    }

    /// Returns the alternate signal stack pointer registered by this thread.
    long alternateSignalStackPointer() {
        return alternateSignalStackPointer;
    }

    /// Returns the alternate signal stack size registered by this thread.
    long alternateSignalStackSize() {
        return alternateSignalStackSize;
    }

    /// Returns the alternate signal stack flags reported for this thread.
    long alternateSignalStackFlags() {
        return alternateSignalStackFlags;
    }

    /// Clears this thread's alternate signal stack registration.
    void disableAlternateSignalStack() {
        alternateSignalStackPointer = 0;
        alternateSignalStackSize = 0;
        alternateSignalStackFlags = SIGNAL_STACK_DISABLED;
    }

    /// Updates this thread's alternate signal stack registration.
    void setAlternateSignalStack(long pointer, long size, long flags) {
        alternateSignalStackPointer = pointer;
        alternateSignalStackSize = size;
        alternateSignalStackFlags = flags;
    }
}
