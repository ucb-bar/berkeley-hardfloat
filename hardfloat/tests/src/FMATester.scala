package hardfloat.test

import chisel3.RawModule
import org.scalatest.ParallelTestExecution
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.text.SimpleDateFormat
import java.util.Calendar
import scala.collection.parallel.CollectionConverters._

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
    os.write(testRunDir / "dut.v", chisel3.getVerilogString(module()))

    /* command Synthesis verilog to C++. */
    val verilatorCompile: Seq[String] = Seq(
      "verilator",
      "-cc",
      "--prefix", "dut",
      "--Mdir", testRunDir.toString,
      "-CFLAGS", s"""-I${getClass.getResource("/includes/").getPath} -include ${getClass.getResource(s"/includes/$name.h").getPath}""",
      "dut.v",
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

import hardfloat.consts

class AddRecFNSpec extends FMATester {
  def test(f: Int): Seq[String] = {
    test(s"AddRecF${f}",
      () => new ValExec_AddRecFN(exp(f), sig(f)),
      Seq(s"f${f}_add")
    )
  }
  "AddRecF16" should "pass" in {
    check(test(16))
  }
  "AddRecF32" should "pass" in {
    check(test(32))
  }
  "AddRecF64" should "pass" in {
    check(test(64))
  }
}

class CompareRecFNSpec extends FMATester {
  def test(f: Int, fn: String): Seq[String] = {
    val generator = fn match {
      case "lt" => () => new ValExec_CompareRecFN_lt(exp(f), sig(f))
      case "le" => () => new ValExec_CompareRecFN_le(exp(f), sig(f))
      case "eq" => () => new ValExec_CompareRecFN_eq(exp(f), sig(f))
    }
    test(
      s"CompareRecF${f}_$fn",
      generator,
      "CompareRecFN.cpp",
      Seq(Seq(s"f${f}_${fn}"))
    )
  }

  "CompareRecF16_lt" should "pass" in {
    check(test(16, "lt"))
  }
  "CompareRecF32_lt" should "pass" in {
    check(test(32, "lt"))
  }
  "CompareRecF64_lt" should "pass" in {
    check(test(64, "lt"))
  }
  "CompareRecF16_le" should "pass" in {
    check(test(16, "le"))
  }
  "CompareRecF32_le" should "pass" in {
    check(test(32, "le"))
  }
  "CompareRecF64_le" should "pass" in {
    check(test(64, "le"))
  }
  "CompareRecF16_eq" should "pass" in {
    check(test(16, "eq"))
  }
  "CompareRecF32_eq" should "pass" in {
    check(test(32, "eq"))
  }
  "CompareRecF64_eq" should "pass" in {
    check(test(64, "eq"))
  }
}

class DivSqrtRecF64Spec extends FMATester {
  def test(fn: String): Seq[String] = {
    val generator = fn match {
      case "div" => () => new ValExec_DivSqrtRecF64_div
      case "sqrt" => () => new ValExec_DivSqrtRecF64_sqrt
    }
    test(
      s"DivSqrtRecF64_${fn}",
      generator,
      (if (fn == "sqrt") Seq("-level2") else Seq.empty) ++ Seq(s"f64_${fn}")
    )
  }
  "DivSqrtRecF64_div" should "pass" in {
    check(test("div"))
  }
  "DivSqrtRecF64_sqrt" should "pass" in {
    check(test("sqrt"))
  }
}

class DivSqrtRecFn_smallSpec extends FMATester {
  def test(f: Int, fn: String): Seq[String] = {
    def generator(options: Int) = fn match {
      case "div" => () => new ValExec_DivSqrtRecFN_small_div(exp(f), sig(f), options)
      case "sqrt" => () => new ValExec_DivSqrtRecFN_small_sqrt(exp(f), sig(f), options)
    }
    test(
      s"DivSqrtRecF${f}_small_${fn}",
      generator(0),
      (if (fn == "sqrt") Seq("-level2") else Seq.empty) ++ Seq(s"f${f}_${fn}")
    )
    test(
      s"DivSqrtRecF${f}_small_${fn}",
      generator(consts.divSqrtOpt_twoBitsPerCycle),
      (if (fn == "sqrt") Seq("-level2") else Seq.empty) ++ Seq(s"f${f}_${fn}")
    )
  }
  "DivSqrtRecF16_small_div" should "pass" in {
    check(test(16, "div"))
  }
  "DivSqrtRecF32_small_div" should "pass" in {
    check(test(32, "div"))
  }
  "DivSqrtRecF64_small_div" should "pass" in {
    check(test(64, "div"))
  }
  "DivSqrtRecF16_small_sqrt" should "pass" in {
    check(test(16, "sqrt"))
  }
  "DivSqrtRecF32_small_sqrt" should "pass" in {
    check(test(32, "sqrt"))
  }
  "DivSqrtRecF64_small_sqrt" should "pass" in {
    check(test(64, "sqrt"))
  }
}

class FnFromRecFnSpec extends FMATester {
  def test(f: Int): Seq[String] = {
    test(
      s"f${f}FromRecF${f}",
      () => new ValExec_fNFromRecFN(exp(f), sig(f)),
      "fNFromRecFN.cpp",
      Seq(Seq("-level2", s"-f${f}"))
    )
  }

  "f16FromRecF16" should "pass" in {
    check(test(16))
  }

  "f32FromRecF32" should "pass" in {
    check(test(32))
  }

  "f64FromRecF64" should "pass" in {
    check(test(64))
  }
}

class INToRecFNSpec extends FMATester {
  def test(i: Int, f: Int): Seq[String] = {
    test(
      s"I${i}ToRecF${f}",
      () => new ValExec_INToRecFN(i, exp(f), sig(f)),
      Seq("-level2", s"i${i}_to_f${f}")
    )
  }
  "I32ToRecF16" should "pass" in {
    check(test(32, 16))
  }
  "I32ToRecF32" should "pass" in {
    check(test(32, 32))
  }
  "I32ToRecF64" should "pass" in {
    check(test(32, 64))
  }
  "I64ToRecF16" should "pass" in {
    check(test(64, 16))
  }
  "I64ToRecF32" should "pass" in {
    check(test(64, 32))
  }
  "I64ToRecF64" should "pass" in {
    check(test(64, 64))
  }
}

class MulAddRecFNSpec extends FMATester {
  def test(f: Int, fn: String): Seq[String] = {
    test(
      s"MulAddRecF${f}${fn match {
        case "add" => "_add"
        case "mul" => "_mul"
        case "mulAdd" => ""
      }}",
      () => fn match {
        case "add" => new ValExec_MulAddRecFN_add(exp(f), sig(f))
        case "mul" => new ValExec_MulAddRecFN_mul(exp(f), sig(f))
        case "mulAdd" => new ValExec_MulAddRecFN(exp(f), sig(f))
      },
      Seq(s"f${f}_${fn}")
    )
  }
  "MulAddRecF16" should "pass" in {
    check(test(16, "mulAdd"))
  }
  "MulAddRecF32" should "pass" in {
    check(test(32, "mulAdd"))
  }
  "MulAddRecF64" should "pass" in {
    check(test(64, "mulAdd"))
  }
  "MulAddRecF16_add" should "pass" in {
    check(test(16, "add"))
  }
  "MulAddRecF32_add" should "pass" in {
    check(test(32, "add"))
  }
  "MulAddRecF64_add" should "pass" in {
    check(test(64, "add"))
  }
  "MulAddRecF16_mul" should "pass" in {
    check(test(16, "mul"))
  }
  "MulAddRecF32_mul" should "pass" in {
    check(test(32, "mul"))
  }
  "MulAddRecF64_mul" should "pass" in {
    check(test(64, "mul"))
  }
}

class MulRecFNSpec extends FMATester {
  def test(f: Int): Seq[String] = {
    test(s"MulRecF${f}",
      () => new ValExec_MulRecFN(exp(f), sig(f)),
      Seq(s"f${f}_mul")
    )
  }
  "MulRecF16" should "pass" in {
    check(test(16))
  }
  "MulRecF32" should "pass" in {
    check(test(32))
  }
  "MulRecF64" should "pass" in {
    check(test(64))
  }
}

class RecFNToUINSpec extends FMATester {
  def test(f: Int, i: Int): Seq[String] = {
    val (softfloatArgs, dutArgs) = roundings.map { case (s, d) =>
      (s +: Seq("-exact", "-level2", s"f${f}_to_ui${i}"), Seq(d))
    }.unzip
    test(
      s"RecF${f}ToUI${i}",
      () => new ValExec_RecFNToUIN(exp(f), sig(f), i),
      "RecFNToUIN.cpp",
      softfloatArgs,
      Some(dutArgs)
    )
  }

  "RecF16ToUI32" should "pass" in {
    check(test(16, 32))
  }
  "RecF16ToUI64" should "pass" in {
    check(test(16, 64))
  }
  "RecF32ToUI32" should "pass" in {
    check(test(32, 32))
  }
  "RecF32ToUI64" should "pass" in {
    check(test(32, 64))
  }
  "RecF64ToUI32" should "pass" in {
    check(test(64, 32))
  }
  "RecF64ToUI64" should "pass" in {
    check(test(64, 64))
  }
}

class RecFNToINSpec extends FMATester {
  def test(f: Int, i: Int): Seq[String] = {
    val (softfloatArgs, dutArgs) = roundings.map { case (s, d) =>
      (s +: Seq("-exact", "-level2", s"f${f}_to_i${i}"), Seq(d))
    }.unzip
    test(
      s"RecF${f}ToI${i}",
      () => new ValExec_RecFNToIN(exp(f), sig(f), i),
      "RecFNToIN.cpp",
      softfloatArgs,
      Some(dutArgs)
    )
  }

  "RecF16ToI32" should "pass" in {
    check(test(16, 32))
  }
  "RecF16ToI64" should "pass" in {
    check(test(16, 64))
  }
  "RecF32ToI32" should "pass" in {
    check(test(32, 32))
  }
  "RecF32ToI64" should "pass" in {
    check(test(32, 64))
  }
  "RecF64ToI32" should "pass" in {
    check(test(64, 32))
  }
  "RecF64ToI64" should "pass" in {
    check(test(64, 64))
  }
}

class RecFNToRecFNSpec extends FMATester {
  def test(f0: Int, f1: Int): Seq[String] = {
    test(
      s"RecF${f0}ToRecF${f1}",
      () => new ValExec_RecFNToRecFN(exp(f0), sig(f0), exp(f1), sig(f1)),
      Seq("-level2", s"f${f0}_to_f${f1}")
    )
  }
  "RecF16ToRecF32" should "pass" in {
    check(test(16, 32))
  }
  "RecF16ToRecF64" should "pass" in {
    check(test(16, 64))
  }
  "RecF32ToRecF16" should "pass" in {
    check(test(32, 16))
  }
  "RecF32ToRecF64" should "pass" in {
    check(test(32, 64))
  }
  "RecF64ToRecF16" should "pass" in {
    check(test(64, 16))
  }
  "RecF64ToRecF32" should "pass" in {
    check(test(64, 32))
  }
}