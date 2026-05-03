// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.MappedRegionCache;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.runtime.FilesystemMountSpec;
import org.glavo.riscv.runtime.GuestCredentials;
import org.glavo.riscv.runtime.TimeSource;
import org.glavo.riscv.runtime.VectorUnit;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;

/// Stores simulator configuration for one guest execution.
@NotNullByDefault
public final class RiscVContext {
    /// The command-line name used for default guest process metadata.
    public static final String ID = "riscv";

    /// The default guest virtual address window size in bytes.
    public static final long DEFAULT_MEMORY_SIZE = 4L * 1024L * 1024L * 1024L;

    /// The default guest memory base address.
    public static final long DEFAULT_MEMORY_BASE = 0L;

    /// The default guest base page size in bytes.
    public static final long DEFAULT_PAGE_SIZE = Memory.DEFAULT_PAGE_SIZE;

    /// The default committed-page limit, where zero means unlimited.
    public static final long DEFAULT_MAX_COMMITTED_PAGES = Memory.DEFAULT_MAX_COMMITTED_PAGES;

    /// The default HugeTLB page size in bytes.
    public static final long DEFAULT_HUGE_PAGE_SIZE = Memory.DEFAULT_HUGE_PAGE_SIZE;

    /// The default HugeTLB page pool size.
    public static final long DEFAULT_HUGE_PAGES = Memory.DEFAULT_HUGE_PAGES;

    /// The default vector register length in bits.
    public static final long DEFAULT_VECTOR_VLEN = VectorUnit.DEFAULT_VLEN_BITS;

    /// The sentinel value that asks the runtime to infer the memory base from ELF load segments.
    public static final long AUTO_MEMORY_BASE = -1L;

    /// The sentinel fixed-clock value that asks the runtime to use the host clock.
    public static final long HOST_CLOCK_NANOS = -1L;

    /// Host standard input exposed to guest syscalls.
    private final InputStream in;

    /// Host standard output exposed to guest syscalls.
    private final OutputStream out;

    /// Host standard error exposed to guest syscalls.
    private final OutputStream err;

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

    /// The sparse memory lookup cache scoped by the current host thread.
    private final ThreadLocal<MappedRegionCache> mappedRegionCache;

    /// Creates a simulator context.
    public RiscVContext(
            InputStream in,
            OutputStream out,
            OutputStream err,
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
            String @Unmodifiable [] programArguments,
            ThreadLocal<MappedRegionCache> mappedRegionCache) {
        if (memoryBase < 0 && memoryBase != AUTO_MEMORY_BASE) {
            throw new RiscVException("riscv.memoryBase must be non-negative or -1 for auto: " + memoryBase);
        }
        if (memorySize <= 0) {
            throw new RiscVException("riscv.memorySize must be positive: " + memorySize);
        }
        if (memoryBase != AUTO_MEMORY_BASE && memoryBase > Long.MAX_VALUE - memorySize) {
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
        VectorUnit.validateVectorLength((int) vectorVlenBits);
        if (maxInstructions < 0) {
            throw new RiscVException("riscv.maxInstructions must be non-negative: " + maxInstructions);
        }

        String[] parsedFilesystemMounts = parseFilesystemMounts(hostRoot, filesystemMounts);
        validateFilesystemMounts(parsedFilesystemMounts);
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

        this.in = in;
        this.out = out;
        this.err = err;
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
        this.programArguments = programArguments.clone();
        this.mappedRegionCache = mappedRegionCache;
    }

    /// Returns host standard input exposed to guest syscalls.
    public InputStream in() {
        return in;
    }

    /// Returns host standard output exposed to guest syscalls.
    public OutputStream out() {
        return out;
    }

    /// Returns host standard error exposed to guest syscalls.
    public OutputStream err() {
        return err;
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

    /// Returns the sparse memory lookup cache for the current host thread.
    public ThreadLocal<MappedRegionCache> mappedRegionCache() {
        return mappedRegionCache;
    }

    /// Creates the guest time source from the fixed-clock debug option.
    public static TimeSource timeSourceFromDebugFixedClockNanos(long debugFixedClockNanos) {
        if (debugFixedClockNanos == HOST_CLOCK_NANOS) {
            return TimeSource.system();
        }
        if (debugFixedClockNanos < HOST_CLOCK_NANOS) {
            throw new RiscVException("riscv.debugFixedClockNanos must be non-negative or -1 for host clocks: "
                    + debugFixedClockNanos);
        }

        return TimeSource.fixedEpochNanoseconds(debugFixedClockNanos);
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

    /// Validates configured mount host paths.
    private static void validateFilesystemMounts(String @Unmodifiable [] filesystemMounts) {
        for (String mount : filesystemMounts) {
            FilesystemMountSpec spec = FilesystemMountSpec.parse(mount);
            try {
                Path.of(spec.hostPath());
            } catch (InvalidPathException exception) {
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
