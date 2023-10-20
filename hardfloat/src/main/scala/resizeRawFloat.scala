
/*============================================================================

This Chisel source file is part of a pre-release version of the HardFloat IEEE
Floating-Point Arithmetic Package, by John R. Hauser (with some contributions
from Yunsup Lee and Andrew Waterman, mainly concerning testing).

Copyright 2010, 2011, 2012, 2013, 2014, 2015, 2016, 2017 The Regents of the
University of California.  All rights reserved.

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
import chisel3.util.Fill

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------

object resizeRawFloat
{
    def apply(expWidth: Int, sigWidth: Int, in: RawFloat): RawFloat =
    {
        val sAdjustedExp =
            in.sExp +& ((BigInt(1)<<expWidth) - (BigInt(1)<<in.expWidth)).S

        val out = Wire(new RawFloat(expWidth, sigWidth))
        out.sign   := in.sign
        out.isNaN  := in.isNaN
        out.isInf  := in.isInf
        out.isZero := in.isZero
        out.sExp :=
            (if (in.expWidth <= expWidth)
                 sAdjustedExp
             else
                 ((sAdjustedExp < 0.S)##
                     Mux(sAdjustedExp(in.expWidth + 1, expWidth + 1).orR,
                         Fill(expWidth - 1, 1.U(1.W)) ## 0.U(2.W),
                         sAdjustedExp(expWidth, 0)
                     )
                 ).asSInt)
        out.sig :=
            (if (in.sigWidth <= sigWidth)
                 in.sig<<(sigWidth - in.sigWidth)
             else
                 in.sig(in.sigWidth, in.sigWidth - sigWidth + 1) ##
                     in.sig(in.sigWidth - sigWidth, 0).orR
                 )
        out
    }
}

