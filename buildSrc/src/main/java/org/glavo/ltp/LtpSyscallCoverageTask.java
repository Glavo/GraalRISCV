// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.ltp;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Generates an implementation coverage report from the LTP syscall runtest list.
@DisableCachingByDefault(because = "This report is a local compatibility investigation artifact.")
@NotNullByDefault
public abstract class LtpSyscallCoverageTask extends DefaultTask {
    private static final Pattern SYS_CONSTANT_PATTERN =
            Pattern.compile("\\bprivate\\s+static\\s+final\\s+int\\s+(SYS_[A-Z0-9_]+)\\s*=\\s*\\d+\\s*;");
    private static final Pattern SYS_CASE_PATTERN = Pattern.compile("\\bcase\\s+(SYS_[A-Z0-9_]+)\\b");
    private static final Pattern NUMBERED_SYSCALL_TEST_PATTERN = Pattern.compile("(.+?[a-z]2)\\d{2}$");

    /// Returns the LTP `runtest/syscalls` file.
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getLtpSyscallsFile();

    /// Returns the simulator Linux syscall implementation source.
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getLinuxGuestSyscallsFile();

    /// Returns the architecture syscall list used to filter LTP testcase families.
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getAbiSyscallsFile();

    /// Returns the generated Markdown report.
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /// Parses LTP syscall tests and writes the coverage report.
    @TaskAction
    public void generate() {
        try {
            Path ltpSyscalls = getLtpSyscallsFile().get().getAsFile().toPath();
            Path linuxSyscalls = getLinuxGuestSyscallsFile().get().getAsFile().toPath();
            Path output = getOutputFile().get().getAsFile().toPath();

            Map<String, TreeSet<String>> testsBySyscall = parseLtpSyscallTests(ltpSyscalls);
            Set<String> implementedSyscalls = parseImplementedSyscalls(linuxSyscalls);
            Set<String> abiSyscalls = getAbiSyscallsFile().isPresent()
                    ? parseAbiSyscalls(getAbiSyscallsFile().get().getAsFile().toPath())
                    : Set.of();
            writeReport(output, testsBySyscall, implementedSyscalls, abiSyscalls);
        } catch (IOException exception) {
            throw new GradleException("Failed to generate LTP syscall coverage report", exception);
        }
    }

    /// Parses the LTP syscall runtest file into inferred syscall names and testcase tags.
    private static Map<String, TreeSet<String>> parseLtpSyscallTests(Path path) throws IOException {
        TreeMap<String, TreeSet<String>> testsBySyscall = new TreeMap<>();
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int commentStart = line.indexOf('#');
            if (commentStart >= 0) {
                line = line.substring(0, commentStart).strip();
            }
            if (line.isEmpty()) {
                continue;
            }

            String[] fields = line.split("\\s+");
            if (fields.length == 0) {
                continue;
            }

            String tag = fields[0];
            String syscall = inferSyscallName(tag);
            if (!syscall.isEmpty()) {
                testsBySyscall.computeIfAbsent(syscall, ignored -> new TreeSet<>()).add(tag);
            }
        }
        return testsBySyscall;
    }

    /// Parses implemented Linux syscall dispatch cases from `LinuxGuestSyscalls`.
    private static Set<String> parseImplementedSyscalls(Path path) throws IOException {
        String source = Files.readString(path, StandardCharsets.UTF_8);
        TreeMap<String, String> constantNames = new TreeMap<>();
        Matcher constantMatcher = SYS_CONSTANT_PATTERN.matcher(source);
        while (constantMatcher.find()) {
            String constant = constantMatcher.group(1);
            constantNames.put(constant, constantToSyscallName(constant));
        }

        TreeSet<String> implemented = new TreeSet<>();
        Matcher caseMatcher = SYS_CASE_PATTERN.matcher(source);
        while (caseMatcher.find()) {
            String constant = caseMatcher.group(1);
            String syscall = constantNames.get(constant);
            if (syscall != null) {
                implemented.add(syscall);
            }
        }
        return implemented;
    }

    /// Parses a LTP architecture syscall list.
    private static Set<String> parseAbiSyscalls(Path path) throws IOException {
        TreeSet<String> syscalls = new TreeSet<>();
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int commentStart = line.indexOf('#');
            if (commentStart >= 0) {
                line = line.substring(0, commentStart).strip();
            }
            if (line.isEmpty()) {
                continue;
            }

            String[] fields = line.split("\\s+");
            if (fields.length >= 2) {
                syscalls.add(normalizeKnownAlias(fields[0]));
            }
        }
        return syscalls;
    }

    /// Writes a Markdown coverage report.
    private static void writeReport(
            Path output,
            Map<String, TreeSet<String>> testsBySyscall,
            Set<String> implementedSyscalls,
            Set<String> abiSyscalls) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        TreeMap<String, TreeSet<String>> abiTestsBySyscall = new TreeMap<>();
        TreeMap<String, TreeSet<String>> unclassifiedTestsBySyscall = new TreeMap<>();
        for (Map.Entry<String, TreeSet<String>> entry : testsBySyscall.entrySet()) {
            if (abiSyscalls.isEmpty() || abiSyscalls.contains(entry.getKey())) {
                abiTestsBySyscall.put(entry.getKey(), entry.getValue());
            } else {
                unclassifiedTestsBySyscall.put(entry.getKey(), entry.getValue());
            }
        }

        ArrayList<String> covered = new ArrayList<>();
        ArrayList<String> missing = new ArrayList<>();
        for (String syscall : abiTestsBySyscall.keySet()) {
            if (implementedSyscalls.contains(syscall)) {
                covered.add(syscall);
            } else {
                missing.add(syscall);
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("# LTP Syscall Coverage\n\n");
        report.append("This report compares syscall names inferred from `runtest/syscalls` with dispatch cases in `LinuxGuestSyscalls`.\n");
        if (abiSyscalls.isEmpty()) {
            report.append("It is a coverage guide, not a behavioral pass/fail result.\n\n");
        } else {
            report.append("The missing list is filtered through the configured architecture syscall list to avoid treating libc wrappers or other architecture-specific LTP families as direct ABI gaps.\n");
            report.append("It is a coverage guide, not a behavioral pass/fail result.\n\n");
        }
        report.append("## Summary\n\n");
        report.append("| Metric | Count |\n");
        report.append("| --- | ---: |\n");
        report.append("| LTP syscall names inferred | ").append(testsBySyscall.size()).append(" |\n");
        report.append("| ABI-filtered LTP names | ").append(abiTestsBySyscall.size()).append(" |\n");
        report.append("| Implemented ABI names covered by LTP | ").append(covered.size()).append(" |\n");
        report.append("| ABI-filtered LTP names without implementation entry | ").append(missing.size()).append(" |\n");
        report.append("| Unclassified or non-ABI LTP names | ").append(unclassifiedTestsBySyscall.size()).append(" |\n");
        report.append("| Implemented dispatch names | ").append(implementedSyscalls.size()).append(" |\n\n");

        appendTable(report, "Missing ABI Implementation Entries", missing, abiTestsBySyscall);
        appendTable(report, "Implemented ABI Entries Covered By LTP", covered, abiTestsBySyscall);

        TreeSet<String> implementedWithoutLtp = new TreeSet<>(implementedSyscalls);
        implementedWithoutLtp.removeAll(abiTestsBySyscall.keySet());
        report.append("## Implemented Dispatch Names Without LTP Syscalls Entry\n\n");
        if (implementedWithoutLtp.isEmpty()) {
            report.append("None.\n");
        } else {
            report.append(String.join(", ", implementedWithoutLtp)).append("\n\n");
        }

        appendTable(
                report,
                "Unclassified Or Non-ABI LTP Test Families",
                new ArrayList<>(unclassifiedTestsBySyscall.keySet()),
                unclassifiedTestsBySyscall);

        Files.writeString(output, report.toString(), StandardCharsets.UTF_8);
    }

    /// Appends one syscall coverage table.
    private static void appendTable(
            StringBuilder report,
            String title,
            ArrayList<String> syscalls,
            Map<String, TreeSet<String>> testsBySyscall) {
        report.append("## ").append(title).append("\n\n");
        if (syscalls.isEmpty()) {
            report.append("None.\n\n");
            return;
        }

        report.append("| Syscall | LTP Tests | Test Tags |\n");
        report.append("| --- | ---: | --- |\n");
        for (String syscall : syscalls) {
            TreeSet<String> tests = testsBySyscall.get(syscall);
            report.append("| `").append(syscall).append("` | ")
                    .append(tests.size())
                    .append(" | ")
                    .append(sampleTags(tests))
                    .append(" |\n");
        }
        report.append('\n');
    }

    /// Returns a compact sample of testcase tags.
    private static String sampleTags(TreeSet<String> tests) {
        final int limit = 12;
        ArrayList<String> tags = new ArrayList<>(limit + 1);
        int index = 0;
        for (String test : tests) {
            if (index >= limit) {
                tags.add("...");
                break;
            }
            tags.add("`" + test + "`");
            index++;
        }
        return String.join(", ", tags);
    }

    /// Infers a syscall name from an LTP testcase tag.
    private static String inferSyscallName(String tag) {
        String normalized = tag.toLowerCase().replace('-', '_');
        boolean removedUnderscoreSuffix = false;
        while (normalized.matches(".*_\\d+$")) {
            normalized = normalized.replaceFirst("_\\d+$", "");
            removedUnderscoreSuffix = true;
        }
        if (removedUnderscoreSuffix) {
            return normalizeKnownAlias(normalized);
        }

        Matcher numberedSyscallMatcher = NUMBERED_SYSCALL_TEST_PATTERN.matcher(normalized);
        if (numberedSyscallMatcher.matches()) {
            return normalizeKnownAlias(numberedSyscallMatcher.group(1));
        }

        return normalizeKnownAlias(normalized.replaceFirst("\\d+$", ""));
    }

    /// Converts a Java syscall constant to a Linux syscall name.
    private static String constantToSyscallName(String constant) {
        String name = constant.substring("SYS_".length()).toLowerCase();
        return normalizeKnownAlias(name);
    }

    /// Normalizes LTP tag families that encode syscall variants in testcase names.
    private static String normalizeKnownAlias(String name) {
        return switch (name) {
            case "epoll_pwait" -> "epoll_pwait";
            case "eventfd" -> "eventfd2";
            case "faccessat2" -> "faccessat2";
            case "fstatat" -> "newfstatat";
            case "fchownat" -> "fchownat";
            case "fgetxattr" -> "fgetxattr";
            case "flistxattr" -> "flistxattr";
            case "fstatfs" -> "fstatfs";
            case "getdents" -> "getdents64";
            case "lgetxattr" -> "lgetxattr";
            case "llistxattr" -> "llistxattr";
            case "newfstatat" -> "newfstatat";
            case "pwritev2" -> "pwritev2";
            case "readlinkat" -> "readlinkat";
            case "renameat2" -> "renameat2";
            case "rt_sigtimedwait" -> "rt_sigtimedwait";
            case "rt_tgsigqueueinfo" -> "rt_tgsigqueueinfo";
            case "sync_file_range" -> "sync_file_range";
            default -> name;
        };
    }
}
