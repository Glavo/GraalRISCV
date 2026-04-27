package org.glavo.riscv;

import com.oracle.truffle.api.TruffleLanguage;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/// Stores per-context simulator configuration derived from Truffle language options.
@NotNullByDefault
public final class RiscVContext {
    /// The Truffle environment associated with this context.
    private final TruffleLanguage.Env env;

    /// The guest memory base address.
    private final long memoryBase;

    /// The guest memory size in bytes.
    private final long memorySize;

    /// The maximum guest instruction count, or zero when unlimited.
    private final long maxInstructions;

    /// Whether guest instruction tracing is enabled.
    private final boolean trace;

    /// The normalized host directory exposed through read-only guest file syscalls.
    private final Path hostRoot;

    /// Creates a simulator context.
    public RiscVContext(
            TruffleLanguage.Env env,
            long memoryBase,
            long memorySize,
            long maxInstructions,
            boolean trace,
            String hostRoot) {
        if (memoryBase < 0 && memoryBase != RiscVLanguage.AUTO_MEMORY_BASE) {
            throw new RiscVException("riscv.memoryBase must be non-negative or -1 for auto: " + memoryBase);
        }
        if (memorySize <= 0) {
            throw new RiscVException("riscv.memorySize must be positive: " + memorySize);
        }
        if (memoryBase != RiscVLanguage.AUTO_MEMORY_BASE && memoryBase > Long.MAX_VALUE - memorySize) {
            throw new RiscVException("Guest memory range overflows: base=" + memoryBase + ", size=" + memorySize);
        }
        if (maxInstructions < 0) {
            throw new RiscVException("riscv.maxInstructions must be non-negative: " + maxInstructions);
        }

        try {
            this.hostRoot = Path.of(hostRoot).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            throw new RiscVException("riscv.hostRoot is invalid: " + hostRoot, exception);
        }

        this.env = env;
        this.memoryBase = memoryBase;
        this.memorySize = memorySize;
        this.maxInstructions = maxInstructions;
        this.trace = trace;
    }

    /// Returns the Truffle environment associated with this context.
    public TruffleLanguage.Env env() {
        return env;
    }

    /// Returns the configured guest memory base address.
    public long memoryBase() {
        return memoryBase;
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

    /// Returns the host directory exposed through read-only guest file syscalls.
    public Path hostRoot() {
        return hostRoot;
    }
}
