
/*============================================================================

This Chisel source file is part of a pre-release version of the HardFloat IEEE
Floating-Point Arithmetic Package, by John R. Hauser (with some contributions
from Yunsup Lee and Andrew Waterman, mainly concerning testing).

Copyright 2010, 2011, 2012, 2013, 2014, 2015, 2016, 2017 The Regents of the
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

import chisel3._
import chisel3.util._

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
object lowMask
{
    def apply(in: UInt, topBound: BigInt, bottomBound: BigInt): UInt =
    {
        require(topBound != bottomBound)
        val numInVals = BigInt(1)<<in.getWidth
        if (topBound < bottomBound) {
            lowMask(~in, numInVals - 1 - topBound, numInVals - 1 - bottomBound)
        } else if (numInVals > 64 /* Empirical */) {
            // For simulation performance, we should avoid generating
            // exteremely wide shifters, so we divide and conquer.
            // Empirically, this does not impact synthesis QoR.
            val mid = numInVals / 2
            val msb = in(in.getWidth - 1)
            val lsbs = in(in.getWidth - 2, 0)
            if (mid < topBound) {
                if (mid <= bottomBound) {
                    Mux(msb,
                        lowMask(lsbs, topBound - mid, bottomBound - mid),
                        0.U
                    )
                } else {
                    Mux(msb,
                        lowMask(lsbs, topBound - mid, 0) ## ((BigInt(1)<<(mid - bottomBound).toInt) - 1).U,
                        lowMask(lsbs, mid, bottomBound)
                    )
                }
            } else {
                ~Mux(msb, 0.U, ~lowMask(lsbs, topBound, bottomBound))
            }
        } else {
            val shift = (BigInt(-1)<<numInVals.toInt).S>>in
            Reverse(
                shift(
                    (numInVals - 1 - bottomBound).toInt,
                    (numInVals - topBound).toInt
                )
            )
        }
    }
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
object countLeadingZeros
{
    def apply(in: UInt): UInt = PriorityEncoder(in.asBools.reverse)
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
object orReduceBy2
{
    def apply(in: UInt): UInt =
    {
        val reducedWidth = (in.getWidth + 1)>>1
        val reducedVec = Wire(Vec(reducedWidth, Bool()))
        for (ix <- 0 until reducedWidth - 1) {
            reducedVec(ix) := in(ix * 2 + 1, ix * 2).orR
        }
        reducedVec(reducedWidth - 1) :=
            in(in.getWidth - 1, (reducedWidth - 1) * 2).orR
        reducedVec.asUInt
    }
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
object orReduceBy4
{
    def apply(in: UInt): UInt =
    {
        val reducedWidth = (in.getWidth + 3)>>2
        val reducedVec = Wire(Vec(reducedWidth, Bool()))
        for (ix <- 0 until reducedWidth - 1) {
            reducedVec(ix) := in(ix * 4 + 3, ix * 4).orR
        }
        reducedVec(reducedWidth - 1) :=
            in(in.getWidth - 1, (reducedWidth - 1) * 4).orR
        reducedVec.asUInt
    }
}

