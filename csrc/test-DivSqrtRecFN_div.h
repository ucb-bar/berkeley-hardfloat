#include "dut.h"

#define ROUNDING_MODE io_roundingMode
#define DETECT_TININESS io_detectTininess

static void initialize_dut(dut& m)
{
  m.io_input_valid = 1;
}

static int process_inputs(dut& m)
{
  char value[64];

  if (!m.io_input_ready) {
    return 1;
  }

  if (scanf("%s", value) != 1) {
    return 0;
  }
  m.io_output_a = strtoll(value, NULL, 16);

  if (scanf("%s", value) != 1) {
    return 0;
  }
  m.io_output_b = strtoll(value, NULL, 16);

  return 1;
}

static int process_outputs(dut &m)
{
  char value[64];

  if (!m.io_input_ready) {
    return 1;
  }

  // output
  if (scanf("%s", value) != 1) {
    return 0;
  }
  m.io_input_bits_out = strtoll(value, NULL, 16);

  // exception flags
  if (scanf("%s", value) != 1) {
    return 0;
  }
  m.io_input_bits_exceptionFlags = strtoll(value, NULL, 16);

  return 1;
}

