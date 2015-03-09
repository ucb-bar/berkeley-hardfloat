static dat_t<2>* rm;

static dat_t<1>* read_ready;
static dat_t<1>* read_valid;
static std::vector<dat_t<FLEN>*> read_inputs;
static dat_t<FLEN>* read_expected_ieee;
static dat_t<5>* read_expected_exception;

static std::vector<dat_t<FLEN>*> inputs;
static dat_t<FLEN>* expected_ieee;
static dat_t<FLEN+1>* expected_recoded;
static dat_t<5>* expected_exception;
static dat_t<FLEN>* actual_ieee;
static dat_t<FLEN+1>* actual_recoded;
static dat_t<5>* actual_exception;
static dat_t<1>* check;
static dat_t<1>* pass;

#define _SIGNAL(w, s) (&m->Test_f##w##_div__io_##s)
#define SIGNAL(w, s) _SIGNAL(w, s)

static void initialize_dat_pointers(dut_t* m)
{
  rm = SIGNAL(FLEN, input_bits_rm);

  read_ready = SIGNAL(FLEN, input_ready);
  read_valid = SIGNAL(FLEN, input_valid);
  read_inputs.push_back(SIGNAL(FLEN, input_bits_a));
  read_inputs.push_back(SIGNAL(FLEN, input_bits_b));
  read_expected_ieee = SIGNAL(FLEN, input_bits_out);
  read_expected_exception = SIGNAL(FLEN, input_bits_exception);

  inputs.push_back(SIGNAL(FLEN, output_a));
  inputs.push_back(SIGNAL(FLEN, output_b));
  expected_ieee = SIGNAL(FLEN, expected_ieee);
  expected_recoded = SIGNAL(FLEN, expected_recoded);
  expected_exception = SIGNAL(FLEN, expected_exception);
  actual_ieee = SIGNAL(FLEN, actual_ieee);
  actual_recoded = SIGNAL(FLEN, actual_recoded);
  actual_exception = SIGNAL(FLEN, actual_exception);
  check = SIGNAL(FLEN, check);
  pass = SIGNAL(FLEN, pass);

  *read_valid = LIT<1>(1);
}

static int process_inputs(void)
{
  char value[64];

  if (!read_ready->to_bool()) {
    return 1;
  }

  for (size_t i=0; i<read_inputs.size(); i++) {
    if (scanf("%s", value) != 1) {
      return 0;
    }
    dat_from_hex<FLEN>(value, *read_inputs[i]);
  }

  return 1;
}

static int process_outputs(void)
{
  char value[64];

  if (!read_ready->to_bool()) {
    return 1;
  }

  // output
  if (scanf("%s", value) != 1) {
    return 0;
  }
  dat_from_hex<FLEN>(value, *read_expected_ieee);

  // exception flags
  if (scanf("%s", value) != 1) {
    return 0;
  }
  dat_from_hex<5>(value, *read_expected_exception);

  return 1;
}
