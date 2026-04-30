// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.zig;

import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.NotNullByDefault;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/// Builds a static `riscv64-linux-musl` ELF from C source files with Zig CC.
@NotNullByDefault
public abstract class RiscVLinuxMuslStaticZigCCTask extends AbstractRiscVZigCCTask {
    /// Creates the static Linux musl Zig CC task and sets Linux target defaults.
    ///
    /// @param execOperations the Gradle execution service
    @Inject
    public RiscVLinuxMuslStaticZigCCTask(ExecOperations execOperations) {
        super(execOperations);

        getEnabledTargetFeatures().convention(List.of("m", "a", "f", "d", "c", "zicsr", "zifencei"));
        getAbi().convention("lp64d");
        getCodeModel().convention("medany");
        getAdditionalCompilerArguments().convention(List.of("-O0", "-g0", "-no-pie"));
    }

    /// Returns the static Linux musl RISC-V target triple.
    ///
    /// @return the target triple
    @Override
    protected String targetTriple() {
        return "riscv64-linux-musl";
    }

    /// Adds static Linux musl compiler and linker arguments.
    ///
    /// @param arguments the mutable compiler argument list
    @Override
    protected void addTargetArguments(ArrayList<String> arguments) {
        arguments.add("-static");
    }
}
