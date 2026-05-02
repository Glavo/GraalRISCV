// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.RiscVException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;

/// Stores the Linux user and group identity exposed to a guest process.
///
/// @param userName the login name exposed through the default guest environment
/// @param realUserId the real Linux uid returned by identity syscalls and auxv
/// @param effectiveUserId the effective Linux uid returned by identity syscalls and auxv
/// @param savedUserId the saved Linux uid returned by `getresuid`
/// @param realGroupId the real Linux gid returned by identity syscalls and auxv
/// @param effectiveGroupId the effective Linux gid returned by identity syscalls and auxv
/// @param savedGroupId the saved Linux gid returned by `getresgid`
/// @param supplementaryGroups the supplementary Linux gids returned by `getgroups`
/// @param homeDirectory the absolute guest home directory used by the default environment
/// @param shell the absolute guest shell path used by the default environment
@NotNullByDefault
public record GuestCredentials(
        String userName,
        long realUserId,
        long effectiveUserId,
        long savedUserId,
        long realGroupId,
        long effectiveGroupId,
        long savedGroupId,
        long @Unmodifiable [] supplementaryGroups,
        String homeDirectory,
        String shell) {
    /// The default guest login name.
    public static final String DEFAULT_USER_NAME = "user";

    /// The default guest uid.
    public static final long DEFAULT_USER_ID = 1000;

    /// The default guest gid.
    public static final long DEFAULT_GROUP_ID = 1000;

    /// The default guest shell path.
    public static final String DEFAULT_SHELL = "/bin/sh";

    /// The maximum Linux uid or gid value accepted by this runtime.
    public static final long MAX_ID = 0xffff_ffffL;

    /// Creates guest credentials after validating and defensively copying mutable inputs.
    public GuestCredentials {
        validateUserName("userName", userName);
        validateId("realUserId", realUserId);
        validateId("effectiveUserId", effectiveUserId);
        validateId("savedUserId", savedUserId);
        validateId("realGroupId", realGroupId);
        validateId("effectiveGroupId", effectiveGroupId);
        validateId("savedGroupId", savedGroupId);
        supplementaryGroups = supplementaryGroups.clone();
        for (long group : supplementaryGroups) {
            validateId("supplementaryGroups", group);
        }
        homeDirectory = normalizeGuestPath("homeDirectory", homeDirectory);
        shell = normalizeGuestPath("shell", shell);
    }

    /// Returns the default non-root guest identity.
    public static GuestCredentials defaultUser() {
        return of(DEFAULT_USER_NAME, DEFAULT_USER_ID, DEFAULT_GROUP_ID, "", "", DEFAULT_SHELL);
    }

    /// Creates guest credentials from user-facing option values.
    public static GuestCredentials of(
            String userName,
            long userId,
            long groupId,
            String supplementaryGroups,
            String homeDirectory,
            String shell) {
        validateUserName("userName", userName);
        validateId("userId", userId);
        validateId("groupId", groupId);
        long[] groups = parseSupplementaryGroups(supplementaryGroups, groupId);
        String resolvedHomeDirectory = homeDirectory.isEmpty() ? defaultHomeDirectory(userName) : homeDirectory;
        return new GuestCredentials(
                userName,
                userId,
                userId,
                userId,
                groupId,
                groupId,
                groupId,
                groups,
                resolvedHomeDirectory,
                shell);
    }

    /// Returns a copy of the configured supplementary group list.
    @Override
    public long @Unmodifiable [] supplementaryGroups() {
        return supplementaryGroups.clone();
    }

    /// Returns the number of supplementary groups.
    public int supplementaryGroupCount() {
        return supplementaryGroups.length;
    }

    /// Returns the supplementary group at the requested zero-based index.
    public long supplementaryGroupAt(int index) {
        return supplementaryGroups[index];
    }

    /// Returns the default environment exposed to an initial guest process.
    public String @Unmodifiable [] initialEnvironment() {
        return new String[]{
                "LANG=C",
                "PATH=/usr/bin:/bin",
                "PWD=/",
                "USER=" + userName,
                "LOGNAME=" + userName,
                "HOME=" + homeDirectory,
                "SHELL=" + shell,
                "TERM=xterm-256color"
        };
    }

    /// Converts a Linux uid or gid to the unsigned 32-bit integer stored in guest structs.
    public static int idToInt(long id) {
        validateId("id", id);
        return (int) id;
    }

    /// Validates and normalizes a comma-separated supplementary group list.
    public static String normalizeSupplementaryGroupsOption(String value) {
        if (value.isEmpty() || "none".equals(value)) {
            return value;
        }

        long[] groups = parseSupplementaryGroups(value, DEFAULT_GROUP_ID);
        StringBuilder builder = new StringBuilder();
        for (long group : groups) {
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(group);
        }
        return builder.toString();
    }

    /// Validates one Linux uid or gid value.
    public static void validateId(String optionName, long value) {
        if (value < 0 || value > MAX_ID) {
            throw new RiscVException(optionName + " must be between 0 and " + MAX_ID + ": " + value);
        }
    }

    /// Validates one guest login name.
    public static void validateUserName(String optionName, String value) {
        if (value.isEmpty()) {
            throw new RiscVException(optionName + " must not be empty");
        }
        if (".".equals(value) || "..".equals(value)) {
            throw new RiscVException(optionName + " must not be `.` or `..`");
        }
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch <= 0x20 || ch == ':' || ch == '/' || ch == '\\' || ch == 0x7f) {
                throw new RiscVException(optionName + " contains an invalid character: " + value);
            }
        }
    }

    /// Returns the default home directory for a login name.
    private static String defaultHomeDirectory(String userName) {
        return "root".equals(userName) ? "/root" : "/home/" + userName;
    }

    /// Parses supplementary groups, defaulting to the primary gid when no group option was supplied.
    private static long @Unmodifiable [] parseSupplementaryGroups(String value, long defaultGroupId) {
        if (value.isEmpty()) {
            return new long[]{defaultGroupId};
        }
        if ("none".equals(value)) {
            return new long[0];
        }

        ArrayList<Long> groups = new ArrayList<>();
        for (String item : value.split(",")) {
            if (item.isEmpty()) {
                throw new RiscVException("supplementaryGroups contains an empty entry: " + value);
            }
            try {
                long group = Long.decode(item);
                validateId("supplementaryGroups", group);
                groups.add(group);
            } catch (NumberFormatException exception) {
                throw new RiscVException("supplementaryGroups contains an invalid gid: " + item, exception);
            }
        }

        long[] result = new long[groups.size()];
        for (int index = 0; index < groups.size(); index++) {
            result[index] = groups.get(index);
        }
        return result;
    }

    /// Normalizes an absolute Linux guest path.
    private static String normalizeGuestPath(String optionName, String guestPath) {
        if (!guestPath.startsWith("/") || guestPath.indexOf('\\') >= 0 || guestPath.indexOf(':') >= 0) {
            throw new RiscVException(optionName + " must use absolute Linux path syntax: " + guestPath);
        }

        ArrayList<String> segments = new ArrayList<>();
        for (String segment : guestPath.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    throw new RiscVException(optionName + " must not escape above `/`: " + guestPath);
                }
                segments.remove(segments.size() - 1);
                continue;
            }
            segments.add(segment);
        }
        return segments.isEmpty() ? "/" : "/" + String.join("/", segments);
    }
}
