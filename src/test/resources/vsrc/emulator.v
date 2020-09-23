module hardfloatTestHarness;

  reg clk = 1;
  always #0.5 clk = ~clk;

  reg reset = 1;

`include `EXPERIMENT

  integer cnt = 0;
  integer error = 0;
  integer stdin = 32'h8000_0000;
  integer stdout = 32'h8000_0001;
  reg [1023:0] vcdplusfile = 0;

  initial
  begin
    if (!$value$plusargs("rm=%d", rm))
    begin
      $display("specify rounding mode with +rm=<rounding mode>!");
      $finish();
    end
`ifdef DEBUG
    if ($value$plusargs("vcdplusfile=%s", vcdplusfile))
    begin
      $vcdplusfile(vcdplusfile);
      $vcdpluson(0);
      $vcdplusmemon(0);
    end
`endif
    #77.2; reset = 0;
  end

  always @(negedge clk)
  begin
    if (!reset)
    begin
      process_stdin;
    end
  end

  always @(posedge clk)
  begin
    if (!reset && check === 1'b1)
    begin
      cnt <= cnt + 1'd1;
      if (cnt % 10000 == 0) begin
        $display("ran %d tests.", cnt);
      end
      if (pass !== 1'b1) begin
        $fwrite(stdout, "[%d] ", cnt);
        print_inputs;
        $fwrite(stdout, " expected_ieee=%x actual_ieee=%x expected_recoded=%x actual_recoded=%x expected_exception=%x actual_expection=%x\n",
          expected_ieee, actual_ieee,
          expected_recoded, actual_recoded,
          expected_exception, actual_exception);

        error = error + 1;
        if (error == 20) begin
          $display("reached %d errors. aborting.\n", error);
`ifdef DEBUG
          $vcdplusclose();
`endif
          $finish();
        end
      end
    end
  end

endmodule
