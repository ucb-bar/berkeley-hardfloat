package hardfloat

import Chisel._
import Node._;

class normalizeN_io(normSize : Int, logNormSize : Int) extends Bundle() {
  val in       = Bits(normSize, INPUT);
  val distance = Bits(logNormSize, OUTPUT);
  val out      = Bits(normSize, OUTPUT);
}

class normalizeN(expSize : Int = 8, sigSize : Int = 24) extends Component {
  
  if( !((expSize == 8 && sigSize == 24) || (expSize == 11 && sigSize == 53) ) ) {
    printf("normalizeN.scala: invalid expSize (%d) and sigSize (%d) provided\n", expSize, sigSize);
    throw new IllegalArgumentException("normalizeN.scala: (expSize, sigSize) must be (8, 24) or (11, 53)");
  }
  
  val logNormSize = (if( expSize == 8 && sigSize ==24 ) 5 else 6);
  val normSize = (if( expSize == 8 && sigSize ==24 ) 32 else 64);

  override val io: normalizeN_io = new normalizeN_io( normSize, logNormSize );
  
  // If it's not 32, then it's 64 (we should probably throw an error instead)
  val normalize = (if( expSize == 8 && sigSize == 24 ) new normalize32() else new normalize64());
  
  normalize.io.out      <> io.out;
  normalize.io.in       <> io.in;
  normalize.io.distance <> io.distance;
}
