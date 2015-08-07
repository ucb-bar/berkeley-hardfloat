// See LICENSE for license details.

//*** THIS MODULE HAS NOT BEEN FULLY OPTIMIZED.
//*** DO THIS ANOTHER WAY?

package hardfloat

import Chisel._
import Chisel.ImplicitConversions._
import consts._

object MaskOnes
{
  def apply(in: UInt, start: Int, length: Int): UInt = {
    val top = 1 << in.getWidth
    val shift = SInt(BigInt(-1) << top) >> in
    Reverse(shift(top-1-start,top-length-start))
  }
}

object estNormDistPNNegSumS
{
  def priorityEncode(key: UInt, n: Int, s: Int) =
    PriorityMux((0 until s).map(i => (key(s-1-i), UInt(n+i, log2Up(n+s-1)))))

  def apply(a: UInt, b: UInt, n: Int, s: Int) =
    priorityEncode((a ^ b) ^ ~((a & b) << 1), n, s)
}

object estNormDistPNPosSumS
{
  def apply(a: UInt, b: UInt, n: Int, s: Int) =
    estNormDistPNNegSumS.priorityEncode((a ^ b) ^ ((a | b) << 1), n, s)
}

class mulAddSubRecodedFloatN_io(sigWidth: Int, expWidth: Int) extends Bundle {
  val op = UInt(INPUT, 2)
  val a = UInt(INPUT, expWidth+sigWidth+1)
  val b = UInt(INPUT, expWidth+sigWidth+1)
  val c = UInt(INPUT, expWidth+sigWidth+1)
  val roundingMode = UInt(INPUT, 2)
  val out = UInt(OUTPUT, expWidth+sigWidth+1)
  val exceptionFlags = UInt(OUTPUT, 5)
}

class mulAddSubRecodedFloatN(sigWidth: Int, expWidth: Int, speed: Boolean = false) extends Module {
  val io = new mulAddSubRecodedFloatN_io(sigWidth, expWidth)

  val sigSumSize = (sigWidth+2)*3
  val normSize = (sigWidth+2)*2
  val logNormSize = log2Up(normSize)
  val firstNormUnit = 1 << logNormSize-2
  val minNormExp = (1 << expWidth-2) + 2
  val minExp = minNormExp - sigWidth

  val signA  = io.a(expWidth+sigWidth)
  val expA   = io.a(expWidth+sigWidth-1, sigWidth)
  val fractA = io.a(sigWidth-1, 0)
  val isZeroA = expA(expWidth-1, expWidth-3) === UInt(0)
  val isSpecialA = expA(expWidth-1, expWidth-2) === UInt(3)
  val isInfA = isSpecialA && !expA(expWidth-3)
  val isNaNA = isSpecialA && expA(expWidth-3)
  val isSigNaNA = isNaNA && !fractA(sigWidth-1)
  val sigA = Cat(!isZeroA, fractA)

  val signB  = io.b(expWidth+sigWidth)
  val expB   = io.b(expWidth+sigWidth-1, sigWidth)
  val fractB = io.b(sigWidth-1, 0)
  val isZeroB = expB(expWidth-1, expWidth-3) === UInt(0)
  val isSpecialB = expB(expWidth-1, expWidth-2) === UInt(3)
  val isInfB = isSpecialB && !expB(expWidth-3)
  val isNaNB = isSpecialB && expB(expWidth-3)
  val isSigNaNB = isNaNB && !fractB(sigWidth-1)
  val sigB = Cat(!isZeroB, fractB)

  val opSignC  = io.c(expWidth+sigWidth) ^ io.op(0)
  val expC   = io.c(expWidth+sigWidth-1, sigWidth)
  val fractC = io.c(sigWidth-1, 0)
  val isZeroC = expC(expWidth-1, expWidth-3) === UInt(0)
  val isSpecialC = expC(expWidth-1, expWidth-2) === UInt(3)
  val isInfC = isSpecialC & !expC(expWidth-3)
  val isNaNC = isSpecialC &  expC(expWidth-3)
  val isSigNaNC = isNaNC & !fractC(sigWidth-1)
  val sigC = Cat(!isZeroC, fractC)

  val roundingMode_nearest_even = io.roundingMode === round_nearest_even
  val roundingMode_minMag       = io.roundingMode === round_minMag
  val roundingMode_min          = io.roundingMode === round_min
  val roundingMode_max          = io.roundingMode === round_max

  //------------------------------------------------------------------------
  //------------------------------------------------------------------------
  val signProd = signA ^ signB ^ io.op(1)
  val isZeroProd = isZeroA || isZeroB
  val sExpAlignedProd = Cat(Fill(3, !expB(expWidth-1)), expB(expWidth-2, 0)) + expA + UInt(sigWidth+4)

  //------------------------------------------------------------------------
  //------------------------------------------------------------------------
  val doSubMags = signProd ^ opSignC

  val sNatCAlignDist = sExpAlignedProd - expC
  val CAlignDist_floor = isZeroProd || sNatCAlignDist(expWidth+1)
  val CAlignDist_0 = CAlignDist_floor || sNatCAlignDist(expWidth, 0) === UInt(0)
  val isCDominant = !isZeroC && (CAlignDist_floor || sNatCAlignDist(expWidth, 0) < UInt(sigWidth+2))
  val CAlignDist =
        Mux(CAlignDist_floor, UInt(0),
        Mux(sNatCAlignDist(expWidth, 0) < UInt(sigSumSize-1), sNatCAlignDist,
        UInt(sigSumSize-1)))(log2Up(sigSumSize)-1, 0)
  val sExpSum = Mux(CAlignDist_floor, expC, sExpAlignedProd)

// *** USE `sNatCAlignDist'?
  var CExtraMask = MaskOnes(CAlignDist, normSize, sigWidth+1)
  val negSigC = Mux(doSubMags, ~sigC, sigC)
  val alignedNegSigC =
      Cat(Cat(doSubMags, negSigC, Fill(normSize, doSubMags)).toSInt >> CAlignDist,
       ((sigC & CExtraMask) != UInt(0)) ^ doSubMags)(sigSumSize-1, 0)

  val (sigSum, estNormPos_dist, estNormNeg_dist) =
    if (speed) {
      val sigPartialProd = RedundantUInt.fromProduct(sigA, sigB)
      val sigPartialSum = (sigPartialProd << 1) + alignedNegSigC
      val sigSum = (sigPartialSum.toUInt)(sigSumSize-1,0)

      val estNorm_a = sigPartialSum.right(normSize, 1)
      val estNorm_b = sigPartialSum.left(normSize, 1)

      (sigSum,
        estNormDistPNPosSumS(estNorm_a, estNorm_b, sigWidth+1, normSize),
        estNormDistPNNegSumS(estNorm_a, estNorm_b, sigWidth+1, normSize))
    } else {
      val sigSum = ((sigA * sigB) << 1) + alignedNegSigC
      val dist = estNormDistPNPosSumS(UInt(0, normSize), sigSum(normSize, 1), sigWidth+1, normSize)
      (sigSum, dist, dist)
    }

  val firstReduceSigSum = Cat(sigSum(normSize-firstNormUnit-1, normSize-firstNormUnit*2) != UInt(0), sigSum(normSize-firstNormUnit*2-1, 0) != UInt(0))
  val notSigSum = ~ sigSum
  val firstReduceNotSigSum = Cat(( notSigSum(normSize-firstNormUnit-1, normSize-firstNormUnit*2) != UInt(0) ), ( notSigSum(normSize-firstNormUnit*2-1, 0) != UInt(0) ))
//*** USE RESULT OF `CAlignDest - 1' TO TEST FOR ZERO?
  val CDom_estNormDist =
      Mux(CAlignDist_0 | doSubMags, CAlignDist, (CAlignDist - UInt(1))(log2Up(sigWidth+1)-1, 0))
  val CDom_firstNormAbsSigSum =
      (((~ doSubMags & ~ CDom_estNormDist(logNormSize-2)).toSInt &
        Cat(sigSum(sigSumSize-1, normSize-firstNormUnit), firstReduceSigSum != UInt(0)).toSInt) |
      ((~ doSubMags & CDom_estNormDist(logNormSize-2)).toSInt &
        Cat(sigSum(sigSumSize-firstNormUnit-1, normSize-firstNormUnit*2), firstReduceSigSum(0)).toSInt) |
      ((doSubMags & ~ CDom_estNormDist(logNormSize-2)).toSInt &
        Cat(notSigSum(sigSumSize-1, normSize-firstNormUnit), firstReduceNotSigSum != UInt(0)).toSInt) |
      ((doSubMags & CDom_estNormDist(logNormSize-2)).toSInt &
        Cat(notSigSum(sigSumSize-firstNormUnit-1, normSize-firstNormUnit*2), firstReduceNotSigSum(0)).toSInt)).toUInt
  //------------------------------------------------------------------------
  // (For this case, bits above `sigSum(normSize)' are never interesting.  Also,
  // if there is any significant cancellation, then `sigSum(0)' must equal
  // `doSubMags'.)
  //------------------------------------------------------------------------
  val notCDom_pos_firstNormAbsSigSum = {
    var t1 = Cat(sigSum(normSize, normSize-firstNormUnit*2), Mux(doSubMags, ~ firstReduceNotSigSum(0), firstReduceSigSum(0)))
    var t2 = sigSum(sigSumSize-firstNormUnit*2-1,1)
    if (firstNormUnit*5+1 < sigSumSize)
      t1 = Mux(estNormPos_dist(logNormSize-3), t1, Cat(sigSum(sigSumSize-firstNormUnit*5-1,1), Fill(firstNormUnit*6-(sigWidth+1)*2, doSubMags)))
    if (2 < (normSize-firstNormUnit*3))
      t2 = Cat(sigSum(sigSumSize-firstNormUnit*2-1, normSize-firstNormUnit*3), Mux(doSubMags, notSigSum(normSize-firstNormUnit*3-1,1) === UInt(0), sigSum(normSize-firstNormUnit*3-1,1) != UInt(0)))
    else if (firstNormUnit*3 > (sigWidth+1)*2)
      t2 = Cat(t2, Fill(firstNormUnit*3-(sigWidth+1)*2, doSubMags))

    Mux(estNormPos_dist(logNormSize-1),
      Mux(estNormPos_dist(logNormSize-2), Cat(sigSum(sigSumSize-firstNormUnit*3-1,1), Fill(firstNormUnit*4-(sigWidth+1)*2, doSubMags)), t2),
      Mux(estNormPos_dist(logNormSize-2), t1, Cat(sigSum(sigSumSize-firstNormUnit*4-1,1), Fill(firstNormUnit*5-(sigWidth+1)*2, doSubMags))))
  }
  //------------------------------------------------------------------------
  // (For this case, bits above `notSigSum(normSize-1)' are never interesting.
  // Also, if there is any significant cancellation, then `notSigSum(0)' must
  // be zero.)
  //------------------------------------------------------------------------
  val notCDom_neg_cFirstNormAbsSigSum = {
    var t1 = Cat(notSigSum(normSize-1,normSize-firstNormUnit*2), firstReduceNotSigSum(0))
    var t2 = notSigSum(sigSumSize-firstNormUnit*2-1,1)
    if (firstNormUnit*5 < sigSumSize)
      t1 = Mux(estNormNeg_dist(logNormSize-3), t1, notSigSum(sigSumSize-firstNormUnit*5,1) << (firstNormUnit*6-(sigWidth+1)*2))
    if (2 < (normSize-firstNormUnit*3))
      t2 = Cat(notSigSum(sigSumSize-firstNormUnit*2,normSize-firstNormUnit*3), notSigSum(normSize-firstNormUnit*3-1,1) != UInt(0))

    Mux(estNormNeg_dist(logNormSize-1),
      Mux(estNormNeg_dist(logNormSize-2), notSigSum(sigSumSize-firstNormUnit*3,1) << (firstNormUnit*4-(sigWidth+1)*2), t2),
      Mux(estNormNeg_dist(logNormSize-2), t1, notSigSum(sigSumSize-firstNormUnit*4,1) << (firstNormUnit*5-(sigWidth+1)*2)))
  }
  val notCDom_signSigSum = sigSum(normSize+1)
  val doNegSignSum =
      Mux(isCDominant, doSubMags & ~ isZeroC, notCDom_signSigSum)
  val estNormDist =
    Mux(isCDominant, CDom_estNormDist,
    Mux(notCDom_signSigSum, estNormNeg_dist,
    estNormPos_dist))
  val cFirstNormAbsSigSum = // ??? odd mux gives the best DC synthesis QoR
    Mux(notCDom_signSigSum,
      Mux(isCDominant, CDom_firstNormAbsSigSum, notCDom_neg_cFirstNormAbsSigSum),
      Mux(isCDominant, CDom_firstNormAbsSigSum, notCDom_pos_firstNormAbsSigSum))
  val doIncrSig = ~ isCDominant & ~ notCDom_signSigSum & doSubMags
  val estNormDist_5 = estNormDist(logNormSize-3, 0).toUInt
  val normTo2ShiftDist = ~ estNormDist_5
  val absSigSumExtraMask = Cat(MaskOnes(normTo2ShiftDist, 0, firstNormUnit-1), Bool(true))
  val sigX3 =
      Cat(cFirstNormAbsSigSum(sigWidth+firstNormUnit+3,1) >> normTo2ShiftDist,
       Mux(doIncrSig, (~cFirstNormAbsSigSum(firstNormUnit-1,0) & absSigSumExtraMask) === UInt(0),
        (cFirstNormAbsSigSum(firstNormUnit-1,0) & absSigSumExtraMask) != UInt(0)))(sigWidth+4, 0)

  val sigX3Shift1 = sigX3(sigWidth+4,sigWidth+3) === UInt(0)
  val sExpX3 = sExpSum - estNormDist

  val isZeroY = sigX3(sigWidth+4,sigWidth+2) === UInt(0)
  val signY = ~isZeroY & (signProd ^ doNegSignSum)
  val sExpX3_13 = sExpX3(expWidth, 0)
  val roundMask = Fill(sigWidth+4, sExpX3(expWidth+1)) | Cat(MaskOnes(~sExpX3_13, (1 << expWidth+1)-2 - minNormExp, sigWidth+2) | sigX3(sigWidth+3), UInt(3))

  val roundPosMask = ~(roundMask >> 1) & roundMask
  val roundPosBit = (sigX3 & roundPosMask) != UInt(0)
  val anyRoundExtra = (sigX3 & (roundMask >> 1)) != UInt(0)
  val allRoundExtra = (~sigX3 & (roundMask >> 1)) === UInt(0)
  val anyRound = roundPosBit | anyRoundExtra
  val allRound = roundPosBit & allRoundExtra
  val roundDirectUp = Mux(signY, roundingMode_min, roundingMode_max)
  val roundUp =
        ( ~ doIncrSig & roundingMode_nearest_even &
                        roundPosBit & anyRoundExtra ) |
        ( ~ doIncrSig & roundDirectUp             & anyRound    ) |
        (   doIncrSig                             & allRound    ) |
        (   doIncrSig & roundingMode_nearest_even & roundPosBit ) |
        (   doIncrSig & roundDirectUp )
  val roundEven =
      Mux(doIncrSig,
          roundingMode_nearest_even & ~ roundPosBit &   allRoundExtra,
          roundingMode_nearest_even &   roundPosBit & ~ anyRoundExtra)
  val roundInexact = Mux(doIncrSig, ~ allRound, anyRound)
  val roundUp_sigY3 = (((sigX3 | roundMask) >> 2) + UInt(1))(sigWidth+2,0)
  val sigY3 =
    Mux(~roundUp & ~roundEven, (sigX3 & ~roundMask) >> 2, UInt(0)) |
    Mux(roundUp, roundUp_sigY3, UInt(0)) |
    Mux(roundEven, roundUp_sigY3 & ~(roundMask >> 1), UInt(0))
//*** HANDLE DIFFERENTLY?  (NEED TO ACCOUNT FOR ROUND-EVEN ZEROING MSB.)
  val sExpY =
    Mux(sigY3(sigWidth+2), sExpX3 + UInt(1), UInt(0)) |
    Mux(sigY3(sigWidth+1), sExpX3, UInt(0)) |
    Mux(sigY3(sigWidth+2,sigWidth+1) === UInt(0), sExpX3 - UInt(1), UInt(0))
  val expY = sExpY(expWidth-1, 0)
  val fractY = Mux(sigX3Shift1, sigY3(sigWidth-1, 0), sigY3(sigWidth, 1))

  val overflowY = sExpY(expWidth, expWidth-2) === UInt(3)
//*** HANDLE DIFFERENTLY?  (NEED TO ACCOUNT FOR ROUND-EVEN ZEROING MSB.)
  val totalUnderflowY = sExpY(expWidth) | sExpY(expWidth-1, 0) < UInt(minExp)
  val underflowY = roundInexact && (sExpX3(expWidth+1) || sExpX3_13 <= Mux(sigX3Shift1, UInt(minNormExp), UInt(minNormExp-1)))
  val inexactY = roundInexact

  val overflowY_roundMagUp =
      roundingMode_nearest_even | ( roundingMode_min & signY ) |
          ( roundingMode_max & ~ signY )

  //------------------------------------------------------------------------
  //------------------------------------------------------------------------
  val mulSpecial = isSpecialA | isSpecialB
  val addSpecial = mulSpecial | isSpecialC
  val notSpecial_addZeros = isZeroProd & isZeroC
  val commonCase = ~ addSpecial & ~ notSpecial_addZeros

  val notSigNaN_invalid =
        ( isInfA & isZeroB ) |
        ( isZeroA & isInfB ) |
        ( ~ isNaNA & ~ isNaNB & ( isInfA | isInfB ) & isInfC & doSubMags )
  val invalid = isSigNaNA | isSigNaNB | isSigNaNC | notSigNaN_invalid
  val overflow = commonCase & overflowY
  val underflow = commonCase & underflowY
  val inexact = overflow | ( commonCase & inexactY )

  val notSpecial_isZeroOut =
      notSpecial_addZeros | isZeroY | totalUnderflowY
  val isSatOut = overflow & ~ overflowY_roundMagUp
  val notNaN_isInfOut =
      isInfA | isInfB | isInfC | ( overflow & overflowY_roundMagUp )
  val isNaNOut = isNaNA | isNaNB | isNaNC | notSigNaN_invalid

  val signOut =
        (!doSubMags                                      && opSignC     ) ||
        (isNaNOut                                        && Bool(true)  ) ||
        (mulSpecial && !isSpecialC                       && signProd    ) ||
        (!mulSpecial && isSpecialC                       && opSignC     ) ||
        (!mulSpecial && notSpecial_addZeros && doSubMags && Bool(false) ) ||
        (commonCase                                      && signY       )
  val expOut =
      (   expY &
          ~ Mux(notSpecial_isZeroOut, UInt(7 << expWidth-3), UInt(0, expWidth) ) &
          ~ Mux(isSatOut            , UInt(2 << expWidth-3), UInt(0, expWidth) ) &
          ~ Mux(notNaN_isInfOut     , UInt(1 << expWidth-3), UInt(0, expWidth) ) ) | 
          Mux(isSatOut       , UInt((6 << expWidth-3)-1), UInt(0, expWidth) ) |
          Mux(notNaN_isInfOut, UInt(6 << expWidth-3), UInt(0, expWidth) ) |
          Mux(isNaNOut       , UInt(7 << expWidth-3), UInt(0, expWidth) )
  val fractOut = fractY | Fill(sigWidth, isNaNOut || isSatOut)

  io.out := Cat(signOut, expOut, fractOut)
  io.exceptionFlags := Cat(invalid, Bool(false), overflow, underflow, inexact)
}
