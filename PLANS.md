# GraalRISCV Truffle RISC-V Simulator Plan

## Summary

本项目第一阶段实现一个基于 GraalVM Truffle 的 RISC-V 模拟器 MVP。

目标范围固定为：

- 支持裸机 RISC-V ELF 程序加载与执行。
- 支持 `RV64IMAC`，即 64 位整数基础指令、乘除扩展、原子扩展和压缩指令扩展。
- 作为 Truffle language 暴露给 GraalVM Polyglot API，language id 为 `riscv`。

第一阶段明确不包含：

- Linux user-mode syscall 兼容层。
- 完整 machine emulation。
- MMU、privileged ISA、中断、设备模型、浮点扩展、向量扩展。

## Build And Dependencies

- 使用当前 Gradle Java 项目作为基础。
- Java toolchain 使用 Java 25。
- 增加 GraalVM Truffle 依赖：
  - `org.graalvm.truffle:truffle-api:25.0.2`
  - `org.graalvm.truffle:truffle-dsl-processor:25.0.2`
- 增加 Polyglot integration test 依赖：
  - `org.graalvm.polyglot:polyglot:25.0.2`
- 增加 JetBrains annotations：
  - `org.jetbrains:annotations:26.1.0`
- Gradle 命令始终使用 workspace-local Gradle user home：
  - `./gradlew -g .gradle-user-home compileJava test`

## Language Interface

- 实现 `RiscVLanguage extends TruffleLanguage<RiscVContext>`。
- 使用 `@TruffleLanguage.Registration` 注册：
  - `id = "riscv"`
  - `name = "RISC-V"`
  - `defaultMimeType = "application/x-riscv-elf"`
  - `byteMimeTypes = {"application/x-riscv-elf"}`
- Polyglot 使用方式：

```java
try (Context context = Context.newBuilder("riscv").build()) {
    Value exitCode = context.eval(
            Source.newBuilder(
                            "riscv",
                            ByteSequence.create(elfBytes),
                            "program.elf")
                    .mimeType("application/x-riscv-elf")
                    .build());
}
```

- `eval` 返回 guest program 的 `long exitCode`。
- MVP language options：
  - `riscv.memorySize`：默认 `134217728`，即 `128 MiB`。
  - `riscv.maxInstructions`：默认 `0`，表示不限指令数。
  - `riscv.trace`：默认 `false`。

## ELF Loading

- 仅支持 ELF64 little-endian。
- `e_machine` 必须为 `EM_RISCV`。
- 第一阶段只接受 `ET_EXEC`。
- 加载所有 `PT_LOAD` segment。
- `p_filesz` 范围写入内存，`p_memsz - p_filesz` 范围清零。
- `pc` 初始化为 `e_entry`。
- 若存在 symbol table，解析 `tohost` 和 `fromhost`。
- 拒绝以下输入：
  - 非 ELF。
  - 非 ELF64。
  - 非 little-endian。
  - 非 RISC-V machine type。
  - 动态链接 ELF。
  - 需要 relocation 的输入。
  - segment 超出 guest memory 范围。

## Machine Model

- 默认 guest memory base 为 `0x80000000`。
- 默认 guest memory size 为 `128 MiB`。
- `x0` 恒为 `0`。
- `x1` 到 `x31` 为 64 位 integer registers。
- `x2` 初始化为 16-byte aligned stack top。
- `pc` 为 64 位 guest address。
- 单线程执行。
- `fence` 和 `fence.i` 在 MVP 中作为 no-op。
- LR/SC reservation 状态保存在 `MachineState`。
- 未对齐访问在 MVP 中抛出 guest exception，不做硬件兼容模拟。

## Execution Architecture

- 使用 lazy basic-block translation。
- 第一次到达某个 `pc` 时解码一个 basic block 并缓存。
- `RiscVRootNode` 持有主执行循环。
- `BlockNode` 执行一段顺序指令，直到分支、跳转、trap、退出或 instruction budget 用尽。
- `InstructionNode` 表示单条指令语义。
- 建议核心类型：
  - `RiscVLanguage`
  - `RiscVContext`
  - `RiscVRootNode`
  - `MachineState`
  - `Memory`
  - `ElfImage`
  - `BlockNode`
  - `InstructionNode`

## ISA Implementation Order

1. RV64I base integer instructions.
2. M extension integer multiplication and division instructions.
3. C extension compressed instruction decoding and execution.
4. A extension atomic memory instructions.

RV64I 阶段优先实现：

- Integer register-immediate instructions.
- Integer register-register instructions.
- Load and store instructions.
- Branch instructions.
- `jal` and `jalr`.
- `lui` and `auipc`.
- `ecall`, `ebreak`, `fence`, `fence.i`.

## Program Exit Rules

- 若 ELF 包含 `tohost` symbol：
  - guest 写入 `tohost == 1` 表示 exit code `0`。
  - guest 写入其他非零值表示失败，exit code 为 `value >>> 1`。
- 若 ELF 不包含 `tohost` symbol：
  - `ecall` 且 `a7 == 93` 时使用 `a0` 作为 exit code。
  - `ebreak` 作为 exit code `0`。
- 非法指令、越界内存访问、未对齐访问、无效跳转目标通过 guest exception 报错。

## Tests

- ELF loader tests：
  - legal minimal ELF。
  - invalid magic。
  - unsupported class or data encoding。
  - wrong machine type。
  - segment out of guest memory range。
  - BSS zero fill。
  - `tohost` and `fromhost` symbol resolution。
- Decoder tests：
  - immediate extraction。
  - sign extension。
  - compressed instruction expansion。
  - illegal encodings。
  - `x0` write suppression。
- Execution tests：
  - arithmetic instructions。
  - branch instructions。
  - load and store instructions。
  - `jal` and `jalr`。
  - M extension instructions。
  - C extension instructions。
  - A extension instructions。
  - `ecall` exit。
  - `tohost` exit。
- Polyglot integration tests：
  - build an in-memory ELF byte array。
  - run it through `Context.eval`。
  - assert returned exit code。

## Acceptance Criteria

- `./gradlew -g .gradle-user-home compileJava test` passes。
- A minimal RV64I ELF can be loaded and exited through `ecall`。
- A minimal RV64IMAC ELF can be loaded and exited through `tohost`。
- Unsupported ELF and unsupported instructions fail with clear exceptions。
- All Java code follows repository rules:
  - Every class is annotated with `@NotNullByDefault`。
  - Nullable values are explicitly marked with `@Nullable`。
  - Java `Optional` is not used。
  - Public classes, fields, and methods use `///` Markdown-style Javadoc。

## Reference Material

- GraalVM Truffle Language Implementation Framework: <https://www.graalvm.org/jdk25/graalvm-as-a-platform/language-implementation-framework/>
- `TruffleLanguage.Registration` API: <https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration.html>
- RISC-V Unprivileged ISA: <https://docs.riscv.org/reference/isa/unpriv/unpriv-index.html>
- RISC-V ELF psABI: <https://riscv-non-isa.github.io/riscv-elf-psabi-doc/>
- RISC-V Architectural Tests: <https://github.com/riscv/riscv-arch-test>
