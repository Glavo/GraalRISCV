package org.glavo.riscv;

import org.glavo.riscv.exception.RiscVException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.IOAccess;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Provides the command-line entry point for executing RISC-V ELF programs.
@NotNullByDefault
public final class Main {
    /// The usage text printed for invalid arguments or `--help`.
    private static final String USAGE = """
            Usage: graalriscv [options] <program.elf> [program-args...]

            Options:
              --memory-base <address>    Guest memory base address; accepts auto, decimal, or 0x-prefixed hex.
              --memory-size <bytes>      Guest memory size in bytes.
              --max-instructions <count> Maximum guest instruction count; 0 means unlimited.
              --host-root <path>         Host directory exposed to sandboxed guest file syscalls.
              --debug-fixed-clock-nanos <nanos>
                                          Fixed epoch nanoseconds for deterministic guest time.
              --debug-trace-compilation  Print Truffle compilation diagnostics with synchronous debug compilation.
              --trace                    Print guest instruction trace lines.
              -h, --help                 Print this help message.
            """;

    /// The process exit code used for command-line usage errors.
    private static final int USAGE_ERROR = 2;

    /// The process exit code used for host-side execution failures.
    private static final int HOST_ERROR = 1;

    /// The Truffle system property that controls attach-library diagnostics.
    private static final String ATTACH_LIBRARY_FAILURE_ACTION_PROPERTY = "polyglotimpl.AttachLibraryFailureAction";

    /// The default attach-library diagnostic behavior used by the CLI.
    private static final String DEFAULT_ATTACH_LIBRARY_FAILURE_ACTION = "ignore";

    /// Prevents construction of this utility class.
    private Main() {
    }

    /// Runs the CLI and terminates the host process with the guest exit code.
    public static void main(String[] args) {
        System.exit(run(args, System.in, System.out, System.err));
    }

    /// Runs the CLI using supplied host streams and returns the process exit code.
    public static int run(String[] args, InputStream in, OutputStream out, OutputStream err) {
        PrintStream errorStream = err instanceof PrintStream printStream ? printStream : new PrintStream(err, true);
        CliOptions options = parseArguments(args, out, errorStream);
        if (options.mode() == CliMode.HELP) {
            return 0;
        }
        if (options.mode() == CliMode.ERROR) {
            return USAGE_ERROR;
        }

        try {
            byte[] elf = Files.readAllBytes(options.programPath());
            try (Context context = createContext(options, in, out, err)) {
                Source source = Source.newBuilder(
                                RiscVLanguage.ID,
                                ByteSequence.create(elf),
                                options.programPath().toString())
                        .mimeType(RiscVLanguage.ELF_MIME_TYPE)
                        .build();
                Value value = context.eval(source);
                return normalizeExitCode(value.asLong());
            }
        } catch (IOException exception) {
            errorStream.println("Failed to read ELF file: " + exception.getMessage());
            return HOST_ERROR;
        } catch (RuntimeException exception) {
            errorStream.println("Execution failed: " + exception.getMessage());
            return HOST_ERROR;
        }
    }

    /// Creates the Polyglot context used for guest execution.
    private static Context createContext(CliOptions options, InputStream in, OutputStream out, OutputStream err) {
        configurePolyglotDefaults();

        Context.Builder builder = Context.newBuilder(RiscVLanguage.ID)
                .in(in)
                .out(out)
                .err(err)
                .allowIO(IOAccess.ALL)
                .allowCreateThread(true)
                .arguments(RiscVLanguage.ID, options.applicationArguments())
                .option("engine.WarnInterpreterOnly", "false");

        if (options.memoryBase() != null) {
            builder.option("riscv.memoryBase", options.memoryBase());
        }
        if (options.memorySize() != null) {
            builder.option("riscv.memorySize", options.memorySize());
        }
        if (options.maxInstructions() != null) {
            builder.option("riscv.maxInstructions", options.maxInstructions());
        }
        if (options.debugFixedClockNanos() != null) {
            builder.option("riscv.debugFixedClockNanos", options.debugFixedClockNanos());
        }
        if (options.debugTraceCompilation()) {
            builder.allowExperimentalOptions(true)
                    .option("engine.Compilation", "true")
                    .option("engine.BackgroundCompilation", "false")
                    .option("engine.FirstTierCompilationThreshold", "10000")
                    .option("engine.OSRCompilationThreshold", "10000")
                    .option("engine.TraceCompilation", "true");
        }
        builder.option("riscv.hostRoot", options.hostRoot().toString());
        if (options.trace()) {
            builder.option("riscv.trace", "true");
        }

        return builder.build();
    }

    /// Applies CLI defaults for Polyglot runtime behavior without overriding user-supplied properties.
    private static void configurePolyglotDefaults() {
        if (System.getProperty(ATTACH_LIBRARY_FAILURE_ACTION_PROPERTY) == null) {
            System.setProperty(ATTACH_LIBRARY_FAILURE_ACTION_PROPERTY, DEFAULT_ATTACH_LIBRARY_FAILURE_ACTION);
        }
    }

    /// Parses command-line arguments.
    private static CliOptions parseArguments(String[] args, OutputStream out, PrintStream err) {
        @Nullable String memoryBase = null;
        @Nullable String memorySize = null;
        @Nullable String maxInstructions = null;
        @Nullable String debugFixedClockNanos = null;
        @Nullable Path hostRoot = null;
        boolean debugTraceCompilation = false;
        boolean trace = false;
        @Nullable Path programPath = null;
        ArrayList<String> programArguments = new ArrayList<>();
        boolean parseOptions = true;

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (parseOptions && "--".equals(argument)) {
                parseOptions = false;
                continue;
            }
            if (parseOptions && ("-h".equals(argument) || "--help".equals(argument))) {
                printUsage(out);
                return CliOptions.help();
            }
            if (parseOptions && "--trace".equals(argument)) {
                trace = true;
                continue;
            }
            if (parseOptions && "--debug-trace-compilation".equals(argument)) {
                debugTraceCompilation = true;
                continue;
            }
            if (parseOptions && "--memory-base".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --memory-base.");
                    printUsage(err);
                    return CliOptions.error();
                }
                memoryBase = parseLongOption("--memory-base", args[index], err);
                if (memoryBase == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--memory-size".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --memory-size.");
                    printUsage(err);
                    return CliOptions.error();
                }
                memorySize = parseLongOption("--memory-size", args[index], err);
                if (memorySize == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--max-instructions".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --max-instructions.");
                    printUsage(err);
                    return CliOptions.error();
                }
                maxInstructions = parseLongOption("--max-instructions", args[index], err);
                if (maxInstructions == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--debug-fixed-clock-nanos".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --debug-fixed-clock-nanos.");
                    printUsage(err);
                    return CliOptions.error();
                }
                debugFixedClockNanos = parseLongOption("--debug-fixed-clock-nanos", args[index], err);
                if (debugFixedClockNanos == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--host-root".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --host-root.");
                    printUsage(err);
                    return CliOptions.error();
                }
                hostRoot = parsePathOption("--host-root", args[index], err);
                if (hostRoot == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (programPath != null) {
                programArguments.add(argument);
                continue;
            }
            if (parseOptions && argument.startsWith("-")) {
                err.println("Unknown option: " + argument);
                printUsage(err);
                return CliOptions.error();
            }
            programPath = Path.of(argument);
        }

        if (programPath == null) {
            err.println("Missing ELF file.");
            printUsage(err);
            return CliOptions.error();
        }

        Path resolvedHostRoot = hostRoot != null ? hostRoot : defaultHostRoot(programPath);
        return CliOptions.execute(
                programPath,
                programArguments,
                memoryBase,
                memorySize,
                maxInstructions,
                debugFixedClockNanos,
                resolvedHostRoot,
                debugTraceCompilation,
                trace);
    }

    /// Parses a signed long option and returns its normalized decimal string value.
    private static @Nullable String parseLongOption(String optionName, String value, PrintStream err) {
        if ("--memory-base".equals(optionName) && "auto".equalsIgnoreCase(value)) {
            return Long.toString(RiscVLanguage.AUTO_MEMORY_BASE);
        }

        try {
            return Long.toString(Long.decode(value));
        } catch (NumberFormatException exception) {
            err.println("Invalid value for " + optionName + ": " + value);
            return null;
        }
    }

    /// Parses a path option and returns its normalized absolute path.
    private static @Nullable Path parsePathOption(String optionName, String value, PrintStream err) {
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            err.println("Invalid value for " + optionName + ": " + value);
            return null;
        }
    }

    /// Returns the default host root for a guest program.
    private static Path defaultHostRoot(Path programPath) {
        Path absoluteProgramPath = programPath.toAbsolutePath().normalize();
        Path parent = absoluteProgramPath.getParent();
        return parent != null ? parent : Path.of(".").toAbsolutePath().normalize();
    }

    /// Prints command-line usage.
    private static void printUsage(OutputStream stream) {
        try {
            stream.write(USAGE.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            stream.flush();
        } catch (IOException exception) {
            throw new RiscVException("Failed to write usage text", exception);
        }
    }

    /// Normalizes a guest exit code into a host process exit code.
    private static int normalizeExitCode(long exitCode) {
        if (exitCode < 0 || exitCode > 255) {
            return HOST_ERROR;
        }
        return (int) exitCode;
    }

    /// Identifies the requested CLI action.
    @NotNullByDefault
    private enum CliMode {
        /// Execute a guest ELF program.
        EXECUTE,

        /// Print help and exit successfully.
        HELP,

        /// Report a command-line error.
        ERROR
    }

    /// Stores parsed command-line options.
    ///
    /// @param mode the requested CLI action
    /// @param programPath the guest ELF path to execute
    /// @param programArguments the guest application arguments after the ELF path
    /// @param memoryBase the optional guest memory base option value
    /// @param memorySize the optional guest memory size option value
    /// @param maxInstructions the optional maximum instruction count option value
    /// @param debugFixedClockNanos the optional fixed debug `clock_gettime` nanosecond option value
    /// @param hostRoot the host directory exposed through sandboxed guest file syscalls
    /// @param debugTraceCompilation whether Truffle compilation diagnostics should be enabled
    /// @param trace whether instruction tracing is enabled
    @NotNullByDefault
    private record CliOptions(
            CliMode mode,
            Path programPath,
            String @Unmodifiable [] programArguments,
            @Nullable String memoryBase,
            @Nullable String memorySize,
            @Nullable String maxInstructions,
            @Nullable String debugFixedClockNanos,
            Path hostRoot,
            boolean debugTraceCompilation,
            boolean trace) {
        /// Creates parsed command-line options.
        private CliOptions {
            programArguments = programArguments.clone();
        }

        /// Returns a copy of the guest arguments after the ELF path.
        @Override
        public String @Unmodifiable [] programArguments() {
            return programArguments.clone();
        }

        /// Creates options for printing help.
        static CliOptions help() {
            return new CliOptions(
                    CliMode.HELP,
                    Path.of("."),
                    new String[0],
                    null,
                    null,
                    null,
                    null,
                    Path.of("."),
                    false,
                    false);
        }

        /// Creates options for a usage error.
        static CliOptions error() {
            return new CliOptions(
                    CliMode.ERROR,
                    Path.of("."),
                    new String[0],
                    null,
                    null,
                    null,
                    null,
                    Path.of("."),
                    false,
                    false);
        }

        /// Creates options for executing a guest ELF program.
        static CliOptions execute(
                Path programPath,
                List<String> programArguments,
                @Nullable String memoryBase,
                @Nullable String memorySize,
                @Nullable String maxInstructions,
                @Nullable String debugFixedClockNanos,
                Path hostRoot,
                boolean debugTraceCompilation,
                boolean trace) {
            return new CliOptions(
                    CliMode.EXECUTE,
                    programPath,
                    programArguments.toArray(String[]::new),
                    memoryBase,
                    memorySize,
                    maxInstructions,
                    debugFixedClockNanos,
                    hostRoot,
                    debugTraceCompilation,
                    trace);
        }

        /// Returns the arguments exposed to the guest as `argv`, including `argv[0]`.
        String @Unmodifiable [] applicationArguments() {
            String[] result = new String[programArguments.length + 1];
            result[0] = programPath.toString();
            System.arraycopy(programArguments, 0, result, 1, programArguments.length);
            return result;
        }
    }
}
