
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

//*** THIS MODULE HAS NOT BEEN FULLY OPTIMIZED.

package hardfloat

import Chisel._
import consts._

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------

class MulAddRecFN_interIo(expWidth: Int, sigWidth: Int) extends Bundle
{
    val highExpA           = UInt(width = 3)
    val isNaN_isQuietNaNA  = Bool()
    val highExpB           = UInt(width = 3)
    val isNaN_isQuietNaNB  = Bool()
    val signProd           = Bool()
    val isZeroProd         = Bool()
    val opSignC            = Bool()
    val highExpC           = UInt(width = 3)
    val isNaN_isQuietNaNC  = Bool()
    val isCDominant        = Bool()
    val CAlignDist_0       = Bool()
    val CAlignDist         = UInt(width = log2Up(sigWidth * 3 + 2))
    val bit0AlignedNegSigC = Bool()
    val highAlignedNegSigC = UInt(width = sigWidth + 2)
    val sExpSum            = UInt(width = expWidth + 3)
    val roundingMode       = Bits(width = 2)

    override def cloneType = new MulAddRecFN_interIo(expWidth, sigWidth).asInstanceOf[this.type]
}

class MulAddRecFN_preMul_io(expWidth: Int, sigWidth: Int) extends Bundle
{
    val op = Bits(INPUT, 2)
    val a = Bits(INPUT, expWidth + sigWidth + 1)
    val b = Bits(INPUT, expWidth + sigWidth + 1)
    val c = Bits(INPUT, expWidth + sigWidth + 1)
    val roundingMode = Bits(INPUT, 2)
    val mulAddA = UInt(OUTPUT, sigWidth)
    val mulAddB = UInt(OUTPUT, sigWidth)
    val mulAddC = UInt(OUTPUT, sigWidth * 2)
    val toPostMul = new MulAddRecFN_interIo(expWidth, sigWidth).asOutput

    override def cloneType = new MulAddRecFN_preMul_io(expWidth, sigWidth).asInstanceOf[this.type]
}

class MulAddRecFN_postMul_io(expWidth: Int, sigWidth: Int) extends Bundle
{
    val fromPreMul = new MulAddRecFN_interIo(expWidth, sigWidth).asInput
    val mulAddResult = UInt(INPUT, sigWidth * 2 + 1)
    val out = Bits(OUTPUT, expWidth + sigWidth + 1)
    val exceptionFlags = Bits(OUTPUT, 5)
}

class MulAddRecFN_preMul(expWidth: Int, sigWidth: Int) extends Module
{
    val io = new MulAddRecFN_preMul_io(expWidth, sigWidth)

    val sigSumSize = sigWidth * 3 + 3
    val normSize = sigWidth * 2 + 2

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val signA  = io.a(expWidth + sigWidth)
    val expA   = io.a(expWidth + sigWidth - 1, sigWidth - 1)
    val fractA = io.a(sigWidth - 2, 0)
    val isZeroA = (expA(expWidth, expWidth - 2) === UInt(0))
    val sigA = Cat(! isZeroA, fractA)

    val signB  = io.b(expWidth + sigWidth)
    val expB   = io.b(expWidth + sigWidth - 1, sigWidth - 1)
    val fractB = io.b(sigWidth - 2, 0)
    val isZeroB = (expB(expWidth, expWidth - 2) === UInt(0))
    val sigB = Cat(! isZeroB, fractB)

    val opSignC = io.c(expWidth + sigWidth) ^ io.op(0)
    val expC   = io.c(expWidth + sigWidth - 1, sigWidth - 1)
    val fractC = io.c(sigWidth - 2, 0)
    val isZeroC = (expC(expWidth, expWidth - 2) === UInt(0))
    val sigC = Cat(! isZeroC, fractC)

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val signProd = signA ^ signB ^ io.op(1)
    val isZeroProd = isZeroA || isZeroB
    val sExpAlignedProd =
        expA + Cat(Fill(3, ! expB(expWidth)), expB(expWidth - 1, 0)) +
            UInt(sigWidth + 3)

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val doSubMags = signProd ^ opSignC

    val sNatCAlignDist = sExpAlignedProd - expC
    val CAlignDist_floor = isZeroProd || sNatCAlignDist(expWidth + 2)
    val CAlignDist_0 =
        CAlignDist_floor || (sNatCAlignDist(expWidth + 1, 0) === UInt(0))
    val isCDominant =
        ! isZeroC &&
            (CAlignDist_floor ||
                 (sNatCAlignDist(expWidth + 1, 0) < UInt(sigWidth + 1)))
    val CAlignDist =
        Mux(CAlignDist_floor,
            UInt(0),
            Mux(sNatCAlignDist(expWidth + 1, 0) < UInt(sigSumSize - 1),
                sNatCAlignDist(log2Up(sigSumSize) - 1, 0),
                UInt(sigSumSize - 1)
            )
        )
    val sExpSum = Mux(CAlignDist_floor, expC, sExpAlignedProd)
// *** USE `sNatCAlignDist'?:
    var CExtraMask = lowMask(CAlignDist, sigWidth + normSize, normSize)
    val negSigC = Mux(doSubMags, ~sigC, sigC)
// *** FINAL CLIPPING NOT NEEDED?:
    val alignedNegSigC =
        Cat(Cat(doSubMags, negSigC, Fill(normSize, doSubMags)).toSInt>>
                CAlignDist,
            (sigC & CExtraMask).orR ^ doSubMags
        )(sigSumSize - 1, 0)

    io.mulAddA := sigA
    io.mulAddB := sigB
    io.mulAddC := alignedNegSigC(sigWidth * 2, 1)

    io.toPostMul.highExpA           := expA(expWidth, expWidth - 2)
    io.toPostMul.isNaN_isQuietNaNA  := fractA(sigWidth - 2)
    io.toPostMul.highExpB           := expB(expWidth, expWidth - 2)
    io.toPostMul.isNaN_isQuietNaNB  := fractB(sigWidth - 2)
    io.toPostMul.signProd           := signProd
    io.toPostMul.isZeroProd         := isZeroProd
    io.toPostMul.opSignC            := opSignC
    io.toPostMul.highExpC           := expC(expWidth, expWidth - 2)
    io.toPostMul.isNaN_isQuietNaNC  := fractC(sigWidth - 2)
    io.toPostMul.isCDominant        := isCDominant
    io.toPostMul.CAlignDist_0       := CAlignDist_0
    io.toPostMul.CAlignDist         := CAlignDist
    io.toPostMul.bit0AlignedNegSigC := alignedNegSigC(0)
    io.toPostMul.highAlignedNegSigC :=
        alignedNegSigC(sigWidth * 3 + 2, sigWidth * 2 + 1)
    io.toPostMul.sExpSum            := sExpSum
    io.toPostMul.roundingMode       := io.roundingMode
}

object estNormDistPNNegSumS
{
    def apply(a: UInt, b: UInt, n: Int, s: Int) =
        priorityEncode((a ^ b) ^ ~((a & b)<<1), n, s)
}

object estNormDistPNPosSumS
{
    def apply(a: UInt, b: UInt, n: Int, s: Int) =
        priorityEncode((a ^ b) ^ (a | b)<<1, n, s)
}

class MulAddRecFN_postMul(expWidth: Int, sigWidth: Int) extends Module
{
    val io = new MulAddRecFN_postMul_io(expWidth, sigWidth)

    val sigSumSize = sigWidth * 3 + 3
    val normSize = sigWidth * 2 + 2
    val logNormSize = log2Up(normSize)
    val firstNormUnit = 1<<(logNormSize - 2)
    val minNormExp = (1<<(expWidth - 1)) + 2
    val minExp = minNormExp - sigWidth + 1

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val isZeroA    = (io.fromPreMul.highExpA === UInt(0))
    val isSpecialA = (io.fromPreMul.highExpA(2, 1) === UInt(3))
    val isInfA = isSpecialA && ! io.fromPreMul.highExpA(0)
    val isNaNA = isSpecialA &&   io.fromPreMul.highExpA(0)
    val isSigNaNA = isNaNA && ! io.fromPreMul.isNaN_isQuietNaNA;

    val isZeroB    = (io.fromPreMul.highExpB === UInt(0))
    val isSpecialB = (io.fromPreMul.highExpB(2, 1) === UInt(3))
    val isInfB = isSpecialB && ! io.fromPreMul.highExpB(0)
    val isNaNB = isSpecialB &&   io.fromPreMul.highExpB(0)
    val isSigNaNB = isNaNB && ! io.fromPreMul.isNaN_isQuietNaNB;

    val isZeroC    = (io.fromPreMul.highExpC === UInt(0))
    val isSpecialC = (io.fromPreMul.highExpC(2, 1) === UInt(3))
    val isInfC = isSpecialC && ! io.fromPreMul.highExpC(0)
    val isNaNC = isSpecialC &&   io.fromPreMul.highExpC(0)
    val isSigNaNC = isNaNC && ! io.fromPreMul.isNaN_isQuietNaNC;

    val roundingMode_nearest_even =
        (io.fromPreMul.roundingMode === round_nearest_even)
    val roundingMode_minMag = (io.fromPreMul.roundingMode === round_minMag)
    val roundingMode_min    = (io.fromPreMul.roundingMode === round_min)
    val roundingMode_max    = (io.fromPreMul.roundingMode === round_max)

    val signZeroNotEqOpSigns = Mux(roundingMode_min, Bool(true), Bool(false))
    val doSubMags = io.fromPreMul.signProd ^ io.fromPreMul.opSignC

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val sigSum =
        Cat(Mux(io.mulAddResult(sigWidth * 2),
                io.fromPreMul.highAlignedNegSigC + UInt(1),
                io.fromPreMul.highAlignedNegSigC
               ),
            io.mulAddResult(sigWidth * 2 - 1, 0),
            io.fromPreMul.bit0AlignedNegSigC
        )

// *** TEMPORARY:
    val estNormPos_dist =
        estNormDistPNPosSumS(
            UInt(0, normSize), sigSum(normSize, 1), sigWidth, normSize)
    val estNormNeg_dist = estNormPos_dist

    val firstReduceSigSum =
        Cat(sigSum(
                normSize - firstNormUnit - 1, normSize - firstNormUnit * 2
            ).orR,
            sigSum(normSize - firstNormUnit * 2 - 1, 0).orR
        )
    val complSigSum = ~sigSum
    val firstReduceComplSigSum =
        Cat(complSigSum(
                normSize - firstNormUnit - 1, normSize - firstNormUnit * 2
            ).orR,
            complSigSum(normSize - firstNormUnit * 2 - 1, 0).orR
        )
//*** USE RESULT OF `CAlignDest - 1' TO TEST FOR ZERO?
    val CDom_estNormDist =
        Mux(io.fromPreMul.CAlignDist_0 || doSubMags,
            io.fromPreMul.CAlignDist,
            (io.fromPreMul.CAlignDist - UInt(1))(log2Up(sigWidth) - 1, 0)
        )
    val CDom_firstNormAbsSigSum =
        Mux(! doSubMags && ! CDom_estNormDist(logNormSize - 2),
            Cat(sigSum(sigSumSize - 1, normSize - firstNormUnit),
                firstReduceSigSum.orR
            ),
            UInt(0)
        ) |
        Mux(! doSubMags && CDom_estNormDist(logNormSize - 2),
            Cat(sigSum(
                    sigSumSize - firstNormUnit - 1,
                    normSize - firstNormUnit * 2
                ),
                firstReduceSigSum(0)
            ),
            UInt(0)
        ) |
        Mux(doSubMags && ! CDom_estNormDist(logNormSize - 2),
            Cat(complSigSum(sigSumSize - 1, normSize - firstNormUnit),
                firstReduceComplSigSum.orR
            ),
            UInt(0)
        ) |
        Mux(doSubMags && CDom_estNormDist(logNormSize - 2),
            Cat(complSigSum(
                    sigSumSize - firstNormUnit - 1,
                    normSize - firstNormUnit * 2
                ),
                firstReduceComplSigSum(0)
            ),
            UInt(0)
        )
    //------------------------------------------------------------------------
    // (For this case, bits above `sigSum(normSize)' are never interesting.
    // Also, if there is any significant cancellation, then `sigSum(0)' must
    // equal `doSubMags'.)
    //------------------------------------------------------------------------
    val notCDom_pos_firstNormAbsSigSum = {
        var t1 =
            Cat(sigSum(normSize, normSize - firstNormUnit * 2),
                Mux(doSubMags,
                    ! firstReduceComplSigSum(0),
                    firstReduceSigSum(0)
                )
            )
        var t2 = sigSum(sigSumSize - firstNormUnit * 2 - 1, 1)
        if (firstNormUnit * 5 + 1 < sigSumSize) {
            t1 = Mux(estNormPos_dist(logNormSize - 3),
                     t1,
                     Cat(sigSum(sigSumSize - firstNormUnit * 5 - 1, 1),
                         Fill(firstNormUnit * 6 - sigWidth * 2, doSubMags)
                     )
                 )
        }
        if (2 < normSize - firstNormUnit * 3) {
            t2 = Cat(sigSum(
                         sigSumSize - firstNormUnit * 2 - 1,
                         normSize - firstNormUnit * 3
                      ),
                      Mux(doSubMags,
                          (complSigSum(normSize - firstNormUnit * 3 - 1, 1) ===
                               UInt(0)),
                          (sigSum(normSize - firstNormUnit * 3 - 1, 1) =/=
                               UInt(0))
                      )
                 )
        } else if (sigWidth * 2 < firstNormUnit * 3) {
            t2 = Cat(t2, Fill(firstNormUnit * 3 - sigWidth * 2, doSubMags))
        }
        Mux(estNormPos_dist(logNormSize - 1),
             Mux(estNormPos_dist(logNormSize - 2),
                 Cat(sigSum(sigSumSize - firstNormUnit * 3 - 1, 1),
                     Fill(firstNormUnit * 4 - sigWidth * 2, doSubMags)
                 ),
                 t2
             ),
             Mux(estNormPos_dist(logNormSize - 2),
                 t1,
                 Cat(sigSum(sigSumSize - firstNormUnit * 4 - 1, 1),
                     Fill(firstNormUnit * 5 - sigWidth * 2, doSubMags)
                 )
             )
        )
    }
    //------------------------------------------------------------------------
    // (For this case, bits above `complSigSum(normSize - 1)' are never
    // interesting.  Also, if there is any significant cancellation, then
    // `complSigSum(0)' must be zero.)
    //------------------------------------------------------------------------
    val notCDom_neg_cFirstNormAbsSigSum = {
        var t1 =
            Cat(complSigSum(normSize - 1, normSize - firstNormUnit * 2),
                firstReduceComplSigSum(0)
            )
        var t2 = complSigSum(sigSumSize - firstNormUnit * 2 - 1, 1)
        if (firstNormUnit * 5 < sigSumSize) {
            t1 = Mux(estNormNeg_dist(logNormSize - 3),
                     t1,
                     complSigSum(sigSumSize - firstNormUnit * 5, 1)<<
                         (firstNormUnit * 6 - sigWidth * 2)
                 )
        }
        if (2 < normSize - firstNormUnit * 3) {
            t2 = Cat(complSigSum(
                         sigSumSize - firstNormUnit * 2,
                         normSize - firstNormUnit * 3
                     ),
                     complSigSum(normSize - firstNormUnit * 3 - 1, 1).orR
                 )
        }
        Mux(estNormNeg_dist(logNormSize - 1),
             Mux(estNormNeg_dist(logNormSize - 2),
                 complSigSum(sigSumSize - firstNormUnit * 3, 1)<<
                     (firstNormUnit * 4 - sigWidth * 2),
                 t2
             ),
             Mux(estNormNeg_dist(logNormSize - 2),
                 t1,
                 complSigSum(sigSumSize - firstNormUnit * 4, 1)<<
                     (firstNormUnit * 5 - sigWidth * 2)
             )
        )
    }
    val notCDom_signSigSum = sigSum(normSize + 1)
    val doNegSignSum =
        Mux(io.fromPreMul.isCDominant,
            doSubMags && ! isZeroC,
            notCDom_signSigSum
        )
    val estNormDist =
        Mux(io.fromPreMul.isCDominant,
            CDom_estNormDist,
            Mux(notCDom_signSigSum, estNormNeg_dist, estNormPos_dist)
        )
    //------------------------------------------------------------------------
    // (??? Odd mux gives the best DC synthesis QoR.)
    //------------------------------------------------------------------------
    val cFirstNormAbsSigSum =
        Mux(notCDom_signSigSum,
            Mux(io.fromPreMul.isCDominant,
                CDom_firstNormAbsSigSum,
                notCDom_neg_cFirstNormAbsSigSum
            ),
            Mux(io.fromPreMul.isCDominant,
                CDom_firstNormAbsSigSum,
                notCDom_pos_firstNormAbsSigSum
            )
        )
    val doIncrSig =
        ! io.fromPreMul.isCDominant && ! notCDom_signSigSum && doSubMags
    val estNormDist_5 = estNormDist(logNormSize - 3, 0)
    val normTo2ShiftDist = ~estNormDist_5
    val absSigSumExtraMask =
        Cat(lowMask(normTo2ShiftDist, firstNormUnit - 1, 0), Bool(true))
    val sigX3 =
        Cat(cFirstNormAbsSigSum(sigWidth + firstNormUnit + 2, 1)>>
                normTo2ShiftDist,
            Mux(doIncrSig,
                ((~cFirstNormAbsSigSum(firstNormUnit - 1, 0) &
                      absSigSumExtraMask) ===
                     UInt(0)),
                ((cFirstNormAbsSigSum(firstNormUnit - 1, 0) &
                      absSigSumExtraMask) =/=
                     UInt(0))
            )
        )(sigWidth + 3, 0)

    val sigX3Shift1 = (sigX3(sigWidth + 3, sigWidth + 2) === UInt(0))
    val sExpX3 = io.fromPreMul.sExpSum - estNormDist

    val isZeroY = (sigX3(sigWidth + 3, sigWidth + 1) === UInt(0))
//    val signY = ! isZeroY && (io.fromPreMul.signProd ^ doNegSignSum)
    val signY =
        Mux(isZeroY,
            signZeroNotEqOpSigns,
            io.fromPreMul.signProd ^ doNegSignSum
        )
    val sExpX3_13 = sExpX3(expWidth + 1, 0)
    val roundMask =
        Fill(sigWidth + 3, sExpX3(expWidth + 2)) |
            Cat(lowMask(sExpX3_13, minNormExp - sigWidth, minNormExp + 1) |
                    sigX3(sigWidth + 2),
                UInt(3, 2)
            )

    val roundPosMask = ~(roundMask>>1) & roundMask
    val roundPosBit = (sigX3 & roundPosMask).orR
    val anyRoundExtra = (( sigX3 & roundMask>>1) =/=  UInt(0))
    val allRoundExtra = ((~sigX3 & roundMask>>1) === UInt(0))
    val anyRound = roundPosBit || anyRoundExtra
    val allRound = roundPosBit && allRoundExtra
    val roundDirectUp = Mux(signY, roundingMode_min, roundingMode_max)
    val roundUp =
        (! doIncrSig && roundingMode_nearest_even &&
                                               roundPosBit && anyRoundExtra) ||
        (! doIncrSig && roundDirectUp           && anyRound   ) ||
        (doIncrSig                              && allRound   ) ||
        (doIncrSig && roundingMode_nearest_even && roundPosBit) ||
        (doIncrSig && roundDirectUp             && Bool(true) )
    val roundEven =
        Mux(doIncrSig,
            roundingMode_nearest_even && ! roundPosBit &&   allRoundExtra,
            roundingMode_nearest_even &&   roundPosBit && ! anyRoundExtra
        )
    val roundInexact = Mux(doIncrSig, ! allRound, anyRound)
    val roundUp_sigY3 =
        (((sigX3 | roundMask)>>2) + UInt(1))(sigWidth + 1, 0)
    val sigY3 =
        Mux(! roundUp && ! roundEven, (sigX3 & ~roundMask)>>2,       UInt(0)) |
        Mux(roundUp,                roundUp_sigY3,                   UInt(0)) |
        Mux(roundEven,              roundUp_sigY3 & ~(roundMask>>1), UInt(0))
//*** HANDLE DIFFERENTLY?  (NEED TO ACCOUNT FOR ROUND-EVEN ZEROING MSB.)
    val sExpY =
        Mux(sigY3(sigWidth + 1), sExpX3 + UInt(1), UInt(0)) |
        Mux(sigY3(sigWidth),     sExpX3,           UInt(0)) |
        Mux((sigY3(sigWidth + 1, sigWidth) === UInt(0)),
            sExpX3 - UInt(1),
            UInt(0)
        )
    val expY = sExpY(expWidth, 0)
    val fractY =
        Mux(sigX3Shift1, sigY3(sigWidth - 2, 0), sigY3(sigWidth - 1, 1))

    val overflowY = (sExpY(expWidth + 1, expWidth - 1) === UInt(3))
//*** HANDLE DIFFERENTLY?  (NEED TO ACCOUNT FOR ROUND-EVEN ZEROING MSB.)
    val totalUnderflowY =
        ! isZeroY &&
            (sExpY(expWidth + 1) || (sExpY(expWidth, 0) < UInt(minExp)))
    val underflowY =
        roundInexact &&
            (sExpX3(expWidth + 2) ||
                 (sExpX3_13 <=
                      Mux(sigX3Shift1, UInt(minNormExp), UInt(minNormExp - 1)))
            )
    val inexactY = roundInexact

    val roundMagUp =
        (roundingMode_min && signY) || (roundingMode_max && ! signY)
    val overflowY_roundMagUp = roundingMode_nearest_even || roundMagUp

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val mulSpecial = isSpecialA || isSpecialB
    val addSpecial = mulSpecial || isSpecialC
    val notSpecial_addZeros = io.fromPreMul.isZeroProd && isZeroC
    val commonCase = ! addSpecial && ! notSpecial_addZeros

    val notSigNaN_invalid =
        (isInfA && isZeroB) || (isZeroA && isInfB) ||
            (! isNaNA && ! isNaNB && (isInfA || isInfB) && isInfC && doSubMags)
    val invalid = isSigNaNA || isSigNaNB || isSigNaNC || notSigNaN_invalid
    val overflow  = commonCase && overflowY
    val underflow = commonCase && underflowY
    val inexact = overflow || (commonCase && inexactY)

    val notSpecial_isZeroOut =
        notSpecial_addZeros || isZeroY || totalUnderflowY
    val pegMinFiniteMagOut = commonCase && totalUnderflowY && roundMagUp
    val pegMaxFiniteMagOut = overflow && ! overflowY_roundMagUp
    val notNaN_isInfOut =
        isInfA || isInfB || isInfC || (overflow && overflowY_roundMagUp)
    val isNaNOut = isNaNA || isNaNB || isNaNC || notSigNaN_invalid

    val uncommonCaseSignOut =
        (! doSubMags                              && io.fromPreMul.opSignC ) ||
        (mulSpecial && ! isSpecialC               && io.fromPreMul.signProd) ||
        (! mulSpecial && isSpecialC               && io.fromPreMul.opSignC ) ||
        (! mulSpecial && notSpecial_addZeros && doSubMags &&
                                                     signZeroNotEqOpSigns  )
    val signOut = (! isNaNOut && uncommonCaseSignOut) || (commonCase && signY)
    val expOut =
        (expY &
             ~Mux(notSpecial_isZeroOut,
                  UInt(7<<(expWidth - 2)),
                  UInt(0, expWidth + 1)
              ) &
             ~Mux(pegMinFiniteMagOut,
                  ~UInt(minExp, expWidth + 1),
                  UInt(0, expWidth + 1)
              ) &
             ~Mux(pegMaxFiniteMagOut,
                  UInt(1<<(expWidth - 1), expWidth + 1),
                  UInt(0, expWidth + 1)
              ) &
             ~Mux(notNaN_isInfOut,
                  UInt(1<<(expWidth - 2)),
                  UInt(0, expWidth + 1)
              )) |
            Mux(pegMinFiniteMagOut, UInt(minExp), UInt(0, expWidth + 1)) |
            Mux(pegMaxFiniteMagOut,
                UInt((6<<(expWidth - 2)) - 1),
                UInt(0, expWidth + 1)
            ) |
            Mux(notNaN_isInfOut,
                UInt(6<<(expWidth - 2)),
                UInt(0, expWidth + 1)
            ) |
            Mux(isNaNOut, UInt(7<<(expWidth - 2)), UInt(0, expWidth + 1))
    val fractOut =
        Mux((totalUnderflowY && roundMagUp) || isNaNOut,
            Mux(isNaNOut, UInt(1)<<(sigWidth - 2), UInt(0)),
            fractY
        ) |
        Fill(sigWidth - 1, pegMaxFiniteMagOut)

    io.out := Cat(signOut, expOut, fractOut)
    io.exceptionFlags :=
        Cat(invalid, Bool(false), overflow, underflow, inexact)
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------

class MulAddRecFN_io(expWidth: Int, sigWidth: Int) extends Bundle
{
    val op = Bits(INPUT, 2)
    val a = Bits(INPUT, expWidth + sigWidth + 1)
    val b = Bits(INPUT, expWidth + sigWidth + 1)
    val c = Bits(INPUT, expWidth + sigWidth + 1)
    val roundingMode = Bits(INPUT, 2)
    val out = Bits(OUTPUT, expWidth + sigWidth + 1)
    val exceptionFlags = Bits(OUTPUT, 5)
}

class MulAddRecFN(expWidth: Int, sigWidth: Int) extends Module
{
    val io = new MulAddRecFN_io(expWidth, sigWidth)

    val mulAddRecFN_preMul =
        Module(new MulAddRecFN_preMul(expWidth, sigWidth))
    val mulAddRecFN_postMul =
        Module(new MulAddRecFN_postMul(expWidth, sigWidth))

    mulAddRecFN_preMul.io.op := io.op
    mulAddRecFN_preMul.io.a  := io.a
    mulAddRecFN_preMul.io.b  := io.b
    mulAddRecFN_preMul.io.c  := io.c
    mulAddRecFN_preMul.io.roundingMode := io.roundingMode

    mulAddRecFN_postMul.io.fromPreMul := mulAddRecFN_preMul.io.toPostMul
    mulAddRecFN_postMul.io.mulAddResult :=
        mulAddRecFN_preMul.io.mulAddA * mulAddRecFN_preMul.io.mulAddB +
            Cat(UInt(0, 1), mulAddRecFN_preMul.io.mulAddC)

    io.out := mulAddRecFN_postMul.io.out
    io.exceptionFlags := mulAddRecFN_postMul.io.exceptionFlags
}

