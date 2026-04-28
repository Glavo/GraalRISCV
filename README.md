# GraalRISCV

GraalRISCV is an experimental GraalVM Truffle-based RISC-V ELF simulator. It
targets RV64GC user-mode execution and currently focuses on freestanding ELF
programs and statically linked Linux RISC-V programs.

The project is still in active development. It is not a full system emulator:
it does not boot Linux, implement privileged mode, emulate devices, or provide
page tables and interrupts.

## Run An ELF

Use the Gradle application task during development:

```text
./gradlew run --args="hello.elf"
```

Create installable launch scripts:

```text
./gradlew installDist
build\install\graalriscv\bin\graalriscv.bat hello.elf
```

Create and run the Shadow JAR:

```text
./gradlew shadowJar
java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar build/libs/GraalRISCV-1.0-SNAPSHOT-all.jar hello.elf
```

The CLI accepts:

```text
graalriscv [options] <program.elf> [program-args...]

Options:
  --memory-base <address>    Guest memory base address; accepts auto, decimal, or 0x-prefixed hex.
  --memory-size <bytes>      Guest memory size in bytes.
  --max-instructions <count> Maximum guest instruction count; 0 means unlimited.
  --host-root <path>         Host directory exposed to sandboxed guest file syscalls.
  --debug-fixed-clock-nanos <nanos>
                              Fixed epoch nanoseconds for deterministic guest time.
  --debug-trace-compilation  Print Truffle compilation diagnostics with synchronous debug compilation.
  --trace                    Print guest instruction trace lines.
  -h, --help                 Print this help message.
```

## Supported ELF Inputs

The loader accepts ELF64 little-endian RISC-V executable files with statically
resolved `PT_LOAD` segments. Loadable segments must have valid file ranges,
power-of-two `p_align` values, ELF address/offset alignment congruence, readable
permissions, and non-overlapping guest memory ranges. The entry point must be
inside an executable `PT_LOAD` segment.

Dynamic linking and runtime relocation processing are not supported. Inputs with
`PT_INTERP`, `PT_DYNAMIC`, `SHT_DYNAMIC`, `SHT_REL`, or `SHT_RELA` metadata are
rejected during loading.

## Current Linux User-Mode Support

The simulator implements a deterministic single-process subset of the Linux
RISC-V syscall ABI. The current subset is sufficient for small statically linked
musl programs, including `printf`, argument passing, basic file I/O, directory
listing, cwd-aware path resolution, file mutation through a host-root sandbox,
filesystem status queries, time queries, anonymous memory mappings, and common
libc process setup probes.

Supported syscall families currently include:

- process exit and identity queries
- `read`, `write`, `readv`, `writev`
- `openat`, `close`, `fstat`, `newfstatat`, `readlinkat`, `getdents64`,
  `mkdirat`, `unlinkat`, `renameat`, `renameat2`, `truncate`, `ftruncate`,
  `statfs`, `fstatfs`, `lseek`, `ioctl`, `fcntl`, `dup`, `dup3`, and `pipe2`
- `getcwd`, `chdir`, `fchdir`, `faccessat`, and `faccessat2`
- `brk`, `mmap`, `munmap`, `mprotect`, `madvise`, and `riscv_hwprobe`
- `clock_gettime`, `gettimeofday`, `times`, `nanosleep`, `getrusage`, and
  `prlimit64`
- deterministic single-thread compatibility for `clone`, `futex`,
  `set_tid_address`, `set_robust_list`, signal-mask setup, signal-action setup,
  `sigaltstack`, `sched_getaffinity`, `sched_yield`, and process-group probes
- `getrandom`, which returns deterministic pseudo-random bytes for reproducible
  runs

File syscalls are sandboxed under `--host-root`, which defaults to the current
working directory. Host file access, directory descriptors, and directory
listing, working-directory changes, and mutation are implemented through
Truffle file APIs.
Unsupported `ecall` failures include the syscall number, guest PC, and argument
registers.

## Build The Freestanding C Example

Gradle downloads the configured Zig toolchain and uses `zig cc` to build the
RISC-V ELF:

```text
./gradlew buildHelloWorldExample
./gradlew runHelloWorldExample
```

The generated ELF is written to:

```text
build/examples/hello/hello.elf
```

The example source and linker script live under `examples/hello`.

The example task uses the same freestanding build flags as the manual workflow:

```text
zig cc --target=riscv64-freestanding -Xclang -target-feature -Xclang +m -Xclang -target-feature -Xclang +a -Xclang -target-feature -Xclang +c -mabi=lp64 -mcmodel=medany -nostdlib -ffreestanding -fno-sanitize=undefined -fno-builtin -fno-pic -fno-pie -fno-stack-protector -fno-asynchronous-unwind-tables -Wl,-T,examples/hello/linker.ld -Wl,--build-id=none -o build/examples/hello/hello.elf examples/hello/HelloWorld.c
```

The expected simulator output is:

```text
Hello World!
```

## Build Static Linux C Examples

The `examples/linux-static` directory contains statically linked Linux musl
smoke programs. They are built with the Gradle-managed Zig toolchain.

```text
./gradlew buildLinuxStaticPrintfExample
./gradlew testLinuxStaticPrintfExample
./gradlew testLinuxStaticArgvExample
./gradlew testLinuxStaticFileIoExample
./gradlew testLinuxStaticDirectoryListExample
./gradlew testLinuxStaticFileMutationExample
./gradlew testLinuxStaticWorkingDirectoryExample
./gradlew testLinuxStaticFilesystemStatusExample
```

Run every available C smoke check:

```text
./gradlew checkHelloWorldExample
```

## CI And Package Smoke Checks

Run the no-toolchain package smoke tests:

```text
./gradlew packageSmokeTest
```

This generates a tiny RISC-V ELF fixture under `build/fixtures/smoke`, then runs
it through both the `installDist` launch script and the packaged Shadow JAR. It
does not require Zig or any host RISC-V compiler.

Run the local CI verification task:

```text
./gradlew ciCheck
```

This compiles main and test sources, runs the unit tests, builds distribution
artifacts, builds the Shadow JAR, and runs the no-toolchain package smoke checks.

## Native Image

The build includes opt-in native-image packaging tasks:

```text
./gradlew nativeCompile
./gradlew nativeImageSmokeTest
```

These tasks require a GraalVM Native Image toolchain.

## Performance Status

Performance work is ongoing. Recent profiling removed a large boxed-PC
allocation hotspot in the decoded block cache and removed a regressive block
inline-cache dispatch path. The remaining bottlenecks are still in the guest
instruction dispatch path, especially `InstructionNode.execute`,
`BlockNode.execute`, and block-cache lookup.
