Berkeley Hardware Floating-Point Units
======================================

This repository contains hardware floating-point units written in Chisel.
This library contains parameterized floating-point units for fused multiply-add
operations, conversions between integer and floating-point numbers, and
conversions between floating-point conversions with different precision.

**WARNING**:
These units are works in progress.  They may not be yet completely free of
bugs, nor are they fully optimized.


Recoded Format
--------------

The floating-point units in this repository work on an internal recoded format
(exponent has an additional bit) to handle subnormal numbers more efficiently
in a microprocessor.  A more detailed explanation will come soon, but in the
mean time here are some example mappings for single-precision numbers.

    IEEE format                           Recoded format
    ----------------------------------    -----------------------------------
    s 00000000 00000000000000000000000    s 000------ 00000000000000000000000
    s 00000000 00000000000000000000001    s 001101011 00000000000000000000000
    s 00000000 00000000000000000000011    s 001101100 10000000000000000000000
    s 00000000 00000000000000000000111    s 001101101 11000000000000000000000
        ...              ...                   ...              ... 
    s 00000000 00111111111111111111111    s 001111111 11111111111111111111000
    s 00000000 01111111111111111111111    s 010000000 11111111111111111111100
    s 00000000 11111111111111111111111    s 010000001 11111111111111111111110
    s 00000001 11111111111111111111111    s 010000010 11111111111111111111111
    s 00000010 11111111111111111111111    s 010000011 11111111111111111111111
        ...              ...                   ...              ... 
    s 11111101 11111111111111111111111    s 101111110 11111111111111111111111
    s 11111110 11111111111111111111111    s 101111111 11111111111111111111111
    s 11111111 00000000000000000000000    s 110------ -----------------------
    s 11111111 11111111111111111111111    s 111------ 11111111111111111111111


Unit-Testing
------------

To unit-test these floating-point units, you need the berkeley-testfloat-3
package.

To test floating-point units with the C simulator:

    $ make

