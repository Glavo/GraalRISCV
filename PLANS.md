# Plans

## Current Status

- GraalRISCV is a user-mode RV64 emulator that implements the RVA23U64 user-mode profile with profile-valid VLEN.
- The supported execution target is statically linked 64-bit little-endian RISC-V ELF programs on a Linux-like user-mode runtime.
- The project remains user-mode only. Privileged mode, guest page tables, interrupts, devices, and Linux kernel boot are out of scope unless a later plan explicitly adds them.
- The current build can run unit tests, package smoke tests, example workloads, pinned `riscv-tests`, and repository-owned RVA22U64/RVA23U64 acceptance tests.

## Completed Milestones

### RVA22U64 Profile

- RV64I, M, A, F, D, C, Zicsr, Zicntr, Zihpm, Zihintpause, Zba, Zbb, Zbs, Zicbom, Zicbop, Zicboz, Zfhmin, and Zkt-required user-mode behavior are implemented.
- `Rva22Profile` is the central profile capability source used by Linux `riscv_hwprobe`, documentation, and tests.
- `Zkt` is reported for the simulator's data-independent guest-visible execution model; relevant integer comparison, bit-count, min/max, byte-combine, and high-half multiplication paths use fixed-shape helpers.
- Compatibility extensions such as `Zifencei`, `Zfa`, and `Zihintntl` remain supported without making them RVA22U64 mandatory conditions.
- Focused decoder, interpreter, micro-block, CSR, memory, atomic, floating-point, CBO, hint, `hwprobe`, and assembly acceptance tests cover the implemented profile behavior.

### Linux User-Mode Runtime

- Static freestanding, musl C, Go, SQLite, RVV examples including CRC, CoreMark, and `riscv-tests` workloads are supported by the current syscall and ELF runtime.
- File syscalls are sandboxed through `--mount` entries backed by `TruffleFile`; `--host-root` remains a compatibility alias for `--mount /=<path>`.
- `GuestProcess` and `GuestThread` hold Linux user-mode process and thread state for thread ids, clear-child-TID wakeups, robust futex lists, alternate signal stacks, restartable sequence registration, and signal masks.
- Guest time syscalls use `TimeSource`, including a non-default virtual-time implementation based on retired instruction count.

### Memory And Build Foundation

- `Memory` uses lazy committed heap `long[]` pages accessed through `jdk.internal.misc.Unsafe`, with software ITLB/DTLB fast paths, VMA tracking, committed-page limits, configurable base pages, and HugeTLB pool accounting.
- ELF segments, stack, `brk`, anonymous mappings, `munmap`, `mremap`, `mprotect`, and `madvise` are integrated with the paged-memory model for current workloads.
- Gradle builds examples and acceptance tests on Windows without Make, including pinned `rvv-examples`, `riscv-tests`, and `riscv-test-env` archives.
- Trace and micro-bytecode execution paths exist as the current performance foundation; diagnostic tracing and Truffle compilation logging remain opt-in.

### RVA23U64 Profile

- `Rva23Profile` is the central RVA23U64 capability source; Linux `riscv_hwprobe` reports RVA23U64 capabilities when VLEN is at least 128 bits and falls back to the RVA22U64 surface for shorter vector configurations.
- Additional RVA23U64 scalar instructions are implemented and unit-tested in the decoder, interpreter, and production micro-bytecode paths: `Zicond`, `Zimop`, `Zcmop`, `Zcb`, and `Zawrs`.
- `Supm` pointer masking is implemented through Linux `PR_SET_TAGGED_ADDR_CTRL` and `PR_GET_TAGGED_ADDR_CTRL`, with default PMLEN `0`, implemented PMLEN `7`, syscall tagged-address ABI gating through `PR_TAGGED_ADDR_ENABLE`, clone inheritance, and masking for instruction fetch, PC targets, memory accesses, atomics, CBO zeroing, and syscall guest pointers. Micro-block hot memory paths use a precomputed mask and avoid `ThreadLocal` lookups.
- Base RVV 1.0 is implemented in the interpreter and reusable micro-block fallback path: vector architectural state, configurable `VLEN`, vector CSRs, `vsetvli`/`vsetivli`/`vsetvl`, unit-stride/strided/indexed/segment/mask/whole-register vector load-store, fault-only-first load decoding and execution, single-width and mixed-width integer arithmetic, fixed-point rounding/saturation, compare, mask, scalar move, carry/borrow, reductions, slide, gather, compress, multiply, divide, remainder, floating-point arithmetic, reductions, fused operations, conversions, scalar moves, and focused unit and RVA23 acceptance coverage.
- Mandatory RVA23U64 vector additions on top of base `V` are implemented and tested: `Zvbb`, `Zvkb`, `Zvfhmin`, `Zvkt`, and the `Zvl128b` minimum-length requirement.
- Optional standard `Zvbc` vector carry-less multiplication is implemented and reported for `SEW=64` `vclmul.[vv,vx]` and `vclmulh.[vv,vx]`; the RVV CRC example exercises the standard `Zvbc` paths.
- `src/test/asm/rva23` and `testRva23Acceptance` build and run repository-owned RVA23 scalar, Supm, vector, vector bit-manipulation, and half-conversion acceptance coverage.
- Optional future-profile extensions such as `Zfh`, `Zacas`, and remaining vector crypto groups remain unreported until implemented.

## Remaining Work

### Maintain RVA23U64 Correctness

- Keep `Rva23Profile`, Linux `riscv_hwprobe`, README, CI aggregation, and RVA23 acceptance tests aligned whenever scalar, vector, Supm, or profile-reporting behavior changes.
- Broaden `testRva23Acceptance` with more illegal-opcode boundaries and vector corner cases as bugs or spec ambiguities are found.
- Preserve the `Zkt`/`Zvkt` audit boundary by keeping guest-visible data-dependent branches, table lookups, exceptions, counters, and syscall time out of mandatory data-independent instruction paths.
- Keep optional extensions such as `Zfh`, `Zacas`, `Zvkng`, and `Zvksg` as separate future work unless they are implemented and tested like `Zvbc`.

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
- Preserve the RVA22U64 `hwprobe` surface for configurations that do not meet RVA23U64 requirements, including vector lengths below 128 bits.
- Preserve the `Zkt` audit boundary by keeping guest-visible data-dependent branches, table lookups, exceptions, counters, and syscall time out of mandatory data-independent instruction paths.
- Keep `Zifencei` as a compatibility extension rather than a mandatory RVA22U64 user-profile requirement.

### Maintain Build And Performance Probes

- Keep README, CI aggregation, package smoke tests, example workloads, `riscv-tests`, and RVA22U64/RVA23U64 acceptance tests aligned with behavior changes.
- Tune the existing trace executor and micro-bytecode executor based on measurements before adding new performance subsystems.
- Keep rejected performance experiments documented outside `PLANS.md`.
