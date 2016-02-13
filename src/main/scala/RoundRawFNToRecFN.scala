
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

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------

class RawFloat(val expWidth: Int, val sigWidth: Int) extends Bundle
{
    val sign = Bool()
    val isNaN = Bool()               // overrides all other fields
    val isInf = Bool()               // overrides `isZero', `sExp', and `fract'
    val isZero = Bool()              // overrides `sExp' and `fract'
    val sExp = SInt(width = expWidth + 2)
    val sig = Bits(width = sigWidth + 3)   // 2 m.s. bits cannot both be 0

    override def cloneType =
        new RawFloat(expWidth, sigWidth).asInstanceOf[this.type]
}

object isSigNaNRawFN
{
    def apply(in: RawFloat): Bool = in.isNaN && ! in.sig(in.sigWidth)
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------

class RoundRawFNToRecFN(expWidth: Int, sigWidth: Int) extends Module
{
    val io = new Bundle {
        val invalidExc = Bool(INPUT)   // overrides `infiniteExc' and `in'
        val infiniteExc = Bool(INPUT)  // overrides `in' except for `in.sign'
        val in = new RawFloat(expWidth, sigWidth).asInput
        val roundingMode = Bits(INPUT, 2)
        val out = Bits(OUTPUT, expWidth + sigWidth + 1)
        val exceptionFlags = Bits(OUTPUT, 5)
    }

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val NaNExp = 7<<(expWidth - 2)
    val infExp = 6<<(expWidth - 2)
    val maxFiniteExp = infExp - 1
    val minNormExp = (1<<(expWidth - 1)) + 2
    val minNonzeroExp = minNormExp - sigWidth + 1

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val roundingMode_nearest_even = (io.roundingMode === round_nearest_even)
    val roundingMode_minMag       = (io.roundingMode === round_minMag)
    val roundingMode_min          = (io.roundingMode === round_min)
    val roundingMode_max          = (io.roundingMode === round_max)

    val roundMagUp =
        (roundingMode_min && io.in.sign) || (roundingMode_max && ! io.in.sign)

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val doShiftSigDown1 = io.in.sig(sigWidth + 2)
    val roundMask =
        Cat(Fill(sigWidth + 1, (io.in.sExp < SInt(0))) |
                lowMask(
                    io.in.sExp(expWidth, 0),
                    minNormExp - sigWidth - 1,
                    minNormExp
                ) | doShiftSigDown1,
            UInt(3, 2)
        )
    val roundPosMask = ~(roundMask>>1) & roundMask
    val roundPosBit = (io.in.sig & roundPosMask).orR
    val anyRoundExtra = (io.in.sig & roundMask>>1).orR
    val anyRound = roundPosBit || anyRoundExtra

    val roundedSig =
        Mux((roundingMode_nearest_even && roundPosBit) ||
                (roundMagUp && anyRound),
            (((io.in.sig | roundMask)>>2) + UInt(1)) &
                ~Mux(roundingMode_nearest_even && roundPosBit &&
                         ! anyRoundExtra,
                     roundMask>>1,
                     UInt(0, sigWidth + 2)
                 ),
            (io.in.sig & ~roundMask)>>2
        )
//*** NEED TO ACCOUNT FOR ROUND-EVEN ZEROING MSB OF SUBNORMAL SIG?
    val sRoundedExp = io.in.sExp + (roundedSig>>sigWidth).zext

    val common_expOut = sRoundedExp(expWidth, 0)
    val common_fractOut =
        Mux(doShiftSigDown1,
            roundedSig(sigWidth - 1, 1),
            roundedSig(sigWidth - 2, 0)
        )

    val common_overflow = (sRoundedExp>>(expWidth - 1) >= SInt(3))
//*** NEED TO ACCOUNT FOR ROUND-EVEN ZEROING MSB OF SUBNORMAL SIG?
    val common_totalUnderflow = (sRoundedExp < SInt(minNonzeroExp))
    val common_underflow =
        anyRound &&
            (io.in.sExp <
                 Mux(doShiftSigDown1, SInt(minNormExp - 1), SInt(minNormExp)))
    val common_inexact = anyRound

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val isNaNOut = io.invalidExc || io.in.isNaN
    val notNaN_isSpecialInfOut = io.infiniteExc || io.in.isInf
    val commonCase = ! isNaNOut && ! notNaN_isSpecialInfOut && ! io.in.isZero
    val overflow  = commonCase && common_overflow
    val underflow = commonCase && common_underflow
    val inexact = overflow || (commonCase && common_inexact)

    val overflow_roundMagUp = roundingMode_nearest_even || roundMagUp
    val pegMinNonzeroMagOut = commonCase && common_totalUnderflow && roundMagUp
    val pegMaxFiniteMagOut = commonCase && overflow && ! overflow_roundMagUp
    val notNaN_isInfOut =
        notNaN_isSpecialInfOut || (overflow && overflow_roundMagUp)

    val signOut = Mux(isNaNOut, Bool(false), io.in.sign)
    val expOut =
        (common_expOut &
             ~Mux(io.in.isZero || common_totalUnderflow,
                  UInt(7<<(expWidth - 2), expWidth + 1),
                  UInt(0)
              ) &
             ~Mux(pegMinNonzeroMagOut,
                  ~UInt(minNonzeroExp, expWidth + 1),
                  UInt(0)
              ) &
             ~Mux(pegMaxFiniteMagOut,
                  UInt(1<<(expWidth - 1), expWidth + 1),
                  UInt(0)
              ) &
             ~Mux(notNaN_isInfOut,
                  UInt(1<<(expWidth - 2), expWidth + 1),
                  UInt(0)
              )) |
            Mux(pegMinNonzeroMagOut,
                UInt(minNonzeroExp, expWidth + 1),
                UInt(0)
            ) |
            Mux(pegMaxFiniteMagOut,
                UInt(maxFiniteExp, expWidth + 1),
                UInt(0)
            ) |
            Mux(notNaN_isInfOut, UInt(infExp, expWidth + 1), UInt(0)) |
            Mux(isNaNOut,        UInt(NaNExp, expWidth + 1), UInt(0))
    val fractOut =
        Mux(common_totalUnderflow || isNaNOut,
            Mux(isNaNOut, UInt(1)<<(sigWidth - 2), UInt(0)),
            common_fractOut
        ) |
        Fill(sigWidth - 1, pegMaxFiniteMagOut)

    io.out := Cat(signOut, expOut, fractOut)
    io.exceptionFlags :=
        Cat(io.invalidExc, io.infiniteExc, overflow, underflow, inexact)
}

