
/*============================================================================

This Chisel source file is part of a pre-release version of the HardFloat IEEE
Floating-Point Arithmetic Package, by John R. Hauser (with some contributions
from Yunsup Lee and Andrew Waterman, mainly concerning testing).

Copyright 2017 SiFive, Inc.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice,
    this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions, and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 3. Neither the name of SiFive nor the names of its contributors may
    be used to endorse or promote products derived from this software without
    specific prior written permission.

THIS SOFTWARE IS PROVIDED BY SIFIVE AND CONTRIBUTORS "AS IS", AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, ARE
DISCLAIMED.  IN NO EVENT SHALL SIFIVE OR CONTRIBUTORS BE LIABLE FOR ANY
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

/*----------------------------------------------------------------------------
| Computes a division or square root for floating-point in recoded form.
| Multiple clock cycles are needed for each division or square-root operation,
| except possibly in special cases.
*----------------------------------------------------------------------------*/

class
    DivSqrtRecFNToRaw_small(expWidth: Int, sigWidth: Int, options: Int)
    extends Module
{
    val io = new Bundle {
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val inReady        = Bool(OUTPUT)
        val inValid        = Bool(INPUT)
        val sqrtOp         = Bool(INPUT)
        val a              = Bits(INPUT, expWidth + sigWidth + 1)
        val b              = Bits(INPUT, expWidth + sigWidth + 1)
        val roundingMode   = UInt(INPUT, 3)
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val rawOutValid_div  = Bool(OUTPUT)
        val rawOutValid_sqrt = Bool(OUTPUT)
        val roundingModeOut  = UInt(OUTPUT, 3)
        val invalidExc       = Bool(OUTPUT)
        val infiniteExc      = Bool(OUTPUT)
        val rawOut = new RawFloat(expWidth, sigWidth + 2).asOutput
    }

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val cycleNum       = Reg(init = UInt(0, log2Up(sigWidth + 3)))

    val sqrtOp_Z       = Reg(Bool())
    val majorExc_Z     = Reg(Bool())
//*** REDUCE 3 BITS TO 2-BIT CODE:
    val isNaN_Z        = Reg(Bool())
    val isInf_Z        = Reg(Bool())
    val isZero_Z       = Reg(Bool())
    val sign_Z         = Reg(Bool())
    val sExp_Z         = Reg(SInt(width = expWidth + 2))
    val fractB_Z       = Reg(UInt(width = sigWidth - 1))
    val roundingMode_Z = Reg(UInt(width = 3))

    /*------------------------------------------------------------------------
    | (The most-significant and least-significant bits of 'rem_Z' are needed
    | only for square roots.)
    *------------------------------------------------------------------------*/
    val rem_Z          = Reg(UInt(width = sigWidth + 2))
    val notZeroRem_Z   = Reg(Bool())
    val sigX_Z         = Reg(UInt(width = sigWidth + 2))

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val rawA_S = rawFloatFromRecFN(expWidth, sigWidth, io.a)
    val rawB_S = rawFloatFromRecFN(expWidth, sigWidth, io.b)

//*** IMPROVE THESE:
    val notSigNaNIn_invalidExc_S_div =
        (rawA_S.isZero && rawB_S.isZero) || (rawA_S.isInf && rawB_S.isInf)
    val notSigNaNIn_invalidExc_S_sqrt =
        ! rawA_S.isNaN && ! rawA_S.isZero && rawA_S.sign
    val majorExc_S =
        Mux(io.sqrtOp,
            isSigNaNRawFloat(rawA_S) || notSigNaNIn_invalidExc_S_sqrt,
            isSigNaNRawFloat(rawA_S) || isSigNaNRawFloat(rawB_S) ||
                notSigNaNIn_invalidExc_S_div ||
                (! rawA_S.isNaN && ! rawA_S.isInf && rawB_S.isZero)
        )
    val isNaN_S =
        Mux(io.sqrtOp,
            rawA_S.isNaN || notSigNaNIn_invalidExc_S_sqrt,
            rawA_S.isNaN || rawB_S.isNaN || notSigNaNIn_invalidExc_S_div
        )
    val isInf_S  = Mux(io.sqrtOp, rawA_S.isInf,  rawA_S.isInf || rawB_S.isZero)
    val isZero_S = Mux(io.sqrtOp, rawA_S.isZero, rawA_S.isZero || rawB_S.isInf)
    val sign_S = rawA_S.sign ^ (! io.sqrtOp && rawB_S.sign)

    val specialCaseA_S = rawA_S.isNaN || rawA_S.isInf || rawA_S.isZero
    val specialCaseB_S = rawB_S.isNaN || rawB_S.isInf || rawB_S.isZero
    val normalCase_S_div = ! specialCaseA_S && ! specialCaseB_S
    val normalCase_S_sqrt = ! specialCaseA_S && ! rawA_S.sign
    val normalCase_S = Mux(io.sqrtOp, normalCase_S_sqrt, normalCase_S_div)

    val sExpQuot_S_div =
        rawA_S.sExp +&
            Cat(rawB_S.sExp(expWidth), ~rawB_S.sExp(expWidth - 1, 0)).asSInt
//*** IS THIS OPTIMAL?:
    val sSatExpQuot_S_div =
        Cat(Mux((SInt(BigInt(7)<<(expWidth - 2)) <= sExpQuot_S_div),
                UInt(6),
                sExpQuot_S_div(expWidth + 1, expWidth - 2)
            ),
            sExpQuot_S_div(expWidth - 3, 0)
        ).asSInt

    val evenSqrt_S = io.sqrtOp && ! rawA_S.sExp(0)
    val oddSqrt_S  = io.sqrtOp &&   rawA_S.sExp(0)

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val idle = (cycleNum === UInt(0))
    val inReady = (cycleNum <= UInt(1))
    val entering = inReady && io.inValid
    val entering_normalCase = entering && normalCase_S

    val skipCycle2 = (cycleNum === UInt(3)) && sigX_Z(sigWidth + 1)

    when (! idle || io.inValid) {
        cycleNum :=
            Mux(entering & ! normalCase_S, UInt(1), UInt(0)) |
            Mux(entering_normalCase,
                Mux(io.sqrtOp,
                    Mux(rawA_S.sExp(0), UInt(sigWidth), UInt(sigWidth + 1)),
                    UInt(sigWidth + 2)
                ),
                UInt(0)
            ) |
            Mux(! idle && ! skipCycle2, cycleNum - UInt(1), UInt(0)) |
            Mux(! idle &&   skipCycle2, UInt(1),            UInt(0))
    }

    io.inReady := inReady

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    when (entering) {
        sqrtOp_Z   := io.sqrtOp
        majorExc_Z := majorExc_S
        isNaN_Z    := isNaN_S
        isInf_Z    := isInf_S
        isZero_Z   := isZero_S
        sign_Z     := sign_S
    }
    when (entering_normalCase) {
        sExp_Z :=
            Mux(io.sqrtOp,
                (rawA_S.sExp>>1) +& SInt(BigInt(1)<<(expWidth - 1)),
                sSatExpQuot_S_div
            )
        roundingMode_Z := io.roundingMode
    }
    when (entering_normalCase && ! io.sqrtOp) {
        fractB_Z := rawB_S.sig(sigWidth - 2, 0)
    }

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val rem =
        Mux(inReady && ! oddSqrt_S, rawA_S.sig<<1, UInt(0)) |
        Mux(inReady && oddSqrt_S,
            Cat(rawA_S.sig(sigWidth - 1, sigWidth - 2) - UInt(1),
                rawA_S.sig(sigWidth - 3, 0)<<3
            ),
            UInt(0)
        ) |
        Mux(! inReady, rem_Z<<1, UInt(0))
    val bitMask = (UInt(1)<<cycleNum)>>2
    val trialTerm =
        Mux(inReady && ! io.sqrtOp, rawB_S.sig<<1,                   UInt(0)) |
        Mux(inReady && evenSqrt_S,  UInt(BigInt(1)<<sigWidth),       UInt(0)) |
        Mux(inReady && oddSqrt_S,   UInt(BigInt(5)<<(sigWidth - 1)), UInt(0)) |
        Mux(! inReady && ! sqrtOp_Z, Cat(UInt(1, 1), fractB_Z)<<1,   UInt(0)) |
        Mux(! inReady &&   sqrtOp_Z, sigX_Z<<1 | bitMask,            UInt(0))
    val trialRem = rem.zext - trialTerm.zext
    val newBit = (SInt(0) <= trialRem)

    when (entering_normalCase || (cycleNum > UInt(2))) {
        rem_Z := Mux(newBit, trialRem.asUInt, rem)
    }
    when (entering_normalCase || (! inReady && newBit)) {
        notZeroRem_Z := (trialRem =/= SInt(0))
        sigX_Z :=
            Mux(inReady && ! io.sqrtOp, newBit<<(sigWidth + 1),    UInt(0)) |
            Mux(inReady &&   io.sqrtOp, UInt(BigInt(1)<<sigWidth), UInt(0)) |
            Mux(inReady && oddSqrt_S,   newBit<<(sigWidth - 1),    UInt(0)) |
            Mux(! inReady,              sigX_Z | bitMask,          UInt(0))
    }

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val rawOutValid = (cycleNum === UInt(1))

    io.rawOutValid_div  := rawOutValid && ! sqrtOp_Z
    io.rawOutValid_sqrt := rawOutValid &&   sqrtOp_Z
    io.roundingModeOut  := roundingMode_Z
    io.invalidExc    := majorExc_Z &&   isNaN_Z
    io.infiniteExc   := majorExc_Z && ! isNaN_Z
    io.rawOut.isNaN  := isNaN_Z
    io.rawOut.isInf  := isInf_Z
    io.rawOut.isZero := isZero_Z
    io.rawOut.sign   := sign_Z
    io.rawOut.sExp   := sExp_Z
    io.rawOut.sig    := sigX_Z<<1 | notZeroRem_Z

}

/*----------------------------------------------------------------------------
*----------------------------------------------------------------------------*/

class
    DivSqrtRecFN_small(expWidth: Int, sigWidth: Int, options: Int)
    extends Module
{
    val io = new Bundle {
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val inReady        = Bool(OUTPUT)
        val inValid        = Bool(INPUT)
        val sqrtOp         = Bool(INPUT)
        val a              = Bits(INPUT, expWidth + sigWidth + 1)
        val b              = Bits(INPUT, expWidth + sigWidth + 1)
        val roundingMode   = UInt(INPUT, 3)
        val detectTininess = UInt(INPUT, 1)
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val outValid_div   = Bool(OUTPUT)
        val outValid_sqrt  = Bool(OUTPUT)
        val out            = Bits(OUTPUT, expWidth + sigWidth + 1)
        val exceptionFlags = Bits(OUTPUT, 5)
    }

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val divSqrtRecFNToRaw =
        Module(new DivSqrtRecFNToRaw_small(expWidth, sigWidth, options))

    io.inReady := divSqrtRecFNToRaw.io.inReady
    divSqrtRecFNToRaw.io.inValid      := io.inValid
    divSqrtRecFNToRaw.io.sqrtOp       := io.sqrtOp
    divSqrtRecFNToRaw.io.a            := io.a
    divSqrtRecFNToRaw.io.b            := io.b
    divSqrtRecFNToRaw.io.roundingMode := io.roundingMode

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    io.outValid_div  := divSqrtRecFNToRaw.io.rawOutValid_div
    io.outValid_sqrt := divSqrtRecFNToRaw.io.rawOutValid_sqrt

    val roundRawFNToRecFN =
        Module(new RoundRawFNToRecFN(expWidth, sigWidth, 0))
    roundRawFNToRecFN.io.invalidExc   := divSqrtRecFNToRaw.io.invalidExc
    roundRawFNToRecFN.io.infiniteExc  := divSqrtRecFNToRaw.io.infiniteExc
    roundRawFNToRecFN.io.in           := divSqrtRecFNToRaw.io.rawOut
    roundRawFNToRecFN.io.roundingMode := divSqrtRecFNToRaw.io.roundingModeOut
    roundRawFNToRecFN.io.detectTininess := io.detectTininess
    io.out            := roundRawFNToRecFN.io.out
    io.exceptionFlags := roundRawFNToRecFN.io.exceptionFlags

}

