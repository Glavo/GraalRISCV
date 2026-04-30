/*
 * Copyright 2026 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glavo;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNullByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/// Provides shared `zig cc` execution logic for RISC-V example build tasks.
@DisableCachingByDefault(because = "The compiled ELF is a local verification artifact.")
@NotNullByDefault
public abstract class AbstractRiscVZigCCTask extends DefaultTask {

    /// The Gradle service used to execute the Zig compiler process.
    private final ExecOperations execOperations;

    /// Creates the task and sets compiler options common to the example programs.
    ///
    /// @param execOperations the Gradle execution service
    @Inject
    public AbstractRiscVZigCCTask(ExecOperations execOperations) {
        this.execOperations = execOperations;

        getEnabledTargetFeatures().convention(List.of("m", "a", "c"));
        getAbi().convention("lp64");
        getCodeModel().convention("medany");
        getAdditionalCompilerArguments().convention(List.of());
    }

    /// Returns the Zig executable file.
    ///
    /// @return the Zig executable file property
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getZigExecutable();

    /// Returns the C source file to compile.
    ///
    /// @return the source file property
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSourceFile();

    /// Returns the generated ELF output file.
    ///
    /// @return the output file property
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

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

    /// Returns the Zig target triple passed to `zig cc`.
    ///
    /// @return the target triple
    @Input
    public final String getTarget() {
        return targetTriple();
    }

    /// Returns the RISC-V target features enabled with Clang target-feature options.
    ///
    /// @return the enabled target feature list property
    @Input
    public abstract ListProperty<String> getEnabledTargetFeatures();

    /// Returns the RISC-V ABI passed to the compiler.
    ///
    /// @return the ABI property
    @Input
    public abstract Property<String> getAbi();

    /// Returns the code model passed to the compiler.
    ///
    /// @return the code model property
    @Input
    public abstract Property<String> getCodeModel();

    /// Returns additional compiler and linker arguments appended before the output path.
    ///
    /// @return the additional compiler argument list property
    @Input
    public abstract ListProperty<String> getAdditionalCompilerArguments();

    /// Invokes `zig cc` and writes the configured ELF output.
    @TaskAction
    public void compile() {
        File outputFile = getOutputFile().get().getAsFile();
        File outputParent = outputFile.getParentFile();
        if (outputParent != null && !outputParent.isDirectory() && !outputParent.mkdirs()) {
            throw new GradleException("Failed to create output directory: " + outputParent);
        }

        File localCache = getLocalCacheDirectory().get().getAsFile();
        File globalCache = getGlobalCacheDirectory().get().getAsFile();
        createDirectory(localCache);
        createDirectory(globalCache);

        execOperations.exec(spec -> {
            spec.setExecutable(getZigExecutable().get().getAsFile());
            spec.environment("ZIG_LOCAL_CACHE_DIR", localCache.getAbsolutePath());
            spec.environment("ZIG_GLOBAL_CACHE_DIR", globalCache.getAbsolutePath());
            spec.args(createCompilerArguments(outputFile));
        });
    }

    /// Returns the target triple passed to `zig cc`.
    ///
    /// @return the target triple
    protected abstract String targetTriple();

    /// Adds target-kind-specific compiler and linker arguments.
    ///
    /// @param arguments the mutable compiler argument list
    protected abstract void addTargetArguments(ArrayList<String> arguments);

    /// Creates a directory if it does not already exist.
    ///
    /// @param directory the directory to create
    /// @throws GradleException if the directory cannot be created
    private static void createDirectory(File directory) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new GradleException("Failed to create directory: " + directory);
        }
    }

    /// Creates the `zig cc` argument list.
    ///
    /// @param outputFile the output ELF file
    /// @return the compiler arguments
    private List<String> createCompilerArguments(File outputFile) {
        ArrayList<String> arguments = new ArrayList<>();
        arguments.add("cc");
        arguments.add("--target=" + targetTriple());

        for (String feature : getEnabledTargetFeatures().get()) {
            arguments.add("-Xclang");
            arguments.add("-target-feature");
            arguments.add("-Xclang");
            arguments.add(normalizeTargetFeature(feature));
        }

        arguments.add("-mabi=" + getAbi().get());
        arguments.add("-mcmodel=" + getCodeModel().get());
        arguments.add("-fno-sanitize=undefined");
        addTargetArguments(arguments);
        arguments.add("-Wl,--build-id=none");
        arguments.addAll(getAdditionalCompilerArguments().get());
        arguments.add("-o");
        arguments.add(outputFile.getAbsolutePath());
        arguments.add(getSourceFile().get().getAsFile().getAbsolutePath());
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
