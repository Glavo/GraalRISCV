/*
 * Linker script for the freestanding examples. It places a minimal RV64 ELF at
 * the simulator's default high memory text address and splits executable and
 * writable data into explicit PT_LOAD segments.
 */

OUTPUT_ARCH(riscv)
ENTRY(_start)

/* Keep code and read-only data executable/readable, and mutable data writable. */
PHDRS
{
  text PT_LOAD FLAGS(5);
  data PT_LOAD FLAGS(6);
}

SECTIONS
{
  /* Match the address used by the freestanding example Gradle tasks. */
  . = 0x80000000;

  /* Code must be aligned for regular and compressed RISC-V instruction fetch. */
  .text : ALIGN(4)
  {
    *(.text.start .text.start.*)
    *(.text .text.*)
  } :text

  /* Constants share the read-only load segment with executable code. */
  .rodata : ALIGN(8)
  {
    *(.srodata .srodata.*)
    *(.rodata .rodata.*)
  } :text

  /* Writable initialized data starts a separate read/write load segment. */
  .data : ALIGN(8)
  {
    *(.sdata .sdata.*)
    *(.data .data.*)
  } :data

  /* Export BSS bounds for runtimes that choose to clear them explicitly. */
  .bss : ALIGN(8)
  {
    __bss_start = .;
    *(.sbss .sbss.*)
    *(.bss .bss.*)
    *(COMMON)
    __bss_end = .;
  } :data

  /* Remove host-toolchain metadata that is not needed by the simulator tests. */
  /DISCARD/ :
  {
    *(.comment)
    *(.note .note.*)
    *(.eh_frame .eh_frame.*)
  }
}
