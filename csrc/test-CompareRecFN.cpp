
// include files are part of the g++ command line

#define _SIGNAL(fw, op, s) (&module->ValExec_CompareRecF##fw##_##op##__io_##s)
#define SIGNAL(fw, op, s) _SIGNAL(fw, op, s)

static int process_inputs(dat_t<FLEN>* inputA, dat_t<FLEN>* inputB)
{
    char value[64];

    if (scanf("%s", value) != 1) return 0;
    dat_from_hex<FLEN>(value, *inputA);
    if (scanf("%s", value) != 1) return 0;
    dat_from_hex<FLEN>(value, *inputB);
    return 1;
}

static
 int
  process_outputs(dat_t<1>* expected_out, dat_t<5>* expected_exceptionFlags)
{
    char value[64];

    // output
    if (scanf("%s", value) != 1) return 0;
    dat_from_hex<1>(value, *expected_out);

    // exception flags
    if (scanf("%s", value) != 1) return 0;
    dat_from_hex<5>(value, *expected_exceptionFlags);

    return 1;
}

int main (int argc, char* argv[])
{
    dat_t<FLEN>* inputA;
    dat_t<FLEN>* inputB;
    dat_t<1>* expected_out;
    dat_t<5>* expected_exceptionFlags;
    dat_t<1>* actual_out;
    dat_t<5>* actual_exceptionFlags;
    dat_t<1>* check;
    dat_t<1>* pass;
    dut_t* module = new dut_t();
    size_t cnt = 0;
    size_t error = 0;

    module->init();

    inputA = SIGNAL(FLEN, compareOp, a);
    inputB = SIGNAL(FLEN, compareOp, b);
    expected_out = SIGNAL(FLEN, compareOp, expected_out);
    expected_exceptionFlags = SIGNAL(FLEN, compareOp, expected_exceptionFlags);
    actual_out = SIGNAL(FLEN, compareOp, actual_out);
    actual_exceptionFlags = SIGNAL(FLEN, compareOp, actual_exceptionFlags);
    check = SIGNAL(FLEN, compareOp, check);
    pass = SIGNAL(FLEN, compareOp, pass);

    // reset
    for (size_t i=0; i<10; i++) {
        module->clock_lo(LIT<1>(1));
        module->clock_hi(LIT<1>(1));
    }

    // main operation
    for (;;) {
        if (
               ! process_inputs(inputA, inputB)
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
                printf(
                    " %s %s",
                    inputA->to_str().c_str(),
                    inputB->to_str().c_str()
                );
                printf(
                    " => %s %s   expected: %s %s\n",
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

