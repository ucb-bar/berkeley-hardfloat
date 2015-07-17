// See LICENSE for license details.

package hardfloat

import Chisel._

object Normalize
{
  def apply(in: UInt) = {
    require((in.getWidth & (in.getWidth-1)) == 0)
    val shift = ~Log2(in, in.getWidth)
    (in << shift, shift)
  }
}
