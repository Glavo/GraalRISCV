// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.riscv.tests;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNullByDefault;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// Runs built `riscv-tests` ELF files with the JRISC-V command-line entry point.
@DisableCachingByDefault(because = "The task executes simulator acceptance tests and has no reusable outputs.")
@NotNullByDefault
public abstract class RiscVTestsRunTask extends DefaultTask {
    /// The Gradle service used to launch the simulator process.
    private final ExecOperations execOperations;

    /// Creates the task.
    ///
    /// @param execOperations the Gradle execution service
    @Inject
    public RiscVTestsRunTask(ExecOperations execOperations) {
        this.execOperations = execOperations;

        getMaxInstructions().convention("10000000");
        getFilter().convention(".*");
        getJvmArguments().convention(List.of());
    }

    /// Returns the directory containing built riscv-tests ELF files.
    ///
    /// @return the ELF directory property
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getElfDirectory();

    /// Returns the Java runtime classpath used to launch the simulator.
    ///
    /// @return the simulator classpath
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    /// Returns the simulator main class name.
    ///
    /// @return the simulator main class property
    @Input
    public abstract Property<String> getMainClass();

    /// Returns the JVM arguments used to launch the simulator.
    ///
    /// @return the JVM argument list property
    @Input
    public abstract ListProperty<String> getJvmArguments();

    /// Returns the maximum instruction count passed to the simulator.
    ///
    /// @return the maximum instruction count property
    @Input
    public abstract Property<String> getMaxInstructions();

    /// Returns the regular expression used to select ELF files by file name.
    ///
    /// @return the ELF filename filter property
    @Input
    public abstract Property<String> getFilter();

    /// Runs every selected ELF and fails when any guest exits unsuccessfully or writes unexpected output.
    @TaskAction
    public void runTests() {
        File elfDirectory = getElfDirectory().get().getAsFile();
        Pattern filter = Pattern.compile(getFilter().get());
        List<File> elfFiles = findElfFiles(elfDirectory, filter);
        if (elfFiles.isEmpty()) {
            throw new GradleException("No riscv-tests ELF files matched filter " + getFilter().get()
                    + " under " + elfDirectory);
        }

        ArrayList<String> failures = new ArrayList<>();
        for (File elfFile : elfFiles) {
            runElf(elfFile, failures);
        }

        if (!failures.isEmpty()) {
            throw new GradleException("riscv-tests failures:\n" + String.join("\n", failures));
        }

        getLogger().lifecycle("Passed {} riscv-tests ELFs", elfFiles.size());
    }

    /// Finds matching ELF files under the supplied directory.
    ///
    /// @param elfDirectory the root directory to scan
    /// @param filter the filename filter pattern
    /// @return the sorted matching ELF files
    private static List<File> findElfFiles(File elfDirectory, Pattern filter) {
        try (Stream<java.nio.file.Path> stream = Files.walk(elfDirectory.toPath())) {
            return stream
                    .map(java.nio.file.Path::toFile)
                    .filter(file -> file.isFile()
                            && file.getName().endsWith(".elf")
                            && filter.matcher(file.getName()).find())
                    .sorted(Comparator.comparing(File::getName))
                    .toList();
        } catch (IOException exception) {
            throw new GradleException("Failed to scan riscv-tests ELF directory: " + elfDirectory, exception);
        }
    }

    /// Runs one ELF and appends a failure diagnostic when the simulator result is unexpected.
    ///
    /// @param elfFile the ELF file to execute
    /// @param failures the mutable failure list
    private void runElf(File elfFile, ArrayList<String> failures) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ExecResult result = execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.jvmArgs(getJvmArguments().get());
            spec.args("--max-instructions", getMaxInstructions().get(), elfFile.getAbsolutePath());
            spec.setStandardOutput(stdout);
            spec.setErrorOutput(stderr);
            spec.setIgnoreExitValue(true);
        });

        String actualOutput = stdout.toString(StandardCharsets.UTF_8).trim();
        String actualError = stderr.toString(StandardCharsets.UTF_8).trim();
        if (result.getExitValue() != 0 || !actualOutput.isEmpty() || !actualError.isEmpty()) {
            StringBuilder builder = new StringBuilder(elfFile.getName())
                    .append(": exit=")
                    .append(result.getExitValue());
            if (!actualOutput.isEmpty()) {
                builder.append(", stdout=").append(actualOutput);
            }
            if (!actualError.isEmpty()) {
                builder.append(", stderr=").append(actualError);
            }
            failures.add(builder.toString());
        }
    }
}
