
  reg [1:0] rm;

  wire read_ready;
  reg [63:0] read_input0;
  reg [63:0] read_expected_ieee;
  reg [4:0] read_expected_exception;

  wire [63:0] input0;
  wire [63:0] expected_ieee;
  wire [64:0] expected_recoded;
  wire [4:0] expected_exception;
  wire [63:0] actual_ieee;
  wire [64:0] actual_recoded;
  wire [4:0] actual_exception;
  wire check;
  wire pass;

  task process_stdin;
  
    if (read_ready)
    begin
      if ($fscanf(stdin, "%x %x %x", read_input0, read_expected_ieee, read_expected_exception) != 3) begin
        $display("done.");
`ifdef DEBUG
        $vcdplusclose();
`endif
        $finish();
      end
    end
  
  endtask
  
  task print_inputs;
  
    $fwrite(stdout, "i0=%x", input0);
  
  endtask

  Test_f64_sqrt dut
  (
    .clk(clk),
    .reset(reset),
    .io_input_ready(read_ready),
    .io_input_valid(1'b1),
    .io_input_bits_b(read_input0),
    .io_input_bits_rm(rm),
    .io_input_bits_out(read_expected_ieee),
    .io_input_bits_exception(read_expected_exception),
    .io_output_b(input0),
    .io_output_rm(),
    .io_expected_ieee(expected_ieee),
    .io_expected_exception(expected_exception),
    .io_expected_recoded(expected_recoded),
    .io_actual_ieee(actual_ieee),
    .io_actual_exception(actual_exception),
    .io_actual_recoded(actual_recoded),
    .io_check(check),
    .io_pass(pass)
  );


