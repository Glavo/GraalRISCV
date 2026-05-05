// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.runtime.GuestCredentials;
import org.glavo.riscv.runtime.net.GuestNetworkBackend;
import org.glavo.riscv.runtime.net.GuestNetworkMode;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests simulator context option normalization.
@NotNullByDefault
public final class RiscVContextTest {
    /// A temporary host root used by filesystem option tests.
    @TempDir
    private Path tempDirectory;

    /// Verifies that default credentials do not parse mounted guest account databases.
    @Test
    public void defaultCredentialsDoNotParseMountedPasswd() throws Exception {
        Path root = writePasswdRoot();

        GuestCredentials credentials = context(root, null).guestCredentials();

        assertNull(credentials.userName());
        assertEquals(1000, credentials.realUserId());
        assertEquals(1000, credentials.realGroupId());
        assertNull(credentials.homeDirectory());
        assertNull(credentials.shell());
        assertArrayEquals(
                new String[]{"LANG=C", "PATH=/usr/bin:/bin", "PWD=/", "TERM=xterm-256color"},
                credentials.initialEnvironment());
    }

    /// Verifies that an explicit guest login name populates account-related environment variables.
    @Test
    public void explicitUserNamePopulatesDefaultEnvironment() throws Exception {
        Path root = writePasswdRoot();

        GuestCredentials credentials = context(root, "cliuser").guestCredentials();

        assertEquals("cliuser", credentials.userName());
        assertEquals("/home/cliuser", credentials.homeDirectory());
        assertEquals(GuestCredentials.DEFAULT_SHELL, credentials.shell());
        assertArrayEquals(
                new String[]{
                        "LANG=C",
                        "PATH=/usr/bin:/bin",
                        "PWD=/",
                        "USER=cliuser",
                        "LOGNAME=cliuser",
                        "HOME=/home/cliuser",
                        "SHELL=" + GuestCredentials.DEFAULT_SHELL,
                        "TERM=xterm-256color"
                },
                credentials.initialEnvironment());
    }

    /// Verifies that explicit guest environment entries override credential-derived defaults.
    @Test
    public void explicitEnvironmentOverridesDefaultEnvironment() throws Exception {
        RiscVContext context = context(writePasswdRoot(), "cliuser", GuestNetworkMode.NONE.backend(), new String[]{
                "DISPLAY=:1",
                "HOME=/tmp",
                "TERM=xterm"
        });

        assertArrayEquals(
                new String[]{
                        "LANG=C",
                        "PATH=/usr/bin:/bin",
                        "PWD=/",
                        "USER=cliuser",
                        "LOGNAME=cliuser",
                        "SHELL=" + GuestCredentials.DEFAULT_SHELL,
                        "DISPLAY=:1",
                        "HOME=/tmp",
                        "TERM=xterm"
                },
                context.initialEnvironment());
    }

    /// Verifies that guest Internet sockets are disabled by default.
    @Test
    public void defaultNetworkBackendIsDisabled() throws Exception {
        assertTrue(!context(writePasswdRoot(), null).networkBackend().enabled());
    }

    /// Verifies that an explicit host network backend is stored in the context.
    @Test
    public void explicitHostNetworkBackendIsEnabled() throws Exception {
        assertTrue(context(writePasswdRoot(), null, GuestNetworkMode.HOST.backend()).networkBackend().enabled());
    }

    /// Creates a context using the supplied host root and optional login name.
    private static RiscVContext context(Path root, @Nullable String userName) {
        return context(root, userName, GuestNetworkMode.NONE.backend());
    }

    /// Creates a context using the supplied host root, login name, and network backend.
    private static RiscVContext context(Path root, @Nullable String userName, GuestNetworkBackend networkBackend) {
        return context(root, userName, networkBackend, new String[0]);
    }

    /// Creates a context using the supplied host root, login name, network backend, and environment overrides.
    private static RiscVContext context(
            Path root,
            @Nullable String userName,
            GuestNetworkBackend networkBackend,
            String @Unmodifiable [] environmentOverrides) {
        return new RiscVContext(
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                new ByteArrayOutputStream(),
                RiscVContext.DEFAULT_MEMORY_BASE,
                RiscVContext.DEFAULT_MEMORY_SIZE,
                RiscVContext.DEFAULT_PAGE_SIZE,
                RiscVContext.DEFAULT_MAX_COMMITTED_PAGES,
                RiscVContext.DEFAULT_HUGE_PAGE_SIZE,
                RiscVContext.DEFAULT_HUGE_PAGES,
                RiscVContext.DEFAULT_VECTOR_VLEN,
                0,
                false,
                RiscVContext.timeSourceFromDebugFixedClockNanos(RiscVContext.HOST_CLOCK_NANOS),
                root.toString(),
                "",
                "",
                false,
                userName,
                GuestCredentials.DEFAULT_USER_ID,
                GuestCredentials.DEFAULT_GROUP_ID,
                "",
                "",
                "",
                new String[]{"program"},
                environmentOverrides,
                null,
                networkBackend);
    }

    /// Creates a host root containing a guest passwd file.
    private Path writePasswdRoot() throws Exception {
        Path root = tempDirectory.resolve("root");
        Files.createDirectories(root.resolve("etc"));
        Files.writeString(
                root.resolve("etc").resolve("passwd"),
                """
                        root:x:0:0:root:/root:/bin/bash
                        imageuser:x:1000:1000:Image User:/users/image:/usr/bin/zsh
                        """,
                StandardCharsets.UTF_8);
        return root;
    }
}
