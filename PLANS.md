# Plans

## Active Work

### 1. Complete RV64GC user-mode execution

- Keep RV64I, M, A, F, D, C, Zicsr, and Zifencei coverage stable while tightening edge semantics.
- Finish remaining floating-point exception-flag and NaN behavior work with focused decoder and execution tests.

### 2. Support broader static Linux user-mode programs

- Keep the simulator user-mode only; do not implement privileged mode, page tables, interrupts, devices, or Linux kernel boot.
- Expand ELF, auxv, stack, `mmap`, and static-runtime behavior only as acceptance workloads require.
- Harden the new Linux-like paged virtual memory implementation; do not reintroduce a long-term `MemorySegment` fallback.
- Treat `memorySize` as the guest virtual address window size, not as an eager host-memory allocation size.
- Continue moving ELF segments, stack, `brk`, anonymous `mmap`, `munmap`, `mprotect`, and `madvise` behavior toward one VMA and page-table implementation, reducing remaining syscall-side mapping duplication.
- Extend lazy page commit, committed-page limits, configurable power-of-two base page size, HugeTLB pool handling, and future native or file-mapped page allocators with broader Linux edge-case coverage.
- Preserve freestanding examples and existing static musl coverage while broadening toward larger libc and language-runtime programs.

### 3. Expand syscall compatibility

- Add syscall and flag edge cases only when backed by direct tests or acceptance workloads.
- Keep `--host-root` sandboxing based on TruffleFile and reject path escapes.
- Keep thread-style `clone` and futex behavior deterministic through Truffle `Env` guest threads.
- Keep unsupported syscall diagnostics actionable with syscall number, guest PC, and argument registers.

### 4. Maintain build, docs, and performance probes

- Keep Zig example tasks, CI aggregation tasks, and README coverage in sync as examples change.
- Continue measuring the embedded hot trace executor, especially trace length, trigger thresholds, batched store fast paths, the small trace direct-call PIC, and interaction with Graal compilation diagnostics.
- Continue improving paged-memory slow paths, especially cross-page accesses and richer fault reporting; package-level `MemoryAccess`, split read/write access-local Unsafe-coordinate page caching, direct-mapped software TLB hits, zero-fill page caching, window-sized VarHandle radix page-table lookup, and VMA access protections are now in place.
- Keep paged memory tests covering lazy commit, committed-page limits, configurable page size, HugeTLB pool accounting, and VMA split/merge behavior as the syscall layer is simplified.
- Keep CoreMark, Zig examples, and the local Go demo as acceptance workloads for the paged-memory migration.
- Profile the remaining generic complex floating-point arithmetic and conversion micro-op path before deciding whether to split it further; bit-level sign-injection, minimum/maximum, classify, compare, and raw move operations now use direct micro-op bodies.
- Evaluate deeper register staging for the custom micro-bytecode executor beyond the current local register-array access.
- Keep rejected performance experiments documented outside `PLANS.md`.
