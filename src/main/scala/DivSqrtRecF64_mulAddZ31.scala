
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

// YUNSUP: Lines with CHISEL are modified from the original Verilog source
// code to bridge the different language semantics of Chisel and Verilog.

/*----------------------------------------------------------------------------
| Computes a division or square root for standard 64-bit floating-point in
| recoded form, using a separate integer multiplier-adder.  Multiple clock
| cycles are needed for each division or square-root operation.
| See "docs/DivSqrtRecF64_mulAddZ31.txt" for more details.
*----------------------------------------------------------------------------*/

class DivSqrtRecF64_mulAddZ31 extends Module
{
    val io = new Bundle {
        val inReady_div  = Bool(OUTPUT)
        val inReady_sqrt = Bool(OUTPUT)
        val inValid = Bool(INPUT)
        val sqrtOp = Bool(INPUT)
        val a = Bits(INPUT, 65)
        val b = Bits(INPUT, 65)
        val roundingMode = Bits(INPUT, 2)
        val outValid_div  = Bool(OUTPUT)
        val outValid_sqrt = Bool(OUTPUT)
        val out = Bits(OUTPUT, 65)
        val exceptionFlags = Bits(OUTPUT, 5)
        val usingMulAdd = Bits(OUTPUT, 4)
        val latchMulAddA_0 = Bool(OUTPUT)
        val mulAddA_0 = Bits(OUTPUT, 54)
        val latchMulAddB_0 = Bool(OUTPUT)
        val mulAddB_0 = Bits(OUTPUT, 54)
        val mulAddC_2 = Bits(OUTPUT, 105)
        val mulAddResult_3 = Bits(INPUT, 105)
    }

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val valid_PA        = Reg(init = Bool(false))
    val sqrtOp_PA       = Reg(Bool())
    val sign_PA         = Reg(Bool())
//*** REDUCE?:
    val specialCodeB_PA = Reg(Bits(width = 3))
    val fractB_51_PA    = Reg(Bool())
    val roundingMode_PA = Reg(Bits(width = 2))
    val specialCodeA_PA = Reg(Bits(width = 3))
    val fractA_51_PA    = Reg(Bool())
    val exp_PA          = Reg(Bits(width = 14))
    val fractB_other_PA = Reg(Bits(width = 51))
    val fractA_other_PA = Reg(Bits(width = 51))

    val valid_PB        = Reg(init = Bool(false))
    val sqrtOp_PB       = Reg(Bool())
    val sign_PB         = Reg(Bool())
//*** REDUCE?:
    val specialCodeA_PB = Reg(Bits(width = 3))
    val fractA_51_PB    = Reg(Bool())
    val specialCodeB_PB = Reg(Bits(width = 3))
    val fractB_51_PB    = Reg(Bool())
    val roundingMode_PB = Reg(Bits(width = 2))
    val exp_PB          = Reg(Bits(width = 14))
    val fractA_0_PB     = Reg(Bool())
    val fractB_other_PB = Reg(Bits(width = 51))

    val valid_PC        = Reg(init = Bool(false))
    val sqrtOp_PC       = Reg(Bool())
    val sign_PC         = Reg(Bool())
//*** REDUCE?:
    val specialCodeA_PC = Reg(Bits(width = 3))
    val fractA_51_PC    = Reg(Bool())
    val specialCodeB_PC = Reg(Bits(width = 3))
    val fractB_51_PC    = Reg(Bool())
    val roundingMode_PC = Reg(Bits(width = 2))
    val exp_PC          = Reg(Bits(width = 14))
    val fractA_0_PC     = Reg(Bool())
    val fractB_other_PC = Reg(Bits(width = 51))

    val cycleNum_A      = Reg(init = Bits(0, 3))
    val cycleNum_B      = Reg(init = Bits(0, 4))
    val cycleNum_C      = Reg(init = Bits(0, 3))
    val cycleNum_E      = Reg(init = Bits(0, 3))

    val fractR0_A       = Reg(Bits(width = 9 ))
//*** COMBINE `hiSqrR0_A_sqrt' AND `partNegSigma0_A'?
    val hiSqrR0_A_sqrt  = Reg(Bits(width = 10))
    val partNegSigma0_A = Reg(Bits(width = 21))
    val nextMulAdd9A_A  = Reg(Bits(width = 9 ))
    val nextMulAdd9B_A  = Reg(Bits(width = 9 ))
    val ER1_B_sqrt      = Reg(Bits(width = 17))

    val ESqrR1_B_sqrt   = Reg(Bits(width = 32))
    val sigX1_B         = Reg(Bits(width = 58))
    val sqrSigma1_C     = Reg(Bits(width = 33))
    val sigXN_C         = Reg(Bits(width = 58))
    val u_C_sqrt        = Reg(Bits(width = 31))
    val E_E_div         = Reg(Bool())
    val sigT_E          = Reg(Bits(width = 53))
    val extraT_E        = Reg(Bool())
    val isNegRemT_E     = Reg(Bool())
    val isZeroRemT_E    = Reg(Bool())

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val ready_PA = Wire(Bool())
    val ready_PB = Wire(Bool())
    val ready_PC = Wire(Bool())
    val leaving_PA = Wire(Bool())
    val leaving_PB = Wire(Bool())
    val leaving_PC = Wire(Bool())

    val cyc_B10_sqrt = Wire(Bool())
    val cyc_B9_sqrt  = Wire(Bool())
    val cyc_B8_sqrt  = Wire(Bool())
    val cyc_B7_sqrt  = Wire(Bool())

    val cyc_B6 = Wire(Bool())
    val cyc_B5 = Wire(Bool())
    val cyc_B4 = Wire(Bool())
    val cyc_B3 = Wire(Bool())
    val cyc_B2 = Wire(Bool())
    val cyc_B1 = Wire(Bool())

    val cyc_B6_div = Wire(Bool())
    val cyc_B5_div = Wire(Bool())
    val cyc_B4_div = Wire(Bool())
    val cyc_B3_div = Wire(Bool())
    val cyc_B2_div = Wire(Bool())
    val cyc_B1_div = Wire(Bool())

    val cyc_B6_sqrt = Wire(Bool())
    val cyc_B5_sqrt = Wire(Bool())
    val cyc_B4_sqrt = Wire(Bool())
    val cyc_B3_sqrt = Wire(Bool())
    val cyc_B2_sqrt = Wire(Bool())
    val cyc_B1_sqrt = Wire(Bool())

    val cyc_C5 = Wire(Bool())
    val cyc_C4 = Wire(Bool())
    val cyc_C3 = Wire(Bool())
    val cyc_C2 = Wire(Bool())
    val cyc_C1 = Wire(Bool())

    val cyc_E4 = Wire(Bool())
    val cyc_E3 = Wire(Bool())
    val cyc_E2 = Wire(Bool())
    val cyc_E1 = Wire(Bool())

    val zSigma1_B4         = Wire(Bits())
    val sigXNU_B3_CX       = Wire(Bits())
    val zComplSigT_C1_sqrt = Wire(Bits())
    val zComplSigT_C1      = Wire(Bits())

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
//*** NEED TO COMPUTE AS MUCH AS POSSIBLE IN PREVIOUS CYCLE?:
    io.inReady_div :=
//*** REPLACE ALL OF `! cyc_B*_sqrt' BY `! (valid_PB && sqrtOp_PB)'?:
        ready_PA && ! cyc_B7_sqrt && ! cyc_B6_sqrt && ! cyc_B5_sqrt &&
            ! cyc_B4_sqrt && ! cyc_B3 && ! cyc_B2 && ! cyc_B1_sqrt &&
            ! cyc_C5 && ! cyc_C4
    io.inReady_sqrt :=
        ready_PA && ! cyc_B6_sqrt && ! cyc_B5_sqrt && ! cyc_B4_sqrt &&
            ! cyc_B2_div && ! cyc_B1_sqrt
    val cyc_S_div  = io.inReady_div  && io.inValid && ! io.sqrtOp
    val cyc_S_sqrt = io.inReady_sqrt && io.inValid &&   io.sqrtOp
    val cyc_S = cyc_S_div || cyc_S_sqrt

    val signA_S  = io.a(64)
    val expA_S   = io.a(63, 52)
    val fractA_S = io.a(51, 0)
    val specialCodeA_S = expA_S(11, 9)
    val isZeroA_S    = (specialCodeA_S === UInt(0, 3))
    val isSpecialA_S = (specialCodeA_S(2, 1) === UInt(3, 2))

    val signB_S  = io.b(64)
    val expB_S   = io.b(63, 52)
    val fractB_S = io.b(51, 0)
    val specialCodeB_S = expB_S(11, 9)
    val isZeroB_S    = (specialCodeB_S === UInt(0, 3))
    val isSpecialB_S = (specialCodeB_S(2, 1) === UInt(3, 2))

    val sign_S = Mux(io.sqrtOp, signB_S, signA_S ^ signB_S)

    val normalCase_S_div =
        ! isSpecialA_S && ! isSpecialB_S && ! isZeroA_S && ! isZeroB_S
    val normalCase_S_sqrt = ! isSpecialB_S && ! isZeroB_S && ! signB_S
    val normalCase_S = Mux(io.sqrtOp, normalCase_S_sqrt, normalCase_S_div)

    val entering_PA_normalCase_div  = cyc_S_div  && normalCase_S_div
    val entering_PA_normalCase_sqrt = cyc_S_sqrt && normalCase_S_sqrt
    val entering_PA_normalCase =
        entering_PA_normalCase_div || entering_PA_normalCase_sqrt
    val entering_PA =
        entering_PA_normalCase || (cyc_S && (valid_PA || ! ready_PB))
    val entering_PB_S =
        cyc_S && ! normalCase_S && ! valid_PA &&
            (leaving_PB || (! valid_PB && ! ready_PC))
    val entering_PC_S =
        cyc_S && ! normalCase_S && ! valid_PA && ! valid_PB && ready_PC

    when (entering_PA || leaving_PA) {
        valid_PA := entering_PA
    }
    when (entering_PA) {
        sqrtOp_PA       := io.sqrtOp
        sign_PA         := sign_S
        specialCodeB_PA := specialCodeB_S
        fractB_51_PA    := fractB_S(51)
        roundingMode_PA := io.roundingMode
    }
    when (entering_PA && ! io.sqrtOp) {
        specialCodeA_PA := specialCodeA_S
        fractA_51_PA    := fractA_S(51)
    }
    when (entering_PA_normalCase) {
        exp_PA :=
            Mux(io.sqrtOp,
                expB_S,
                expA_S + Cat(Fill(3, expB_S(11)), ~expB_S(10, 0))
            )
        fractB_other_PA := fractB_S(50, 0)
    }
    when (entering_PA_normalCase_div) {
        fractA_other_PA := fractA_S(50, 0)
    }

    val isZeroA_PA    = (specialCodeA_PA === UInt(0, 3))
    val isSpecialA_PA = (specialCodeA_PA(2, 1) === UInt(3, 2))
    val sigA_PA = Cat(UInt(1, 1), fractA_51_PA, fractA_other_PA)

    val isZeroB_PA    = (specialCodeB_PA === UInt(0, 3))
    val isSpecialB_PA = (specialCodeB_PA(2, 1) === UInt(3, 2))
    val sigB_PA = Cat(UInt(1, 1), fractB_51_PA, fractB_other_PA)

    val normalCase_PA =
        Mux(sqrtOp_PA,
            ! isSpecialB_PA && ! isZeroB_PA && ! sign_PA,
            ! isSpecialA_PA && ! isSpecialB_PA && ! isZeroA_PA && ! isZeroB_PA
        )

    val valid_normalCase_leaving_PA = cyc_B4_div || cyc_B7_sqrt
    val valid_leaving_PA =
        Mux(normalCase_PA, valid_normalCase_leaving_PA, ready_PB)
    leaving_PA := valid_PA && valid_leaving_PA
    ready_PA := ! valid_PA || valid_leaving_PA

    val entering_PB_normalCase =
        valid_PA && normalCase_PA && valid_normalCase_leaving_PA
    val entering_PB = entering_PB_S || leaving_PA

    when (entering_PB || leaving_PB) {
        valid_PB := entering_PB
    }
    when (entering_PB) {
        sqrtOp_PB       := Mux(valid_PA, sqrtOp_PA,       io.sqrtOp      )
        sign_PB         := Mux(valid_PA, sign_PA,         sign_S         )
        specialCodeA_PB := Mux(valid_PA, specialCodeA_PA, specialCodeA_S )
        fractA_51_PB    := Mux(valid_PA, fractA_51_PA,    fractA_S(51)   )
        specialCodeB_PB := Mux(valid_PA, specialCodeB_PA, specialCodeB_S )
        fractB_51_PB    := Mux(valid_PA, fractB_51_PA,    fractB_S(51)   )
        roundingMode_PB := Mux(valid_PA, roundingMode_PA, io.roundingMode)
    }
    when (entering_PB_normalCase) {
        exp_PB          := exp_PA
        fractA_0_PB     := fractA_other_PA(0)
        fractB_other_PB := fractB_other_PA
    }

    val isZeroA_PB    = (specialCodeA_PB === UInt(0, 3))
    val isSpecialA_PB = (specialCodeA_PB(2, 1) === UInt(3, 2))
    val isZeroB_PB    = (specialCodeB_PB === UInt(0, 3))
    val isSpecialB_PB = (specialCodeB_PB(2, 1) === UInt(3, 2))
    val normalCase_PB =
        Mux(sqrtOp_PB,
            ! isSpecialB_PB && ! isZeroB_PB && ! sign_PB,
            ! isSpecialA_PB && ! isSpecialB_PB && ! isZeroA_PB && ! isZeroB_PB
        )

    val valid_normalCase_leaving_PB = cyc_C3
    val valid_leaving_PB =
        Mux(normalCase_PB, valid_normalCase_leaving_PB, ready_PC)
    leaving_PB := valid_PB && valid_leaving_PB
    ready_PB := ! valid_PB || valid_leaving_PB

    val entering_PC_normalCase =
        valid_PB && normalCase_PB && valid_normalCase_leaving_PB
    val entering_PC = entering_PC_S || leaving_PB

    when (entering_PC || leaving_PC) {
        valid_PC := entering_PC
    }
    when (entering_PC) {
        sqrtOp_PC       := Mux(valid_PB, sqrtOp_PB,       io.sqrtOp      )
        sign_PC         := Mux(valid_PB, sign_PB,         sign_S         )
        specialCodeA_PC := Mux(valid_PB, specialCodeA_PB, specialCodeA_S )
        fractA_51_PC    := Mux(valid_PB, fractA_51_PB,    fractA_S(51)   )
        specialCodeB_PC := Mux(valid_PB, specialCodeB_PB, specialCodeB_S )
        fractB_51_PC    := Mux(valid_PB, fractB_51_PB,    fractB_S(51)   )
        roundingMode_PC := Mux(valid_PB, roundingMode_PB, io.roundingMode)
    }
    when (entering_PC_normalCase) {
        exp_PC          := exp_PB
        fractA_0_PC     := fractA_0_PB
        fractB_other_PC := fractB_other_PB
    }

    val isZeroA_PC    = (specialCodeA_PC === UInt(0, 3))
    val isSpecialA_PC = (specialCodeA_PC(2, 1) === UInt(3, 2))
    val isInfA_PC     = isSpecialA_PC && ! specialCodeA_PC(0)
    val isNaNA_PC     = isSpecialA_PC &&   specialCodeA_PC(0)
    val isSigNaNA_PC  = isNaNA_PC && ! fractA_51_PC

    val isZeroB_PC    = (specialCodeB_PC === UInt(0, 3))
    val isSpecialB_PC = (specialCodeB_PC(2, 1) === UInt(3, 2))
    val isInfB_PC     = isSpecialB_PC && ! specialCodeB_PC(0)
    val isNaNB_PC     = isSpecialB_PC &&   specialCodeB_PC(0)
    val isSigNaNB_PC  = isNaNB_PC && ! fractB_51_PC
    val sigB_PC       = Cat(UInt(1, 1), fractB_51_PC, fractB_other_PC)

    val normalCase_PC =
        Mux(sqrtOp_PC, ! isSpecialB_PC && ! isZeroB_PC && ! sign_PC,
            ! isSpecialA_PC && ! isSpecialB_PC && ! isZeroA_PC && ! isZeroB_PC)

    val expP2_PC = exp_PC + UInt(2)
    val expP1_PC =
        Mux(exp_PC(0),
            Cat(expP2_PC(13, 1), UInt(0, 1)),
            Cat(exp_PC(13, 1),   UInt(1, 1))
        )

    val roundingMode_near_even_PC = (roundingMode_PC === round_nearest_even)
    val roundingMode_minMag_PC    = (roundingMode_PC === round_minMag)
    val roundingMode_min_PC       = (roundingMode_PC === round_min)
    val roundingMode_max_PC       = (roundingMode_PC === round_max)

    val roundMagUp_PC =
        Mux(sign_PC, roundingMode_min_PC, roundingMode_max_PC)
    val overflowY_roundMagUp_PC = roundingMode_near_even_PC || roundMagUp_PC
    val roundMagDown_PC = ! roundMagUp_PC && ! roundingMode_near_even_PC

    val valid_leaving_PC = ! normalCase_PC || cyc_E1
    leaving_PC := valid_PC && valid_leaving_PC
    ready_PC := ! valid_PC || valid_leaving_PC
    io.outValid_div  := leaving_PC && ! sqrtOp_PC
    io.outValid_sqrt := leaving_PC &&   sqrtOp_PC

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    when (entering_PA_normalCase || (cycleNum_A =/= UInt(0))) {
        cycleNum_A :=
            Mux(entering_PA_normalCase_div,  UInt(3),           UInt(0)) |
            Mux(entering_PA_normalCase_sqrt, UInt(6),           UInt(0)) |
            Mux(! entering_PA_normalCase, cycleNum_A - UInt(1), UInt(0))
    }

    val cyc_A7_sqrt = entering_PA_normalCase_sqrt
    val cyc_A6_sqrt = (cycleNum_A === UInt(6))
    val cyc_A5_sqrt = (cycleNum_A === UInt(5))
    val cyc_A4_sqrt = (cycleNum_A === UInt(4))

    val cyc_A4_div = entering_PA_normalCase_div

    val cyc_A4 = cyc_A4_sqrt || cyc_A4_div
    val cyc_A3 = (cycleNum_A === UInt(3))
    val cyc_A2 = (cycleNum_A === UInt(2))
    val cyc_A1 = (cycleNum_A === UInt(1))

    val cyc_A3_div = cyc_A3 && ! sqrtOp_PA
    val cyc_A2_div = cyc_A2 && ! sqrtOp_PA
    val cyc_A1_div = cyc_A1 && ! sqrtOp_PA

    val cyc_A3_sqrt = cyc_A3 && sqrtOp_PA
    val cyc_A2_sqrt = cyc_A2 && sqrtOp_PA
    val cyc_A1_sqrt = cyc_A1 && sqrtOp_PA

    when (cyc_A1 || (cycleNum_B =/= UInt(0))) {
        cycleNum_B :=
            Mux(cyc_A1,
                Mux(sqrtOp_PA, UInt(10), UInt(6)),
                cycleNum_B - UInt(1)
            )
    }

    cyc_B10_sqrt := (cycleNum_B === UInt(10))
    cyc_B9_sqrt  := (cycleNum_B === UInt(9))
    cyc_B8_sqrt  := (cycleNum_B === UInt(8))
    cyc_B7_sqrt  := (cycleNum_B === UInt(7))

    cyc_B6 := (cycleNum_B === UInt(6))
    cyc_B5 := (cycleNum_B === UInt(5))
    cyc_B4 := (cycleNum_B === UInt(4))
    cyc_B3 := (cycleNum_B === UInt(3))
    cyc_B2 := (cycleNum_B === UInt(2))
    cyc_B1 := (cycleNum_B === UInt(1))

    cyc_B6_div := cyc_B6 && valid_PA && ! sqrtOp_PA
    cyc_B5_div := cyc_B5 && valid_PA && ! sqrtOp_PA
    cyc_B4_div := cyc_B4 && valid_PA && ! sqrtOp_PA
    cyc_B3_div := cyc_B3 && ! sqrtOp_PB
    cyc_B2_div := cyc_B2 && ! sqrtOp_PB
    cyc_B1_div := cyc_B1 && ! sqrtOp_PB

    cyc_B6_sqrt := cyc_B6 && valid_PB && sqrtOp_PB
    cyc_B5_sqrt := cyc_B5 && valid_PB && sqrtOp_PB
    cyc_B4_sqrt := cyc_B4 && valid_PB && sqrtOp_PB
    cyc_B3_sqrt := cyc_B3 && sqrtOp_PB
    cyc_B2_sqrt := cyc_B2 && sqrtOp_PB
    cyc_B1_sqrt := cyc_B1 && sqrtOp_PB

    when (cyc_B1 || (cycleNum_C =/= UInt(0))) {
        cycleNum_C :=
            Mux(cyc_B1, Mux(sqrtOp_PB, UInt(6), UInt(5)), cycleNum_C - UInt(1))
    }

    val cyc_C6_sqrt = (cycleNum_C === UInt(6))

    cyc_C5 := (cycleNum_C === UInt(5))
    cyc_C4 := (cycleNum_C === UInt(4))
    cyc_C3 := (cycleNum_C === UInt(3))
    cyc_C2 := (cycleNum_C === UInt(2))
    cyc_C1 := (cycleNum_C === UInt(1))

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

    when (cyc_C1 || (cycleNum_E =/= UInt(0))) {
        cycleNum_E := Mux(cyc_C1, UInt(4), cycleNum_E - UInt(1))
    }

    cyc_E4 := (cycleNum_E === UInt(4))
    cyc_E3 := (cycleNum_E === UInt(3))
    cyc_E2 := (cycleNum_E === UInt(2))
    cyc_E1 := (cycleNum_E === UInt(1))

    val cyc_E4_div = cyc_E4 && ! sqrtOp_PC
    val cyc_E3_div = cyc_E3 && ! sqrtOp_PC
    val cyc_E2_div = cyc_E2 && ! sqrtOp_PC
    val cyc_E1_div = cyc_E1 && ! sqrtOp_PC

    val cyc_E4_sqrt = cyc_E4 && sqrtOp_PC
    val cyc_E3_sqrt = cyc_E3 && sqrtOp_PC
    val cyc_E2_sqrt = cyc_E2 && sqrtOp_PC
    val cyc_E1_sqrt = cyc_E1 && sqrtOp_PC

    /*------------------------------------------------------------------------
    | Macrostage A, built around a 9x9-bit multiplier-adder.
    *------------------------------------------------------------------------*/
    val zFractB_A4_div = Mux(cyc_A4_div, fractB_S, UInt(0))

    val zLinPiece_0_A4_div = cyc_A4_div && (fractB_S(51, 49) === UInt(0))
    val zLinPiece_1_A4_div = cyc_A4_div && (fractB_S(51, 49) === UInt(1))
    val zLinPiece_2_A4_div = cyc_A4_div && (fractB_S(51, 49) === UInt(2))
    val zLinPiece_3_A4_div = cyc_A4_div && (fractB_S(51, 49) === UInt(3))
    val zLinPiece_4_A4_div = cyc_A4_div && (fractB_S(51, 49) === UInt(4))
    val zLinPiece_5_A4_div = cyc_A4_div && (fractB_S(51, 49) === UInt(5))
    val zLinPiece_6_A4_div = cyc_A4_div && (fractB_S(51, 49) === UInt(6))
    val zLinPiece_7_A4_div = cyc_A4_div && (fractB_S(51, 49) === UInt(7))
    val zK1_A4_div =
        Mux(zLinPiece_0_A4_div, UInt("h1C7", 9), UInt(0)) |
        Mux(zLinPiece_1_A4_div, UInt("h16C", 9), UInt(0)) |
        Mux(zLinPiece_2_A4_div, UInt("h12A", 9), UInt(0)) |
        Mux(zLinPiece_3_A4_div, UInt("h0F8", 9), UInt(0)) |
        Mux(zLinPiece_4_A4_div, UInt("h0D2", 9), UInt(0)) |
        Mux(zLinPiece_5_A4_div, UInt("h0B4", 9), UInt(0)) |
        Mux(zLinPiece_6_A4_div, UInt("h09C", 9), UInt(0)) |
        Mux(zLinPiece_7_A4_div, UInt("h089", 9), UInt(0))
    val zComplFractK0_A4_div =
        Mux(zLinPiece_0_A4_div, ~UInt("hFE3", 12), UInt(0)) |
        Mux(zLinPiece_1_A4_div, ~UInt("hC5D", 12), UInt(0)) |
        Mux(zLinPiece_2_A4_div, ~UInt("h98A", 12), UInt(0)) |
        Mux(zLinPiece_3_A4_div, ~UInt("h739", 12), UInt(0)) |
        Mux(zLinPiece_4_A4_div, ~UInt("h54B", 12), UInt(0)) |
        Mux(zLinPiece_5_A4_div, ~UInt("h3A9", 12), UInt(0)) |
        Mux(zLinPiece_6_A4_div, ~UInt("h242", 12), UInt(0)) |
        Mux(zLinPiece_7_A4_div, ~UInt("h10B", 12), UInt(0))

    val zFractB_A7_sqrt = Mux(cyc_A7_sqrt, fractB_S, UInt(0))

    val zQuadPiece_0_A7_sqrt = cyc_A7_sqrt && ! expB_S(0) && ! fractB_S(51)
    val zQuadPiece_1_A7_sqrt = cyc_A7_sqrt && ! expB_S(0) &&   fractB_S(51)
    val zQuadPiece_2_A7_sqrt = cyc_A7_sqrt &&   expB_S(0) && ! fractB_S(51)
    val zQuadPiece_3_A7_sqrt = cyc_A7_sqrt &&   expB_S(0) &&   fractB_S(51)
    val zK2_A7_sqrt =
        Mux(zQuadPiece_0_A7_sqrt, UInt("h1C8", 9), UInt(0)) |
        Mux(zQuadPiece_1_A7_sqrt, UInt("h0C1", 9), UInt(0)) |
        Mux(zQuadPiece_2_A7_sqrt, UInt("h143", 9), UInt(0)) |
        Mux(zQuadPiece_3_A7_sqrt, UInt("h089", 9), UInt(0))
    val zComplK1_A7_sqrt =
        Mux(zQuadPiece_0_A7_sqrt, ~UInt("h3D0", 10), UInt(0)) |
        Mux(zQuadPiece_1_A7_sqrt, ~UInt("h220", 10), UInt(0)) |
        Mux(zQuadPiece_2_A7_sqrt, ~UInt("h2B2", 10), UInt(0)) |
        Mux(zQuadPiece_3_A7_sqrt, ~UInt("h181", 10), UInt(0))

    val zQuadPiece_0_A6_sqrt = cyc_A6_sqrt && ! exp_PA(0) && ! sigB_PA(51)
    val zQuadPiece_1_A6_sqrt = cyc_A6_sqrt && ! exp_PA(0) &&   sigB_PA(51)
    val zQuadPiece_2_A6_sqrt = cyc_A6_sqrt &&   exp_PA(0) && ! sigB_PA(51)
    val zQuadPiece_3_A6_sqrt = cyc_A6_sqrt &&   exp_PA(0) &&   sigB_PA(51)
    val zComplFractK0_A6_sqrt =
        Mux(zQuadPiece_0_A6_sqrt, ~UInt("h1FE5", 13), UInt(0)) |
        Mux(zQuadPiece_1_A6_sqrt, ~UInt("h1435", 13), UInt(0)) |
        Mux(zQuadPiece_2_A6_sqrt, ~UInt("h0D2C", 13), UInt(0)) |
        Mux(zQuadPiece_3_A6_sqrt, ~UInt("h04E8", 13), UInt(0))

    val mulAdd9A_A =
        zFractB_A4_div(48, 40) | zK2_A7_sqrt |
            Mux(! cyc_S, nextMulAdd9A_A, UInt(0))
    val mulAdd9B_A =
        zK1_A4_div | zFractB_A7_sqrt(50, 42) |
            Mux(! cyc_S, nextMulAdd9B_A, UInt(0))
    val mulAdd9C_A =
        Cat(zComplK1_A7_sqrt,                  Fill(10, cyc_A7_sqrt)) |
        Cat(cyc_A6_sqrt, zComplFractK0_A6_sqrt, Fill(6, cyc_A6_sqrt)) |
        Cat(cyc_A4_div,  zComplFractK0_A4_div,  Fill(8, cyc_A4_div )) |
// CHISEL:
        Mux(cyc_A5_sqrt, UInt(1<<18, 20) + (fractR0_A<<10), UInt(0)) |
        Mux(cyc_A4_sqrt && ! hiSqrR0_A_sqrt(9), UInt(1<<10), UInt(0)) |
        Mux((cyc_A4_sqrt && hiSqrR0_A_sqrt(9)) || cyc_A3_div,
            sigB_PA(46, 26) + UInt(1<<10),
            UInt(0)
        ) |
        Mux(cyc_A3_sqrt || cyc_A2, partNegSigma0_A, UInt(0)) |
        Mux(cyc_A1_sqrt,           fractR0_A<<16,   UInt(0)) |
        Mux(cyc_A1_div,            fractR0_A<<15,   UInt(0))
    val loMulAdd9Out_A =
        mulAdd9A_A * mulAdd9B_A + Cat(UInt(0, 1), mulAdd9C_A(17, 0))
    val mulAdd9Out_A =
        Cat(Mux(loMulAdd9Out_A(18),
                mulAdd9C_A(24, 18) + UInt(1),
                mulAdd9C_A(24, 18)
            ),
            loMulAdd9Out_A(17, 0)
        )

    val zFractR0_A6_sqrt =
        Mux(cyc_A6_sqrt && mulAdd9Out_A(19),
            ~mulAdd9Out_A>>10,
            UInt(0)
        )(8, 0) // CHISEL
    /*------------------------------------------------------------------------
    | (`sqrR0_A5_sqrt' will usually be >= 1, but not always.)
    *------------------------------------------------------------------------*/
    val sqrR0_A5_sqrt = Mux(exp_PA(0), mulAdd9Out_A<<1, mulAdd9Out_A)
    val zFractR0_A4_div =
        Mux(cyc_A4_div && mulAdd9Out_A(20),
            ~mulAdd9Out_A>>11,
            UInt(0)
        )(8, 0) // CHISEL
// CHISEL:
    val zSigma0_A2 =
        Mux(cyc_A2 && mulAdd9Out_A(11), ~mulAdd9Out_A>>2, UInt(0))(8, 0)
// CHISEL:
    val fractR1_A1 =
        Mux(sqrtOp_PA, mulAdd9Out_A>>10, mulAdd9Out_A>>9)(14, 0)
    val r1_A1 = Cat(UInt(1, 1), fractR1_A1)
    val ER1_A1_sqrt = Mux(exp_PA(0), r1_A1<<1, r1_A1)

    when (cyc_A6_sqrt || cyc_A4_div) {
        fractR0_A := zFractR0_A6_sqrt | zFractR0_A4_div
    }

    when (cyc_A5_sqrt) {
        hiSqrR0_A_sqrt := sqrR0_A5_sqrt>>10
    }

    when (cyc_A4_sqrt || cyc_A3) {
// CHISEL:
        partNegSigma0_A :=
            Mux(cyc_A4_sqrt, mulAdd9Out_A, mulAdd9Out_A>>9)(20, 0)
    }

    when (
        cyc_A7_sqrt || cyc_A6_sqrt || cyc_A5_sqrt || cyc_A4 || cyc_A3 || cyc_A2
    ) {
        nextMulAdd9A_A :=
            Mux(cyc_A7_sqrt,           ~mulAdd9Out_A>>11, UInt(0)) |
            zFractR0_A6_sqrt                                       |
            Mux(cyc_A4_sqrt,           sigB_PA(43, 35),   UInt(0)) |
            zFractB_A4_div(43, 35)                                 |
            Mux(cyc_A5_sqrt || cyc_A3, sigB_PA(52, 44),   UInt(0)) |
            zSigma0_A2
    }
    when (cyc_A7_sqrt || cyc_A6_sqrt || cyc_A5_sqrt || cyc_A4 || cyc_A2) {
        nextMulAdd9B_A :=
            zFractB_A7_sqrt(50, 42)                                     |
            zFractR0_A6_sqrt                                            |
            Mux(cyc_A5_sqrt, sqrR0_A5_sqrt(9, 1),              UInt(0)) |
            zFractR0_A4_div                                             |
            Mux(cyc_A4_sqrt, hiSqrR0_A_sqrt(8, 0),             UInt(0)) |
            Mux(cyc_A2,      Cat(UInt(1, 1), fractR0_A(8, 1)), UInt(0))
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
        Mux(cyc_A1_sqrt,               ER1_A1_sqrt<<36,  UInt(0)) | // 52:36
        Mux(cyc_B7_sqrt || cyc_A1_div, sigB_PA,          UInt(0)) | // 52:0
        Mux(cyc_B6_div,                sigA_PA,          UInt(0)) | // 52:0
        zSigma1_B4(45, 12)                                        | // 33:0
//*** ONLY 30 BITS NEEDED IN CYCLE C6:
        Mux(cyc_B3 || cyc_C6_sqrt, sigXNU_B3_CX(57, 12), UInt(0)) | // 45:0
        Mux(cyc_C4_div,            sigXN_C(57, 25)<<13,  UInt(0)) | // 45:13
        Mux(cyc_C4_sqrt,           u_C_sqrt<<15,         UInt(0)) | // 45:15
        Mux(cyc_C1_div,            sigB_PC,              UInt(0)) | // 52:0
        zComplSigT_C1_sqrt                                          // 53:0
    io.latchMulAddB_0 :=
        cyc_A1 || cyc_B7_sqrt || cyc_B6_sqrt || cyc_B4 ||
            cyc_C6_sqrt || cyc_C4 || cyc_C1
    io.mulAddB_0 :=
        Mux(cyc_A1,      r1_A1<<36,          UInt(0)) |  // 51:36
        Mux(cyc_B7_sqrt, ESqrR1_B_sqrt<<19,  UInt(0)) |  // 50:19
        Mux(cyc_B6_sqrt, ER1_B_sqrt<<36,     UInt(0)) |  // 52:36
        zSigma1_B4                                    |  // 45:0
        Mux(cyc_C6_sqrt, sqrSigma1_C(30, 1), UInt(0)) |  // 29:0
        Mux(cyc_C4,      sqrSigma1_C,        UInt(0)) |  // 32:0
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
        Mux(cyc_B1,                  sigX1_B<<47,     UInt(0)) |
        Mux(cyc_C6_sqrt,             sigX1_B<<46,     UInt(0)) |
        Mux(cyc_C4_sqrt || cyc_C2,   sigXN_C<<47,     UInt(0)) |
        Mux(cyc_E3_div && ! E_E_div, fractA_0_PC<<53, UInt(0)) |
        Mux(cyc_E3_sqrt,
            (Mux(exp_PC(0),
                 Cat(sigB_PC(0), UInt(0, 1)),
                 Cat(sigB_PC(1) ^ sigB_PC(0), sigB_PC(0))
             ) ^ Cat(! extraT_E, UInt(0, 1))
            )<<54,
            UInt(0)
        )

    val ESqrR1_B8_sqrt = io.mulAddResult_3(103, 72)
    zSigma1_B4 := Mux(cyc_B4, ~io.mulAddResult_3(90, 45), UInt(0))
    val sqrSigma1_B1 = io.mulAddResult_3(79, 47)
    sigXNU_B3_CX := io.mulAddResult_3(104, 47)   // x1, x2, u (sqrt), xT'
    val E_C1_div = ! io.mulAddResult_3(104)
    zComplSigT_C1 :=
        Mux((cyc_C1_div && ! E_C1_div) || cyc_C1_sqrt,
            ~io.mulAddResult_3(104, 51),
            UInt(0)
        ) |
        Mux(cyc_C1_div && E_C1_div,
            Cat(UInt(0, 1), ~io.mulAddResult_3(102, 50)),
            UInt(0)
        )
    zComplSigT_C1_sqrt :=
        Mux(cyc_C1_sqrt, ~io.mulAddResult_3(104, 51), UInt(0))
    /*------------------------------------------------------------------------
    | (For square root, `sigT_C1' will usually be >= 1, but not always.)
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
        E_E_div  := E_C1_div
        sigT_E   := sigT_C1(53, 1)
        extraT_E := sigT_C1(0)
    }

    when (cyc_E2) {
        isNegRemT_E := Mux(sqrtOp_PC,  remT_E2(55),  remT_E2(53))
        isZeroRemT_E :=
            (remT_E2(53, 0) === UInt(0)) &&
                (! sqrtOp_PC || (remT_E2(55, 54) === UInt(0)))
    }

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val sExpX_E =
        Mux(! sqrtOp_PC &&   E_E_div, exp_PC,                     UInt(0)) |
        Mux(! sqrtOp_PC && ! E_E_div, expP1_PC,                   UInt(0)) |
        Mux( sqrtOp_PC,           (exp_PC>>1) + UInt("h400", 12), UInt(0))

    val posExpX_E = sExpX_E(12, 0)
// *** IMPROVE:
    val roundMask_E = lowMask(posExpX_E, (1<<10) - (3<<4) - 3, (1<<10) + 2)
    val incrPosMask_E =
        ~Cat(UInt(0, 1), roundMask_E) & Cat(roundMask_E, UInt(1, 1))

    val hiRoundPosBitT_E = (sigT_E & incrPosMask_E>>1).orR
    val all0sHiRoundExtraT_E = (( sigT_E & roundMask_E>>1) === UInt(0))
    val all1sHiRoundExtraT_E = ((~sigT_E & roundMask_E>>1) === UInt(0))
    val all1sHiRoundT_E =
        (! roundMask_E(0) || hiRoundPosBitT_E) && all1sHiRoundExtraT_E

//*** FOLD THIS INCREMENT INTO THE MAIN CALCULATION OF T ABOVE?  (BUT DOING
//***  SO COULD IMPACT THE CRITICAL PATH AROUND THE BIG MULTIPLIER.)
    val sigAdjT_E = UInt(0, 54) + sigT_E + roundMagUp_PC // CHISEL
    val sigY0_E = sigAdjT_E & Cat(UInt(1, 1), ~roundMask_E)
    val sigY1_E = (sigAdjT_E | Cat(UInt(0, 1), roundMask_E)) + UInt(1)

    /*------------------------------------------------------------------------
    | X is the best estimate of the result, to 54 bits of precision.
    | T is the lower-bound "trial" result value with 53 bits of precision.
    | X = {T, extraT} + 1.
    *------------------------------------------------------------------------*/
    val trueLtX_E1 =
        Mux(sqrtOp_PC, ! isNegRemT_E && ! isZeroRemT_E, isNegRemT_E)
    val trueEqX_E1 = isZeroRemT_E

    /*------------------------------------------------------------------------
    | (If the rounding position is the last bit, the halfway case is
    | impossible.  However, the halfway case is possible when rounding at
    | other bit positions.)
    *------------------------------------------------------------------------*/
    val hiRoundPosBit_E1 =
        hiRoundPosBitT_E ^
            (roundMask_E(0) && ! trueLtX_E1 && all1sHiRoundExtraT_E &&
                 extraT_E)
    val anyRoundExtra_E1 = ! trueEqX_E1 || ! extraT_E || ! all1sHiRoundExtraT_E
    val roundEvenMask_E1 =
        Mux(roundingMode_near_even_PC && hiRoundPosBit_E1 &&
                ! anyRoundExtra_E1,
//*** CAN SUBSTITUTE `{roundMask_E, UInt("b1", 1)}'.
            incrPosMask_E,
            UInt(0)
        )
    val sigY_E1 =
        Mux((roundMagDown_PC && extraT_E && ! trueLtX_E1 && all1sHiRoundT_E) ||
            (roundMagUp_PC &&
                 ((extraT_E && ! trueLtX_E1 && ! trueEqX_E1) ||
                      ! all1sHiRoundT_E)) ||
            (roundingMode_near_even_PC &&
                 (hiRoundPosBitT_E ||
                      ((extraT_E || ! trueLtX_E1) && ! roundMask_E(0)) ||
                      (extraT_E && ! trueLtX_E1 && all1sHiRoundExtraT_E))),
            sigY1_E,
            sigY0_E
        ) & ~roundEvenMask_E1
    val fractY_E1 = sigY_E1(51, 0)
    val inexactY_E1 = hiRoundPosBit_E1 || anyRoundExtra_E1
    val sExpY_E1 =
        Mux(! sigY_E1(53),                           sExpX_E,  UInt(0)) |
        Mux(sigY_E1(53) && ! sqrtOp_PC &&   E_E_div, expP1_PC, UInt(0)) |
        Mux(sigY_E1(53) && ! sqrtOp_PC && ! E_E_div, expP2_PC, UInt(0)) |
        Mux(sigY_E1(53) && sqrtOp_PC,
            (expP2_PC>>1) + UInt("h400", 12),
            UInt(0)
        )
    val expY_E1 = sExpY_E1(11, 0)

    val overflowY_E1 = ! sExpY_E1(13) && (UInt("b011", 3) <= sExpY_E1(12, 10))
//*** COMPARE WITH MULTIPLIER UNIT:
    val totalUnderflowY_E1 =
        sExpY_E1(13) || (sExpY_E1(12, 0) < UInt("b0001111001110", 13))
    val underflowY_E1 =
        totalUnderflowY_E1 ||
            ((posExpX_E <= UInt("b0010000000001", 13)) && inexactY_E1)

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val notSigNaN_invalid_PC =
        Mux(sqrtOp_PC,
            ! isNaNB_PC && ! isZeroB_PC && sign_PC,
            (isZeroA_PC && isZeroB_PC) || (isInfA_PC && isInfB_PC)
        )
    val invalid_PC =
        (! sqrtOp_PC && isSigNaNA_PC) || isSigNaNB_PC || notSigNaN_invalid_PC
    val infinity_PC =
        ! sqrtOp_PC && ! isSpecialA_PC && ! isZeroA_PC && isZeroB_PC

    val overflow_E1 = normalCase_PC && overflowY_E1
    val underflow_E1 = normalCase_PC && underflowY_E1
//*** SPEED BY USING `normalCase_PC && totalUnderflowY_E1' INSTEAD OF
//***  `underflow_E1'?
    val inexact_E1 =
        overflow_E1 || underflow_E1 || (normalCase_PC && inexactY_E1)

    val notSpecial_isZeroOut_E1 =
        Mux(sqrtOp_PC,
            isZeroB_PC,
            isZeroA_PC || isInfB_PC || (totalUnderflowY_E1 && ! roundMagUp_PC)
        )
    val pegMinFiniteMagOut_E1 =
        normalCase_PC && totalUnderflowY_E1 && roundMagUp_PC
    val pegMaxFiniteMagOut_E1 = overflow_E1 && ! overflowY_roundMagUp_PC
    val notNaN_isInfOut_E1 =
        Mux(sqrtOp_PC,
            isInfB_PC,
            isInfA_PC || isZeroB_PC || (overflow_E1 && overflowY_roundMagUp_PC)
        )
    val isNaNOut_PC =
        (! sqrtOp_PC && isNaNA_PC) || isNaNB_PC || notSigNaN_invalid_PC

    val signOut_PC =
        ! isNaNOut_PC && Mux(sqrtOp_PC, isZeroB_PC && sign_PC, sign_PC)
    val expOut_E1 =
        (expY_E1 &
             ~Mux(notSpecial_isZeroOut_E1,
                  ~UInt("b000111111111", 12),
                  UInt(0)
             ) &
             ~Mux(pegMinFiniteMagOut_E1,
                  ~UInt("b001111001110", 12),
                  UInt(0)
             ) &
             ~Mux(pegMaxFiniteMagOut_E1,
                  ~UInt("b101111111111", 12),
                  UInt(0)
             ) &
             ~Mux(notNaN_isInfOut_E1,
                  ~UInt("b110111111111", 12),
                  UInt(0)
             )) |
            Mux(pegMinFiniteMagOut_E1, UInt("b001111001110", 12), UInt(0)) |
            Mux(pegMaxFiniteMagOut_E1, UInt("b101111111111", 12), UInt(0)) |
            Mux(notNaN_isInfOut_E1,    UInt("b110000000000", 12), UInt(0)) |
            Mux(isNaNOut_PC,           UInt("b111000000000", 12), UInt(0))
    val fractOut_E1 =
        Mux(notSpecial_isZeroOut_E1 || totalUnderflowY_E1 || isNaNOut_PC,
            Mux(isNaNOut_PC, UInt(1)<<51, UInt(0)),
            fractY_E1
        ) |
        Fill(52, pegMaxFiniteMagOut_E1)
    io.out := Cat(signOut_PC, expOut_E1, fractOut_E1)

    io.exceptionFlags :=
        Cat(invalid_PC, infinity_PC, overflow_E1, underflow_E1, inexact_E1)

}

