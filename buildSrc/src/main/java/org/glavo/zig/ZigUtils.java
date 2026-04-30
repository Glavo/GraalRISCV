// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.zig;

import kala.compress.archivers.ArchiveEntry;
import kala.compress.archivers.tar.TarArchiveEntry;
import kala.compress.archivers.tar.TarArchiveInputStream;
import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveInputStream;
import kala.compress.utils.IOUtils;
import org.gradle.api.GradleException;
import org.jetbrains.annotations.NotNullByDefault;
import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/// Utility methods for locating and naming Zig toolchain artifacts.
@NotNullByDefault
public final class ZigUtils {

    /// The Zig compiler version used by this build.
    public static final String ZIG_VERSION = "0.16.0";

    /// Prevents instantiation.
    private ZigUtils() {
    }

    /// Returns {@code true} when the current host OS is Windows.
    public static boolean isWindowsHost() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /// Detects the Zig OS identifier for the current host.
    ///
    /// @return the Zig OS string, e.g. {@code "windows"}, {@code "linux"}, {@code "macos"}
    /// @throws GradleException if the host OS is not supported
    public static String detectZigHostOs() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "windows";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            return "macos";
        } else if (osName.contains("linux")) {
            return "linux";
        } else if (osName.contains("freebsd")) {
            return "freebsd";
        } else if (osName.contains("openbsd")) {
            return "openbsd";
        } else if (osName.contains("netbsd")) {
            return "netbsd";
        } else {
            throw new GradleException("Unsupported Zig host OS: " + System.getProperty("os.name"));
        }
    }

    /// Detects the Zig architecture identifier for the current host.
    ///
    /// @return the Zig architecture string, e.g. {@code "x86_64"}, {@code "aarch64"}
    /// @throws GradleException if the host architecture is not supported
    public static String detectZigHostArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        return switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            case "x86", "i386", "i686" -> "x86";
            case "riscv64" -> "riscv64";
            case "loongarch64" -> "loongarch64";
            case "s390x" -> "s390x";
            case "ppc64le" -> "powerpc64le";
            default -> throw new GradleException("Unsupported Zig host architecture: " + System.getProperty("os.arch"));
        };
    }

    /// Returns the archive base name for the Zig distribution, e.g. {@code "zig-x86_64-linux-0.16.0"}.
    ///
    /// @return the archive base name without file extension
    public static String getZigArchiveBaseName() {
        return "zig-" + detectZigHostArch() + "-" + detectZigHostOs() + "-" + ZIG_VERSION;
    }

    /// Returns the archive file name for the Zig distribution, including extension.
    ///
    /// @return the archive file name, e.g. {@code "zig-x86_64-linux-0.16.0.tar.xz"}
    public static String getZigArchiveName() {
        String ext = isWindowsHost() ? "zip" : "tar.xz";
        return getZigArchiveBaseName() + "." + ext;
    }

    /// Returns the name of the Zig executable file for the current host OS.
    ///
    /// @return {@code "zig.exe"} on Windows, {@code "zig"} elsewhere
    public static String getZigExecutableName() {
        return isWindowsHost() ? "zig.exe" : "zig";
    }

    /// Returns the Zig executable path inside an extracted Zig installation.
    ///
    /// @param installDirectory the directory that contains the extracted Zig archive root
    /// @return the expected Zig executable file
    public static File getZigExecutableFile(File installDirectory) {
        return installDirectory.toPath()
                .resolve(getZigArchiveBaseName())
                .resolve(getZigExecutableName())
                .toFile();
    }

    /// Extracts the current host Zig archive into the given destination directory.
    ///
    /// @param archiveFile the downloaded Zig archive
    /// @param destinationDirectory the target directory for extracted files
    /// @throws GradleException if the archive cannot be extracted or the Zig executable is missing
    public static void extractZigArchive(File archiveFile, File destinationDirectory) {
        try {
            Files.createDirectories(destinationDirectory.toPath());
            if (isWindowsHost()) {
                extractZipArchive(archiveFile.toPath(), destinationDirectory.toPath());
            } else {
                extractTarXzArchive(archiveFile.toPath(), destinationDirectory.toPath());
            }

            File executable = getZigExecutableFile(destinationDirectory);
            if (!executable.isFile()) {
                throw new GradleException("Extracted Zig executable was not found: " + executable);
            }
            if (!isWindowsHost() && !executable.setExecutable(true, false)) {
                throw new GradleException("Failed to mark Zig executable as executable: " + executable);
            }
        } catch (IOException e) {
            throw new GradleException("Failed to extract Zig archive: " + archiveFile, e);
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

    /// Extracts a TAR.XZ archive with Kala Compress.
    ///
    /// @param archiveFile the TAR.XZ archive path
    /// @param destinationDirectory the destination directory
    /// @throws IOException if archive I/O fails
    private static void extractTarXzArchive(Path archiveFile, Path destinationDirectory) throws IOException {
        try (InputStream fileInput = new BufferedInputStream(Files.newInputStream(archiveFile));
             XZInputStream xzInput = new XZInputStream(fileInput);
             TarArchiveInputStream archiveInput = new TarArchiveInputStream(xzInput)) {
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

        Path parent = target.getParent();
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
        Path target = linkPath.getParent().resolve(linkTarget).normalize();
        Path normalizedDestination = destinationDirectory.toAbsolutePath().normalize();
        if (!target.startsWith(normalizedDestination)) {
            throw new IOException("Archive symbolic link escapes destination: " + linkTarget);
        }

        Path parent = linkPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
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
