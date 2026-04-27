package org.glavo.riscv;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
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

    /// The default guest memory size in bytes.
    public static final long DEFAULT_MEMORY_SIZE = 128L * 1024L * 1024L;

    /// The sentinel value that asks the runtime to infer the memory base from ELF load segments.
    public static final long AUTO_MEMORY_BASE = -1L;

    /// The `riscv.memoryBase` language option.
    @Option(
            name = "memoryBase",
            help = "Guest memory base address. Use -1 to infer it from ELF load segments. Default: -1.",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE)
    static final OptionKey<Long> MEMORY_BASE = new OptionKey<>(AUTO_MEMORY_BASE);

    /// The `riscv.memorySize` language option.
    @Option(
            name = "memorySize",
            help = "Guest memory size in bytes. Default: 134217728.",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE)
    static final OptionKey<Long> MEMORY_SIZE = new OptionKey<>(DEFAULT_MEMORY_SIZE);

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

    /// Creates a RISC-V Truffle language instance.
    public RiscVLanguage() {
    }

    /// Creates a context from the current Truffle environment and option values.
    @Override
    protected RiscVContext createContext(Env env) {
        return new RiscVContext(
                env,
                env.getOptions().get(MEMORY_BASE),
                env.getOptions().get(MEMORY_SIZE),
                env.getOptions().get(MAX_INSTRUCTIONS),
                env.getOptions().get(TRACE));
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
