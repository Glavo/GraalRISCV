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

import kala.compress.archivers.ArchiveEntry;
import kala.compress.archivers.tar.TarArchiveEntry;
import kala.compress.archivers.tar.TarArchiveInputStream;
import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveInputStream;
import kala.compress.utils.IOUtils;
import org.gradle.api.GradleException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/// Utility methods for locating and naming Go toolchain artifacts.
@NotNullByDefault
public final class GoUtils {

    /// The Go toolchain version used by this build.
    public static final String GO_VERSION = "1.25.9";

    /// Prevents instantiation.
    private GoUtils() {
    }

    /// Returns {@code true} when the current host OS is Windows.
    ///
    /// @return whether the current host is Windows
    public static boolean isWindowsHost() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /// Detects the Go OS identifier for the current host.
    ///
    /// @return the Go OS string, e.g. {@code "windows"}, {@code "linux"}, {@code "darwin"}
    /// @throws GradleException if the host OS is not supported
    public static String detectGoHostOs() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "windows";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            return "darwin";
        } else if (osName.contains("linux")) {
            return "linux";
        } else if (osName.contains("freebsd")) {
            return "freebsd";
        } else {
            throw new GradleException("Unsupported Go host OS: " + System.getProperty("os.name"));
        }
    }

    /// Detects the Go architecture identifier for the current host.
    ///
    /// @return the Go architecture string, e.g. {@code "amd64"}, {@code "arm64"}
    /// @throws GradleException if the host architecture is not supported
    public static String detectGoHostArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        return switch (arch) {
            case "amd64", "x86_64" -> "amd64";
            case "aarch64", "arm64" -> "arm64";
            case "x86", "i386", "i686" -> "386";
            case "riscv64" -> "riscv64";
            case "loongarch64" -> "loong64";
            case "s390x" -> "s390x";
            case "ppc64le" -> "ppc64le";
            default -> throw new GradleException("Unsupported Go host architecture: " + System.getProperty("os.arch"));
        };
    }

    /// Returns the Go distribution name without the archive extension.
    ///
    /// @return the Go distribution name
    public static String getGoDistributionName() {
        return "go" + GO_VERSION + "." + detectGoHostOs() + "-" + detectGoHostArch();
    }

    /// Returns the Go archive file name for the current host.
    ///
    /// @return the archive file name
    public static String getGoArchiveName() {
        return getGoDistributionName() + (isWindowsHost() ? ".zip" : ".tar.gz");
    }

    /// Returns the name of the Go executable file for the current host OS.
    ///
    /// @return {@code "go.exe"} on Windows, {@code "go"} elsewhere
    public static String getGoExecutableName() {
        return isWindowsHost() ? "go.exe" : "go";
    }

    /// Returns the Go executable path inside an extracted Go installation.
    ///
    /// @param installDirectory the directory that contains the extracted {@code go} archive root
    /// @return the expected Go executable file
    public static File getGoExecutableFile(File installDirectory) {
        return installDirectory.toPath()
                .resolve("go")
                .resolve("bin")
                .resolve(getGoExecutableName())
                .toFile();
    }

    /// Extracts the current host Go archive into the given destination directory.
    ///
    /// @param archiveFile the downloaded Go archive
    /// @param destinationDirectory the target directory for extracted files
    /// @throws GradleException if the archive cannot be extracted or the Go executable is missing
    public static void extractGoArchive(File archiveFile, File destinationDirectory) {
        try {
            Files.createDirectories(destinationDirectory.toPath());
            if (isWindowsHost()) {
                extractZipArchive(archiveFile.toPath(), destinationDirectory.toPath());
            } else {
                extractTarGzArchive(archiveFile.toPath(), destinationDirectory.toPath());
            }

            File executable = getGoExecutableFile(destinationDirectory);
            if (!executable.isFile()) {
                throw new GradleException("Extracted Go executable was not found: " + executable);
            }
            if (!isWindowsHost() && !executable.setExecutable(true, false)) {
                throw new GradleException("Failed to mark Go executable as executable: " + executable);
            }
        } catch (IOException e) {
            throw new GradleException("Failed to extract Go archive: " + archiveFile, e);
        }
    }

    /// Extracts a ZIP archive with Kala Compress.
    ///
    /// @param archiveFile the ZIP archive path
    /// @param destinationDirectory the destination directory
    /// @throws IOException if archive I/O fails
    private static void extractZipArchive(Path archiveFile, Path destinationDirectory) throws IOException {
        try (InputStream fileInput = new BufferedInputStream(Files.newInputStream(archiveFile));
             ZipArchiveInputStream archiveInput = new ZipArchiveInputStream(fileInput)) {
            ZipArchiveEntry entry;
            while ((entry = archiveInput.getNextEntry()) != null) {
                if (!archiveInput.canReadEntryData(entry)) {
                    throw new IOException("Unsupported ZIP entry: " + entry.getName());
                }

                extractArchiveEntry(destinationDirectory, entry, archiveInput);
            }
        }
    }

    /// Extracts a TAR.GZ archive with Kala Compress.
    ///
    /// @param archiveFile the TAR.GZ archive path
    /// @param destinationDirectory the destination directory
    /// @throws IOException if archive I/O fails
    private static void extractTarGzArchive(Path archiveFile, Path destinationDirectory) throws IOException {
        try (InputStream fileInput = new BufferedInputStream(Files.newInputStream(archiveFile));
             GZIPInputStream gzipInput = new GZIPInputStream(fileInput);
             TarArchiveInputStream archiveInput = new TarArchiveInputStream(gzipInput)) {
            TarArchiveEntry entry;
            while ((entry = archiveInput.getNextEntry()) != null) {
                if (!archiveInput.canReadEntryData(entry)) {
                    throw new IOException("Unsupported TAR entry: " + entry.getName());
                }

                extractArchiveEntry(destinationDirectory, entry, archiveInput);
            }
        }
    }

    /// Extracts a single archive entry.
    ///
    /// @param destinationDirectory the normalized extraction root
    /// @param entry the archive entry metadata
    /// @param input the archive stream positioned at the entry data
    /// @throws IOException if the entry cannot be written safely
    private static void extractArchiveEntry(Path destinationDirectory, ArchiveEntry entry, InputStream input)
            throws IOException {
        String entryName = entry.getName();
        Path target = resolveArchiveEntryPath(destinationDirectory, entryName);
        if (entry.isDirectory()) {
            Files.createDirectories(target);
            return;
        }

        if (entry instanceof TarArchiveEntry tarEntry) {
            if (tarEntry.isSymbolicLink()) {
                extractSymbolicLink(destinationDirectory, target, tarEntry.getLinkName());
                return;
            }
            if (!tarEntry.isFile()) {
                return;
            }
        } else if (entry instanceof ZipArchiveEntry zipEntry && zipEntry.isUnixSymlink()) {
            throw new IOException("Unsupported ZIP symbolic link entry: " + entryName);
        }

        @Nullable Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
            IOUtils.copy(input, output);
        }
    }

    /// Extracts a TAR symbolic link after checking that its resolved target stays inside the destination.
    ///
    /// @param destinationDirectory the extraction root
    /// @param linkPath the symlink path to create
    /// @param linkTarget the link target stored in the archive
    /// @throws IOException if the symlink escapes the destination or cannot be created
    private static void extractSymbolicLink(Path destinationDirectory, Path linkPath, String linkTarget)
            throws IOException {
        @Nullable Path linkParent = linkPath.getParent();
        if (linkParent == null) {
            throw new IOException("Archive symbolic link has no parent directory: " + linkPath);
        }

        Path target = linkParent.resolve(linkTarget).normalize();
        Path normalizedDestination = destinationDirectory.toAbsolutePath().normalize();
        if (!target.startsWith(normalizedDestination)) {
            throw new IOException("Archive symbolic link escapes destination: " + linkTarget);
        }

        Files.createDirectories(linkParent);
        Files.deleteIfExists(linkPath);
        Files.createSymbolicLink(linkPath, Path.of(linkTarget));
    }

    /// Resolves an archive entry path and rejects path traversal outside the destination.
    ///
    /// @param destinationDirectory the extraction root
    /// @param entryName the raw archive entry name
    /// @return the normalized target path for the archive entry
    /// @throws IOException if the entry path escapes the destination directory
    private static Path resolveArchiveEntryPath(Path destinationDirectory, String entryName) throws IOException {
        Path normalizedDestination = destinationDirectory.toAbsolutePath().normalize();
        Path target = normalizedDestination.resolve(entryName.replace('\\', '/')).normalize();
        if (!target.startsWith(normalizedDestination)) {
            throw new IOException("Archive entry escapes destination: " + entryName);
        }
        return target;
    }
}
