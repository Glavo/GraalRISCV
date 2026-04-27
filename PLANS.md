# Plans

## Active Work

### 1. Verify the C example workflow with the Gradle-managed Zig toolchain

- Run `./gradlew -g .gradle-user-home checkHelloWorldExample` on Linux and macOS after Zig download and extraction are available.
- Record any non-Windows Zig download, extraction, or linker troubleshooting that is needed after real-toolchain verification.

### 2. Harden ELF loading and diagnostics

- Expand ELF loader tests for segment alignment, overlapping `PT_LOAD` ranges, non-readable or non-executable flags, unsupported dynamic sections, and relocation sections.
- Improve diagnostics for common linker layout mistakes, especially images whose load address falls outside the selected guest memory range.
- Preserve the current `jelf`-based parser unless a specific compatibility or maintenance issue proves it inadequate.
- Document supported ELF constraints: ELF64, little-endian, `EM_RISCV`, executable `PT_LOAD` images, no dynamic linking, and no runtime relocations.

### 3. Strengthen syscall and host I/O behavior

- Decide which additional Linux RISC-V ABI calls are required for the next useful C workload, such as `openat`, `mmap`, `munmap`, or signal-mask stubs.
- Keep host I/O behavior deterministic enough for tests while still mapping stdout and stderr to the CLI process.

### 4. Expand ISA coverage and execution tests

- Add table-driven decoder tests for RV64I edge cases, including sign extension, shift masks, branches, jumps, and misaligned control-flow targets.
- Add focused tests for RV64M arithmetic corner cases such as division by zero, signed overflow, high-half multiplication, and word-sized operations.
- Add more RVC fixtures for mixed 16-bit and 32-bit instruction streams.
- Define the intended behavior for RV64A atomics and either implement the missing pieces or reject unsupported instructions with clear diagnostics.

### 5. Improve Truffle integration and execution structure

- Split large instruction dispatch logic into smaller execution helpers or node groups where it reduces risk and improves profiling clarity.
- Add execution counters or tracing hooks that can be enabled from the CLI without changing guest behavior.
- Check that interpreter state, memory access, and block execution remain compatible with Truffle partial evaluation expectations.
- Keep the public CLI stable while internal execution nodes evolve.

### 6. Prepare packaging and CI workflow

- Add CI-ready Gradle verification steps that compile main code, compile tests, run unit tests, and build the distribution artifacts with workspace-local Gradle state.
- Add a packaged binary smoke test that runs a tiny checked-in ELF fixture or a generated fixture that does not require an external toolchain.
- Keep the Gradle-managed Zig setup cacheable and CI-friendly so contributors do not need a separately installed RISC-V toolchain.
- Track release notes for supported ISA subsets, ELF limitations, syscall coverage, and known unsupported workloads.
