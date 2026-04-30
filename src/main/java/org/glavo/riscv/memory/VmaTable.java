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
        ArrayList<Vma> mergedVmas = new ArrayList<>(vmas.size());
        for (Vma vma : vmas) {
            appendMerged(mergedVmas, vma);
        }
        return fromMergedList(mergedVmas);
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
        boolean mergePrevious = insertionIndex > 0 && canMergeEntryBefore(insertionIndex - 1, vma);
        boolean mergeNext = insertionIndex < addresses.length && canMergeEntryAfter(vma, insertionIndex);
        int mergedIndex = mergePrevious ? insertionIndex - 1 : insertionIndex;
        int newSize = addresses.length + 1 - (mergePrevious ? 1 : 0) - (mergeNext ? 1 : 0);
        long mergedAddress = mergePrevious ? addresses[insertionIndex - 1] : vma.address();
        long mergedEndAddress = mergeNext ? endAddresses[insertionIndex] : vma.endAddress();

        long[] newAddresses = new long[newSize];
        long[] newEndAddresses = new long[newSize];
        boolean[] newHuge = new boolean[newSize];
        byte[] newHugePagePreferences = new byte[newSize];
        long[] newProtections = new long[newSize];
        copyRange(0, mergedIndex, newAddresses, newEndAddresses, newHuge, newHugePagePreferences, newProtections);
        newAddresses[mergedIndex] = mergedAddress;
        newEndAddresses[mergedIndex] = mergedEndAddress;
        newHuge[mergedIndex] = vma.huge();
        newHugePagePreferences[mergedIndex] = vma.hugePagePreference();
        newProtections[mergedIndex] = vma.protection();

        int sourceIndex = insertionIndex + (mergeNext ? 1 : 0);
        int destinationIndex = mergedIndex + 1;
        copyRange(
                sourceIndex,
                addresses.length - sourceIndex,
                newAddresses,
                newEndAddresses,
                newHuge,
                newHugePagePreferences,
                newProtections,
                destinationIndex);
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

    /// Copies a range from this table to destination arrays at the same offset.
    private void copyRange(
            int sourceIndex,
            int length,
            long[] newAddresses,
            long[] newEndAddresses,
            boolean[] newHuge,
            byte[] newHugePagePreferences,
            long[] newProtections) {
        copyRange(
                sourceIndex,
                length,
                newAddresses,
                newEndAddresses,
                newHuge,
                newHugePagePreferences,
                newProtections,
                sourceIndex);
    }

    /// Copies a range from this table to destination arrays.
    private void copyRange(
            int sourceIndex,
            int length,
            long[] newAddresses,
            long[] newEndAddresses,
            boolean[] newHuge,
            byte[] newHugePagePreferences,
            long[] newProtections,
            int destinationIndex) {
        System.arraycopy(addresses, sourceIndex, newAddresses, destinationIndex, length);
        System.arraycopy(endAddresses, sourceIndex, newEndAddresses, destinationIndex, length);
        System.arraycopy(huge, sourceIndex, newHuge, destinationIndex, length);
        System.arraycopy(hugePagePreferences, sourceIndex, newHugePagePreferences, destinationIndex, length);
        System.arraycopy(protections, sourceIndex, newProtections, destinationIndex, length);
    }

    /// Copies a pre-merged VMA list into parallel arrays.
    private static VmaTable fromMergedList(ArrayList<Vma> vmas) {
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

    /// Appends a VMA to a sorted list, merging with the previous VMA when all attributes match.
    private static void appendMerged(ArrayList<Vma> vmas, Vma vma) {
        int lastIndex = vmas.size() - 1;
        if (lastIndex < 0) {
            vmas.add(vma);
            return;
        }

        Vma previous = vmas.get(lastIndex);
        if (canMerge(previous, vma)) {
            vmas.set(lastIndex, new Vma(
                    previous.address(),
                    vma.endAddress(),
                    previous.huge(),
                    previous.hugePagePreference(),
                    previous.protection()));
        } else {
            vmas.add(vma);
        }
    }

    /// Returns true when two adjacent VMAs can be represented as one equivalent VMA.
    private static boolean canMerge(Vma first, Vma second) {
        return first.endAddress() == second.address()
                && first.huge() == second.huge()
                && first.hugePagePreference() == second.hugePagePreference()
                && first.protection() == second.protection();
    }

    /// Returns true when an existing VMA can be merged before the supplied VMA.
    private boolean canMergeEntryBefore(int index, Vma vma) {
        return endAddresses[index] == vma.address()
                && huge[index] == vma.huge()
                && hugePagePreferences[index] == vma.hugePagePreference()
                && protections[index] == vma.protection();
    }

    /// Returns true when an existing VMA can be merged after the supplied VMA.
    private boolean canMergeEntryAfter(Vma vma, int index) {
        return vma.endAddress() == addresses[index]
                && vma.huge() == huge[index]
                && vma.hugePagePreference() == hugePagePreferences[index]
                && vma.protection() == protections[index];
    }

    /// Returns true when two half-open address ranges overlap.
    private static boolean rangesOverlap(long firstStart, long firstEnd, long secondStart, long secondEnd) {
        return firstStart < secondEnd && secondStart < firstEnd;
    }
}
