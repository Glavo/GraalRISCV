#include "riscv_test.h"
#include "test_macros.h"

.option arch, +zba, +zbb, +zbs, +zfhmin, +zfa

RVTEST_RV64UF
RVTEST_CODE_BEGIN

  TEST_CASE( 2, a0, 0x0000000000000021, \
    li a1, 0x10; \
    li a2, 0x01; \
    sh1add a0, a1, a2; \
  )

  TEST_CASE( 3, a0, 0x000000000000003f, \
    li a1, 0x01; \
    clz a0, a1; \
  )

  TEST_CASE( 4, a0, 0x0000000000000080, \
    li a1, 0x00; \
    bseti a0, a1, 7; \
  )

  TEST_CASE( 5, a0, 0x0000000000003c00, \
    li a1, 0x3c00; \
    fmv.h.x ft0, a1; \
    fmv.x.h a0, ft0; \
  )

  TEST_CASE( 6, a0, 0x0000000000003c00, \
    li a1, 0x3f800000; \
    fmv.w.x ft0, a1; \
    fcvt.h.s ft1, ft0, rne; \
    fmv.x.h a0, ft1; \
  )

  TEST_CASE( 7, a0, 0x000000003f800000, \
    li a1, 0x3c00; \
    fmv.h.x ft0, a1; \
    fcvt.s.h ft1, ft0; \
    fmv.x.w a0, ft1; \
  )

  TEST_CASE( 8, a0, 0x000000003f800000, \
    fli.s ft0, 1.0; \
    fmv.x.w a0, ft0; \
  )

  TEST_CASE( 9, a0, 0x4000000000000000, \
    fli.d ft0, 2.0; \
    fmv.x.d a0, ft0; \
  )

  TEST_CASE( 10, a0, 0x000000003f800000, \
    li a1, 0x3fe00000; \
    fmv.w.x ft0, a1; \
    fround.s ft1, ft0, rtz; \
    fmv.x.w a0, ft1; \
  )

  TEST_CASE( 11, a0, 0x0000000000000001, \
    li a1, 0x41f0000000100000; \
    fmv.d.x ft0, a1; \
    fcvtmod.w.d a0, ft0, rtz; \
  )

  TEST_CASE( 12, a0, 0x000000007fc00000, \
    li a1, 0x7fc00000; \
    fmv.w.x ft0, a1; \
    li a2, 0x40000000; \
    fmv.w.x ft1, a2; \
    fminm.s ft2, ft0, ft1; \
    fmv.x.w a0, ft2; \
  )

  TEST_CASE( 13, a0, 0x0000000000000000, \
    li a1, 0x7ff8000000000000; \
    fmv.d.x ft0, a1; \
    fli.d ft1, 1.0; \
    fleq.d a0, ft0, ft1; \
  )

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
