// See LICENSE for license details.

//
// recodedFloat64ToAny( in, out );
// Author: Brian Richards, 10/21/2010
//

package hardfloat

import Chisel._
import consts._

object recodedFloatNClassify {
  def apply(in: UInt, sigWidth: Int, expWidth: Int) = {
    val sign = in(sigWidth+expWidth)
    val exp = in(sigWidth+expWidth-1,sigWidth)
    val sig = in(sigWidth-1,0)
  
    val code        = exp(expWidth-1,expWidth-3)
    val codeHi      = code(2,1)
    val isSpecial   = codeHi === UInt(3)

    val isHighSubnormalIn = exp(expWidth-3, 0) < UInt(2)
    val isSubnormal = code === UInt(1) || codeHi === UInt(1) && isHighSubnormalIn
    val isNormal = codeHi === UInt(1) && !isHighSubnormalIn || codeHi === UInt(2)
    val isZero = code === UInt(0)
    val isInf = isSpecial && !exp(expWidth-3)
    val isNaN = code.andR
    val isSNaN = isNaN && !sig(sigWidth-1)
    val isQNaN = isNaN && sig(sigWidth-1)
  
    Cat(isQNaN, isSNaN, isInf && !sign, isNormal && !sign,
        isSubnormal && !sign, isZero && !sign, isZero && sign,
        isSubnormal && sign, isNormal && sign, isInf && sign)
  }
}
