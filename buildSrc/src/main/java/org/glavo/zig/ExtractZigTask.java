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
package org.glavo.zig;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;

/// Extracts the configured Zig archive into a versioned toolchain directory.
@CacheableTask
@NotNullByDefault
public abstract class ExtractZigTask extends DefaultTask {
    /// The Gradle service used to delete stale extracted output before extraction.
    private final FileSystemOperations fileSystemOperations;

    /// Creates the Zig extraction task.
    ///
    /// @param fileSystemOperations the Gradle file-system service
    @Inject
    public ExtractZigTask(FileSystemOperations fileSystemOperations) {
        this.fileSystemOperations = fileSystemOperations;
    }

    /// Returns the downloaded Zig archive file.
    ///
    /// @return the archive file property
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArchiveFile();

    /// Returns the extracted Zig distribution root directory.
    ///
    /// @return the extracted Zig root directory property
    @OutputDirectory
    public abstract DirectoryProperty getInstallDirectory();

    /// Extracts the archive into the configured output directory.
    @TaskAction
    public void extract() {
        File installDirectory = getInstallDirectory().get().getAsFile();
        @Nullable File parentDirectory = installDirectory.getParentFile();
        if (parentDirectory == null) {
            throw new GradleException("Zig install directory has no parent: " + installDirectory);
        }

        fileSystemOperations.delete(spec -> spec.delete(installDirectory));
        if (!parentDirectory.isDirectory() && !parentDirectory.mkdirs()) {
            throw new GradleException("Failed to create Zig toolchain directory: " + parentDirectory);
        }

        ZigUtils.extractZigArchive(getArchiveFile().get().getAsFile(), parentDirectory);

        File executable = new File(installDirectory, ZigUtils.getZigExecutableName());
        if (!executable.isFile()) {
            throw new GradleException("Extracted Zig executable was not found: " + executable);
        }
    }
}
