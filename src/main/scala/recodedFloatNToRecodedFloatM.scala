package hardfloat

import Chisel._
import Node._
import fpu_recoded._

object recodedFloatNToRecodedFloatM_noncompliant
{
  def apply(in: Bits, inSigWidth: Int, inExpWidth: Int, outSigWidth: Int, outExpWidth: Int) = {
    val sign = in(inSigWidth + inExpWidth)

    val sig = if (outSigWidth > inSigWidth)
      Cat(in(inSigWidth-1,0), Bits(0, outSigWidth-inSigWidth))
    else
      in(outSigWidth-1,0)

    val expCode = in(inSigWidth + inExpWidth-1, inSigWidth+inExpWidth-3)
    val lsbs = in(inSigWidth + inExpWidth-2, inSigWidth)
    val exp = if (outExpWidth > inExpWidth) {
      Mux(expCode < Bits(1), lsbs,
      Mux(expCode < Bits(4), Cat(Bits((1 << (outExpWidth-inExpWidth))-1), lsbs),
      Mux(expCode < Bits(6), Cat(Bits(1 << (outExpWidth-inExpWidth)), lsbs),
      Mux(expCode < Bits(7), Bits(3 << (outExpWidth-2)),
                             Bits(7 << (outExpWidth-3))))))
    } else {
      Mux(expCode < Bits(6), Cat(expCode(2), lsbs(outExpWidth-2,0)), Cat(expCode, lsbs(outExpWidth-4,0)))
    }
    Cat(sign, exp, sig)
  }
}

object recodedFloatNToRecodedFloatM
{
  def apply(in: Bits, roundingMode: Bits, inSigWidth: Int, inExpWidth: Int, outSigWidth: Int, outExpWidth: Int) = {
    val sign = in(inSigWidth+inExpWidth)
    val sigIn = in(inSigWidth-1,0)
    val expIn = in(inSigWidth + inExpWidth-1, inSigWidth)
    val expCode = in(inSigWidth + inExpWidth-1, inSigWidth+inExpWidth-3)
    val expLSBs = in(inSigWidth + inExpWidth-2, inSigWidth)
    val isSignalingNaN = expCode.andR && !sigIn(inSigWidth-1)

    if (inSigWidth > outSigWidth) {
      require (inExpWidth > outExpWidth)
      val maxExp = UFix((1 << inExpWidth-1) + (1 << outExpWidth-2)-1)
      val minExp = UFix((1 << inExpWidth-1) - ((1 << outExpWidth-2)-1) - outSigWidth)
      val maxSubnormalExp = UFix((1 << inExpWidth-1) - ((1 << outExpWidth-2)-1))
      val minSubnormalExp = minExp

      val isSpecial = !expCode.orR || expCode(2,1).andR
      val isSubnormal = expIn >= minSubnormalExp && expIn <= maxSubnormalExp
      val isUnderflow = expIn < minExp && !isSpecial
      val isOverflow = expIn > maxExp && !isSpecial

      val round_position = Mux(isSubnormal, maxSubnormalExp + UFix(1) - expIn, UFix(0))(log2up(outSigWidth+1)-1,0)
      val sigMSBsShifted = Cat(Bits(1), sigIn(inSigWidth-1,inSigWidth-outSigWidth-1), Bits(0, outSigWidth+1)) >> round_position
      val subNormStickyBit = sigMSBsShifted(outSigWidth,0) != UFix(0) || sigIn(inSigWidth-outSigWidth-2,0) != UFix(0)
      val roundBits = Cat(sigMSBsShifted(outSigWidth+2,outSigWidth+1), subNormStickyBit)

      val roundInexact = roundBits(1,0) != UFix(0) && !isSpecial
      val round =
        Mux(roundingMode === round_nearest_even, roundBits(1,0).andR || roundBits(2,1).andR,
        Mux(roundingMode === round_min, sign && roundInexact,
        Mux(roundingMode === round_max, !sign && roundInexact,
         /* roundingMode === round_minMag */ Bool(false))))
      val roundMaskShifted   = (Fill(outSigWidth+2,Bits(1)) << round_position.toUFix)(outSigWidth+1,0)
      val sigUnrounded = Cat(Bits(1, 2), sigIn(inSigWidth-1,inSigWidth-outSigWidth)) | ~roundMaskShifted
      val sigRounded = Mux(round, sigUnrounded + UFix(1), sigUnrounded)
      val expUnrounded = expIn(outExpWidth-1,0) + UFix(1 << outExpWidth-1)
      val expRounded = Mux(sigRounded(outSigWidth+1), expUnrounded + UFix(1), expUnrounded)

      val underflow_to_small = roundingMode === round_min && sign || roundingMode === round_max && !sign
      val overflow_to_inf    = underflow_to_small || roundingMode === round_nearest_even

      val sigOverflow = Fill(outSigWidth, !overflow_to_inf)
      val expOverflow = Mux(overflow_to_inf, UFix(3 << outExpWidth-2), UFix((3 << outExpWidth-2)-1))
      val sigUnderflow = UFix(0)
      val expUnderflow = Mux(underflow_to_small, UFix((1 << outExpWidth-2)-outSigWidth+2), UFix(0))

      val expOut =
            Mux(isSpecial, expCode << UFix(outExpWidth-3),
            Mux(isOverflow, expOverflow,
            Mux(isUnderflow, expUnderflow,
            expRounded)))
      val sigOut =
            Mux(isSpecial, isSignalingNaN << UFix(outSigWidth-1) | sigIn(inSigWidth-1,inSigWidth-outSigWidth),
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
      Mux(expCode < Bits(1), expLSBs,
        Mux(expCode < Bits(4), Cat(Bits((1 << (outExpWidth-inExpWidth))-1), expLSBs),
        Mux(expCode < Bits(6), Cat(Bits(1 << (outExpWidth-inExpWidth)), expLSBs),
        Mux(expCode < Bits(7), Bits(3 << (outExpWidth-2)),
                               Bits(7 << (outExpWidth-3))))))
      val sigOut = isSignalingNaN << UFix(outSigWidth-1) | sigIn << UFix(outSigWidth-inSigWidth)
      val out = Cat(sign, expOut, sigOut)
      (out, isSignalingNaN << UFix(4))
    }
  }
}
