package hardfloat.test

import chisel3.RawModule
import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.AnnotationSeq
import firrtl.options.TargetDirAnnotation
import firrtl.stage.OutputFileAnnotation

import org.scalatest.ParallelTestExecution
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.parallel.CollectionConverters._

import java.text.SimpleDateFormat
import java.util.Calendar

trait FMATester extends AnyFlatSpec with Matchers with ParallelTestExecution {
  def exp(f: Int) = f match {
    case 16 => 5
    case 32 => 8
    case 64 => 11
  }

  def sig(f: Int) = f match {
    case 16 => 11
    case 32 => 24
    case 64 => 53
  }

  val roundings = Seq(
    "-rnear_even" -> "0",
    "-rminMag" -> "1",
    "-rmin" -> "2",
    "-rmax" -> "3",
    "-rnear_maxMag" -> "4",
    "-rodd" -> "6"
  )

  def check(stdouts: Seq[String]) = {
    stdouts foreach (_ shouldNot include("expected"))
    stdouts foreach (_ shouldNot include("Ran 0 tests."))
    stdouts foreach (_ should include("No errors found."))
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

    val testRunDir = os.pwd / "test_run_dir" / s"${this.getClass.getSimpleName}_$name" / s"${new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance.getTime)}"
    os.makeDir.all(testRunDir)
    /** elaborate module to [[testDir]]. */
    val annos: AnnotationSeq = (new chisel3.stage.ChiselStage).execute(
      Array("-X", "verilog"),
      Seq(
        TargetDirAnnotation(testRunDir.toString),
        ChiselGeneratorAnnotation(module)
      )
    )

    /* command Synthesis verilog to C++. */
    val verilatorCompile: Seq[String] = Seq(
      "verilator",
      "-cc",
      "--prefix", "dut",
      "--Mdir", testRunDir.toString,
      "-CFLAGS", s"""-I${getClass.getResource("/includes/").getPath} -include ${getClass.getResource(s"/includes/$name.h").getPath}""",
      annos.collectFirst {
        case OutputFileAnnotation(f) => f
      }.get + ".v",
      "--exe", s"${getClass.getResource(s"/csrc/$harness").getPath}"
    ) ++ (if (sys.env.contains("VCD")) Seq("--trace") else Seq.empty)
    os.proc(verilatorCompile).call(testRunDir)

    /* Build C++ executor. */
    val verilatorBuild: Seq[String] = Seq(
      "make",
      "-C", testRunDir.toString,
      "-j",
      "-f", s"dut.mk",
      "dut")
    os.proc(verilatorBuild).call(testRunDir)

    def executeAndLog(softfloatArg: Seq[String], dutArg: Seq[String]): String = {
      val stdoutFile = testRunDir / s"${name}__${(softfloatArg ++ dutArg).mkString("_")}.txt"
      val vcdFile = testRunDir / s"${name}__${(softfloatArg ++ dutArg).mkString("_")}.vcd"
      os.proc((testRunDir / "dut").toString +: dutArg).call(stdin = os.proc("testfloat_gen" +: softfloatArg).spawn().stdout, stdout = stdoutFile, stderr = vcdFile)
      os.read(stdoutFile)
    }

    (if (dutArgs.isDefined) {
      require(softfloatArgs.size == dutArgs.get.size, "size of softfloatArgs and dutArgs should be same.")
      (softfloatArgs zip dutArgs.get).par.map { case (s, d) => executeAndLog(s, d) }
    } else softfloatArgs.par.map { s => executeAndLog(s, Seq.empty) }).seq
  }
}
