
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
import consts._

class INToRecFN(intWidth: Int, expWidth: Int, sigWidth: Int) extends Module
{
    val io = new Bundle {
        val signedIn = Bool(INPUT)
        val in = Bits(INPUT, intWidth)
        val roundingMode = Bits(INPUT, 2)
        val out = Bits(OUTPUT, expWidth + sigWidth + 1)
        val exceptionFlags = Bits(OUTPUT, 5)
    }

    val normWidth = 1<<log2Up(intWidth - 1)

    val sign = io.signedIn && io.in(intWidth - 1)
    val absIn = Mux(sign, -io.in, io.in)
    val normCount = ~Log2(absIn<<(normWidth - intWidth), normWidth)
    val normAbsIn = (absIn<<normCount)(intWidth - 1, 0)

    var roundBits = UInt(0, 3)
    if (intWidth - sigWidth >= 2)
        roundBits =
            Cat(normAbsIn(intWidth - sigWidth, intWidth - sigWidth - 1),
                normAbsIn(intWidth - sigWidth - 2, 0).orR
            )
    else if (intWidth - sigWidth == 1)
        roundBits =
            Cat(normAbsIn(intWidth - sigWidth, intWidth - sigWidth - 1),
                Bool(false)
            )

    val roundInexact = roundBits(1, 0).orR
    val round =
        Mux((io.roundingMode === round_nearest_even),
            roundBits(2, 1).andR || roundBits(1, 0).andR,
            Bool(false)
        ) |
        Mux((io.roundingMode === round_min),
            sign && roundInexact,
            Bool(false)
        ) |
        Mux((io.roundingMode === round_max),
            ! sign && roundInexact,
            Bool(false)
        )

    var unroundedNorm = UInt(0)
    if (intWidth - sigWidth >= 0)
        unroundedNorm = normAbsIn(intWidth - 1, intWidth - sigWidth)
    else {
        unroundedNorm = Cat(normAbsIn, UInt(0, sigWidth - intWidth))
    }
    unroundedNorm = Cat(UInt(0, 1), unroundedNorm)
    val roundedNorm = Mux(round, unroundedNorm + UInt(1), unroundedNorm)

    var overflow_unrounded = Bool(false)
    var unroundedExp = ~normCount
    if (log2Up(intWidth) > expWidth - 1) {
        overflow_unrounded = (unroundedExp >= UInt(1<<(expWidth - 1)))
        unroundedExp = unroundedExp(expWidth - 2, 0)
    } else if (log2Up(intWidth) < expWidth - 1) {
        unroundedExp =
            Cat(UInt(0, expWidth - 1 - log2Up(intWidth)), unroundedExp)
    }

    val roundedExp = Cat(UInt(0, 1), unroundedExp) + roundedNorm(sigWidth)
    var overflow_rounded = Bool(false)
    if (intWidth >= (1<<(expWidth - 1))) {
        overflow_rounded = roundedExp(expWidth - 1)
    }
    val expOut =
        Cat(normAbsIn(intWidth - 1),
            Mux(overflow_unrounded,
                UInt(1<<(expWidth - 1)),
                roundedExp(expWidth - 1, 0)
            )
        )

    val overflow = overflow_unrounded || overflow_rounded
    val inexact = roundInexact || overflow

    io.out := Cat(sign, expOut, roundedNorm(sigWidth - 2, 0))
    io.exceptionFlags := Cat(UInt(0, 2), overflow, UInt(0, 1), inexact)
}

