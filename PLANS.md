# Plans

## Active Work

### 1. Complete RV64GC user-mode execution

- Keep RV64I, M, A, F, D, C, Zicsr, and Zifencei coverage stable while tightening edge semantics.
- Finish remaining floating-point exception-flag and NaN behavior work with focused decoder and execution tests.

### 2. Support broader static Linux user-mode programs

- Keep the simulator user-mode only; do not implement privileged mode, page tables, interrupts, devices, or Linux kernel boot.
- Expand ELF, auxv, stack, `mmap`, and static-runtime behavior only as acceptance workloads require.
- Harden the new Linux-like paged virtual memory implementation; do not reintroduce a long-term `MemorySegment` fallback.
- Keep the default memory layout Linux-like and sparse: base address `0`, a large virtual address window, and `memorySize` as a guest virtual address window size rather than an eager host-memory allocation size.
- Continue moving ELF segments, stack, `brk`, anonymous `mmap`, `munmap`, `mprotect`, and `madvise` behavior toward one VMA and page-table implementation, reducing remaining syscall-side mapping duplication.
- Extend lazy page commit, committed-page limits, configurable power-of-two base page size, HugeTLB pool handling, and future native or file-mapped page allocators with broader Linux edge-case coverage.
- Preserve freestanding examples and existing static musl coverage while broadening toward larger libc and language-runtime programs.

### 3. Expand syscall compatibility

- Add syscall and flag edge cases only when backed by direct tests or acceptance workloads.
- Keep `--host-root` sandboxing based on TruffleFile and reject path escapes.
- Keep thread-style `clone` and futex behavior deterministic through Truffle `Env` guest threads.
- Continue expanding the `GuestProcess`/`GuestThread` split for signal-mask and additional clone flag semantics.
- Keep unsupported syscall diagnostics actionable with syscall number, guest PC, and argument registers.

### 4. Maintain build, docs, and performance probes

- Keep Zig and Go example tasks, CI aggregation tasks, and README coverage in sync as examples change.
- Use performance diagnostics only when they do not change the default hot-path shape. Prefer external profilers, isolated benchmark builds, or debug-only execution variants over counter fields, nullable branches, or additional dispatch checks in default `MemoryAccess`, micro-op execution, block dispatch, or trace dispatch.
- Continue improving paged-memory hot and slow paths, especially `MemoryAccess` local-cache checks, cross-page accesses, richer fault reporting, TLB lookup behavior, page-table lookup costs, and VMA protection checks.
- Tune the existing trace executor instead of adding a separate branch-prediction subsystem: measure and adjust trace length, hot-successor thresholds, batched store fast paths, the trace direct-call PIC, trace lookup hashing, side-exit behavior, and interaction with Graal compilation diagnostics.
- Keep paged memory tests covering lazy commit, committed-page limits, configurable page size, HugeTLB pool accounting, and current VMA split/merge behavior as the syscall layer is simplified.
- Keep CoreMark, Zig examples, Go examples, and the local Go demo as acceptance workloads for the paged-memory migration.
- Evaluate deeper register staging for the custom micro-bytecode executor beyond the current local register-array access.
- Treat `@TruffleBoundary` on hot generic instruction execution, MethodHandle or lambda-based instruction dispatch, broad ELF predecode, and broad synchronization removal as experiment-only ideas unless diagnostics identify them as real bottlenecks.
- Keep rejected performance experiments documented outside `PLANS.md`.
