# GraalRISCV

GraalRISCV is a GraalVM Truffle-based RV64IMAC ELF simulator.

## Run An ELF

Use the Gradle application task during development:

```text
./gradlew -g .gradle-user-home run --args="hello.elf"
```

Create installable launch scripts:

```text
./gradlew -g .gradle-user-home installDist
build\install\graalriscv\bin\graalriscv.bat hello.elf
```

Create and run the Shadow JAR:

```text
./gradlew -g .gradle-user-home shadowJar
java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar build/libs/GraalRISCV-1.0-SNAPSHOT-all.jar hello.elf
```

The CLI accepts:

```text
graalriscv [options] <program.elf>

Options:
  --memory-base <address>    Guest memory base address; accepts auto, decimal, or 0x-prefixed hex.
  --memory-size <bytes>      Guest memory size in bytes.
  --max-instructions <count> Maximum guest instruction count; 0 means unlimited.
  --trace                    Print guest instruction trace lines.
  -h, --help                 Print this help message.
```

The simulator currently supports the Linux RISC-V ABI calls `read`, `write`, `exit`, and `brk`.
Unsupported `ecall` failures include the syscall number, program counter, and argument registers.

## Build The C Hello World Example

Install a RISC-V bare-metal GCC toolchain that provides `riscv64-unknown-elf-gcc`, then run:

```text
./gradlew -g .gradle-user-home buildHelloWorldExample
./gradlew -g .gradle-user-home runHelloWorldExample
```

To use a non-default compiler path:

```text
./gradlew -g .gradle-user-home -PriscvGcc=C:\path\to\riscv64-unknown-elf-gcc.exe runHelloWorldExample
```

The generated ELF is written to:

```text
build/examples/hello/hello.elf
```

The example source and linker script live under `examples/hello`.

The example task uses the same freestanding build flags as the manual workflow:

```text
riscv64-unknown-elf-gcc -march=rv64imac -mabi=lp64 -mcmodel=medany -nostdlib -nostartfiles -ffreestanding -fno-builtin -fno-pic -fno-pie -fno-stack-protector -fno-asynchronous-unwind-tables -Wl,-T,examples/hello/linker.ld -Wl,--no-relax -Wl,--build-id=none -o build/examples/hello/hello.elf examples/hello/HelloWorld.c
```

The expected simulator output is:

```text
Hello World!
```

Run every available Hello World smoke check:

```text
./gradlew -g .gradle-user-home checkHelloWorldExample
```

These tasks skip the example build and run steps when `riscv64-unknown-elf-gcc` is not available. Set `-PriscvGcc=<path>` to use a compiler outside `PATH`.
