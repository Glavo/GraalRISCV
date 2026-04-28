package org.glavo.riscv;

import com.oracle.truffle.api.nodes.ControlFlowException;
import org.jetbrains.annotations.NotNullByDefault;

/// Transfers control from guest execution to the scheduler when one guest thread exits.
@NotNullByDefault
final class ThreadExitException extends ControlFlowException {
    /// The exit code supplied by the guest thread.
    private final long exitCode;

    /// Creates a thread-exit transfer with the supplied guest exit code.
    ThreadExitException(long exitCode) {
        this.exitCode = exitCode;
    }

    /// Returns the guest thread exit code.
    long exitCode() {
        return exitCode;
    }
}
