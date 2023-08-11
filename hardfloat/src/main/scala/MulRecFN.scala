
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
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES
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
class MulFullRawFN(expWidth: Int, sigWidth: Int) extends chisel3.RawModule
{
    val io = IO(new Bundle {
        val a = Input(new RawFloat(expWidth, sigWidth))
        val b = Input(new RawFloat(expWidth, sigWidth))
        val invalidExc = Output(Bool())
        val rawOut = Output(new RawFloat(expWidth, sigWidth*2 - 1))
    })

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val notSigNaN_invalidExc = (io.a.isInf && io.b.isZero) || (io.a.isZero && io.b.isInf)
    val notNaN_isInfOut = io.a.isInf || io.b.isInf
    val notNaN_isZeroOut = io.a.isZero || io.b.isZero
    val notNaN_signOut = io.a.sign ^ io.b.sign
    val common_sExpOut = io.a.sExp + io.b.sExp - (1<<expWidth).S
    val common_sigOut = (io.a.sig * io.b.sig)(sigWidth*2 - 1, 0)
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

class MulRawFN(expWidth: Int, sigWidth: Int) extends chisel3.RawModule
{
    val io = IO(new Bundle {
        val a = Input(new RawFloat(expWidth, sigWidth))
        val b = Input(new RawFloat(expWidth, sigWidth))
        val invalidExc = Output(Bool())
        val rawOut = Output(new RawFloat(expWidth, sigWidth + 2))
    })

    val mulFullRaw = Module(new MulFullRawFN(expWidth, sigWidth))

    mulFullRaw.io.a := io.a
    mulFullRaw.io.b := io.b

    io.invalidExc := mulFullRaw.io.invalidExc
    io.rawOut := mulFullRaw.io.rawOut
    io.rawOut.sig := {
      val sig = mulFullRaw.io.rawOut.sig
      Cat(sig >> (sigWidth - 2), sig(sigWidth - 3, 0).orR)
    }
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------

class MulRecFN(expWidth: Int, sigWidth: Int) extends chisel3.RawModule
{
    val io = IO(new Bundle {
        val a = Input(UInt((expWidth + sigWidth + 1).W))
        val b = Input(UInt((expWidth + sigWidth + 1).W))
        val roundingMode = Input(UInt(3.W))
        val detectTininess = Input(Bool())
        val out = Output(UInt((expWidth + sigWidth + 1).W))
        val exceptionFlags = Output(UInt(5.W))
    })

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val mulRawFN = Module(new MulRawFN(expWidth, sigWidth))

    mulRawFN.io.a := rawFloatFromRecFN(expWidth, sigWidth, io.a)
    mulRawFN.io.b := rawFloatFromRecFN(expWidth, sigWidth, io.b)

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val roundRawFNToRecFN =
        Module(new RoundRawFNToRecFN(expWidth, sigWidth, 0))
    roundRawFNToRecFN.io.invalidExc   := mulRawFN.io.invalidExc
    roundRawFNToRecFN.io.infiniteExc  := false.B
    roundRawFNToRecFN.io.in           := mulRawFN.io.rawOut
    roundRawFNToRecFN.io.roundingMode := io.roundingMode
    roundRawFNToRecFN.io.detectTininess := io.detectTininess
    io.out            := roundRawFNToRecFN.io.out
    io.exceptionFlags := roundRawFNToRecFN.io.exceptionFlags
}

