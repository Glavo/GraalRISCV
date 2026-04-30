#include "riscv_test.h"
#include "test_macros.h"

.option arch, +a

RVTEST_RV64U
RVTEST_CODE_BEGIN

  TEST_CASE( 2, a0, 0x0000000000000000, \
    la a1, lrsc_word; \
    li a2, 41; \
    sw a2, 0(a1); \
    lr.w a3, (a1); \
    li a4, 42; \
    sc.w a5, a4, (a1); \
    lw a6, 0(a1); \
    li a0, 1; \
    li a7, 41; \
    bne a3, a7, 1f; \
    bnez a5, 1f; \
    li a7, 42; \
    bne a6, a7, 1f; \
    li a0, 0; \
  1: \
  )

  TEST_CASE( 3, a0, 0x0000000000000000, \
    la a1, lrsc_word; \
    li a4, 43; \
    sc.w a5, a4, (a1); \
    lw a6, 0(a1); \
    li a0, 1; \
    beqz a5, 1f; \
    li a7, 42; \
    bne a6, a7, 1f; \
    li a0, 0; \
  1: \
  )

  TEST_CASE( 4, a0, 0x0000000000000000, \
    la a1, lrsc_double; \
    li a2, 0x1122334455667788; \
    sd a2, 0(a1); \
    lr.w a3, (a1); \
    li a4, 0x0102030405060708; \
    sc.d a5, a4, (a1); \
    ld a6, 0(a1); \
    li a0, 1; \
    beqz a5, 1f; \
    bne a6, a2, 1f; \
    li a0, 0; \
  1: \
  )

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
  .balign 8
lrsc_word:
  .word 0
  .word 0
lrsc_double:
  .dword 0

RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
