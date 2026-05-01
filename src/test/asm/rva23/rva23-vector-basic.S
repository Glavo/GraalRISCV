#include "riscv_test.h"
#include "test_macros.h"

.equ VTYPE_E8_M1_TA_MA, ((1 << 7) | (1 << 6) | (0 << 3) | 0)
.equ VTYPE_E32_M1_TA_MA, ((1 << 7) | (1 << 6) | (2 << 3) | 0)
.equ VSETVLI_T2_A0_E8_M1, ((VTYPE_E8_M1_TA_MA << 20) | (10 << 15) | (7 << 12) | (7 << 7) | 0x57)
.equ VSETVLI_T2_A0_E32_M1, ((VTYPE_E32_M1_TA_MA << 20) | (10 << 15) | (7 << 12) | (7 << 7) | 0x57)
.equ VLE8_V1_T0, ((1 << 25) | (0 << 12) | (5 << 15) | (1 << 7) | 0x07)
.equ VLE32_V1_T0, ((1 << 25) | (6 << 12) | (5 << 15) | (1 << 7) | 0x07)
.equ VADD_VI_V2_V1_5, ((1 << 25) | (1 << 20) | (5 << 15) | (3 << 12) | (2 << 7) | 0x57)
.equ VMSLTU_VX_V2_V1_A1, ((0x1a << 26) | (1 << 25) | (1 << 20) | (11 << 15) | (4 << 12) | (2 << 7) | 0x57)
.equ VMSGT_VI_V3_V1_0, ((0x1f << 26) | (1 << 25) | (1 << 20) | (0 << 15) | (3 << 12) | (3 << 7) | 0x57)
.equ VMAND_MM_V4_V2_V3, ((0x19 << 26) | (1 << 25) | (2 << 20) | (3 << 15) | (2 << 12) | (4 << 7) | 0x57)
.equ VSE8_V4_T1, ((1 << 25) | (0 << 12) | (6 << 15) | (4 << 7) | 0x27)
.equ VSE32_V2_T1, ((1 << 25) | (6 << 12) | (6 << 15) | (2 << 7) | 0x27)

RVTEST_RV64U
RVTEST_CODE_BEGIN

  TEST_CASE( 2, a0, 0x0000000000000004, \
    li a0, 10; \
    .word VSETVLI_T2_A0_E32_M1; \
    mv a0, t2; \
  )

  TEST_CASE( 3, a0, 0x0000000000000000, \
    la t0, vector_input_words; \
    la t1, vector_output_words; \
    li a0, 4; \
    .word VSETVLI_T2_A0_E32_M1; \
    .word VLE32_V1_T0; \
    .word VADD_VI_V2_V1_5; \
    .word VSE32_V2_T1; \
    lw t3, 0(t1); \
    li t4, 6; \
    bne t3, t4, 1f; \
    lw t3, 4(t1); \
    li t4, 7; \
    bne t3, t4, 1f; \
    lw t3, 8(t1); \
    li t4, 8; \
    bne t3, t4, 1f; \
    lw t3, 12(t1); \
    li t4, 9; \
    bne t3, t4, 1f; \
    li a0, 0; \
    j 2f; \
  1: \
    li a0, 1; \
  2: \
  )

  TEST_CASE( 4, a0, 0x0000000000000000, \
    la t0, vector_input_bytes; \
    la t1, vector_output_bytes; \
    li a0, 8; \
    li a1, 4; \
    .word VSETVLI_T2_A0_E8_M1; \
    .word VLE8_V1_T0; \
    .word VMSLTU_VX_V2_V1_A1; \
    .word VMSGT_VI_V3_V1_0; \
    .word VMAND_MM_V4_V2_V3; \
    .word VSE8_V4_T1; \
    lbu t3, 0(t1); \
    li t4, 7; \
    bne t3, t4, 1f; \
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
vector_input_words:
  .word 1, 2, 3, 4
vector_output_words:
  .word 0, 0, 0, 0
vector_input_bytes:
  .byte 1, 2, 3, 4, 0x80, 0xf0, 0x7f, 0xff
vector_output_bytes:
  .byte 0, 0, 0, 0, 0, 0, 0, 0

RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
