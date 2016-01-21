
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

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
object lowMask
{
    def apply(in: UInt, topBound: Int, bottomBound: Int): UInt = {
        require(topBound != bottomBound)
        val numInVals = 1<<in.getWidth
        if (topBound < bottomBound) {
            lowMask(~in, numInVals - 1 - topBound, numInVals - 1 - bottomBound)
        } else {
            val shift = SInt(BigInt(-1)<<numInVals)>>in
            Reverse(shift(numInVals - 1 - bottomBound, numInVals - topBound))
        }
    }
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
object priorityEncode
{
    def apply(key: UInt, n: Int, s: Int) = {
        UInt(n + s - 1) - Log2(key(s - 1, 0), s)
    }
}

