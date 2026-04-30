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
