// See LICENSE for license details.

package hardfloat

import Chisel._
import Node._
import util.Random
import scala.sys.process._

class TestMulAdd(val sigWidth: Int, val expWidth: Int) extends Module {
  val io = new Bundle {
    val a = Bits(INPUT, sigWidth + expWidth)
    val b = Bits(INPUT, sigWidth + expWidth)
    val c = Bits(INPUT, sigWidth + expWidth)
    val rm = Bits(INPUT, 2)

    val expected = new Bundle {
      val ieee = Bits(INPUT, sigWidth + expWidth)
      val exception = Bits(INPUT, 5)
      val recoded = Bits(OUTPUT, sigWidth + expWidth + 1)
    }

    val actual = new Bundle {
      val ieee = Bits(OUTPUT, sigWidth + expWidth)
      val exception = Bits(OUTPUT, 5)
      val recoded = Bits(OUTPUT, sigWidth + expWidth + 1)
    }

    val check = Bool(OUTPUT)
    val pass = Bool(OUTPUT)
  }

  val fma = Module(new mulAddSubRecodedFloatN(sigWidth, expWidth))
  fma.io.op := UInt(0)
  fma.io.a := floatNToRecodedFloatN(io.a, sigWidth, expWidth)
  fma.io.b := floatNToRecodedFloatN(io.b, sigWidth, expWidth)
  fma.io.c := floatNToRecodedFloatN(io.c, sigWidth, expWidth)
  fma.io.roundingMode := io.rm

  io.expected.recoded := floatNToRecodedFloatN(io.expected.ieee, sigWidth, expWidth)

  io.actual.ieee := recodedFloatNToFloatN(fma.io.out, sigWidth, expWidth)
  io.actual.exception := fma.io.exceptionFlags
  io.actual.recoded := fma.io.out

  io.check := Bool(true)
  io.pass :=
    io.expected.ieee === io.actual.ieee &&
    io.expected.exception === io.actual.exception
}

class Test_f32_mulAdd extends TestMulAdd(23, 9)
class Test_f64_mulAdd extends TestMulAdd(52, 12)

class FMAPipeline(sigWidth: Int, expWidth: Int) extends Module {
  val io = new Bundle {
    val req = Bool(INPUT)
    val resp = Bool(OUTPUT)
    val a = Bits(INPUT, sigWidth + expWidth + 1)
    val b = Bits(INPUT, sigWidth + expWidth + 1)
    val c = Bits(INPUT, sigWidth + expWidth + 1)
    val out = Bits(OUTPUT, sigWidth + expWidth + 1 + 5)
  }
  val fma = Module(new mulAddSubRecodedFloatN(sigWidth, expWidth))
  fma.io.a := io.a
  fma.io.b := io.b
  fma.io.c := io.c
  fma.io.op := Bits(0)
  fma.io.roundingMode := io.a
  io.out := RegEnable(Cat(fma.io.exceptionFlags, fma.io.out), io.req)
  io.resp := Reg(next=io.req)
}

class FMARecoded(val sigWidth: Int, val expWidth: Int)  extends Module {
  val io = new Bundle {
    val a = Bits(INPUT, sigWidth + expWidth + 1)
    val b = Bits(INPUT, sigWidth + expWidth + 1)
    val c = Bits(INPUT, sigWidth + expWidth + 1)
    val out = Bits(OUTPUT, sigWidth + expWidth + 1 + 5)
  }

  val fma = Module(new FMAPipeline(sigWidth, expWidth))
  val en = io.a.orR
  fma.io.req := Reg(next=en, init=Bool(false))
  fma.io.a := RegEnable(io.a, en)
  fma.io.b := RegEnable(io.b, en)
  fma.io.c := RegEnable(io.c, en)
  io.out := RegEnable(fma.io.out, fma.io.resp)
}

class SFMARecoded extends FMARecoded(23, 9)
class DFMARecoded extends FMARecoded(52, 12)

class RAM(val w: Int, val d: Int, val nr: Int) extends Module {
  val io = new Bundle {
    val wa = Bits(INPUT, log2Up(d))
    val we = Bool(INPUT)
    val wd = Bits(INPUT, w)

    val ra = Vec.fill(nr)(Bits(INPUT, log2Up(d)))
    val re = Vec.fill(nr)(Bool(INPUT))
    val rd = Vec.fill(nr)(Bits(OUTPUT, w))
  }

  val ram = Mem(Bits(width = w), d)
  when (io.we) { ram(io.wa) := io.wd }

  for (i <- 0 until nr) {
    val ra = RegEnable(io.ra(i), io.re(i))
    val re = Reg(next=io.re(i), init=Bool(false))
    io.rd(i) := RegEnable(ram(ra), re)
  }
}

object FMATest {
  def main(args: Array[String]): Unit = {
    val testArgs = args.slice(1, args.length)
    args(0) match {
      case "f32_mulAdd" =>
        chiselMain(testArgs, () => Module(new Test_f32_mulAdd))
      case "f64_mulAdd" =>
        chiselMain(testArgs, () => Module(new Test_f64_mulAdd))
    }
  }
}
