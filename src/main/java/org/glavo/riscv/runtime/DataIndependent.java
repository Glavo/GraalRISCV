// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.jetbrains.annotations.NotNullByDefault;

/// Provides fixed-shape integer helpers for guest operations that may process secret data.
@NotNullByDefault
public final class DataIndependent {
    /// Low bit of each byte in a 64-bit word.
    private static final long BYTE_LOW_BITS = 0x0101_0101_0101_0101L;

    /// Prevents construction of this utility class.
    private DataIndependent() {
    }

    /// Returns `1` when `left` is less than `right` as signed 64-bit values, otherwise `0`.
    public static long signedLessThan(long left, long right) {
        long difference = left - right;
        long leftSign = left >> (Long.SIZE - 1);
        long rightSign = right >> (Long.SIZE - 1);
        long signDifference = leftSign ^ rightSign;
        return ((signDifference & leftSign) | (~signDifference & (difference >> (Long.SIZE - 1)))) & 1L;
    }

    /// Returns `1` when `left` is less than `right` as unsigned 64-bit values, otherwise `0`.
    public static long unsignedLessThan(long left, long right) {
        long difference = left - right;
        return ((~left & right) | (~(left ^ right) & difference)) >>> (Long.SIZE - 1);
    }

    /// Selects `whenOne` when `condition` is `1`, and `whenZero` when `condition` is `0`.
    public static long select(long condition, long whenOne, long whenZero) {
        long mask = -condition;
        return (whenOne & mask) | (whenZero & ~mask);
    }

    /// Returns `1` when `value` is zero, otherwise `0`.
    public static long isZero(long value) {
        return ((value | -value) >>> (Long.SIZE - 1)) ^ 1L;
    }

    /// Returns `1` when `value` is nonzero, otherwise `0`.
    public static long isNonzero(long value) {
        return (value | -value) >>> (Long.SIZE - 1);
    }

    /// Returns the signed minimum of two 64-bit values.
    public static long signedMinimum(long left, long right) {
        return select(signedLessThan(left, right), left, right);
    }

    /// Returns the signed maximum of two 64-bit values.
    public static long signedMaximum(long left, long right) {
        return select(signedLessThan(left, right), right, left);
    }

    /// Returns the unsigned minimum of two 64-bit values.
    public static long unsignedMinimum(long left, long right) {
        return select(unsignedLessThan(left, right), left, right);
    }

    /// Returns the unsigned maximum of two 64-bit values.
    public static long unsignedMaximum(long left, long right) {
        return select(unsignedLessThan(left, right), right, left);
    }

    /// Computes the high half of signed-by-unsigned 64-bit multiplication.
    public static long multiplyHighSignedUnsigned(long signed, long unsigned) {
        return multiplyHighUnsigned(signed, unsigned) - (unsigned & (signed >> (Long.SIZE - 1)));
    }

    /// Computes the high half of signed-by-signed 64-bit multiplication.
    public static long multiplyHighSigned(long left, long right) {
        return multiplyHighUnsigned(left, right)
                - (right & (left >> (Long.SIZE - 1)))
                - (left & (right >> (Long.SIZE - 1)));
    }

    /// Computes the high half of unsigned-by-unsigned 64-bit multiplication.
    public static long multiplyHighUnsigned(long left, long right) {
        long leftLow = left & 0xffff_ffffL;
        long leftHigh = left >>> Integer.SIZE;
        long rightLow = right & 0xffff_ffffL;
        long rightHigh = right >>> Integer.SIZE;

        long lowCarry = (leftLow * rightLow) >>> Integer.SIZE;
        long firstMiddle = leftHigh * rightLow + lowCarry;
        long firstMiddleLow = firstMiddle & 0xffff_ffffL;
        long firstMiddleHigh = firstMiddle >>> Integer.SIZE;
        long secondMiddle = firstMiddleLow + leftLow * rightHigh;
        return leftHigh * rightHigh + firstMiddleHigh + (secondMiddle >>> Integer.SIZE);
    }

    /// Returns the number of one bits in a 64-bit word.
    public static int bitCount(long value) {
        long result = value - ((value >>> 1) & 0x5555_5555_5555_5555L);
        result = (result & 0x3333_3333_3333_3333L) + ((result >>> 2) & 0x3333_3333_3333_3333L);
        result = (result + (result >>> 4)) & 0x0f0f_0f0f_0f0f_0f0fL;
        return (int) ((result * BYTE_LOW_BITS) >>> (Long.SIZE - Byte.SIZE));
    }

    /// Returns the number of one bits in a 32-bit word.
    public static int bitCountWord(int value) {
        int result = value - ((value >>> 1) & 0x5555_5555);
        result = (result & 0x3333_3333) + ((result >>> 2) & 0x3333_3333);
        result = (result + (result >>> 4)) & 0x0f0f_0f0f;
        return (result * 0x0101_0101) >>> (Integer.SIZE - Byte.SIZE);
    }

    /// Returns the number of leading zero bits in a 64-bit word.
    public static int numberOfLeadingZeros(long value) {
        long spread = value;
        spread |= spread >>> 1;
        spread |= spread >>> 2;
        spread |= spread >>> 4;
        spread |= spread >>> 8;
        spread |= spread >>> 16;
        spread |= spread >>> 32;
        return Long.SIZE - bitCount(spread);
    }

    /// Returns the number of trailing zero bits in a 64-bit word.
    public static int numberOfTrailingZeros(long value) {
        return bitCount((value & -value) - 1);
    }

    /// Returns the number of leading zero bits in a 32-bit word.
    public static int numberOfLeadingZerosWord(int value) {
        int spread = value;
        spread |= spread >>> 1;
        spread |= spread >>> 2;
        spread |= spread >>> 4;
        spread |= spread >>> 8;
        spread |= spread >>> 16;
        return Integer.SIZE - bitCountWord(spread);
    }

    /// Returns the number of trailing zero bits in a 32-bit word.
    public static int numberOfTrailingZerosWord(int value) {
        return bitCountWord((value & -value) - 1);
    }

    /// Implements the `orc.b` byte-wise nonzero propagation.
    public static long orCombineBytes(long value) {
        long result = 0;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            long byteValue = (value >>> shift) & 0xffL;
            long byteMask = -((byteValue | -byteValue) >>> (Long.SIZE - 1));
            result |= (byteMask & 0xffL) << shift;
        }
        return result;
    }
}
