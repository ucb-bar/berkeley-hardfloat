package hardfloat.test

import java.io._

import chisel3.RawModule
import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.AnnotationSeq
import firrtl.options.TargetDirAnnotation
import firrtl.stage.OutputFileAnnotation
import scala.sys.process.{Process, ProcessLogger}

trait FMATester extends HardfloatTester {
  def check(stdouts: Seq[String]) = {
    stdouts foreach(_  shouldNot include("expected"))
    stdouts foreach(_  shouldNot include("Ran 0 tests."))
    stdouts foreach(_  should include("No errors found."))
  }

  def test(name: String, module: () => RawModule, softfloatArg: Seq[String]): Seq[String] = {
    val (softfloatArgs, dutArgs) = (roundings.map { case (s, d) =>
      (Seq(s, "-tininessbefore") ++ softfloatArg, Seq(d, "0"))
    } ++ roundings.map { case (s, d) =>
      (Seq(s, "-tininessafter") ++ softfloatArg, Seq(d, "1"))
    }).unzip
    test(name, module, "test.cpp", softfloatArgs, Some(dutArgs))
  }

  /** Run a FMA test. Before running, `softfloat_gen` should be accessible in the $PATH environment.
    *
    * @param name          is name of this test, which should corresponds to header's name in `includes` directory.
    * @param module        function to generate DUT.
    * @param harness       C++ harness name, which should corresponds to c++ hardness's name in `csrc` directory.
    * @param softfloatArgs arguments passed to `softfloat_gen` application. If has multiple command lines, multiple test will be executed.
    * @param dutArgs       arguments passed to verilator dut executor, If set to [[None]], no arguments will be passed to.
    */
  def test(name: String, module: () => RawModule, harness: String, softfloatArgs: Seq[Seq[String]], dutArgs: Option[Seq[Seq[String]]] = None) = {
    /** generated test directory. */
    val testDir: File = createTestDirectory(this.getClass.getSimpleName + s"_$name")
    val testDirAbsolutePath: String = testDir.toPath.toAbsolutePath.toString
    /** elaborate module to [[testDir]]. */
    val annos: AnnotationSeq = (new chisel3.stage.ChiselStage).execute(
      Array("-X", "verilog"),
      Seq(
        TargetDirAnnotation(testDirAbsolutePath),
        ChiselGeneratorAnnotation(module)
      )
    )

    /* command Synthesis verilog to C++. */
    val verilatorCompile: Seq[String] = Seq(
      "verilator",
      "-cc",
      "--prefix", "dut",
      "--Mdir", testDirAbsolutePath,
      "-CFLAGS", s"""-I${getClass.getResource("/includes/").getPath} -include ${getClass.getResource(s"/includes/$name.h").getPath}""",
      annos.collectFirst {
        case OutputFileAnnotation(f) => f
      }.get + ".v",
      "--exe", s"${getClass.getResource(s"/csrc/$harness").getPath}"
    ) ++ (if (sys.env.contains("VCD")) Seq("--trace") else Seq.empty)
    logger.warn("verilog to C++: " + verilatorCompile.mkString(" "))

    /* Build C++ executor. */
    val verilatorBuild: Seq[String] = Seq(
      "make",
      "-C", testDirAbsolutePath,
      "-j",
      "-f", s"dut.mk",
      "dut")
    logger.warn("C++ to executor: " + verilatorBuild.mkString(" "))

    Process(verilatorCompile) #&&
      Process(verilatorBuild) !
      ProcessLogger(logger.warn(_), logger.error(_))

    def executeAndLog(softfloatArg: Seq[String], dutArg: Seq[String]): String = {
      val stdoutFile = new File(s"${testDirAbsolutePath}/${name}__${(softfloatArg ++ dutArg).mkString("_")}.txt")
      val vcdFile = new File(s"${testDirAbsolutePath}/${name}__${(softfloatArg ++ dutArg).mkString("_")}.vcd")
      val stdout = new PrintWriter(stdoutFile)
      val stderr = new PrintWriter(vcdFile)
      Process("testfloat_gen" +: softfloatArg) #|
        ((testDirAbsolutePath + File.separator + "dut") +: dutArg) !
        ProcessLogger(s => stdout.write(s+"\n"), s=> stderr.write(s+"\n"))
      stdout.close()
      stderr.close()
      val f = io.Source.fromFile(stdoutFile)
      val ret = f.getLines().mkString("\n")
      f.close()
      ret
    }

    (if (dutArgs.isDefined) {
      require(softfloatArgs.size == dutArgs.get.size, "size of softfloatArgs and dutArgs should be same.")
      (softfloatArgs zip dutArgs.get).par.map { case (s, d) => executeAndLog(s, d)}
    } else softfloatArgs.par.map{s => executeAndLog(s, Seq.empty)}).seq
  }
}