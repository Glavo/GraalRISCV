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
package org.glavo.go;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/// Builds a static `linux/riscv64` executable from a Go module.
@NotNullByDefault
public abstract class RiscVGoBuildTask extends DefaultTask {
    /// The Gradle service used to execute the Go compiler process.
    private final ExecOperations execOperations;

    /// Creates the Go build task and sets RISC-V build defaults.
    ///
    /// @param execOperations the Gradle execution service
    @Inject
    public RiscVGoBuildTask(ExecOperations execOperations) {
        this.execOperations = execOperations;

        getGoOS().convention("linux");
        getGoArch().convention("riscv64");
        getGoRiscV64().convention("rva20u64");
        getAdditionalBuildArguments().convention(List.of("-trimpath"));
    }

    /// Returns the Go executable file.
    ///
    /// @return the Go executable file property
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getGoExecutable();

    /// Returns the Go module directory to build.
    ///
    /// @return the module directory property
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getModuleDirectory();

    /// Returns the generated RISC-V executable.
    ///
    /// @return the output file property
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /// Returns the Go build cache directory.
    ///
    /// @return the Go build cache directory property
    @LocalState
    public abstract DirectoryProperty getBuildCacheDirectory();

    /// Returns the Go module cache directory.
    ///
    /// @return the Go module cache directory property
    @LocalState
    public abstract DirectoryProperty getModuleCacheDirectory();

    /// Returns the target Go operating system.
    ///
    /// @return the GOOS property
    @Input
    public abstract Property<String> getGoOS();

    /// Returns the target Go architecture.
    ///
    /// @return the GOARCH property
    @Input
    public abstract Property<String> getGoArch();

    /// Returns the target Go RISC-V profile.
    ///
    /// @return the GORISCV64 property
    @Input
    public abstract Property<String> getGoRiscV64();

    /// Returns additional arguments passed after `go build`.
    ///
    /// @return the additional build argument list property
    @Input
    public abstract ListProperty<String> getAdditionalBuildArguments();

    /// Invokes `go build` and writes the configured RISC-V executable.
    @TaskAction
    public void build() {
        File outputFile = getOutputFile().get().getAsFile();
        @Nullable File outputParent = outputFile.getParentFile();
        if (outputParent != null && !outputParent.isDirectory() && !outputParent.mkdirs()) {
            throw new GradleException("Failed to create output directory: " + outputParent);
        }

        File buildCache = getBuildCacheDirectory().get().getAsFile();
        File moduleCache = getModuleCacheDirectory().get().getAsFile();
        createDirectory(buildCache);
        createDirectory(moduleCache);

        execOperations.exec(spec -> {
            spec.setExecutable(getGoExecutable().get().getAsFile());
            spec.setWorkingDir(getModuleDirectory().get().getAsFile());
            spec.environment("GOOS", getGoOS().get());
            spec.environment("GOARCH", getGoArch().get());
            spec.environment("GORISCV64", getGoRiscV64().get());
            spec.environment("CGO_ENABLED", "0");
            spec.environment("GOCACHE", buildCache.getAbsolutePath());
            spec.environment("GOMODCACHE", moduleCache.getAbsolutePath());
            spec.args(createBuildArguments(outputFile));
        });
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

    /// Creates the `go build` argument list.
    ///
    /// @param outputFile the output executable file
    /// @return the build arguments
    private List<String> createBuildArguments(File outputFile) {
        ArrayList<String> arguments = new ArrayList<>();
        arguments.add("build");
        arguments.addAll(getAdditionalBuildArguments().get());
        arguments.add("-o");
        arguments.add(outputFile.getAbsolutePath());
        arguments.add(".");
        return arguments;
    }
}
