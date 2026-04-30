#include "riscv_test.h"
#include "test_macros.h"

.option arch, +zicbom, +zicboz, +zicbop, +zihintntl, +zihintpause

RVTEST_RV64U
RVTEST_CODE_BEGIN

  TEST_CASE( 2, a0, 0x0000000000000000, \
    la a1, cbo_buffer; \
    li a2, 0x55; \
    li a3, 128; \
  1: \
    sb a2, 0(a1); \
    addi a1, a1, 1; \
    addi a3, a3, -1; \
    bnez a3, 1b; \
    la a1, cbo_buffer; \
    addi a1, a1, 72; \
    cbo.inval 0(a1); \
    cbo.clean 0(a1); \
    cbo.flush 0(a1); \
    cbo.zero 0(a1); \
    la a1, cbo_buffer; \
    lbu a2, 63(a1); \
    li a0, 1; \
    li a3, 0x55; \
    bne a2, a3, 2f; \
    lbu a2, 64(a1); \
    bnez a2, 2f; \
    lbu a2, 127(a1); \
    bnez a2, 2f; \
    li a0, 0; \
  2: \
  )

  TEST_CASE( 3, a0, 0x0000000000000007, \
    la a1, cbo_buffer; \
    prefetch.i 0(a1); \
    prefetch.r 32(a1); \
    prefetch.w 64(a1); \
    ntl.p1; \
    ntl.pall; \
    ntl.s1; \
    ntl.all; \
    pause; \
    li a0, 7; \
  )

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
  .balign 64
cbo_buffer:
  .fill 128, 1, 0

RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
