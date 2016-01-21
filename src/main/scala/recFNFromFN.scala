
/*============================================================================

This Chisel source file is part of a pre-release version of the HardFloat IEEE
Floating-Point Arithmetic Package, by John R. Hauser (with contributions from
Brian Richards, Yunsup Lee, and Andrew Waterman).

Copyright 2010, 2011, 2012, 2013, 2014, 2015, 2016 The Regents of the
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

import Chisel._

object recFNFromFN
{
    def apply(expWidth: Int, sigWidth: Int, in: Bits) = {
        val normWidth = 1<<log2Up(sigWidth - 1)

        val sign = in(expWidth + sigWidth - 1)
        val expIn = in(expWidth + sigWidth - 2, sigWidth - 1)
        val fractIn = in(sigWidth - 2, 0)

        val isZeroExpIn = (expIn === UInt(0))
        val isZeroFractIn = (fractIn === UInt(0))
        val isZero = isZeroExpIn && isZeroFractIn

        val normCount =
            ~Log2(fractIn<<(normWidth - sigWidth + 1), normWidth)
        val normalizedFract =
            Cat((fractIn<<normCount)(sigWidth - 3, 0), UInt(0, 1))

        val adjustedExp =
            Mux(isZeroExpIn,
                normCount ^ Fill(expWidth + 1, Bool(true)),
                expIn
            ) + (UInt(1<<(expWidth - 1)) | Mux(isZeroExpIn, UInt(2), UInt(1)))

        val isNaN =
            (adjustedExp(expWidth, expWidth - 1) === UInt(3)) &&
                ! isZeroFractIn

        val expOut =
            (adjustedExp & ~(Fill(3, isZero)<<(expWidth - 2))) |
                isNaN<<(expWidth - 2)
        val fractOut = Mux(isZeroExpIn, normalizedFract, fractIn)
        Cat(sign, expOut, fractOut)
    }
}

