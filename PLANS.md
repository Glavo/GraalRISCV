# Plans

## Scope

- GraalRISCV is a user-mode RV64 emulator for 64-bit little-endian RISC-V ELF programs.
- Linux-like user-mode execution is the primary compatibility target; FreeBSD user-mode support is incremental.
- Privileged mode, guest page tables, interrupts, devices, and Linux kernel boot are out of scope unless a later plan explicitly adds them.

## Current Baseline

- ISA support targets RVA23U64 when the configured VLEN is profile-valid; shorter vector configurations expose the RVA22U64 capability surface.
- Linux workloads currently cover static C/musl/Go programs, SQLite, CoreMark, RVV examples, `riscv-tests`, Ubuntu Base dynamic-linking smoke tests, interactive shell use, fastfetch, and BellSoft JDK 25 interpreted `java -version`.
- Gradle-managed external downloads are cached under project-root `downloads/` so `clean` preserves them.
- FreeBSD support currently covers static user-mode programs needed by the FreeBSD Go hello-world smoke workload.
- The runtime includes Docker-like bind/tar mounts, virtual `/proc`, minimal virtual `/sys` DMI, PCI, and class stubs, Linux-like `/dev`, deterministic `NETLINK_ROUTE` interface and default-route metadata, guest credentials, deterministic time, process/thread state, `clone`/`wait4`, terminal handling, and Gradle-managed example/test tasks.
- Syscall handling is split by guest ABI: `GuestSyscalls` owns shared runtime state and helpers, while `LinuxGuestSyscalls` and `FreeBsdGuestSyscalls` own ABI-specific dispatch and compatibility behavior.
- LTP syscall work currently includes a coverage report and an ABI-table driven static smoke ELF for core identity, process/resource, time, random, and file-system behavior.
- CLI execution runs through the plain Java `RiscVEngine` and `RiscVInterpreter` loop. Decoded blocks and traces use `ExecutableBlock` and `ExecutableTrace`, with `TraceCompiler` reserved as the future bytecode trace compiler boundary.
- The Java interpreter includes a PC-indexed `DecodedCodeSegment` fast path for batched integer blocks, backed by a local segment cache, segment-local operand rewrites, packed register operands, segment-internal fast-slice dispatch, batched retire accounting, lazy memory-fault PC materialization, and aligned scalar memory helpers. `MemoryAccess` owns a non-null software TLB directly, while direct `Memory` API paths create short-lived non-null caches without `ThreadLocal` state. Unsupported, checked, traced, syscall, CSR, floating-point, vector, atomic, and active LR/SC reservation paths fall back to the existing block interpreter.

## Maintenance Principles

- Expand syscall, ELF, filesystem, terminal, and runtime behavior only when a direct test or real workload requires it.
- Keep profile reporting, Linux `riscv_hwprobe`, README claims, and acceptance tests aligned whenever ISA behavior changes.
- Keep filesystem simulation behind `GuestFileSystem`; avoid coupling workload-specific behavior directly to mount implementations.
- Preserve deterministic behavior for tests, especially around time, process/thread state, random data, terminal state, and child process cleanup.
- Prefer focused smoke workloads over broad host-dependent assumptions.

## Remaining Work

### ISA And Profile Correctness

- Add ISA corner-case coverage as bugs or spec ambiguities are found.
- Preserve the `Zkt`/`Zvkt` audit boundary and keep unimplemented optional extensions unreported.

### Linux Runtime Compatibility

- Continue improving signal, process, clone, exec, and dynamic-linking semantics as workloads require them.
- Add richer `/proc`, `/sys`, device, terminal, and process/thread-scoped filesystem state only when concrete programs need it.

### FreeBSD Runtime Compatibility

- Grow FreeBSD stack, auxv, dynamic-linking, syscall, threading, and libc behavior from direct smoke workloads.

### Memory And Mapping

- Reduce syscall-side mapping duplication by moving ELF segments, stack, `brk`, anonymous mappings, `munmap`, `mprotect`, and `madvise` behavior toward one VMA and page-table implementation.
- Preserve the current page-backing shape so native or file-mapped backings can be added later.
- Broaden paged-memory tests as mapping behavior changes.

### Build And Performance

- Keep README, CI aggregation, package smoke tests, example workloads, `riscv-tests`, and RVA22U64/RVA23U64 acceptance tests aligned with behavior changes.
- Continue investigating HotSpot JIT/RVC code generation beyond the interpreted JDK 25 smoke path.
- Continue tuning the decoded segment fast path, trace executor, and micro-bytecode executor from CoreMark/HotLoop measurements before adding a JVM bytecode trace compiler.
