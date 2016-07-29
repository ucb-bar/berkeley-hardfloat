
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

class DivSqrtRecFN(expWidth: Int, sigWidth: Int) extends Module
{
  class DivSqrtInput extends Bundle {
    val sqrtOp = Bool(INPUT)
    val roundingMode = Bits(INPUT, 2)
    val a = Bits(INPUT, expWidth + sigWidth + 1)
    val b = Bits(INPUT, expWidth + sigWidth + 1)
  }

  val io = new Bundle {
    val in = Decoupled(new DivSqrtInput).flip
    val out = Decoupled(new Bundle {
      val out = Bits(OUTPUT, expWidth + sigWidth + 1)
      val exceptionFlags = Bits(OUTPUT, 5)
    })
  }

  val valid = Reg(init = Bool(false))
  val bits = Reg(new DivSqrtInput)
  val res = Reg(new RawFloat(expWidth, sigWidth))
  val nSig = expWidth + 7
  val count = Reg(UInt(width = log2Ceil(nSig + 1)))
  val isInvalid = Reg(Bool())

  io.out.valid := valid && count >= nSig
  io.in.ready := !valid
  when (io.out.fire()) {
    valid := Bool(false)
  }
  when (io.in.fire()) {
    valid := Bool(true)
    count := UInt(0)
    isInvalid := Bool(false)
    bits := io.in.bits
    res := rawFNFromRecFN(expWidth, sigWidth, UInt(0, expWidth + sigWidth + 1))
  }
  when (valid && count < nSig) {
    count := count + UInt(1)
  }

  val rawA = rawFNFromRecFN(expWidth, sigWidth, bits.a)
  val rawB = rawFNFromRecFN(expWidth, sigWidth, bits.b)

  when (valid) {
    when (bits.sqrtOp && count === UInt(0)) {
      when (rawB.isNaN || rawB.isZero) {
        res.isNaN := rawB.isNaN
        res.isZero := rawB.isZero
        count := nSig
      }
      when (rawB.sign && !rawB.isZero) {
        isInvalid := Bool(true)
        count := nSig
      }
    }
  }
}
