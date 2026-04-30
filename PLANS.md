# Plans

## Completed Work

### 1. RV64GC user-mode baseline

- The simulator now targets the complete RVA20U64 user-mode profile and keeps RV64I, M, A, F, D, C, Zicsr, and Zifencei coverage in decoder, execution, CSR, atomic, and floating-point tests.
- Floating-point execution covers fixed and dynamic rounding, exception flags, NaN boxing, canonical NaN behavior, fused multiply-add invalid-flag handling, and dedicated floating-point micro-opcode execution tests.
- RV64GC edge coverage now includes signed division overflow, division-by-zero results, register shift masks, word result sign extension, quiet and signaling NaN comparisons, `fmin` / `fmax` NaN selection, NaN class bits, and invalid conversion saturation.

### 2. Linux user-mode execution scope

- The implementation remains user-mode only; privileged mode, guest page tables, interrupts, devices, and Linux kernel boot have not been added.
- A narrow `riscv-test-env` bootstrap compatibility shim supports the machine CSRs and `mret` needed to enter its `p` tests without implementing full privileged mode.
- Static ELF execution now covers the bundled freestanding examples, static musl C examples, static Go hello-world and showcase examples, SQLite showcase, and CoreMark.
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
- Guest syscall time reads now go through a dedicated `TimeSource` abstraction instead of exposing `java.time.Clock` through the simulator context or syscall constructors.

### 5. Build, documentation, and performance groundwork

- Zig, Go, SQLite, package smoke, CI aggregation, README, and native-image tasks are in place for the current example and packaging workflows.
- README introductory wording is kept concise and states the implemented RVA22U64 user-mode profile without duplicating detailed workload or extension notes from later sections.
- Gradle can download pinned `riscv-tests` and `riscv-test-env` source archives, build RV64GC `p` ISA test ELFs with Zig CC on Windows without invoking Make, and run the built ELFs against the simulator.
- Performance diagnostics have been kept out of the default hot paths; tracing and Truffle compilation diagnostics remain opt-in debug or task-level probes.
- The current trace and micro-bytecode executor includes specialized block execution modes and dedicated floating-point micro-opcodes.

### 6. RVA22U64 complete user-mode profile

- Complete RVA22U64 user-mode support is implemented as a compatible default superset without adding a profile-selection CLI; `Zkt` is reported for the simulator's data-independent guest-visible execution model.
- A central `Rva22Profile` capability description now feeds Linux `riscv_hwprobe` extension bits, README wording, tests, and plan tracking, while `RiscVExtensions` retains Linux hwprobe bit constants.
- RVA22U64 behavior-only requirements are covered by the current user-mode model: atomic aligned 32-bit instruction fetches on executable memory for `Ziccif`, LR/SC forward-progress modeling for `Ziccrse`, AMO arithmetic support for `Ziccamoa`, ordinary misaligned scalar load/store support for `Zicclsm`, 64-byte reservation-set compatibility for `Za64rs`, and 64-byte cache-block reporting for `Zic64b`.
- `Zba`, `Zbb`, and `Zbs` integer bit-manipulation instructions are decoded and executed in the interpreter and through the micro-bytecode generic operation path.
- `Zkt`-relevant integer comparisons, high-half multiplication, bit counts, byte combining, and min/max execution paths use data-independent helper operations in both interpreter and micro-bytecode execution.
- `Zfhmin` half-precision data-transfer and conversion instructions are decoded and executed, including `FLH`, `FSH`, `FMV.X.H`, `FMV.H.X`, `FCVT.S.H`, `FCVT.H.S`, and the `D`-dependent `FCVT.D.H` / `FCVT.H.D` conversions.
- `Zfa` additional floating-point instructions are decoded and executed for the implemented RV64 F/D profile, including floating-point immediate loads, IEEE 754-2019 min/max-number, round-to-integer, modular double-to-word conversion, and quiet comparisons.
- `Zicbom` cache-block clean, flush, and invalidate decode as no-ops in the current cacheless model; `Zicboz` zeroes the containing 64-byte cache block through normal guest memory writes.
- Linux `riscv_hwprobe` now reports the implemented `Zkt`, `Zba`, `Zbb`, `Zbs`, `Zfa`, `Zfhmin`, `Zicboz`, `Zicbom`, `Zicbop`, `Zihintntl`, `Zihintpause`, `Zicntr`, `Zihpm`, `Zaamo`, and `Zalrsc` capabilities, plus 64-byte CBO block-size keys.
- User hardware performance counter CSRs `hpmcounter3` through `hpmcounter31` are exposed as deterministic read-only zero counters, matching the minimal current `Zihpm` model without inventing host performance events.
- Mandatory RVA22U64 `fence.tso` behavior is covered as a regular fence no-op distinct from `fence.i` instruction-fetch synchronization.
- Linux `riscv_hwprobe` reports ordinary misaligned scalar accesses as software-emulated support while keeping misaligned vector accesses unsupported.
- `hwprobe` regression coverage now asserts that mandatory RVA22U64 extension bits, including `Zkt`, and compatible extras are reported while optional or not-yet-implemented extensions such as `V`, full `Zfh`, `Zacas`, `Zicond`, and `Zawrs` are not reported.
- Focused interpreter, decoder, micro-bytecode, floating-point, hint, CBO, and `hwprobe` tests cover the implemented profile behavior and compatible extras.
- Repository-owned RVA22U64 assembly acceptance tests under `src/test/asm` now build with the pinned `riscv-tests` environment and run through the simulator, covering representative `Zkt`, `Zba`, `Zbb`, `Zbs`, `Zfhmin`, `Zfa`, `Zicbom`, `Zicboz`, `Zicbop`, `Zihintntl`, and `Zihintpause` instructions from real assembler-generated ELFs.
- RVA22U64 non-instruction acceptance now covers ordinary misaligned scalar load/store behavior, user counter CSR reads, and `fence.i` visibility for self-modifying code; decoded block, direct-call, and trace caches are keyed by an instruction-fetch generation refreshed by `fence.i`.
- LR/SC reservation tracking now records the reserved access width, store-conditional success requires matching address and data size, atomic memory operations require natural alignment, and repository-owned RVA22U64 acceptance covers LR/SC success and failure cases.

## Remaining Work

### 1. Broaden static Linux workload support

- Expand ELF, auxv, stack, `mmap`, and static-runtime behavior only when direct tests or acceptance workloads require it.
- Preserve freestanding examples and existing static musl, static Go, SQLite, and CoreMark coverage while broadening toward larger libc and language-runtime programs.
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

### 4. Maintain RVA22U64 complete user-mode readiness

- Keep RVA22U64 mandatory coverage in sync when decoder, memory, atomic, CSR, cache-block, timing-observable, or `hwprobe` behavior changes.
- Do not report optional RVA22U64 extensions such as `V`, full `Zfh`, `Zkn`, `Zks`, `Zicond`, `Zacas`, or `Zawrs` until they are implemented and tested.
- Preserve the `Zkt` audit boundary by keeping guest-visible data-dependent branches, table lookups, exceptions, counters, and syscall time out of mandatory data-independent instruction paths.
- Preserve existing `Zifencei` support as a compatibility extension even though it is not a mandatory RVA22U64 user-profile extension.

### 5. Maintain build, docs, and performance probes

- Keep Zig, Go, package smoke, CI aggregation, and README coverage in sync as examples change.
- Keep paged-memory tests covering lazy commit, committed-page limits, configurable page size, HugeTLB pool accounting, and current VMA split/merge behavior as the syscall layer is simplified.
- Keep freestanding, static musl, static Go, SQLite, CoreMark, and `riscv-tests` as acceptance workloads for the paged-memory migration and RV64GC behavior.
- Tune the existing trace executor instead of adding a separate branch-prediction subsystem: measure and adjust trace length, hot-successor thresholds, batched store fast paths, the trace direct-call PIC, trace lookup hashing, side-exit behavior, and interaction with Graal compilation diagnostics.
- Evaluate deeper register staging for the custom micro-bytecode executor beyond the current local register-array access.
- Treat `@TruffleBoundary` on hot generic instruction execution, MethodHandle or lambda-based instruction dispatch, broad ELF predecode, and broad synchronization removal as experiment-only ideas unless diagnostics identify them as real bottlenecks.
- Keep rejected performance experiments documented outside `PLANS.md`.
