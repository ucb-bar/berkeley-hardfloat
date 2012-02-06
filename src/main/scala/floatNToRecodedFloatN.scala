package hardfloat

import Chisel._
import Node._;
import scala.math.{log, ceil}

class floatNToRecodedFloatN_io(size: Int) extends Bundle() {
  val in  = Bits(size, INPUT);
  val out = Bits(size+1, OUTPUT);
}

class floatNToRecodedFloatN(expSize : Int = 8, sigSize : Int = 24) extends Component
{
  def ceilLog2(x : Int) = ceil(log(x)/log(2.0)).toInt;

  val size = expSize + sigSize;
  val logNormSize = ceilLog2( sigSize );
  val normSize = 1 << logNormSize;

  override val io = new floatNToRecodedFloatN_io(size);

  val sign    = io.in(size-1);
  val expIn   = io.in(size-2, sigSize-1).toUFix;
  val fractIn = io.in(sigSize-2, 0).toUFix;
  val isZeroExpIn = ( expIn === Bits("b0", expSize) );
  val isZeroFractIn = ( fractIn === Bits("b0", sigSize-1) );
  val isZeroOrSubnormal = isZeroExpIn;
  val isZero = isZeroOrSubnormal && isZeroFractIn;
  val isSubnormal = isZeroOrSubnormal && ~isZeroFractIn;
  val isNormalOrSpecial = ~isZeroExpIn;

  val norm_in = Cat(fractIn, Bits("b0",normSize-sigSize+1));

  val normalizeFract = new normalizeN(expSize, sigSize);
  normalizeFract.io.in := norm_in;
  val norm_count = normalizeFract.io.distance;
  val norm_out   = normalizeFract.io.out;

  val normalizedFract = norm_out(normSize-2,normSize-sigSize);
  val commonExp =
    Mux(isSubnormal, Cat(Fill(expSize-logNormSize+1, Bits("b1", 1)), ~norm_count), Bits("b0", expSize+1)) |
    Mux(isNormalOrSpecial, expIn, Bits("b0", expSize+1));
  val expAdjust = Mux(isZero, Bits("b0",expSize+1), Cat(Bits("b1", 1), Bits(0, expSize-1), Bits("b1", 1)));
  val adjustedCommonExp = commonExp.toUFix + expAdjust.toUFix + isSubnormal.toUFix;
  val isNaN = (adjustedCommonExp(expSize,expSize-1) === Bits("b11",2)) & ~ isZeroFractIn;

  //val expOut = adjustedCommonExp | (isNaN(6, 0)<<6);
  val expOut = adjustedCommonExp | (isNaN << UFix(expSize-2));
  val fractOut = Mux(isZeroOrSubnormal, normalizedFract, fractIn);

  io.out := Cat(sign, expOut, fractOut);
}
