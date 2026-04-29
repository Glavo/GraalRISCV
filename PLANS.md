# Plans

## Active Work

### 1. Complete RV64GC user-mode execution

- Keep RV64I, M, A, F, D, C, Zicsr, and Zifencei coverage stable while tightening edge semantics.
- Finish remaining floating-point exception-flag and NaN behavior work with focused decoder and execution tests.

### 2. Support broader static Linux user-mode programs

- Keep the simulator user-mode only; do not implement privileged mode, page tables, interrupts, devices, or Linux kernel boot.
- Expand ELF, auxv, stack, `mmap`, and static-runtime behavior only as acceptance workloads require.
- Continue the sparse-memory migration by moving ELF segments, stack, and `brk` backing from the initial window into explicit regions.
- Keep both native and heap-backed memory regions supported through the shared region table.
- Preserve freestanding examples and existing static musl coverage while broadening toward larger libc and language-runtime programs.

### 3. Expand syscall compatibility

- Add syscall and flag edge cases only when backed by direct tests or acceptance workloads.
- Keep `--host-root` sandboxing based on TruffleFile and reject path escapes.
- Keep thread-style `clone` and futex behavior deterministic through Truffle `Env` guest threads.
- Keep unsupported syscall diagnostics actionable with syscall number, guest PC, and argument registers.

### 4. Maintain build, docs, and performance probes

- Keep Zig example tasks, CI aggregation tasks, and README coverage in sync as examples change.
- Continue measuring the direct-call hot trace executor, especially trace length, trigger thresholds, and interaction with Graal compilation diagnostics.
- Add load/store region inline caches after sparse region-table correctness is stable.
- Profile the remaining generic complex floating-point micro-op path before deciding whether to split it further.
- Evaluate deeper register staging for the custom micro-bytecode executor beyond the current local register-array access.
- Keep rejected performance experiments documented outside `PLANS.md`.
