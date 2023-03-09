
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
import chisel3.util.{Cat, Fill}
import consts._

/*----------------------------------------------------------------------------
| Computes a division or square root for standard 64-bit floating-point in
| recoded form, using a separate integer multiplier-adder.  Multiple clock
| cycles are needed for each division or square-root operation.  See
| "docs/DivSqrtRecF64_mulAddZ31.txt" for more details.
*----------------------------------------------------------------------------*/

class DivSqrtRecF64ToRaw_mulAddZ31 extends Module
{
    val io = IO(new Bundle {
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val inReady_div    = Output(Bool())
        val inReady_sqrt   = Output(Bool())
        val inValid        = Input(Bool())
        val sqrtOp         = Input(Bool())
        val a              = Input(Bits(65.W))
        val b              = Input(Bits(65.W))
        val roundingMode   = Input(UInt(3.W))
//*** OPTIONALLY PROPAGATE:
//        val detectTininess = Input(UInt(1.W))
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val usingMulAdd    = Output(Bits(4.W))
        val latchMulAddA_0 = Output(Bool())
        val mulAddA_0      = Output(UInt(54.W))
        val latchMulAddB_0 = Output(Bool())
        val mulAddB_0      = Output(UInt(54.W))
        val mulAddC_2      = Output(UInt(105.W))
        val mulAddResult_3 = Input(UInt(105.W))
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val rawOutValid_div  = Output(Bool())
        val rawOutValid_sqrt = Output(Bool())
        val roundingModeOut  = Output(UInt(3.W))
        val invalidExc       = Output(Bool())
        val infiniteExc      = Output(Bool())
        val rawOut = Output(new RawFloat(11, 55))
    })

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val cycleNum_A      = RegInit(0.U(3.W))
    val cycleNum_B      = RegInit(0.U(4.W))
    val cycleNum_C      = RegInit(0.U(3.W))
    val cycleNum_E      = RegInit(0.U(3.W))

    val valid_PA        = RegInit(false.B)
    val sqrtOp_PA       = Reg(Bool())
    val majorExc_PA     = Reg(Bool())
//*** REDUCE 3 BITS TO 2-BIT CODE:
    val isNaN_PA        = Reg(Bool())
    val isInf_PA        = Reg(Bool())
    val isZero_PA       = Reg(Bool())
    val sign_PA         = Reg(Bool())
    val sExp_PA         = Reg(SInt(13.W))
    val fractB_PA       = Reg(UInt(52.W))
    val fractA_PA       = Reg(UInt(52.W))
    val roundingMode_PA = Reg(UInt(3.W))

    val valid_PB        = RegInit(false.B)
    val sqrtOp_PB       = Reg(Bool())
    val majorExc_PB     = Reg(Bool())
//*** REDUCE 3 BITS TO 2-BIT CODE:
    val isNaN_PB        = Reg(Bool())
    val isInf_PB        = Reg(Bool())
    val isZero_PB       = Reg(Bool())
    val sign_PB         = Reg(Bool())
    val sExp_PB         = Reg(SInt(13.W))
    val bit0FractA_PB   = Reg(UInt(1.W))
    val fractB_PB       = Reg(UInt(52.W))
    val roundingMode_PB = Reg(UInt(3.W))

    val valid_PC        = RegInit(false.B)
    val sqrtOp_PC       = Reg(Bool())
    val majorExc_PC     = Reg(Bool())
//*** REDUCE 3 BITS TO 2-BIT CODE:
    val isNaN_PC        = Reg(Bool())
    val isInf_PC        = Reg(Bool())
    val isZero_PC       = Reg(Bool())
    val sign_PC         = Reg(Bool())
    val sExp_PC         = Reg(SInt(13.W))
    val bit0FractA_PC   = Reg(UInt(1.W))
    val fractB_PC       = Reg(UInt(52.W))
    val roundingMode_PC = Reg(UInt(3.W))

    val fractR0_A       = Reg(UInt(9.W))
//*** COMBINE 'hiSqrR0_A_sqrt' AND 'partNegSigma0_A'?
    val hiSqrR0_A_sqrt  = Reg(UInt(10.W))
    val partNegSigma0_A = Reg(UInt(21.W))
    val nextMulAdd9A_A  = Reg(UInt(9.W))
    val nextMulAdd9B_A  = Reg(UInt(9.W))
    val ER1_B_sqrt      = Reg(UInt(17.W))

    val ESqrR1_B_sqrt   = Reg(UInt(32.W))
    val sigX1_B         = Reg(UInt(58.W))
    val sqrSigma1_C     = Reg(UInt(33.W))
    val sigXN_C         = Reg(UInt(58.W))
    val u_C_sqrt        = Reg(UInt(31.W))
    val E_E_div         = Reg(Bool())
    val sigT_E          = Reg(UInt(54.W))
    val isNegRemT_E     = Reg(Bool())
    val isZeroRemT_E    = Reg(Bool())

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val ready_PA   = Wire(Bool())
    val ready_PB   = Wire(Bool())
    val ready_PC   = Wire(Bool())
    val leaving_PA = Wire(Bool())
    val leaving_PB = Wire(Bool())
    val leaving_PC = Wire(Bool())

    val zSigma1_B4         = Wire(UInt())
    val sigXNU_B3_CX       = Wire(UInt())
    val zComplSigT_C1_sqrt = Wire(UInt())
    val zComplSigT_C1      = Wire(UInt())

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val cyc_S_div  = io.inReady_div  && io.inValid && ! io.sqrtOp
    val cyc_S_sqrt = io.inReady_sqrt && io.inValid &&   io.sqrtOp
    val cyc_S = cyc_S_div || cyc_S_sqrt

    val rawA_S = rawFloatFromRecFN(11, 53, io.a)
    val rawB_S = rawFloatFromRecFN(11, 53, io.b)

    val notSigNaNIn_invalidExc_S_div =
        (rawA_S.isZero && rawB_S.isZero) || (rawA_S.isInf && rawB_S.isInf)
    val notSigNaNIn_invalidExc_S_sqrt =
        ! rawB_S.isNaN && ! rawB_S.isZero && rawB_S.sign
    val majorExc_S =
        Mux(io.sqrtOp,
            isSigNaNRawFloat(rawB_S) || notSigNaNIn_invalidExc_S_sqrt,
            isSigNaNRawFloat(rawA_S) || isSigNaNRawFloat(rawB_S) ||
                notSigNaNIn_invalidExc_S_div ||
                (! rawA_S.isNaN && ! rawA_S.isInf && rawB_S.isZero)
        )
    val isNaN_S =
        Mux(io.sqrtOp,
            rawB_S.isNaN || notSigNaNIn_invalidExc_S_sqrt,
            rawA_S.isNaN || rawB_S.isNaN || notSigNaNIn_invalidExc_S_div
        )
    val isInf_S  = Mux(io.sqrtOp, rawB_S.isInf,  rawA_S.isInf || rawB_S.isZero)
    val isZero_S = Mux(io.sqrtOp, rawB_S.isZero, rawA_S.isZero || rawB_S.isInf)
    val sign_S = (! io.sqrtOp && rawA_S.sign) ^ rawB_S.sign

    val specialCaseA_S = rawA_S.isNaN || rawA_S.isInf || rawA_S.isZero
    val specialCaseB_S = rawB_S.isNaN || rawB_S.isInf || rawB_S.isZero
    val normalCase_S_div = ! specialCaseA_S && ! specialCaseB_S
    val normalCase_S_sqrt = ! specialCaseB_S && ! rawB_S.sign
    val normalCase_S = Mux(io.sqrtOp, normalCase_S_sqrt, normalCase_S_div)

    val sExpQuot_S_div =
        rawA_S.sExp +& (rawB_S.sExp(11) ## ~rawB_S.sExp(10, 0)).asSInt
//*** IS THIS OPTIMAL?:
    val sSatExpQuot_S_div =
        (Mux(((7<<9).S <= sExpQuot_S_div),
            6.U,
            sExpQuot_S_div(12, 9)
            ) ##
            sExpQuot_S_div(8, 0)
        ).asSInt

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val entering_PA_normalCase_div  = cyc_S_div  && normalCase_S_div
    val entering_PA_normalCase_sqrt = cyc_S_sqrt && normalCase_S_sqrt
    val entering_PA_normalCase =
        entering_PA_normalCase_div || entering_PA_normalCase_sqrt

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    when (entering_PA_normalCase || (cycleNum_A =/= 0.U)) {
        cycleNum_A :=
            Mux(entering_PA_normalCase_div,  3.U,              0.U) |
            Mux(entering_PA_normalCase_sqrt, 6.U,              0.U) |
            Mux(! entering_PA_normalCase,    cycleNum_A - 1.U, 0.U)
    }

    val cyc_A7_sqrt = entering_PA_normalCase_sqrt
    val cyc_A6_sqrt = (cycleNum_A === 6.U)
    val cyc_A5_sqrt = (cycleNum_A === 5.U)
    val cyc_A4_sqrt = (cycleNum_A === 4.U)

    val cyc_A4_div = entering_PA_normalCase_div

    val cyc_A4 = cyc_A4_sqrt || cyc_A4_div
    val cyc_A3 = (cycleNum_A === 3.U)
    val cyc_A2 = (cycleNum_A === 2.U)
    val cyc_A1 = (cycleNum_A === 1.U)

    val cyc_A3_div = cyc_A3 && ! sqrtOp_PA
    val cyc_A2_div = cyc_A2 && ! sqrtOp_PA
    val cyc_A1_div = cyc_A1 && ! sqrtOp_PA

    val cyc_A3_sqrt = cyc_A3 && sqrtOp_PA
    val cyc_A2_sqrt = cyc_A2 && sqrtOp_PA
    val cyc_A1_sqrt = cyc_A1 && sqrtOp_PA

    when (cyc_A1 || (cycleNum_B =/= 0.U)) {
        cycleNum_B :=
            Mux(cyc_A1,
                Mux(sqrtOp_PA, 10.U, 6.U),
                cycleNum_B - 1.U
            )
    }

    val cyc_B10_sqrt = (cycleNum_B === 10.U)
    val cyc_B9_sqrt  = (cycleNum_B === 9.U)
    val cyc_B8_sqrt  = (cycleNum_B === 8.U)
    val cyc_B7_sqrt  = (cycleNum_B === 7.U)

    val cyc_B6 = (cycleNum_B === 6.U)
    val cyc_B5 = (cycleNum_B === 5.U)
    val cyc_B4 = (cycleNum_B === 4.U)
    val cyc_B3 = (cycleNum_B === 3.U)
    val cyc_B2 = (cycleNum_B === 2.U)
    val cyc_B1 = (cycleNum_B === 1.U)

    val cyc_B6_div = cyc_B6 && valid_PA && ! sqrtOp_PA
    val cyc_B5_div = cyc_B5 && valid_PA && ! sqrtOp_PA
    val cyc_B4_div = cyc_B4 && valid_PA && ! sqrtOp_PA
    val cyc_B3_div = cyc_B3 && ! sqrtOp_PB
    val cyc_B2_div = cyc_B2 && ! sqrtOp_PB
    val cyc_B1_div = cyc_B1 && ! sqrtOp_PB

    val cyc_B6_sqrt = cyc_B6 && valid_PB && sqrtOp_PB
    val cyc_B5_sqrt = cyc_B5 && valid_PB && sqrtOp_PB
    val cyc_B4_sqrt = cyc_B4 && valid_PB && sqrtOp_PB
    val cyc_B3_sqrt = cyc_B3 && sqrtOp_PB
    val cyc_B2_sqrt = cyc_B2 && sqrtOp_PB
    val cyc_B1_sqrt = cyc_B1 && sqrtOp_PB

    when (cyc_B1 || (cycleNum_C =/= 0.U)) {
        cycleNum_C :=
            Mux(cyc_B1, Mux(sqrtOp_PB, 6.U, 5.U), cycleNum_C - 1.U)
    }

    val cyc_C6_sqrt = (cycleNum_C === 6.U)

    val cyc_C5 = (cycleNum_C === 5.U)
    val cyc_C4 = (cycleNum_C === 4.U)
    val cyc_C3 = (cycleNum_C === 3.U)
    val cyc_C2 = (cycleNum_C === 2.U)
    val cyc_C1 = (cycleNum_C === 1.U)

    val cyc_C5_div = cyc_C5 && ! sqrtOp_PB
    val cyc_C4_div = cyc_C4 && ! sqrtOp_PB
    val cyc_C3_div = cyc_C3 && ! sqrtOp_PB
    val cyc_C2_div = cyc_C2 && ! sqrtOp_PC
    val cyc_C1_div = cyc_C1 && ! sqrtOp_PC

    val cyc_C5_sqrt = cyc_C5 && sqrtOp_PB
    val cyc_C4_sqrt = cyc_C4 && sqrtOp_PB
    val cyc_C3_sqrt = cyc_C3 && sqrtOp_PB
    val cyc_C2_sqrt = cyc_C2 && sqrtOp_PC
    val cyc_C1_sqrt = cyc_C1 && sqrtOp_PC

    when (cyc_C1 || (cycleNum_E =/= 0.U)) {
        cycleNum_E := Mux(cyc_C1, 4.U, cycleNum_E - 1.U)
    }

    val cyc_E4 = (cycleNum_E === 4.U)
    val cyc_E3 = (cycleNum_E === 3.U)
    val cyc_E2 = (cycleNum_E === 2.U)
    val cyc_E1 = (cycleNum_E === 1.U)

    val cyc_E4_div = cyc_E4 && ! sqrtOp_PC
    val cyc_E3_div = cyc_E3 && ! sqrtOp_PC
    val cyc_E2_div = cyc_E2 && ! sqrtOp_PC
    val cyc_E1_div = cyc_E1 && ! sqrtOp_PC

    val cyc_E4_sqrt = cyc_E4 && sqrtOp_PC
    val cyc_E3_sqrt = cyc_E3 && sqrtOp_PC
    val cyc_E2_sqrt = cyc_E2 && sqrtOp_PC
    val cyc_E1_sqrt = cyc_E1 && sqrtOp_PC

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val entering_PA =
        entering_PA_normalCase || (cyc_S && (valid_PA || ! ready_PB))

    when (entering_PA || leaving_PA) {
        valid_PA := entering_PA
    }
    when (entering_PA) {
        sqrtOp_PA   := io.sqrtOp
        majorExc_PA := majorExc_S
        isNaN_PA    := isNaN_S
        isInf_PA    := isInf_S
        isZero_PA   := isZero_S
        sign_PA     := sign_S
    }
    when (entering_PA_normalCase) {
        sExp_PA := Mux(io.sqrtOp, rawB_S.sExp, sSatExpQuot_S_div)
        fractB_PA := rawB_S.sig(51, 0)
        roundingMode_PA := io.roundingMode
    }
    when (entering_PA_normalCase_div) {
        fractA_PA := rawA_S.sig(51, 0)
    }

    val normalCase_PA = ! isNaN_PA && ! isInf_PA && ! isZero_PA
    val sigA_PA = 1.U(1.W) ## fractA_PA
    val sigB_PA = 1.U(1.W) ## fractB_PA

    val valid_normalCase_leaving_PA = cyc_B4_div || cyc_B7_sqrt
    val valid_leaving_PA =
        Mux(normalCase_PA, valid_normalCase_leaving_PA, ready_PB)
    leaving_PA := valid_PA && valid_leaving_PA
    ready_PA := ! valid_PA || valid_leaving_PA

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val entering_PB_S =
        cyc_S && ! normalCase_S && ! valid_PA &&
            (leaving_PB || (! valid_PB && ! ready_PC))
    val entering_PB_normalCase =
        valid_PA && normalCase_PA && valid_normalCase_leaving_PA
    val entering_PB = entering_PB_S || leaving_PA

    when (entering_PB || leaving_PB) {
        valid_PB := entering_PB
    }
    when (entering_PB) {
        sqrtOp_PB   := Mux(valid_PA, sqrtOp_PA,   io.sqrtOp )
        majorExc_PB := Mux(valid_PA, majorExc_PA, majorExc_S)
        isNaN_PB    := Mux(valid_PA, isNaN_PA,    isNaN_S   )
        isInf_PB    := Mux(valid_PA, isInf_PA,    isInf_S   )
        isZero_PB   := Mux(valid_PA, isZero_PA,   isZero_S  )
        sign_PB     := Mux(valid_PA, sign_PA,     sign_S    )
    }
    when (entering_PB_normalCase) {
        sExp_PB         := sExp_PA
        bit0FractA_PB   := fractA_PA(0)
        fractB_PB       := fractB_PA
        roundingMode_PB := Mux(valid_PA, roundingMode_PA, io.roundingMode)
    }

    val normalCase_PB = ! isNaN_PB && ! isInf_PB && ! isZero_PB

    val valid_normalCase_leaving_PB = cyc_C3
    val valid_leaving_PB =
        Mux(normalCase_PB, valid_normalCase_leaving_PB, ready_PC)
    leaving_PB := valid_PB && valid_leaving_PB
    ready_PB := ! valid_PB || valid_leaving_PB

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val entering_PC_S =
        cyc_S && ! normalCase_S && ! valid_PA && ! valid_PB && ready_PC
    val entering_PC_normalCase =
        valid_PB && normalCase_PB && valid_normalCase_leaving_PB
    val entering_PC = entering_PC_S || leaving_PB

    when (entering_PC || leaving_PC) {
        valid_PC := entering_PC
    }
    when (entering_PC) {
        sqrtOp_PC   := Mux(valid_PB, sqrtOp_PB,   io.sqrtOp )
        majorExc_PC := Mux(valid_PB, majorExc_PB, majorExc_S)
        isNaN_PC    := Mux(valid_PB, isNaN_PB,    isNaN_S   )
        isInf_PC    := Mux(valid_PB, isInf_PB,    isInf_S   )
        isZero_PC   := Mux(valid_PB, isZero_PB,   isZero_S  )
        sign_PC     := Mux(valid_PB, sign_PB,     sign_S    )
    }
    when (entering_PC_normalCase) {
        sExp_PC         := sExp_PB
        bit0FractA_PC   := bit0FractA_PB
        fractB_PC       := fractB_PB
        roundingMode_PC := Mux(valid_PB, roundingMode_PB, io.roundingMode)
    }

    val normalCase_PC = ! isNaN_PC && ! isInf_PC && ! isZero_PC
    val sigB_PC = 1.U(1.W) ## fractB_PC

    val valid_leaving_PC = ! normalCase_PC || cyc_E1
    leaving_PC := valid_PC && valid_leaving_PC
    ready_PC := ! valid_PC || valid_leaving_PC

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
//*** NEED TO COMPUTE AS MUCH AS POSSIBLE IN PREVIOUS CYCLE?:
    io.inReady_div :=
//*** REPLACE ALL OF '! cyc_B*_sqrt' BY '! (valid_PB && sqrtOp_PB)'?:
        ready_PA && ! cyc_B7_sqrt && ! cyc_B6_sqrt && ! cyc_B5_sqrt &&
            ! cyc_B4_sqrt && ! cyc_B3 && ! cyc_B2 && ! cyc_B1_sqrt &&
            ! cyc_C5 && ! cyc_C4
    io.inReady_sqrt :=
        ready_PA && ! cyc_B6_sqrt && ! cyc_B5_sqrt && ! cyc_B4_sqrt &&
            ! cyc_B2_div && ! cyc_B1_sqrt

    /*------------------------------------------------------------------------
    | Macrostage A, built around a 9x9-bit multiplier-adder.
    *------------------------------------------------------------------------*/
    val zFractB_A4_div = Mux(cyc_A4_div, rawB_S.sig(51, 0), 0.U)

    val zLinPiece_0_A4_div = cyc_A4_div && (rawB_S.sig(51, 49) === 0.U)
    val zLinPiece_1_A4_div = cyc_A4_div && (rawB_S.sig(51, 49) === 1.U)
    val zLinPiece_2_A4_div = cyc_A4_div && (rawB_S.sig(51, 49) === 2.U)
    val zLinPiece_3_A4_div = cyc_A4_div && (rawB_S.sig(51, 49) === 3.U)
    val zLinPiece_4_A4_div = cyc_A4_div && (rawB_S.sig(51, 49) === 4.U)
    val zLinPiece_5_A4_div = cyc_A4_div && (rawB_S.sig(51, 49) === 5.U)
    val zLinPiece_6_A4_div = cyc_A4_div && (rawB_S.sig(51, 49) === 6.U)
    val zLinPiece_7_A4_div = cyc_A4_div && (rawB_S.sig(51, 49) === 7.U)
    val zK1_A4_div =
        Mux(zLinPiece_0_A4_div, "h1C7".U, 0.U) |
        Mux(zLinPiece_1_A4_div, "h16C".U, 0.U) |
        Mux(zLinPiece_2_A4_div, "h12A".U, 0.U) |
        Mux(zLinPiece_3_A4_div, "h0F8".U, 0.U) |
        Mux(zLinPiece_4_A4_div, "h0D2".U, 0.U) |
        Mux(zLinPiece_5_A4_div, "h0B4".U, 0.U) |
        Mux(zLinPiece_6_A4_div, "h09C".U, 0.U) |
        Mux(zLinPiece_7_A4_div, "h089".U, 0.U)
    val zComplFractK0_A4_div =
        Mux(zLinPiece_0_A4_div, ~"hFE3".U(12.W), 0.U) |
        Mux(zLinPiece_1_A4_div, ~"hC5D".U(12.W), 0.U) |
        Mux(zLinPiece_2_A4_div, ~"h98A".U(12.W), 0.U) |
        Mux(zLinPiece_3_A4_div, ~"h739".U(12.W), 0.U) |
        Mux(zLinPiece_4_A4_div, ~"h54B".U(12.W), 0.U) |
        Mux(zLinPiece_5_A4_div, ~"h3A9".U(12.W), 0.U) |
        Mux(zLinPiece_6_A4_div, ~"h242".U(12.W), 0.U) |
        Mux(zLinPiece_7_A4_div, ~"h10B".U(12.W), 0.U)

    val zFractB_A7_sqrt = Mux(cyc_A7_sqrt, rawB_S.sig(51, 0), 0.U)

    val zQuadPiece_0_A7_sqrt =
        cyc_A7_sqrt && ! rawB_S.sExp(0) && ! rawB_S.sig(51)
    val zQuadPiece_1_A7_sqrt =
        cyc_A7_sqrt && ! rawB_S.sExp(0) &&   rawB_S.sig(51)
    val zQuadPiece_2_A7_sqrt =
        cyc_A7_sqrt &&   rawB_S.sExp(0) && ! rawB_S.sig(51)
    val zQuadPiece_3_A7_sqrt = cyc_A7_sqrt && rawB_S.sExp(0) && rawB_S.sig(51)
    val zK2_A7_sqrt =
        Mux(zQuadPiece_0_A7_sqrt, "h1C8".U, 0.U) |
        Mux(zQuadPiece_1_A7_sqrt, "h0C1".U, 0.U) |
        Mux(zQuadPiece_2_A7_sqrt, "h143".U, 0.U) |
        Mux(zQuadPiece_3_A7_sqrt, "h089".U, 0.U)
    val zComplK1_A7_sqrt =
        Mux(zQuadPiece_0_A7_sqrt, ~"h3D0".U(10.W), 0.U) |
        Mux(zQuadPiece_1_A7_sqrt, ~"h220".U(10.W), 0.U) |
        Mux(zQuadPiece_2_A7_sqrt, ~"h2B2".U(10.W), 0.U) |
        Mux(zQuadPiece_3_A7_sqrt, ~"h181".U(10.W), 0.U)

    val zQuadPiece_0_A6_sqrt = cyc_A6_sqrt && ! sExp_PA(0) && ! sigB_PA(51)
    val zQuadPiece_1_A6_sqrt = cyc_A6_sqrt && ! sExp_PA(0) &&   sigB_PA(51)
    val zQuadPiece_2_A6_sqrt = cyc_A6_sqrt &&   sExp_PA(0) && ! sigB_PA(51)
    val zQuadPiece_3_A6_sqrt = cyc_A6_sqrt &&   sExp_PA(0) &&   sigB_PA(51)
    val zComplFractK0_A6_sqrt =
        Mux(zQuadPiece_0_A6_sqrt, ~"h1FE5".U(13.W), 0.U) |
        Mux(zQuadPiece_1_A6_sqrt, ~"h1435".U(13.W), 0.U) |
        Mux(zQuadPiece_2_A6_sqrt, ~"h0D2C".U(13.W), 0.U) |
        Mux(zQuadPiece_3_A6_sqrt, ~"h04E8".U(13.W), 0.U)

    val mulAdd9A_A =
        zFractB_A4_div(48, 40) | zK2_A7_sqrt |
            Mux(! cyc_S, nextMulAdd9A_A, 0.U)
    val mulAdd9B_A =
        zK1_A4_div | zFractB_A7_sqrt(50, 42) |
            Mux(! cyc_S, nextMulAdd9B_A, 0.U)
    val mulAdd9C_A =
//*** ADJUST CONSTANTS SO 'Fill'S AREN'T NEEDED:
        zComplK1_A7_sqrt ##                  Fill(10, cyc_A7_sqrt) |
        Cat(cyc_A6_sqrt, zComplFractK0_A6_sqrt, Fill(6, cyc_A6_sqrt)) |
        Cat(cyc_A4_div,  zComplFractK0_A4_div,  Fill(8, cyc_A4_div )) |
        Mux(cyc_A5_sqrt,     (1<<18).U +& (fractR0_A<<10), 0.U) |
        Mux(cyc_A4_sqrt && ! hiSqrR0_A_sqrt(9), (1<<10).U, 0.U) |
        Mux((cyc_A4_sqrt && hiSqrR0_A_sqrt(9)) || cyc_A3_div,
            sigB_PA(46, 26) + (1<<10).U,
            0.U
        ) |
        Mux(cyc_A3_sqrt || cyc_A2, partNegSigma0_A, 0.U) |
        Mux(cyc_A1_sqrt,           fractR0_A<<16,   0.U) |
        Mux(cyc_A1_div,            fractR0_A<<15,   0.U)
    val loMulAdd9Out_A = mulAdd9A_A * mulAdd9B_A +& mulAdd9C_A(17, 0)
    val mulAdd9Out_A =
        Cat(Mux(loMulAdd9Out_A(18),
                mulAdd9C_A(24, 18) + 1.U,
                mulAdd9C_A(24, 18)
            ),
            loMulAdd9Out_A(17, 0)
        )

    val zFractR0_A6_sqrt =
        Mux(cyc_A6_sqrt && mulAdd9Out_A(19), ~(mulAdd9Out_A>>10), 0.U)
    /*------------------------------------------------------------------------
    | ('sqrR0_A5_sqrt' is usually >= 1, but not always.)
    *------------------------------------------------------------------------*/
    val sqrR0_A5_sqrt = Mux(sExp_PA(0), mulAdd9Out_A<<1, mulAdd9Out_A)
    val zFractR0_A4_div =
        Mux(cyc_A4_div && mulAdd9Out_A(20), ~(mulAdd9Out_A>>11), 0.U)
    val zSigma0_A2 =
        Mux(cyc_A2 && mulAdd9Out_A(11), ~(mulAdd9Out_A>>2), 0.U)
    val r1_A1 = (1<<15).U | Mux(sqrtOp_PA, mulAdd9Out_A>>10, mulAdd9Out_A>>9)
    val ER1_A1_sqrt = Mux(sExp_PA(0), r1_A1<<1, r1_A1)

    when (cyc_A6_sqrt || cyc_A4_div) {
        fractR0_A := zFractR0_A6_sqrt | zFractR0_A4_div
    }
    when (cyc_A5_sqrt) {
        hiSqrR0_A_sqrt := sqrR0_A5_sqrt>>10
    }
    when (cyc_A4_sqrt || cyc_A3) {
        partNegSigma0_A := Mux(cyc_A4_sqrt, mulAdd9Out_A, mulAdd9Out_A>>9)
    }
    when (
        cyc_A7_sqrt || cyc_A6_sqrt || cyc_A5_sqrt || cyc_A4 || cyc_A3 || cyc_A2
    ) {
        nextMulAdd9A_A :=
            Mux(cyc_A7_sqrt,           ~mulAdd9Out_A>>11, 0.U) |
            zFractR0_A6_sqrt                                       |
            Mux(cyc_A4_sqrt,           sigB_PA(43, 35),   0.U) |
            zFractB_A4_div(43, 35)                                 |
            Mux(cyc_A5_sqrt || cyc_A3, sigB_PA(52, 44),   0.U) |
            zSigma0_A2
    }
    when (cyc_A7_sqrt || cyc_A6_sqrt || cyc_A5_sqrt || cyc_A4 || cyc_A2) {
        nextMulAdd9B_A :=
            zFractB_A7_sqrt(50, 42)                                     |
            zFractR0_A6_sqrt                                            |
            Mux(cyc_A5_sqrt, sqrR0_A5_sqrt(9, 1),              0.U) |
            zFractR0_A4_div                                             |
            Mux(cyc_A4_sqrt, hiSqrR0_A_sqrt(8, 0),             0.U) |
            Mux(cyc_A2,      Cat(1.U(1.W), fractR0_A(8, 1)), 0.U)
    }
    when (cyc_A1_sqrt) {
        ER1_B_sqrt := ER1_A1_sqrt
    }

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    io.latchMulAddA_0 :=
        cyc_A1 || cyc_B7_sqrt || cyc_B6_div || cyc_B4 || cyc_B3 ||
            cyc_C6_sqrt || cyc_C4 || cyc_C1
    io.mulAddA_0 :=
        Mux(cyc_A1_sqrt,               ER1_A1_sqrt<<36,  0.U) | // 52:36
        Mux(cyc_B7_sqrt || cyc_A1_div, sigB_PA,          0.U) | // 52:0
        Mux(cyc_B6_div,                sigA_PA,          0.U) | // 52:0
        zSigma1_B4(45, 12)                                        | // 33:0
//*** ONLY 30 BITS NEEDED IN CYCLE C6:
        Mux(cyc_B3 || cyc_C6_sqrt, sigXNU_B3_CX(57, 12), 0.U) | // 45:0
        Mux(cyc_C4_div,            sigXN_C(57, 25)<<13,  0.U) | // 45:13
        Mux(cyc_C4_sqrt,           u_C_sqrt<<15,         0.U) | // 45:15
        Mux(cyc_C1_div,            sigB_PC,              0.U) | // 52:0
        zComplSigT_C1_sqrt                                          // 53:0
    io.latchMulAddB_0 :=
        cyc_A1 || cyc_B7_sqrt || cyc_B6_sqrt || cyc_B4 ||
            cyc_C6_sqrt || cyc_C4 || cyc_C1
    io.mulAddB_0 :=
        Mux(cyc_A1,      r1_A1<<36,          0.U) |  // 51:36
        Mux(cyc_B7_sqrt, ESqrR1_B_sqrt<<19,  0.U) |  // 50:19
        Mux(cyc_B6_sqrt, ER1_B_sqrt<<36,     0.U) |  // 52:36
        zSigma1_B4                                    |  // 45:0
        Mux(cyc_C6_sqrt, sqrSigma1_C(30, 1), 0.U) |  // 29:0
        Mux(cyc_C4,      sqrSigma1_C,        0.U) |  // 32:0
        zComplSigT_C1                                    // 53:0

    io.usingMulAdd :=
        Cat(cyc_A4 || cyc_A3_div || cyc_A1_div ||
                cyc_B10_sqrt || cyc_B9_sqrt || cyc_B7_sqrt || cyc_B6 ||
                cyc_B5_sqrt || cyc_B3_sqrt || cyc_B2_div || cyc_B1_sqrt ||
                cyc_C4,
            cyc_A3 || cyc_A2_div ||
                cyc_B9_sqrt || cyc_B8_sqrt || cyc_B6 || cyc_B5 ||
                cyc_B4_sqrt || cyc_B2_sqrt || cyc_B1_div || cyc_C6_sqrt ||
                cyc_C3,
            cyc_A2 || cyc_A1_div ||
                cyc_B8_sqrt || cyc_B7_sqrt || cyc_B5 || cyc_B4 ||
                cyc_B3_sqrt || cyc_B1_sqrt || cyc_C5 ||
                cyc_C2,
            io.latchMulAddA_0 || cyc_B6 || cyc_B2_sqrt
        )

    io.mulAddC_2 :=
        Mux(cyc_B1,                  sigX1_B<<47,       0.U) |
        Mux(cyc_C6_sqrt,             sigX1_B<<46,       0.U) |
        Mux(cyc_C4_sqrt || cyc_C2,   sigXN_C<<47,       0.U) |
        Mux(cyc_E3_div && ! E_E_div, bit0FractA_PC<<53, 0.U) |
        Mux(cyc_E3_sqrt,
            (Mux(sExp_PC(0),
                 sigB_PC(0)<<1,
                 (sigB_PC(1) ^ sigB_PC(0)) ## sigB_PC(0)
             ) ^ ((~ sigT_E(0))<<1)
            )<<54,
            0.U
        )

    val ESqrR1_B8_sqrt = io.mulAddResult_3(103, 72)
    zSigma1_B4 := Mux(cyc_B4, ~io.mulAddResult_3(90, 45), 0.U)
    val sqrSigma1_B1 = io.mulAddResult_3(79, 47)
    sigXNU_B3_CX := io.mulAddResult_3(104, 47)   // x1, x2, u (sqrt), xT'
    val E_C1_div = ! io.mulAddResult_3(104)
    zComplSigT_C1 :=
        Mux((cyc_C1_div && ! E_C1_div) || cyc_C1_sqrt,
            ~io.mulAddResult_3(104, 51),
            0.U
        ) |
        Mux(cyc_C1_div && E_C1_div, ~io.mulAddResult_3(102, 50), 0.U)
    zComplSigT_C1_sqrt :=
        Mux(cyc_C1_sqrt, ~io.mulAddResult_3(104, 51), 0.U)
    /*------------------------------------------------------------------------
    | (For square root, 'sigT_C1' will usually be >= 1, but not always.)
    *------------------------------------------------------------------------*/
    val sigT_C1 = ~zComplSigT_C1
    val remT_E2 = io.mulAddResult_3(55, 0)

    when (cyc_B8_sqrt) {
        ESqrR1_B_sqrt := ESqrR1_B8_sqrt
    }
    when (cyc_B3) {
        sigX1_B := sigXNU_B3_CX
    }
    when (cyc_B1) {
        sqrSigma1_C := sqrSigma1_B1
    }

    when (cyc_C6_sqrt || cyc_C5_div || cyc_C3_sqrt) {
        sigXN_C := sigXNU_B3_CX
    }
    when (cyc_C5_sqrt) {
        u_C_sqrt := sigXNU_B3_CX(56, 26)
    }
    when (cyc_C1) {
        E_E_div := E_C1_div
        sigT_E  := sigT_C1
    }

    when (cyc_E2) {
        isNegRemT_E := Mux(sqrtOp_PC,  remT_E2(55),  remT_E2(53))
        isZeroRemT_E :=
            (remT_E2(53, 0) === 0.U) &&
                (! sqrtOp_PC || (remT_E2(55, 54) === 0.U))
    }

    /*------------------------------------------------------------------------
    | T is the lower-bound "trial" result value, with 54 bits of precision.
    | It is known that the true unrounded result is within the range of
    | (T, T + (2 ulps of 54 bits)).  X is defined as the best estimate,
    | = T + (1 ulp), which is exactly in the middle of the possible range.
    *------------------------------------------------------------------------*/
    val trueLtX_E1 =
        Mux(sqrtOp_PC, ! isNegRemT_E && ! isZeroRemT_E, isNegRemT_E)
    val trueEqX_E1 = isZeroRemT_E

    /*------------------------------------------------------------------------
    | The inputs to these two values are stable for several clock cycles in
    | advance, so the circuitry can be minimized at the expense of speed.
*** ANY WAY TO TELL THIS TO THE TOOLS?
    *------------------------------------------------------------------------*/
    val sExpP1_PC = sExp_PC + 1.S
    val sigTP1_E = sigT_E +& 1.U

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    io.rawOutValid_div  := leaving_PC && ! sqrtOp_PC
    io.rawOutValid_sqrt := leaving_PC &&   sqrtOp_PC
    io.roundingModeOut  := roundingMode_PC
    io.invalidExc       := majorExc_PC &&   isNaN_PC
    io.infiniteExc      := majorExc_PC && ! isNaN_PC
    io.rawOut.isNaN  := isNaN_PC
    io.rawOut.isInf  := isInf_PC
    io.rawOut.isZero := isZero_PC
    io.rawOut.sign := sign_PC
    io.rawOut.sExp :=
        Mux(! sqrtOp_PC &&   E_E_div, sExp_PC,                    0.S) |
        Mux(! sqrtOp_PC && ! E_E_div, sExpP1_PC,                  0.S) |
        Mux(  sqrtOp_PC,              (sExp_PC>>1) +& 1024.S, 0.S)
    io.rawOut.sig := Mux(trueLtX_E1, sigT_E, sigTP1_E) ## ! trueEqX_E1

}

/*----------------------------------------------------------------------------
*----------------------------------------------------------------------------*/

class DivSqrtRecF64_mulAddZ31(options: Int) extends Module
{
    val io = IO(new Bundle {
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val inReady_div    = Output(Bool())
        val inReady_sqrt   = Output(Bool())
        val inValid        = Input(Bool())
        val sqrtOp         = Input(Bool())
        val a              = Input(Bits(65.W))
        val b              = Input(Bits(65.W))
        val roundingMode   = Input(UInt(3.W))
        val detectTininess = Input(UInt(1.W))
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val usingMulAdd    = Output(Bits(4.W))
        val latchMulAddA_0 = Output(Bool())
        val mulAddA_0      = Output(UInt(54.W))
        val latchMulAddB_0 = Output(Bool())
        val mulAddB_0      = Output(UInt(54.W))
        val mulAddC_2      = Output(UInt(105.W))
        val mulAddResult_3 = Input(UInt(105.W))
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val outValid_div   = Output(Bool())
        val outValid_sqrt  = Output(Bool())
        val out            = Output(Bits(65.W))
        val exceptionFlags = Output(Bits(5.W))
    })

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val divSqrtRecF64ToRaw = Module(new DivSqrtRecF64ToRaw_mulAddZ31)

    io.inReady_div  := divSqrtRecF64ToRaw.io.inReady_div
    io.inReady_sqrt := divSqrtRecF64ToRaw.io.inReady_sqrt
    divSqrtRecF64ToRaw.io.inValid      := io.inValid
    divSqrtRecF64ToRaw.io.sqrtOp       := io.sqrtOp
    divSqrtRecF64ToRaw.io.a            := io.a
    divSqrtRecF64ToRaw.io.b            := io.b
    divSqrtRecF64ToRaw.io.roundingMode := io.roundingMode

    io.usingMulAdd    := divSqrtRecF64ToRaw.io.usingMulAdd
    io.latchMulAddA_0 := divSqrtRecF64ToRaw.io.latchMulAddA_0
    io.mulAddA_0      := divSqrtRecF64ToRaw.io.mulAddA_0
    io.latchMulAddB_0 := divSqrtRecF64ToRaw.io.latchMulAddB_0
    io.mulAddB_0      := divSqrtRecF64ToRaw.io.mulAddB_0
    io.mulAddC_2      := divSqrtRecF64ToRaw.io.mulAddC_2
    divSqrtRecF64ToRaw.io.mulAddResult_3 := io.mulAddResult_3

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    io.outValid_div  := divSqrtRecF64ToRaw.io.rawOutValid_div
    io.outValid_sqrt := divSqrtRecF64ToRaw.io.rawOutValid_sqrt

    val roundRawFNToRecFN =
        Module(new RoundRawFNToRecFN(11, 53, flRoundOpt_sigMSBitAlwaysZero))
    roundRawFNToRecFN.io.invalidExc   := divSqrtRecF64ToRaw.io.invalidExc
    roundRawFNToRecFN.io.infiniteExc  := divSqrtRecF64ToRaw.io.infiniteExc
    roundRawFNToRecFN.io.in           := divSqrtRecF64ToRaw.io.rawOut
    roundRawFNToRecFN.io.roundingMode := divSqrtRecF64ToRaw.io.roundingModeOut
    roundRawFNToRecFN.io.detectTininess := io.detectTininess
    io.out            := roundRawFNToRecFN.io.out
    io.exceptionFlags := roundRawFNToRecFN.io.exceptionFlags

}

