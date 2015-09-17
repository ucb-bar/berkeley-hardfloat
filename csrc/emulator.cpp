// include files are part of the g++ command line

int main (int argc, char* argv[])
{
  if (argc < 2) {
    printf("usage: %s <rounding mode>\n", argv[0]);
    return -1;
  }

  dut_t* module = new dut_t();
  module->init();
  initialize_dat_pointers(module);
  dat_from_hex<2>(argv[1], *rm);

  size_t error = 0;
  size_t cnt = 0;

  // reset
  for (size_t i=0; i<10; i++) {
    module->clock_lo(LIT<1>(1));
    module->clock_hi(LIT<1>(1));
  }

  // main operation
  while (cnt < (1UL << 20)) {
    if (!process_inputs()) break;
    if (!process_outputs()) break;

    module->clock_lo(LIT<1>(0));

    if (check->to_bool()) {
      if (cnt % 10000 == 0) printf("ran %ld tests.\n", cnt);
      if (!pass->to_bool()) {
        error++;
        printf("[%07ld] ", cnt);
        for (size_t i=0; i<inputs.size(); i++) {
          printf("i%ld=%s ", i, inputs[i]->to_str().c_str());
        }
        printf("expected_ieee=%s actual_ieee=%s expected_recoded=%s actual_recoded=%s expected_exception=%s actual_exception=%s\n",
          expected_ieee->to_str().c_str(), actual_ieee->to_str().c_str(),
          expected_recoded->to_str().c_str(), actual_recoded->to_str().c_str(),
          expected_exception->to_str().c_str(), expected_exception->to_str().c_str());
        if (error == 20) {
          printf("reached %ld errors in %ld tests. aborting.\n", error, cnt);
          break;
        }
      }
      cnt++;
    }

    module->clock_hi(LIT<1>(0));
  }

  if (error > 0)
    printf("reached %ld errors in %ld tests. aborting.\n", error, cnt);
  
  return 0;
}
