
// include files are part of the g++ command line

#define _SIGNAL(fw, iw, s) (&module->ValExec_RecF##fw##ToI##iw##__io_##s)
#define SIGNAL(fw, iw, s) _SIGNAL(fw, iw, s)

static int process_inputs(dat_t<FLEN>* input)
{
    char value[64];

    if (scanf("%s", value) != 1) return 0;
    dat_from_hex<FLEN>(value, *input);
    return 1;
}

static
 int
  process_outputs(dat_t<ILEN>* expected_out, dat_t<5>* expected_exceptionFlags)
{
    char value[64];

    // output
    if (scanf("%s", value) != 1) return 0;
    dat_from_hex<ILEN>(value, *expected_out);

    // exception flags
    if (scanf("%s", value) != 1) return 0;
    dat_from_hex<5>(value, *expected_exceptionFlags);

    return 1;
}

int main (int argc, char* argv[])
{
    if (argc < 2) {
        printf("usage: %s <rounding mode>\n", argv[0]);
        return -1;
    }

    dat_t<2>* roundingMode;
    dat_t<FLEN>* input;
    dat_t<ILEN>* expected_out;
    dat_t<5>* expected_exceptionFlags;
    dat_t<ILEN>* actual_out;
    dat_t<5>* actual_exceptionFlags;
    dat_t<1>* check;
    dat_t<1>* pass;
    dut_t* module = new dut_t();
    size_t cnt = 0;
    size_t error = 0;
    char value[64];

    module->init();

    roundingMode = SIGNAL(FLEN, ILEN, roundingMode);
    input = SIGNAL(FLEN, ILEN, in);
    expected_out = SIGNAL(FLEN, ILEN, expected_out);
    expected_exceptionFlags = SIGNAL(FLEN, ILEN, expected_exceptionFlags);
    actual_out = SIGNAL(FLEN, ILEN, actual_out);
    actual_exceptionFlags = SIGNAL(FLEN, ILEN, actual_exceptionFlags);
    check = SIGNAL(FLEN, ILEN, check);
    pass = SIGNAL(FLEN, ILEN, pass);

    dat_from_hex<2>(argv[1], *roundingMode);

    // reset
    for (size_t i=0; i<10; i++) {
        module->clock_lo(LIT<1>(1));
        module->clock_hi(LIT<1>(1));
    }

    // main operation
    for (;;) {
        if (
               ! process_inputs(input)
            || ! process_outputs(expected_out, expected_exceptionFlags)
        ) {
            printf("Ran %ld tests.\n", cnt);
            if (!error) fputs("No errors found.\n", stdout);
            break;
        }

        module->clock_lo(LIT<1>(0));

        if (check->to_bool()) {
            if ((cnt % 10000 == 0) && cnt) printf("Ran %ld tests.\n", cnt);
            if (!pass->to_bool()) {
                error++;
                printf("[%07ld]", cnt);
                printf(" %s", input->to_str().c_str());
                printf(
                    "\n\t=> %s %s   expected: %s %s\n",
                    actual_out->to_str().c_str(),
                    actual_exceptionFlags->to_str().c_str(),
                    expected_out->to_str().c_str(),
                    expected_exceptionFlags->to_str().c_str()
                );
                if (error == 20) {
                    printf("Reached %ld errors. Aborting.\n", error);
                    break;
                }
            }
            cnt++;
        }

        module->clock_hi(LIT<1>(0));
    }

    return 0;
}

