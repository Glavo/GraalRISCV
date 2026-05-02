// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.RiscVException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/// Parses and formats guest filesystem mount specifications.
///
/// Mount specs accept the legacy `guest=host` form and the Docker-like
/// `type=...,source=...,target=...` form used by the command-line interface and
/// the `riscv.mounts` language option.
@NotNullByDefault
public record FilesystemMountSpec(
        String guestPath,
        String hostPath,
        Type type,
        @Nullable Boolean readOnly,
        boolean memory) {
    /// Creates a normalized mount specification.
    ///
    /// @param guestPath the absolute guest-visible mount point
    /// @param hostPath the host path backing the mount
    /// @param type the requested mount type, or `AUTO` to infer it from the host path
    /// @param readOnly the explicit read-only setting, or null to use the mount-type default
    /// @param memory whether a tar mount should be loaded into mutable process memory
    public FilesystemMountSpec {
        @Nullable String normalizedGuestPath = GuestFileSystem.normalizeAbsoluteGuestPath(guestPath);
        if (normalizedGuestPath == null) {
            throw new RiscVException("Filesystem mount guest path must use absolute Linux syntax: " + guestPath);
        }
        if (hostPath.isEmpty()) {
            throw new RiscVException("Filesystem mount source must not be empty");
        }
        if (memory && type == Type.BIND) {
            throw new RiscVException("Filesystem mount memory option is only valid for tar mounts");
        }
        if (!memory && type == Type.TAR && Boolean.FALSE.equals(readOnly)) {
            throw new RiscVException("Writable tar mounts require memory=true");
        }

        guestPath = normalizedGuestPath;
        if (memory && type == Type.AUTO) {
            type = Type.TAR;
        }
    }

    /// Parses one mount specification.
    public static FilesystemMountSpec parse(String value) {
        if (value.startsWith("/")) {
            return parseLegacy(value);
        }
        return parseKeyValue(value);
    }

    /// Creates a legacy auto-detected mount specification.
    public static FilesystemMountSpec legacy(String guestPath, String hostPath) {
        return new FilesystemMountSpec(guestPath, hostPath, Type.AUTO, null, false);
    }

    /// Returns this mount specification encoded as one key-value line.
    public String encode() {
        StringBuilder builder = new StringBuilder();
        if (type != Type.AUTO) {
            builder.append("type=").append(type.optionName()).append(',');
        }
        builder.append("source=").append(encodeValue(hostPath));
        builder.append(",target=").append(encodeValue(guestPath));
        if (readOnly != null) {
            builder.append(",readonly=").append(readOnly);
        }
        if (memory) {
            builder.append(",memory=true");
        }
        return builder.toString();
    }

    /// Returns the read-only flag to apply to a bind mount.
    public boolean bindReadOnly() {
        return Boolean.TRUE.equals(readOnly);
    }

    /// Returns the read-only flag to apply to a tar mount.
    public boolean tarReadOnly() {
        return readOnly == null || readOnly;
    }

    /// Parses a legacy `guest=host` mount specification.
    private static FilesystemMountSpec parseLegacy(String value) {
        int separator = value.indexOf('=');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new RiscVException("Invalid filesystem mount entry: " + value);
        }
        return legacy(value.substring(0, separator), value.substring(separator + 1));
    }

    /// Parses a Docker-like key-value mount specification.
    private static FilesystemMountSpec parseKeyValue(String value) {
        @Nullable Type type = null;
        @Nullable String source = null;
        @Nullable String target = null;
        @Nullable Boolean readOnly = null;
        boolean memory = false;

        for (String token : splitTokens(value)) {
            int separator = token.indexOf('=');
            if (separator < 0) {
                String flag = token.toLowerCase();
                switch (flag) {
                    case "readonly", "ro" -> readOnly = mergeReadOnly(readOnly, true, token);
                    case "rw" -> readOnly = mergeReadOnly(readOnly, false, token);
                    case "memory" -> memory = true;
                    default -> throw new RiscVException("Unknown filesystem mount flag: " + token);
                }
                continue;
            }

            String key = token.substring(0, separator).toLowerCase();
            String optionValue = decodeValue(token.substring(separator + 1));
            switch (key) {
                case "type" -> {
                    if (type != null) {
                        throw new RiscVException("Duplicate filesystem mount type option");
                    }
                    type = parseType(optionValue);
                }
                case "source", "src" -> {
                    if (source != null) {
                        throw new RiscVException("Duplicate filesystem mount source option");
                    }
                    source = optionValue;
                }
                case "target", "destination", "dst" -> {
                    if (target != null) {
                        throw new RiscVException("Duplicate filesystem mount target option");
                    }
                    target = optionValue;
                }
                case "readonly", "ro" -> readOnly = mergeReadOnly(readOnly, parseBoolean(key, optionValue), token);
                case "rw" -> readOnly = mergeReadOnly(readOnly, !parseBoolean(key, optionValue), token);
                case "memory" -> memory = parseBoolean(key, optionValue);
                default -> throw new RiscVException("Unknown filesystem mount option: " + token.substring(0, separator));
            }
        }

        if (source == null) {
            throw new RiscVException("Filesystem mount source option is required");
        }
        if (target == null) {
            throw new RiscVException("Filesystem mount target option is required");
        }
        return new FilesystemMountSpec(target, source, type == null ? Type.AUTO : type, readOnly, memory);
    }

    /// Splits a key-value mount spec on commas.
    private static String[] splitTokens(String value) {
        ArrayList<String> tokens = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == ',') {
                if (index == start) {
                    throw new RiscVException("Filesystem mount option contains an empty token: " + value);
                }
                tokens.add(value.substring(start, index));
                start = index + 1;
            }
        }
        return tokens.toArray(String[]::new);
    }

    /// Parses a mount type option value.
    private static Type parseType(String value) {
        return switch (value.toLowerCase()) {
            case "bind" -> Type.BIND;
            case "tar" -> Type.TAR;
            default -> throw new RiscVException("Unsupported filesystem mount type: " + value);
        };
    }

    /// Parses a boolean mount option value.
    private static boolean parseBoolean(String key, String value) {
        return switch (value.toLowerCase()) {
            case "true", "1", "yes" -> true;
            case "false", "0", "no" -> false;
            default -> throw new RiscVException("Invalid boolean value for filesystem mount option " + key + ": " + value);
        };
    }

    /// Merges a read-only option and rejects contradictory flags.
    private static Boolean mergeReadOnly(@Nullable Boolean current, boolean value, String token) {
        if (current != null && current != value) {
            throw new RiscVException("Conflicting filesystem mount read-only option: " + token);
        }
        return value;
    }

    /// Encodes one key-value component.
    private static String encodeValue(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '%' || ch == ',' || ch == '=' || ch == '\n' || ch == '\r') {
                builder.append('%');
                String hex = Integer.toHexString(ch).toUpperCase();
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    /// Decodes one key-value component.
    private static String decodeValue(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch != '%' || index + 2 >= value.length()) {
                builder.append(ch);
                continue;
            }

            int high = Character.digit(value.charAt(index + 1), 16);
            int low = Character.digit(value.charAt(index + 2), 16);
            if (high < 0 || low < 0) {
                builder.append(ch);
                continue;
            }
            builder.append((char) ((high << 4) | low));
            index += 2;
        }
        return builder.toString();
    }

    /// Identifies the requested mount backend.
    public enum Type {
        /// Infer the mount type from the host path.
        AUTO("auto"),

        /// Mount a host directory or file directly.
        BIND("bind"),

        /// Mount a tar archive.
        TAR("tar");

        /// The command-line option spelling for this type.
        private final String optionName;

        /// Creates a mount type.
        Type(String optionName) {
            this.optionName = optionName;
        }

        /// Returns the command-line option spelling for this type.
        public String optionName() {
            return optionName;
        }
    }
}
