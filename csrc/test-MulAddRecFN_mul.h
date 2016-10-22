
static dat_t<3>* roundingMode;
static dat_t<1>* detectTininess;
static std::vector<dat_t<FLEN>*> inputs;
static dat_t<FLEN>* expected_out;
static dat_t<FLEN+1>* expected_recOut;
static dat_t<5>* expected_exceptionFlags;
static dat_t<FLEN+1>* actual_out;
static dat_t<5>* actual_exceptionFlags;
static dat_t<1>* check;
static dat_t<1>* pass;

#define _SIGNAL(w, s) (&m->ValExec_MulAddRecF##w##_mul__io_##s)
#define SIGNAL(w, s) _SIGNAL(w, s)

static void initialize_dat_pointers(dut_t* m)
{
  roundingMode   = SIGNAL(FLEN, roundingMode);
  detectTininess = SIGNAL(FLEN, detectTininess);
  inputs.push_back(SIGNAL(FLEN, a));
  inputs.push_back(SIGNAL(FLEN, b));
  expected_out = SIGNAL(FLEN, expected_out);
  expected_recOut = SIGNAL(FLEN, expected_recOut);
  expected_exceptionFlags = SIGNAL(FLEN, expected_exceptionFlags);
  actual_out = SIGNAL(FLEN, actual_out);
  actual_exceptionFlags = SIGNAL(FLEN, actual_exceptionFlags);
  check = SIGNAL(FLEN, check);
  pass = SIGNAL(FLEN, pass);
}

static int process_inputs(void)
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
  dat_from_hex<FLEN>(value, *expected_out);

  // exception flags
  if (scanf("%s", value) != 1) {
    return 0;
  }
  dat_from_hex<5>(value, *expected_exceptionFlags);

  return 1;
}

