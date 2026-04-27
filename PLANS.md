# Plans

## Active Work

### 1. Implement user-mode RV64GC instruction support

- Keep the current RV64IMAC behavior stable while adding the missing RV64GC pieces.
- Finish double-precision directed rounding beyond the default Java IEEE operations and audit remaining RMM tie cases.
- Complete the remaining floating-point exception-flag edge cases and settle the NaN payload preservation policy.
- Add focused decoder and execution tests for remaining double-precision rounding, exact exception-flag edge cases, and NaN payload behavior.

### 2. Support static Linux user-mode programs

- Keep the simulator user-mode only; do not implement privileged mode, page tables, interrupts, devices, or Linux kernel boot.
- Extend auxv if broader static libc programs require entries beyond the current musl `printf` smoke path.
- Broaden statically linked Linux ELF coverage beyond the current resolved-segment musl `printf` baseline.
- Reject dynamic linking in this phase with clear diagnostics when inputs contain `PT_INTERP`, `PT_DYNAMIC`, or unresolved relocation metadata.
- Preserve existing freestanding ELF behavior and tests while adding Linux static program behavior.

### 3. Expand Linux syscall coverage for static libc

- Keep syscall handling deterministic and single-process unless a later plan explicitly expands that boundary.
- Preserve `--host-root` as the filesystem sandbox root and reject path escapes.
- Extend `openat` and file descriptor handling to support sandboxed writes for regular files, including `O_WRONLY`, `O_RDWR`, `O_CREAT`, `O_TRUNC`, and `O_APPEND`.
- Add syscall implementations commonly needed by static musl programs: `mprotect`, `madvise`, `prlimit64`, `uname`, `clock_gettime`, `gettimeofday`, `newfstatat`, `readlinkat`, `fcntl`, `getuid`, `geteuid`, `getgid`, `getegid`, and single-threaded `futex` behavior.
- Keep unsupported syscall diagnostics actionable by including the syscall number, guest PC, and argument registers.
- Add direct syscall tests for success paths, Linux-compatible error returns, filesystem sandboxing, and deterministic time/random behavior.

### 4. Add static Linux C acceptance examples

- Add an argument-passing example that verifies `argc` and `argv` from the generated Linux initial stack.
- Add a sandboxed file I/O example that verifies regular file reads and writes under `--host-root`.
- Keep no-toolchain package smoke tests available so basic CI remains possible without Zig.

### 5. Keep build, CI, and documentation current

- Run `./gradlew -g .gradle-user-home checkHelloWorldExample` on Linux and macOS after Zig download and extraction are available.
- Record any non-Windows Zig download, extraction, or linker troubleshooting that is needed after real-toolchain verification.
- Keep `ciCheck` covering compile, tests, package artifacts, no-toolchain smoke checks, and the new static Linux C acceptance checks when Zig is available.
- Check that interpreter state, memory access, block caching, and instruction dispatch remain compatible with Truffle partial evaluation expectations as execution state grows.
- Keep CLI behavior stable while internal execution nodes evolve.
- Track release notes for supported ISA subsets, ELF limitations, syscall coverage, and known unsupported workloads.
