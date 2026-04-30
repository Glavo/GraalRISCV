#include "riscv_test.h"
#include "test_macros.h"

.option arch, +m, +c

RVTEST_RV64U
RVTEST_CODE_BEGIN

  TEST_CASE( 2, a0, 0x0000000000000001, \
    li a1, -5; \
    li a2, 7; \
    slt a0, a1, a2; \
  )

  TEST_CASE( 3, a0, 0x0000000000000000, \
    li a1, -1; \
    li a2, 2; \
    sltu a0, a1, a2; \
  )

  TEST_CASE( 4, a0, 0xfffffffffffffffe, \
    li a1, -1; \
    li a2, -1; \
    mulhu a0, a1, a2; \
  )

  TEST_CASE( 5, a0, 0xffffffffffffffff, \
    li a1, -1; \
    li a2, 2; \
    mulhsu a0, a1, a2; \
  )

  TEST_CASE( 6, a0, 0xffffffffffffffff, \
    li a1, 0x8000000000000000; \
    li a2, 2; \
    mulh a0, a1, a2; \
  )

  TEST_CASE( 7, a0, 0x000000000000000f, \
    li a0, 0xff; \
    li a1, 0x0f; \
    c.and a0, a1; \
  )

  TEST_CASE( 8, a0, 0xfffffffffffffffe, \
    li a0, -1; \
    c.addiw a0, -1; \
  )

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
