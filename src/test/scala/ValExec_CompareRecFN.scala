
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
import Chisel._

class ValExec_CompareRecFN_lt(expWidth: Int, sigWidth: Int) extends Module
{
    val io = new Bundle {
        val a = Bits(INPUT, expWidth + sigWidth)
        val b = Bits(INPUT, expWidth + sigWidth)
        val expected = new Bundle {
            val out = Bits(INPUT, 1)
            val exceptionFlags = Bits(INPUT, 5)
        }
        val actual = new Bundle {
            val out = Bits(OUTPUT, 1)
            val exceptionFlags = Bits(OUTPUT, 5)
        }
        val check = Bool(OUTPUT)
        val pass = Bool(OUTPUT)
    }

    val compareRecFN = Module(new CompareRecFN(expWidth, sigWidth))
    compareRecFN.io.a := recFNFromFN(expWidth, sigWidth, io.a)
    compareRecFN.io.b := recFNFromFN(expWidth, sigWidth, io.b)
    compareRecFN.io.signaling := Bool(true)

    io.actual.out := compareRecFN.io.lt
    io.actual.exceptionFlags := compareRecFN.io.exceptionFlags

    io.check := Bool(true)
    io.pass :=
        (io.actual.out === io.expected.out) &&
        (io.actual.exceptionFlags === io.expected.exceptionFlags)
}

class ValExec_CompareRecFN_le(expWidth: Int, sigWidth: Int) extends Module
{
    val io = new Bundle {
        val a = Bits(INPUT, expWidth + sigWidth)
        val b = Bits(INPUT, expWidth + sigWidth)
        val expected = new Bundle {
            val out = Bits(INPUT, 1)
            val exceptionFlags = Bits(INPUT, 5)
        }
        val actual = new Bundle {
            val out = Bits(OUTPUT, 1)
            val exceptionFlags = Bits(OUTPUT, 5)
        }
        val check = Bool(OUTPUT)
        val pass = Bool(OUTPUT)
    }

    val compareRecFN = Module(new CompareRecFN(expWidth, sigWidth))
    compareRecFN.io.a := recFNFromFN(expWidth, sigWidth, io.a)
    compareRecFN.io.b := recFNFromFN(expWidth, sigWidth, io.b)
    compareRecFN.io.signaling := Bool(true)

    io.actual.out := compareRecFN.io.lt || compareRecFN.io.eq
    io.actual.exceptionFlags := compareRecFN.io.exceptionFlags

    io.check := Bool(true)
    io.pass :=
        (io.actual.out === io.expected.out) &&
        (io.actual.exceptionFlags === io.expected.exceptionFlags)
}

class ValExec_CompareRecFN_eq(expWidth: Int, sigWidth: Int) extends Module
{
    val io = new Bundle {
        val a = Bits(INPUT, expWidth + sigWidth)
        val b = Bits(INPUT, expWidth + sigWidth)
        val expected = new Bundle {
            val out = Bits(INPUT, 1)
            val exceptionFlags = Bits(INPUT, 5)
        }
        val actual = new Bundle {
            val out = Bits(OUTPUT, 1)
            val exceptionFlags = Bits(OUTPUT, 5)
        }
        val check = Bool(OUTPUT)
        val pass = Bool(OUTPUT)
    }

    val compareRecFN = Module(new CompareRecFN(expWidth, sigWidth))
    compareRecFN.io.a := recFNFromFN(expWidth, sigWidth, io.a)
    compareRecFN.io.b := recFNFromFN(expWidth, sigWidth, io.b)
    compareRecFN.io.signaling := Bool(false)

    io.actual.out := compareRecFN.io.eq
    io.actual.exceptionFlags := compareRecFN.io.exceptionFlags

    io.check := Bool(true)
    io.pass :=
        (io.actual.out === io.expected.out) &&
        (io.actual.exceptionFlags === io.expected.exceptionFlags)
}

class CompareRecFNSpec extends FMATester {
    def test(f: Int, fn: String): Seq[String] = {
        val generator = fn match {
            case "lt" => () => new ValExec_CompareRecFN_lt(exp(f), sig(f))
            case "le" => () => new ValExec_CompareRecFN_le(exp(f), sig(f))
            case "eq" => () => new ValExec_CompareRecFN_eq(exp(f), sig(f))
        }
        test(
            s"CompareRecF${f}_$fn",
            generator,
            "CompareRecFN.cpp",
            Seq(Seq(s"f${f}_${fn}"))
        )
    }

    "CompareRecF16_lt" should "pass" in {
        check(test(16, "lt"))
    }
    "CompareRecF32_lt" should "pass" in {
        check(test(32, "lt"))
    }
    "CompareRecF64_lt" should "pass" in {
        check(test(64, "lt"))
    }
    "CompareRecF16_le" should "pass" in {
        check(test(16, "le"))
    }
    "CompareRecF32_le" should "pass" in {
        check(test(32, "le"))
    }
    "CompareRecF64_le" should "pass" in {
        check(test(64, "le"))
    }
    "CompareRecF16_eq" should "pass" in {
        check(test(16, "eq"))
    }
    "CompareRecF32_eq" should "pass" in {
        check(test(32, "eq"))
    }
    "CompareRecF64_eq" should "pass" in {
        check(test(64, "eq"))
  }
}