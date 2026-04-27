package org.glavo.riscv;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.HashMap;

/// Executes one loaded RISC-V ELF image.
@NotNullByDefault
public final class RiscVRootNode extends RootNode {
    /// The frame slot that stores the guest memory for the active execution.
    private static final int MEMORY_FRAME_SLOT = 0;

    /// The frame slot that stores the architectural state for the active execution.
    private static final int STATE_FRAME_SLOT = 1;

    /// The number of static frame slots used by the root execution loop.
    private static final int EXECUTION_FRAME_SLOT_COUNT = 2;

    /// Resolves the current RISC-V context for this root node.
    private static final TruffleLanguage.ContextReference<RiscVContext> CONTEXT_REFERENCE =
            TruffleLanguage.ContextReference.create(RiscVLanguage.class);

    /// The parsed ELF image executed by this root node.
    private final ElfImage image;

    /// The lazily populated block cache for this execution root.
    private final HashMap<Long, BlockNode> blocks = new HashMap<>();

    /// The Truffle loop node that repeatedly dispatches guest basic blocks.
    @Child
    private LoopNode executionLoop = Truffle.getRuntime().createLoopNode(new GuestExecutionRepeatingNode());

    /// Creates a root node for a parsed ELF image.
    public RiscVRootNode(RiscVLanguage language, ElfImage image) {
        super(language, createFrameDescriptor());
        this.image = image;
    }

    /// Executes the guest program and returns its exit code.
    @Override
    public Object execute(VirtualFrame frame) {
        RiscVContext context = CONTEXT_REFERENCE.get(this);
        try (Memory memory = new Memory(resolveMemoryBase(context), context.memorySize())) {
            MachineState state = createState(context, memory);
            frame.setObjectStatic(MEMORY_FRAME_SLOT, memory);
            frame.setObjectStatic(STATE_FRAME_SLOT, state);
            try {
                executionLoop.execute(frame);
                throw new RiscVException("Guest execution loop terminated unexpectedly");
            } finally {
                state.syscalls().close();
                frame.clearObjectStatic(MEMORY_FRAME_SLOT);
                frame.clearObjectStatic(STATE_FRAME_SLOT);
            }
        } catch (ProgramExitException exit) {
            return exit.exitCode();
        }
    }

    /// Creates and initializes architectural state for a guest run.
    private MachineState createState(RiscVContext context, Memory memory) {
        long initialProgramBreak = memory.baseAddress();
        for (ElfImage.LoadSegment segment : image.loadSegments()) {
            long contentsLength = segment.contents().length;
            validateGuestRange(memory, segment.virtualAddress(), segment.memorySize());
            memory.load(segment.virtualAddress(), segment.contents(), 0, (int) contentsLength);
            if (segment.memorySize() > contentsLength) {
                memory.clear(segment.virtualAddress() + contentsLength, segment.memorySize() - contentsLength);
            }
            initialProgramBreak = Math.max(
                    initialProgramBreak,
                    Math.min(alignUp(segment.virtualAddress() + segment.memorySize(), 16), memory.endAddress()));
        }

        MachineState state = new MachineState(
                memory,
                context.maxInstructions(),
                context.trace(),
                image.tohostAddress(),
                image.fromhostAddress(),
                new GuestSyscalls(
                        memory,
                        context.env().in(),
                        context.env().out(),
                        context.env().err(),
                        initialProgramBreak,
                        context.env(),
                        context.hostRoot(),
                        context.clock()),
                context.env().err());
        state.setPc(image.entryPoint());
        state.setRegister(2, LinuxInitialStack.initialize(memory, memory.endAddress(), context.programArguments(), image));
        return state;
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

    /// Returns a cached block for program counters outside the dispatch inline cache.
    @CompilerDirectives.TruffleBoundary
    BlockNode blockForUncached(Memory memory, long pc) {
        BlockNode block = blocks.get(pc);
        if (block == null) {
            block = insert(RiscVDecoder.decodeBlock(memory, pc));
            blocks.put(pc, block);
        }
        return block;
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

    /// Creates the static frame slots used by the root execution loop.
    private static FrameDescriptor createFrameDescriptor() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder(EXECUTION_FRAME_SLOT_COUNT);
        builder.addSlots(EXECUTION_FRAME_SLOT_COUNT, FrameSlotKind.Static);
        return builder.build();
    }

    /// Repeats guest basic-block dispatch for the root Truffle loop node.
    @NotNullByDefault
    private static final class GuestExecutionRepeatingNode extends Node implements RepeatingNode {
        /// The self-specializing dispatch cache for hot guest basic blocks.
        @Child
        private BlockDispatchNode dispatch = new BlockDispatchNode();

        /// Executes one guest basic block and continues the enclosing loop.
        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            Memory memory = (Memory) frame.getObjectStatic(MEMORY_FRAME_SLOT);
            MachineState state = (MachineState) frame.getObjectStatic(STATE_FRAME_SLOT);
            dispatch.execute((RiscVRootNode) getRootNode(), memory, state);
            return true;
        }
    }
}
