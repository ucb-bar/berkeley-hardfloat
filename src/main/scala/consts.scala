// fpu_recoded.vh -- Common definitions for the recoded FPU blocks.
// Author: Brian Richards, 10/31/2010

//`ifndef _fpu_recoded_vh
//`define _fpu_recoded_vh

package hardfloat

import Chisel._;
import Node._;

object fpu_recoded {

// Rounding modes:
val round_nearest_even = UInt("b00",2);
val round_minMag       = UInt("b01",2);
val round_min          = UInt("b10",2);
val round_max          = UInt("b11",2);

// Integer type codes:
val type_uint32        = UInt("b00",2);
val type_int32         = UInt("b01",2);
val type_uint64        = UInt("b10",2);
val type_int64         = UInt("b11",2);

// FPU data type codes:
val fpu_type_f32       = UInt("b000",3);
val fpu_type_f64       = UInt("b001",3);
val fpu_type_uint32    = UInt("b100",3);
val fpu_type_int32     = UInt("b101",3);
val fpu_type_uint64    = UInt("b110",3);
val fpu_type_int64     = UInt("b111",3);
}


// synopsys translate_off
// Debugging C routine
//extern "C" void readHex_ui8_sp( output bit (31,0) );
//extern "C" void readHex_ui8_n( output bit (31,0) );
//extern "C" void readHex_ui32_sp( output bit (31,0) );
//extern "C" void readHex_ui32_n( output bit (31,0) );
//extern "C" void readHex_ui64_sp( output bit (63,0) );
//extern "C" void readHex_ui64_n( output bit (63,0) );
//extern "C" void writeHex_ui8_sp( input bit (31,0) );
//extern "C" void writeHex_ui8_n( input bit (31,0) );
//extern "C" void writeHex_ui32_sp( input bit (31,0) );
//extern "C" void writeHex_ui32_n( input bit (31,0) );
//extern "C" void writeHex_ui64_sp( input bit (63,0) );
//extern "C" void writeHex_ui64_n( input bit (63,0) );

// synopsys translate_on
//`endif // _fpu_recoded_vh_
