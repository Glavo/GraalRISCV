# GraalRISCV


GraalRISCV is a pure Java 64-bit RISC-V user-mode emulator built with GraalVM’s Truffle framework.
It can be used to run 64-bit RISC-V ELF executables on the JVM.

The project is currently under development.

We have implemented the complete RVA20U64 Profile (RV64GC) and are gradually adding support for more system calls.

It can already run some programs statically linked against musl libc.

For example, it can already run CoreMark:

```
> arch
x86_64
> file coremark.riscv64-musl
coremark.riscv64-musl: ELF 64-bit LSB executable, UCB RISC-V, RVC, double-float ABI, version 1 (SYSV), statically linked, with debug_info, not stripped
> java -jar GraalRISCV-1.0-SNAPSHOT-all.jar coremark.riscv64-musl
2K performance run parameters for coremark.
CoreMark Size    : 666
Total ticks      : 12492
Total time (secs): 12.492000
Iterations/Sec   : 880.563561
Iterations       : 11000
Compiler version : Clang 21.1.0
Compiler flags   : -O2   -lrt
Memory location  : Please put data memory location here
                        (e.g. code in flash, data on heap etc)
seedcrc          : 0xe9f5
[0]crclist       : 0xe714
[0]crcmatrix     : 0x1fd7
[0]crcstate      : 0x8e3a
[0]crcfinal      : 0x33ff
Correct operation validated. See README.md for run and reporting rules.
CoreMark 1.0 : 880.563561 / Clang 21.1.0 -O2   -lrt / Heap
```

We are working to support more programs.

## Requirements

- JDK 25

## Run A RISC-V Program

During development, run an ELF directly through Gradle:

```text
./gradlew run --args="path/to/program.riscv64-musl"
```

Pass guest arguments after the ELF path:

```text
./gradlew run --args="path/to/program.riscv64-musl alpha --beta"
```

You can also package it as a Shadow JAR:

```text
./gradlew shadowJar
java --sun-misc-unsafe-memory-access=allow -jar build/libs/GraalRISCV-1.0-SNAPSHOT-all.jar path/to/program.riscv64-musl
```

## CLI Options

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

`--host-root` controls the host directory visible to guest file syscalls. If it
is omitted, the CLI uses the directory containing the guest program.

## Supported ELF Inputs

The loader accepts ELF64 little-endian RISC-V executable files with statically
resolved `PT_LOAD` segments. It validates segment ranges, alignment,
permissions, overlap, and entry-point placement before execution.

Static Linux and freestanding executables are the intended input shape. Dynamic
ELF metadata such as `PT_INTERP`, `PT_DYNAMIC`, `SHT_DYNAMIC`, `SHT_REL`, and
`SHT_RELA` is rejected during loading.

## Current Linux User-Mode Support

The simulator implements a deterministic single-process subset of the Linux
RISC-V syscall ABI for static libc programs. File syscalls are implemented
through Truffle file APIs and sandboxed under `--host-root`.

Supported syscall families currently include:

- process exit and identity queries
- `read`, `write`, `readv`, `writev`
- `pread64`, `pwrite64`
- `openat`, `close`, `fstat`, `newfstatat`, `readlinkat`, `getdents64`,
  `mkdirat`, `unlinkat`, `renameat`, `renameat2`, `truncate`, `ftruncate`,
  `statfs`, `fstatfs`, `statx`, `sync`, `fsync`, `fdatasync`, `syncfs`,
  `lseek`, `ioctl`, `fcntl`, `dup`, `dup3`, and `pipe2`
- deterministic single-process `eventfd2`, `epoll_create1`, `epoll_ctl`, and
  zero-timeout `epoll_pwait`
- `getcwd`, `chdir`, `fchdir`, `faccessat`, and `faccessat2`
- `brk`, `mmap`, `munmap`, `mprotect`, `madvise`, and `riscv_hwprobe`
- `clock_gettime`, `gettimeofday`, `times`, `nanosleep`, `getrusage`, and
  `prlimit64`
- deterministic compatibility for `set_tid_address`, `set_robust_list`,
  signal-mask setup, signal-action setup, `sigaltstack`,
  `sched_getaffinity`, `sched_yield`, and process-group probes
- Linux thread-style `clone` and futex paths used by simple static musl pthread
  joins
- `getrandom`, which returns deterministic pseudo-random bytes for reproducible
  runs

## Build The Freestanding C Examples

Gradle uses the managed Zig toolchain to build the freestanding RISC-V examples:

```text
./gradlew buildHelloWorldExample
./gradlew runHelloWorldExample
./gradlew testHotLoopExample
```

Generated ELFs:

```text
build/examples/hello/hello.elf
build/examples/hello/hot-loop.elf
```

The source files and linker script are under `examples/hello`.

Expected `HelloWorld.c` simulator output:

```text
Hello World!
```

## Build Static Linux C Examples

The `examples/linux-static` directory contains static `riscv64-linux-musl`
acceptance programs. They are built with Gradle-managed Zig and executed through
the GraalRISCV CLI.

Run the static musl `printf` example:

```text
./gradlew runLinuxStaticPrintfExample
```

Run the static Linux smoke checks:

```text
./gradlew testLinuxStaticPrintfExample
./gradlew testLinuxStaticArgvExample
./gradlew testLinuxStaticFileIoExample
./gradlew testLinuxStaticDirectoryListExample
./gradlew testLinuxStaticFileMutationExample
./gradlew testLinuxStaticWorkingDirectoryExample
./gradlew testLinuxStaticFilesystemStatusExample
./gradlew testLinuxStaticStatxMetadataExample
./gradlew testLinuxStaticPositionedIoExample
./gradlew testLinuxStaticEventPollingExample
./gradlew testLinuxStaticThreadJoinExample
./gradlew testLinuxStaticRuntimeServicesExample
```

Run every built-in C example check:

```text
./gradlew checkHelloWorldExample
```

Generated static Linux ELFs are written under:

```text
build/examples/linux-static
```

## Package And CI Smoke Checks

Run package smoke tests that do not require Zig:

```text
./gradlew packageSmokeTest
```

This generates a tiny RISC-V ELF fixture under `build/fixtures/smoke`, then runs
it through both the `installDist` launcher and the packaged Shadow JAR.

Run the local CI verification task:

```text
./gradlew ciCheck
```

This compiles main and test sources, runs the unit tests, builds distribution
artifacts, builds the Shadow JAR, and runs no-toolchain package smoke checks.

## Native Image Packaging

The build includes opt-in native-image packaging tasks for environments with a
GraalVM Native Image toolchain:

```text
./gradlew nativeCompile
./gradlew nativeImageSmokeTest
```

JVM mode is the primary path for current performance work.

## Performance Work

CoreMark and the freestanding hot-loop example are useful probes for dispatch
and block-cache performance. The CLI includes `--debug-trace-compilation` for
Truffle compilation diagnostics, and the Gradle task below runs the hot-loop
example with those diagnostics enabled:

```text
./gradlew runHotLoopCompilationTrace
```
