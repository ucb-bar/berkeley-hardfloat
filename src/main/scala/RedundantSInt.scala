// See LICENSE for license details.

package hardfloat

import Chisel._
import Chisel.ImplicitConversions._

class RedundantSInt(val left: SInt, val right: SInt, val width: Int) {
  def toSInt = left + right
  def toUInt = this.toSInt.toUInt

  def + (x: UInt): RedundantSInt = this + x.zext
  def + (x: SInt): RedundantSInt = {
    require(x.getWidth > 0)
    if (width < x.getWidth)
      this.padTo(x.getWidth) + x
    else if (width > x.getWidth)
      this + RedundantSInt.padTo(x, width)
    else {
      val (a1, b1, x1) = (left(width-2,0), right(width-2,0), x(width-2,0))
      val sums = left ^ right ^ x
      val carries = a1 & b1 | a1 & x1 | b1 & x1
      RedundantSInt(sums, carries.toSInt << 1)
    }
  }

  def << (x: Int) = RedundantSInt(left << x, right << x)
  def >> (x: Int) = RedundantSInt(left >> x, right >> x)

  def padTo(w: Int) =
    if (width < w) RedundantSInt(RedundantSInt.padTo(left, w),
                                 RedundantSInt.padTo(right, w))
    else this
}

object RedundantSInt {
  def padTo(x: SInt, len: Int) =
    if (x.getWidth < len) Cat(Fill(len-x.getWidth, x(x.getWidth-1)), x).toSInt
    else x
  def apply(a: SInt, b: SInt = SInt(0)) = {
    require(a.getWidth >= 0 && b.getWidth > 0)
    val w = a.getWidth.max(b.getWidth)
    new RedundantSInt(padTo(a, w), padTo(b, w), w)
  }

  def fromProduct(a: SInt, b: SInt) = {
    val (aw, bw) = (a.getWidth, b.getWidth)
    require(aw > 0 && bw > 0)

    val ppm = Module(new PartialProductMultiplier(aw, bw))
    ppm.io.in0 := a
    ppm.io.in1 := b
    RedundantSInt(ppm.io.out0, ppm.io.out1)
  }
}

object RedundantUInt {
  def apply(a: UInt, b: UInt = UInt(0)) = RedundantSInt(a.zext, b.zext)
  def fromProduct(a: UInt, b: UInt) = RedundantSInt.fromProduct(a.zext, b.zext)
}

class PartialProductMultiplier(w0: Int, w1: Int) extends BlackBox {
  val io = new Bundle {
    val in0 = SInt(INPUT, w0)
    val in1 = SInt(INPUT, w1)
    val out0 = SInt(OUTPUT, w0 + w1 + 2)
    val out1 = SInt(OUTPUT, w0 + w1 + 2)
  }

  setVerilogParameters("#(" + w0 + ", " + w1 + ")")

  io.out0 := io.in0 * io.in1
  io.out1 := SInt(0)
}
