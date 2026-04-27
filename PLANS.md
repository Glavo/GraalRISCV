# Plans

## Active Work

### 1. Verify the C example workflow with the Gradle-managed Zig toolchain

- Run `./gradlew -g .gradle-user-home checkHelloWorldExample` on Linux and macOS after Zig download and extraction are available.
- Record any non-Windows Zig download, extraction, or linker troubleshooting that is needed after real-toolchain verification.

### 2. Harden ELF loading and diagnostics

- Preserve the current `jelf`-based parser unless a specific compatibility or maintenance issue proves it inadequate.

### 3. Strengthen syscall and host I/O behavior

- Keep host I/O behavior deterministic enough for tests while still mapping stdout and stderr to the CLI process.

### 4. Improve Truffle integration and execution structure

- Split large instruction dispatch logic into smaller execution helpers or node groups where it reduces risk and improves profiling clarity.
- Check that interpreter state, memory access, and block execution remain compatible with Truffle partial evaluation expectations.
- Keep the public CLI stable while internal execution nodes evolve.

### 5. Prepare packaging and CI workflow

- Add CI-ready Gradle verification steps that compile main code, compile tests, run unit tests, and build the distribution artifacts with workspace-local Gradle state.
- Add a packaged binary smoke test that runs a tiny checked-in ELF fixture or a generated fixture that does not require an external toolchain.
- Keep the Gradle-managed Zig setup cacheable and CI-friendly so contributors do not need a separately installed RISC-V toolchain.
- Track release notes for supported ISA subsets, ELF limitations, syscall coverage, and known unsupported workloads.
