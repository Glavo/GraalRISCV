// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.parser.ElfImage;
import org.glavo.riscv.parser.ElfLoader;
import org.glavo.riscv.runtime.fs.GuestFileSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.charset.StandardCharsets;

/// Loads a Linux user-mode ELF executable and prepares its initial process image.
@NotNullByDefault
public final class LinuxProgramLoader {
    /// The number of leading executable bytes used by Linux binary format probes.
    private static final int BINARY_FORMAT_HEADER_SIZE = 256;

    /// The maximum number of script interpreter rewrites accepted for one executable.
    private static final int MAX_SCRIPT_RECURSION = 4;

    /// The initial Linux user stack backing size.
    private static final long INITIAL_STACK_SIZE = 8L * 1024L * 1024L;

    /// The deterministic base address used for guest-loaded position-independent executables.
    private static final long DYNAMIC_EXECUTABLE_BASE = 0x0040_0000L;

    /// The alignment used when assigning load bias values to `ET_DYN` images.
    private static final long DYNAMIC_LOAD_ALIGNMENT = 0x0020_0000L;

    /// Prevents construction of this utility class.
    private LinuxProgramLoader() {
    }

    /// Resolves Linux binary formats that rewrite the executable and argument vector before ELF loading.
    ///
    /// @param executableBytes the first executable image bytes
    /// @param executablePath the guest path for the first executable image, or null for host-loaded bytes
    /// @param fileSystem the guest filesystem used to read interpreter files
    /// @param arguments the original argument vector exposed to `execve`
    public static ResolvedExecutable resolveExecutable(
            byte @Unmodifiable [] executableBytes,
            @Nullable String executablePath,
            GuestFileSystem fileSystem,
            String @Unmodifiable [] arguments) {
        return resolveExecutable(executableBytes, executablePath, fileSystem, arguments, 0);
    }

    /// Resolves one recursive Linux script interpreter step.
    private static ResolvedExecutable resolveExecutable(
            byte @Unmodifiable [] executableBytes,
            @Nullable String executablePath,
            GuestFileSystem fileSystem,
            String @Unmodifiable [] arguments,
            int scriptDepth) {
        @Nullable ScriptInterpreter scriptInterpreter = parseScriptInterpreter(executableBytes);
        if (scriptInterpreter == null) {
            return new ResolvedExecutable(executableBytes, executablePath, arguments);
        }
        if (scriptDepth >= MAX_SCRIPT_RECURSION) {
            throw new RiscVException("Script interpreter recursion limit exceeded: " + executablePath);
        }
        if (executablePath == null) {
            throw new RiscVException("Script executable requires a guest path");
        }

        String interpreterPath = scriptInterpreter.path();
        byte[] interpreterBytes = fileSystem.readFile(interpreterPath);
        String[] interpreterArguments = scriptInterpreter.arguments(executablePath, arguments);
        return resolveExecutable(
                interpreterBytes,
                interpreterPath,
                fileSystem,
                interpreterArguments,
                scriptDepth + 1);
    }

    /// Parses a Linux `#!` interpreter line from executable bytes, or null for non-script inputs.
    private static @Nullable ScriptInterpreter parseScriptInterpreter(byte @Unmodifiable [] executableBytes) {
        if (executableBytes.length < 2 || executableBytes[0] != '#' || executableBytes[1] != '!') {
            return null;
        }

        int headerLength = Math.min(executableBytes.length, BINARY_FORMAT_HEADER_SIZE);
        int lineEnd = 2;
        while (lineEnd < headerLength && executableBytes[lineEnd] != '\n') {
            lineEnd++;
        }
        if (lineEnd == BINARY_FORMAT_HEADER_SIZE && lineEnd < executableBytes.length) {
            throw new RiscVException("Script interpreter line is too long");
        }

        int contentStart = skipSpacesAndTabs(executableBytes, 2, lineEnd);
        int contentEnd = trimTrailingSpacesAndTabs(executableBytes, contentStart, lineEnd);
        if (contentStart == contentEnd) {
            throw new RiscVException("Script interpreter path is missing");
        }

        int pathEnd = contentStart;
        while (pathEnd < contentEnd && !isSpaceOrTab(executableBytes[pathEnd])) {
            pathEnd++;
        }
        String path = new String(executableBytes, contentStart, pathEnd - contentStart, StandardCharsets.UTF_8);
        @Nullable String argument = null;
        int argumentStart = skipSpacesAndTabs(executableBytes, pathEnd, contentEnd);
        if (argumentStart < contentEnd) {
            argument = new String(
                    executableBytes,
                    argumentStart,
                    contentEnd - argumentStart,
                    StandardCharsets.UTF_8);
        }
        return new ScriptInterpreter(path, argument);
    }

    /// Skips ASCII spaces and tabs in a byte range.
    private static int skipSpacesAndTabs(byte @Unmodifiable [] bytes, int start, int end) {
        int index = start;
        while (index < end && isSpaceOrTab(bytes[index])) {
            index++;
        }
        return index;
    }

    /// Trims ASCII spaces and tabs from the end of a byte range.
    private static int trimTrailingSpacesAndTabs(byte @Unmodifiable [] bytes, int start, int end) {
        int index = end;
        while (index > start && isSpaceOrTab(bytes[index - 1])) {
            index--;
        }
        return index;
    }

    /// Returns true when a shebang byte is an ASCII space or tab.
    private static boolean isSpaceOrTab(byte value) {
        return value == ' ' || value == '\t';
    }

    /// Parses an executable and its optional dynamic interpreter.
    ///
    /// @param executableBytes the executable ELF bytes
    /// @param executablePath the guest executable path used for `AT_EXECFN`, or null for host-loaded programs
    /// @param fileSystem the guest filesystem used to read dynamic interpreters
    /// @param pageSize the guest base page size
    public static LoadedProgram load(
            byte @Unmodifiable [] executableBytes,
            @Nullable String executablePath,
            GuestFileSystem fileSystem,
            long pageSize) {
        ElfImage executable = ElfLoader.load(executableBytes);
        LoadedImage loadedExecutable = loadImage(executable, DYNAMIC_EXECUTABLE_BASE, pageSize);
        @Nullable LoadedImage loadedInterpreter = null;
        @Nullable String interpreterPath = executable.interpreterPath();
        if (interpreterPath != null) {
            byte[] interpreterBytes = fileSystem.readFile(interpreterPath);
            ElfImage interpreter = ElfLoader.load(interpreterBytes);
            if (interpreter.hasInterpreter()) {
                throw new RiscVException("ELF interpreter must not request another interpreter: " + interpreterPath);
            }
            loadedInterpreter = loadImage(
                    interpreter,
                    alignUp(
                            Math.max(loadedExecutable.endAddress(), DYNAMIC_EXECUTABLE_BASE) + DYNAMIC_LOAD_ALIGNMENT,
                            DYNAMIC_LOAD_ALIGNMENT),
                    pageSize);
        }
        return new LoadedProgram(loadedExecutable, loadedInterpreter, executablePath);
    }

    /// Maps, initializes, protects, and stacks a loaded program in an empty guest address space.
    ///
    /// @param memory the guest address space
    /// @param program the loaded executable metadata
    /// @param arguments the argument vector exposed to the guest
    /// @param pageSize the guest base page size
    public static LoadedProcess initialize(
            Memory memory,
            LoadedProgram program,
            String @Unmodifiable [] arguments,
            long pageSize) {
        long initialProgramBreak = loadImages(memory, program, pageSize);
        long stackPointer = initializeLinuxStack(memory, program, arguments, pageSize);
        return new LoadedProcess(initialProgramBreak, stackPointer);
    }

    /// Maps, initializes, protects, and stacks a loaded program with an explicit environment vector.
    ///
    /// @param memory the guest address space
    /// @param program the loaded executable metadata
    /// @param arguments the argument vector exposed to the guest
    /// @param environment the environment vector exposed to the guest
    /// @param pageSize the guest base page size
    public static LoadedProcess initialize(
            Memory memory,
            LoadedProgram program,
            String @Unmodifiable [] arguments,
            String @Unmodifiable [] environment,
            long pageSize) {
        return initialize(memory, program, arguments, environment, GuestCredentials.defaultUser(), pageSize);
    }

    /// Maps, initializes, protects, and stacks a loaded program with explicit environment and credentials.
    ///
    /// @param memory the guest address space
    /// @param program the loaded executable metadata
    /// @param arguments the argument vector exposed to the guest
    /// @param environment the environment vector exposed to the guest
    /// @param credentials the guest identity values exposed through auxv
    /// @param pageSize the guest base page size
    public static LoadedProcess initialize(
            Memory memory,
            LoadedProgram program,
            String @Unmodifiable [] arguments,
            String @Unmodifiable [] environment,
            GuestCredentials credentials,
            long pageSize) {
        long initialProgramBreak = loadImages(memory, program, pageSize);
        long stackPointer = initializeLinuxStack(memory, program, arguments, environment, credentials, pageSize);
        return new LoadedProcess(initialProgramBreak, stackPointer);
    }

    /// Initializes loadable ELF images and returns the initial program break.
    private static long loadImages(Memory memory, LoadedProgram program, long pageSize) {
        long initialProgramBreak = memory.baseAddress();
        for (LoadedImage loadedImage : program.images()) {
            for (ElfImage.LoadSegment segment : loadedImage.image().loadSegments()) {
                if (segment.memorySize() == 0) {
                    continue;
                }
                long runtimeAddress = loadedImage.runtimeAddress(segment.virtualAddress());
                long pageStart = alignDown(runtimeAddress, pageSize);
                long pageEnd = alignUp(runtimeAddress + segment.memorySize(), pageSize);
                long pageLength = pageEnd - pageStart;
                validateGuestRange(memory, pageStart, pageLength);
                mapLoadSegment(memory, pageStart, pageLength);
            }
        }

        for (LoadedImage loadedImage : program.images()) {
            for (ElfImage.LoadSegment segment : loadedImage.image().loadSegments()) {
                if (segment.memorySize() == 0) {
                    continue;
                }
                long runtimeAddress = loadedImage.runtimeAddress(segment.virtualAddress());
                long contentsLength = segment.contents().length;
                memory.load(runtimeAddress, segment.contents(), 0, (int) contentsLength);
                if (segment.memorySize() > contentsLength) {
                    memory.clear(runtimeAddress + contentsLength, segment.memorySize() - contentsLength);
                }
                initialProgramBreak = Math.max(
                        initialProgramBreak,
                        Math.min(alignUp(runtimeAddress + segment.memorySize(), 16), memory.endAddress()));
            }
        }

        for (LoadedImage loadedImage : program.images()) {
            for (ElfImage.LoadSegment segment : loadedImage.image().loadSegments()) {
                if (segment.memorySize() == 0) {
                    continue;
                }
                long runtimeAddress = loadedImage.runtimeAddress(segment.virtualAddress());
                long pageStart = alignDown(runtimeAddress, pageSize);
                long pageEnd = alignUp(runtimeAddress + segment.memorySize(), pageSize);
                long pageLength = pageEnd - pageStart;
                if (!memory.protect(pageStart, pageLength, segmentProtection(segment))) {
                    throw new RiscVException("Failed to protect ELF segment page range: segment="
                            + formatRange(pageStart, pageEnd));
                }
            }
        }
        return initialProgramBreak;
    }

    /// Adds explicit backing for one ELF load segment.
    private static void mapLoadSegment(Memory memory, long runtimeAddress, long memorySize) {
        if (memorySize == 0) {
            return;
        }
        int pageSize = memory.pageSize();
        long endAddress = runtimeAddress + memorySize;
        for (long pageAddress = runtimeAddress; pageAddress < endAddress; pageAddress += pageSize) {
            long pageLength = Math.min(pageSize, endAddress - pageAddress);
            if (memory.isBacked(pageAddress, pageLength)) {
                continue;
            }
            if (!memory.map(pageAddress, pageLength)) {
                throw new RiscVException("ELF segment overlaps another guest memory region: segment="
                        + formatRange(runtimeAddress, runtimeAddress + memorySize)
                        + ", memory=" + formatRange(memory.baseAddress(), memory.endAddress()));
            }
        }
    }

    /// Initializes the Linux user stack at the top of the contiguous guest memory segment.
    private static long initializeLinuxStack(
            Memory memory,
            LoadedProgram program,
            String @Unmodifiable [] arguments,
            long pageSize) {
        long stackTop = memory.endAddress();
        ensureStackBacking(memory, stackTop, pageSize);
        return LinuxInitialStack.initialize(
                memory,
                stackTop,
                arguments,
                program.executable().image(),
                program.executable().loadBias(),
                program.interpreterBase(),
                program.executablePath(),
                pageSize);
    }

    /// Initializes the Linux user stack with an explicit environment vector.
    private static long initializeLinuxStack(
            Memory memory,
            LoadedProgram program,
            String @Unmodifiable [] arguments,
            String @Unmodifiable [] environment,
            GuestCredentials credentials,
            long pageSize) {
        long stackTop = memory.endAddress();
        ensureStackBacking(memory, stackTop, pageSize);
        return LinuxInitialStack.initialize(
                memory,
                stackTop,
                arguments,
                environment,
                program.executable().image(),
                program.executable().loadBias(),
                program.interpreterBase(),
                program.executablePath(),
                credentials,
                pageSize);
    }

    /// Ensures that the initial stack range has guest memory backing.
    private static void ensureStackBacking(Memory memory, long stackTop, long pageSize) {
        if (memory.hasDenseInitialBacking()) {
            return;
        }

        long stackSize = Math.min(INITIAL_STACK_SIZE, memory.size());
        long stackBase = alignDown(stackTop - stackSize, pageSize);
        if (stackBase < memory.baseAddress()) {
            stackBase = memory.baseAddress();
        }
        long stackLength = stackTop - stackBase;
        if (stackLength <= 0 || !memory.map(stackBase, stackLength)) {
            throw new RiscVException("Failed to allocate guest stack backing: stack="
                    + formatRange(stackBase, stackTop)
                    + ", memory=" + formatRange(memory.baseAddress(), memory.endAddress()));
        }
    }

    /// Assigns a load bias to an ELF image.
    private static LoadedImage loadImage(ElfImage image, long requestedBase, long pageSize) {
        long loadBias = 0;
        if (image.isPositionIndependent()) {
            long minimumAddress = imageMinimumAddress(image);
            loadBias = alignUp(requestedBase, pageSize) - alignDown(minimumAddress, pageSize);
        }
        return new LoadedImage(image, loadBias);
    }

    /// Returns the lowest virtual address covered by a loadable image segment.
    private static long imageMinimumAddress(ElfImage image) {
        long minimumAddress = Long.MAX_VALUE;
        for (ElfImage.LoadSegment segment : image.loadSegments()) {
            minimumAddress = Math.min(minimumAddress, segment.virtualAddress());
        }
        if (minimumAddress == Long.MAX_VALUE) {
            throw new RiscVException("ELF image contains no PT_LOAD segments");
        }
        return minimumAddress;
    }

    /// Converts ELF segment flags into guest memory protection bits.
    private static long segmentProtection(ElfImage.LoadSegment segment) {
        long protection = 0;
        if ((segment.flags() & 0x4) != 0) {
            protection |= Memory.PROTECTION_READ;
        }
        if ((segment.flags() & 0x2) != 0) {
            protection |= Memory.PROTECTION_WRITE;
        }
        if ((segment.flags() & 0x1) != 0) {
            protection |= Memory.PROTECTION_EXECUTE;
        }
        return protection;
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

    /// Describes executable bytes and arguments after Linux binary format rewriting.
    ///
    /// @param executableBytes the final executable image bytes to parse as ELF
    /// @param executablePath the guest path for the final executable image, or null for host-loaded bytes
    /// @param arguments the final argument vector exposed to the loaded image
    public record ResolvedExecutable(
            byte @Unmodifiable [] executableBytes,
            @Nullable String executablePath,
            String @Unmodifiable [] arguments) {
        /// Creates a resolved executable snapshot.
        public ResolvedExecutable {
            executableBytes = executableBytes.clone();
            arguments = arguments.clone();
        }

        /// Returns a copy of the final executable image bytes.
        @Override
        public byte @Unmodifiable [] executableBytes() {
            return executableBytes.clone();
        }

        /// Returns a copy of the final argument vector.
        @Override
        public String @Unmodifiable [] arguments() {
            return arguments.clone();
        }
    }

    /// Stores one parsed Linux script interpreter directive.
    ///
    /// @param path the interpreter path from the `#!` line
    /// @param argument the optional single interpreter argument from the `#!` line
    private record ScriptInterpreter(String path, @Nullable String argument) {
        /// Builds the interpreter argument vector for a script execution.
        String @Unmodifiable [] arguments(String scriptPath, String @Unmodifiable [] originalArguments) {
            int originalTailLength = Math.max(0, originalArguments.length - 1);
            String[] result = new String[originalTailLength + (argument == null ? 2 : 3)];
            int index = 0;
            result[index++] = path;
            if (argument != null) {
                result[index++] = argument;
            }
            result[index++] = scriptPath;
            if (originalTailLength > 0) {
                System.arraycopy(originalArguments, 1, result, index, originalTailLength);
            }
            return result;
        }
    }

    /// Describes the fully load-biased program images that form one process startup.
    ///
    /// @param executable the main executable image
    /// @param interpreter the optional dynamic interpreter image
    /// @param executablePath the guest executable path used for `AT_EXECFN`, or null for host-loaded programs
    public record LoadedProgram(
            LoadedImage executable,
            @Nullable LoadedImage interpreter,
            @Nullable String executablePath) {
        /// Returns the images that must be mapped before execution starts.
        public LoadedImage @Unmodifiable [] images() {
            return interpreter == null
                    ? new LoadedImage[]{executable}
                    : new LoadedImage[]{executable, interpreter};
        }

        /// Returns the initial program counter.
        public long entryPoint() {
            return interpreter == null ? executable.runtimeEntryPoint() : interpreter.runtimeEntryPoint();
        }

        /// Returns the interpreter load base exposed through `AT_BASE`, or `ABSENT_ADDRESS` for static programs.
        public long interpreterBase() {
            return interpreter == null ? ElfImage.ABSENT_ADDRESS : interpreter.loadBias();
        }
    }

    /// Describes one ELF image after applying its process load bias.
    ///
    /// @param image the parsed ELF image
    /// @param loadBias the additive address load bias used for this image
    public record LoadedImage(ElfImage image, long loadBias) {
        /// Returns a load-biased runtime address.
        public long runtimeAddress(long address) {
            return address + loadBias;
        }

        /// Returns a load-biased optional runtime address.
        public long runtimeOptionalAddress(long address) {
            return address == ElfImage.ABSENT_ADDRESS ? ElfImage.ABSENT_ADDRESS : runtimeAddress(address);
        }

        /// Returns the runtime entry point.
        public long runtimeEntryPoint() {
            return runtimeAddress(image.entryPoint());
        }

        /// Returns the exclusive end of the highest loaded segment.
        public long endAddress() {
            long endAddress = 0;
            for (ElfImage.LoadSegment segment : image.loadSegments()) {
                endAddress = Math.max(endAddress, runtimeAddress(segment.virtualAddress()) + segment.memorySize());
            }
            return endAddress;
        }
    }

    /// Describes process-memory initialization results.
    ///
    /// @param initialProgramBreak the first program-break value after image loading
    /// @param stackPointer the initial guest stack pointer
    public record LoadedProcess(long initialProgramBreak, long stackPointer) {
    }
}
