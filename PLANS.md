# Plans

## Active Work

### 1. Implement user-mode RV64GC instruction support

- Keep the current RV64IMAC behavior stable while adding the missing RV64GC pieces.
- Implement the remaining spec-compatible floating-point behavior for arithmetic, fused multiply-add, sign injection, min/max, compare, classify, and integer/floating conversions.
- Add focused decoder and execution tests for the remaining F and D arithmetic, conversion, and floating-point exception behavior.

### 2. Support static Linux user-mode programs

- Keep the simulator user-mode only; do not implement privileged mode, page tables, interrupts, devices, or Linux kernel boot.
- Update the CLI shape to support `graalriscv [options] <program.elf> [program-args...]`.
- Construct a Linux-compatible initial stack with `argc`, `argv`, `envp`, `auxv`, and string storage.
- Keep the initial stack 16-byte aligned and initialize `sp` to that stack.
- Provide deterministic default environment variables such as `LANG=C`, `PATH=/usr/bin:/bin`, and `PWD=/`.
- Populate enough auxv entries for static libc startup, including program headers, page size, entry point, uid/gid values, random bytes, and platform strings.
- Accept statically linked Linux ELF executables with resolved load segments.
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

- Add a `printf("Hello World!\n")` C example compiled as a static Linux RISC-V program with the Gradle-managed Zig toolchain.
- Use Zig target `riscv64-linux-musl` for this example, without `-nostdlib`, a custom `_start`, or a freestanding linker script.
- Add `buildLinuxStaticPrintfExample` to compile the example.
- Add `testLinuxStaticPrintfExample` to assert stdout is `Hello World!\n`, stderr is empty, and the exit code is `0`.
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
