// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.MappedRegionCache;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.nodes.RiscVRootNode;
import org.glavo.riscv.parser.ElfImage;
import org.glavo.riscv.parser.ElfLoader;
import org.glavo.riscv.runtime.TimeSource;
import org.glavo.riscv.runtime.VectorUnit;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.jetbrains.annotations.NotNullByDefault;

/// Registers the RISC-V ELF simulator as a Truffle language.
@TruffleLanguage.Registration(
        id = RiscVLanguage.ID,
        name = "RISC-V",
        version = "1.0",
        defaultMimeType = RiscVLanguage.ELF_MIME_TYPE,
        byteMimeTypes = RiscVLanguage.ELF_MIME_TYPE,
        contextPolicy = TruffleLanguage.ContextPolicy.EXCLUSIVE)
@NotNullByDefault
public final class RiscVLanguage extends TruffleLanguage<RiscVContext> {
    /// The Polyglot language id used to evaluate RISC-V ELF byte sources.
    public static final String ID = "riscv";

    /// The MIME type accepted by the language parser.
    public static final String ELF_MIME_TYPE = "application/x-riscv-elf";

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

    /// The sentinel `riscv.debugFixedClockNanos` value that asks the runtime to use the host clock.
    public static final long HOST_CLOCK_NANOS = -1L;

    /// The per-context and per-host-thread sparse memory lookup cache.
    private final ContextThreadLocal<MappedRegionCache> mappedRegionCache;

    /// The `riscv.memoryBase` language option.
    @Option(
            name = "memoryBase",
            help = "Guest memory base address. Use -1 to infer it from ELF load segments. Default: 0.",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE)
    static final OptionKey<Long> MEMORY_BASE = new OptionKey<>(DEFAULT_MEMORY_BASE);

    /// The `riscv.memorySize` language option.
    @Option(
            name = "memorySize",
            help = "Guest virtual address window size in bytes. Default: 4294967296.",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE)
    static final OptionKey<Long> MEMORY_SIZE = new OptionKey<>(DEFAULT_MEMORY_SIZE);

    /// The `riscv.pageSize` language option.
    @Option(
            name = "pageSize",
            help = "Guest base page size in bytes; power of two at least 4096. Default: 4096.",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE)
    static final OptionKey<Long> PAGE_SIZE = new OptionKey<>(DEFAULT_PAGE_SIZE);

    /// The `riscv.maxCommittedPages` language option.
    @Option(
            name = "maxCommittedPages",
            help = "Maximum number of committed guest base pages. Zero means unlimited. Default: 0.",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE)
    static final OptionKey<Long> MAX_COMMITTED_PAGES = new OptionKey<>(DEFAULT_MAX_COMMITTED_PAGES);

    /// The `riscv.hugePageSize` language option.
    @Option(
            name = "hugePageSize",
            help = "Guest HugeTLB page size in bytes. Default: 2097152.",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE)
    static final OptionKey<Long> HUGE_PAGE_SIZE = new OptionKey<>(DEFAULT_HUGE_PAGE_SIZE);

    /// The `riscv.hugePages` language option.
    @Option(
            name = "hugePages",
            help = "Number of guest HugeTLB pages available to MAP_HUGETLB. Default: 0.",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE)
    static final OptionKey<Long> HUGE_PAGES = new OptionKey<>(DEFAULT_HUGE_PAGES);

    /// The `riscv.vectorVlen` language option.
    @Option(
            name = "vectorVlen",
            help = "Vector register length in bits; power of two from 64 through 65536. Default: 128.",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE)
    static final OptionKey<Long> VECTOR_VLEN = new OptionKey<>(DEFAULT_VECTOR_VLEN);

    /// The `riscv.maxInstructions` language option.
    @Option(
            name = "maxInstructions",
            help = "Maximum guest instruction count. Zero means unlimited. Default: 0.",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE)
    static final OptionKey<Long> MAX_INSTRUCTIONS = new OptionKey<>(0L);

    /// The `riscv.trace` language option.
    @Option(
            name = "trace",
            help = "Print guest instruction trace lines to stderr. Default: false.",
            category = OptionCategory.EXPERT,
            stability = OptionStability.STABLE)
    static final OptionKey<Boolean> TRACE = new OptionKey<>(false);

    /// The `riscv.debugFixedClockNanos` language option.
    @Option(
            name = "debugFixedClockNanos",
            help = "Fixed epoch nanoseconds for the guest clock used by clock_gettime. Use -1 to use the host clock. Default: -1.",
            category = OptionCategory.EXPERT,
            stability = OptionStability.STABLE)
    static final OptionKey<Long> DEBUG_FIXED_CLOCK_NANOS = new OptionKey<>(HOST_CLOCK_NANOS);

    /// The `riscv.hostRoot` language option.
    @Option(
            name = "hostRoot",
            help = "Host directory mounted at guest `/` when riscv.mounts is empty. Default: current directory.",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE)
    static final OptionKey<String> HOST_ROOT = new OptionKey<>(".");

    /// The `riscv.mounts` language option.
    @Option(
            name = "mounts",
            help = "Newline-separated guest=host filesystem mounts. Default: use riscv.hostRoot as `/`.",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE)
    static final OptionKey<String> MOUNTS = new OptionKey<>("");

    /// Creates a RISC-V Truffle language instance.
    @SuppressWarnings("deprecation")
    public RiscVLanguage() {
        this.mappedRegionCache = createContextThreadLocal((context, thread) -> new MappedRegionCache());
    }

    /// Creates a context from the current Truffle environment and option values.
    @Override
    protected RiscVContext createContext(Env env) {
        return new RiscVContext(
                env,
                env.getOptions().get(MEMORY_BASE),
                env.getOptions().get(MEMORY_SIZE),
                env.getOptions().get(PAGE_SIZE),
                env.getOptions().get(MAX_COMMITTED_PAGES),
                env.getOptions().get(HUGE_PAGE_SIZE),
                env.getOptions().get(HUGE_PAGES),
                env.getOptions().get(VECTOR_VLEN),
                env.getOptions().get(MAX_INSTRUCTIONS),
                env.getOptions().get(TRACE),
                timeSourceFromDebugFixedClockNanos(env.getOptions().get(DEBUG_FIXED_CLOCK_NANOS)),
                env.getOptions().get(HOST_ROOT),
                env.getOptions().get(MOUNTS),
                mappedRegionCache);
    }

    /// Allows clone-created guest threads to enter the same exclusive language context.
    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    /// Creates the guest time source from the fixed-clock debug option.
    static TimeSource timeSourceFromDebugFixedClockNanos(long debugFixedClockNanos) {
        if (debugFixedClockNanos == HOST_CLOCK_NANOS) {
            return TimeSource.system();
        }
        if (debugFixedClockNanos < HOST_CLOCK_NANOS) {
            throw new RiscVException("riscv.debugFixedClockNanos must be non-negative or -1 for host clocks: "
                    + debugFixedClockNanos);
        }

        return TimeSource.fixedEpochNanoseconds(debugFixedClockNanos);
    }

    /// Parses an ELF byte source into a root call target.
    @Override
    protected CallTarget parse(ParsingRequest request) {
        Source source = request.getSource();
        if (!source.hasBytes()) {
            throw new RiscVException("RISC-V sources must be byte-based ELF inputs");
        }

        ElfImage image = ElfLoader.load(source.getBytes().toByteArray());
        return new RiscVRootNode(this, image).getCallTarget();
    }

    /// Returns descriptors for the language options supported by this language.
    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new RiscVLanguageOptionDescriptors();
    }
}
