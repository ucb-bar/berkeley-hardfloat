#include "dut.h"

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
  m.io_input_bits_a = strtoull(value, NULL, 16);

  return 1;
}

static int process_outputs(dut& m)
{
  char value[64];

  if (!m.io_input_ready) {
    return 1;
  }

  // output
  if (scanf("%s", value) != 1) {
    return 0;
  }
  m.io_input_bits_out = strtoull(value, NULL, 16);

  // exception flags
  if (scanf("%s", value) != 1) {
    return 0;
  }
  m.io_input_bits_exceptionFlags = strtoull(value, NULL, 16);

  return 1;
}

