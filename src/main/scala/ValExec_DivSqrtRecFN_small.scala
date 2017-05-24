
/*============================================================================

This Chisel source file is part of a pre-release version of the HardFloat IEEE
Floating-Point Arithmetic Package, by John R. Hauser (with some contributions
from Yunsup Lee and Andrew Waterman, mainly concerning testing).

Copyright 2017 SiFive, Inc.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice,
    this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions, and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 3. Neither the name of SiFive nor the names of its contributors may
    be used to endorse or promote products derived from this software without
    specific prior written permission.

THIS SOFTWARE IS PROVIDED BY SIFIVE AND CONTRIBUTORS "AS IS", AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, ARE
DISCLAIMED.  IN NO EVENT SHALL SIFIVE OR CONTRIBUTORS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

=============================================================================*/

package hardfloat

import Chisel._

class DivRecFN_io(expWidth: Int, sigWidth: Int) extends Bundle {
    val a = Bits(width = expWidth + sigWidth)
    val b = Bits(width = expWidth + sigWidth)
    val roundingMode   = UInt(width = 3)
    val detectTininess = UInt(width = 1)
    val out = Bits(width = expWidth + sigWidth)
    val exceptionFlags = Bits(width = 5)

    override def cloneType =
        new DivRecFN_io(expWidth, sigWidth).asInstanceOf[this.type]
}

class
    ValExec_DivSqrtRecFN_small_div(expWidth: Int, sigWidth: Int) extends Module
{
    val io = new Bundle {
        val input = Decoupled(new DivRecFN_io(expWidth, sigWidth)).flip

        val output = new Bundle {
            val a = Bits(OUTPUT, expWidth + sigWidth)
            val b = Bits(OUTPUT, expWidth + sigWidth)
            val roundingMode   = UInt(OUTPUT, 3)
            val detectTininess = UInt(OUTPUT, 1)
        }

        val expected = new Bundle {
            val out = Bits(OUTPUT, expWidth + sigWidth)
            val exceptionFlags = Bits(OUTPUT, 5)
            val recOut = Bits(OUTPUT, expWidth + sigWidth + 1)
        }

        val actual = new Bundle {
            val out = Bits(OUTPUT, expWidth + sigWidth + 1)
            val exceptionFlags = Bits(OUTPUT, 5)
        }

        val check = Bool(OUTPUT)
        val pass = Bool(OUTPUT)
    }

    val ds = Module(new DivSqrtRecFN_small(expWidth, sigWidth, 0))
    val cq = Module(new Queue(new DivRecFN_io(expWidth, sigWidth), 5))

    cq.io.enq.valid := io.input.valid && ds.io.inReady
    cq.io.enq.bits := io.input.bits

    io.input.ready := ds.io.inReady && cq.io.enq.ready
    ds.io.inValid := io.input.valid && cq.io.enq.ready
    ds.io.sqrtOp := Bool(false)
    ds.io.a := recFNFromFN(expWidth, sigWidth, io.input.bits.a)
    ds.io.b := recFNFromFN(expWidth, sigWidth, io.input.bits.b)
    ds.io.roundingMode   := io.input.bits.roundingMode
    ds.io.detectTininess := io.input.bits.detectTininess

    io.output.a := cq.io.deq.bits.a
    io.output.b := cq.io.deq.bits.b
    io.output.roundingMode   := cq.io.deq.bits.roundingMode
    io.output.detectTininess := cq.io.deq.bits.detectTininess

    io.expected.out := cq.io.deq.bits.out
    io.expected.exceptionFlags := cq.io.deq.bits.exceptionFlags
    io.expected.recOut := recFNFromFN(expWidth, sigWidth, cq.io.deq.bits.out)

    io.actual.out := ds.io.out
    io.actual.exceptionFlags := ds.io.exceptionFlags

    cq.io.deq.ready := ds.io.outValid_div

    io.check := ds.io.outValid_div
    io.pass :=
        cq.io.deq.valid &&
        equivRecFN(expWidth, sigWidth, io.actual.out, io.expected.recOut) &&
        (io.actual.exceptionFlags === io.expected.exceptionFlags)
}

class
    ValExec_DivSqrtRecF16_small_div
        extends ValExec_DivSqrtRecFN_small_div(5, 11)
class
    ValExec_DivSqrtRecF32_small_div
        extends ValExec_DivSqrtRecFN_small_div(8, 24)
class
    ValExec_DivSqrtRecF64_small_div
        extends ValExec_DivSqrtRecFN_small_div(11, 53)

class SqrtRecFN_io(expWidth: Int, sigWidth: Int) extends Bundle {
    val a = Bits(width = expWidth + sigWidth)
    val roundingMode   = UInt(width = 3)
    val detectTininess = UInt(width = 1)
    val out = Bits(width = expWidth + sigWidth)
    val exceptionFlags = Bits(width = 5)

    override def cloneType =
        new SqrtRecFN_io(expWidth, sigWidth).asInstanceOf[this.type]
}

class
    ValExec_DivSqrtRecFN_small_sqrt(expWidth: Int, sigWidth: Int)
    extends Module
{
    val io = new Bundle {
        val input = Decoupled(new SqrtRecFN_io(expWidth, sigWidth)).flip

        val output = new Bundle {
            val a = Bits(OUTPUT, expWidth + sigWidth)
            val roundingMode   = UInt(OUTPUT, 3)
            val detectTininess = UInt(OUTPUT, 1)
        }

        val expected = new Bundle {
            val out = Bits(OUTPUT, expWidth + sigWidth)
            val exceptionFlags = Bits(OUTPUT, 5)
            val recOut = Bits(OUTPUT, expWidth + sigWidth + 1)
        }

        val actual = new Bundle {
            val out = Bits(OUTPUT, expWidth + sigWidth + 1)
            val exceptionFlags = Bits(OUTPUT, 5)
        }

        val check = Bool(OUTPUT)
        val pass = Bool(OUTPUT)
    }

    val ds = Module(new DivSqrtRecFN_small(expWidth, sigWidth, 0))
    val cq = Module(new Queue(new SqrtRecFN_io(expWidth, sigWidth), 5))

    cq.io.enq.valid := io.input.valid && ds.io.inReady
    cq.io.enq.bits := io.input.bits

    io.input.ready := ds.io.inReady && cq.io.enq.ready
    ds.io.inValid := io.input.valid && cq.io.enq.ready
    ds.io.sqrtOp := Bool(true)
    ds.io.a := recFNFromFN(expWidth, sigWidth, io.input.bits.a)
    ds.io.roundingMode   := io.input.bits.roundingMode
    ds.io.detectTininess := io.input.bits.detectTininess

    io.output.a := cq.io.deq.bits.a
    io.output.roundingMode   := cq.io.deq.bits.roundingMode
    io.output.detectTininess := cq.io.deq.bits.detectTininess

    io.expected.out := cq.io.deq.bits.out
    io.expected.exceptionFlags := cq.io.deq.bits.exceptionFlags
    io.expected.recOut := recFNFromFN(expWidth, sigWidth, cq.io.deq.bits.out)

    io.actual.exceptionFlags := ds.io.exceptionFlags
    io.actual.out := ds.io.out

    cq.io.deq.ready := ds.io.outValid_sqrt

    io.check := ds.io.outValid_sqrt
    io.pass :=
        cq.io.deq.valid &&
        equivRecFN(expWidth, sigWidth, io.actual.out, io.expected.recOut) &&
        (io.actual.exceptionFlags === io.expected.exceptionFlags)
}

class
    ValExec_DivSqrtRecF16_small_sqrt
        extends ValExec_DivSqrtRecFN_small_sqrt(5, 11)
class
    ValExec_DivSqrtRecF32_small_sqrt
        extends ValExec_DivSqrtRecFN_small_sqrt(8, 24)
class
    ValExec_DivSqrtRecF64_small_sqrt
        extends ValExec_DivSqrtRecFN_small_sqrt(11, 53)

