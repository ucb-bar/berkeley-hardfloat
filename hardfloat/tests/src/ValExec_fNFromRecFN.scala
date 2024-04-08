
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

// Put these functions into dedicated classes so that the generated RTL
// provides dedicated conversion modules that we can instantiate when needed

class fn_from_recfn(expWidth: Int, sigWidth: Int) extends RawModule
{
    val io = IO(new Bundle {
        val i =  Input(UInt((expWidth + sigWidth + 1).W))
        val o = Output(Bits((expWidth + sigWidth).W))
    })
    io.o := fNFromRecFN(expWidth, sigWidth, io.i)
}

class recfn_from_fn(expWidth: Int, sigWidth: Int) extends RawModule
{
    val io = IO(new Bundle {
        val i =  Input(Bits((expWidth + sigWidth).W))
        val o = Output(UInt((expWidth + sigWidth + 1).W))
    })
    io.o := recFNFromFN(expWidth, sigWidth, io.i)
}

class ValExec_fNFromRecFN(expWidth: Int, sigWidth: Int) extends Module
{
    val io = IO(new Bundle {
        val a = Input(Bits((expWidth + sigWidth).W))
        val out = Output(Bits((expWidth + sigWidth).W))
        val check = Output(Bool())
        val pass = Output(Bool())
    })

    val encode = Module(new recfn_from_fn(expWidth, sigWidth))
    val decode = Module(new fn_from_recfn(expWidth, sigWidth))

    encode.io.i := io.a
    decode.io.i := encode.io.o
    io.out      := decode.io.o

    io.check := true.B
    io.pass := (io.out === io.a)
}
