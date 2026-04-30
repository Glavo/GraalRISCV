# Plans

## Completed Work

### 1. RV64GC user-mode baseline

- The simulator now targets the complete RVA20U64 user-mode profile and keeps RV64I, M, A, F, D, C, Zicsr, and Zifencei coverage in decoder, execution, CSR, atomic, and floating-point tests.
- Floating-point execution covers fixed and dynamic rounding, exception flags, NaN boxing, canonical NaN behavior, fused multiply-add invalid-flag handling, and dedicated floating-point micro-opcode execution tests.
- RV64GC edge coverage now includes signed division overflow, division-by-zero results, register shift masks, word result sign extension, quiet and signaling NaN comparisons, `fmin` / `fmax` NaN selection, NaN class bits, and invalid conversion saturation.

### 2. Linux user-mode execution scope

- The implementation remains user-mode only; privileged mode, guest page tables, interrupts, devices, and Linux kernel boot have not been added.
- Static ELF execution now covers the bundled freestanding examples, static musl C examples, static Go hello-world example, and CoreMark.
- The default memory layout is Linux-like and sparse: base address `0`, a large virtual address window, and `memorySize` as a guest virtual address window size rather than an eager host-memory allocation size.

### 3. Paged virtual memory foundation

- `Memory` now uses heap `long[]` pages accessed through `jdk.internal.misc.Unsafe` base objects and byte offsets, with no long-term `MemorySegment` backend.
- Lazy page commit, committed-page limits, configurable power-of-two base page size, software DTLB and ITLB fast paths, HugeTLB pool accounting, VMA split/merge, access protection, and cross-page accesses are implemented and covered by focused tests.
- `MAP_HUGETLB` consumes the configured huge-page pool, and `madvise(MADV_HUGEPAGE)` / `madvise(MADV_NOHUGEPAGE)` record VMA preference without changing default access behavior.

### 4. Linux syscall compatibility foundation

- File syscalls are sandboxed under `--mount` entries through `TruffleFile`, including root-mount defaults, subdirectory overlays, path normalization, and path-escape rejection. `--host-root` remains as a compatibility alias for `--mount /=<path>`.
- ELF segments, the initial stack, auxv, `brk`, anonymous `mmap`, `munmap`, `mremap`, `mprotect`, and `madvise` are wired through the current paged-memory implementation for the accepted static workloads.
- `GuestProcess` and `GuestThread` state now exists for thread ids, clear-child-TID wakeups, robust futex lists, alternate signal stacks, restartable sequence registration, and per-thread signal masks.
- Thread-style `clone`, futex wait/wake, deterministic process and signal setup syscalls, `epoll_pwait` signal-mask handling, and unsupported-syscall diagnostics with syscall number, guest PC, and argument registers are covered by tests.

### 5. Build, documentation, and performance groundwork

- Zig, Go, package smoke, CI aggregation, README, and native-image tasks are in place for the current example and packaging workflows.
- Performance diagnostics have been kept out of the default hot paths; tracing and Truffle compilation diagnostics remain opt-in debug or task-level probes.
- The current trace and micro-bytecode executor includes specialized block execution modes and dedicated floating-point micro-opcodes.

## Remaining Work

### 1. Broaden static Linux workload support

- Expand ELF, auxv, stack, `mmap`, and static-runtime behavior only when direct tests or acceptance workloads require it.
- Preserve freestanding examples and existing static musl, static Go, and CoreMark coverage while broadening toward larger libc and language-runtime programs.
- Keep the simulator user-mode only; do not implement privileged mode, guest page tables, interrupts, devices, or Linux kernel boot.

### 2. Continue the memory and mapping cleanup

- Continue moving ELF segments, stack, `brk`, anonymous `mmap`, `munmap`, `mprotect`, and `madvise` behavior toward one VMA and page-table implementation, reducing remaining syscall-side mapping duplication.
- Extend lazy page commit, committed-page limits, configurable base page size, HugeTLB pool handling, and VMA split/merge coverage with broader Linux edge-case tests.
- Preserve the `Unsafe` base-object plus byte-offset page backing shape so future native or file-mapped allocators can be added without reintroducing a long-term `MemorySegment` backend.
- Continue improving paged-memory hot and slow paths, especially `MemoryAccess` local-cache checks, cross-page accesses, richer fault reporting, TLB lookup behavior, page-table lookup costs, and VMA protection checks.

### 3. Expand syscall and thread compatibility

- Add syscall and flag edge cases only when backed by direct tests or acceptance workloads.
- Continue expanding the `GuestProcess` / `GuestThread` split for signal delivery state and additional clone flag semantics.
- Keep thread-style `clone` and futex behavior deterministic through Truffle `Env` guest threads.
- Keep unsupported syscall diagnostics actionable with syscall number, guest PC, and argument registers.

### 4. Maintain build, docs, and performance probes

- Keep Zig, Go, package smoke, CI aggregation, and README coverage in sync as examples change.
- Keep paged-memory tests covering lazy commit, committed-page limits, configurable page size, HugeTLB pool accounting, and current VMA split/merge behavior as the syscall layer is simplified.
- Keep freestanding, static musl, static Go, and CoreMark examples as acceptance workloads for the paged-memory migration.
- Tune the existing trace executor instead of adding a separate branch-prediction subsystem: measure and adjust trace length, hot-successor thresholds, batched store fast paths, the trace direct-call PIC, trace lookup hashing, side-exit behavior, and interaction with Graal compilation diagnostics.
- Evaluate deeper register staging for the custom micro-bytecode executor beyond the current local register-array access.
- Treat `@TruffleBoundary` on hot generic instruction execution, MethodHandle or lambda-based instruction dispatch, broad ELF predecode, and broad synchronization removal as experiment-only ideas unless diagnostics identify them as real bottlenecks.
- Keep rejected performance experiments documented outside `PLANS.md`.
