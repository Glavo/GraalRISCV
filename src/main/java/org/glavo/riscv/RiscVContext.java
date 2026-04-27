package org.glavo.riscv;

import com.oracle.truffle.api.TruffleLanguage;
import org.jetbrains.annotations.NotNullByDefault;

/// Stores per-context simulator configuration derived from Truffle language options.
@NotNullByDefault
public final class RiscVContext {
    /// The Truffle environment associated with this context.
    private final TruffleLanguage.Env env;

    /// The guest memory size in bytes.
    private final long memorySize;

    /// The maximum guest instruction count, or zero when unlimited.
    private final long maxInstructions;

    /// Whether guest instruction tracing is enabled.
    private final boolean trace;

    /// Creates a simulator context.
    public RiscVContext(TruffleLanguage.Env env, long memorySize, long maxInstructions, boolean trace) {
        if (memorySize <= 0) {
            throw new RiscVException("riscv.memorySize must be positive: " + memorySize);
        }
        if (maxInstructions < 0) {
            throw new RiscVException("riscv.maxInstructions must be non-negative: " + maxInstructions);
        }

        this.env = env;
        this.memorySize = memorySize;
        this.maxInstructions = maxInstructions;
        this.trace = trace;
    }

    /// Returns the Truffle environment associated with this context.
    public TruffleLanguage.Env env() {
        return env;
    }

    /// Returns the configured guest memory size in bytes.
    public long memorySize() {
        return memorySize;
    }

    /// Returns the configured maximum guest instruction count, or zero when unlimited.
    public long maxInstructions() {
        return maxInstructions;
    }

    /// Returns whether guest instruction tracing is enabled.
    public boolean trace() {
        return trace;
    }
}
