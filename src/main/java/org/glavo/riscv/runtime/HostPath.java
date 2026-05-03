// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Set;

/// Thin host path wrapper used by guest filesystem and syscall code.
@NotNullByDefault
public final class HostPath {
    private final Path path;

    /// Creates a host path wrapper.
    public HostPath(Path path) {
        this.path = path;
    }

    /// Creates a host path wrapper from a string path.
    public static HostPath of(String path) {
        return new HostPath(Path.of(path));
    }

    /// Returns the wrapped Java path.
    public Path path() {
        return path;
    }

    /// Returns an absolute host path.
    public HostPath getAbsoluteFile() {
        return new HostPath(path.toAbsolutePath());
    }

    /// Returns a normalized host path.
    public HostPath normalize() {
        return new HostPath(path.normalize());
    }

    /// Returns a canonical host path.
    public HostPath getCanonicalFile() throws IOException {
        return new HostPath(path.toRealPath());
    }

    /// Returns true when this path starts with the supplied path.
    public boolean startsWith(HostPath other) {
        return path.startsWith(other.path);
    }

    /// Resolves a relative path below this path.
    public HostPath resolve(String other) {
        return new HostPath(path.resolve(other));
    }

    /// Returns the relative path from this path to the supplied path.
    public HostPath relativize(HostPath other) {
        return new HostPath(path.relativize(other.path));
    }

    /// Returns the path string.
    public String getPath() {
        return path.toString();
    }

    /// Returns the final path component.
    public String getName() {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    /// Returns the parent path.
    public @Nullable HostPath getParent() {
        Path parent = path.getParent();
        return parent == null ? null : new HostPath(parent);
    }

    /// Returns true when this path exists.
    public boolean exists() {
        return Files.exists(path);
    }

    /// Returns true when this path is a symbolic link.
    public boolean isSymbolicLink() {
        return Files.isSymbolicLink(path);
    }

    /// Returns true when this path is a directory.
    public boolean isDirectory() {
        return Files.isDirectory(path);
    }

    /// Returns true when this path is a regular file.
    public boolean isRegularFile() {
        return Files.isRegularFile(path);
    }

    /// Returns true when this path is readable.
    public boolean isReadable() {
        return Files.isReadable(path);
    }

    /// Returns true when this path is writable.
    public boolean isWritable() {
        return Files.isWritable(path);
    }

    /// Returns true when this path is executable.
    public boolean isExecutable() {
        return Files.isExecutable(path);
    }

    /// Opens this path for reading.
    public InputStream newInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    /// Opens this path as a seekable byte channel.
    public SeekableByteChannel newByteChannel(Set<? extends OpenOption> options) throws IOException {
        return Files.newByteChannel(path, options);
    }

    /// Lists immediate children.
    public HostPath[] list() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            java.util.ArrayList<HostPath> result = new java.util.ArrayList<>();
            for (Path child : stream) {
                result.add(new HostPath(child));
            }
            return result.toArray(HostPath[]::new);
        }
    }

    /// Creates this directory.
    public void createDirectory() throws IOException {
        Files.createDirectory(path);
    }

    /// Deletes this path.
    public void delete() throws IOException {
        Files.delete(path);
    }

    /// Moves this path.
    public void move(HostPath target, CopyOption... options) throws IOException {
        Files.move(path, target.path, options);
    }

    /// Reads the target of this symbolic link.
    public HostPath readSymbolicLink() throws IOException {
        return new HostPath(Files.readSymbolicLink(path));
    }

    /// Returns this file size.
    public long size() throws IOException {
        return Files.size(path);
    }

    /// Returns whether this path entry exists without following links.
    public boolean entryExists() {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public String toString() {
        return path.toString();
    }
}
