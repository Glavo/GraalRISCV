# GraalRISCV


GraalRISCV is a pure Java 64-bit RISC-V user-mode emulator built with GraalVM’s Truffle framework.
It implements the RVA23U64 user-mode profile.

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
graalriscv [options] --guest-program <path> [program-args...]

Options:
  --guest-program <path>    Load the executable from an absolute guest path resolved through --mount.
  --memory-base <address>    Guest memory base address; accepts auto, decimal, or 0x-prefixed hex.
                              Default is 0; auto infers the base from ELF load segments.
  --memory-size <bytes>      Guest virtual address window size in bytes.
  --page-size <bytes>        Guest base page size in bytes; power of two at least 4096.
  --max-committed-pages <n>  Maximum committed guest base pages; 0 means unlimited.
  --huge-page-size <bytes>   Guest HugeTLB page size in bytes.
  --huge-pages <n>           Guest HugeTLB page pool size.
  --vector-vlen <bits>       Vector register length in bits. Default is 128.
  --max-instructions <count> Maximum guest instruction count; 0 means unlimited.
  --mount <guest>=<path>     Mount a host directory or tar archive at an absolute guest path.
  --use-host-tty             Try to connect guest /dev/tty to the host controlling terminal.
  --user <name>              Guest login name. Default is user.
  --uid <id>                 Guest real, effective, and saved uid. Default is 1000.
  --gid <id>                 Guest real, effective, and saved gid. Default is 1000.
  --groups <ids|none>        Comma-separated supplementary guest gids, or none.
  --home <path>              Guest home directory used by the default environment.
  --shell <path>             Guest shell path used by the default environment.
  --debug-fixed-clock-nanos <nanos>
                              Fixed epoch nanoseconds for deterministic guest time.
  --debug-trace-compilation  Print Truffle compilation diagnostics with synchronous debug compilation.
  --trace                    Print guest instruction trace lines.
  -h, --help                 Print this help message.
```

`<program.elf>` is interpreted as a host path. Use `--guest-program` when the
executable should be loaded from the guest filesystem. `--mount` controls the
host directories and tar archives visible to guest file syscalls. For example,
`--mount /=ubuntu-base.tar --guest-program /usr/bin/true` runs a guest path from
the mounted root archive, and `--mount /data=dataset.tar` overlays `dataset.tar`
at guest `/data`. If no `/` mount is provided for a host executable, the CLI
mounts the directory containing that executable at `/`.
By default, guest `/dev/tty` is backed by the configured process streams; pass
`--use-host-tty` to try a real host controlling terminal when available.

## Supported ELF Inputs

The loader accepts ELF64 little-endian RISC-V `ET_EXEC` and `ET_DYN` inputs. It
validates segment ranges, alignment, permissions, overlap, entry-point
placement, and dynamic interpreter metadata before execution. Dynamically linked
Linux programs are supported when their interpreter and shared libraries are
available through configured guest mounts.

## Current Linux User-Mode Support

The simulator implements enough Linux RISC-V user-mode behavior to run the
bundled static musl examples, the static Go example, CoreMark, RVV examples, and
common dynamically linked Ubuntu Base shell, coreutils, and findutils commands.
File access is sandboxed under configured `--mount` entries, with built-in
`/proc` and `/dev` virtual filesystems for process metadata and basic character
devices. Guest uid, gid, supplementary groups, login name, home, and shell can be
configured with CLI options.

## Examples

Each `run...Example` task builds the required RISC-V program before executing
it.

The `decompressUbuntuBaseImage` task downloads Ubuntu Base 26.04 for RISC-V and
produces a `.tar` root filesystem under `build/downloads/ubuntu-base/26.04`
without unpacking the tar entries.

Run the Ubuntu Base dynamic-linking smoke tests:

```text
./gradlew testUbuntuBaseTrue
./gradlew testUbuntuBaseBash
./gradlew testUbuntuBaseLs
./gradlew testUbuntuBaseCat
./gradlew testUbuntuBaseFind
```

The C and CoreMark examples use Zig CC. Gradle downloads the configured Zig
release when needed. To use an existing Zig executable command or path, set one
of `ZIG_EXECUTABLE`, `zigExecutable`, or `graalriscv.zigExecutable`:

```text
ZIG_EXECUTABLE=zig ./gradlew runLinuxStaticPrintfExample
ZIG_EXECUTABLE=/path/to/zig ./gradlew runLinuxStaticPrintfExample
./gradlew "-PzigExecutable=/path/to/zig" runLinuxStaticPrintfExample
```

The Go example uses the Go toolchain. Gradle downloads the configured Go
release when needed. To use an existing Go executable command or path, set one
of `GO_EXECUTABLE`, `goExecutable`, or `graalriscv.goExecutable`:

```text
GO_EXECUTABLE=go ./gradlew runGoHelloWorldExample
GO_EXECUTABLE=/path/to/go ./gradlew runGoHelloWorldExample
./gradlew "-PgoExecutable=/path/to/go" runGoHelloWorldExample
```

| Example | Purpose | Command |
| --- | --- | --- |
| Freestanding Hello World | Run the smallest freestanding output example. | `./gradlew runHelloWorldExample` |
| Freestanding hot loop | Run a small CPU hot-loop probe. | `./gradlew runHotLoopExample` |
| Static Go hello-world | Build and run a static `linux/riscv64` Go program. | `./gradlew runGoHelloWorldExample` |
| Static Go showcase | Run a larger Go standard-library workload covering JSON, sorting, compression, hashing, and goroutines. | `./gradlew runGoShowcaseExample` |
| Static musl printf | Run a static musl `printf` hello-world program. | `./gradlew runLinuxStaticPrintfExample` |
| SQLite showcase | Download SQLite, build a static RISC-V file-database demo, and run transactions and queries. | `./gradlew runSQLiteShowcaseExample` |
| RVV vector add | Build and run the RVV vector-add example. | `./gradlew runRvvVectorAddExample` |
| RVV matrix transpose | Build and run the RVV matrix-transpose smoke example. | `./gradlew runRvvMatrixTransposeExample` |
| RVV reduction | Build and run the RVV reduction smoke example. | `./gradlew runRvvReductionExample` |
| RVV softmax | Build and run the RVV softmax example. | `./gradlew runRvvSoftmaxExample` |
| RVV polynomial basic | Build and run the RVV polynomial basic example. | `./gradlew runRvvPolynomialBasicExample` |
| RVV micro-benchmark | Build and run the RVV micro-benchmark example. | `./gradlew runRvvUbenchExample` |
| RVV CRC | Build and run the RVV CRC smoke example. | `./gradlew runRvvCrcExample` |
| CoreMark | Build and run the downloaded CoreMark benchmark. | `./gradlew runCoreMarkExample` |

Run the larger showcase workloads together:

```text
./gradlew checkShowcaseExamples
```

## RISC-V ISA Acceptance Tests

Run the RV64GC baseline `riscv-tests` ISA acceptance tests through the
GraalRISCV CLI:

```text
./gradlew buildRiscVTests
./gradlew testRiscVTests
```

Run the repository-owned RVA22U64 extension acceptance tests:

```text
./gradlew testRva22Acceptance
```

Run the repository-owned RVA23U64 extension acceptance tests:

```text
./gradlew testRva23Acceptance
```

Use `graalriscv.riscvTestsFilter` to run a subset by ELF filename regex, and
`graalriscv.riscvTestsMaxInstructions` to override the per-ELF instruction
limit:

```text
./gradlew "-Pgraalriscv.riscvTestsFilter=^rv64ui-p-.*[.]elf$" testRiscVTests
./gradlew "-Pgraalriscv.riscvTestsMaxInstructions=20000000" testRiscVTests
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
When the managed Zig archive, extracted toolchain, or manual Zig executable
configuration is already present, it also runs the static Linux C example
checks and the RVA22U64/RVA23U64 acceptance suites.

Run the Zig-backed CI example checks explicitly:

```text
./gradlew ciZigExampleCheck
```

Force `ciCheck` to download/use Zig and include those checks:

```text
./gradlew -PgraalriscvCiIncludeZigExamples=true ciCheck
```

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
