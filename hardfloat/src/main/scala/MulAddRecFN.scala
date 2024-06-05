
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
import chisel3.util._
import consts._

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------

class MulAddRecFN_interIo(expWidth: Int, sigWidth: Int) extends Bundle
{
//*** ENCODE SOME OF THESE CASES IN FEWER BITS?:
    val isSigNaNAny     = Bool()
    val isNaNAOrB       = Bool()
    val isInfA          = Bool()
    val isZeroA         = Bool()
    val isInfB          = Bool()
    val isZeroB         = Bool()
    val signProd        = Bool()
    val isNaNC          = Bool()
    val isInfC          = Bool()
    val isZeroC         = Bool()
    val sExpSum         = SInt((expWidth + 2).W)
    val doSubMags       = Bool()
    val CIsDominant     = Bool()
    val CDom_CAlignDist = UInt(log2Ceil(sigWidth + 1).W)
    val highAlignedSigC = UInt((sigWidth + 2).W)
    val bit0AlignedSigC = UInt(1.W)

}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
class MulAddRecFNToRaw_preMul(expWidth: Int, sigWidth: Int) extends RawModule
{
    override def desiredName = s"MulAddRecFNToRaw_preMul_e${expWidth}_s${sigWidth}"
    val io = IO(new Bundle {
        val op = Input(Bits(2.W))
        val a = Input(Bits((expWidth + sigWidth + 1).W))
        val b = Input(Bits((expWidth + sigWidth + 1).W))
        val c = Input(Bits((expWidth + sigWidth + 1).W))
        val mulAddA = Output(UInt(sigWidth.W))
        val mulAddB = Output(UInt(sigWidth.W))
        val mulAddC = Output(UInt((sigWidth * 2).W))
        val toPostMul = Output(new MulAddRecFN_interIo(expWidth, sigWidth))
    })

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
//*** POSSIBLE TO REDUCE THIS BY 1 OR 2 BITS?  (CURRENTLY 2 BITS BETWEEN
//***  UNSHIFTED C AND PRODUCT):
    val sigSumWidth = sigWidth * 3 + 3

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val rawA = rawFloatFromRecFN(expWidth, sigWidth, io.a)
    val rawB = rawFloatFromRecFN(expWidth, sigWidth, io.b)
    val rawC = rawFloatFromRecFN(expWidth, sigWidth, io.c)

    val signProd = rawA.sign ^ rawB.sign ^ io.op(1)
//*** REVIEW THE BIAS FOR 'sExpAlignedProd':
    val sExpAlignedProd =
        rawA.sExp +& rawB.sExp + (-(BigInt(1)<<expWidth) + sigWidth + 3).S

    val doSubMags = signProd ^ rawC.sign ^ io.op(0)

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val sNatCAlignDist = sExpAlignedProd - rawC.sExp
    val posNatCAlignDist = sNatCAlignDist(expWidth + 1, 0)
    val isMinCAlign = rawA.isZero || rawB.isZero || (sNatCAlignDist < 0.S)
    val CIsDominant =
        ! rawC.isZero && (isMinCAlign || (posNatCAlignDist <= sigWidth.U))
    val CAlignDist =
        Mux(isMinCAlign,
            0.U,
            Mux(posNatCAlignDist < (sigSumWidth - 1).U,
                posNatCAlignDist(log2Ceil(sigSumWidth) - 1, 0),
                (sigSumWidth - 1).U
            )
        )
    val mainAlignedSigC =
        (Mux(doSubMags, ~rawC.sig, rawC.sig) ## Fill(sigSumWidth - sigWidth + 2, doSubMags)).asSInt>>CAlignDist
    val reduced4CExtra =
        (orReduceBy4(rawC.sig<<((sigSumWidth - sigWidth - 1) & 3)) &
             lowMask(
                 CAlignDist>>2,
//*** NOT NEEDED?:
//                 (sigSumWidth + 2)>>2,
                 (sigSumWidth - 1)>>2,
                 (sigSumWidth - sigWidth - 1)>>2
             )
        ).orR
    val alignedSigC =
        Cat(mainAlignedSigC>>3,
            Mux(doSubMags,
                mainAlignedSigC(2, 0).andR && ! reduced4CExtra,
                mainAlignedSigC(2, 0).orR  ||   reduced4CExtra
            )
        )

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    io.mulAddA := rawA.sig
    io.mulAddB := rawB.sig
    io.mulAddC := alignedSigC(sigWidth * 2, 1)

    io.toPostMul.isSigNaNAny :=
        isSigNaNRawFloat(rawA) || isSigNaNRawFloat(rawB) ||
            isSigNaNRawFloat(rawC)
    io.toPostMul.isNaNAOrB := rawA.isNaN || rawB.isNaN
    io.toPostMul.isInfA    := rawA.isInf
    io.toPostMul.isZeroA   := rawA.isZero
    io.toPostMul.isInfB    := rawB.isInf
    io.toPostMul.isZeroB   := rawB.isZero
    io.toPostMul.signProd  := signProd
    io.toPostMul.isNaNC    := rawC.isNaN
    io.toPostMul.isInfC    := rawC.isInf
    io.toPostMul.isZeroC   := rawC.isZero
    io.toPostMul.sExpSum   :=
        Mux(CIsDominant, rawC.sExp, sExpAlignedProd - sigWidth.S)
    io.toPostMul.doSubMags := doSubMags
    io.toPostMul.CIsDominant := CIsDominant
    io.toPostMul.CDom_CAlignDist := CAlignDist(log2Ceil(sigWidth + 1) - 1, 0)
    io.toPostMul.highAlignedSigC :=
        alignedSigC(sigSumWidth - 1, sigWidth * 2 + 1)
    io.toPostMul.bit0AlignedSigC := alignedSigC(0)
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
class MulAddRecFNToRaw_postMul(expWidth: Int, sigWidth: Int) extends RawModule
{
    override def desiredName = s"MulAddRecFNToRaw_postMul_e${expWidth}_s${sigWidth}"
    val io = IO(new Bundle {
        val fromPreMul = Input(new MulAddRecFN_interIo(expWidth, sigWidth))
        val mulAddResult = Input(UInt((sigWidth * 2 + 1).W))
        val roundingMode = Input(UInt(3.W))
        val invalidExc  = Output(Bool())
        val rawOut = Output(new RawFloat(expWidth, sigWidth + 2))
    })

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val sigSumWidth = sigWidth * 3 + 3

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val roundingMode_min = (io.roundingMode === round_min)

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val opSignC = io.fromPreMul.signProd ^ io.fromPreMul.doSubMags
    val sigSum =
        Cat(Mux(io.mulAddResult(sigWidth * 2),
                io.fromPreMul.highAlignedSigC + 1.U,
                io.fromPreMul.highAlignedSigC
               ),
            io.mulAddResult(sigWidth * 2 - 1, 0),
            io.fromPreMul.bit0AlignedSigC
        )

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val CDom_sign = opSignC
    val CDom_sExp = io.fromPreMul.sExpSum - io.fromPreMul.doSubMags.zext
    val CDom_absSigSum =
        Mux(io.fromPreMul.doSubMags,
            ~sigSum(sigSumWidth - 1, sigWidth + 1),
            0.U(1.W) ##
//*** IF GAP IS REDUCED TO 1 BIT, MUST REDUCE THIS COMPONENT TO 1 BIT TOO:
                io.fromPreMul.highAlignedSigC(sigWidth + 1, sigWidth) ##
                sigSum(sigSumWidth - 3, sigWidth + 2)

        )
    val CDom_absSigSumExtra =
        Mux(io.fromPreMul.doSubMags,
            (~sigSum(sigWidth, 1)).orR,
            sigSum(sigWidth + 1, 1).orR
        )
    val CDom_mainSig =
        (CDom_absSigSum<<io.fromPreMul.CDom_CAlignDist)(
            sigWidth * 2 + 1, sigWidth - 3)
    val CDom_reduced4SigExtra =
        (orReduceBy4(CDom_absSigSum(sigWidth - 1, 0)<<(~sigWidth & 3)) &
             lowMask(io.fromPreMul.CDom_CAlignDist>>2, 0, sigWidth>>2)).orR
    val CDom_sig =
        Cat(CDom_mainSig>>3,
            CDom_mainSig(2, 0).orR || CDom_reduced4SigExtra ||
                CDom_absSigSumExtra
        )

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val notCDom_signSigSum = sigSum(sigWidth * 2 + 3)
    val notCDom_absSigSum =
        Mux(notCDom_signSigSum,
            ~sigSum(sigWidth * 2 + 2, 0),
            sigSum(sigWidth * 2 + 2, 0) + io.fromPreMul.doSubMags
        )
    val notCDom_reduced2AbsSigSum = orReduceBy2(notCDom_absSigSum)
    val notCDom_normDistReduced2 = countLeadingZeros(notCDom_reduced2AbsSigSum)
    val notCDom_nearNormDist = notCDom_normDistReduced2<<1
    val notCDom_sExp = io.fromPreMul.sExpSum - notCDom_nearNormDist.asUInt.zext
    val notCDom_mainSig =
        (notCDom_absSigSum<<notCDom_nearNormDist)(
            sigWidth * 2 + 3, sigWidth - 1)
    val notCDom_reduced4SigExtra =
        (orReduceBy2(
             notCDom_reduced2AbsSigSum(sigWidth>>1, 0)<<((sigWidth>>1) & 1)) &
             lowMask(notCDom_normDistReduced2>>1, 0, (sigWidth + 2)>>2)
        ).orR
    val notCDom_sig =
        Cat(notCDom_mainSig>>3,
            notCDom_mainSig(2, 0).orR || notCDom_reduced4SigExtra
        )
    val notCDom_completeCancellation =
        (notCDom_sig(sigWidth + 2, sigWidth + 1) === 0.U)
    val notCDom_sign =
        Mux(notCDom_completeCancellation,
            roundingMode_min,
            io.fromPreMul.signProd ^ notCDom_signSigSum
        )

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val notNaN_isInfProd = io.fromPreMul.isInfA || io.fromPreMul.isInfB
    val notNaN_isInfOut = notNaN_isInfProd || io.fromPreMul.isInfC
    val notNaN_addZeros =
        (io.fromPreMul.isZeroA || io.fromPreMul.isZeroB) &&
            io.fromPreMul.isZeroC

    io.invalidExc :=
        io.fromPreMul.isSigNaNAny ||
        (io.fromPreMul.isInfA && io.fromPreMul.isZeroB) ||
        (io.fromPreMul.isZeroA && io.fromPreMul.isInfB) ||
        (! io.fromPreMul.isNaNAOrB &&
             (io.fromPreMul.isInfA || io.fromPreMul.isInfB) &&
             io.fromPreMul.isInfC &&
             io.fromPreMul.doSubMags)
    io.rawOut.isNaN := io.fromPreMul.isNaNAOrB || io.fromPreMul.isNaNC
    io.rawOut.isInf := notNaN_isInfOut
//*** IMPROVE?:
    io.rawOut.isZero :=
        notNaN_addZeros ||
            (! io.fromPreMul.CIsDominant && notCDom_completeCancellation)
    io.rawOut.sign :=
        (notNaN_isInfProd && io.fromPreMul.signProd) ||
        (io.fromPreMul.isInfC && opSignC) ||
        (notNaN_addZeros && ! roundingMode_min &&
            io.fromPreMul.signProd && opSignC) ||
        (notNaN_addZeros && roundingMode_min &&
            (io.fromPreMul.signProd || opSignC)) ||
        (! notNaN_isInfOut && ! notNaN_addZeros &&
             Mux(io.fromPreMul.CIsDominant, CDom_sign, notCDom_sign))
    io.rawOut.sExp := Mux(io.fromPreMul.CIsDominant, CDom_sExp, notCDom_sExp)
    io.rawOut.sig := Mux(io.fromPreMul.CIsDominant, CDom_sig, notCDom_sig)
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------

class MulAddRecFN(expWidth: Int, sigWidth: Int) extends RawModule
{
    override def desiredName = s"MulAddRecFN_e${expWidth}_s${sigWidth}"
    val io = IO(new Bundle {
        val op = Input(Bits(2.W))
        val a = Input(Bits((expWidth + sigWidth + 1).W))
        val b = Input(Bits((expWidth + sigWidth + 1).W))
        val c = Input(Bits((expWidth + sigWidth + 1).W))
        val roundingMode   = Input(UInt(3.W))
        val detectTininess = Input(UInt(1.W))
        val out = Output(Bits((expWidth + sigWidth + 1).W))
        val exceptionFlags = Output(Bits(5.W))
    })

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val mulAddRecFNToRaw_preMul =
        Module(new MulAddRecFNToRaw_preMul(expWidth, sigWidth))
    val mulAddRecFNToRaw_postMul =
        Module(new MulAddRecFNToRaw_postMul(expWidth, sigWidth))

    mulAddRecFNToRaw_preMul.io.op := io.op
    mulAddRecFNToRaw_preMul.io.a  := io.a
    mulAddRecFNToRaw_preMul.io.b  := io.b
    mulAddRecFNToRaw_preMul.io.c  := io.c

    val mulAddResult =
        (mulAddRecFNToRaw_preMul.io.mulAddA *
             mulAddRecFNToRaw_preMul.io.mulAddB) +&
            mulAddRecFNToRaw_preMul.io.mulAddC

    mulAddRecFNToRaw_postMul.io.fromPreMul :=
        mulAddRecFNToRaw_preMul.io.toPostMul
    mulAddRecFNToRaw_postMul.io.mulAddResult := mulAddResult
    mulAddRecFNToRaw_postMul.io.roundingMode := io.roundingMode

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val roundRawFNToRecFN =
        Module(new RoundRawFNToRecFN(expWidth, sigWidth, 0))
    roundRawFNToRecFN.io.invalidExc   := mulAddRecFNToRaw_postMul.io.invalidExc
    roundRawFNToRecFN.io.infiniteExc  := false.B
    roundRawFNToRecFN.io.in           := mulAddRecFNToRaw_postMul.io.rawOut
    roundRawFNToRecFN.io.roundingMode := io.roundingMode
    roundRawFNToRecFN.io.detectTininess := io.detectTininess
    io.out            := roundRawFNToRecFN.io.out
    io.exceptionFlags := roundRawFNToRecFN.io.exceptionFlags
}

