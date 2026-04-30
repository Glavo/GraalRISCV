// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.zig;

import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.NotNullByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;

/// Builds a freestanding `riscv64-freestanding` ELF from C source files with Zig CC.
@NotNullByDefault
public abstract class RiscVFreestandingZigCCTask extends AbstractRiscVZigCCTask {
    /// Creates the freestanding Zig CC task.
    ///
    /// @param execOperations the Gradle execution service
    @Inject
    public RiscVFreestandingZigCCTask(ExecOperations execOperations) {
        super(execOperations);
    }

    /// Returns the linker script used for the freestanding ELF image layout.
    ///
    /// @return the linker script file property
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getLinkerScript();

    /// Returns the freestanding RISC-V target triple.
    ///
    /// @return the target triple
    @Override
    protected String targetTriple() {
        return "riscv64-freestanding";
    }

    /// Adds freestanding compiler and linker arguments.
    ///
    /// @param arguments the mutable compiler argument list
    @Override
    protected void addTargetArguments(ArrayList<String> arguments) {
        if (!getLinkerScript().isPresent()) {
            throw new GradleException("A linker script is required for freestanding RISC-V examples.");
        }

        File linkerScript = getLinkerScript().get().getAsFile();
        arguments.add("-nostdlib");
        arguments.add("-ffreestanding");
        arguments.add("-fno-builtin");
        arguments.add("-fno-pic");
        arguments.add("-fno-pie");
        arguments.add("-fno-stack-protector");
        arguments.add("-fno-asynchronous-unwind-tables");
        arguments.add("-Wl,-T," + linkerScript.getAbsolutePath());
    }
}
