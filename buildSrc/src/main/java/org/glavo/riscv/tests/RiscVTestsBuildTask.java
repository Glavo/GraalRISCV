// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.riscv.tests;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Builds pinned `riscv-tests` RV64GC user-mode assembly tests with Zig CC without invoking Make.
@DisableCachingByDefault(because = "The generated RISC-V test ELFs are local verification artifacts.")
@NotNullByDefault
public abstract class RiscVTestsBuildTask extends DefaultTask {
    /// The RV64GC user-mode test suites built by default.
    private static final @Unmodifiable List<String> DEFAULT_SUITES = List.of(
            "rv64ui",
            "rv64um",
            "rv64ua",
            "rv64uc",
            "rv64uf",
            "rv64ud"
    );

    /// The default RV64GC user-mode target features enabled with Clang.
    private static final @Unmodifiable List<String> DEFAULT_TARGET_FEATURES = List.of(
            "m",
            "a",
            "f",
            "d",
            "c",
            "zicsr",
            "zifencei"
    );

    /// The Gradle service used to execute Zig compiler processes.
    private final ExecOperations execOperations;

    /// The Gradle service used to clean stale generated ELF files.
    private final FileSystemOperations fileSystemOperations;

    /// Creates the task and sets the default RV64GC compiler shape.
    ///
    /// @param execOperations the Gradle execution service
    /// @param fileSystemOperations the Gradle file-system service
    @Inject
    public RiscVTestsBuildTask(ExecOperations execOperations, FileSystemOperations fileSystemOperations) {
        this.execOperations = execOperations;
        this.fileSystemOperations = fileSystemOperations;

        getSuites().convention(DEFAULT_SUITES);
        getTargetTriple().convention("riscv64-freestanding");
        getEnabledTargetFeatures().convention(DEFAULT_TARGET_FEATURES);
        getAbi().convention("lp64d");
        getCodeModel().convention("medany");
        getAdditionalCompilerArguments().convention(List.of(
                "-O2",
                "-g0",
                "-static",
                "-fvisibility=hidden"
        ));
    }

    /// Returns the Zig executable command or path.
    ///
    /// @return the Zig executable command property
    @Input
    public abstract Property<String> getZigExecutable();

    /// Returns the extracted `riscv-tests` source directory.
    ///
    /// @return the extracted source directory property
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDirectory();

    /// Returns the local test environment directory containing `riscv_test.h` and `link.ld`.
    ///
    /// @return the test environment directory property
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getEnvironmentDirectory();

    /// Returns the directory that receives generated ELF files.
    ///
    /// @return the output directory property
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /// Returns the local Zig cache directory for this build.
    ///
    /// @return the local cache directory property
    @LocalState
    public abstract DirectoryProperty getLocalCacheDirectory();

    /// Returns the global Zig cache directory for this build.
    ///
    /// @return the global cache directory property
    @LocalState
    public abstract DirectoryProperty getGlobalCacheDirectory();

    /// Returns the `riscv-tests` suite names to build.
    ///
    /// @return the suite list property
    @Input
    public abstract ListProperty<String> getSuites();

    /// Returns the Zig target triple passed to `zig cc`.
    ///
    /// @return the target triple property
    @Input
    public abstract Property<String> getTargetTriple();

    /// Returns the RISC-V target features enabled with Clang target-feature options.
    ///
    /// @return the enabled target feature list property
    @Input
    public abstract ListProperty<String> getEnabledTargetFeatures();

    /// Returns the RISC-V ABI passed to `zig cc`.
    ///
    /// @return the RISC-V ABI property
    @Input
    public abstract Property<String> getAbi();

    /// Returns the code model passed to `zig cc`.
    ///
    /// @return the code model property
    @Input
    public abstract Property<String> getCodeModel();

    /// Returns additional compiler and linker arguments appended before the output path.
    ///
    /// @return the additional compiler argument list property
    @Input
    public abstract ListProperty<String> getAdditionalCompilerArguments();

    /// Builds all configured RV64GC `p` tests.
    @TaskAction
    public void build() {
        File sourceDirectory = getSourceDirectory().get().getAsFile();
        File environmentDirectory = getEnvironmentDirectory().get().getAsFile();
        File outputDirectory = getOutputDirectory().get().getAsFile();
        File localCache = getLocalCacheDirectory().get().getAsFile();
        File globalCache = getGlobalCacheDirectory().get().getAsFile();

        cleanOutputDirectory(outputDirectory);
        createDirectory(outputDirectory);
        createDirectory(localCache);
        createDirectory(globalCache);

        File isaDirectory = requireDirectory(sourceDirectory.toPath().resolve("isa").toFile(), "riscv-tests ISA");
        File macrosDirectory = requireDirectory(
                isaDirectory.toPath().resolve("macros/scalar").toFile(),
                "scalar macros"
        );
        File linkerScript = requireFile(environmentDirectory.toPath().resolve("link.ld").toFile(), "linker script");
        requireFile(environmentDirectory.toPath().resolve("riscv_test.h").toFile(), "riscv_test.h");
        requireFile(macrosDirectory.toPath().resolve("test_macros.h").toFile(), "test_macros.h");

        int builtTests = 0;
        for (String suite : getSuites().get()) {
            File suiteDirectory = requireDirectory(isaDirectory.toPath().resolve(suite).toFile(), suite + " suite");
            File makefrag = requireFile(suiteDirectory.toPath().resolve("Makefrag").toFile(), suite + " Makefrag");
            List<String> testNames = readSuiteTestNames(makefrag.toPath(), suite);
            for (String testName : testNames) {
                File sourceFile = requireFile(suiteDirectory.toPath().resolve(testName + ".S").toFile(),
                        suite + " test source");
                File outputFile = outputDirectory.toPath().resolve(suite + "-p-" + testName + ".elf").toFile();
                compileTest(
                        sourceFile,
                        outputFile,
                        environmentDirectory,
                        macrosDirectory,
                        linkerScript,
                        localCache,
                        globalCache
                );
                builtTests++;
            }
        }

        getLogger().lifecycle("Built {} riscv-tests ELFs under {}", builtTests, outputDirectory);
    }

    /// Creates a directory if it does not already exist.
    ///
    /// @param directory the directory to create
    /// @throws GradleException if the directory cannot be created
    private static void createDirectory(File directory) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new GradleException("Failed to create directory: " + directory);
        }
    }

    /// Deletes stale generated files while keeping the output directory itself stable for Windows file locking.
    ///
    /// @param outputDirectory the output directory to clean
    private void cleanOutputDirectory(File outputDirectory) {
        if (!outputDirectory.isDirectory()) {
            return;
        }

        File[] children = outputDirectory.listFiles();
        if (children == null) {
            throw new GradleException("Failed to list riscv-tests output directory: " + outputDirectory);
        }
        for (File child : children) {
            fileSystemOperations.delete(spec -> spec.delete(child));
        }
    }

    /// Requires an existing directory.
    ///
    /// @param directory the directory to check
    /// @param description the directory description for diagnostics
    /// @return the checked directory
    /// @throws GradleException if the directory does not exist
    private static File requireDirectory(File directory, String description) {
        if (!directory.isDirectory()) {
            throw new GradleException("Missing " + description + " directory: " + directory);
        }
        return directory;
    }

    /// Requires an existing file.
    ///
    /// @param file the file to check
    /// @param description the file description for diagnostics
    /// @return the checked file
    /// @throws GradleException if the file does not exist
    private static File requireFile(File file, String description) {
        if (!file.isFile()) {
            throw new GradleException("Missing " + description + ": " + file);
        }
        return file;
    }

    /// Reads the scalar test list from a suite `Makefrag`.
    ///
    /// @param makefrag the suite `Makefrag` path
    /// @param suite the suite name
    /// @return the scalar test source base names
    /// @throws GradleException if the suite test list cannot be read
    private static List<String> readSuiteTestNames(Path makefrag, String suite) {
        String variableName = suite + "_sc_tests";
        ArrayList<String> testNames = new ArrayList<>();
        boolean readingVariable = false;

        try {
            for (String rawLine : Files.readAllLines(makefrag, StandardCharsets.UTF_8)) {
                @Nullable String chunk = null;
                String line = removeMakeComment(rawLine).trim();
                if (readingVariable) {
                    chunk = line;
                } else if (line.startsWith(variableName)) {
                    int equalsIndex = line.indexOf('=');
                    if (equalsIndex < 0) {
                        throw new GradleException("Malformed " + variableName + " assignment in " + makefrag);
                    }
                    chunk = line.substring(equalsIndex + 1).trim();
                    readingVariable = true;
                }

                if (chunk == null) {
                    continue;
                }

                boolean continues = chunk.endsWith("\\");
                if (continues) {
                    chunk = chunk.substring(0, chunk.length() - 1).trim();
                }
                appendTestNames(testNames, chunk);
                if (!continues) {
                    break;
                }
            }
        } catch (IOException exception) {
            throw new GradleException("Failed to read riscv-tests Makefrag: " + makefrag, exception);
        }

        if (testNames.isEmpty()) {
            throw new GradleException("No scalar tests were found in " + makefrag);
        }
        return testNames;
    }

    /// Removes a Make comment from one line.
    ///
    /// @param line the raw line
    /// @return the line before the first comment marker
    private static String removeMakeComment(String line) {
        int commentIndex = line.indexOf('#');
        return commentIndex >= 0 ? line.substring(0, commentIndex) : line;
    }

    /// Appends whitespace-separated test names from one Make variable chunk.
    ///
    /// @param testNames the mutable test name list
    /// @param chunk the Make variable chunk
    private static void appendTestNames(ArrayList<String> testNames, String chunk) {
        if (chunk.isEmpty()) {
            return;
        }
        for (String token : chunk.split("\\s+")) {
            if (!token.isEmpty()) {
                testNames.add(token);
            }
        }
    }

    /// Compiles one `riscv-tests` assembly source into an ELF.
    ///
    /// @param sourceFile the assembly source file
    /// @param outputFile the generated ELF file
    /// @param environmentDirectory the local `riscv_test.h` directory
    /// @param macrosDirectory the upstream scalar macro directory
    /// @param linkerScript the local linker script
    /// @param localCache the Zig local cache directory
    /// @param globalCache the Zig global cache directory
    private void compileTest(
            File sourceFile,
            File outputFile,
            File environmentDirectory,
            File macrosDirectory,
            File linkerScript,
            File localCache,
            File globalCache
    ) {
        @Nullable File outputParent = outputFile.getParentFile();
        if (outputParent != null) {
            createDirectory(outputParent);
        }

        execOperations.exec(spec -> {
            spec.setExecutable(getZigExecutable().get());
            spec.environment("ZIG_LOCAL_CACHE_DIR", localCache.getAbsolutePath());
            spec.environment("ZIG_GLOBAL_CACHE_DIR", globalCache.getAbsolutePath());
            spec.args(createCompilerArguments(
                    sourceFile,
                    outputFile,
                    environmentDirectory,
                    macrosDirectory,
                    linkerScript
            ));
        });
    }

    /// Creates the `zig cc` argument list for one `riscv-tests` source.
    ///
    /// @param sourceFile the assembly source file
    /// @param outputFile the generated ELF file
    /// @param environmentDirectory the local `riscv_test.h` directory
    /// @param macrosDirectory the upstream scalar macro directory
    /// @param linkerScript the local linker script
    /// @return the compiler arguments
    private List<String> createCompilerArguments(
            File sourceFile,
            File outputFile,
            File environmentDirectory,
            File macrosDirectory,
            File linkerScript
    ) {
        ArrayList<String> arguments = new ArrayList<>();
        arguments.add("cc");
        arguments.add("--target=" + getTargetTriple().get());
        for (String feature : getEnabledTargetFeatures().get()) {
            arguments.add("-Xclang");
            arguments.add("-target-feature");
            arguments.add("-Xclang");
            arguments.add(normalizeTargetFeature(feature));
        }
        arguments.add("-mabi=" + getAbi().get());
        arguments.add("-mcmodel=" + getCodeModel().get());
        arguments.add("-fno-sanitize=undefined");
        arguments.add("-nostdlib");
        arguments.add("-ffreestanding");
        arguments.add("-fno-builtin");
        arguments.add("-fno-pic");
        arguments.add("-fno-pie");
        arguments.add("-fno-stack-protector");
        arguments.add("-fno-asynchronous-unwind-tables");
        arguments.add("-Wl,--build-id=none");
        arguments.add("-Wl,-T," + linkerScript.getAbsolutePath());
        arguments.add("-I");
        arguments.add(environmentDirectory.getAbsolutePath());
        arguments.add("-I");
        arguments.add(macrosDirectory.getAbsolutePath());
        arguments.addAll(getAdditionalCompilerArguments().get());
        arguments.add("-o");
        arguments.add(outputFile.getAbsolutePath());
        arguments.add(sourceFile.getAbsolutePath());
        return arguments;
    }

    /// Normalizes a RISC-V target feature for Clang.
    ///
    /// @param feature the configured feature name
    /// @return the feature with an explicit enable or disable prefix
    private static String normalizeTargetFeature(String feature) {
        if (feature.startsWith("+") || feature.startsWith("-")) {
            return feature;
        }
        return "+" + feature;
    }
}
