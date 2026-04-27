# GraalRISCV

GraalRISCV is a GraalVM Truffle-based RV64IMAC ELF simulator.

TODO: In development.

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
graalriscv [options] <program.elf>

Options:
  --memory-base <address>    Guest memory base address; accepts auto, decimal, or 0x-prefixed hex.
  --memory-size <bytes>      Guest memory size in bytes.
  --max-instructions <count> Maximum guest instruction count; 0 means unlimited.
  --host-root <path>         Host directory exposed to guest read-only openat calls.
  --trace                    Print guest instruction trace lines.
  -h, --help                 Print this help message.
```

The simulator currently supports the Linux RISC-V ABI calls `read`, `write`, `readv`, `writev`,
`openat`, `close`, `fstat`, `lseek`, `ioctl`, `exit`, `exit_group`, `set_tid_address`,
`set_robust_list`, `rt_sigaction`, `rt_sigprocmask`, `getpid`, `gettid`, `brk`,
`mmap`, `munmap`, and `getrandom`.
The `ioctl` support is limited to the tty queries `TCGETS` and `TIOCGWINSZ`.
The `openat` support is limited to read-only regular files under `--host-root`, which defaults to
the ELF file's parent directory.
The `mmap` support is limited to anonymous mappings inside the configured guest memory window.
The `getrandom` implementation returns deterministic pseudo-random bytes for reproducible runs.
Signal actions and masks are accepted as deterministic single-threaded stubs; host signals are not delivered to guests.
Unsupported `ecall` failures include the syscall number, program counter, and argument registers.

## Supported ELF Inputs

The loader accepts ELF64 little-endian RISC-V executable files with statically resolved `PT_LOAD`
segments. Loadable segments must have valid file ranges, power-of-two `p_align` values, ELF
address/offset alignment congruence, readable permissions, and non-overlapping guest memory ranges.
The entry point must be inside an executable `PT_LOAD` segment.

Dynamic linking and runtime relocation processing are not supported. Inputs with `PT_DYNAMIC`,
`SHT_DYNAMIC`, `SHT_REL`, or `SHT_RELA` metadata are rejected during loading.

## Build The C Hello World Example

Gradle downloads the configured Zig toolchain and uses `zig cc` to build the RISC-V ELF:

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

Run every available Hello World smoke check:

```text
./gradlew checkHelloWorldExample
```
