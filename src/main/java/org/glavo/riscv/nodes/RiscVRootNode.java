// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import org.glavo.riscv.RiscVContext;
import org.glavo.riscv.RiscVLanguage;
import org.glavo.riscv.exception.ProgramExitException;
import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.exception.ThreadExitException;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.parser.DecodedBlock;
import org.glavo.riscv.parser.DecodedInstruction;
import org.glavo.riscv.parser.ElfImage;
import org.glavo.riscv.parser.RiscVDecoder;
import org.glavo.riscv.parser.RiscVOperation;
import org.glavo.riscv.runtime.GuestSyscalls;
import org.glavo.riscv.runtime.LinuxInitialStack;
import org.glavo.riscv.runtime.MachineState;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/// Executes one loaded RISC-V ELF image.
@NotNullByDefault
public final class RiscVRootNode extends RootNode {
    /// The initial Linux user stack backing size.
    private static final long INITIAL_STACK_SIZE = 8L * 1024L * 1024L;

    /// Resolves the current RISC-V context for this root node.
    private static final TruffleLanguage.ContextReference<RiscVContext> CONTEXT_REFERENCE =
            TruffleLanguage.ContextReference.create(RiscVLanguage.class);

    /// The parsed ELF image executed by this root node.
    private final ElfImage image;

    /// The lazily populated block cache for this execution root.
    private final BlockCache blocks;

    /// The Truffle loop root used by the main thread and clone-created guest threads.
    private final RootCallTarget guestLoopTarget;

    /// Creates a root node for a parsed ELF image.
    public RiscVRootNode(RiscVLanguage language, ElfImage image) {
        super(language);
        this.image = image;
        this.blocks = new BlockCache(language);
        this.guestLoopTarget = new GuestLoopRootNode(language, blocks).getCallTarget();
    }

    /// Executes the guest program and returns its exit code.
    @Override
    public Object execute(VirtualFrame frame) {
        RiscVContext context = CONTEXT_REFERENCE.get(this);
        try (Memory memory = Memory.sparse(
                resolveMemoryBase(context),
                context.memorySize(),
                context.pageSize(),
                context.maxCommittedPages(),
                context.hugePageSize(),
                context.hugePages(),
                context.mappedRegionCache())) {
            MachineState state = createState(context, memory);
            try {
                return executeGuestLoop(memory, state);
            } catch (ProgramExitException exit) {
                state.syscalls().requestProcessExit(exit.exitCode());
                state.syscalls().recordThreadExit(state, exit.exitCode());
                return exit.exitCode();
            } catch (RuntimeException | Error throwable) {
                state.syscalls().requestProcessExit(1);
                throw throwable;
            } finally {
                state.syscalls().joinGuestThreads();
                state.syscalls().close();
            }
        }
    }

    /// Runs the decoded-block dispatch loop for an initialized guest state.
    private int executeGuestLoop(Memory memory, MachineState state) {
        try {
            guestLoopTarget.call(new GuestLoopState(memory, state, state.pc()));
            throw new AssertionError("Guest loop returned without an exit signal");
        } catch (ThreadExitException exit) {
            return (int) state.syscalls().completeThreadExit(state, exit.exitCode());
        }
    }

    /// Runs one clone-created guest thread on its Truffle host thread.
    private void runGuestThread(Memory memory, MachineState state) {
        try {
            executeGuestThreadLoop(memory, state);
        } catch (ThreadExitException exit) {
            state.syscalls().recordThreadExit(state, exit.exitCode());
        } catch (ProgramExitException exit) {
            state.syscalls().requestProcessExit(exit.exitCode());
            state.syscalls().recordThreadExit(state, exit.exitCode());
        } catch (Throwable throwable) {
            state.syscalls().recordThreadFailure(throwable);
            state.syscalls().recordThreadExit(state, 1);
        }
    }

    /// Runs the decoded-block dispatch loop for a clone-created guest thread.
    private void executeGuestThreadLoop(Memory memory, MachineState state) {
        guestLoopTarget.call(new GuestLoopState(memory, state, state.pc()));
    }

    /// Creates and initializes architectural state for a guest run.
    private MachineState createState(RiscVContext context, Memory memory) {
        long initialProgramBreak = memory.baseAddress();
        for (ElfImage.LoadSegment segment : image.loadSegments()) {
            long contentsLength = segment.contents().length;
            validateGuestRange(memory, segment.virtualAddress(), segment.memorySize());
            if (!memory.hasDenseInitialBacking()) {
                mapLoadSegment(memory, segment);
            }
            memory.load(segment.virtualAddress(), segment.contents(), 0, (int) contentsLength);
            if (segment.memorySize() > contentsLength) {
                memory.clear(segment.virtualAddress() + contentsLength, segment.memorySize() - contentsLength);
            }
            initialProgramBreak = Math.max(
                    initialProgramBreak,
                    Math.min(alignUp(segment.virtualAddress() + segment.memorySize(), 16), memory.endAddress()));
        }

        GuestSyscalls syscalls = new GuestSyscalls(
                memory,
                context.env().in(),
                context.env().out(),
                context.env().err(),
                initialProgramBreak,
                context.env(),
                context.hostRoot(),
                context.clock(),
                childState -> runGuestThread(memory, childState));
        MachineState state = new MachineState(
                memory,
                context.maxInstructions(),
                context.trace(),
                image.tohostAddress(),
                image.fromhostAddress(),
                syscalls,
                context.env().err());
        state.setPc(image.entryPoint());
        state.setRegister(2, initializeLinuxStack(memory, context));
        return state;
    }

    /// Adds explicit backing for one ELF load segment.
    private static void mapLoadSegment(Memory memory, ElfImage.LoadSegment segment) {
        if (segment.memorySize() == 0) {
            return;
        }
        if (!memory.map(segment.virtualAddress(), segment.memorySize())) {
            throw new RiscVException("ELF segment overlaps another guest memory region: segment="
                    + formatRange(segment.virtualAddress(), segment.virtualAddress() + segment.memorySize())
                    + ", memory=" + formatRange(memory.baseAddress(), memory.endAddress()));
        }
    }

    /// Initializes the Linux user stack at the top of the contiguous guest memory segment.
    private long initializeLinuxStack(Memory memory, RiscVContext context) {
        if (memory.hasDenseInitialBacking()) {
            return LinuxInitialStack.initialize(
                    memory,
                    memory.endAddress(),
                    context.programArguments(),
                    image,
                    context.pageSize());
        }

        long stackTop = memory.endAddress();
        long stackSize = Math.min(INITIAL_STACK_SIZE, memory.size());
        long stackBase = alignDown(stackTop - stackSize, context.pageSize());
        if (stackBase < memory.baseAddress()) {
            stackBase = memory.baseAddress();
        }
        long stackLength = stackTop - stackBase;
        if (stackLength <= 0 || !memory.map(stackBase, stackLength)) {
            throw new RiscVException("Failed to allocate guest stack backing: stack="
                    + formatRange(stackBase, stackTop)
                    + ", memory=" + formatRange(memory.baseAddress(), memory.endAddress()));
        }
        return LinuxInitialStack.initialize(memory, stackTop, context.programArguments(), image, context.pageSize());
    }

    /// Resolves the memory base from context options or the lowest ELF load segment address.
    private long resolveMemoryBase(RiscVContext context) {
        if (context.memoryBase() != RiscVLanguage.AUTO_MEMORY_BASE) {
            return context.memoryBase();
        }

        long baseAddress = Long.MAX_VALUE;
        for (ElfImage.LoadSegment segment : image.loadSegments()) {
            baseAddress = Math.min(baseAddress, segment.virtualAddress());
        }
        return baseAddress == Long.MAX_VALUE ? Memory.DEFAULT_BASE_ADDRESS : baseAddress;
    }

    /// Ensures a loaded ELF segment fits in guest memory.
    private static void validateGuestRange(Memory memory, long address, long length) {
        if (length < 0) {
            throw new RiscVException(memoryLayoutError(
                    memory,
                    "segment length is negative",
                    address,
                    address,
                    length));
        }

        if (address > Long.MAX_VALUE - length) {
            throw new RiscVException("ELF segment is outside guest memory: reason=segment range overflows, "
                    + "segmentStart=" + formatHex(address)
                    + ", length=" + length
                    + ", memory=" + formatRange(memory.baseAddress(), memory.endAddress())
                    + ", memorySize=" + memory.size()
                    + "; use --memory-base auto or choose a lower --memory-base value");
        }

        long endAddress = address + length;
        if (address < memory.baseAddress() || endAddress > memory.endAddress()) {
            throw new RiscVException(memoryLayoutError(
                    memory,
                    memoryLayoutReason(memory, address, endAddress),
                    address,
                    endAddress,
                    length));
        }
    }

    /// Builds a guest memory layout error with range details and CLI hints.
    private static String memoryLayoutError(
            Memory memory,
            String reason,
            long segmentAddress,
            long segmentEndAddress,
            long segmentLength) {
        return "ELF segment is outside guest memory: reason=" + reason
                + ", segment=" + formatRange(segmentAddress, segmentEndAddress)
                + ", length=" + segmentLength
                + ", memory=" + formatRange(memory.baseAddress(), memory.endAddress())
                + ", memorySize=" + memory.size()
                + memoryLayoutHint(memory, segmentAddress, segmentEndAddress);
    }

    /// Describes which side of the guest memory range rejected a segment.
    private static String memoryLayoutReason(Memory memory, long segmentAddress, long segmentEndAddress) {
        boolean startsBeforeMemory = segmentAddress < memory.baseAddress();
        boolean exceedsMemoryEnd = segmentEndAddress > memory.endAddress();
        if (startsBeforeMemory && exceedsMemoryEnd) {
            return "segment starts before guest memory and exceeds guest memory end";
        }
        if (startsBeforeMemory) {
            return "segment starts before guest memory";
        }
        return "segment exceeds guest memory end";
    }

    /// Builds an actionable CLI hint for an invalid guest memory layout.
    private static String memoryLayoutHint(Memory memory, long segmentAddress, long segmentEndAddress) {
        StringBuilder hint = new StringBuilder();
        if (segmentAddress < memory.baseAddress()) {
            hint.append("; Use --memory-base auto or set --memory-base ").append(formatHex(segmentAddress));
        }

        if (segmentEndAddress > memory.endAddress()) {
            if (hint.length() == 0) {
                hint.append("; Increase --memory-size");
            } else {
                hint.append("; with that base, use --memory-size");
            }

            long requiredBaseAddress = segmentAddress < memory.baseAddress()
                    ? segmentAddress
                    : memory.baseAddress();
            long requiredSize = segmentEndAddress - requiredBaseAddress;
            hint.append(" to at least ").append(requiredSize).append(" bytes");
        }

        return hint.toString();
    }

    /// Formats an unsigned guest address as a lowercase hexadecimal string.
    private static String formatHex(long value) {
        return "0x" + Long.toUnsignedString(value, 16);
    }

    /// Formats an address range with an inclusive start and exclusive end.
    private static String formatRange(long startAddress, long endAddress) {
        return "[" + formatHex(startAddress) + "," + formatHex(endAddress) + ")";
    }

    /// Rounds an address up to the requested power-of-two alignment.
    private static long alignUp(long address, long alignment) {
        long mask = alignment - 1;
        if (address > Long.MAX_VALUE - mask) {
            return address;
        }
        return (address + mask) & ~mask;
    }

    /// Rounds an address down to the requested power-of-two alignment.
    private static long alignDown(long address, long alignment) {
        return address & -alignment;
    }

    /// Holds per-thread guest loop state while a Truffle loop root is executing.
    @NotNullByDefault
    private static final class GuestLoopState {
        /// Number of entries in the thread-local decoded-block lookup cache.
        private static final int LOCAL_BLOCK_CACHE_SIZE = 16;

        /// The guest memory shared by the process.
        private final Memory memory;

        /// The architectural state for the running guest thread.
        private final MachineState state;

        /// The syscall handler shared by all guest threads in this process.
        private final GuestSyscalls syscalls;

        /// Reusable block call arguments used to avoid per-block varargs allocation.
        private final Object[] blockArguments;

        /// The next guest program counter to dispatch.
        private long pc;

        /// Guest PCs cached in `localBlocks`.
        private final long[] localBlockPcs = new long[LOCAL_BLOCK_CACHE_SIZE];

        /// Thread-local decoded block entries used before probing the shared cache.
        private final @Nullable BlockEntry[] localBlocks = new BlockEntry[LOCAL_BLOCK_CACHE_SIZE];

        /// Creates loop-local state for one guest thread.
        private GuestLoopState(Memory memory, MachineState state, long pc) {
            this.memory = memory;
            this.state = state;
            this.syscalls = state.syscalls();
            this.blockArguments = new Object[] { state };
            this.pc = pc;
        }

        /// Returns the guest memory shared by the process.
        private Memory memory() {
            return memory;
        }

        /// Returns the architectural state for the running guest thread.
        private MachineState state() {
            return state;
        }

        /// Returns the syscall handler shared by all guest threads in this process.
        private GuestSyscalls syscalls() {
            return syscalls;
        }

        /// Returns the reusable arguments array passed to decoded block call targets.
        private Object[] blockArguments() {
            return blockArguments;
        }

        /// Returns the next guest program counter to dispatch.
        private long pc() {
            return pc;
        }

        /// Updates the next guest program counter to dispatch.
        private void setPc(long pc) {
            this.pc = pc;
        }

        /// Returns the decoded block target for the current program counter.
        private BlockEntry blockFor(BlockCache blocks) {
            int slot = localBlockSlot(pc);
            BlockEntry block = localBlocks[slot];
            if (block != null && localBlockPcs[slot] == pc) {
                return block;
            }

            block = blocks.getOrDecode(memory, pc);
            localBlockPcs[slot] = pc;
            localBlocks[slot] = block;
            return block;
        }

        /// Hashes a guest PC into the thread-local decoded-block cache.
        private static int localBlockSlot(long pc) {
            long hash = pc >>> 1;
            hash ^= hash >>> 4;
            return (int) hash & (LOCAL_BLOCK_CACHE_SIZE - 1);
        }
    }

    /// Executes the guest dispatch loop as a Truffle loop root.
    @NotNullByDefault
    private static final class GuestLoopRootNode extends RootNode {
        /// The loop node that repeatedly dispatches one decoded guest block.
        @Child private LoopNode loop;

        /// Creates a root for guest-thread execution.
        private GuestLoopRootNode(RiscVLanguage language, BlockCache blocks) {
            super(language);
            this.loop = Truffle.getRuntime().createLoopNode(new GuestLoopRepeatingNode(blocks));
        }

        /// Runs guest blocks until the guest exits by throwing an execution-control exception.
        @Override
        public Object execute(VirtualFrame frame) {
            loop.execute(frame);
            throw new AssertionError("Guest loop returned without an exit signal");
        }
    }

    /// Dispatches one guest block per Truffle loop iteration.
    @NotNullByDefault
    private static final class GuestLoopRepeatingNode extends Node implements RepeatingNode {
        /// The block dispatch node used by this loop.
        @Child private BlockDispatchNode dispatch;

        /// Creates a repeating node backed by the supplied decoded-block cache.
        private GuestLoopRepeatingNode(BlockCache blocks) {
            this.dispatch = new BlockDispatchNode(blocks);
        }

        /// Executes one decoded guest block and continues looping.
        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            GuestLoopState loopState = (GuestLoopState) frame.getArguments()[0];
            MachineState state = loopState.state();
            loopState.syscalls().checkProcessStatus();
            long pc = dispatch.execute(loopState, state);
            loopState.setPc(pc);
            return true;
        }
    }

    /// Dispatches decoded blocks through a small direct-call inline cache.
    @NotNullByDefault
    private static final class BlockDispatchNode extends Node {
        /// Consecutive executions required before a guest PC is promoted to a direct call.
        private static final int DIRECT_CALL_INSTALL_THRESHOLD = 128;

        /// The decoded block cache shared by all dispatch calls.
        private final BlockCache blocks;

        /// The hot trace cache shared by all dispatch calls.
        private final TraceCache traces;

        /// Direct-call entry for a stable hot guest trace.
        @Child private @Nullable CachedTraceCallNode cachedTrace;

        /// Direct-call entry for a stable self-looping guest block.
        @Child private @Nullable CachedBlockCallNode cachedCall;

        /// Fallback call node used after the direct-call cache is full.
        @Child private IndirectCallNode indirectCall = IndirectCallNode.create();

        /// The trace start PC currently being considered for direct-call promotion.
        private long traceCandidatePc;

        /// Consecutive trace direct-call promotion observations for `traceCandidatePc`.
        private int traceCandidateCount;

        /// The guest PC currently being considered for direct-call promotion.
        private long candidatePc;

        /// Consecutive direct-call promotion observations for `candidatePc`.
        private int candidateCount;

        /// Creates a block dispatch node backed by the supplied decoded-block cache.
        private BlockDispatchNode(BlockCache blocks) {
            this.blocks = blocks;
            this.traces = new TraceCache(blocks.language());
        }

        /// Executes the decoded block for the supplied guest program counter.
        private long execute(GuestLoopState loopState, MachineState state) {
            long pc = loopState.pc();
            if (state.canRetireBlock()) {
                TraceEntry trace = traces.get(pc);
                if (trace != null) {
                    return executeTraceCachedOrMiss(loopState, state, trace);
                }
            }
            return executeBlockCachedOrMiss(loopState, state, pc);
        }

        /// Executes a cached direct trace call, or falls back to an indirect trace call.
        private long executeTraceCachedOrMiss(GuestLoopState loopState, MachineState state, TraceEntry trace) {
            CachedTraceCallNode cachedTrace = this.cachedTrace;
            if (cachedTrace != null && cachedTrace.matches(trace.startPc())) {
                cachedTrace.call(loopState.blockArguments());
                return state.pc();
            }
            return executeTraceMiss(loopState, state, trace);
        }

        /// Handles a direct trace-call cache miss for the supplied trace.
        private long executeTraceMiss(GuestLoopState loopState, MachineState state, TraceEntry trace) {
            if (CompilerDirectives.inInterpreter() && shouldInstallDirectTrace(trace.startPc())) {
                CachedTraceCallNode cachedTrace = installCachedTrace(trace);
                if (cachedTrace != null) {
                    cachedTrace.call(loopState.blockArguments());
                    return state.pc();
                }
            }

            indirectCall.call(trace.target(), loopState.blockArguments());
            return state.pc();
        }

        /// Executes a cached direct block call, or installs and executes a new cache entry.
        private long executeBlockCachedOrMiss(GuestLoopState loopState, MachineState state, long pc) {
            CachedBlockCallNode cachedCall = this.cachedCall;
            if (cachedCall != null && cachedCall.matches(pc)) {
                cachedCall.call(loopState.blockArguments());
                long nextPc = state.pc();
                observeTransition(loopState.memory(), state, cachedCall.entry(), nextPc);
                return nextPc;
            }
            return executeBlockMiss(loopState, state, pc);
        }

        /// Handles a direct-call cache miss for the supplied guest program counter.
        private long executeBlockMiss(GuestLoopState loopState, MachineState state, long pc) {
            BlockEntry entry = loopState.blockFor(blocks);
            if (CompilerDirectives.inInterpreter() && shouldInstallDirectCall(pc)) {
                CachedBlockCallNode cachedCall = installCachedCall(entry);
                if (cachedCall != null) {
                    cachedCall.call(loopState.blockArguments());
                    long nextPc = state.pc();
                    observeTransition(loopState.memory(), state, entry, nextPc);
                    return nextPc;
                }
            }

            indirectCall.call(entry.target(), loopState.blockArguments());
            long nextPc = state.pc();
            observeTransition(loopState.memory(), state, entry, nextPc);
            return nextPc;
        }

        /// Records a block transition and compiles a trace when the successor edge is hot.
        private void observeTransition(Memory memory, MachineState state, BlockEntry entry, long nextPc) {
            entry.recordSuccessor(nextPc);
            if (state.canRetireBlock() && CompilerDirectives.inInterpreter()) {
                traces.compileIfHot(memory, blocks, entry);
            }
        }

        /// Returns true after a trace PC has been reached repeatedly in the interpreter.
        private boolean shouldInstallDirectTrace(long pc) {
            if (cachedTrace != null) {
                return false;
            }

            if (traceCandidatePc == pc) {
                traceCandidateCount++;
            } else {
                traceCandidatePc = pc;
                traceCandidateCount = 1;
            }
            return traceCandidateCount >= DIRECT_CALL_INSTALL_THRESHOLD;
        }

        /// Installs a direct trace call node for a newly observed hot trace.
        private @Nullable CachedTraceCallNode installCachedTrace(TraceEntry trace) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                CachedTraceCallNode cachedTrace = this.cachedTrace;
                if (cachedTrace != null) {
                    return cachedTrace.matches(trace.startPc()) ? cachedTrace : null;
                }

                cachedTrace = insert(new CachedTraceCallNode(trace));
                this.cachedTrace = cachedTrace;
                return cachedTrace;
            }
        }

        /// Returns true after a guest PC has shown stable self-loop behavior in the interpreter.
        private boolean shouldInstallDirectCall(long pc) {
            if (cachedCall != null) {
                return false;
            }

            if (candidatePc == pc) {
                candidateCount++;
            } else {
                candidatePc = pc;
                candidateCount = 1;
            }
            return candidateCount >= DIRECT_CALL_INSTALL_THRESHOLD;
        }

        /// Installs a direct call node for a newly observed guest block when capacity remains.
        private @Nullable CachedBlockCallNode installCachedCall(BlockEntry entry) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                CachedBlockCallNode cachedCall = this.cachedCall;
                if (cachedCall != null) {
                    return cachedCall.matches(entry.pc()) ? cachedCall : null;
                }

                cachedCall = insert(new CachedBlockCallNode(entry));
                this.cachedCall = cachedCall;
                return cachedCall;
            }
        }
    }

    /// Calls one hot decoded trace through a direct Truffle call node.
    @NotNullByDefault
    private static final class CachedTraceCallNode extends Node {
        /// The guest program counter handled by this cached trace.
        private final long pc;

        /// The direct call node for the decoded trace target.
        @Child private DirectCallNode call;

        /// Creates a cached direct call for one decoded trace.
        private CachedTraceCallNode(TraceEntry trace) {
            this.pc = trace.startPc();
            this.call = DirectCallNode.create(trace.target());
        }

        /// Returns true when this node handles the supplied trace start PC.
        private boolean matches(long pc) {
            return this.pc == pc;
        }

        /// Executes the cached decoded trace.
        private void call(Object[] arguments) {
            call.call(arguments);
        }
    }

    /// Calls one decoded block through a direct Truffle call node.
    @NotNullByDefault
    private static final class CachedBlockCallNode extends Node {
        /// The decoded block entry handled by this cached call.
        private final BlockEntry entry;

        /// The guest program counter handled by this cached direct call.
        private final long pc;

        /// The direct call node for the decoded block target.
        @Child private DirectCallNode call;

        /// Creates a cached direct call for one decoded block.
        private CachedBlockCallNode(BlockEntry entry) {
            this.entry = entry;
            this.pc = entry.pc();
            this.call = DirectCallNode.create(entry.target());
        }

        /// Returns the decoded block entry handled by this cached call.
        private BlockEntry entry() {
            return entry;
        }

        /// Returns true when this node handles the supplied guest program counter.
        private boolean matches(long pc) {
            return this.pc == pc;
        }

        /// Executes the cached decoded block.
        private void call(Object[] arguments) {
            call.call(arguments);
        }
    }

    /// Stores immutable metadata and executable target for one decoded guest block.
    @NotNullByDefault
    private static final class BlockEntry {
        /// Successor observations required before a block can seed a trace.
        private static final int HOT_SUCCESSOR_THRESHOLD = 64;

        /// Saturating upper bound for successor observation counts.
        private static final int MAX_SUCCESSOR_COUNT = 1 << 20;

        /// The guest program counter where this block starts.
        private final long pc;

        /// The decoded block metadata used for trace formation.
        private final DecodedBlock decodedBlock;

        /// The executable block target.
        private final RootCallTarget target;

        /// The most recently stable successor PC observed after executing this block.
        private long successorPc;

        /// The number of consecutive observations for `successorPc`.
        private int successorCount;

        /// Creates a decoded block entry.
        private BlockEntry(long pc, DecodedBlock decodedBlock, RootCallTarget target) {
            this.pc = pc;
            this.decodedBlock = decodedBlock;
            this.target = target;
        }

        /// Returns the guest program counter where this block starts.
        private long pc() {
            return pc;
        }

        /// Returns the decoded block metadata.
        private DecodedBlock decodedBlock() {
            return decodedBlock;
        }

        /// Returns the executable block target.
        private RootCallTarget target() {
            return target;
        }

        /// Records the successor observed after executing this block once.
        private void recordSuccessor(long nextPc) {
            if (successorPc == nextPc) {
                if (successorCount < MAX_SUCCESSOR_COUNT) {
                    successorCount++;
                }
                return;
            }

            successorPc = nextPc;
            successorCount = 1;
        }

        /// Returns true when the block has a hot successor suitable for trace formation.
        private boolean hasHotSuccessor() {
            return successorCount >= HOT_SUCCESSOR_THRESHOLD;
        }

        /// Returns the hot successor PC observed for this block.
        private long hotSuccessorPc() {
            return successorPc;
        }

        /// Returns true when this block is safe to include in a trace root.
        private boolean isTraceable() {
            for (DecodedInstruction instruction : decodedBlock.instructions()) {
                RiscVOperation operation = instruction.operation();
                if (operation == RiscVOperation.ECALL || operation == RiscVOperation.EBREAK) {
                    return false;
                }
            }
            return true;
        }
    }

    /// Stores one compiled trace target.
    ///
    /// @param startPc the guest program counter where the trace starts
    /// @param target the executable trace target
    @NotNullByDefault
    private record TraceEntry(
            long startPc,
            RootCallTarget target) {
    }

    /// Stores compiled hot traces in a primitive long-keyed open-addressing map.
    @NotNullByDefault
    private static final class TraceCache {
        /// The initial number of slots in the trace cache.
        private static final int INITIAL_CAPACITY = 64;

        /// The maximum number of decoded blocks included in one trace.
        private static final int MAX_TRACE_BLOCKS = 16;

        /// The resize threshold numerator for the trace cache load factor.
        private static final int LOAD_FACTOR_NUMERATOR = 1;

        /// The resize threshold denominator for the trace cache load factor.
        private static final int LOAD_FACTOR_DENOMINATOR = 2;

        /// The language instance used to create trace roots.
        private final RiscVLanguage language;

        /// Guest program counters stored in cache slots.
        private volatile long[] keys = new long[INITIAL_CAPACITY];

        /// Trace values stored in the slot matching `keys`.
        private volatile @Nullable TraceEntry[] values = new TraceEntry[INITIAL_CAPACITY];

        /// The number of occupied trace cache slots.
        private int size;

        /// Creates a trace cache for one dispatch node.
        private TraceCache(RiscVLanguage language) {
            this.language = language;
        }

        /// Returns the compiled trace for a guest program counter, or null on miss.
        private @Nullable TraceEntry get(long pc) {
            long[] currentKeys = keys;
            @Nullable TraceEntry[] currentValues = values;
            int slot = findSlot(pc, currentKeys, currentValues);
            return currentValues[slot];
        }

        /// Compiles a trace for a block whose successor edge is hot, unless one already exists.
        private void compileIfHot(Memory memory, BlockCache blocks, BlockEntry start) {
            if (!start.hasHotSuccessor() || !start.isTraceable() || get(start.pc()) != null) {
                return;
            }
            compileAndCache(memory, blocks, start);
        }

        /// Compiles and caches a trace after a miss on the unsynchronized fast path.
        private synchronized void compileAndCache(Memory memory, BlockCache blocks, BlockEntry start) {
            int slot = findSlot(start.pc(), keys, values);
            if (values[slot] != null || !start.hasHotSuccessor() || !start.isTraceable()) {
                return;
            }

            TraceBuildResult result = buildTrace(memory, blocks, start);
            if (result.blockCount() < 2) {
                return;
            }

            CompilerDirectives.transferToInterpreter();
            if ((size + 1) * LOAD_FACTOR_DENOMINATOR >= values.length * LOAD_FACTOR_NUMERATOR) {
                grow();
                slot = findSlot(start.pc(), keys, values);
            }

            TraceEntry trace = new TraceEntry(
                    start.pc(),
                    new RiscVMicroTraceRootNode(language, result.targets(), result.expectedNextPcs()).getCallTarget());
            keys[slot] = start.pc();
            values[slot] = trace;
            size++;
        }

        /// Builds one linear trace by following currently hot successor edges.
        private TraceBuildResult buildTrace(Memory memory, BlockCache blocks, BlockEntry start) {
            RootCallTarget[] targets = new RootCallTarget[MAX_TRACE_BLOCKS];
            long[] expectedNextPcs = new long[MAX_TRACE_BLOCKS - 1];
            long[] pcs = new long[MAX_TRACE_BLOCKS];
            int count = 0;
            BlockEntry current = start;

            while (count < MAX_TRACE_BLOCKS && current.isTraceable()) {
                pcs[count] = current.pc();
                targets[count] = current.target();
                count++;

                if (count == MAX_TRACE_BLOCKS) {
                    break;
                }

                if (!current.hasHotSuccessor()) {
                    break;
                }

                long successorPc = current.hotSuccessorPc();
                if (containsPc(pcs, count, successorPc)) {
                    break;
                }

                BlockEntry successor = blocks.getOrDecode(memory, successorPc);
                if (!successor.isTraceable()) {
                    break;
                }

                expectedNextPcs[count - 1] = successorPc;
                current = successor;
            }

            RootCallTarget[] trimmedTargets = new RootCallTarget[count];
            System.arraycopy(targets, 0, trimmedTargets, 0, count);
            long[] trimmedExpectedNextPcs = new long[Math.max(0, count - 1)];
            System.arraycopy(expectedNextPcs, 0, trimmedExpectedNextPcs, 0, trimmedExpectedNextPcs.length);
            return new TraceBuildResult(trimmedTargets, trimmedExpectedNextPcs);
        }

        /// Returns true when the supplied PC is already present in the trace prefix.
        private static boolean containsPc(long[] pcs, int count, long pc) {
            for (int index = 0; index < count; index++) {
                if (pcs[index] == pc) {
                    return true;
                }
            }
            return false;
        }

        /// Doubles the trace cache capacity and reinserts all existing entries.
        private void grow() {
            long[] oldKeys = keys;
            @Nullable TraceEntry[] oldValues = values;
            keys = new long[oldKeys.length << 1];
            values = new TraceEntry[keys.length];

            for (int index = 0; index < oldValues.length; index++) {
                TraceEntry trace = oldValues[index];
                if (trace != null) {
                    int slot = findSlot(oldKeys[index], keys, values);
                    keys[slot] = oldKeys[index];
                    values[slot] = trace;
                }
            }
        }

        /// Finds the trace cache slot containing the key or the first empty slot for it.
        private static int findSlot(long pc, long[] keys, @Nullable TraceEntry[] values) {
            int mask = values.length - 1;
            int slot = hash(pc) & mask;
            while (true) {
                TraceEntry trace = values[slot];
                if (trace == null || keys[slot] == pc) {
                    return slot;
                }
                slot = (slot + 1) & mask;
            }
        }

        /// Hashes a guest program counter for open addressing.
        private static int hash(long value) {
            long hash = value ^ (value >>> 32);
            hash ^= hash >>> 16;
            hash *= 0x9e37_79b9_7f4a_7c15L;
            hash ^= hash >>> 32;
            return (int) hash;
        }
    }

    /// Stores temporary trace build arrays trimmed to the number of selected blocks.
    ///
    /// @param targets the block call targets in trace order
    /// @param expectedNextPcs the expected successor PCs between adjacent trace blocks
    @NotNullByDefault
    private record TraceBuildResult(
            RootCallTarget @Unmodifiable [] targets,
            long @Unmodifiable [] expectedNextPcs) {
        /// Returns the number of blocks in this trace.
        private int blockCount() {
            return targets.length;
        }
    }

    /// Stores decoded guest blocks in a primitive long-keyed open-addressing map.
    @NotNullByDefault
    private static final class BlockCache {
        /// The initial number of slots in the decoded block cache.
        private static final int INITIAL_CAPACITY = 256;

        /// The resize threshold numerator for the cache load factor.
        private static final int LOAD_FACTOR_NUMERATOR = 1;

        /// The resize threshold denominator for the cache load factor.
        private static final int LOAD_FACTOR_DENOMINATOR = 2;

        /// The language instance used to create micro-bytecode block roots.
        private final RiscVLanguage language;

        /// Guest program counters stored in cache slots.
        private volatile long[] keys = new long[INITIAL_CAPACITY];

        /// Cache values stored in the slot matching `keys`.
        private volatile @Nullable BlockEntry[] values = new BlockEntry[INITIAL_CAPACITY];

        /// The number of occupied cache slots.
        private int size;

        /// Creates a decoded block cache for one root node.
        private BlockCache(RiscVLanguage language) {
            this.language = language;
        }

        /// Returns the language instance used to compile blocks and traces.
        private RiscVLanguage language() {
            return language;
        }

        /// Returns the cached block for a guest program counter, decoding it on a cache miss.
        private BlockEntry getOrDecode(Memory memory, long pc) {
            long[] currentKeys = keys;
            @Nullable BlockEntry[] currentValues = values;
            int slot = findSlot(pc, currentKeys, currentValues);
            BlockEntry block = currentValues[slot];
            if (block != null) {
                return block;
            }

            return decodeAndCache(memory, pc);
        }

        /// Decodes and caches a block after a miss on the unsynchronized fast path.
        private synchronized BlockEntry decodeAndCache(Memory memory, long pc) {
            int slot = findSlot(pc, keys, values);
            BlockEntry block = values[slot];
            if (block != null) {
                return block;
            }

            CompilerDirectives.transferToInterpreter();
            if ((size + 1) * LOAD_FACTOR_DENOMINATOR >= values.length * LOAD_FACTOR_NUMERATOR) {
                grow();
                slot = findSlot(pc, keys, values);
            }

            DecodedBlock decodedBlock = RiscVDecoder.decodeBlock(memory, pc);
            block = new BlockEntry(pc, decodedBlock, RiscVMicroBlockCompiler.compile(language, decodedBlock));
            keys[slot] = pc;
            values[slot] = block;
            size++;
            return block;
        }

        /// Doubles the cache capacity and reinserts all existing entries.
        private void grow() {
            long[] oldKeys = keys;
            @Nullable BlockEntry[] oldValues = values;
            keys = new long[oldKeys.length << 1];
            values = new BlockEntry[keys.length];

            for (int index = 0; index < oldValues.length; index++) {
                BlockEntry block = oldValues[index];
                if (block != null) {
                    int slot = findSlot(oldKeys[index], keys, values);
                    keys[slot] = oldKeys[index];
                    values[slot] = block;
                }
            }
        }

        /// Finds the cache slot containing the key or the first empty slot for it.
        private static int findSlot(long pc, long[] keys, @Nullable BlockEntry[] values) {
            int mask = values.length - 1;
            int slot = hash(pc) & mask;
            while (true) {
                BlockEntry block = values[slot];
                if (block == null || keys[slot] == pc) {
                    return slot;
                }
                slot = (slot + 1) & mask;
            }
        }

        /// Hashes a guest program counter for open addressing.
        private static int hash(long value) {
            long hash = value ^ (value >>> 32);
            hash ^= hash >>> 16;
            hash *= 0x9e37_79b9_7f4a_7c15L;
            hash ^= hash >>> 32;
            return (int) hash;
        }
    }
}
