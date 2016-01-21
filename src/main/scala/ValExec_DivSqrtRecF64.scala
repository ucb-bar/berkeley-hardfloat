
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

class DivRecF64_io extends Bundle {
    val a = Bits(width = 64)
    val b = Bits(width = 64)
    val roundingMode = Bits(width = 2)
    val out = Bits(width = 64)
    val exceptionFlags = Bits(width = 5)
}

class ValExec_DivSqrtRecF64_div extends Module {
    val io = new Bundle {
        val input = Decoupled(new DivRecF64_io).flip

        val output = new Bundle {
            val a = Bits(OUTPUT, 64)
            val b = Bits(OUTPUT, 64)
            val roundingMode = Bits(OUTPUT, 2)
        }

        val expected = new Bundle {
            val out = Bits(OUTPUT, 64)
            val exceptionFlags = Bits(OUTPUT, 5)
            val recOut = Bits(OUTPUT, 65)
        }

        val actual = new Bundle {
            val out = Bits(OUTPUT, 65)
            val exceptionFlags = Bits(OUTPUT, 5)
        }

        val check = Bool(OUTPUT)
        val pass = Bool(OUTPUT)
    }

    val ds = Module(new DivSqrtRecF64)
    val cq = Module(new Queue(new DivRecF64_io, 5))

    cq.io.enq.valid := io.input.valid && ds.io.inReady_div
    cq.io.enq.bits := io.input.bits

    io.input.ready := ds.io.inReady_div && cq.io.enq.ready
    ds.io.inValid := io.input.valid && cq.io.enq.ready
    ds.io.sqrtOp := Bool(false)
    ds.io.a := recFNFromFN(11, 53, io.input.bits.a)
    ds.io.b := recFNFromFN(11, 53, io.input.bits.b)
    ds.io.roundingMode := io.input.bits.roundingMode

    io.output.a := cq.io.deq.bits.a
    io.output.b := cq.io.deq.bits.b
    io.output.roundingMode := cq.io.deq.bits.roundingMode

    io.expected.out := cq.io.deq.bits.out
    io.expected.exceptionFlags := cq.io.deq.bits.exceptionFlags
    io.expected.recOut := recFNFromFN(11, 53, cq.io.deq.bits.out)

    io.actual.out := ds.io.out
    io.actual.exceptionFlags := ds.io.exceptionFlags

    cq.io.deq.ready := ds.io.outValid_div

    io.check := ds.io.outValid_div
    io.pass :=
        cq.io.deq.valid &&
        equivRecFN(11, 53, io.actual.out, io.expected.recOut) &&
        (io.actual.exceptionFlags === io.expected.exceptionFlags)
}

class SqrtRecF64_io extends Bundle {
    val b = Bits(width = 64)
    val roundingMode = Bits(width = 2)
    val out = Bits(width = 64)
    val exceptionFlags = Bits(width = 5)
}

class ValExec_DivSqrtRecF64_sqrt extends Module {
    val io = new Bundle {
        val input = Decoupled(new SqrtRecF64_io).flip

        val output = new Bundle {
            val b = Bits(OUTPUT, 64)
            val roundingMode = Bits(OUTPUT, 2)
        }

        val expected = new Bundle {
            val out = Bits(OUTPUT, 64)
            val exceptionFlags = Bits(OUTPUT, 5)
            val recOut = Bits(OUTPUT, 65)
        }

        val actual = new Bundle {
            val out = Bits(OUTPUT, 65)
            val exceptionFlags = Bits(OUTPUT, 5)
        }

        val check = Bool(OUTPUT)
        val pass = Bool(OUTPUT)
    }

    val ds = Module(new DivSqrtRecF64)
    val cq = Module(new Queue(new SqrtRecF64_io, 5))

    cq.io.enq.valid := io.input.valid && ds.io.inReady_sqrt
    cq.io.enq.bits := io.input.bits

    io.input.ready := ds.io.inReady_sqrt && cq.io.enq.ready
    ds.io.inValid := io.input.valid && cq.io.enq.ready
    ds.io.sqrtOp := Bool(true)
    ds.io.b := recFNFromFN(11, 53, io.input.bits.b)
    ds.io.roundingMode := io.input.bits.roundingMode

    io.output.b := cq.io.deq.bits.b
    io.output.roundingMode := cq.io.deq.bits.roundingMode

    io.expected.out := cq.io.deq.bits.out
    io.expected.exceptionFlags := cq.io.deq.bits.exceptionFlags
    io.expected.recOut := recFNFromFN(11, 53, cq.io.deq.bits.out)

    io.actual.exceptionFlags := ds.io.exceptionFlags
    io.actual.out := ds.io.out

    cq.io.deq.ready := ds.io.outValid_sqrt

    io.check := ds.io.outValid_sqrt
    io.pass :=
        cq.io.deq.valid &&
        equivRecFN(11, 53, io.actual.out, io.expected.recOut) &&
        (io.actual.exceptionFlags === io.expected.exceptionFlags)
}

