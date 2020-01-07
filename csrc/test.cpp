#if VM_TRACE
# include "verilator.h"
#endif

// include files are part of the g++ command line

int main (int argc, char* argv[])
{
    if (argc < 3) {
        printf("usage: %s <rounding-mode> <tininess-detection>\n", argv[0]);
        return -1;
    }

    dut module;

#if VM_TRACE
    VerilatedVcdFILE vcdfd(stderr);
    VerilatedVcdC tfp(&vcdfd);
    Verilated::traceEverOn(true);
    module.trace(&tfp, 99);
    tfp.open("");
#endif

    initialize_dut(module);
    module.ROUNDING_MODE = strtoull(argv[1], NULL, 16);
    module.DETECT_TININESS = strtoull(argv[2], NULL, 16);

    size_t error = 0;
    size_t cnt = 0;

    // reset
    for (size_t i=0; i<10; i++) {
        module.reset = 1;
        module.clock = 0;
        module.eval();
        module.clock = 1;
        module.eval();
    }
    module.reset = 0;

    // main operation
    for (size_t cycle = 0; ; cycle++) {
        if (!process_inputs(module) || !process_outputs(module)) {
            printf("Ran %ld tests.\n", cnt);
            if (!error) fputs("No errors found.\n", stdout);
            break;
        }

        module.clock = 0;
        module.eval();

#if VM_TRACE
        tfp.dump(static_cast<vluint64_t>(cycle * 2));
#endif

        if (module.io_check) {
            if ((cnt % 10000 == 0) && cnt) printf("Ran %ld tests.\n", cnt);
            if (!module.io_pass) {
                error++;
                printf("[%07ld]", cnt);
                // for (size_t i=0; i<inputs.size(); i++) {
                //    printf(" %s", inputs[i]->to_str().c_str());
                // }
                printf(
                    "\n\t=> %#x %#x   expected: %#x %#x\n",
                    module.io_actual_out,
                    module.io_actual_exceptionFlags,
                    module.io_expected_recOut,
                    module.io_expected_exceptionFlags
                );
                if (error == 20) {
                    printf("Reached %ld errors. Aborting.\n", error);
                    break;
                }
            }
            cnt++;
        }

        module.clock = 1;
        module.eval();

#if VM_TRACE
        tfp.dump(static_cast<vluint64_t>(cycle * 2 + 1));
#endif
    }

    return 0;
}

