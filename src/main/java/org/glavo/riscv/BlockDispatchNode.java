package org.glavo.riscv;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Dispatches guest program counters through a small self-specializing block inline cache.
@NotNullByDefault
public final class BlockDispatchNode extends Node {
    /// The maximum number of decoded blocks installed directly under this dispatch chain.
    private static final int INLINE_CACHE_LIMIT = 64;

    /// The cache depth of this dispatch node.
    private final int depth;

    /// The cached guest program counter for this dispatch node.
    @CompilationFinal
    private long cachedPc;

    /// The decoded block used when `cachedPc` matches the current guest program counter.
    @Child
    private @Nullable BlockNode cachedBlock;

    /// The next dispatch cache entry checked after this one misses.
    @Child
    private @Nullable BlockDispatchNode next;

    /// Creates the root dispatch cache entry.
    public BlockDispatchNode() {
        this(0);
    }

    /// Creates a dispatch cache entry at the supplied depth.
    private BlockDispatchNode(int depth) {
        this.depth = depth;
    }

    /// Executes the decoded block for the current guest program counter.
    public void execute(RiscVRootNode root, Memory memory, MachineState state) {
        long pc = state.pc();
        if (cachedBlock != null && cachedPc == pc) {
            cachedBlock.execute(state);
            return;
        }

        if (next != null) {
            next.execute(root, memory, state);
            return;
        }

        CompilerDirectives.transferToInterpreterAndInvalidate();
        executeMiss(root, memory, state, pc);
    }

    /// Handles a dispatch miss by extending the inline cache or using the root fallback cache.
    private void executeMiss(RiscVRootNode root, Memory memory, MachineState state, long pc) {
        if (depth < INLINE_CACHE_LIMIT) {
            cachedPc = pc;
            cachedBlock = insert(RiscVDecoder.decodeBlock(memory, pc));
            next = insert(new BlockDispatchNode(depth + 1));
            cachedBlock.execute(state);
            return;
        }

        root.blockForUncached(memory, pc).execute(state);
    }
}
