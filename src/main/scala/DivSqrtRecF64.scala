
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
import consts._

class DivSqrtRecF64 extends Module
{
    val io = IO(new Bundle {
        val inReady_div  = Bool(OUTPUT)
        val inReady_sqrt = Bool(OUTPUT)
        val inValid = Bool(INPUT)
        val sqrtOp = Bool(INPUT)
        val a = Bits(INPUT, 65)
        val b = Bits(INPUT, 65)
        val roundingMode   = UInt(INPUT, 3)
        val detectTininess = UInt(INPUT, 1)
        val outValid_div  = Bool(OUTPUT)
        val outValid_sqrt = Bool(OUTPUT)
        val out = Bits(OUTPUT, 65)
        val exceptionFlags = Bits(OUTPUT, 5)
    })

    val ds = Module(new DivSqrtRecF64_mulAddZ31(0))

    io.inReady_div    := ds.io.inReady_div
    io.inReady_sqrt   := ds.io.inReady_sqrt
    ds.io.inValid        := io.inValid
    ds.io.sqrtOp         := io.sqrtOp
    ds.io.a              := io.a
    ds.io.b              := io.b
    ds.io.roundingMode   := io.roundingMode
    ds.io.detectTininess := io.detectTininess
    io.outValid_div   := ds.io.outValid_div
    io.outValid_sqrt  := ds.io.outValid_sqrt
    io.out            := ds.io.out
    io.exceptionFlags := ds.io.exceptionFlags

    val mul = Module(new Mul54)

    mul.io.val_s0     := ds.io.usingMulAdd(0)
    mul.io.latch_a_s0 := ds.io.latchMulAddA_0
    mul.io.a_s0       := ds.io.mulAddA_0
    mul.io.latch_b_s0 := ds.io.latchMulAddB_0
    mul.io.b_s0       := ds.io.mulAddB_0
    mul.io.c_s2       := ds.io.mulAddC_2
    ds.io.mulAddResult_3 := mul.io.result_s3
}

class Mul54 extends Module
{
    val io = IO(new Bundle {
        val val_s0 = Bool(INPUT)
        val latch_a_s0 = Bool(INPUT)
        val a_s0 = UInt(INPUT, 54)
        val latch_b_s0 = Bool(INPUT)
        val b_s0 = UInt(INPUT, 54)
        val c_s2 = UInt(INPUT, 105)
        val result_s3 = UInt(OUTPUT, 105)
    })

    val val_s1 = Reg(Bool())
    val val_s2 = Reg(Bool())
    val reg_a_s1 = Reg(UInt(width = 54))
    val reg_b_s1 = Reg(UInt(width = 54))
    val reg_a_s2 = Reg(UInt(width = 54))
    val reg_b_s2 = Reg(UInt(width = 54))
    val reg_result_s3 = Reg(UInt(width = 105))

    val_s1 := io.val_s0
    val_s2 := val_s1

    when (io.val_s0) {
        when (io.latch_a_s0) {
            reg_a_s1 := io.a_s0
        }
        when (io.latch_b_s0) {
            reg_b_s1 := io.b_s0
        }
    }

    when (val_s1) {
        reg_a_s2 := reg_a_s1
        reg_b_s2 := reg_b_s1
    }

    when (val_s2) {
        reg_result_s3 := (reg_a_s2 * reg_b_s2)(104,0) + io.c_s2
    }

    io.result_s3 := reg_result_s3
}

