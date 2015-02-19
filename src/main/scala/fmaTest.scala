// See LICENSE for license details.

package hardfloat

import Chisel._
import Node._
import util.Random
import scala.sys.process._

class FMA(val sigWidth: Int, val expWidth: Int) extends Module {
  val io = new Bundle {
    val a = Bits(INPUT, sigWidth + expWidth)
    val b = Bits(INPUT, sigWidth + expWidth)
    val c = Bits(INPUT, sigWidth + expWidth)
    val rm = Bits(INPUT, 2)
    val correct_out = Bits(INPUT, sigWidth + expWidth)
    val correct_exception = Bits(INPUT, 5)

    val recoded_correct_out = Bits(OUTPUT, sigWidth + expWidth + 1)
    val recoded_out = Bits(OUTPUT, sigWidth + expWidth + 1)
    val ieee_out = Bits(OUTPUT, sigWidth + expWidth)
    val exception = Bits(OUTPUT, 5)
    val pass = Bool(OUTPUT)
  }

  val fma = Module(new mulAddSubRecodedFloatN(sigWidth, expWidth))
  fma.io.op := UInt(0)
  fma.io.a := floatNToRecodedFloatN(io.a, sigWidth, expWidth)
  fma.io.b := floatNToRecodedFloatN(io.b, sigWidth, expWidth)
  fma.io.c := floatNToRecodedFloatN(io.c, sigWidth, expWidth)
  fma.io.roundingMode := io.rm

  io.recoded_correct_out := floatNToRecodedFloatN(io.correct_out, sigWidth, expWidth)
  io.recoded_out := fma.io.out
  io.ieee_out := recodedFloatNToFloatN(fma.io.out, sigWidth, expWidth)
  io.exception := fma.io.exceptionFlags
  io.pass :=
    io.correct_out === io.ieee_out &&
    io.correct_exception === io.exception
}

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
      case "sp-fma" =>
        chiselMain(testArgs, () => Module(new FMA(23, 9)))
    }
  }
}
