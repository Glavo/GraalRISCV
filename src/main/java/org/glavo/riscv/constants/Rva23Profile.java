// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.constants;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Defines the target RVA23U64 profile capability sets without enabling profile reporting.
@NotNullByDefault
public final class Rva23Profile {
    /// The canonical profile name.
    public static final String NAME = "RVA23U64";

    /// The mandatory base ISA for the profile.
    public static final String MANDATORY_BASE = "RV64I";

    /// The mandatory RVA23U64 extensions and named mandatory features.
    public static final @Unmodifiable List<String> MANDATORY_EXTENSIONS = List.of(
            "M",
            "A",
            "F",
            "D",
            "C",
            "B",
            "V",
            "Zicsr",
            "Zicntr",
            "Zihpm",
            "Ziccif",
            "Ziccrse",
            "Ziccamoa",
            "Zicclsm",
            "Za64rs",
            "Zihintpause",
            "Zihintntl",
            "Zba",
            "Zbb",
            "Zbs",
            "Zic64b",
            "Zicbom",
            "Zicbop",
            "Zicboz",
            "Zicond",
            "Zifencei",
            "Zimop",
            "Zca",
            "Zcb",
            "Zcd",
            "Zcmop",
            "Zfa",
            "Zfhmin",
            "Zkt",
            "Zmmul",
            "Zaamo",
            "Zalrsc",
            "Zawrs",
            "Zve32x",
            "Zve32f",
            "Zve64x",
            "Zve64f",
            "Zve64d",
            "Zvbb",
            "Zvfhmin",
            "Zvkb",
            "Zvkt",
            "Zvl32b",
            "Zvl64b",
            "Zvl128b",
            "Supm");

    /// RVA23U64 mandatory scalar extensions implemented on top of the RVA22U64 baseline.
    public static final @Unmodifiable List<String> IMPLEMENTED_ADDITIONAL_SCALAR_EXTENSIONS = List.of(
            "Zicond",
            "Zimop",
            "Zcmop",
            "Zcb",
            "Zawrs");

    /// RVA23U64 mandatory areas that still block reporting the full profile.
    public static final @Unmodifiable List<String> PENDING_MANDATORY_AREAS = List.of(
            "V",
            "Zvbb",
            "Zvfhmin",
            "Zvkb",
            "Zvkt",
            "Zvl128b",
            "Supm");

    /// Linux hwprobe bits for the implemented additional RVA23U64 scalar extensions.
    public static final long HWPROBE_IMPLEMENTED_ADDITIONAL_SCALAR_EXTENSIONS =
            RiscVExtensions.HWPROBE_EXT_ZICOND
                    | RiscVExtensions.HWPROBE_EXT_ZAWRS;

    /// Prevents construction of this constants class.
    private Rva23Profile() {
    }
}
