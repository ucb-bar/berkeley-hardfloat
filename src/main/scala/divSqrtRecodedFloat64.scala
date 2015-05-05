// See LICENSE for license details.

package hardfloat

import Chisel._
import Node._
import consts._

class divSqrtRecodedFloat64 extends Module
{
  val io = new Bundle {
    val inReady_div = Bool(OUTPUT)
    val inReady_sqrt = Bool(OUTPUT)
    val inValid = Bool(INPUT)
    val sqrtOp = Bool(INPUT)
    val a = Bits(INPUT, 65)
    val b = Bits(INPUT, 65)
    val roundingMode = Bits(INPUT, 2)
    val outValid_div = Bool(OUTPUT)
    val outValid_sqrt = Bool(OUTPUT)
    val out = Bits(OUTPUT, 65)
    val exceptionFlags = Bits(OUTPUT, 5)
  }

  val ds = Module(new divSqrtRecodedFloat64_mulAddZ31)

  io.inReady_div := ds.io.inReady_div
  io.inReady_sqrt := ds.io.inReady_sqrt
  ds.io.inValid := io.inValid
  ds.io.sqrtOp := io.sqrtOp
  ds.io.a := io.a
  ds.io.b := io.b
  ds.io.roundingMode := io.roundingMode
  io.outValid_div := ds.io.outValid_div
  io.outValid_sqrt := ds.io.outValid_sqrt
  io.out := ds.io.out
  io.exceptionFlags := ds.io.exceptionFlags

  val mul = Module(new mul54)

  mul.io.val_s0 := ds.io.usingMulAdd(0)
  mul.io.latch_a_s0 := ds.io.latchMulAddA_0
  mul.io.a_s0 := ds.io.mulAddA_0
  mul.io.latch_b_s0 := ds.io.latchMulAddB_0
  mul.io.b_s0 := ds.io.mulAddB_0
  mul.io.c_s2 := ds.io.mulAddC_2
  ds.io.mulAddResult_3 := mul.io.result_s3
}
class mul54(val wmul: Int = 54, wadd: Int = 105 ) extends Module
{
  val io = new Bundle {
    val val_s0 = Bool(INPUT)
    val latch_a_s0 = Bool(INPUT)
    val a_s0 = UInt(INPUT, wmul)
    val latch_b_s0 = Bool(INPUT)
    val b_s0 = UInt(INPUT, wmul)
    val c_s2 = UInt(INPUT, wadd)
    val result_s3 = UInt(OUTPUT, wadd)
  }

    val val_s1 = Reg(Bool())
    val val_s1_1 = Reg(Bool())  
    val val_s2 = Reg(Bool())
    val reg_a_s1 = Reg(UInt(width = wmul))
    val reg_b_s1 = Reg(UInt(width = wmul))
    val reg_mul_s2 = Reg(UInt(width = 	wadd))
    val reg_mul_s3 = Reg(UInt(width = 	wadd))	
    val reg_c_s3 = Reg(UInt(width = wadd))

    val_s1_1 := io.val_s0 && (io.latch_a_s0 || io.latch_b_s0)
    val_s1 := io.val_s0
    val_s2 := val_s1

    when (io.val_s0 && io.latch_a_s0) {reg_a_s1 := io.a_s0}
    when (io.val_s0 && io.latch_b_s0) {reg_b_s1 := io.b_s0}

    val mul_result = (reg_a_s1 * reg_b_s1)(wadd-1 , 0)

    when (val_s1_1) {
      reg_mul_s2 := mul_result
    }

    when (val_s2) {
      reg_mul_s3 := reg_mul_s2
      reg_c_s3 := io.c_s2
    }

    io.result_s3 := reg_mul_s3 + reg_c_s3
}
