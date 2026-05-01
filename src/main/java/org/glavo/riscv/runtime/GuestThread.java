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

    /// The Linux signal mask currently installed for this guest thread.
    private long signalMask;

    /// The active RISC-V userspace pointer mask length for this thread.
    private int pointerMaskLength;

    /// Whether the Linux tagged-address syscall ABI flag is enabled for this thread.
    private boolean taggedAddressAbiEnabled;

    /// Whether this thread has a registered restartable sequence area.
    private boolean restartableSequenceRegistered;

    /// The guest `struct rseq` address registered by `rseq`.
    private long restartableSequenceAddress;

    /// The guest `struct rseq` length registered by `rseq`.
    private long restartableSequenceLength;

    /// The architecture restartable sequence signature registered by `rseq`.
    private long restartableSequenceSignature;

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

    /// Returns the Linux signal mask currently installed for this thread.
    long signalMask() {
        return signalMask;
    }

    /// Updates the Linux signal mask currently installed for this thread.
    void setSignalMask(long signalMask) {
        this.signalMask = signalMask;
    }

    /// Returns the active RISC-V userspace pointer mask length for this thread.
    int pointerMaskLength() {
        return pointerMaskLength;
    }

    /// Returns true when the Linux tagged-address syscall ABI flag is enabled for this thread.
    boolean taggedAddressAbiEnabled() {
        return taggedAddressAbiEnabled;
    }

    /// Updates this thread's RISC-V userspace pointer masking state.
    void setTaggedAddressControl(int pointerMaskLength, boolean taggedAddressAbiEnabled) {
        this.pointerMaskLength = pointerMaskLength;
        this.taggedAddressAbiEnabled = taggedAddressAbiEnabled;
    }

    /// Copies inherited per-thread execution controls from a parent guest thread.
    void inheritExecutionControlsFrom(GuestThread parent) {
        pointerMaskLength = parent.pointerMaskLength;
        taggedAddressAbiEnabled = parent.taggedAddressAbiEnabled;
    }

    /// Returns true when this thread has a registered restartable sequence area.
    boolean hasRestartableSequence() {
        return restartableSequenceRegistered;
    }

    /// Returns the registered restartable sequence area address, or zero when unregistered.
    long restartableSequenceAddress() {
        return restartableSequenceAddress;
    }

    /// Returns the registered restartable sequence area length.
    long restartableSequenceLength() {
        return restartableSequenceLength;
    }

    /// Returns the registered restartable sequence signature.
    long restartableSequenceSignature() {
        return restartableSequenceSignature;
    }

    /// Updates this thread's restartable sequence registration.
    void setRestartableSequence(long address, long length, long signature) {
        restartableSequenceRegistered = true;
        restartableSequenceAddress = address;
        restartableSequenceLength = length;
        restartableSequenceSignature = signature;
    }

    /// Clears this thread's restartable sequence registration.
    void clearRestartableSequence() {
        restartableSequenceRegistered = false;
        restartableSequenceAddress = 0;
        restartableSequenceLength = 0;
        restartableSequenceSignature = 0;
    }
}
