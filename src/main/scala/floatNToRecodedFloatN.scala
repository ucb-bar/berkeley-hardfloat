// See LICENSE for license details.

package hardfloat

import Chisel._
import Chisel.ImplicitConversions._

object floatNToRecodedFloatN
{
  def apply(in: UInt, sigWidth: Int, expWidth: Int) = {
    val sign = in(sigWidth+expWidth-1)
    val expIn = in(sigWidth+expWidth-2, sigWidth)
    val fractIn = in(sigWidth-1, 0)
    val normWidth = 1 << log2Up(sigWidth)

    val isZeroExpIn = expIn === UInt(0)
    val isZeroFractIn = fractIn === UInt(0)
    val isZero = isZeroExpIn && isZeroFractIn
    val isSubnormal = isZeroExpIn && !isZeroFractIn

    val (norm, normCount) = Normalize(fractIn << (normWidth-sigWidth))
    val subnormal_expOut = Cat(Fill(expWidth-log2Up(sigWidth), Bool(true)), ~normCount)

    val normalizedFract = norm(normWidth-2, normWidth-sigWidth-1)
    val commonExp = Mux(isZeroExpIn, Mux(isZeroFractIn, UInt(0), subnormal_expOut), expIn)
    val expAdjust = Mux(isZero, UInt(0), UInt(1 << expWidth-2) | Mux(isSubnormal, UInt(2), UInt(1)))
    val adjustedCommonExp = commonExp + expAdjust
    val isNaN = adjustedCommonExp(expWidth-1,expWidth-2).andR && !isZeroFractIn

    val expOut = adjustedCommonExp | (isNaN << (expWidth-3))
    val fractOut = Mux(isZeroExpIn, normalizedFract, fractIn)

    Cat(sign, expOut, fractOut)
  }
}
