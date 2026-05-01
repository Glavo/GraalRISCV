# Plans

## Current Status

- GraalRISCV is a user-mode RV64 emulator for statically linked 64-bit little-endian RISC-V ELF programs on a Linux-like runtime.
- The implemented profile is RVA23U64 when the configured VLEN is profile-valid; shorter vector configurations expose the RVA22U64 capability surface.
- The project remains user-mode only. Privileged mode, guest page tables, interrupts, devices, and Linux kernel boot stay out of scope unless a later plan explicitly adds them.
- The build can run unit tests, package smoke tests, example workloads, pinned `riscv-tests`, and repository-owned RVA22U64/RVA23U64 acceptance tests.

## Completed

- RVA22U64 mandatory user-mode behavior is implemented and reported through the centralized `Rva22Profile` capability source.
- RVA23U64 profile support is implemented through `Rva23Profile`, including scalar additions, Supm pointer masking through Linux `prctl`, base RVV 1.0, mandatory vector additions, and the `Zvl128b` minimum-length requirement.
- `Zkt` and `Zvkt` are reported for the simulator's data-independent guest-visible execution model.
- Optional standard `Zvbc` is implemented and reported for `SEW=64` `vclmul.[vv,vx]` and `vclmulh.[vv,vx]`; the RVV CRC example exercises the standard `Zvbc` paths.
- The Linux user-mode runtime supports current static freestanding, musl C, Go, SQLite, RVV examples including CRC, CoreMark, and `riscv-tests` workloads.
- File syscalls are sandboxed through `--mount` entries backed by `TruffleFile`; `--host-root` remains a compatibility alias for `--mount /=<path>`.
- Guest process/thread state covers current thread ids, clear-child-TID wakeups, robust futex lists, alternate signal stacks, restartable sequence registration, signal masks, and deterministic guest time through `TimeSource`.
- `Memory` uses lazy committed heap `long[]` pages accessed through `jdk.internal.misc.Unsafe`, with software ITLB/DTLB fast paths, VMA tracking, committed-page limits, configurable base pages, and HugeTLB pool accounting.
- Gradle builds examples and acceptance tests on Windows without Make, including pinned `rvv-examples`, `riscv-tests`, and `riscv-test-env` archives.

## Remaining Work

### ISA And Profile Correctness

- Keep `Rva22Profile`, `Rva23Profile`, Linux `riscv_hwprobe`, README, CI aggregation, and acceptance tests aligned whenever ISA or profile-reporting behavior changes.
- Broaden RVA22U64/RVA23U64 acceptance coverage with illegal-opcode boundaries and vector corner cases as bugs or spec ambiguities are found.
- Preserve the `Zkt`/`Zvkt` audit boundary by keeping guest-visible data-dependent branches, table lookups, exceptions, counters, and syscall time out of mandatory data-independent instruction paths.
- Keep optional extensions such as `Zfh`, `Zacas`, `Zvkng`, and `Zvksg` as separate future work unless they are implemented, tested, and reported like `Zvbc`.

### Linux Runtime Compatibility

- Expand ELF, auxv, stack, `mmap`, and static-runtime behavior only when direct tests or real workloads require it.
- Add syscall and flag edge cases as workloads need them, keeping unsupported-syscall diagnostics actionable.
- Continue improving `GuestProcess` / `GuestThread` separation for signal delivery and additional clone semantics while keeping thread behavior deterministic.

### Memory And Mapping

- Reduce syscall-side mapping duplication by moving ELF segments, stack, `brk`, anonymous mappings, `munmap`, `mprotect`, and `madvise` behavior toward one VMA and page-table implementation.
- Preserve the `Unsafe` base-object plus byte-offset backing shape so native or file-mapped page backings can be added later.
- Broaden tests for lazy commit, committed-page limits, page-size configuration, HugeTLB accounting, VMA split/merge behavior, protection checks, and cross-page accesses.

### Build And Performance

- Keep README, CI aggregation, package smoke tests, example workloads, `riscv-tests`, and RVA22U64/RVA23U64 acceptance tests aligned with behavior changes.
- Tune the existing trace executor and micro-bytecode executor based on measurements before adding new performance subsystems.
- Keep rejected performance experiments documented outside `PLANS.md`.
