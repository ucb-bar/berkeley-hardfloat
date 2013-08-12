//
// recodedFloat64ToAny( in, out );
// Author: Brian Richards, 10/21/2010
//

package hardfloat

import Chisel._
import Node._
import scala.math._
import fpu_recoded._

object recodedFloatNToAny {
  def apply(in: UInt, roundingMode: UInt, typeOp: UInt, sigWidth: Int, expWidth: Int, intWidth: Int) = {
    val io = new recodedFloat64ToAny_io(sigWidth, expWidth, intWidth)
  
    val sign = in(sigWidth+expWidth)
    val exponent = in(sigWidth+expWidth-1,sigWidth)
    val sig = in(sigWidth-1,0)
  
    val isTiny      = !exponent(expWidth-2,0).andR
    val isZeroOrOne = !exponent(expWidth-1)
    val isZero      = exponent(expWidth-1,expWidth-3) === UInt(0)
    val isSpecial   = exponent(expWidth-1,expWidth-2).andR
  
    val roundDist = Mux(isZeroOrOne, UInt(0), exponent(min(expWidth-1, log2Up(intWidth))-1,0))
    val shiftedSig = Cat(!isZeroOrOne, sig) << roundDist
    val unrounded = shiftedSig(min(1 << expWidth-1, intWidth)+sigWidth-1, sigWidth)
    val roundBits = Cat(shiftedSig(sigWidth, sigWidth-1), shiftedSig(sigWidth-2, 0).orR)
    val roundNearest = Mux(isZeroOrOne, !isTiny && roundBits(1,0).orR, roundBits(2,1).andR || roundBits(1,0).andR)
    val nonzeroSig = Mux(isZeroOrOne, !isZero, roundBits(1,0).orR)
    val round =
      Mux(roundingMode === round_nearest_even, roundNearest,
      Mux(roundingMode === round_min, sign && nonzeroSig,
      Mux(roundingMode === round_max, !sign && nonzeroSig,
       /* roundingMode === round_minMag */ Bool(false))))
    val onescomp = Mux(sign, ~unrounded, unrounded)
    var rounded = Mux(round ^ sign, onescomp + UInt(1), onescomp)
    if (intWidth > rounded.getWidth)
      rounded = Cat(Fill(intWidth-rounded.getWidth, rounded(rounded.getWidth-1)), rounded)
    
    val roundCarry = round && unrounded.andR
    val signedOverflowCarry = !sign && roundCarry
    val signedOverflow = !sign || round || unrounded.orR
    val posExponent = exponent(expWidth-2,0)
    def signedInvalid(intWidth: Int) = Mux(isZeroOrOne, Bool(false),
      Mux(posExponent === UInt(intWidth-2), signedOverflowCarry,
      Mux(posExponent === UInt(intWidth-1), signedOverflow,
      posExponent >= UInt(intWidth))))
    def unsignedInvalid(intWidth: Int) = Mux(isZeroOrOne, sign && round,
      sign || Mux(posExponent === UInt(intWidth-1), roundCarry, posExponent >= UInt(intWidth)))
  
    val invalid = isSpecial ||
      Mux(typeOp === type_uint32, unsignedInvalid(intWidth/2),
      Mux(typeOp === type_int32, signedInvalid(intWidth/2),
      Mux(typeOp === type_uint64, unsignedInvalid(intWidth),
       /* typeOp === type_int64 */ signedInvalid(intWidth))))
    
    val out = Mux(invalid, ~UInt(0, intWidth), rounded)
    val exc = Cat(invalid, UInt(0,4))
    (out, exc)
  }
}
