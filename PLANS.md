# Plans

## Active Work

### 1. Complete RV64GC user-mode execution

- Keep RV64I, M, A, F, D, C, Zicsr, and Zifencei coverage stable while tightening edge semantics.
- Finish remaining floating-point exception-flag and NaN behavior work with focused decoder and execution tests.

### 2. Support broader static Linux user-mode programs

- Keep the simulator user-mode only; do not implement privileged mode, page tables, interrupts, devices, or Linux kernel boot.
- Expand ELF, auxv, stack, `mmap`, and static-runtime behavior only as acceptance workloads require.
- Replace the current memory model with Linux-like paged virtual memory in one migration; do not keep a long-term `MemorySegment` fallback.
- Treat `memorySize` as the guest virtual address window size, not as an eager host-memory allocation size.
- Model ELF segments, stack, `brk`, anonymous `mmap`, `munmap`, `mprotect`, and `madvise` through one VMA and page-table implementation.
- Commit backing pages lazily and enforce the configured committed-page limit before allocating new page backing.
- Support configurable base page size and an independent HugeTLB pool, with `MAP_HUGETLB` consuming reserved huge pages.
- Preserve freestanding examples and existing static musl coverage while broadening toward larger libc and language-runtime programs.

### 3. Expand syscall compatibility

- Add syscall and flag edge cases only when backed by direct tests or acceptance workloads.
- Keep `--host-root` sandboxing based on TruffleFile and reject path escapes.
- Keep thread-style `clone` and futex behavior deterministic through Truffle `Env` guest threads.
- Keep unsupported syscall diagnostics actionable with syscall number, guest PC, and argument registers.

### 4. Maintain build, docs, and performance probes

- Keep Zig example tasks, CI aggregation tasks, and README coverage in sync as examples change.
- Continue measuring the direct-call hot trace executor, especially trace length, trigger thresholds, and interaction with Graal compilation diagnostics.
- Add software ITLB and DTLB fast paths for paged memory, with cross-page and permission-fault accesses handled by slow paths.
- Keep paged memory tests covering lazy commit, committed-page limits, configurable page size, HugeTLB pool accounting, and VMA split/merge behavior.
- Keep CoreMark, Zig examples, and the local Go demo as acceptance workloads for the paged-memory migration.
- Profile the remaining generic complex floating-point micro-op path before deciding whether to split it further.
- Evaluate deeper register staging for the custom micro-bytecode executor beyond the current local register-array access.
- Keep rejected performance experiments documented outside `PLANS.md`.
