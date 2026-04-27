package org.glavo.riscv;

import com.oracle.truffle.api.TruffleLanguage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

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

    /// The configured host directory exposed through sandboxed guest file syscalls.
    private final String hostRoot;

    /// The guest application arguments supplied after the ELF path.
    private final String @Unmodifiable [] programArguments;

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
            env.getPublicTruffleFile(hostRoot);
        } catch (IllegalArgumentException exception) {
            throw new RiscVException("riscv.hostRoot is invalid: " + hostRoot, exception);
        }

        this.env = env;
        this.memoryBase = memoryBase;
        this.memorySize = memorySize;
        this.maxInstructions = maxInstructions;
        this.trace = trace;
        this.hostRoot = hostRoot;
        this.programArguments = env.getApplicationArguments().clone();
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

    /// Returns the configured host directory exposed through sandboxed guest file syscalls.
    public String hostRoot() {
        return hostRoot;
    }

    /// Returns the guest application arguments supplied after the ELF path.
    public String @Unmodifiable [] programArguments() {
        return programArguments.clone();
    }
}
