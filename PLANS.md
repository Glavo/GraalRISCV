# Plans

## Active Work

### 1. Implement user-mode RV64GC instruction support

- Preserve the audited RV64GC user-mode opcode coverage for RV64I, M, A, F, D, C, Zicsr, and Zifencei while tightening edge semantics.
- Complete the remaining floating-point exception-flag edge cases and settle the NaN payload preservation policy.
- Add focused decoder and execution tests for remaining exact exception-flag edge cases and NaN payload behavior.

### 2. Support static Linux user-mode programs

- Keep the simulator user-mode only; do not implement privileged mode, page tables, interrupts, devices, or Linux kernel boot.
- Extend auxv if broader static libc programs require entries beyond the current musl `printf` smoke path.
- Broaden statically linked Linux ELF coverage beyond the current resolved-segment musl `printf` baseline.
- Preserve existing freestanding ELF behavior and tests while adding Linux static program behavior.

### 3. Expand Linux syscall coverage for static libc

- Keep syscall handling deterministic and single-process unless a later plan explicitly expands that boundary.
- Preserve `--host-root` as the TruffleFile-backed filesystem sandbox root and reject path escapes.
- Broaden file descriptor support beyond the current regular-file read/write/create/truncate/append, positioned file I/O, directory creation/removal, path rename/unlink, cwd-aware `AT_FDCWD` resolution, directory fd, `getdents64`, deterministic filesystem status, descriptor sync, descriptor duplication, in-memory `pipe2`, in-memory `eventfd2`, basic `epoll_create1`/`epoll_ctl`/`epoll_pwait`, and minimal `fcntl` baseline, including additional Linux flags when needed.
- Add syscall implementations as broader static Linux workloads require beyond the current `chdir`/`fchdir`, `clone` parent-return compatibility, `dup`/`dup3`, `epoll_create1`/`epoll_ctl`/`epoll_pwait`, `eventfd2`, `faccessat`/`faccessat2`, `fcntl`, `fdatasync`, `fstatfs`, `fsync`, `ftruncate`, `futex` single-thread compatibility, `getcpu`, `getcwd`, `getdents64`, `getpgid`, `getppid`, `getrusage`, `gettimeofday`, `kill`/`tkill`/`tgkill` validation, `madvise`, `mkdirat`, `mprotect`, `nanosleep`, `newfstatat`, `pipe2`, `pread64`, `prctl`, `prlimit64`, `pwrite64`, `readlinkat`, `renameat`/`renameat2`, `riscv_hwprobe`, `sched_getaffinity`, `sched_yield`, `setsid`, `sigaltstack`, `statfs`, `statx`, `sync`, `syncfs`, `times`, `truncate`, `uname`, `unlinkat`, and user/group identity syscall baseline.
- Keep unsupported syscall diagnostics actionable by including the syscall number, guest PC, and argument registers.
- Add direct syscall tests for success paths, Linux-compatible error returns, filesystem sandboxing, configurable time, and deterministic random behavior.

### 4. Add static Linux C acceptance examples

- Keep no-toolchain package smoke tests available so basic CI remains possible without Zig.
- Add broader static Linux acceptance examples as syscall coverage grows beyond the current `printf`, `argc`/`argv`, sandboxed file I/O, positioned I/O, event polling, directory listing, file mutation, working-directory, filesystem-status, and `statx` metadata smoke paths.

### 5. Keep build, CI, and documentation current

- Run `./gradlew -g .gradle-user-home checkHelloWorldExample` on Linux and macOS after Zig download and extraction are available.
- Record any non-Windows Zig download, extraction, or linker troubleshooting that is needed after real-toolchain verification.
- Keep `ciCheck` covering compile, tests, package artifacts, no-toolchain smoke checks, and the new static Linux C acceptance checks when Zig is available.
- Keep native-image packaging available through the opt-in `nativeCompile` and `nativeImageSmokeTest` tasks, and add it to CI only after Native Image toolchain availability is explicit.
- Continue profiling now that Gradle uses a project-local Polyglot resource cache, scalar initial-memory accesses delegate alignment and range checks to `MemorySegment`, decoded instructions are split into operation-group node subclasses, selected hot integer/control/load/store operations have direct instruction nodes, the root frame stages all 32 integer registers in static Truffle frame slots for migrated direct and operation-group nodes with branch-free frame reads, decoded direct straight-line nodes no longer materialize sequential `pc` inside blocks, the decoded block cache no longer allocates boxed `Long` keys, the negative `BlockDispatchNode` inline-cache path has been removed, block execution no longer rechecks instruction terminators, instruction execution reuses a precomputed fall-through PC, and decoded instruction register accesses skip public index validation.
- Continue profiling phase changes after removing the hot-block promotion cache that reduced hot-loop OSR code size but regressed CoreMark throughput.
- Investigate the remaining dispatch overhead, especially primitive block-cache lookup, instruction-node body shape, and whether the `BlockNode` loop still costs enough to warrant a different compiled-block layout.
- Keep the hot-loop compilation trace task as the current performance regression probe and expand it when broader Linux workloads become stable.
- Keep CLI behavior stable while internal execution nodes evolve.
- Track release notes for supported ISA subsets, ELF limitations, syscall coverage, and known unsupported workloads.
