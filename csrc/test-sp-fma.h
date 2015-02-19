#include "FMA.h"

static dat_t<2>* rm;
static dat_t<1>* pass;
static std::vector<dat_t<32>*> inputs;
static dat_t<32>* expected_ieee;
static dat_t<32>* actual_ieee;
static dat_t<33>* expected_recoded;
static dat_t<33>* actual_recoded;
static dat_t<5>* expected_exception;
static dat_t<5>* actual_exception;

static void initialize_dat_pointers(FMA_t* m)
{
  rm = &m->FMA__io_rm;
  pass = &m->FMA__io_pass;
  inputs.push_back(&m->FMA__io_a);
  inputs.push_back(&m->FMA__io_b);
  inputs.push_back(&m->FMA__io_c);
  expected_ieee = &m->FMA__io_correct_out;
  actual_ieee = &m->FMA__io_ieee_out;
  expected_recoded = &m->FMA__io_recoded_correct_out;
  actual_recoded = &m->FMA__io_recoded_out;
  expected_exception = &m->FMA__io_correct_exception;
  actual_exception = &m->FMA__io_exception;
}

static int process_inputs(std::vector<dat_t<32>*>& inputs)
{
  char value[64];

  for (size_t i=0; i<inputs.size(); i++) {
    if (scanf("%s", value) != 1) {
      return 0;
    }
    dat_from_hex<32>(value, *inputs[i]);
  }

  return 1;
}

static int process_outputs(void)
{
  char value[64];

  // output
  if (scanf("%s", value) != 1) {
    return 0;
  }
  dat_from_hex<32>(value, *expected_ieee);

  // exception flags
  if (scanf("%s", value) != 1) {
    return 0;
  }
  dat_from_hex<5>(value, *expected_exception);

  return 1;
}
