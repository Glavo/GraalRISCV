# Plans

## Current Status

- GraalRISCV is a user-mode RV64 emulator for statically linked 64-bit little-endian RISC-V ELF programs on a Linux-like runtime.
- The implemented profile is RVA23U64 when the configured VLEN is profile-valid; shorter vector configurations expose the RVA22U64 capability surface.
- Privileged mode, guest page tables, interrupts, devices, and Linux kernel boot are out of scope unless a later plan explicitly adds them.
- The build covers unit tests, package smoke tests, example workloads, pinned `riscv-tests`, and repository-owned RVA22U64/RVA23U64 acceptance tests.

## Completed

- RVA22U64 and RVA23U64 user-mode profile support is implemented, centrally reported, and covered by focused tests.
- RVV 1.0, mandatory RVA23U64 vector additions, `Zkt`/`Zvkt`, and optional standard `Zvbc` are implemented; the CRC example exercises `Zvbc`.
- The Linux user-mode runtime supports the current static workload set: freestanding C, musl C, Go, SQLite, RVV examples, CoreMark, and `riscv-tests`.
- The memory, `--mount` filesystem sandboxing, read-only tar mounts, Ubuntu Base image preparation, process/thread state, deterministic time, and Gradle-based example/test build foundations are in place for current workloads.

## Remaining Work

### Dynamic Linking And Ubuntu Root Image

- Support dynamically linked Linux ELF programs while preserving the existing static ELF path.
- Let the CLI load a guest executable from configured mounts when the program argument is an absolute guest path such as `/usr/bin/true`; keep host-file loading when the argument names an existing host file.
- Accept dynamic ELF metadata in the loader, record interpreter and dynamic-program metadata, map `ET_EXEC` and `ET_DYN` images with correct load bias handling, and start `PT_INTERP` programs through the guest dynamic linker.
- Build the initial Linux stack for dynamic programs with main-program auxv values, including `AT_BASE` for the interpreter and `AT_EXECFN` for the guest executable path.
- Add file-backed `MAP_PRIVATE` support for regular files so the dynamic linker can map shared libraries from mounted directories or read-only tar archives.
- Make tar mounts resolve symbolic links for normal path lookup, including Ubuntu Base paths such as `/bin`, `/lib`, interpreter symlinks, and relative links, while preserving `readlinkat` and no-follow behavior.
- Add an Ubuntu Base acceptance task that runs at least `/usr/bin/true` from the downloaded root tar via `--mount /=build/downloads/ubuntu-base/26.04/ubuntu-base-26.04-base-riscv64.tar`.

### ISA And Profile Correctness

- Keep `Rva22Profile`, `Rva23Profile`, Linux `riscv_hwprobe`, README, CI aggregation, and acceptance tests aligned whenever ISA or profile-reporting behavior changes.
- Add ISA corner-case coverage as bugs or spec ambiguities are found.
- Preserve the `Zkt`/`Zvkt` audit boundary, and keep unimplemented optional extensions unreported.

### Linux Runtime Compatibility

- Expand ELF, auxv, stack, `mmap`, syscall, and runtime behavior only when direct tests or real workloads require it.
- Continue improving signal and clone semantics while keeping thread behavior deterministic.

### Memory And Mapping

- Reduce syscall-side mapping duplication by moving ELF segments, stack, `brk`, anonymous mappings, `munmap`, `mprotect`, and `madvise` behavior toward one VMA and page-table implementation.
- Preserve the current page-backing shape so native or file-mapped backings can be added later.
- Broaden paged-memory tests as mapping behavior changes.

### Build And Performance

- Keep README, CI aggregation, package smoke tests, example workloads, `riscv-tests`, and RVA22U64/RVA23U64 acceptance tests aligned with behavior changes.
- Tune the existing trace executor and micro-bytecode executor based on measurements before adding new performance subsystems.
