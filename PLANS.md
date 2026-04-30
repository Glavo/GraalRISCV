# Plans

## Current Status

- GraalRISCV is a user-mode RV64 emulator that implements the RVA22U64 user-mode profile.
- The supported execution target is statically linked 64-bit little-endian RISC-V ELF programs on a Linux-like user-mode runtime.
- The project remains user-mode only. Privileged mode, guest page tables, interrupts, devices, and Linux kernel boot are out of scope unless a later plan explicitly adds them.
- The current build can run unit tests, package smoke tests, example workloads, pinned `riscv-tests`, and repository-owned RVA22U64 acceptance tests.

## Completed Milestones

### RVA22U64 Profile

- RV64I, M, A, F, D, C, Zicsr, Zicntr, Zihpm, Zihintpause, Zba, Zbb, Zbs, Zicbom, Zicbop, Zicboz, Zfhmin, and Zkt-required user-mode behavior are implemented.
- `Rva22Profile` is the central profile capability source used by Linux `riscv_hwprobe`, documentation, and tests.
- `Zkt` is reported for the simulator's data-independent guest-visible execution model; relevant integer comparison, bit-count, min/max, byte-combine, and high-half multiplication paths use fixed-shape helpers.
- Compatibility extensions such as `Zifencei`, `Zfa`, and `Zihintntl` remain supported without making them RVA22U64 mandatory conditions.
- Focused decoder, interpreter, micro-block, CSR, memory, atomic, floating-point, CBO, hint, `hwprobe`, and assembly acceptance tests cover the implemented profile behavior.

### Linux User-Mode Runtime

- Static freestanding, musl C, Go, SQLite, CoreMark, and `riscv-tests` workloads are supported by the current syscall and ELF runtime.
- File syscalls are sandboxed through `--mount` entries backed by `TruffleFile`; `--host-root` remains a compatibility alias for `--mount /=<path>`.
- `GuestProcess` and `GuestThread` hold Linux user-mode process and thread state for thread ids, clear-child-TID wakeups, robust futex lists, alternate signal stacks, restartable sequence registration, and signal masks.
- Guest time syscalls use `TimeSource`, including a non-default virtual-time implementation based on retired instruction count.

### Memory And Build Foundation

- `Memory` uses lazy committed heap `long[]` pages accessed through `jdk.internal.misc.Unsafe`, with software ITLB/DTLB fast paths, VMA tracking, committed-page limits, configurable base pages, and HugeTLB pool accounting.
- ELF segments, stack, `brk`, anonymous mappings, `munmap`, `mremap`, `mprotect`, and `madvise` are integrated with the paged-memory model for current workloads.
- Gradle builds examples and acceptance tests on Windows without Make, including pinned `riscv-tests` and `riscv-test-env` archives.
- Trace and micro-bytecode execution paths exist as the current performance foundation; diagnostic tracing and Truffle compilation logging remain opt-in.

## Remaining Work

### Implement RVA23U64 User-Mode Profile

- Treat RVA23U64 as a complete-profile target, not a partial feature flag. Do not report RVA23U64 through README, `riscv_hwprobe`, or profile constants until all mandatory user-mode requirements and acceptance tests pass.
- Add `Rva23Profile` alongside `Rva22Profile`, with mandatory, optional, compatibility, and reported-hwprobe capability sets derived from one source of truth.
- Implement the new mandatory scalar extensions over RVA22U64: `Zicond`, `Zimop`, `Zcmop`, `Zcb`, and `Zawrs`; promote existing `Zfa` and `Zihintntl` from compatibility extensions to RVA23U64 mandatory extensions.
- Implement vector support for correctness first through the interpreter: vector architectural state, vector CSRs, configurable `VLEN`, `V` 1.0 base behavior, `Zvfhmin`, `Zvbb`, and `Zvkt`. Micro-block and trace execution may initially fall back to shared interpreter semantics for vector instructions.
- Add `--vector-vlen <bits>`, defaulting to `128`, and accept only power-of-two values that can satisfy the implemented vector register layout. RVA23U64 reporting requires a configuration that satisfies the profile minimum.
- Implement `Supm` through the Linux `prctl` tagged-address control ABI instead of a CLI option. Support `PR_SET_TAGGED_ADDR_CTRL` and `PR_GET_TAGGED_ADDR_CTRL`, default to PMLEN `0`, allow selecting PMLEN `7`, and apply masking consistently to user-mode instruction fetch, memory accesses, atomics, CBO operations, and syscall guest pointers.
- Extend `riscv_hwprobe` reporting for RVA23U64 mandatory scalar and vector capabilities, update misaligned vector reporting to match the implementation, and continue leaving optional extensions such as `Zvkng`, `Zvksg`, and `Zacas` unreported until implemented.
- Add `src/test/asm/rva23` and a `testRva23Acceptance` Gradle task using `-march=rva23u64` or the equivalent explicit extension string; keep `testRva22Acceptance` as a regression suite.
- Cover scalar additions, vector configuration, vector integer/FP/load-store behavior, vector bit-manipulation, `Zvkt` data-independent behavior, Supm masking, `hwprobe`, and illegal opcode boundaries with unit and assembly acceptance tests.

### Broaden Static Linux Compatibility

- Expand ELF, auxv, stack, `mmap`, and static-runtime behavior only when direct tests or acceptance workloads require it.
- Add syscall and flag edge cases as real workloads need them, keeping unsupported-syscall diagnostics actionable.
- Continue improving `GuestProcess` / `GuestThread` separation for signal delivery and additional clone semantics while keeping thread behavior deterministic.

### Continue Memory And Mapping Cleanup

- Reduce remaining syscall-side mapping duplication by moving ELF segments, stack, `brk`, anonymous mappings, `munmap`, `mprotect`, and `madvise` behavior toward one VMA and page-table implementation.
- Preserve the `Unsafe` base-object plus byte-offset backing shape so native or file-mapped page backings can be added later.
- Keep broadening tests for lazy commit, committed-page limits, page-size configuration, HugeTLB accounting, VMA split/merge behavior, protection checks, and cross-page accesses.

### Maintain RVA22U64 Correctness

- Keep mandatory RVA22U64 coverage in sync when decoder, memory, atomic, CSR, cache-block, timing-observable, or `hwprobe` behavior changes.
- Do not report optional extensions such as `V`, full `Zfh`, `Zkn`, `Zks`, `Zicond`, `Zacas`, or `Zawrs` until they are implemented and tested.
- Preserve the `Zkt` audit boundary by keeping guest-visible data-dependent branches, table lookups, exceptions, counters, and syscall time out of mandatory data-independent instruction paths.
- Keep `Zifencei` as a compatibility extension rather than a mandatory RVA22U64 user-profile requirement.

### Maintain Build And Performance Probes

- Keep README, CI aggregation, package smoke tests, example workloads, `riscv-tests`, and RVA22U64 acceptance tests aligned with behavior changes.
- Tune the existing trace executor and micro-bytecode executor based on measurements before adding new performance subsystems.
- Keep rejected performance experiments documented outside `PLANS.md`.
