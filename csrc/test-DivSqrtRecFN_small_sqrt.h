
static dat_t<3>* roundingMode;
static dat_t<1>* detectTininess;

static dat_t<1>* read_ready;
static dat_t<1>* read_valid;
static std::vector<dat_t<FLEN>*> read_inputs;
static dat_t<FLEN>* read_expected_out;
static dat_t<5>* read_expected_exceptionFlags;

static std::vector<dat_t<FLEN>*> inputs;
static dat_t<FLEN>* expected_out;
static dat_t<FLEN+1>* expected_recOut;
static dat_t<5>* expected_exceptionFlags;
static dat_t<FLEN+1>* actual_out;
static dat_t<5>* actual_exceptionFlags;
static dat_t<1>* check;
static dat_t<1>* pass;

#define _SIGNAL(w, s) (&m->ValExec_DivSqrtRecF##w##_small_sqrt__io_##s)
#define SIGNAL(w, s) _SIGNAL(w, s)

static void initialize_dat_pointers(dut_t* m)
{
  roundingMode   = SIGNAL(FLEN, input_bits_roundingMode);
  detectTininess = SIGNAL(FLEN, input_bits_detectTininess);

  read_ready = SIGNAL(FLEN, input_ready);
  read_valid = SIGNAL(FLEN, input_valid);
  read_inputs.push_back(SIGNAL(FLEN, input_bits_a));
  read_expected_out = SIGNAL(FLEN, input_bits_out);
  read_expected_exceptionFlags = SIGNAL(FLEN, input_bits_exceptionFlags);

  inputs.push_back(SIGNAL(FLEN, output_a));
  expected_out = SIGNAL(FLEN, expected_out);
  expected_recOut = SIGNAL(FLEN, expected_recOut);
  expected_exceptionFlags = SIGNAL(FLEN, expected_exceptionFlags);
  actual_out = SIGNAL(FLEN, actual_out);
  actual_exceptionFlags = SIGNAL(FLEN, actual_exceptionFlags);
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
  dat_from_hex<FLEN>(value, *read_expected_out);

  // exception flags
  if (scanf("%s", value) != 1) {
    return 0;
  }
  dat_from_hex<5>(value, *read_expected_exceptionFlags);

  return 1;
}

