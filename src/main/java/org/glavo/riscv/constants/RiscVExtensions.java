// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.constants;

import org.jetbrains.annotations.NotNullByDefault;

/// Defines simulator-visible RISC-V ISA extension capability constants.
@NotNullByDefault
public final class RiscVExtensions {
    /// The cache block size required by the RVA22U64 `Zic64b` profile component.
    public static final long CACHE_BLOCK_SIZE = 64;

    /// Linux `RISCV_HWPROBE_IMA_FD`.
    public static final long HWPROBE_IMA_FD = 1;

    /// Linux `RISCV_HWPROBE_IMA_C`.
    public static final long HWPROBE_IMA_C = 1 << 1;

    /// Linux `RISCV_HWPROBE_EXT_ZBA`.
    public static final long HWPROBE_EXT_ZBA = 1 << 3;

    /// Linux `RISCV_HWPROBE_EXT_ZBB`.
    public static final long HWPROBE_EXT_ZBB = 1 << 4;

    /// Linux `RISCV_HWPROBE_EXT_ZBS`.
    public static final long HWPROBE_EXT_ZBS = 1 << 5;

    /// Linux `RISCV_HWPROBE_EXT_ZICBOZ`.
    public static final long HWPROBE_EXT_ZICBOZ = 1 << 6;

    /// Linux `RISCV_HWPROBE_EXT_ZFHMIN`.
    public static final long HWPROBE_EXT_ZFHMIN = 1 << 28;

    /// Linux `RISCV_HWPROBE_EXT_ZFA`.
    public static final long HWPROBE_EXT_ZFA = 1L << 32;

    /// Linux `RISCV_HWPROBE_EXT_ZIHINTNTL`.
    public static final long HWPROBE_EXT_ZIHINTNTL = 1L << 29;

    /// Linux `RISCV_HWPROBE_EXT_ZIHINTPAUSE`.
    public static final long HWPROBE_EXT_ZIHINTPAUSE = 1L << 36;

    /// Linux `RISCV_HWPROBE_EXT_ZICNTR`.
    public static final long HWPROBE_EXT_ZICNTR = 1L << 50;

    /// Linux `RISCV_HWPROBE_EXT_ZICBOM`.
    public static final long HWPROBE_EXT_ZICBOM = 1L << 55;

    /// Linux `RISCV_HWPROBE_EXT_ZICBOP`.
    public static final long HWPROBE_EXT_ZICBOP = 1L << 60;

    /// The extension bits currently reported through Linux `RISCV_HWPROBE_KEY_IMA_EXT_0`.
    public static final long HWPROBE_IMA_EXTENSIONS =
            HWPROBE_IMA_FD
                    | HWPROBE_IMA_C
                    | HWPROBE_EXT_ZBA
                    | HWPROBE_EXT_ZBB
                    | HWPROBE_EXT_ZBS
                    | HWPROBE_EXT_ZICBOZ
                    | HWPROBE_EXT_ZFHMIN
                    | HWPROBE_EXT_ZFA
                    | HWPROBE_EXT_ZIHINTNTL
                    | HWPROBE_EXT_ZIHINTPAUSE
                    | HWPROBE_EXT_ZICNTR
                    | HWPROBE_EXT_ZICBOM
                    | HWPROBE_EXT_ZICBOP;

    /// Prevents construction of this constants class.
    private RiscVExtensions() {
    }
}
