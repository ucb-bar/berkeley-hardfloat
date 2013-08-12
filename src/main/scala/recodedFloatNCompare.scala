package hardfloat

import Chisel._
import Node._

class recodedFloatNCompare_io(SIG_WIDTH: Int, EXP_WIDTH: Int) extends Bundle {
  val a              = UInt(INPUT, SIG_WIDTH + EXP_WIDTH + 1);
  val b              = UInt(INPUT, SIG_WIDTH + EXP_WIDTH + 1);
  val a_eq_b         = Bool(OUTPUT);
  val a_lt_b         = Bool(OUTPUT);
  val a_eq_b_invalid = Bool(OUTPUT);
  val a_lt_b_invalid = Bool(OUTPUT);
}

class recodedFloatNCompare(SIG_WIDTH: Int, EXP_WIDTH: Int) extends Module {
  val io = new recodedFloat32Compare_io(SIG_WIDTH, EXP_WIDTH)

  val signA = io.a(SIG_WIDTH+EXP_WIDTH)
  val expA = io.a(SIG_WIDTH+EXP_WIDTH-1, SIG_WIDTH)
  val sigA = io.a(SIG_WIDTH-1,0)
  val codeA = expA(EXP_WIDTH-1, EXP_WIDTH-3)
  val isZeroA = !codeA.orR
  val isNaNA = codeA.andR
  val isSignalingNaNA = isNaNA && !sigA(SIG_WIDTH-1)

  val signB = io.b(SIG_WIDTH+EXP_WIDTH)
  val expB = io.b(SIG_WIDTH+EXP_WIDTH-1,SIG_WIDTH)
  val sigB = io.b(SIG_WIDTH-1,0)
  val codeB = expB(EXP_WIDTH-1, EXP_WIDTH-3)
  val isZeroB = !codeB.orR
  val isNaNB = codeB.andR
  val isSignalingNaNB = isNaNB && !sigB(SIG_WIDTH-1)

  val signEqual = signA === signB
  val expEqual = expA === expB
  val magEqual = expEqual && sigA === sigB
  val magLess = expA < expB || expEqual && sigA < sigB

  io.a_eq_b_invalid := isSignalingNaNA || isSignalingNaNB
  io.a_lt_b_invalid := isNaNA || isNaNB
  io.a_eq_b := !isNaNA && magEqual && (isZeroA || signEqual)
  io.a_lt_b := !io.a_lt_b_invalid &&
    Mux(signB, signA && !magLess && !magEqual, Mux(signA, !(isZeroA && isZeroB), magLess))
}
