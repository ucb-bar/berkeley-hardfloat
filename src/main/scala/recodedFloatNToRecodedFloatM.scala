// See LICENSE for license details.

package hardfloat

import Chisel._
import Chisel.ImplicitConversions._
import consts._

object recodedFloatNToRecodedFloatM_noncompliant
{
  def apply(in: UInt, inSigWidth: Int, inExpWidth: Int, outSigWidth: Int, outExpWidth: Int) = {
    val sign = in(inSigWidth + inExpWidth)

    val sig = if (outSigWidth > inSigWidth)
      Cat(in(inSigWidth-1,0), UInt(0, outSigWidth-inSigWidth))
    else
      in(outSigWidth-1,0)

    val expCode = in(inSigWidth + inExpWidth-1, inSigWidth+inExpWidth-3)
    val lsbs = in(inSigWidth + inExpWidth-2, inSigWidth)
    val exp = if (outExpWidth > inExpWidth) {
      Mux(expCode < UInt(1), lsbs,
      Mux(expCode < UInt(4), Cat(UInt((1 << (outExpWidth-inExpWidth))-1), lsbs),
      Mux(expCode < UInt(6), Cat(UInt(1 << (outExpWidth-inExpWidth)), lsbs),
      Mux(expCode < UInt(7), UInt(3 << (outExpWidth-2)),
                             UInt(7 << (outExpWidth-3))))))
    } else {
      Mux(expCode < UInt(6), Cat(expCode(2), lsbs(outExpWidth-2,0)), Cat(expCode, lsbs(outExpWidth-4,0)))
    }
    Cat(sign, exp, sig)
  }
}

object recodedFloatNToRecodedFloatM
{
  def apply(in: UInt, roundingMode: UInt, inSigWidth: Int, inExpWidth: Int, outSigWidth: Int, outExpWidth: Int) = {
    val sign = in(inSigWidth+inExpWidth)
    val sigIn = in(inSigWidth-1,0)
    val expIn = in(inSigWidth + inExpWidth-1, inSigWidth)
    val expCode = in(inSigWidth + inExpWidth-1, inSigWidth+inExpWidth-3)
    val expLSBs = in(inSigWidth + inExpWidth-2, inSigWidth)
    val isNaN = expCode.andR
    val isSignalingNaN = isNaN && !sigIn(inSigWidth-1)

    if (inSigWidth > outSigWidth) {
      require (inExpWidth > outExpWidth)
      val maxExp = UInt((1 << inExpWidth-1) + (1 << outExpWidth-2)-1)
      val minExp = UInt((1 << inExpWidth-1) - ((1 << outExpWidth-2)-1) - outSigWidth)
      val maxSubnormalExp = UInt((1 << inExpWidth-1) - ((1 << outExpWidth-2)-1))
      val minSubnormalExp = minExp

      val isSpecial = !expCode.orR || expCode(2,1).andR
      val isSubnormal = expIn >= minSubnormalExp && expIn <= maxSubnormalExp
      val isUnderflow = expIn < minExp && !isSpecial
      val isOverflow = expIn > maxExp && !isSpecial

      val round_position = Mux(isSubnormal, maxSubnormalExp + UInt(1) - expIn, UInt(0))(log2Up(outSigWidth+1)-1,0)
      val sigMSBsShifted = Cat(UInt(1), sigIn(inSigWidth-1,inSigWidth-outSigWidth-1), UInt(0, outSigWidth+1)) >> round_position
      val subNormStickyBit = sigMSBsShifted(outSigWidth,0) != UInt(0) || sigIn(inSigWidth-outSigWidth-2,0) != UInt(0)
      val roundBits = Cat(sigMSBsShifted(outSigWidth+2,outSigWidth+1), subNormStickyBit)

      val roundInexact = roundBits(1,0) != UInt(0) && !isSpecial
      val round =
        Mux(roundingMode === round_nearest_even, roundBits(1,0).andR || roundBits(2,1).andR,
        Mux(roundingMode === round_min, sign && roundInexact,
        Mux(roundingMode === round_max, !sign && roundInexact,
         /* roundingMode === round_minMag */ Bool(false))))
      val roundMaskShifted   = (Fill(outSigWidth+2,UInt(1)) << round_position.toUInt)(outSigWidth+1,0)
      val sigUnrounded = Cat(UInt(1, 2), sigIn(inSigWidth-1,inSigWidth-outSigWidth)) | ~roundMaskShifted
      val sigRounded = Mux(round, sigUnrounded + UInt(1), sigUnrounded) & roundMaskShifted
      val expUnrounded = expIn(outExpWidth-1,0) + UInt(1 << outExpWidth-1)
      val expRounded = Mux(sigRounded(outSigWidth+1), expUnrounded + UInt(1), expUnrounded)

      val underflow_to_small = roundingMode === round_min && sign || roundingMode === round_max && !sign
      val overflow_to_inf    = underflow_to_small || roundingMode === round_nearest_even

      val sigOverflow = Fill(outSigWidth, !overflow_to_inf)
      val expOverflow = Mux(overflow_to_inf, UInt(3 << outExpWidth-2), UInt((3 << outExpWidth-2)-1))
      val sigUnderflow = UInt(0)
      val expUnderflow = Mux(underflow_to_small, UInt((1 << outExpWidth-2)-outSigWidth+2), UInt(0))

      val expOut =
            Mux(isSpecial, expCode << (outExpWidth-3),
            Mux(isOverflow, expOverflow,
            Mux(isUnderflow, expUnderflow,
            expRounded)))
      val sigOut =
            Mux(isSpecial, Fill(outSigWidth, isNaN),
            Mux(isOverflow, sigOverflow,
            Mux(isUnderflow, sigUnderflow,
            sigRounded(outSigWidth-1,0))))
      val out = Cat(sign, expOut, sigOut)

      val flagUnderflow  = isUnderflow || isSubnormal && roundInexact
      val flagOverflow = isOverflow || expIn === maxExp && sigRounded(outSigWidth+1)
      val exc = Cat(isSignalingNaN, Bool(false), flagOverflow, flagUnderflow, roundInexact || isOverflow || isUnderflow)
      (out, exc)
    } else {
      require (inExpWidth <= outExpWidth)
      val expOut =
      Mux(expCode < UInt(1), expLSBs,
        Mux(expCode < UInt(4), Cat(UInt((1 << (outExpWidth-inExpWidth))-1), expLSBs),
        Mux(expCode < UInt(6), Cat(UInt(1 << (outExpWidth-inExpWidth)), expLSBs),
        Mux(expCode < UInt(7), UInt(3 << (outExpWidth-2)),
                               UInt(7 << (outExpWidth-3))))))
      val sigOut = Fill(outSigWidth, isNaN) | sigIn << (outSigWidth-inSigWidth)
      val out = Cat(sign, expOut, sigOut)
      (out, isSignalingNaN << 4)
    }
  }
}
