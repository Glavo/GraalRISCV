# Plans

## Active Work

### 1. Implement user-mode RV64GC instruction support

- Preserve the audited RV64GC user-mode opcode coverage for RV64I, M, A, F, D, C, Zicsr, and Zifencei while tightening edge semantics.
- Continue tightening floating-point exception-flag edge cases after the exact FMA invalid-product case, and settle the NaN payload preservation policy.
- Add focused decoder and execution tests for remaining exception-flag edge cases and NaN payload behavior.

### 2. Support static Linux user-mode programs

- Keep the simulator user-mode only; do not implement privileged mode, page tables, interrupts, devices, or Linux kernel boot.
- Extend auxv if broader static libc programs require entries beyond the current musl `printf` smoke path.
- Broaden statically linked Linux ELF coverage beyond the current resolved-segment musl `printf` baseline.
- Preserve existing freestanding ELF behavior and tests while adding Linux static program behavior.
- Keep the initial Linux stack on the contiguous guest-memory fast path; use sparse mappings for dynamic `mmap` regions
  rather than moving stack-heavy single-thread programs onto mapped-memory lookup paths.

### 3. Expand Linux syscall coverage for static libc

- Keep syscall handling deterministic within one guest process while supporting Linux thread-style `clone` through Truffle
  `Env`-created guest threads.
- Preserve `--host-root` as the TruffleFile-backed filesystem sandbox root and reject path escapes.
- Continue broadening file descriptor, memory-management, process, signal, time, and filesystem syscall support after adding
  `clock_getres`, `clock_nanosleep`, `get_robust_list`, `membarrier`, and `rseq` fallback behavior.
- Add Linux flag and edge-case compatibility only when covered by direct tests or acceptance workloads.
- Keep unsupported syscall diagnostics actionable by including the syscall number, guest PC, and argument registers.
- Add direct syscall tests for additional success paths, Linux-compatible error returns, filesystem sandboxing, configurable time, and deterministic random behavior.

### 4. Add static Linux C acceptance examples

- Keep no-toolchain package smoke tests available so basic CI remains possible without Zig.
- Continue adding broader static Linux acceptance examples beyond the current pthread join, runtime-services, and
  process/signal smoke checks as syscall and ELF coverage grows.

### 5. Keep build, CI, and documentation current

- Run `./gradlew -g .gradle-user-home checkHelloWorldExample` on Linux and macOS after Zig download and extraction are available.
- Record any non-Windows Zig download, extraction, or linker troubleshooting that is needed after real-toolchain verification.
- Keep `ciCheck`, `ciZigExampleCheck`, and `graalriscvCiIncludeZigExamples` coverage in sync as new static Linux
  C acceptance checks are added.
- Keep native-image packaging available through the opt-in `nativeCompile` and `nativeImageSmokeTest` tasks, and add it to CI only after Native Image toolchain availability is explicit.
- Continue profiling dispatch overhead, especially primitive block-cache lookup, mapped-memory accesses, remaining operation-group instruction body shape, and whether the `BlockNode` loop warrants a different compiled-block layout.
- Keep rejected performance experiments documented outside `PLANS.md`; do not re-enable the hot-block promotion cache or root `@Children` block adoption without CoreMark comparison.
- Keep the hot-loop compilation trace task as the current performance regression probe and expand it when broader Linux workloads become stable.
- Keep CLI behavior stable while internal execution nodes evolve.
- Track release notes for supported ISA subsets, ELF limitations, syscall coverage, and known unsupported workloads.
