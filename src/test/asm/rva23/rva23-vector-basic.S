#include "riscv_test.h"
#include "test_macros.h"

.equ VTYPE_E8_M1_TA_MA, ((1 << 7) | (1 << 6) | (0 << 3) | 0)
.equ VTYPE_E32_M1_TA_MA, ((1 << 7) | (1 << 6) | (2 << 3) | 0)
.equ VSETVLI_T2_A0_E8_M1, ((VTYPE_E8_M1_TA_MA << 20) | (10 << 15) | (7 << 12) | (7 << 7) | 0x57)
.equ VSETVLI_T2_A0_E32_M1, ((VTYPE_E32_M1_TA_MA << 20) | (10 << 15) | (7 << 12) | (7 << 7) | 0x57)
.equ VLE8_V1_T0, ((1 << 25) | (0 << 12) | (5 << 15) | (1 << 7) | 0x07)
.equ VLE32_V1_T0, ((1 << 25) | (6 << 12) | (5 << 15) | (1 << 7) | 0x07)
.equ VLE32_V2_T1, ((1 << 25) | (6 << 12) | (6 << 15) | (2 << 7) | 0x07)
.equ VLE32_V4_T1, ((1 << 25) | (6 << 12) | (6 << 15) | (4 << 7) | 0x07)
.equ VADD_VI_V2_V1_5, ((1 << 25) | (1 << 20) | (5 << 15) | (3 << 12) | (2 << 7) | 0x57)
.equ VMSLTU_VX_V2_V1_A1, ((0x1a << 26) | (1 << 25) | (1 << 20) | (11 << 15) | (4 << 12) | (2 << 7) | 0x57)
.equ VMSGT_VI_V3_V1_0, ((0x1f << 26) | (1 << 25) | (1 << 20) | (0 << 15) | (3 << 12) | (3 << 7) | 0x57)
.equ VMAND_MM_V4_V2_V3, ((0x19 << 26) | (1 << 25) | (2 << 20) | (3 << 15) | (2 << 12) | (4 << 7) | 0x57)
.equ VCPOP_M_A2_V3, ((0x10 << 26) | (1 << 25) | (3 << 20) | (16 << 15) | (2 << 12) | (12 << 7) | 0x57)
.equ VFIRST_M_A3_V3, ((0x10 << 26) | (1 << 25) | (3 << 20) | (17 << 15) | (2 << 12) | (13 << 7) | 0x57)
.equ VIOTA_M_V6_V3, ((0x14 << 26) | (1 << 25) | (3 << 20) | (16 << 15) | (2 << 12) | (6 << 7) | 0x57)
.equ VID_V_V7, ((0x14 << 26) | (1 << 25) | (0 << 20) | (17 << 15) | (2 << 12) | (7 << 7) | 0x57)
.equ VFADD_VV_V3_V1_V2, ((1 << 25) | (1 << 20) | (2 << 15) | (1 << 12) | (3 << 7) | 0x57)
.equ VREDSUM_VS_V5_V1_V4, ((1 << 25) | (1 << 20) | (4 << 15) | (2 << 12) | (5 << 7) | 0x57)
.equ VSE8_V4_T1, ((1 << 25) | (0 << 12) | (6 << 15) | (4 << 7) | 0x27)
.equ VSE8_V6_T1, ((1 << 25) | (0 << 12) | (6 << 15) | (6 << 7) | 0x27)
.equ VSE8_V7_T2, ((1 << 25) | (0 << 12) | (7 << 15) | (7 << 7) | 0x27)
.equ VSE32_V2_T1, ((1 << 25) | (6 << 12) | (6 << 15) | (2 << 7) | 0x27)
.equ VSE32_V3_T2, ((1 << 25) | (6 << 12) | (7 << 15) | (3 << 7) | 0x27)
.equ VSE32_V5_T2, ((1 << 25) | (6 << 12) | (7 << 15) | (5 << 7) | 0x27)

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

  TEST_CASE( 5, a0, 0x0000000000000000, \
    la t0, vector_float_left; \
    la t1, vector_float_right; \
    li a0, 4; \
    .word VSETVLI_T2_A0_E32_M1; \
    la t2, vector_float_output; \
    .word VLE32_V1_T0; \
    .word VLE32_V2_T1; \
    .word VFADD_VV_V3_V1_V2; \
    .word VSE32_V3_T2; \
    lw t3, 0(t2); \
    li t4, 0x40a00000; \
    bne t3, t4, 1f; \
    li a0, 0; \
    j 2f; \
  1: \
    li a0, 1; \
  2: \
  )

  TEST_CASE( 6, a0, 0x0000000000000000, \
    la t0, vector_reduce_input; \
    la t1, vector_reduce_accumulator; \
    li a0, 4; \
    .word VSETVLI_T2_A0_E32_M1; \
    la t2, vector_reduce_output; \
    .word VLE32_V1_T0; \
    .word VLE32_V4_T1; \
    .word VREDSUM_VS_V5_V1_V4; \
    .word VSE32_V5_T2; \
    lw t3, 0(t2); \
    li t4, 20; \
    bne t3, t4, 1f; \
    li a0, 0; \
    j 2f; \
  1: \
    li a0, 1; \
  2: \
  )

  TEST_CASE( 7, a0, 0x0000000000000000, \
    la t0, vector_input_bytes; \
    la t1, vector_iota_output; \
    la t2, vector_index_output; \
    li a0, 8; \
    .word VSETVLI_T2_A0_E8_M1; \
    la t2, vector_index_output; \
    .word VLE8_V1_T0; \
    .word VMSGT_VI_V3_V1_0; \
    .word VCPOP_M_A2_V3; \
    li t3, 5; \
    bne a2, t3, 1f; \
    .word VFIRST_M_A3_V3; \
    li t3, 0; \
    bne a3, t3, 1f; \
    .word VIOTA_M_V6_V3; \
    .word VSE8_V6_T1; \
    lbu t3, 7(t1); \
    li t4, 5; \
    bne t3, t4, 1f; \
    .word VID_V_V7; \
    .word VSE8_V7_T2; \
    lbu t3, 7(t2); \
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
vector_float_left:
  .word 0x3f800000, 0x40000000, 0x40400000, 0x40800000
vector_float_right:
  .word 0x40800000, 0x40400000, 0x40000000, 0x3f800000
vector_float_output:
  .word 0, 0, 0, 0
vector_reduce_input:
  .word 1, 2, 3, 4
vector_reduce_accumulator:
  .word 10, 0, 0, 0
vector_reduce_output:
  .word 0, 0, 0, 0
vector_iota_output:
  .byte 0, 0, 0, 0, 0, 0, 0, 0
vector_index_output:
  .byte 0, 0, 0, 0, 0, 0, 0, 0

RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
