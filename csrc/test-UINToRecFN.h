#include "dut.h"

#define ROUNDING_MODE io_roundingMode
#define DETECT_TININESS io_detectTininess

static void initialize_dut(dut& m)
{
}

static int process_inputs(dut& m)
{
  char value[64];

  if (scanf("%s", value) != 1) {
    return 0;
  }
  m.io_in = strtoull(value, NULL, 16);

  return 1;
}

static int process_outputs(dut& m)
{
  char value[64];

  // output
  if (scanf("%s", value) != 1) {
    return 0;
  }

  m.io_expected_out = strtoull(value, NULL, 16);

  // exception flags
  if (scanf("%s", value) != 1) {
    return 0;
  }

  m.io_expected_exceptionFlags = strtoull(value, NULL, 16);

  return 1;
}

