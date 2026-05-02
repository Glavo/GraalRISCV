# Plans

## Current Status

- GraalRISCV is a user-mode RV64 emulator for 64-bit little-endian RISC-V ELF programs on Linux-like and FreeBSD syscall runtimes.
- The implemented profile is RVA23U64 when the configured VLEN is profile-valid; shorter vector configurations expose the RVA22U64 capability surface.
- Privileged mode, guest page tables, interrupts, devices, and Linux kernel boot are out of scope unless a later plan explicitly adds them.
- The build covers unit tests, package smoke tests, example workloads, pinned `riscv-tests`, repository-owned RVA22U64/RVA23U64 acceptance tests, and Ubuntu Base dynamic-linking smoke tests.

## Completed

- RVA22U64 and RVA23U64 user-mode profile support is implemented, centrally reported, and covered by focused tests.
- RVV 1.0, mandatory RVA23U64 vector additions, `Zkt`/`Zvkt`, and optional standard `Zvbc` are implemented; the CRC example exercises `Zvbc`.
- RVV gather coverage includes `vrgatherei16.vv`, including the OPIVV encoding used by current Ubuntu RISC-V userland tools.
- The Linux user-mode runtime supports the current static workload set: freestanding C, musl C, Go, SQLite, RVV examples, CoreMark, and `riscv-tests`.
- FreeBSD ELF OS ABI detection selects a FreeBSD RISC-V syscall handler for static user-mode programs.
- Dynamic ELF startup is implemented for guest-mounted programs through `--guest-program` and `execve`, including `PT_INTERP`, `ET_DYN` load bias, auxv metadata, `PR_GET_AUXV`, file-backed `MAP_PRIVATE`, tar symlink/hard-link lookup, virtual `/proc`, shell pipeline smoke coverage, and Ubuntu Base smoke coverage for common shell, coreutils, hashing, sorting, and findutils commands.
- Standard descriptor duplication preserves current stdin/stdout/stderr redirects, including redirects to pipes and other standard descriptors used by child process setup.
- Decoded-block and trace caches use JVM-wide instruction-fetch generations across independent process images, preventing fork/exec children from reusing stale decoded code after interactive shell startup workloads.
- `--mount` accepts Docker-like bind/tar mount specs, rejects the removed `guest=host` form, supports read-only bind mounts, lazy non-memory tar mounts, and writable process-local memory tar mounts.
- Fastfetch Linux RISC-V release downloads are wired into Gradle, including gzip-to-tar preparation and a version smoke task.
- Virtual `/proc/cpuinfo` reports stable RISC-V CPU metadata plus sanitized `graalriscv_` Java runtime summary fields.
- The memory, `--mount` filesystem namespace, customizable `GuestFileSystem` virtual mounts, read-only tar mounts, built-in `/proc` and Linux-like `/dev` with tty, null, zero, and deterministic random devices, configurable guest user credentials, Ubuntu Base image preparation, process/thread state, process-style `clone`/`wait4`, deterministic time, and Gradle-based example/test build foundations are in place for current workloads.
- Zig and Go Gradle toolchain overrides accept either executable paths or command names resolved through `PATH`.
- Ubuntu Base shell startup compatibility covers the currently required identity, process-group, metadata, and readiness syscalls, including `setfsuid`, `setfsgid`, `fchownat`, `pselect6`, and `setpgid`.
- Linux extended-attribute query syscalls report empty guest xattr sets, allowing metadata-heavy tools such as `ls -l` to continue when no virtual xattrs are present.
- Interactive tty handling now exposes sane guest `termios` and `termios2`, keeps guest-driven `TCSETS` state stable, routes standard-input reads through the shared terminal device only when host tty control is active, maps `TCSETS` to Windows console input mode for real host consoles, provides Windows-backed guest-side input echo, VT key input/output, real Windows console window sizing, and shutdown-time console mode restoration for interactive shells.
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

### FreeBSD Runtime Compatibility

- Expand FreeBSD stack, auxv, dynamic-linking, syscall, and libc behavior only when direct tests or real workloads require it.

### Memory And Mapping

- Reduce syscall-side mapping duplication by moving ELF segments, stack, `brk`, anonymous mappings, `munmap`, `mprotect`, and `madvise` behavior toward one VMA and page-table implementation.
- Preserve the current page-backing shape so native or file-mapped backings can be added later.
- Broaden paged-memory tests as mapping behavior changes.

### Build And Performance

- Keep README, CI aggregation, package smoke tests, example workloads, `riscv-tests`, and RVA22U64/RVA23U64 acceptance tests aligned with behavior changes.
- Tune the existing trace executor and micro-bytecode executor based on measurements before adding new performance subsystems.
