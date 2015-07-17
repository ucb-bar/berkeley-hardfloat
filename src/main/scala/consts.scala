// See LICENSE for license details.

// consts.vh -- Common definitions for the recoded FPU blocks.
// Author: Brian Richards, 10/31/2010

package hardfloat

import Chisel._

object consts {
  // Rounding modes
  val round_nearest_even = UInt("b00",2);
  val round_minMag       = UInt("b01",2);
  val round_min          = UInt("b10",2);
  val round_max          = UInt("b11",2);
  
  // Integer type codes
  val type_uint32        = UInt("b00",2);
  val type_int32         = UInt("b01",2);
  val type_uint64        = UInt("b10",2);
  val type_int64         = UInt("b11",2);
}
