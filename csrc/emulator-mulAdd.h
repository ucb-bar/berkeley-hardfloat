static dat_t<2>* rm;
static dat_t<1>* pass;
static std::vector<dat_t<FLEN>*> inputs;
static dat_t<FLEN>* expected_ieee;
static dat_t<FLEN>* actual_ieee;
static dat_t<FLEN+1>* expected_recoded;
static dat_t<FLEN+1>* actual_recoded;
static dat_t<5>* expected_exception;
static dat_t<5>* actual_exception;

#define _SIGNAL(w, s) (&m->Test_f##w##_mulAdd__io_##s)
#define SIGNAL(w, s) _SIGNAL(w, s)

static void initialize_dat_pointers(dut_t* m)
{
  rm = SIGNAL(FLEN, rm);
  pass = SIGNAL(FLEN, pass);
  inputs.push_back(SIGNAL(FLEN, a));
  inputs.push_back(SIGNAL(FLEN, b));
  inputs.push_back(SIGNAL(FLEN, c));
  expected_ieee = SIGNAL(FLEN, correct_out);
  actual_ieee = SIGNAL(FLEN, ieee_out);
  expected_recoded = SIGNAL(FLEN, recoded_correct_out);
  actual_recoded = SIGNAL(FLEN, recoded_out);
  expected_exception = SIGNAL(FLEN, correct_exception);
  actual_exception = SIGNAL(FLEN, exception);
}

static int process_inputs(std::vector<dat_t<FLEN>*>& inputs)
{
  char value[64];

  for (size_t i=0; i<inputs.size(); i++) {
    if (scanf("%s", value) != 1) {
      return 0;
    }
    dat_from_hex<FLEN>(value, *inputs[i]);
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
  dat_from_hex<FLEN>(value, *expected_ieee);

  // exception flags
  if (scanf("%s", value) != 1) {
    return 0;
  }
  dat_from_hex<5>(value, *expected_exception);

  return 1;
}
