package org.glavo.riscv.exception;

import com.oracle.truffle.api.nodes.ControlFlowException;
import org.jetbrains.annotations.NotNullByDefault;

/// Transfers control from guest execution to the scheduler when one guest thread exits.
@NotNullByDefault
public final class ThreadExitException extends ControlFlowException {
    /// The exit code supplied by the guest thread.
    private final long exitCode;

    /// Creates a thread-exit transfer with the supplied guest exit code.
    public ThreadExitException(long exitCode) {
        this.exitCode = exitCode;
    }

    /// Returns the guest thread exit code.
    public long exitCode() {
        return exitCode;
    }
}
