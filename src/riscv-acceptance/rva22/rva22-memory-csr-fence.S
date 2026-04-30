#include "riscv_test.h"
#include "test_macros.h"

.option arch, +zicntr, +zifencei

RVTEST_RV64U
RVTEST_CODE_BEGIN

  TEST_CASE( 2, a0, 0x1122334455667788, \
    la a1, misaligned_buffer; \
    addi a1, a1, 1; \
    li a2, 0x1122334455667788; \
    sd a2, 0(a1); \
    ld a0, 0(a1); \
  )

  TEST_CASE( 3, a0, 0x0000000000000001, \
    rdcycle a1; \
    rdtime a2; \
    rdinstret a3; \
    sltu a4, a1, a2; \
    sltu a5, a2, a3; \
    and a0, a4, a5; \
  )

  TEST_CASE( 4, a0, 0x0000000000000002, \
    call patchable_target; \
    li a1, 1; \
    bne a0, a1, fail; \
    la a1, patchable_target; \
    li a2, 0x00200513; \
    sw a2, 0(a1); \
    fence.i; \
    call patchable_target; \
  )

  TEST_PASSFAIL

RVTEST_CODE_END

  .align 2
patchable_target:
  .word 0x00100513
  .word 0x00008067

  .data
  .balign 8
misaligned_buffer:
  .fill 24, 1, 0

RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
