
/*============================================================================

This Chisel source file is part of a pre-release version of the HardFloat IEEE
Floating-Point Arithmetic Package, by John R. Hauser (with some contributions
from Yunsup Lee and Andrew Waterman, mainly concerning testing).

Copyright 2010, 2011, 2012, 2013, 2014, 2015, 2016, 2017, 2018 The Regents of
the University of California.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice,
    this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions, and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 3. Neither the name of the University nor the names of its contributors may
    be used to endorse or promote products derived from this software without
    specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS "AS IS", AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, ARE
DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

=============================================================================*/

package hardfloat

import chisel3._

object consts {
    /*------------------------------------------------------------------------
    | For rounding to integer values, rounding mode 'odd' rounds to minimum
    | magnitude instead, same as 'minMag'.
    *------------------------------------------------------------------------*/
    def round_near_even   = "b000".U(3.W)
    def round_minMag      = "b001".U(3.W)
    def round_min         = "b010".U(3.W)
    def round_max         = "b011".U(3.W)
    def round_near_maxMag = "b100".U(3.W)
    def round_odd         = "b110".U(3.W)
    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    def tininess_beforeRounding = 0.U
    def tininess_afterRounding  = 1.U
    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    def flRoundOpt_sigMSBitAlwaysZero  = 1
    def flRoundOpt_subnormsAlwaysExact = 2
    def flRoundOpt_neverUnderflows     = 4
    def flRoundOpt_neverOverflows      = 8
    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    def divSqrtOpt_twoBitsPerCycle     = 16
}

class RawFloat(val expWidth: Int, val sigWidth: Int) extends Bundle
{
    val isNaN: Bool = Bool()              // overrides all other fields
    val isInf: Bool = Bool()              // overrides 'isZero', 'sExp', and 'sig'
    val isZero: Bool = Bool()              // overrides 'sExp' and 'sig'
    val sign: Bool = Bool()
    val sExp: SInt = SInt((expWidth + 2).W)
    val sig: UInt = UInt((sigWidth + 1).W)   // 2 m.s. bits cannot both be 0

}

//*** CHANGE THIS INTO A '.isSigNaN' METHOD OF THE 'RawFloat' CLASS:
object isSigNaNRawFloat
{
    def apply(in: RawFloat): Bool = in.isNaN && !in.sig(in.sigWidth - 2)
}

