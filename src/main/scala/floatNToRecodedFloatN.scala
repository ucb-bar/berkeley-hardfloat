package hardfloat

import Chisel._
import Node._

object floatNToRecodedFloatN
{
  def apply(in: Bits, sigWidth: Int, expWidth: Int) = {
    val sign = in(sigWidth+expWidth-1)
    val expIn = in(sigWidth+expWidth-2, sigWidth)
    val fractIn = in(sigWidth-1, 0)
    val normWidth = 1 << log2up(sigWidth)

    val isZeroExpIn = expIn === UFix(0)
    val isZeroFractIn = fractIn === UFix(0)
    val isZero = isZeroExpIn && isZeroFractIn
    val isSubnormal = isZeroExpIn && !isZeroFractIn

    val (norm, normCount) = Normalize(fractIn << UFix(normWidth-sigWidth))
    val subnormal_expOut = Cat(Fill(expWidth-log2up(sigWidth), Bool(true)), ~normCount)

    val normalizedFract = norm(normWidth-2, normWidth-sigWidth-1)
    val commonExp = Mux(isZeroExpIn, Mux(isZeroFractIn, Bits(0), subnormal_expOut), expIn)
    val expAdjust = Mux(isZero, Bits(0), Bits(1 << expWidth-2) | Mux(isSubnormal, UFix(2), UFix(1)))
    val adjustedCommonExp = commonExp + expAdjust
    val isNaN = adjustedCommonExp(expWidth-1,expWidth-2).andR && !isZeroFractIn

    val expOut = adjustedCommonExp | (isNaN << UFix(expWidth-3))
    val fractOut = Mux(isZeroExpIn, normalizedFract, fractIn)

    Cat(sign, expOut, fractOut)
  }
}
