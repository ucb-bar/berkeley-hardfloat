
// include files are part of the g++ command line

int main (int argc, char* argv[])
{
    if (argc < 3) {
        printf("usage: %s <rounding-mode> <tininess-detection>\n", argv[0]);
        return -1;
    }

    dut_t* module = new dut_t();
    module->init();
    initialize_dat_pointers(module);
    dat_from_hex<3>(argv[1], *roundingMode);
    dat_from_hex<1>(argv[2], *detectTininess);

    size_t error = 0;
    size_t cnt = 0;

    // reset
    for (size_t i=0; i<10; i++) {
        module->clock_lo(LIT<1>(1));
        module->clock_hi(LIT<1>(1));
    }

    // main operation
    for (;;) {
        if (!process_inputs() || !process_outputs()) {
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
                for (size_t i=0; i<inputs.size(); i++) {
                    printf(" %s", inputs[i]->to_str().c_str());
                }
                printf(
                    "\n\t=> %s %s   expected: %s %s\n",
                    actual_out->to_str().c_str(),
                    actual_exceptionFlags->to_str().c_str(),
                    expected_recOut->to_str().c_str(),
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

