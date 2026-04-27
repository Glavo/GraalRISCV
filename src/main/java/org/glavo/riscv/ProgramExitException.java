package org.glavo.riscv;

import com.oracle.truffle.api.nodes.ControlFlowException;
import org.jetbrains.annotations.NotNullByDefault;

/// Transfers control from guest execution to the Truffle root node with a guest exit code.
@NotNullByDefault
public final class ProgramExitException extends ControlFlowException {
    /// The exit code produced by the guest program.
    private final long exitCode;

    /// Creates an exit transfer with the supplied guest exit code.
    public ProgramExitException(long exitCode) {
        this.exitCode = exitCode;
    }

    /// Returns the guest exit code.
    public long exitCode() {
        return exitCode;
    }
}
