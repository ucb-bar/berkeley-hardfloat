package hardfloat

import Chisel._
import Node._

object Normalize
{
  def doit(width: Int, pos: Int, in: Bits, dist: Bits): Pair[Bits,Bits] = {
    if (pos == width)
      (in, dist)
    else {
      val shift = in(width-1,pos) === Bits(0)
      doit(width, width - (width-pos)/2, Mux(shift, Cat(in(pos-1,0), Bits(0, width-pos)), in), if (dist == null) shift else Cat(dist, shift))
    }
  }
  def apply(in: Bits) = {
    require((in.getWidth & (in.getWidth-1)) == 0)
    doit(in.getWidth, in.getWidth/2, in, null)
  }
}
