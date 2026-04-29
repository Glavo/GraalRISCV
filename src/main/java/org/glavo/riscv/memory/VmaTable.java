// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.memory;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Arrays;

/// Stores sorted guest VMAs in parallel arrays.
///
/// @param addresses the inclusive guest start addresses for the VMAs
/// @param endAddresses the exclusive guest end addresses for the VMAs
/// @param huge whether each VMA reserves MAP_HUGETLB pages
/// @param hugePagePreferences the transparent huge-page advice for each VMA
/// @param protections the supported guest access-protection flags for each VMA
@NotNullByDefault
record VmaTable(
        long @Unmodifiable [] addresses,
        long @Unmodifiable [] endAddresses,
        boolean @Unmodifiable [] huge,
        byte @Unmodifiable [] hugePagePreferences,
        long @Unmodifiable [] protections) {
    /// Creates a VMA snapshot backed by same-length sorted arrays.
    VmaTable {
        if (addresses.length != endAddresses.length
                || addresses.length != huge.length
                || addresses.length != hugePagePreferences.length
                || addresses.length != protections.length) {
            throw new IllegalArgumentException("VMA arrays have different lengths");
        }
    }

    /// Creates an empty VMA snapshot.
    static VmaTable empty() {
        return new VmaTable(new long[0], new long[0], new boolean[0], new byte[0], new long[0]);
    }

    /// Creates a one-VMA snapshot.
    static VmaTable single(
            long address,
            long endAddress,
            boolean huge,
            byte hugePagePreference,
            long protection) {
        return new VmaTable(
                new long[]{address},
                new long[]{endAddress},
                new boolean[]{huge},
                new byte[]{hugePagePreference},
                new long[]{protection});
    }

    /// Copies a VMA list into a sorted immutable snapshot.
    static VmaTable copyOf(ArrayList<Vma> vmas) {
        long[] addresses = new long[vmas.size()];
        long[] endAddresses = new long[vmas.size()];
        boolean[] huge = new boolean[vmas.size()];
        byte[] hugePagePreferences = new byte[vmas.size()];
        long[] protections = new long[vmas.size()];
        for (int index = 0; index < vmas.size(); index++) {
            Vma vma = vmas.get(index);
            addresses[index] = vma.address();
            endAddresses[index] = vma.endAddress();
            huge[index] = vma.huge();
            hugePagePreferences[index] = vma.hugePagePreference();
            protections[index] = vma.protection();
        }
        return new VmaTable(addresses, endAddresses, huge, hugePagePreferences, protections);
    }

    /// Returns the number of VMAs in this snapshot.
    int size() {
        return addresses.length;
    }

    /// Returns the VMA at the supplied index.
    Vma vma(int index) {
        return new Vma(
                addresses[index],
                endAddresses[index],
                huge[index],
                hugePagePreferences[index],
                protections[index]);
    }

    /// Returns a new snapshot with the supplied non-overlapping VMA inserted.
    VmaTable insert(Vma vma) {
        int insertionIndex = insertionIndex(vma.address());
        long[] newAddresses = new long[addresses.length + 1];
        long[] newEndAddresses = new long[endAddresses.length + 1];
        boolean[] newHuge = new boolean[huge.length + 1];
        byte[] newHugePagePreferences = new byte[hugePagePreferences.length + 1];
        long[] newProtections = new long[protections.length + 1];
        System.arraycopy(addresses, 0, newAddresses, 0, insertionIndex);
        System.arraycopy(endAddresses, 0, newEndAddresses, 0, insertionIndex);
        System.arraycopy(huge, 0, newHuge, 0, insertionIndex);
        System.arraycopy(hugePagePreferences, 0, newHugePagePreferences, 0, insertionIndex);
        System.arraycopy(protections, 0, newProtections, 0, insertionIndex);
        newAddresses[insertionIndex] = vma.address();
        newEndAddresses[insertionIndex] = vma.endAddress();
        newHuge[insertionIndex] = vma.huge();
        newHugePagePreferences[insertionIndex] = vma.hugePagePreference();
        newProtections[insertionIndex] = vma.protection();
        int copiedEntries = addresses.length - insertionIndex;
        System.arraycopy(addresses, insertionIndex, newAddresses, insertionIndex + 1, copiedEntries);
        System.arraycopy(endAddresses, insertionIndex, newEndAddresses, insertionIndex + 1, copiedEntries);
        System.arraycopy(huge, insertionIndex, newHuge, insertionIndex + 1, copiedEntries);
        System.arraycopy(
                hugePagePreferences,
                insertionIndex,
                newHugePagePreferences,
                insertionIndex + 1,
                copiedEntries);
        System.arraycopy(protections, insertionIndex, newProtections, insertionIndex + 1, copiedEntries);
        return new VmaTable(newAddresses, newEndAddresses, newHuge, newHugePagePreferences, newProtections);
    }

    /// Returns true when any VMA overlaps the supplied guest range.
    boolean overlaps(long address, long length) {
        return findOverlap(address, length) != null;
    }

    /// Finds the VMA fully containing the supplied guest range, or null when absent.
    @Nullable Vma find(long address, long length) {
        int index = Arrays.binarySearch(addresses, address);
        if (index < 0) {
            index = -index - 2;
        }
        if (index < 0) {
            return null;
        }
        long vmaEndAddress = endAddresses[index];
        if (address > vmaEndAddress || length > vmaEndAddress - address) {
            return null;
        }
        return vma(index);
    }

    /// Finds the first VMA overlapping the supplied guest range, or null when absent.
    @Nullable Vma findOverlap(long address, long length) {
        long endAddress = address + length;
        int insertionIndex = insertionIndex(address);
        if (insertionIndex > 0) {
            int previousIndex = insertionIndex - 1;
            if (rangesOverlap(address, endAddress, addresses[previousIndex], endAddresses[previousIndex])) {
                return vma(previousIndex);
            }
        }
        if (insertionIndex >= addresses.length) {
            return null;
        }
        return rangesOverlap(address, endAddress, addresses[insertionIndex], endAddresses[insertionIndex])
                ? vma(insertionIndex)
                : null;
    }

    /// Returns the insertion index for an address in this sorted snapshot.
    private int insertionIndex(long address) {
        int index = Arrays.binarySearch(addresses, address);
        return index >= 0 ? index : -index - 1;
    }

    /// Returns true when two half-open address ranges overlap.
    private static boolean rangesOverlap(long firstStart, long firstEnd, long secondStart, long secondEnd) {
        return firstStart < secondEnd && secondStart < firstEnd;
    }
}
