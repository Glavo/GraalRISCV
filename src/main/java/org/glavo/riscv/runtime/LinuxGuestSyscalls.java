// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import org.glavo.riscv.memory.Memory;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.InputStream;
import java.io.OutputStream;

/// Handles the Linux-compatible syscall subset exposed by the simulator.
@NotNullByDefault
public final class LinuxGuestSyscalls extends GuestSyscalls {
    /// Creates a Linux syscall handler backed by the supplied host streams and heap boundary.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak) {
        super(memory, in, out, err, initialProgramBreak);
    }

    /// Creates a Linux syscall handler backed by the supplied host streams, heap boundary, and resolved root mount.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            @Nullable TruffleFile hostRoot) {
        super(memory, in, out, err, initialProgramBreak, hostRoot);
    }

    /// Creates a Linux syscall handler backed by the supplied streams, resolved root mount, and guest time source.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            @Nullable TruffleFile hostRoot,
            TimeSource timeSource) {
        super(memory, in, out, err, initialProgramBreak, hostRoot, timeSource);
    }

    /// Creates a Linux syscall handler backed by the supplied streams, filesystem namespace, and guest time source.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            GuestFileSystem fileSystem,
            TimeSource timeSource) {
        super(memory, in, out, err, initialProgramBreak, fileSystem, timeSource);
    }

    /// Creates a Linux syscall handler backed by streams, filesystem namespace, guest time source, and credentials.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            GuestFileSystem fileSystem,
            TimeSource timeSource,
            GuestCredentials credentials) {
        super(memory, in, out, err, initialProgramBreak, fileSystem, timeSource, credentials);
    }

    /// Creates a Linux syscall handler backed by the supplied host streams, heap boundary, and lazy root mount.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String hostRootPath) {
        super(memory, in, out, err, initialProgramBreak, env, hostRootPath);
    }

    /// Creates a Linux syscall handler backed by the supplied streams, lazy root mount, and guest time source.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String hostRootPath,
            TimeSource timeSource) {
        super(memory, in, out, err, initialProgramBreak, env, hostRootPath, timeSource);
    }

    /// Creates a Linux syscall handler backed by streams, lazy root mount, guest time source, and guest thread runner.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String hostRootPath,
            TimeSource timeSource,
            GuestThreadRunner guestThreadRunner) {
        super(memory, in, out, err, initialProgramBreak, env, hostRootPath, timeSource, guestThreadRunner);
    }

    /// Creates a Linux syscall handler backed by streams, lazy root mount, guest time source, terminal option,
    /// and guest thread runner.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String hostRootPath,
            TimeSource timeSource,
            boolean useHostTty,
            GuestThreadRunner guestThreadRunner) {
        super(memory, in, out, err, initialProgramBreak, env, hostRootPath, timeSource, useHostTty, guestThreadRunner);
    }

    /// Creates a Linux syscall handler backed by the supplied streams, lazy filesystem mounts, and guest time source.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String @Unmodifiable [] filesystemMountSpecs,
            TimeSource timeSource) {
        super(memory, in, out, err, initialProgramBreak, env, filesystemMountSpecs, timeSource);
    }

    /// Creates a Linux syscall handler backed by streams, lazy filesystem mounts, guest time source,
    /// and guest thread runner.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String @Unmodifiable [] filesystemMountSpecs,
            TimeSource timeSource,
            GuestThreadRunner guestThreadRunner) {
        super(memory, in, out, err, initialProgramBreak, env, filesystemMountSpecs, timeSource, guestThreadRunner);
    }

    /// Creates a Linux syscall handler backed by streams, lazy filesystem mounts, guest time source,
    /// terminal option, and guest thread runner.
    public LinuxGuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String @Unmodifiable [] filesystemMountSpecs,
            TimeSource timeSource,
            boolean useHostTty,
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
                guestThreadRunner);
    }

    /// Creates a Linux syscall handler backed by streams, lazy filesystem mounts, time source, credentials,
    /// terminal option, and guest thread runner.
    public LinuxGuestSyscalls(
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

    /// Creates a child-process Linux syscall handler by copying fork-inherited parent state.
    private LinuxGuestSyscalls(LinuxGuestSyscalls parent, Memory memory, GuestProcess process) {
        super(parent, memory, process);
    }

    /// Executes one Linux guest syscall.
    @Override
    public void handle(MachineState state, long pc) {
        handleLinux(state, pc);
    }

    /// Creates a child-process Linux syscall handler.
    @Override
    protected GuestSyscalls createChildSyscalls(Memory childMemory, GuestProcess childProcess) {
        return new LinuxGuestSyscalls(this, childMemory, childProcess);
    }
}
