
/*============================================================================

This Chisel source file is part of a pre-release version of the HardFloat IEEE
Floating-Point Arithmetic Package, by John R. Hauser (ported from Verilog to
Chisel by Andrew Waterman).

Copyright 2019, 2020 The Regents of the University of California.  All rights
reserved.

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
class AddRawFN(expWidth: Int, sigWidth: Int) extends RawModule
{
    val io = IO(new Bundle {
        val subOp = Input(Bool())
        val a = Input(new RawFloat(expWidth, sigWidth))
        val b = Input(new RawFloat(expWidth, sigWidth))
        val roundingMode = Input(UInt(3.W))
        val invalidExc = Output(Bool())
        val rawOut = Output(new RawFloat(expWidth, sigWidth + 2))
    })

    val alignDistWidth = log2Ceil(sigWidth)

    val effSignB = io.b.sign ^ io.subOp
    val eqSigns = io.a.sign === effSignB
    val notEqSigns_signZero = io.roundingMode === round_min
    val sDiffExps = io.a.sExp - io.b.sExp
    val modNatAlignDist = Mux(sDiffExps < 0.S, io.b.sExp - io.a.sExp, sDiffExps)(alignDistWidth - 1, 0)
    val isMaxAlign =
        (sDiffExps>>alignDistWidth) =/= 0.S &&
            ((sDiffExps>>alignDistWidth) =/= -1.S || sDiffExps(alignDistWidth - 1, 0) === 0.U)
    val alignDist = Mux(isMaxAlign, ((BigInt(1)<<alignDistWidth) - 1).U, modNatAlignDist)
    val closeSubMags = !eqSigns && !isMaxAlign && (modNatAlignDist <= 1.U)
    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val close_alignedSigA =
        Mux((0.S <= sDiffExps) &&  sDiffExps(0), io.a.sig<<2, 0.U) |
        Mux((0.S <= sDiffExps) && !sDiffExps(0), io.a.sig<<1, 0.U) |
        Mux((sDiffExps < 0.S)                  , io.a.sig,    0.U)
    val close_sSigSum = close_alignedSigA.asSInt - (io.b.sig<<1).asSInt
    val close_sigSum = Mux(close_sSigSum < 0.S, -close_sSigSum, close_sSigSum)(sigWidth + 1, 0)
    val close_adjustedSigSum = close_sigSum<<(sigWidth & 1)
    val close_reduced2SigSum = orReduceBy2(close_adjustedSigSum)
    val close_normDistReduced2 = countLeadingZeros(close_reduced2SigSum)
    val close_nearNormDist = (close_normDistReduced2<<1)(alignDistWidth - 1, 0)
    val close_sigOut = ((close_sigSum<<close_nearNormDist)<<1)(sigWidth + 2, 0)
    val close_totalCancellation = !(close_sigOut((sigWidth + 2), (sigWidth + 1)).orR)
    val close_notTotalCancellation_signOut = io.a.sign ^ (close_sSigSum < 0.S)
    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val far_signOut = Mux(sDiffExps < 0.S, effSignB, io.a.sign)
    val far_sigLarger  = Mux(sDiffExps < 0.S, io.b.sig, io.a.sig)(sigWidth - 1, 0)
    val far_sigSmaller = Mux(sDiffExps < 0.S, io.a.sig, io.b.sig)(sigWidth - 1, 0)
    val far_mainAlignedSigSmaller = (far_sigSmaller<<5)>>alignDist
    val far_reduced4SigSmaller = orReduceBy4(far_sigSmaller<<2)
    val far_roundExtraMask = lowMask(alignDist(alignDistWidth - 1, 2), (sigWidth + 5)/4, 0)
    val far_alignedSigSmaller =
        Cat(far_mainAlignedSigSmaller>>3,
            far_mainAlignedSigSmaller(2, 0).orR || (far_reduced4SigSmaller & far_roundExtraMask).orR)
    val far_subMags = !eqSigns
    val far_negAlignedSigSmaller = Mux(far_subMags, Cat(1.U, ~far_alignedSigSmaller), far_alignedSigSmaller)
    val far_sigSum = (far_sigLarger<<3) + far_negAlignedSigSmaller + far_subMags
    val far_sigOut = Mux(far_subMags, far_sigSum, (far_sigSum>>1) | far_sigSum(0))(sigWidth + 2, 0)
    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val notSigNaN_invalidExc = io.a.isInf && io.b.isInf && !eqSigns
    val notNaN_isInfOut = io.a.isInf || io.b.isInf
    val addZeros = io.a.isZero && io.b.isZero
    val notNaN_specialCase = notNaN_isInfOut || addZeros
    val notNaN_isZeroOut = addZeros || (!notNaN_isInfOut && closeSubMags && close_totalCancellation)
    val notNaN_signOut =
        (eqSigns                      && io.a.sign          ) ||
        (io.a.isInf                   && io.a.sign          ) ||
        (io.b.isInf                   && effSignB           ) ||
        (notNaN_isZeroOut && !eqSigns && notEqSigns_signZero) ||
        (!notNaN_specialCase && closeSubMags && !close_totalCancellation
                                     && close_notTotalCancellation_signOut) ||
        (!notNaN_specialCase && !closeSubMags && far_signOut)
    val common_sExpOut =
        (Mux(closeSubMags || (sDiffExps < 0.S), io.b.sExp, io.a.sExp)
            - Mux(closeSubMags, close_nearNormDist, far_subMags).zext)
    val common_sigOut = Mux(closeSubMags, close_sigOut, far_sigOut)
    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    io.invalidExc := isSigNaNRawFloat(io.a) || isSigNaNRawFloat(io.b) || notSigNaN_invalidExc
    io.rawOut.isInf := notNaN_isInfOut
    io.rawOut.isZero := notNaN_isZeroOut
    io.rawOut.sExp := common_sExpOut
    io.rawOut.isNaN := io.a.isNaN || io.b.isNaN
    io.rawOut.sign := notNaN_signOut
    io.rawOut.sig := common_sigOut
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------

class AddRecFN(expWidth: Int, sigWidth: Int) extends RawModule
{
    val io = IO(new Bundle {
        val subOp = Input(Bool())
        val a = Input(UInt((expWidth + sigWidth + 1).W))
        val b = Input(UInt((expWidth + sigWidth + 1).W))
        val roundingMode = Input(UInt(3.W))
        val detectTininess = Input(Bool())
        val out = Output(UInt((expWidth + sigWidth + 1).W))
        val exceptionFlags = Output(UInt(5.W))
    })

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val addRawFN = Module(new AddRawFN(expWidth, sigWidth))

    addRawFN.io.subOp        := io.subOp
    addRawFN.io.a            := rawFloatFromRecFN(expWidth, sigWidth, io.a)
    addRawFN.io.b            := rawFloatFromRecFN(expWidth, sigWidth, io.b)
    addRawFN.io.roundingMode := io.roundingMode

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val roundRawFNToRecFN =
        Module(new RoundRawFNToRecFN(expWidth, sigWidth, 0))
    roundRawFNToRecFN.io.invalidExc   := addRawFN.io.invalidExc
    roundRawFNToRecFN.io.infiniteExc  := false.B
    roundRawFNToRecFN.io.in           := addRawFN.io.rawOut
    roundRawFNToRecFN.io.roundingMode := io.roundingMode
    roundRawFNToRecFN.io.detectTininess := io.detectTininess
    io.out            := roundRawFNToRecFN.io.out
    io.exceptionFlags := roundRawFNToRecFN.io.exceptionFlags
}

