// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.TruffleLanguage;
import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.MappedRegionCache;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.runtime.FilesystemMountSpec;
import org.glavo.riscv.runtime.GuestCredentials;
import org.glavo.riscv.runtime.TimeSource;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;

/// Stores per-context simulator configuration derived from Truffle language options.
@NotNullByDefault
public final class RiscVContext {
    /// The Truffle environment associated with this context.
    private final TruffleLanguage.Env env;

    /// The guest memory base address.
    private final long memoryBase;

    /// The guest virtual address window size in bytes.
    private final long memorySize;

    /// The guest base page size in bytes.
    private final long pageSize;

    /// The maximum number of committed guest base pages, or zero when unlimited.
    private final long maxCommittedPages;

    /// The guest HugeTLB page size in bytes.
    private final long hugePageSize;

    /// The number of guest HugeTLB pages reserved for MAP_HUGETLB.
    private final long hugePages;

    /// The vector register length in bits.
    private final int vectorVlenBits;

    /// The maximum guest instruction count, or zero when unlimited.
    private final long maxInstructions;

    /// Whether guest instruction tracing is enabled.
    private final boolean trace;

    /// The time source exposed to guest time syscalls.
    private final TimeSource timeSource;

    /// The host directory mounted at `/` when no explicit filesystem mounts are configured.
    private final String hostRoot;

    /// The configured guest filesystem mounts.
    private final String @Unmodifiable [] filesystemMounts;

    /// The guest path to load as the executable, or null when the source bytes are the executable.
    private final @Nullable String guestProgramPath;

    /// Whether `/dev/tty` should try to use the host controlling terminal.
    private final boolean useHostTty;

    /// The Linux user and group identity exposed to the guest process.
    private final GuestCredentials guestCredentials;

    /// The guest application arguments supplied after the ELF path.
    private final String @Unmodifiable [] programArguments;

    /// The sparse memory lookup cache scoped by the current Truffle context and host thread.
    private final ContextThreadLocal<MappedRegionCache> mappedRegionCache;

    /// Creates a simulator context.
    public RiscVContext(
            TruffleLanguage.Env env,
            long memoryBase,
            long memorySize,
            long pageSize,
            long maxCommittedPages,
            long hugePageSize,
            long hugePages,
            long vectorVlenBits,
            long maxInstructions,
            boolean trace,
            TimeSource timeSource,
            String hostRoot,
            String filesystemMounts,
            String guestProgramPath,
            boolean useHostTty,
            String guestUserName,
            long guestUid,
            long guestGid,
            String guestGroups,
            String guestHome,
            String guestShell,
            ContextThreadLocal<MappedRegionCache> mappedRegionCache) {
        if (memoryBase < 0 && memoryBase != RiscVLanguage.AUTO_MEMORY_BASE) {
            throw new RiscVException("riscv.memoryBase must be non-negative or -1 for auto: " + memoryBase);
        }
        if (memorySize <= 0) {
            throw new RiscVException("riscv.memorySize must be positive: " + memorySize);
        }
        if (memoryBase != RiscVLanguage.AUTO_MEMORY_BASE && memoryBase > Long.MAX_VALUE - memorySize) {
            throw new RiscVException("Guest memory range overflows: base=" + memoryBase + ", size=" + memorySize);
        }
        validatePageSize("riscv.pageSize", pageSize);
        validateHugePageSize(pageSize, hugePageSize);
        if (maxCommittedPages < 0) {
            throw new RiscVException("riscv.maxCommittedPages must be non-negative: " + maxCommittedPages);
        }
        if (hugePages < 0) {
            throw new RiscVException("riscv.hugePages must be non-negative: " + hugePages);
        }
        if (vectorVlenBits < Integer.MIN_VALUE || vectorVlenBits > Integer.MAX_VALUE) {
            throw new RiscVException("riscv.vectorVlen is outside the supported int range: " + vectorVlenBits);
        }
        org.glavo.riscv.runtime.VectorUnit.validateVectorLength((int) vectorVlenBits);
        if (maxInstructions < 0) {
            throw new RiscVException("riscv.maxInstructions must be non-negative: " + maxInstructions);
        }

        String[] parsedFilesystemMounts = parseFilesystemMounts(hostRoot, filesystemMounts);
        validateFilesystemMounts(env, parsedFilesystemMounts);
        @Nullable String normalizedGuestProgramPath = guestProgramPath.isEmpty()
                ? null
                : normalizeMountGuestPath(guestProgramPath);
        GuestCredentials parsedGuestCredentials = GuestCredentials.of(
                guestUserName,
                guestUid,
                guestGid,
                guestGroups,
                guestHome,
                guestShell);

        this.env = env;
        this.memoryBase = memoryBase;
        this.memorySize = memorySize;
        this.pageSize = pageSize;
        this.maxCommittedPages = maxCommittedPages;
        this.hugePageSize = hugePageSize;
        this.hugePages = hugePages;
        this.vectorVlenBits = (int) vectorVlenBits;
        this.maxInstructions = maxInstructions;
        this.trace = trace;
        this.timeSource = timeSource;
        this.hostRoot = hostRoot;
        this.filesystemMounts = parsedFilesystemMounts;
        this.guestProgramPath = normalizedGuestProgramPath;
        this.useHostTty = useHostTty;
        this.guestCredentials = parsedGuestCredentials;
        this.programArguments = env.getApplicationArguments().clone();
        this.mappedRegionCache = mappedRegionCache;
    }

    /// Returns the Truffle environment associated with this context.
    public TruffleLanguage.Env env() {
        return env;
    }

    /// Returns the configured guest memory base address.
    public long memoryBase() {
        return memoryBase;
    }

    /// Returns the configured guest virtual address window size in bytes.
    public long memorySize() {
        return memorySize;
    }

    /// Returns the configured guest base page size in bytes.
    public long pageSize() {
        return pageSize;
    }

    /// Returns the maximum number of committed guest base pages, or zero when unlimited.
    public long maxCommittedPages() {
        return maxCommittedPages;
    }

    /// Returns the configured guest HugeTLB page size in bytes.
    public long hugePageSize() {
        return hugePageSize;
    }

    /// Returns the configured guest HugeTLB page pool size.
    public long hugePages() {
        return hugePages;
    }

    /// Returns the configured vector register length in bits.
    public int vectorVlenBits() {
        return vectorVlenBits;
    }

    /// Returns the configured maximum guest instruction count, or zero when unlimited.
    public long maxInstructions() {
        return maxInstructions;
    }

    /// Returns whether guest instruction tracing is enabled.
    public boolean trace() {
        return trace;
    }

    /// Returns the time source exposed to guest time syscalls.
    public TimeSource timeSource() {
        return timeSource;
    }

    /// Returns the host directory mounted at `/` when no explicit filesystem mounts are configured.
    public String hostRoot() {
        return hostRoot;
    }

    /// Returns a copy of the configured guest filesystem mounts.
    public String @Unmodifiable [] filesystemMounts() {
        return filesystemMounts.clone();
    }

    /// Returns the guest path to load as the executable, or null when source bytes contain the executable.
    public @Nullable String guestProgramPath() {
        return guestProgramPath;
    }

    /// Returns whether `/dev/tty` should try to use the host controlling terminal.
    public boolean useHostTty() {
        return useHostTty;
    }

    /// Returns the Linux user and group identity exposed to the guest process.
    public GuestCredentials guestCredentials() {
        return guestCredentials;
    }

    /// Returns the guest application arguments supplied after the ELF path.
    public String @Unmodifiable [] programArguments() {
        return programArguments.clone();
    }

    /// Returns the sparse memory lookup cache for the current Truffle context and host thread.
    public ContextThreadLocal<MappedRegionCache> mappedRegionCache() {
        return mappedRegionCache;
    }

    /// Validates a configured guest page size.
    private static void validatePageSize(String optionName, long pageSize) {
        if (pageSize < Memory.MIN_PAGE_SIZE || pageSize > Integer.MAX_VALUE || !isPowerOfTwo(pageSize)) {
            throw new RiscVException(optionName + " must be a power of two between "
                    + Memory.MIN_PAGE_SIZE + " and " + Integer.MAX_VALUE + ": " + pageSize);
        }
    }

    /// Validates a configured guest HugeTLB page size.
    private static void validateHugePageSize(long pageSize, long hugePageSize) {
        if (hugePageSize < pageSize || !isPowerOfTwo(hugePageSize)) {
            throw new RiscVException("riscv.hugePageSize must be a power of two at least riscv.pageSize: "
                    + hugePageSize);
        }
    }

    /// Returns true when a value is a positive power of two.
    private static boolean isPowerOfTwo(long value) {
        return value > 0 && (value & (value - 1L)) == 0;
    }

    /// Parses newline-separated mount entries, defaulting to `riscv.hostRoot` as `/`.
    private static String @Unmodifiable [] parseFilesystemMounts(String hostRoot, String filesystemMounts) {
        if (filesystemMounts.isEmpty()) {
            return new String[]{new FilesystemMountSpec("/", hostRoot, FilesystemMountSpec.Type.BIND, null, false).encode()};
        }

        ArrayList<String> result = new ArrayList<>();
        for (String mount : filesystemMounts.split("\\R")) {
            if (!mount.isEmpty()) {
                result.add(FilesystemMountSpec.parse(mount).encode());
            }
        }
        if (result.isEmpty()) {
            return new String[]{new FilesystemMountSpec("/", hostRoot, FilesystemMountSpec.Type.BIND, null, false).encode()};
        }
        return result.toArray(String[]::new);
    }

    /// Validates configured mount paths through the Truffle environment.
    private static void validateFilesystemMounts(
            TruffleLanguage.Env env,
            String @Unmodifiable [] filesystemMounts) {
        for (String mount : filesystemMounts) {
            FilesystemMountSpec spec = FilesystemMountSpec.parse(mount);
            try {
                env.getPublicTruffleFile(spec.hostPath());
            } catch (IllegalArgumentException exception) {
                throw new RiscVException("Filesystem mount source is invalid for "
                        + spec.guestPath() + ": " + spec.hostPath(), exception);
            }
        }
    }

    /// Normalizes an absolute Linux guest mount point.
    private static String normalizeMountGuestPath(String guestPath) {
        if (!guestPath.startsWith("/") || guestPath.indexOf('\\') >= 0 || guestPath.indexOf(':') >= 0) {
            throw new RiscVException("Filesystem mount guest path must use absolute Linux syntax: " + guestPath);
        }

        ArrayList<String> segments = new ArrayList<>();
        for (String segment : guestPath.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    throw new RiscVException("Filesystem mount guest path must not escape above `/`: " + guestPath);
                }
                segments.remove(segments.size() - 1);
                continue;
            }
            segments.add(segment);
        }
        return segments.isEmpty() ? "/" : "/" + String.join("/", segments);
    }
}
