# Plans

## Current Status

- GraalRISCV is a user-mode RV64 emulator for 64-bit little-endian RISC-V ELF programs on a Linux-like runtime.
- The implemented profile is RVA23U64 when the configured VLEN is profile-valid; shorter vector configurations expose the RVA22U64 capability surface.
- Privileged mode, guest page tables, interrupts, devices, and Linux kernel boot are out of scope unless a later plan explicitly adds them.
- The build covers unit tests, package smoke tests, example workloads, pinned `riscv-tests`, repository-owned RVA22U64/RVA23U64 acceptance tests, and Ubuntu Base dynamic-linking smoke tests.

## Completed

- RVA22U64 and RVA23U64 user-mode profile support is implemented, centrally reported, and covered by focused tests.
- RVV 1.0, mandatory RVA23U64 vector additions, `Zkt`/`Zvkt`, and optional standard `Zvbc` are implemented; the CRC example exercises `Zvbc`.
- The Linux user-mode runtime supports the current static workload set: freestanding C, musl C, Go, SQLite, RVV examples, CoreMark, and `riscv-tests`.
- Dynamic ELF startup is implemented for guest-mounted programs through `--guest-program` and `execve`, including `PT_INTERP`, `ET_DYN` load bias, auxv metadata, `PR_GET_AUXV`, file-backed `MAP_PRIVATE`, tar symlink/hard-link lookup, virtual `/proc`, shell pipeline smoke coverage, and Ubuntu Base smoke coverage for common shell, coreutils, hashing, sorting, and findutils commands.
- The memory, `--mount` filesystem namespace, customizable `GuestFileSystem` virtual mounts, read-only tar mounts, built-in `/proc` and Linux-like `/dev` with tty, null, zero, and deterministic random devices, configurable guest user credentials, Ubuntu Base image preparation, process/thread state, process-style `clone`/`wait4`, deterministic time, and Gradle-based example/test build foundations are in place for current workloads.
- Ubuntu Base shell startup compatibility covers the currently required identity, process-group, and readiness syscalls, including `setfsuid`, `setfsgid`, `pselect6`, and `setpgid`.
- Interactive tty handling now exposes sane guest `termios` and `termios2`, keeps guest-driven `TCSETS` state stable, routes standard-input reads through the shared terminal device only when host tty control is active, and maps `TCSETS` to Windows console input mode for real host consoles.
- The CLI includes `--root` as a guest root identity shortcut for `--user root --uid 0 --gid 0 --groups 0`.

## Remaining Work

### ISA And Profile Correctness

- Keep `Rva22Profile`, `Rva23Profile`, Linux `riscv_hwprobe`, README, CI aggregation, and acceptance tests aligned whenever ISA or profile-reporting behavior changes.
- Add ISA corner-case coverage as bugs or spec ambiguities are found.
- Preserve the `Zkt`/`Zvkt` audit boundary, and keep unimplemented optional extensions unreported.

### Linux Runtime Compatibility

- Expand ELF, auxv, stack, `mmap`, syscall, dynamic-linking, and runtime behavior only when direct tests or real workloads require it.
- Continue improving signal, process, and clone semantics while keeping thread behavior deterministic.
- Keep filesystem simulation behind `GuestFileSystem`; add richer device, terminal, and process/thread-scoped filesystem state only when workloads require it.

### Memory And Mapping

- Reduce syscall-side mapping duplication by moving ELF segments, stack, `brk`, anonymous mappings, `munmap`, `mprotect`, and `madvise` behavior toward one VMA and page-table implementation.
- Preserve the current page-backing shape so native or file-mapped backings can be added later.
- Broaden paged-memory tests as mapping behavior changes.

### Build And Performance

- Keep README, CI aggregation, package smoke tests, example workloads, `riscv-tests`, and RVA22U64/RVA23U64 acceptance tests aligned with behavior changes.
- Tune the existing trace executor and micro-bytecode executor based on measurements before adding new performance subsystems.
