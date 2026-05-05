// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.gui.SwingFramebufferBackend;
import org.glavo.riscv.runtime.FramebufferDevice;
import org.glavo.riscv.runtime.FramebufferGeometry;
import org.glavo.riscv.runtime.FramebufferPixelFormat;
import org.glavo.riscv.runtime.GuestCredentials;
import org.glavo.riscv.runtime.fs.FilesystemMountSpec;
import org.glavo.riscv.runtime.fs.FilesystemMountSpec.Type;
import org.glavo.riscv.runtime.net.GuestNetworkMode;
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
            Usage: jriscv [options] <program.elf> [program-args...]
                   jriscv [options] --guest-program <path> [program-args...]

            Options:
              --guest-program <path>    Load the executable from an absolute guest path resolved through --mount.
              --memory-base <address>    Guest memory base address; accepts auto, decimal, or 0x-prefixed hex.
                                          Default is 0; auto infers the base from ELF load segments.
              --memory-size <bytes>      Guest virtual address window size in bytes.
              --page-size <bytes>        Guest base page size in bytes; power of two at least 4096.
              --max-committed-pages <n>  Maximum committed guest base pages; 0 means unlimited.
              --huge-page-size <bytes>   Guest HugeTLB page size in bytes.
              --huge-pages <n>           Guest HugeTLB page pool size.
              --vector-vlen <bits>       Vector register length in bits. Default is 128.
              --max-instructions <count> Maximum guest instruction count; 0 means unlimited.
              --mount <spec>             Mount a host path:
                                          type=bind|tar,src=<path>,dst=<guest>[,readonly|rw][,memory].
              --network <none|host>      Guest Internet socket backend. Default is none.
              --use-host-tty             Try to connect guest /dev/tty to the host controlling terminal.
              --framebuffer <width>x<height>
                                          Open a Swing framebuffer and expose it as guest /dev/fb0.
              --framebuffer-scale <n>     Integer Swing framebuffer scale. Default is 3.
              --root                     Shortcut for --user root --uid 0 --gid 0 --groups 0.
              --user <name>              Guest login name exported in the default environment.
              --uid <id>                 Guest real, effective, and saved uid. Default is 1000.
              --gid <id>                 Guest real, effective, and saved gid. Default is 1000.
              --groups <ids|none>        Comma-separated supplementary guest gids, or none.
              --home <path>              Guest home directory used by the default environment.
              --shell <path>             Guest shell path used by the default environment.
              --debug-fixed-clock-nanos <nanos>
                                          Fixed epoch nanoseconds for deterministic guest time.
              --debug-trace-compilation  Accepted for compatibility; currently ignored.
              --trace                    Print guest instruction trace lines.
              -h, --help                 Print this help message.
            """;

    /// The process exit code used for command-line usage errors.
    private static final int USAGE_ERROR = 2;

    /// The process exit code used for host-side execution failures.
    private static final int HOST_ERROR = 1;

    /// The default scale used for CLI-created Swing framebuffer windows.
    private static final int DEFAULT_FRAMEBUFFER_SCALE = 3;

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
            byte[] elf = options.hostProgramPath() == null
                    ? new byte[0]
                    : Files.readAllBytes(options.hostProgramPath());
            @Nullable FramebufferDevice framebufferDevice = options.framebuffer() == null
                    ? null
                    : createFramebufferDevice(options.framebuffer());
            @Nullable SwingFramebufferBackend framebufferBackend = null;
            try {
                if (framebufferDevice != null) {
                    framebufferBackend = new SwingFramebufferBackend(
                            framebufferDevice,
                            "JRISC-V /dev/fb0",
                            intOptionOrDefault(options.framebufferScale(), DEFAULT_FRAMEBUFFER_SCALE),
                            SwingFramebufferBackend.DEFAULT_REFRESH_MILLIS);
                    framebufferBackend.open();
                }

                RiscVContext context = createContext(options, in, out, err, framebufferDevice);
                return normalizeExitCode(RiscVEngine.run(elf, context));
            } finally {
                if (framebufferBackend != null) {
                    framebufferBackend.close();
                }
            }
        } catch (IOException exception) {
            errorStream.println("Failed to read ELF file: " + exception.getMessage());
            return HOST_ERROR;
        } catch (RuntimeException exception) {
            errorStream.println("Execution failed: " + exception.getMessage());
            return HOST_ERROR;
        }
    }

    /// Creates the plain Java simulator context used for guest execution.
    private static RiscVContext createContext(
            CliOptions options,
            InputStream in,
            OutputStream out,
            OutputStream err,
            @Nullable FramebufferDevice framebufferDevice) {
        return new RiscVContext(
                in,
                out,
                err,
                longOptionOrDefault(options.memoryBase(), RiscVContext.DEFAULT_MEMORY_BASE),
                longOptionOrDefault(options.memorySize(), RiscVContext.DEFAULT_MEMORY_SIZE),
                longOptionOrDefault(options.pageSize(), RiscVContext.DEFAULT_PAGE_SIZE),
                longOptionOrDefault(options.maxCommittedPages(), RiscVContext.DEFAULT_MAX_COMMITTED_PAGES),
                longOptionOrDefault(options.hugePageSize(), RiscVContext.DEFAULT_HUGE_PAGE_SIZE),
                longOptionOrDefault(options.hugePages(), RiscVContext.DEFAULT_HUGE_PAGES),
                longOptionOrDefault(options.vectorVlen(), RiscVContext.DEFAULT_VECTOR_VLEN),
                longOptionOrDefault(options.maxInstructions(), 0L),
                options.trace(),
                RiscVContext.timeSourceFromDebugFixedClockNanos(
                        longOptionOrDefault(options.debugFixedClockNanos(), RiscVContext.HOST_CLOCK_NANOS)),
                ".",
                encodeMounts(options.mounts()),
                options.guestProgramPath() == null ? "" : options.guestProgramPath(),
                options.useHostTty(),
                options.guestUserName(),
                longOptionOrDefault(options.guestUid(), GuestCredentials.DEFAULT_USER_ID),
                longOptionOrDefault(options.guestGid(), GuestCredentials.DEFAULT_GROUP_ID),
                options.guestGroups() == null ? "" : options.guestGroups(),
                options.guestHome(),
                options.guestShell(),
                options.applicationArguments(),
                framebufferDevice,
                options.networkMode().backend());
    }

    /// Parses command-line arguments.
    private static CliOptions parseArguments(String[] args, OutputStream out, PrintStream err) {
        @Nullable String memoryBase = null;
        @Nullable String memorySize = null;
        @Nullable String pageSize = null;
        @Nullable String maxCommittedPages = null;
        @Nullable String hugePageSize = null;
        @Nullable String hugePages = null;
        @Nullable String vectorVlen = null;
        @Nullable String maxInstructions = null;
        @Nullable String debugFixedClockNanos = null;
        @Nullable String guestUserName = null;
        @Nullable String guestUid = null;
        @Nullable String guestGid = null;
        @Nullable String guestGroups = null;
        @Nullable String guestHome = null;
        @Nullable String guestShell = null;
        @Nullable String framebuffer = null;
        @Nullable String framebufferScale = null;
        GuestNetworkMode networkMode = GuestNetworkMode.NONE;
        ArrayList<MountOption> mounts = new ArrayList<>();
        boolean useHostTty = false;
        boolean debugTraceCompilation = false;
        boolean trace = false;
        @Nullable String programPath = null;
        @Nullable Path hostProgramPath = null;
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
            if (parseOptions && "--use-host-tty".equals(argument)) {
                useHostTty = true;
                continue;
            }
            if (parseOptions && "--network".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --network.");
                    printUsage(err);
                    return CliOptions.error();
                }
                @Nullable GuestNetworkMode parsedNetworkMode = parseNetworkModeOption(args[index], err);
                if (parsedNetworkMode == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                networkMode = parsedNetworkMode;
                continue;
            }
            if (parseOptions && "--root".equals(argument)) {
                guestUserName = "root";
                guestUid = "0";
                guestGid = "0";
                guestGroups = "0";
                continue;
            }
            if (parseOptions && "--user".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --user.");
                    printUsage(err);
                    return CliOptions.error();
                }
                guestUserName = parseGuestUserNameOption("--user", args[index], err);
                if (guestUserName == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--uid".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --uid.");
                    printUsage(err);
                    return CliOptions.error();
                }
                guestUid = parseLinuxIdOption("--uid", args[index], err);
                if (guestUid == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--gid".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --gid.");
                    printUsage(err);
                    return CliOptions.error();
                }
                guestGid = parseLinuxIdOption("--gid", args[index], err);
                if (guestGid == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--groups".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --groups.");
                    printUsage(err);
                    return CliOptions.error();
                }
                guestGroups = parseGuestGroupsOption("--groups", args[index], err);
                if (guestGroups == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--home".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --home.");
                    printUsage(err);
                    return CliOptions.error();
                }
                guestHome = normalizeGuestPath("--home", args[index], err);
                if (guestHome == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--shell".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --shell.");
                    printUsage(err);
                    return CliOptions.error();
                }
                guestShell = normalizeGuestPath("--shell", args[index], err);
                if (guestShell == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--guest-program".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --guest-program.");
                    printUsage(err);
                    return CliOptions.error();
                }
                if (programPath != null) {
                    err.println("Executable path was specified more than once.");
                    printUsage(err);
                    return CliOptions.error();
                }
                @Nullable String guestProgramPath = normalizeGuestPath("--guest-program", args[index], err);
                if (guestProgramPath == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                programPath = guestProgramPath;
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
            if (parseOptions && "--page-size".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --page-size.");
                    printUsage(err);
                    return CliOptions.error();
                }
                pageSize = parseLongOption("--page-size", args[index], err);
                if (pageSize == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--max-committed-pages".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --max-committed-pages.");
                    printUsage(err);
                    return CliOptions.error();
                }
                maxCommittedPages = parseLongOption("--max-committed-pages", args[index], err);
                if (maxCommittedPages == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--huge-page-size".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --huge-page-size.");
                    printUsage(err);
                    return CliOptions.error();
                }
                hugePageSize = parseLongOption("--huge-page-size", args[index], err);
                if (hugePageSize == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--huge-pages".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --huge-pages.");
                    printUsage(err);
                    return CliOptions.error();
                }
                hugePages = parseLongOption("--huge-pages", args[index], err);
                if (hugePages == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--vector-vlen".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --vector-vlen.");
                    printUsage(err);
                    return CliOptions.error();
                }
                vectorVlen = parseLongOption("--vector-vlen", args[index], err);
                if (vectorVlen == null) {
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
            if (parseOptions && "--mount".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --mount.");
                    printUsage(err);
                    return CliOptions.error();
                }
                @Nullable MountOption mount = parseMountOption(args[index], err);
                if (mount == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                mounts.add(mount);
                continue;
            }
            if (parseOptions && "--framebuffer".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --framebuffer.");
                    printUsage(err);
                    return CliOptions.error();
                }
                framebuffer = parseFramebufferOption(args[index], err);
                if (framebuffer == null) {
                    printUsage(err);
                    return CliOptions.error();
                }
                continue;
            }
            if (parseOptions && "--framebuffer-scale".equals(argument)) {
                index++;
                if (index >= args.length) {
                    err.println("Missing value for --framebuffer-scale.");
                    printUsage(err);
                    return CliOptions.error();
                }
                framebufferScale = parsePositiveIntOption("--framebuffer-scale", args[index], err);
                if (framebufferScale == null) {
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
            @Nullable Path parsedHostProgramPath = parsePathOption("ELF file", argument, err);
            if (parsedHostProgramPath == null) {
                printUsage(err);
                return CliOptions.error();
            }
            programPath = argument;
            hostProgramPath = parsedHostProgramPath;
        }

        if (programPath == null) {
            err.println("Missing ELF file.");
            printUsage(err);
            return CliOptions.error();
        }
        if (framebuffer == null && framebufferScale != null) {
            err.println("--framebuffer-scale requires --framebuffer.");
            printUsage(err);
            return CliOptions.error();
        }

        if (hostProgramPath != null && mounts.stream().noneMatch(mount -> "/".equals(mount.guestPath()))) {
            mounts.add(new MountOption("/", defaultRootMount(hostProgramPath), Type.BIND, null, false));
        }
        return CliOptions.execute(
                programPath,
                hostProgramPath,
                programArguments,
                memoryBase,
                memorySize,
                pageSize,
                maxCommittedPages,
                hugePageSize,
                hugePages,
                vectorVlen,
                maxInstructions,
                debugFixedClockNanos,
                mounts,
                useHostTty,
                guestUserName,
                guestUid,
                guestGid,
                guestGroups,
                guestHome,
                guestShell,
                framebuffer,
                framebufferScale,
                networkMode,
                debugTraceCompilation,
                trace);
    }

    /// Parses a signed long option and returns its normalized decimal string value.
    private static @Nullable String parseLongOption(String optionName, String value, PrintStream err) {
        if ("--memory-base".equals(optionName) && "auto".equalsIgnoreCase(value)) {
            return Long.toString(RiscVContext.AUTO_MEMORY_BASE);
        }

        try {
            return Long.toString(Long.decode(value));
        } catch (NumberFormatException exception) {
            err.println("Invalid value for " + optionName + ": " + value);
            return null;
        }
    }

    /// Returns a parsed normalized long option value or the supplied default.
    private static long longOptionOrDefault(@Nullable String value, long defaultValue) {
        return value == null ? defaultValue : Long.parseLong(value);
    }

    /// Returns a parsed normalized integer option value or the supplied default.
    private static int intOptionOrDefault(@Nullable String value, int defaultValue) {
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    /// Parses a positive integer option and returns its normalized decimal string value.
    private static @Nullable String parsePositiveIntOption(String optionName, String value, PrintStream err) {
        try {
            long parsed = Long.decode(value);
            if (parsed <= 0 || parsed > Integer.MAX_VALUE) {
                err.println("Invalid value for " + optionName + ": " + value);
                return null;
            }
            return Integer.toString((int) parsed);
        } catch (NumberFormatException exception) {
            err.println("Invalid value for " + optionName + ": " + value);
            return null;
        }
    }

    /// Parses a framebuffer geometry option and returns a normalized `widthxheight` value.
    private static @Nullable String parseFramebufferOption(String value, PrintStream err) {
        int separator = value.indexOf('x');
        if (separator < 0) {
            separator = value.indexOf('X');
        }
        if (separator <= 0 || separator >= value.length() - 1
                || value.indexOf('x', separator + 1) >= 0
                || value.indexOf('X', separator + 1) >= 0) {
            err.println("Invalid value for --framebuffer: " + value);
            err.println("Expected --framebuffer <width>x<height>.");
            return null;
        }

        @Nullable String width = parsePositiveIntOption("--framebuffer", value.substring(0, separator), err);
        @Nullable String height = parsePositiveIntOption("--framebuffer", value.substring(separator + 1), err);
        if (width == null || height == null) {
            return null;
        }
        return width + "x" + height;
    }

    /// Parses a guest networking mode option.
    private static @Nullable GuestNetworkMode parseNetworkModeOption(String value, PrintStream err) {
        try {
            return GuestNetworkMode.parse(value);
        } catch (IllegalArgumentException exception) {
            err.println("Invalid value for --network: " + value);
            err.println("Expected --network <none|host>.");
            return null;
        }
    }

    /// Creates a framebuffer device from a normalized `widthxheight` option value.
    private static FramebufferDevice createFramebufferDevice(String framebuffer) {
        int separator = framebuffer.indexOf('x');
        int width = Integer.parseInt(framebuffer.substring(0, separator));
        int height = Integer.parseInt(framebuffer.substring(separator + 1));
        return new FramebufferDevice(FramebufferGeometry.packed(width, height, FramebufferPixelFormat.XRGB8888));
    }

    /// Parses a Linux uid or gid option and returns its normalized decimal string value.
    private static @Nullable String parseLinuxIdOption(String optionName, String value, PrintStream err) {
        try {
            long id = Long.decode(value);
            GuestCredentials.validateId(optionName, id);
            return Long.toString(id);
        } catch (NumberFormatException | RiscVException exception) {
            err.println("Invalid value for " + optionName + ": " + value);
            return null;
        }
    }

    /// Parses a guest login name option.
    private static @Nullable String parseGuestUserNameOption(String optionName, String value, PrintStream err) {
        try {
            GuestCredentials.validateUserName(optionName, value);
            return value;
        } catch (RiscVException exception) {
            err.println("Invalid value for " + optionName + ": " + value);
            return null;
        }
    }

    /// Parses a supplementary group list option.
    private static @Nullable String parseGuestGroupsOption(String optionName, String value, PrintStream err) {
        try {
            return GuestCredentials.normalizeSupplementaryGroupsOption(value);
        } catch (RiscVException exception) {
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

    /// Parses a filesystem mount option.
    private static @Nullable MountOption parseMountOption(String value, PrintStream err) {
        try {
            FilesystemMountSpec spec = FilesystemMountSpec.parse(value);
            @Nullable Path hostPath = parsePathOption("--mount", spec.hostPath(), err);
            return hostPath == null
                    ? null
                    : new MountOption(spec.guestPath(), hostPath, spec.type(), spec.readOnly(), spec.memory());
        } catch (RiscVException exception) {
            err.println("Invalid value for --mount: " + value);
            err.println(exception.getMessage());
            err.println("Expected --mount type=bind|tar,src=<host-path>,dst=<guest-path>[,readonly|rw][,memory].");
            return null;
        }
    }

    /// Normalizes an absolute Linux guest path.
    private static @Nullable String normalizeGuestPath(String label, String guestPath, PrintStream err) {
        if (!guestPath.startsWith("/")) {
            err.println(label + " must be absolute: " + guestPath);
            return null;
        }
        if (guestPath.indexOf('\\') >= 0 || guestPath.indexOf(':') >= 0) {
            err.println(label + " must use Linux path syntax: " + guestPath);
            return null;
        }

        ArrayList<String> segments = new ArrayList<>();
        for (String segment : guestPath.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    err.println(label + " must not escape above `/`: " + guestPath);
                    return null;
                }
                segments.remove(segments.size() - 1);
                continue;
            }
            segments.add(segment);
        }
        return segments.isEmpty() ? "/" : "/" + String.join("/", segments);
    }

    /// Encodes mount options for the runtime context.
    private static String encodeMounts(MountOption @Unmodifiable [] mounts) {
        StringBuilder builder = new StringBuilder();
        for (MountOption mount : mounts) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(new FilesystemMountSpec(
                    mount.guestPath(),
                    mount.hostPath().toString(),
                    mount.type(),
                    mount.readOnly(),
                    mount.memory()).encode());
        }
        return builder.toString();
    }

    /// Returns the default root mount for a guest program.
    private static Path defaultRootMount(Path programPath) {
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
    /// @param programPath the executable path exposed as `argv[0]`
    /// @param hostProgramPath the host file containing the ELF, or null when `programPath` is read from guest mounts
    /// @param programArguments the guest application arguments after the ELF path
    /// @param memoryBase the optional guest memory base option value
    /// @param memorySize the optional guest memory size option value
    /// @param pageSize the optional guest base page size option value
    /// @param maxCommittedPages the optional committed guest base page limit option value
    /// @param hugePageSize the optional guest HugeTLB page size option value
    /// @param hugePages the optional guest HugeTLB page pool size option value
    /// @param vectorVlen the optional vector register length option value
    /// @param maxInstructions the optional maximum instruction count option value
    /// @param debugFixedClockNanos the optional fixed debug `clock_gettime` nanosecond option value
    /// @param mounts the configured guest filesystem mounts
    /// @param useHostTty whether `/dev/tty` should try to use the host controlling terminal
    /// @param guestUserName the optional guest login name option value
    /// @param guestUid the optional guest uid option value
    /// @param guestGid the optional guest gid option value
    /// @param guestGroups the optional supplementary guest gid list option value
    /// @param guestHome the optional guest home directory option value
    /// @param guestShell the optional guest shell option value
    /// @param framebuffer the optional CLI-created framebuffer geometry option value
    /// @param framebufferScale the optional CLI-created Swing framebuffer scale option value
    /// @param networkMode the guest Internet socket backend mode
    /// @param debugTraceCompilation whether trace compilation diagnostics were requested
    /// @param trace whether instruction tracing is enabled
    @NotNullByDefault
    private record CliOptions(
            CliMode mode,
            String programPath,
            @Nullable Path hostProgramPath,
            String @Unmodifiable [] programArguments,
            @Nullable String memoryBase,
            @Nullable String memorySize,
            @Nullable String pageSize,
            @Nullable String maxCommittedPages,
            @Nullable String hugePageSize,
            @Nullable String hugePages,
            @Nullable String vectorVlen,
            @Nullable String maxInstructions,
            @Nullable String debugFixedClockNanos,
            MountOption @Unmodifiable [] mounts,
            boolean useHostTty,
            @Nullable String guestUserName,
            @Nullable String guestUid,
            @Nullable String guestGid,
            @Nullable String guestGroups,
            @Nullable String guestHome,
            @Nullable String guestShell,
            @Nullable String framebuffer,
            @Nullable String framebufferScale,
            GuestNetworkMode networkMode,
            boolean debugTraceCompilation,
            boolean trace) {
        /// Creates parsed command-line options.
        private CliOptions {
            programArguments = programArguments.clone();
            mounts = mounts.clone();
        }

        /// Returns a copy of the guest arguments after the ELF path.
        @Override
        public String @Unmodifiable [] programArguments() {
            return programArguments.clone();
        }

        /// Returns a copy of the configured filesystem mounts.
        @Override
        public MountOption @Unmodifiable [] mounts() {
            return mounts.clone();
        }

        /// Creates options for printing help.
        static CliOptions help() {
            return new CliOptions(
                    CliMode.HELP,
                    ".",
                    Path.of("."),
                    new String[0],
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new MountOption[0],
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    GuestNetworkMode.NONE,
                    false,
                    false);
        }

        /// Creates options for a usage error.
        static CliOptions error() {
            return new CliOptions(
                    CliMode.ERROR,
                    ".",
                    Path.of("."),
                    new String[0],
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new MountOption[0],
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    GuestNetworkMode.NONE,
                    false,
                    false);
        }

        /// Creates options for executing a guest ELF program.
        static CliOptions execute(
                String programPath,
                @Nullable Path hostProgramPath,
                List<String> programArguments,
                @Nullable String memoryBase,
                @Nullable String memorySize,
                @Nullable String pageSize,
                @Nullable String maxCommittedPages,
                @Nullable String hugePageSize,
                @Nullable String hugePages,
                @Nullable String vectorVlen,
                @Nullable String maxInstructions,
                @Nullable String debugFixedClockNanos,
                List<MountOption> mounts,
                boolean useHostTty,
                @Nullable String guestUserName,
                @Nullable String guestUid,
                @Nullable String guestGid,
                @Nullable String guestGroups,
                @Nullable String guestHome,
                @Nullable String guestShell,
                @Nullable String framebuffer,
                @Nullable String framebufferScale,
                GuestNetworkMode networkMode,
                boolean debugTraceCompilation,
                boolean trace) {
            return new CliOptions(
                    CliMode.EXECUTE,
                    programPath,
                    hostProgramPath,
                    programArguments.toArray(String[]::new),
                    memoryBase,
                    memorySize,
                    pageSize,
                    maxCommittedPages,
                    hugePageSize,
                    hugePages,
                    vectorVlen,
                    maxInstructions,
                    debugFixedClockNanos,
                    mounts.toArray(MountOption[]::new),
                    useHostTty,
                    guestUserName,
                    guestUid,
                    guestGid,
                    guestGroups,
                    guestHome,
                    guestShell,
                    framebuffer,
                    framebufferScale,
                    networkMode,
                    debugTraceCompilation,
                    trace);
        }

        /// Returns the arguments exposed to the guest as `argv`, including `argv[0]`.
        String @Unmodifiable [] applicationArguments() {
            String[] result = new String[programArguments.length + 1];
            result[0] = programPath;
            System.arraycopy(programArguments, 0, result, 1, programArguments.length);
            return result;
        }

        /// Returns the guest executable path when the program is loaded from guest mounts.
        @Nullable String guestProgramPath() {
            return hostProgramPath == null ? programPath : null;
        }
    }

    /// Stores one command-line filesystem mount.
    ///
    /// @param guestPath the absolute guest-visible mount point
    /// @param hostPath the host path backing the mount point
    /// @param type the requested mount type, or `AUTO` to infer it from the host path
    /// @param readOnly the explicit read-only setting, or null to use the mount-type default
    /// @param memory whether a tar mount should be loaded into process memory
    @NotNullByDefault
    private record MountOption(
            String guestPath,
            Path hostPath,
            Type type,
            @Nullable Boolean readOnly,
            boolean memory) {
    }
}
