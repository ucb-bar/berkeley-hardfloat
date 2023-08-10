
/*============================================================================

This Chisel source file is part of a pre-release version of the HardFloat IEEE
Floating-Point Arithmetic Package, by John R. Hauser (with some contributions
from Yunsup Lee and Andrew Waterman, mainly concerning testing).

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

package hardfloat.test

import hardfloat._
import chisel3._

class
    ValExec_RecFNToUIN(expWidth: Int, sigWidth: Int, intWidth: Int)
    extends Module
{
    val io = IO(new Bundle {
        val in = Input(Bits((expWidth + sigWidth).W))
        val roundingMode   = Input(UInt(3.W))

        val expected = new Bundle {
            val out = Input(Bits(intWidth.W))
            val exceptionFlags = Input(Bits(5.W))
        }

        val actual = new Bundle {
            val out = Output(Bits(intWidth.W))
            val exceptionFlags = Output(Bits(5.W))
        }

        val check = Output(Bool())
        val pass = Output(Bool())
    })
    val recFNToIN = Module(new RecFNToIN(expWidth, sigWidth, intWidth))
    recFNToIN.io.in := recFNFromFN(expWidth, sigWidth, io.in)
    recFNToIN.io.roundingMode := io.roundingMode
    recFNToIN.io.signedOut := false.B

    io.actual.out := recFNToIN.io.out
    io.actual.exceptionFlags :=
        recFNToIN.io.intExceptionFlags(2, 1).orR ##
            0.U(3.W) ##
            recFNToIN.io.intExceptionFlags(0)


    io.check := true.B
    io.pass :=
        (io.actual.out === io.expected.out) &&
        (io.actual.exceptionFlags === io.expected.exceptionFlags)
}


class
    ValExec_RecFNToIN(expWidth: Int, sigWidth: Int, intWidth: Int)
    extends Module
{
    val io = IO(new Bundle {
        val in = Input(Bits((expWidth + sigWidth).W))
        val roundingMode   = Input(UInt(3.W))

        val expected = new Bundle {
            val out = Input(Bits(intWidth.W))
            val exceptionFlags = Input(Bits(5.W))
        }

        val actual = new Bundle {
            val out = Output(Bits(intWidth.W))
            val exceptionFlags = Output(Bits(5.W))
        }

        val check = Output(Bool())
        val pass = Output(Bool())
    })

    val recFNToIN = Module(new RecFNToIN(expWidth, sigWidth, intWidth))
    recFNToIN.io.in := recFNFromFN(expWidth, sigWidth, io.in)
    recFNToIN.io.roundingMode := io.roundingMode
    recFNToIN.io.signedOut := true.B

    io.actual.out := recFNToIN.io.out
    io.actual.exceptionFlags :=
        recFNToIN.io.intExceptionFlags(2, 1).orR ##
          0.U(3.W) ##
          recFNToIN.io.intExceptionFlags(0)


    io.check := true.B
    io.pass :=
        (io.actual.out === io.expected.out) &&
        (io.actual.exceptionFlags === io.expected.exceptionFlags)
}