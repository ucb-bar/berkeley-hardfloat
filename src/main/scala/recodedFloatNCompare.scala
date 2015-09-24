// See LICENSE for license details.

package hardfloat

import Chisel._

class recodedFloatNCompare_io(SIG_WIDTH: Int, EXP_WIDTH: Int) extends Bundle {
  val a              = UInt(INPUT, SIG_WIDTH + EXP_WIDTH + 1);
  val b              = UInt(INPUT, SIG_WIDTH + EXP_WIDTH + 1);
  val a_eq_b         = Bool(OUTPUT);
  val a_lt_b         = Bool(OUTPUT);
  val a_eq_b_invalid = Bool(OUTPUT);
  val a_lt_b_invalid = Bool(OUTPUT);
}

class recodedFloatNCompare(SIG_WIDTH: Int, EXP_WIDTH: Int) extends Module {
  val io = new recodedFloatNCompare_io(SIG_WIDTH, EXP_WIDTH)

  val signA = io.a(SIG_WIDTH+EXP_WIDTH)
  val magA = io.a(SIG_WIDTH+EXP_WIDTH-1, 0)
  val codeA = io.a(SIG_WIDTH+EXP_WIDTH-1, SIG_WIDTH+EXP_WIDTH-3)
  val isZeroA = codeA === UInt(0)
  val isInfA = codeA === UInt(6)
  val isNaNA = codeA === UInt(7)
  val isSignalingNaNA = isNaNA && !io.a(SIG_WIDTH-1)

  val signB = io.b(SIG_WIDTH+EXP_WIDTH)
  val magB = io.b(SIG_WIDTH+EXP_WIDTH-1, 0)
  val codeB = io.b(SIG_WIDTH+EXP_WIDTH-1, SIG_WIDTH+EXP_WIDTH-3)
  val isZeroB = codeB === UInt(0)
  val isInfB = codeB === UInt(6)
  val isNaNB = codeB === UInt(7)
  val isSignalingNaNB = isNaNB && !io.b(SIG_WIDTH-1)

  val signEqual = signA === signB
  val magEqual = magA === magB || (isInfA && isInfB) || (isZeroA && isZeroB)
  val magLess = magA < magB && !isInfA && !isZeroB

  io.a_eq_b_invalid := isSignalingNaNA || isSignalingNaNB
  io.a_lt_b_invalid := isNaNA || isNaNB
  io.a_eq_b := !isNaNA && magEqual && (isZeroA || signEqual)
  io.a_lt_b := !io.a_lt_b_invalid &&
    Mux(signB, signA && !(magLess || magEqual), Mux(signA, !(isZeroA && isZeroB), magLess))
}
