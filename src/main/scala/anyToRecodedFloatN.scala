// See LICENSE for license details.

//
// anyToRecodedFloat32( in, out );
// Author: Brian Richards, 5/17/2011
// Based on float32ToRecodedFloat32 from John Hauser
//

package hardfloat

import Chisel._
import consts._

object anyToRecodedFloatN
{
  def apply(in: UInt, roundingMode: UInt, typeOp: UInt, sigWidth: Int, expWidth: Int, intWidth: Int) = {
    val sign =
      Mux(typeOp === type_int32, in(intWidth/2-1),
      Mux(typeOp === type_int64, in(intWidth-1),
      Bool(false)))
    val abs = Mux(sign, -in, in)
    val norm_in = Mux(typeOp === type_int64 || typeOp === type_uint64, abs, abs(intWidth/2-1,0))
    val (norm_out, norm_count) = Normalize(norm_in)
    
    var roundBits = UInt(0, 3)
    if (intWidth-sigWidth-1 >= 2)
      roundBits = Cat(norm_out(intWidth-sigWidth-1, intWidth-sigWidth-2), norm_out(intWidth-sigWidth-3,0).orR)
    else if(intWidth-sigWidth-1 == 1)
      roundBits = Cat(norm_out(intWidth-sigWidth-1, intWidth-sigWidth-2), Bool(false))
    
    val roundInexact = roundBits(1,0).orR
    val round = 
      Mux(roundingMode === round_nearest_even, roundBits(2,1).andR || roundBits(1,0).andR,
      Mux(roundingMode === round_min, sign && roundInexact,
      Mux(roundingMode === round_max, !sign && roundInexact,
       /* roundingMode === round_minMag */ Bool(false))))
    
    var norm_unrounded = norm_out
    if (intWidth-sigWidth-1 >= 0)
      norm_unrounded = norm_unrounded(intWidth-1, intWidth-sigWidth-1)
    else
      norm_unrounded = Cat(norm_unrounded, UInt(0, sigWidth-intWidth+1))
    norm_unrounded = Cat(UInt(0, 1), norm_unrounded)
    val norm_rounded = Mux(round, norm_unrounded + UInt(1), norm_unrounded)
    
    var overflow_unrounded = Bool(false)
    var overflow_rounded = Bool(false)
    var exp_unrounded = ~norm_count
    if (log2Up(intWidth) > expWidth-2) {
      overflow_unrounded = exp_unrounded >= UInt(1 << (expWidth-2))
      exp_unrounded = exp_unrounded(expWidth-3,0)
    } else if (log2Up(intWidth) < expWidth-2)
      exp_unrounded = Cat(UInt(0,expWidth-2-log2Up(intWidth)), exp_unrounded)

    val exp_rounded = Cat(UInt(0,1), exp_unrounded) + norm_rounded(sigWidth+1)
    if (intWidth >= (1 << (expWidth-2)))
      overflow_rounded = exp_rounded(expWidth-2)
    val exponent = Cat(norm_out(intWidth-1), Mux(overflow_unrounded, UInt(1 << (expWidth-2)), exp_rounded(expWidth-2,0)))
    
    val overflow = overflow_unrounded || overflow_rounded
    val inexact = roundInexact || overflow
  
    val out = Cat(sign, exponent, norm_rounded(sigWidth-1,0))
    val exc = Cat(UInt(0,2), overflow, UInt(0,1), inexact)
    (out, exc)
  }
}
