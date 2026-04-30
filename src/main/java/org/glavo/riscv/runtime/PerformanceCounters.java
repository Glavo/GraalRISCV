// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.parser.RiscVOperation;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.LongAdder;

/// Collects optional low-overhead runtime counters for performance investigations.
@NotNullByDefault
public final class PerformanceCounters {
    /// Counts generic micro-bytecode operation dispatches by canonical RISC-V operation ordinal.
    private final LongAdder[] genericOperationCounts;

    /// Counts generic micro-bytecode operation dispatches across all operation kinds.
    private final LongAdder genericOperationTotal = new LongAdder();

    /// Counts accesses served by the access-local data page cache.
    private final LongAdder memoryLocalHits = new LongAdder();

    /// Counts accesses missed by the access-local data page cache.
    private final LongAdder memoryLocalMisses = new LongAdder();

    /// Counts page lookups served by the context-thread software TLB.
    private final LongAdder memorySoftwareTlbHits = new LongAdder();

    /// Counts page lookups missed by the context-thread software TLB.
    private final LongAdder memorySoftwareTlbMisses = new LongAdder();

    /// Counts guest block lookups served by the loop-local block cache.
    private final LongAdder localBlockCacheHits = new LongAdder();

    /// Counts guest block lookups missed by the loop-local block cache.
    private final LongAdder localBlockCacheMisses = new LongAdder();

    /// Counts guest block lookups served by the shared decoded block cache.
    private final LongAdder sharedBlockCacheHits = new LongAdder();

    /// Counts guest block lookups missed by the shared decoded block cache.
    private final LongAdder sharedBlockCacheMisses = new LongAdder();

    /// Counts decoded block executions served by a direct-call inline-cache entry.
    private final LongAdder directBlockCallHits = new LongAdder();

    /// Counts decoded block executions that missed the direct-call inline cache.
    private final LongAdder directBlockCallMisses = new LongAdder();

    /// Counts trace executions served by a direct-call inline-cache entry.
    private final LongAdder directTraceCallHits = new LongAdder();

    /// Counts trace executions that missed the direct-call inline cache.
    private final LongAdder directTraceCallMisses = new LongAdder();

    /// Counts trace-cache lookups that found a compiled trace.
    private final LongAdder traceCacheHits = new LongAdder();

    /// Counts trace-cache lookups that did not find a compiled trace.
    private final LongAdder traceCacheMisses = new LongAdder();

    /// Counts trace side exits caused by an unexpected successor PC.
    private final LongAdder traceSideExits = new LongAdder();

    /// Counts compiled traces.
    private final LongAdder compiledTraces = new LongAdder();

    /// Counts decoded blocks included in compiled traces.
    private final LongAdder compiledTraceBlocks = new LongAdder();

    /// Creates an empty performance counter set.
    public PerformanceCounters() {
        RiscVOperation[] operations = RiscVOperation.values();
        this.genericOperationCounts = new LongAdder[operations.length];
        for (int index = 0; index < operations.length; index++) {
            genericOperationCounts[index] = new LongAdder();
        }
    }

    /// Records one operation executed through the generic micro-bytecode fallback.
    public void recordGenericOperation(RiscVOperation operation) {
        genericOperationTotal.increment();
        genericOperationCounts[operation.ordinal()].increment();
    }

    /// Records whether an access-local data page cache lookup hit.
    public void recordMemoryLocalLookup(boolean hit) {
        if (hit) {
            memoryLocalHits.increment();
        } else {
            memoryLocalMisses.increment();
        }
    }

    /// Records whether a context-thread software TLB lookup hit.
    public void recordMemorySoftwareTlbLookup(boolean hit) {
        if (hit) {
            memorySoftwareTlbHits.increment();
        } else {
            memorySoftwareTlbMisses.increment();
        }
    }

    /// Records whether a loop-local decoded block cache lookup hit.
    public void recordLocalBlockCacheLookup(boolean hit) {
        if (hit) {
            localBlockCacheHits.increment();
        } else {
            localBlockCacheMisses.increment();
        }
    }

    /// Records whether a shared decoded block cache lookup hit.
    public void recordSharedBlockCacheLookup(boolean hit) {
        if (hit) {
            sharedBlockCacheHits.increment();
        } else {
            sharedBlockCacheMisses.increment();
        }
    }

    /// Records whether the direct decoded-block call cache handled the dispatch.
    public void recordDirectBlockCallLookup(boolean hit) {
        if (hit) {
            directBlockCallHits.increment();
        } else {
            directBlockCallMisses.increment();
        }
    }

    /// Records whether the direct trace call cache handled the dispatch.
    public void recordDirectTraceCallLookup(boolean hit) {
        if (hit) {
            directTraceCallHits.increment();
        } else {
            directTraceCallMisses.increment();
        }
    }

    /// Records whether the trace cache contained a trace for the current PC.
    public void recordTraceCacheLookup(boolean hit) {
        if (hit) {
            traceCacheHits.increment();
        } else {
            traceCacheMisses.increment();
        }
    }

    /// Records a trace side exit.
    public void recordTraceSideExit() {
        traceSideExits.increment();
    }

    /// Records one compiled trace and its decoded block count.
    public void recordCompiledTrace(int blockCount) {
        compiledTraces.increment();
        compiledTraceBlocks.add(blockCount);
    }

    /// Writes a performance summary to the supplied stream.
    public void writeSummary(OutputStream stream) {
        PrintStream printStream = stream instanceof PrintStream existing ? existing : new PrintStream(stream, true);
        printStream.println("RISC-V performance counters:");
        writeRatio(printStream, "memory.localPageCache", memoryLocalHits.sum(), memoryLocalMisses.sum());
        writeRatio(printStream, "memory.softwareTlb", memorySoftwareTlbHits.sum(), memorySoftwareTlbMisses.sum());
        writeRatio(printStream, "blocks.localCache", localBlockCacheHits.sum(), localBlockCacheMisses.sum());
        writeRatio(printStream, "blocks.sharedCache", sharedBlockCacheHits.sum(), sharedBlockCacheMisses.sum());
        writeRatio(printStream, "dispatch.directBlockCall", directBlockCallHits.sum(), directBlockCallMisses.sum());
        writeRatio(printStream, "dispatch.directTraceCall", directTraceCallHits.sum(), directTraceCallMisses.sum());
        writeRatio(printStream, "traces.cache", traceCacheHits.sum(), traceCacheMisses.sum());
        printStream.println("  traces.compiled=" + compiledTraces.sum());
        printStream.println("  traces.compiledBlocks=" + compiledTraceBlocks.sum());
        printStream.println("  traces.sideExits=" + traceSideExits.sum());
        writeGenericOperationSummary(printStream);
        printStream.flush();
    }

    /// Writes the generic operation dispatch count and per-operation breakdown.
    private void writeGenericOperationSummary(PrintStream stream) {
        long total = genericOperationTotal.sum();
        stream.println("  micro.genericOperations=" + total);
        if (total == 0) {
            return;
        }

        RiscVOperation[] operations = RiscVOperation.values();
        for (RiscVOperation operation : operations) {
            long count = genericOperationCounts[operation.ordinal()].sum();
            if (count != 0) {
                stream.println("    " + operation + "=" + count);
            }
        }
    }

    /// Writes a hit/miss line with integer percentage precision.
    private static void writeRatio(PrintStream stream, String name, long hits, long misses) {
        long total = hits + misses;
        long hitPercent = total == 0 ? 0 : hits * 100 / total;
        stream.println("  " + name + ".hits=" + hits
                + " misses=" + misses
                + " hitRate=" + hitPercent + "%");
    }
}
