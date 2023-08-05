
// include files are part of the g++ command line

#include "dut.h"
#include "verilated.h"

int main (int argc, char* argv[])
{
    dut module;
    size_t cnt = 0;
    size_t error = 0;
    char value[64];

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
    for (;;) {
        if (scanf("%s", value) != 1) {
            printf("Ran %ld tests.\n", cnt);
            if (!error) fputs("No errors found.\n", stdout);
            break;
        }
        module.io_a = strtoull(value, NULL, 16);
        // dat_from_hex<FLEN>(value, *input);

        module.clock = 0;
        module.eval();

        if (module.io_check) {
            if ((cnt % 10000 == 0) && cnt) printf("Ran %ld tests.\n", cnt);
            if (!module.io_pass) {
                error++;
                printf(
                    "[%07ld] %#x => %#x\n",
                    cnt,
                    module.io_a,
                    module.io_out
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
    }

    return 0;
}

