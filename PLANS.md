# Plans

## Active Work

### 1. Verify the Gradle-managed Zig workflow on non-Windows hosts

- Run `./gradlew -g .gradle-user-home checkHelloWorldExample` on Linux and macOS after Zig download and extraction are available.
- Record any non-Windows Zig download, extraction, or linker troubleshooting that is needed after real-toolchain verification.

### 2. Review Truffle execution compatibility

- Check that interpreter state, memory access, block caching, and instruction dispatch remain compatible with Truffle partial evaluation expectations.
- Keep CLI behavior stable while internal execution nodes evolve.

### 3. Document supported runtime surface

- Track release notes for supported ISA subsets, ELF limitations, syscall coverage, and known unsupported workloads.
