
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
import scala.math._
import consts._

class RecFNToIN(expWidth: Int, sigWidth: Int, intWidth: Int) extends Module
{
    val io = new Bundle {
        val in = Bits(INPUT, expWidth + sigWidth + 1)
        val roundingMode = Bits(INPUT, 2)
        val signedOut = Bool(INPUT)
        val out = Bits(OUTPUT, intWidth)
        val intExceptionFlags = Bits(OUTPUT, 3)
    }

    val sign = io.in(expWidth + sigWidth)
    val exp = io.in(expWidth + sigWidth - 1, sigWidth - 1)
    val fract = io.in(sigWidth - 2, 0)

    val isZero = (exp(expWidth, expWidth - 2) === UInt(0))
    val isSpecial = (exp(expWidth, expWidth - 1) === UInt(3))
    val isNaN = isSpecial && exp(expWidth - 2)
    val notSpecial_magGeOne = exp(expWidth)

    /*------------------------------------------------------------------------
    | Assuming the input floating-point value is not a NaN, its magnitude is
    | at least 1, and it is not obviously so large as to lead to overflow,
    | convert its significand to fixed-point (i.e., with the binary point in a
    | fixed location).  For a non-NaN input with a magnitude less than 1, this
    | expression contrives to ensure that the integer bits of `shiftedSig'
    | will all be zeros.
    *------------------------------------------------------------------------*/
    val shiftedSig =
        Cat(notSpecial_magGeOne, fract)<<
            Mux(notSpecial_magGeOne,
                exp(min(expWidth - 2, log2Up(intWidth) - 1), 0),
                UInt(0)
            )
    val unroundedInt =
        (if (shiftedSig.getWidth - (sigWidth - 1) < intWidth) {
             UInt(0, intWidth) |
                 shiftedSig(shiftedSig.getWidth - 1, sigWidth - 1)
         } else {
             shiftedSig(intWidth + sigWidth - 2, sigWidth - 1)
         })
    val roundBits =
        Cat(shiftedSig(sigWidth - 1, sigWidth - 2),
            shiftedSig(sigWidth - 3, 0).orR
        )
    val roundInexact = Mux(notSpecial_magGeOne, roundBits(1, 0).orR, ! isZero)
    val roundIncr_nearestEven =
        Mux(notSpecial_magGeOne,
            roundBits(2, 1).andR || roundBits(1, 0).andR,
            Mux(exp(expWidth - 1, 0).andR, roundBits(1, 0).orR, Bool(false))
        )
    val roundIncr =
        ((io.roundingMode === round_nearest_even) && roundIncr_nearestEven ) ||
        ((io.roundingMode === round_min)        && (  sign && roundInexact)) ||
        ((io.roundingMode === round_max)        && (! sign && roundInexact))
    val complUnroundedInt = Mux(sign, ~unroundedInt, unroundedInt)
    val roundedInt =
        Mux(roundIncr ^ sign, complUnroundedInt + UInt(1), complUnroundedInt)

//*** CHANGE TO TAKE BITS FROM THE ORIGINAL `fract' INSTEAD OF `unroundedInt'?:
    val roundCarryBut2 = unroundedInt(intWidth - 3, 0).andR && roundIncr
    val posExp = exp(expWidth - 1, 0)

    val overflow_signed =
        Mux(notSpecial_magGeOne,
            (posExp >= UInt(intWidth)) ||
                ((posExp === UInt(intWidth - 1)) &&
                     (! sign || unroundedInt(intWidth - 2, 0).orR
                          || roundIncr)) ||
                (! sign && (posExp === UInt(intWidth - 2)) && roundCarryBut2),
            Bool(false)
        )
    val overflow_unsigned =
        Mux(notSpecial_magGeOne,
            sign || (posExp >= UInt(intWidth)) ||
                ((posExp === UInt(intWidth - 1)) &&
                     unroundedInt(intWidth - 2) && roundCarryBut2),
            sign && roundIncr
        )
    val overflow = Mux(io.signedOut, overflow_signed, overflow_unsigned)
    val invalid = isSpecial
    val excSign = sign && ! isNaN
    val excValue =
        Mux(io.signedOut && excSign, UInt(1)<<(intWidth - 1), UInt(0)) |
        Mux(io.signedOut && ! excSign,
            UInt((BigInt(1)<<(intWidth - 1)) - 1),
            UInt(0)
        ) |
        Mux(! io.signedOut && ! excSign,
            UInt((BigInt(1)<<intWidth) - 1),
            UInt(0)
        )
    val inexact = roundInexact && ! invalid && ! overflow

    io.out := Mux(invalid || overflow, excValue, roundedInt)
    io.intExceptionFlags := Cat(invalid, overflow, inexact)
}

