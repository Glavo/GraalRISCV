// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.constants;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Defines the simulator-visible RVA23U64 profile capability sets.
@NotNullByDefault
public final class Rva23Profile {
    /// The canonical profile name.
    public static final String NAME = "RVA23U64";

    /// The mandatory base ISA for the profile.
    public static final String MANDATORY_BASE = "RV64I";

    /// The minimum vector register length required by RVA23U64.
    public static final int MINIMUM_VLEN_BITS = 128;

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

    /// RVA23U64 mandatory vector extensions implemented on top of base `V`.
    public static final @Unmodifiable List<String> IMPLEMENTED_ADDITIONAL_VECTOR_EXTENSIONS = List.of(
            "Zvbb",
            "Zvfhmin",
            "Zvkb",
            "Zvkt",
            "Zvl128b");

    /// Implemented optional vector crypto extensions beyond the RVA23U64 mandatory set.
    public static final @Unmodifiable List<String> IMPLEMENTED_OPTIONAL_VECTOR_EXTENSIONS = List.of(
            "Zvbc");

    /// RVA23U64 mandatory areas that still block reporting the full profile.
    public static final @Unmodifiable List<String> PENDING_MANDATORY_AREAS = List.of();

    /// Linux hwprobe bits for the implemented additional RVA23U64 scalar and vector extensions.
    public static final long HWPROBE_IMPLEMENTED_ADDITIONAL_EXTENSIONS =
            RiscVExtensions.HWPROBE_EXT_ZICOND
                    | RiscVExtensions.HWPROBE_IMA_V
                    | RiscVExtensions.HWPROBE_EXT_ZAWRS
                    | RiscVExtensions.HWPROBE_EXT_ZVBB
                    | RiscVExtensions.HWPROBE_EXT_ZVKB
                    | RiscVExtensions.HWPROBE_EXT_ZVKT
                    | RiscVExtensions.HWPROBE_EXT_ZVFHMIN
                    | RiscVExtensions.HWPROBE_EXT_ZVE32X
                    | RiscVExtensions.HWPROBE_EXT_ZVE32F
                    | RiscVExtensions.HWPROBE_EXT_ZVE64X
                    | RiscVExtensions.HWPROBE_EXT_ZVE64F
                    | RiscVExtensions.HWPROBE_EXT_ZVE64D
                    | RiscVExtensions.HWPROBE_EXT_ZIMOP
                    | RiscVExtensions.HWPROBE_EXT_ZCA
                    | RiscVExtensions.HWPROBE_EXT_ZCB
                    | RiscVExtensions.HWPROBE_EXT_ZCD
                    | RiscVExtensions.HWPROBE_EXT_ZCMOP
                    | RiscVExtensions.HWPROBE_EXT_SUPM;

    /// Linux hwprobe bits for implemented optional RVA23U64 vector extensions.
    public static final long HWPROBE_IMPLEMENTED_OPTIONAL_EXTENSIONS =
            RiscVExtensions.HWPROBE_EXT_ZVBC;

    /// Linux hwprobe bits reported for the complete RVA23U64 user-mode profile.
    public static final long HWPROBE_REPORTED_EXTENSIONS =
            Rva22Profile.HWPROBE_REPORTED_EXTENSIONS
                    | HWPROBE_IMPLEMENTED_ADDITIONAL_EXTENSIONS
                    | HWPROBE_IMPLEMENTED_OPTIONAL_EXTENSIONS;

    /// Prevents construction of this constants class.
    private Rva23Profile() {
    }
}
