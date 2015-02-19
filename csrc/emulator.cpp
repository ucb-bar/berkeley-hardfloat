// include files are part of the g++ command line

int main (int argc, char* argv[])
{
  if (argc < 2) {
    printf("usage: %s <rounding mode>\n", argv[0]);
    return -1;
  }

  FMA_t* module = new FMA_t();
  module->init();
  initialize_dat_pointers(module);

  size_t cnt = 0;

  while (true) {
    if (!process_inputs(inputs)) break;
    if (!process_outputs()) break;

    module->clock_lo(LIT<1>(0));
    if (cnt % 10000 == 0) printf("ran %ld tests.\n", cnt);
    cnt++;
    if (!pass->to_bool()) {
      printf("[%07ld] ", cnt);
      for (size_t i=0; i<inputs.size(); i++) {
        printf("i%ld=%s ", i, inputs[i]->to_str().c_str());
      }
      printf("expected_ieee=%s actual_ieee=%s expected_recoded=%s actual_recoded=%s expected_exception=%s actual_exception=%s\n",
        expected_ieee->to_str().c_str(), actual_ieee->to_str().c_str(),
        expected_recoded->to_str().c_str(), actual_recoded->to_str().c_str(),
        expected_exception->to_str().c_str(), expected_exception->to_str().c_str());
    }
    module->clock_hi(LIT<1>(0));
  }
  
  return 0;
}
