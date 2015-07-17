// See LICENSE for license details.

package hardfloat

import Chisel._

object recodedFloatNToFloatN
{
  def apply(in: UInt, sigWidth: Int, expWidth: Int) = {
    val sign = in(sigWidth+expWidth)
    val expIn = in(sigWidth+expWidth-1, sigWidth)
    val fractIn = in(sigWidth-1, 0)

    val isHighSubnormalIn = expIn(expWidth-3, 0) < UInt(2)
    val isSubnormal = expIn(expWidth-1, expWidth-3) === UInt(1) || expIn(expWidth-1, expWidth-2) === UInt(1) && isHighSubnormalIn
    val isNormal = expIn(expWidth-1, expWidth-2) === UInt(1) && !isHighSubnormalIn || expIn(expWidth-1, expWidth-2) === UInt(2)
    val isSpecial = expIn(expWidth-1, expWidth-2) === UInt(3)
    val isNaN = isSpecial && expIn(expWidth-3)

    val denormShiftDist = UInt(2) - expIn(log2Up(sigWidth)-1, 0)
    val subnormal_fractOut = (Cat(Bool(true), fractIn) >> denormShiftDist)(sigWidth-1, 0)
    val normal_expOut = expIn(expWidth-2, 0) - UInt((1 << (expWidth-2))+1)

    val expOut = Mux(isNormal, normal_expOut, Fill(expWidth-1, isSpecial))
    val fractOut = Mux(isNormal || isNaN, fractIn, Mux(isSubnormal, subnormal_fractOut, UInt(0)))

    Cat(sign, expOut, fractOut)
  }
}
