
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

class CompareRecFN(expWidth: Int, sigWidth: Int) extends Module
{
    val io = new Bundle {
        val a = Bits(INPUT, expWidth + sigWidth + 1)
        val b = Bits(INPUT, expWidth + sigWidth + 1)
        val signaling = Bool(INPUT)
        val lt = Bool(OUTPUT)
        val eq = Bool(OUTPUT)
        val gt = Bool(OUTPUT)
        val exceptionFlags = Bits(OUTPUT, 5)
    }

    val rawA = rawFNFromRecFN(expWidth, sigWidth, io.a)
    val rawB = rawFNFromRecFN(expWidth, sigWidth, io.b)

    val ordered = ! rawA.isNaN && ! rawB.isNaN
    val bothInfs  = rawA.isInf  && rawB.isInf
    val bothZeros = rawA.isZero && rawB.isZero
    val eqExps = (rawA.sExp === rawB.sExp)
    val common_ltMags =
        (rawA.sExp < rawB.sExp) || (eqExps && (rawA.sig < rawB.sig))
    val common_eqMags = eqExps && (rawA.sig === rawB.sig)

    val ordered_lt =
        ! bothZeros &&
            ((rawA.sign && ! rawB.sign) ||
                 (! bothInfs &&
                      ((rawA.sign && ! common_ltMags && ! common_eqMags) ||
                           (! rawB.sign && common_ltMags))))
    val ordered_eq =
        bothZeros || ((rawA.sign === rawB.sign) && (bothInfs || common_eqMags))

    val invalid =
        isSigNaNRawFN(rawA) || isSigNaNRawFN(rawB) ||
            (io.signaling && ! ordered)

    io.lt := ordered && ordered_lt
    io.eq := ordered && ordered_eq
    io.gt := ordered && ! ordered_lt && ! ordered_eq

    io.exceptionFlags := Cat(invalid, Bits(0, 4))
}

