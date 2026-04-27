# GraalRISCV

GraalRISCV is a GraalVM Truffle-based RV64IMAC ELF simulator.

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

## Build The C Hello World Example

Install a RISC-V bare-metal GCC toolchain that provides `riscv64-unknown-elf-gcc`, then run:

```text
./gradlew buildHelloWorldExample
./gradlew runHelloWorldExample
```

To use a non-default compiler path:

```text
./gradlew -PriscvGcc=C:\path\to\riscv64-unknown-elf-gcc.exe runHelloWorldExample
```

The generated ELF is written to:

```text
build/examples/hello/hello.elf
```

The example source and linker script live under `examples/hello`.
