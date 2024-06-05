
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
import chisel3.util.log2Up

import scala.math._
import consts._

class RecFNToIN(expWidth: Int, sigWidth: Int, intWidth: Int) extends chisel3.Module
{
    override def desiredName = s"RecFNToIN_e${expWidth}_s${sigWidth}_i${intWidth}"
    val io = IO(new Bundle {
        val in = Input(Bits((expWidth + sigWidth + 1).W))
        val roundingMode = Input(UInt(3.W))
        val signedOut = Input(Bool())
        val out = Output(Bits(intWidth.W))
        val intExceptionFlags = Output(Bits(3.W))
    })

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val rawIn = rawFloatFromRecFN(expWidth, sigWidth, io.in)

    val magGeOne = rawIn.sExp(expWidth)
    val posExp = rawIn.sExp(expWidth - 1, 0)
    val magJustBelowOne = !magGeOne && posExp.andR

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val roundingMode_near_even   = (io.roundingMode === round_near_even)
    val roundingMode_minMag      = (io.roundingMode === round_minMag)
    val roundingMode_min         = (io.roundingMode === round_min)
    val roundingMode_max         = (io.roundingMode === round_max)
    val roundingMode_near_maxMag = (io.roundingMode === round_near_maxMag)
    val roundingMode_odd         = (io.roundingMode === round_odd)

    /*------------------------------------------------------------------------
    | Assuming the input floating-point value is not a NaN, its magnitude is
    | at least 1, and it is not obviously so large as to lead to overflow,
    | convert its significand to fixed-point (i.e., with the binary point in a
    | fixed location).  For a non-NaN input with a magnitude less than 1, this
    | expression contrives to ensure that the integer bits of 'alignedSig'
    | will all be zeros.
    *------------------------------------------------------------------------*/
    val shiftedSig =
        (magGeOne ## rawIn.sig(sigWidth - 2, 0))<<
            Mux(magGeOne,
                rawIn.sExp(min(expWidth - 2, log2Up(intWidth) - 1), 0),
                0.U
            )
    val alignedSig =
        (shiftedSig>>(sigWidth - 2)) ## shiftedSig(sigWidth - 3, 0).orR
    val unroundedInt = 0.U(intWidth.W) | alignedSig>>2

    val common_inexact = Mux(magGeOne, alignedSig(1, 0).orR, !rawIn.isZero)
    val roundIncr_near_even =
        (magGeOne       && (alignedSig(2, 1).andR || alignedSig(1, 0).andR)) ||
        (magJustBelowOne && alignedSig(1, 0).orR)
    val roundIncr_near_maxMag = (magGeOne && alignedSig(1)) || magJustBelowOne
    val roundIncr =
        (roundingMode_near_even   && roundIncr_near_even  ) ||
        (roundingMode_near_maxMag && roundIncr_near_maxMag) ||
        ((roundingMode_min || roundingMode_odd) &&
             (rawIn.sign && common_inexact)) ||
        (roundingMode_max && (!rawIn.sign && common_inexact))
    val complUnroundedInt = Mux(rawIn.sign, ~unroundedInt, unroundedInt)
    val roundedInt =
        Mux(roundIncr ^ rawIn.sign,
            complUnroundedInt + 1.U,
            complUnroundedInt
        ) | (roundingMode_odd && common_inexact)

    val magGeOne_atOverflowEdge = (posExp === (intWidth - 1).U)
//*** CHANGE TO TAKE BITS FROM THE ORIGINAL 'rawIn.sig' INSTEAD OF FROM
//***  'unroundedInt'?:
    val roundCarryBut2 = unroundedInt(intWidth - 3, 0).andR && roundIncr
    val common_overflow =
        Mux(magGeOne,
            (posExp >= intWidth.U) ||
                Mux(io.signedOut, 
                    Mux(rawIn.sign,
                        magGeOne_atOverflowEdge &&
                            (unroundedInt(intWidth - 2, 0).orR || roundIncr),
                        magGeOne_atOverflowEdge ||
                            ((posExp === (intWidth - 2).U) && roundCarryBut2)
                    ),
                    rawIn.sign ||
                        (magGeOne_atOverflowEdge &&
                             unroundedInt(intWidth - 2) && roundCarryBut2)
                ),
            !io.signedOut && rawIn.sign && roundIncr
        )

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val invalidExc = rawIn.isNaN || rawIn.isInf
    val overflow = !invalidExc && common_overflow
    val inexact  = !invalidExc && !common_overflow && common_inexact

    val excSign = !rawIn.isNaN && rawIn.sign
    val excOut =
        Mux((io.signedOut === excSign),
            (BigInt(1)<<(intWidth - 1)).U,
            0.U
        ) |
        Mux(!excSign, ((BigInt(1)<<(intWidth - 1)) - 1).U, 0.U)

    io.out := Mux(invalidExc || common_overflow, excOut, roundedInt)
    io.intExceptionFlags := invalidExc ## overflow ## inexact
}

