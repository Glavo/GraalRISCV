// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import com.oracle.truffle.api.TruffleLanguage;
import org.glavo.riscv.memory.Memory;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.InputStream;
import java.io.OutputStream;

/// Handles the FreeBSD RISC-V syscall ABI exposed by the simulator.
@NotNullByDefault
public final class FreeBsdGuestSyscalls extends GuestSyscalls {
    /// Creates a FreeBSD syscall handler backed by streams, lazy filesystem mounts, time source, credentials,
    /// terminal option, and guest thread runner.
    public FreeBsdGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String @Unmodifiable [] filesystemMountSpecs,
            TimeSource timeSource,
            boolean useHostTty,
            GuestCredentials credentials,
            GuestThreadRunner guestThreadRunner) {
        super(
                memory,
                in,
                out,
                err,
                initialProgramBreak,
                env,
                filesystemMountSpecs,
                timeSource,
                useHostTty,
                credentials,
                guestThreadRunner);
    }

    /// Creates a child-process FreeBSD syscall handler by copying fork-inherited parent state.
    private FreeBsdGuestSyscalls(FreeBsdGuestSyscalls parent, Memory memory, GuestProcess process) {
        super(parent, memory, process);
    }

    /// Executes one FreeBSD guest syscall.
    @Override
    public void handle(MachineState state, long pc) {
        handleFreeBsd(state, pc);
    }

    /// Creates a child-process FreeBSD syscall handler.
    @Override
    protected GuestSyscalls createChildSyscalls(Memory childMemory, GuestProcess childProcess) {
        return new FreeBsdGuestSyscalls(this, childMemory, childProcess);
    }
}
