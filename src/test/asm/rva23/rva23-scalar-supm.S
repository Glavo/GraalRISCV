#include "riscv_test.h"
#include "test_macros.h"

.equ CZERO_EQZ_A0_A1_A2, ((0x07 << 25) | (12 << 20) | (11 << 15) | (5 << 12) | (10 << 7) | 0x33)
.equ CZERO_NEZ_A0_A1_A2, ((0x07 << 25) | (12 << 20) | (11 << 15) | (7 << 12) | (10 << 7) | 0x33)
.equ MOP_R_A0_A1, (0x81c04073 | (11 << 15) | (10 << 7))
.equ MOP_RR_A0_A1_A2, (0x82004073 | (12 << 20) | (11 << 15) | (10 << 7))
.equ WRS_NTO, 0x00d00073
.equ WRS_STO, 0x01d00073
.equ C_MOP_1, ((3 << 13) | (1 << 7) | 1)
.equ C_LBU_A0_S0_1, ((4 << 13) | (0 << 10) | (0 << 7) | (1 << 6) | (0 << 5) | (2 << 2))
.equ C_LHU_A1_S0_2, ((4 << 13) | (1 << 10) | (0 << 7) | (0 << 6) | (1 << 5) | (3 << 2))
.equ C_LH_A2_S0_2, ((4 << 13) | (1 << 10) | (0 << 7) | (1 << 6) | (1 << 5) | (4 << 2))
.equ C_SB_S1_S0_1, ((4 << 13) | (2 << 10) | (0 << 7) | (1 << 6) | (0 << 5) | (1 << 2))
.equ C_SH_S1_S0_2, ((4 << 13) | (3 << 10) | (0 << 7) | (0 << 6) | (1 << 5) | (1 << 2))
.equ C_ZEXT_B_A0, ((4 << 13) | (7 << 10) | (2 << 7) | (3 << 5) | (0 << 2) | 1)
.equ C_SEXT_B_A1, ((4 << 13) | (7 << 10) | (3 << 7) | (3 << 5) | (1 << 2) | 1)
.equ C_ZEXT_H_A2, ((4 << 13) | (7 << 10) | (4 << 7) | (3 << 5) | (2 << 2) | 1)
.equ C_SEXT_H_A3, ((4 << 13) | (7 << 10) | (5 << 7) | (3 << 5) | (3 << 2) | 1)
.equ C_ZEXT_W_A4, ((4 << 13) | (7 << 10) | (6 << 7) | (3 << 5) | (4 << 2) | 1)
.equ C_NOT_A5, ((4 << 13) | (7 << 10) | (7 << 7) | (3 << 5) | (5 << 2) | 1)
.equ C_MUL_S0_S1, ((4 << 13) | (7 << 10) | (0 << 7) | (2 << 5) | (1 << 2) | 1)

.equ SYS_PRCTL, 167
.equ PR_SET_PDEATHSIG, 1
.equ PR_GET_PDEATHSIG, 2
.equ PR_SET_TAGGED_ADDR_CTRL, 55
.equ PR_GET_TAGGED_ADDR_CTRL, 56
.equ PR_TAGGED_ADDR_ENABLE, 1
.equ PR_PMLEN7, (7 << 24)
.equ PR_TAGGED_ADDR_CTRL_PMLEN7, (PR_TAGGED_ADDR_ENABLE | PR_PMLEN7)
.equ PMLEN7_TEST_TAG, 0x0200000000000000

RVTEST_RV64U
RVTEST_CODE_BEGIN

  TEST_CASE( 2, a0, 0x0000000000000000, \
    li a1, 0x1234; \
    li a2, 0; \
    .word CZERO_EQZ_A0_A1_A2; \
    bnez a0, 1f; \
    li a2, 1; \
    .word CZERO_EQZ_A0_A1_A2; \
    li t0, 0x1234; \
    bne a0, t0, 1f; \
    li a2, 0; \
    .word CZERO_NEZ_A0_A1_A2; \
    bne a0, t0, 1f; \
    li a2, -1; \
    .word CZERO_NEZ_A0_A1_A2; \
    bnez a0, 1f; \
    li a0, 0; \
    j 2f; \
  1: \
    li a0, 1; \
  2: \
  )

  TEST_CASE( 3, a0, 0x0000000000000000, \
    li a0, 99; \
    li a1, 123; \
    .word MOP_R_A0_A1; \
    bnez a0, 1f; \
    li a0, 99; \
    li a1, 123; \
    li a2, 456; \
    .word MOP_RR_A0_A1_A2; \
    bnez a0, 1f; \
    .word WRS_NTO; \
    .word WRS_STO; \
    .hword C_MOP_1; \
    li a0, 0; \
    j 2f; \
  1: \
    li a0, 1; \
  2: \
  )

  TEST_CASE( 4, a0, 0x0000000000000000, \
    la s0, zcb_buffer; \
    li s1, 0x1122334455667788; \
    .hword C_LBU_A0_S0_1; \
    li t0, 0x81; \
    bne a0, t0, 1f; \
    .hword C_LHU_A1_S0_2; \
    li t0, 0x8001; \
    bne a1, t0, 1f; \
    .hword C_LH_A2_S0_2; \
    li t0, -32767; \
    bne a2, t0, 1f; \
    .hword C_SB_S1_S0_1; \
    lbu t0, 1(s0); \
    li t1, 0x88; \
    bne t0, t1, 1f; \
    .hword C_SH_S1_S0_2; \
    lhu t0, 2(s0); \
    li t1, 0x7788; \
    bne t0, t1, 1f; \
    li a0, 0; \
    j 2f; \
  1: \
    li a0, 1; \
  2: \
  )

  TEST_CASE( 5, a0, 0x0000000000000081, \
    li a0, 0xffffffffffff8081; \
    .hword C_ZEXT_B_A0; \
  )

  TEST_CASE( 6, a1, 0xffffffffffffff81, \
    li a1, 0x81; \
    .hword C_SEXT_B_A1; \
  )

  TEST_CASE( 7, a2, 0x0000000000008001, \
    li a2, 0xffffffffffff8001; \
    .hword C_ZEXT_H_A2; \
  )

  TEST_CASE( 8, a3, 0xffffffffffff8001, \
    li a3, 0x8001; \
    .hword C_SEXT_H_A3; \
  )

  TEST_CASE( 9, a4, 0x0000000080000001, \
    li a4, 0xffffffff80000001; \
    .hword C_ZEXT_W_A4; \
  )

  TEST_CASE( 10, a5, 0xffffffffffffffaa, \
    li a5, 0x55; \
    .hword C_NOT_A5; \
  )

  TEST_CASE( 11, s0, 0x000000000000002a, \
    li s0, 7; \
    li s1, 6; \
    .hword C_MUL_S0_S1; \
  )

  TEST_CASE( 12, a0, 0x0000000000000000, \
    la t0, supm_word; \
    sw zero, 0(t0); \
    li a0, PR_SET_TAGGED_ADDR_CTRL; \
    li a1, PR_TAGGED_ADDR_CTRL_PMLEN7; \
    li a2, 0; \
    li a3, 0; \
    li a4, 0; \
    li a7, SYS_PRCTL; \
    ecall; \
    bnez a0, 1f; \
    li a0, PR_SET_PDEATHSIG; \
    li a1, 15; \
    li a7, SYS_PRCTL; \
    ecall; \
    bnez a0, 1f; \
    li a0, PR_GET_PDEATHSIG; \
    la a1, supm_word; \
    li t0, PMLEN7_TEST_TAG; \
    or a1, a1, t0; \
    li a7, SYS_PRCTL; \
    ecall; \
    bnez a0, 1f; \
    lw t0, supm_word; \
    li t1, 15; \
    bne t0, t1, 1f; \
    li a0, PR_GET_TAGGED_ADDR_CTRL; \
    li a1, 0; \
    li a2, 0; \
    li a3, 0; \
    li a4, 0; \
    li a7, SYS_PRCTL; \
    ecall; \
    li t0, PR_TAGGED_ADDR_CTRL_PMLEN7; \
    bne a0, t0, 1f; \
    li a0, 0; \
    j 2f; \
  1: \
    li a0, 1; \
  2: \
  )

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
  .balign 8
zcb_buffer:
  .byte 0x00, 0x81
  .half 0x8001
  .fill 12, 1, 0
supm_word:
  .word 0

RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
