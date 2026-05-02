// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.zig;

import de.undercouch.gradle.tasks.download.Download;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/// Registers and configures the managed Zig toolchain used by RISC-V Gradle tasks.
@NotNullByDefault
public final class ZigToolchainSupport {
    /// The project extension name used to store the shared Zig toolchain support instance.
    private static final String EXTENSION_NAME = "graalRiscVZigToolchain";

    /// Prevents instantiation.
    private ZigToolchainSupport() {
    }

    /// Returns the shared Zig toolchain support instance for a project, creating it when needed.
    ///
    /// @param project the Gradle project
    /// @return the shared Zig toolchain support
    public static ZigToolchain getOrCreate(Project project) {
        @Nullable Object existing = project.getExtensions().findByName(EXTENSION_NAME);
        if (existing instanceof ZigToolchain toolchain) {
            return toolchain;
        }

        Provider<RegularFile> archiveFile = project.getLayout()
                .getBuildDirectory()
                .file("downloads/zig/" + ZigUtils.getZigArchiveName());
        Provider<Directory> installDirectory = project.getLayout()
                .getBuildDirectory()
                .dir("tools/zig/" + ZigUtils.getZigArchiveBaseName());
        Provider<RegularFile> executableFile = installDirectory.map(directory ->
                directory.file(ZigUtils.getZigExecutableName()));
        Provider<String> configuredExecutablePath = project.getProviders()
                .gradleProperty("graalriscv.zigExecutable")
                .orElse(project.getProviders().gradleProperty("zigExecutable"))
                .orElse(project.getProviders().environmentVariable("ZIG_EXECUTABLE"));

        TaskProvider<Download> downloadTask = project.getTasks().register("downloadZig", Download.class, task -> {
            task.setGroup("build setup");
            task.setDescription("Downloads Zig " + ZigUtils.ZIG_VERSION + " for the current host platform.");

            task.src("https://ziglang.org/download/" + ZigUtils.ZIG_VERSION + "/" + ZigUtils.getZigArchiveName());
            task.dest(archiveFile.get().getAsFile());
            task.overwrite(false);
            task.tempAndMove(true);
            task.retries(3);
            task.connectTimeout(30_000);
            task.readTimeout(30_000);

            task.doFirst(ignored -> {
                File archive = archiveFile.get().getAsFile();
                @Nullable File parent = archive.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IllegalStateException("Failed to create Zig archive directory: " + parent);
                }
                if (archive.isFile() && archive.length() == 0L) {
                    project.delete(archive);
                }
            });
        });

        TaskProvider<ExtractZigTask> extractTask = project.getTasks()
                .register("extractZig", ExtractZigTask.class, task -> {
                    task.setGroup("build setup");
                    task.setDescription("Extracts the downloaded Zig toolchain.");

                    task.dependsOn(downloadTask);
                    task.getArchiveFile().set(archiveFile);
                    task.getInstallDirectory().set(installDirectory);
                });

        ZigToolchain toolchain = new ZigToolchain(configuredExecutablePath, executableFile, extractTask);
        project.getExtensions().add(EXTENSION_NAME, toolchain);
        return toolchain;
    }

    /// Shared Zig toolchain providers and task configuration helpers.
    @NotNullByDefault
    public static final class ZigToolchain {
        /// The configured Zig executable path from Gradle properties or the environment.
        private final Provider<String> configuredExecutablePath;

        /// The managed Zig executable file produced by the extraction task.
        private final Provider<RegularFile> executableFile;

        /// The task that extracts the managed Zig distribution.
        private final TaskProvider<ExtractZigTask> extractTask;

        /// Creates a shared Zig toolchain descriptor.
        ///
        /// @param configuredExecutablePath the configured executable path provider
        /// @param executableFile the managed executable file provider
        /// @param extractTask the managed extraction task
        private ZigToolchain(
                Provider<String> configuredExecutablePath,
                Provider<RegularFile> executableFile,
                TaskProvider<ExtractZigTask> extractTask
        ) {
            this.configuredExecutablePath = configuredExecutablePath;
            this.executableFile = executableFile;
            this.extractTask = extractTask;
        }

        /// Configures a task's Zig executable command property.
        ///
        /// @param task the task that needs a Zig executable
        /// @param zigExecutable the task property that receives the executable command
        public void configureExecutable(Task task, Property<String> zigExecutable) {
            @Nullable String configuredPath = configuredExecutablePath.getOrNull();
            if (configuredPath != null) {
                zigExecutable.set(configuredPath);
            } else {
                task.dependsOn(extractTask);
                zigExecutable.set(executableFile.map(file -> file.getAsFile().getAbsolutePath()));
            }
        }

        /// Returns the managed or configured Zig executable path provider.
        ///
        /// @return the configured executable path provider
        public Provider<String> configuredExecutablePath() {
            return configuredExecutablePath;
        }

        /// Returns the managed Zig executable file provider.
        ///
        /// @return the managed executable file provider
        public Provider<RegularFile> executableFile() {
            return executableFile;
        }
    }
}
