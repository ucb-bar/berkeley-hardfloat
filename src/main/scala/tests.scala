// See LICENSE for license details.

package hardfloat

import Chisel._
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

class f64_div_io extends Bundle {
  val a = Bits(width = 64)
  val b = Bits(width = 64)
  val rm = Bits(width = 2)
  val out = Bits(width = 64)
  val exception = Bits(width = 5)
}

class Test_f64_div extends Module {
  val io = new Bundle {
    val input = Decoupled(new f64_div_io).flip

    val output = new Bundle {
      val a = Bits(OUTPUT, 64)
      val b = Bits(OUTPUT, 64)
      val rm = Bits(OUTPUT, 2)
    }

    val expected = new Bundle {
      val ieee = Bits(OUTPUT, 64)
      val exception = Bits(OUTPUT, 5)
      val recoded = Bits(OUTPUT, 65)
    }

    val actual = new Bundle {
      val ieee = Bits(OUTPUT, 64)
      val exception = Bits(OUTPUT, 5)
      val recoded = Bits(OUTPUT, 65)
    }

    val check = Bool(OUTPUT)
    val pass = Bool(OUTPUT)
  }

  val ds = Module(new divSqrtRecodedFloat64)
  val cq = Module(new Queue(new f64_div_io, 5))

  cq.io.enq.valid := io.input.valid && ds.io.inReady_div
  cq.io.enq.bits := io.input.bits

  io.input.ready := ds.io.inReady_div && cq.io.enq.ready
  ds.io.inValid := io.input.valid && cq.io.enq.ready
  ds.io.sqrtOp := Bool(false)
  ds.io.a := floatNToRecodedFloatN(io.input.bits.a, 52, 12)
  ds.io.b := floatNToRecodedFloatN(io.input.bits.b, 52, 12)
  ds.io.roundingMode := io.input.bits.rm

  io.output.a := cq.io.deq.bits.a
  io.output.b := cq.io.deq.bits.b
  io.output.rm := cq.io.deq.bits.rm

  io.expected.ieee := cq.io.deq.bits.out
  io.expected.exception := cq.io.deq.bits.exception
  io.expected.recoded := floatNToRecodedFloatN(cq.io.deq.bits.out, 52, 12)

  io.actual.ieee := recodedFloatNToFloatN(ds.io.out, 52, 12)
  io.actual.exception := ds.io.exceptionFlags
  io.actual.recoded := ds.io.out

  cq.io.deq.ready := ds.io.outValid_div

  io.check := ds.io.outValid_div
  io.pass :=
    cq.io.deq.valid &&
    io.expected.ieee === io.actual.ieee &&
    io.expected.exception === io.actual.exception
}

class f64_sqrt_io extends Bundle {
  val b = Bits(width = 64)
  val rm = Bits(width = 2)
  val out = Bits(width = 64)
  val exception = Bits(width = 5)
}

class Test_f64_sqrt extends Module {
  val io = new Bundle {
    val input = Decoupled(new f64_sqrt_io).flip

    val output = new Bundle {
      val b = Bits(OUTPUT, 64)
      val rm = Bits(OUTPUT, 2)
    }

    val expected = new Bundle {
      val ieee = Bits(OUTPUT, 64)
      val exception = Bits(OUTPUT, 5)
      val recoded = Bits(OUTPUT, 65)
    }

    val actual = new Bundle {
      val ieee = Bits(OUTPUT, 64)
      val exception = Bits(OUTPUT, 5)
      val recoded = Bits(OUTPUT, 65)
    }

    val check = Bool(OUTPUT)
    val pass = Bool(OUTPUT)
  }

  val ds = Module(new divSqrtRecodedFloat64)
  val cq = Module(new Queue(new f64_sqrt_io, 5))

  cq.io.enq.valid := io.input.valid && ds.io.inReady_sqrt
  cq.io.enq.bits := io.input.bits

  io.input.ready := ds.io.inReady_sqrt && cq.io.enq.ready
  ds.io.inValid := io.input.valid && cq.io.enq.ready
  ds.io.sqrtOp := Bool(true)
  ds.io.b := floatNToRecodedFloatN(io.input.bits.b, 52, 12)
  ds.io.roundingMode := io.input.bits.rm

  io.output.b := cq.io.deq.bits.b
  io.output.rm := cq.io.deq.bits.rm

  io.expected.ieee := cq.io.deq.bits.out
  io.expected.exception := cq.io.deq.bits.exception
  io.expected.recoded := floatNToRecodedFloatN(cq.io.deq.bits.out, 52, 12)

  io.actual.ieee := recodedFloatNToFloatN(ds.io.out, 52, 12)
  io.actual.exception := ds.io.exceptionFlags
  io.actual.recoded := ds.io.out

  cq.io.deq.ready := ds.io.outValid_sqrt

  io.check := ds.io.outValid_sqrt
  io.pass :=
    cq.io.deq.valid &&
    io.expected.ieee === io.actual.ieee &&
    io.expected.exception === io.actual.exception
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
      case "f32_mulAdd" =>
        chiselMain(testArgs, () => Module(new Test_f32_mulAdd))
      case "f64_mulAdd" =>
        chiselMain(testArgs, () => Module(new Test_f64_mulAdd))
      case "f64_div" =>
        chiselMain(testArgs, () => Module(new Test_f64_div))
      case "f64_sqrt" =>
        chiselMain(testArgs, () => Module(new Test_f64_sqrt))
    }
  }
}
