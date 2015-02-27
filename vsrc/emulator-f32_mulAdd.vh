
  reg [31:0] input0;
  reg [31:0] input1;
  reg [31:0] input2;
  reg [1:0] rm;
  reg [31:0] expected_ieee;
  wire [32:0] expected_recoded;
  reg [4:0] expected_exception;
  wire [31:0] actual_ieee;
  wire [32:0] actual_recoded;
  wire [4:0] actual_exception;
  wire check;
  wire pass;

  task process_stdin;
  
    if ($fscanf(stdin, "%x %x %x %x %x", input0, input1, input2, expected_ieee, expected_exception) != 5) begin
      $display("done.");
`ifdef DEBUG
      $vcdplusclose();
`endif
      $finish();
    end
  
  endtask
  
  task print_inputs;
  
    $fwrite(stdout, "i0=%x i1=%x i2=%x", input0, input1, input2);
  
  endtask

  Test_f32_mulAdd dut
  (
    .io_a(input0),
    .io_b(input1),
    .io_c(input2),
    .io_rm(rm),
    .io_expected_ieee(expected_ieee),
    .io_expected_exception(expected_exception),
    .io_expected_recoded(expected_recoded),
    .io_actual_ieee(actual_ieee),
    .io_actual_exception(actual_exception),
    .io_actual_recoded(actual_recoded),
    .io_check(check),
    .io_pass(pass)
  );

