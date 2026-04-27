package org.glavo.riscv;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/// Provides the command-line entry point for executing RISC-V ELF programs.
@NotNullByDefault
public final class Main {
    /// The usage text printed for invalid arguments or `--help`.
    private static final String USAGE = """
            Usage: graalriscv [options] <program.elf>

            Options:
              --memory-size <bytes>      Guest memory size in bytes.
              --max-instructions <count> Maximum guest instruction count; 0 means unlimited.
              --trace                    Print guest instruction trace lines.
              -h, --help                 Print this help message.
            """;

    /// The process exit code used for command-line usage errors.
    private static final int USAGE_ERROR = 2;

    /// The process exit code used for host-side execution failures.
    private static final int HOST_ERROR = 1;

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
        Context.Builder builder = Context.newBuilder(RiscVLanguage.ID)
                .in(in)
                .out(out)
                .err(err)
                .option("engine.WarnInterpreterOnly", "false");

        if (options.memorySize() != null) {
            builder.option("riscv.memorySize", options.memorySize());
        }
        if (options.maxInstructions() != null) {
            builder.option("riscv.maxInstructions", options.maxInstructions());
        }
        if (options.trace()) {
            builder.option("riscv.trace", "true");
        }

        return builder.build();
    }

    /// Parses command-line arguments.
    private static CliOptions parseArguments(String[] args, OutputStream out, PrintStream err) {
        @Nullable String memorySize = null;
        @Nullable String maxInstructions = null;
        boolean trace = false;
        @Nullable Path programPath = null;
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
            if (parseOptions && "--memory-size".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --memory-size.");
                    printUsage(err);
                    return CliOptions.error();
                }
                memorySize = args[index];
                continue;
            }
            if (parseOptions && "--max-instructions".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --max-instructions.");
                    printUsage(err);
                    return CliOptions.error();
                }
                maxInstructions = args[index];
                continue;
            }
            if (parseOptions && argument.startsWith("-")) {
                err.println("Unknown option: " + argument);
                printUsage(err);
                return CliOptions.error();
            }
            if (programPath != null) {
                err.println("Only one ELF file can be executed at a time.");
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

        return CliOptions.execute(programPath, memorySize, maxInstructions, trace);
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
    @NotNullByDefault
    private record CliOptions(
            /// The requested CLI action.
            CliMode mode,

            /// The guest ELF path to execute.
            Path programPath,

            /// The optional guest memory size option value.
            @Nullable String memorySize,

            /// The optional maximum instruction count option value.
            @Nullable String maxInstructions,

            /// Whether instruction tracing is enabled.
            boolean trace) {
        /// Creates options for printing help.
        static CliOptions help() {
            return new CliOptions(CliMode.HELP, Path.of("."), null, null, false);
        }

        /// Creates options for a usage error.
        static CliOptions error() {
            return new CliOptions(CliMode.ERROR, Path.of("."), null, null, false);
        }

        /// Creates options for executing a guest ELF program.
        static CliOptions execute(
                Path programPath,
                @Nullable String memorySize,
                @Nullable String maxInstructions,
                boolean trace) {
            return new CliOptions(CliMode.EXECUTE, programPath, memorySize, maxInstructions, trace);
        }
    }
}
