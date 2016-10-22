
static dat_t<3>* roundingMode;
static dat_t<1>* detectTininess;
static std::vector<dat_t<INFLEN>*> inputs;
static dat_t<OUTFLEN>* expected_out;
static dat_t<OUTFLEN+1>* expected_recOut;
static dat_t<5>* expected_exceptionFlags;
static dat_t<OUTFLEN+1>* actual_out;
static dat_t<5>* actual_exceptionFlags;
static dat_t<1>* check;
static dat_t<1>* pass;

#define _SIGNAL(inw, outw, s) (&m->ValExec_RecF##inw##ToRecF##outw##__io_##s)
#define SIGNAL(inw, outw, s) _SIGNAL(inw, outw, s)

static void initialize_dat_pointers(dut_t* m)
{
    roundingMode   = SIGNAL(INFLEN, OUTFLEN, roundingMode);
    detectTininess = SIGNAL(INFLEN, OUTFLEN, detectTininess);
    inputs.push_back(SIGNAL(INFLEN, OUTFLEN, in));
    expected_out = SIGNAL(INFLEN, OUTFLEN, expected_out);
    expected_recOut = SIGNAL(INFLEN, OUTFLEN, expected_recOut);
    expected_exceptionFlags = SIGNAL(INFLEN, OUTFLEN, expected_exceptionFlags);
    actual_out = SIGNAL(INFLEN, OUTFLEN, actual_out);
    actual_exceptionFlags = SIGNAL(INFLEN, OUTFLEN, actual_exceptionFlags);
    check = SIGNAL(INFLEN, OUTFLEN, check);
    pass = SIGNAL(INFLEN, OUTFLEN, pass);
}

static int process_inputs(void)
{
    char value[64];

    for (size_t i=0; i<inputs.size(); i++) {
        if (scanf("%s", value) != 1) {
            return 0;
        }
        dat_from_hex<INFLEN>(value, *inputs[i]);
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
    dat_from_hex<OUTFLEN>(value, *expected_out);

    // exception flags
    if (scanf("%s", value) != 1) {
        return 0;
    }
    dat_from_hex<5>(value, *expected_exceptionFlags);

    return 1;
}

