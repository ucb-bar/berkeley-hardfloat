
static dat_t<3>* roundingMode;
static dat_t<1>* detectTininess;
static std::vector<dat_t<ILEN>*> inputs;
static dat_t<FLEN>* expected_out;
static dat_t<FLEN+1>* expected_recOut;
static dat_t<5>* expected_exceptionFlags;
static dat_t<FLEN+1>* actual_out;
static dat_t<5>* actual_exceptionFlags;
static dat_t<1>* check;
static dat_t<1>* pass;

#define _SIGNAL(iw, fw, s) (&m->ValExec_UI##iw##ToRecF##fw##__io_##s)
#define SIGNAL(iw, fw, s) _SIGNAL(iw, fw, s)

static void initialize_dat_pointers(dut_t* m)
{
  roundingMode   = SIGNAL(ILEN, FLEN, roundingMode);
  detectTininess = SIGNAL(ILEN, FLEN, detectTininess);
  inputs.push_back(SIGNAL(ILEN, FLEN, in));
  expected_out = SIGNAL(ILEN, FLEN, expected_out);
  expected_recOut = SIGNAL(ILEN, FLEN, expected_recOut);
  expected_exceptionFlags = SIGNAL(ILEN, FLEN, expected_exceptionFlags);
  actual_out = SIGNAL(ILEN, FLEN, actual_out);
  actual_exceptionFlags = SIGNAL(ILEN, FLEN, actual_exceptionFlags);
  check = SIGNAL(ILEN, FLEN, check);
  pass = SIGNAL(ILEN, FLEN, pass);
}

static int process_inputs(void)
{
  char value[64];

  if (scanf("%s", value) != 1) {
    return 0;
  }
  dat_from_hex<ILEN>(value, *inputs[0]);

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

