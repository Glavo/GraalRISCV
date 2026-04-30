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

    /// Linux `RISCV_HWPROBE_IMA_V`.
    public static final long HWPROBE_IMA_V = 1 << 2;

    /// Linux `RISCV_HWPROBE_EXT_ZBA`.
    public static final long HWPROBE_EXT_ZBA = 1 << 3;

    /// Linux `RISCV_HWPROBE_EXT_ZBB`.
    public static final long HWPROBE_EXT_ZBB = 1 << 4;

    /// Linux `RISCV_HWPROBE_EXT_ZBS`.
    public static final long HWPROBE_EXT_ZBS = 1 << 5;

    /// Linux `RISCV_HWPROBE_EXT_ZICBOZ`.
    public static final long HWPROBE_EXT_ZICBOZ = 1 << 6;

    /// Linux `RISCV_HWPROBE_EXT_ZKT`.
    public static final long HWPROBE_EXT_ZKT = 1L << 16;

    /// Linux `RISCV_HWPROBE_EXT_ZFH`.
    public static final long HWPROBE_EXT_ZFH = 1L << 27;

    /// Linux `RISCV_HWPROBE_EXT_ZFHMIN`.
    public static final long HWPROBE_EXT_ZFHMIN = 1 << 28;

    /// Linux `RISCV_HWPROBE_EXT_ZIHINTNTL`.
    public static final long HWPROBE_EXT_ZIHINTNTL = 1L << 29;

    /// Linux `RISCV_HWPROBE_EXT_ZFA`.
    public static final long HWPROBE_EXT_ZFA = 1L << 32;

    /// Linux `RISCV_HWPROBE_EXT_ZACAS`.
    public static final long HWPROBE_EXT_ZACAS = 1L << 34;

    /// Linux `RISCV_HWPROBE_EXT_ZICOND`.
    public static final long HWPROBE_EXT_ZICOND = 1L << 35;

    /// Linux `RISCV_HWPROBE_EXT_ZIHINTPAUSE`.
    public static final long HWPROBE_EXT_ZIHINTPAUSE = 1L << 36;

    /// Linux `RISCV_HWPROBE_EXT_ZAWRS`.
    public static final long HWPROBE_EXT_ZAWRS = 1L << 48;

    /// Linux `RISCV_HWPROBE_EXT_ZICNTR`.
    public static final long HWPROBE_EXT_ZICNTR = 1L << 50;

    /// Linux `RISCV_HWPROBE_EXT_ZIHPM`.
    public static final long HWPROBE_EXT_ZIHPM = 1L << 51;

    /// Linux `RISCV_HWPROBE_EXT_ZICBOM`.
    public static final long HWPROBE_EXT_ZICBOM = 1L << 55;

    /// Linux `RISCV_HWPROBE_EXT_ZAAMO`.
    public static final long HWPROBE_EXT_ZAAMO = 1L << 56;

    /// Linux `RISCV_HWPROBE_EXT_ZALRSC`.
    public static final long HWPROBE_EXT_ZALRSC = 1L << 57;

    /// Linux `RISCV_HWPROBE_EXT_ZICBOP`.
    public static final long HWPROBE_EXT_ZICBOP = 1L << 60;

    /// Linux `RISCV_HWPROBE_KEY_IMA_EXT_0` bits for implemented mandatory RVA22U64 requirements.
    public static final long HWPROBE_RVA22U64_MANDATORY_EXTENSIONS = Rva22Profile.HWPROBE_MANDATORY_EXTENSIONS;

    /// Linux `RISCV_HWPROBE_KEY_IMA_EXT_0` bits for compatible extensions beyond RVA22U64 mandates.
    public static final long HWPROBE_COMPATIBILITY_EXTENSIONS = Rva22Profile.HWPROBE_COMPATIBILITY_EXTENSIONS;

    /// The extension bits currently reported through Linux `RISCV_HWPROBE_KEY_IMA_EXT_0`.
    public static final long HWPROBE_IMA_EXTENSIONS = Rva22Profile.HWPROBE_REPORTED_EXTENSIONS;

    /// Prevents construction of this constants class.
    private RiscVExtensions() {
    }
}
