
// include files are part of the g++ command line

#define _SIGNAL(w, s) (&module->ValExec_f##w##FromRecF##w##__io_##s)
#define SIGNAL(w, s) _SIGNAL(w, s)

int main (int argc, char* argv[])
{
    dat_t<FLEN>* input;
    dat_t<FLEN>* output;
    dat_t<1>* check;
    dat_t<1>* pass;
    dut_t* module = new dut_t();
    size_t cnt = 0;
    size_t error = 0;
    char value[64];

    module->init();

    input = SIGNAL(FLEN, a);
    output = SIGNAL(FLEN, out);
    check = SIGNAL(FLEN, check);
    pass = SIGNAL(FLEN, pass);

    // reset
    for (size_t i=0; i<10; i++) {
        module->clock_lo(LIT<1>(1));
        module->clock_hi(LIT<1>(1));
    }

    // main operation
    for (;;) {
        if (scanf("%s", value) != 1) {
            printf("Ran %ld tests.\n", cnt);
            if (!error) fputs("No errors found.\n", stdout);
            break;
        }
        dat_from_hex<FLEN>(value, *input);

        module->clock_lo(LIT<1>(0));

        if (check->to_bool()) {
            if ((cnt % 10000 == 0) && cnt) printf("Ran %ld tests.\n", cnt);
            if (!pass->to_bool()) {
                error++;
                printf(
                    "[%07ld] %s => %s\n",
                    cnt,
                    input->to_str().c_str(),
                    output->to_str().c_str()
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

