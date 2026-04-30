// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.constants;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Defines the simulator-visible RVA22U64 profile capability sets.
@NotNullByDefault
public final class Rva22Profile {
    /// The canonical profile name.
    public static final String NAME = "RVA22U64";

    /// The mandatory base ISA for the profile.
    public static final String MANDATORY_BASE = "RV64I";

    /// The mandatory RVA22U64 extensions and named mandatory features.
    public static final @Unmodifiable List<String> MANDATORY_EXTENSIONS = List.of(
            "M",
            "A",
            "F",
            "D",
            "C",
            "Zicsr",
            "Zicntr",
            "Zihpm",
            "Ziccif",
            "Ziccrse",
            "Ziccamoa",
            "Zicclsm",
            "Za64rs",
            "Zihintpause",
            "Zba",
            "Zbb",
            "Zbs",
            "Zic64b",
            "Zicbom",
            "Zicbop",
            "Zicboz",
            "Zfhmin",
            "Zkt");

    /// The supported optional extension groups defined by the RVA22U64 profile.
    public static final @Unmodifiable List<String> OPTIONAL_EXTENSIONS = List.of(
            "Zfh",
            "V",
            "Zkn",
            "Zks");

    /// Extra implemented extensions that remain outside the RVA22U64 mandatory set.
    public static final @Unmodifiable List<String> COMPATIBILITY_EXTENSIONS = List.of(
            "Zifencei",
            "Zfa",
            "Zihintntl");

    /// Linux hwprobe bits for implemented mandatory RVA22U64 features.
    public static final long HWPROBE_MANDATORY_EXTENSIONS =
            RiscVExtensions.HWPROBE_IMA_FD
                    | RiscVExtensions.HWPROBE_IMA_C
                    | RiscVExtensions.HWPROBE_EXT_ZKT
                    | RiscVExtensions.HWPROBE_EXT_ZBA
                    | RiscVExtensions.HWPROBE_EXT_ZBB
                    | RiscVExtensions.HWPROBE_EXT_ZBS
                    | RiscVExtensions.HWPROBE_EXT_ZICBOZ
                    | RiscVExtensions.HWPROBE_EXT_ZFHMIN
                    | RiscVExtensions.HWPROBE_EXT_ZIHINTPAUSE
                    | RiscVExtensions.HWPROBE_EXT_ZICNTR
                    | RiscVExtensions.HWPROBE_EXT_ZIHPM
                    | RiscVExtensions.HWPROBE_EXT_ZICBOM
                    | RiscVExtensions.HWPROBE_EXT_ZAAMO
                    | RiscVExtensions.HWPROBE_EXT_ZALRSC
                    | RiscVExtensions.HWPROBE_EXT_ZICBOP;

    /// Linux hwprobe bits for implemented extensions outside the RVA22U64 mandatory set.
    public static final long HWPROBE_COMPATIBILITY_EXTENSIONS =
            RiscVExtensions.HWPROBE_EXT_ZFA
                    | RiscVExtensions.HWPROBE_EXT_ZIHINTNTL;

    /// Linux hwprobe bits reported by the simulator.
    public static final long HWPROBE_REPORTED_EXTENSIONS =
            HWPROBE_MANDATORY_EXTENSIONS
                    | HWPROBE_COMPATIBILITY_EXTENSIONS;

    /// Prevents construction of this constants class.
    private Rva22Profile() {
    }
}
