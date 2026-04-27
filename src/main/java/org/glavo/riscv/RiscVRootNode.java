package org.glavo.riscv;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.HashMap;

/// Executes one loaded RISC-V ELF image.
@NotNullByDefault
public final class RiscVRootNode extends RootNode {
    /// Resolves the current RISC-V context for this root node.
    private static final TruffleLanguage.ContextReference<RiscVContext> CONTEXT_REFERENCE =
            TruffleLanguage.ContextReference.create(RiscVLanguage.class);

    /// The parsed ELF image executed by this root node.
    private final ElfImage image;

    /// The lazily populated block cache for this execution root.
    private final HashMap<Long, BlockNode> blocks = new HashMap<>();

    /// Creates a root node for a parsed ELF image.
    public RiscVRootNode(RiscVLanguage language, ElfImage image) {
        super(language);
        this.image = image;
    }

    /// Executes the guest program and returns its exit code.
    @Override
    public Object execute(VirtualFrame frame) {
        RiscVContext context = CONTEXT_REFERENCE.get(this);
        try (Memory memory = new Memory(resolveMemoryBase(context), context.memorySize())) {
            MachineState state = createState(context, memory);
            while (true) {
                blockFor(memory, state.pc()).execute(state);
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
                        initialProgramBreak));
        state.setPc(image.entryPoint());
        state.setRegister(2, memory.endAddress() & ~0xfL);
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

    /// Returns a cached block for the supplied guest program counter.
    @CompilerDirectives.TruffleBoundary
    private BlockNode blockFor(Memory memory, long pc) {
        BlockNode block = blocks.get(pc);
        if (block == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            block = insert(RiscVDecoder.decodeBlock(memory, pc));
            blocks.put(pc, block);
        }
        return block;
    }

    /// Ensures a loaded ELF segment fits in guest memory.
    private static void validateGuestRange(Memory memory, long address, long length) {
        if (address < memory.baseAddress()
                || length < 0
                || address - memory.baseAddress() > memory.size() - length) {
            throw new RiscVException("ELF segment is outside guest memory: address=0x"
                    + Long.toUnsignedString(address, 16) + ", length=" + length);
        }
    }

    /// Rounds an address up to the requested power-of-two alignment.
    private static long alignUp(long address, long alignment) {
        long mask = alignment - 1;
        if (address > Long.MAX_VALUE - mask) {
            return address;
        }
        return (address + mask) & ~mask;
    }
}
