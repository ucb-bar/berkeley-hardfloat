
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

package hardfloat

import Chisel._

class
    ValExec_UINToRecFN(intWidth: Int, expWidth: Int, sigWidth: Int)
    extends Module
{
    val io = new Bundle {
        val in = Bits(INPUT, intWidth)
        val roundingMode   = UInt(INPUT, 3)
        val detectTininess = UInt(INPUT, 1)

        val expected = new Bundle {
            val out = Bits(INPUT, expWidth + sigWidth)
            val exceptionFlags = Bits(INPUT, 5)
            val recOut = Bits(OUTPUT, expWidth + sigWidth + 1)
        }

        val actual = new Bundle {
            val out = Bits(OUTPUT, expWidth + sigWidth + 1)
            val exceptionFlags = Bits(OUTPUT, 5)
        }

        val check = Bool(OUTPUT)
        val pass = Bool(OUTPUT)
    }

    val iNToRecFN = Module(new INToRecFN(intWidth, expWidth, sigWidth))
    iNToRecFN.io.signedIn := Bool(false)
    iNToRecFN.io.in := io.in
    iNToRecFN.io.roundingMode   := io.roundingMode
    iNToRecFN.io.detectTininess := io.detectTininess

    io.expected.recOut := recFNFromFN(expWidth, sigWidth, io.expected.out)

    io.actual.out := iNToRecFN.io.out
    io.actual.exceptionFlags := iNToRecFN.io.exceptionFlags

    io.check := Bool(true)
    io.pass :=
        equivRecFN(expWidth, sigWidth, io.actual.out, io.expected.recOut) &&
        (io.actual.exceptionFlags === io.expected.exceptionFlags)
}

class ValExec_UI32ToRecF16 extends ValExec_UINToRecFN(32, 5, 11)
class ValExec_UI32ToRecF32 extends ValExec_UINToRecFN(32, 8, 24)
class ValExec_UI32ToRecF64 extends ValExec_UINToRecFN(32, 11, 53)
class ValExec_UI64ToRecF16 extends ValExec_UINToRecFN(64, 5, 11)
class ValExec_UI64ToRecF32 extends ValExec_UINToRecFN(64, 8, 24)
class ValExec_UI64ToRecF64 extends ValExec_UINToRecFN(64, 11, 53)

class
    ValExec_INToRecFN(intWidth: Int, expWidth: Int, sigWidth: Int)
    extends Module
{
    val io = new Bundle {
        val in = Bits(INPUT, intWidth)
        val roundingMode   = UInt(INPUT, 3)
        val detectTininess = UInt(INPUT, 1)

        val expected = new Bundle {
            val out = Bits(INPUT, expWidth + sigWidth)
            val exceptionFlags = Bits(INPUT, 5)
            val recOut = Bits(OUTPUT, expWidth + sigWidth + 1)
        }

        val actual = new Bundle {
            val out = Bits(OUTPUT, expWidth + sigWidth + 1)
            val exceptionFlags = Bits(OUTPUT, 5)
        }

        val check = Bool(OUTPUT)
        val pass = Bool(OUTPUT)
    }

    val iNToRecFN = Module(new INToRecFN(intWidth, expWidth, sigWidth))
    iNToRecFN.io.signedIn := Bool(true)
    iNToRecFN.io.in := io.in
    iNToRecFN.io.roundingMode   := io.roundingMode
    iNToRecFN.io.detectTininess := io.detectTininess

    io.expected.recOut := recFNFromFN(expWidth, sigWidth, io.expected.out)

    io.actual.out := iNToRecFN.io.out
    io.actual.exceptionFlags := iNToRecFN.io.exceptionFlags

    io.check := Bool(true)
    io.pass :=
        equivRecFN(expWidth, sigWidth, io.actual.out, io.expected.recOut) &&
        (io.actual.exceptionFlags === io.expected.exceptionFlags)
}

class ValExec_I32ToRecF16 extends ValExec_INToRecFN(32, 5, 11)
class ValExec_I32ToRecF32 extends ValExec_INToRecFN(32, 8, 24)
class ValExec_I32ToRecF64 extends ValExec_INToRecFN(32, 11, 53)
class ValExec_I64ToRecF16 extends ValExec_INToRecFN(64, 5, 11)
class ValExec_I64ToRecF32 extends ValExec_INToRecFN(64, 8, 24)
class ValExec_I64ToRecF64 extends ValExec_INToRecFN(64, 11, 53)

