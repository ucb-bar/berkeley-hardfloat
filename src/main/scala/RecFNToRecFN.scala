
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

class
    RecFNToRecFN(
        inExpWidth: Int, inSigWidth: Int, outExpWidth: Int, outSigWidth: Int)
    extends Module
{
    val io = new Bundle {
        val in = Bits(INPUT, inExpWidth + inSigWidth + 1)
        val roundingMode = Bits(INPUT, 2)
        val out = Bits(OUTPUT, outExpWidth + outSigWidth + 1)
        val exceptionFlags = Bits(OUTPUT, 5)
    }

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val outRawFloat =
        resizeRawFN(
            outExpWidth,
            outSigWidth,
            rawFNFromRecFN(inExpWidth, inSigWidth, io.in)
        )
    val invalidExc = isSigNaNRawFN(outRawFloat)

    if ((outExpWidth >= inExpWidth) && (outSigWidth >= inSigWidth)) {
        //--------------------------------------------------------------------
        // No rounding required.
        //--------------------------------------------------------------------

        val sign = outRawFloat.sign && ! outRawFloat.isNaN
        val expOut =
            (outRawFloat.sExp(outExpWidth, 0) &
                 ~Mux(outRawFloat.isZero,
                      UInt(6<<(outExpWidth - 2), outExpWidth + 1),
                      UInt(0)
                  ) &
                 ~Mux(outRawFloat.isZero || outRawFloat.isInf,
                      UInt(1<<(outExpWidth - 2), outExpWidth + 1),
                      UInt(0)
                  )) |
                Mux(outRawFloat.isInf,
                    UInt(6<<(outExpWidth - 2), outExpWidth + 1),
                    UInt(0)
                ) |
                Mux(outRawFloat.isNaN,
                    UInt(7<<(outExpWidth - 2), outExpWidth + 1),
                    UInt(0)
                )
        val fractOut =
            Mux(outRawFloat.isNaN,
                UInt(1)<<(outSigWidth - 2),
                outRawFloat.sig(outSigWidth, 2))

        io.out := Cat(sign, expOut, fractOut)
        io.exceptionFlags := Cat(invalidExc, Bits(0, 4))

    } else {
        //--------------------------------------------------------------------
        // Rounding may be required.
        //--------------------------------------------------------------------

        val roundRawFNToRecFN =
            Module(new RoundRawFNToRecFN(outExpWidth, outSigWidth))
        roundRawFNToRecFN.io.invalidExc := invalidExc
        roundRawFNToRecFN.io.infiniteExc := Bool(false)
        roundRawFNToRecFN.io.in := outRawFloat
        roundRawFNToRecFN.io.roundingMode := io.roundingMode
        io.out := roundRawFNToRecFN.io.out
        io.exceptionFlags := roundRawFNToRecFN.io.exceptionFlags

    }
}

