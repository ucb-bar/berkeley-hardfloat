
CHISEL_VERSION = 2.2.32

#default: test-c test-v
default: test-c

berkeley-softfloat-3/extract.stamp: patches/berkeley-softfloat-3/*
	rm -rf berkeley-softfloat-3
	git clone git://github.com/ucb-bar/berkeley-softfloat-3.git
	patch -p0 < patches/berkeley-softfloat-3/0001-specialize_riscv.patch
	touch berkeley-softfloat-3/extract.stamp

berkeley-testfloat-3/extract.stamp:
	rm -rf berkeley-testfloat-3
	git clone git://github.com/ucb-bar/berkeley-testfloat-3.git
	touch berkeley-testfloat-3/extract.stamp

berkeley-softfloat-3/build/Linux-x86_64-GCC/softfloat.a: berkeley-softfloat-3/extract.stamp
	$(MAKE) -C berkeley-softfloat-3/build/Linux-x86_64-GCC

berkeley-testfloat-3/build/Linux-x86_64-GCC/testfloat_gen: berkeley-testfloat-3/extract.stamp \
                                                           berkeley-softfloat-3/build/Linux-x86_64-GCC/softfloat.a
	$(MAKE) -C berkeley-testfloat-3/build/Linux-x86_64-GCC

./testfloat_gen: berkeley-testfloat-3/build/Linux-x86_64-GCC/testfloat_gen
	cp berkeley-testfloat-3/build/Linux-x86_64-GCC/testfloat_gen .

ifneq (,$(shell which testfloat_gen 2> /dev/null))
TESTFLOAT_GEN = $(shell which testfloat_gen)
else
TESTFLOAT_GEN = ./testfloat_gen
endif

tests = \
 f32FromRecF32 \
 f64FromRecF64 \
 UI32ToRecF32 \
 UI32ToRecF64 \
 UI64ToRecF32 \
 UI64ToRecF64 \
 I32ToRecF32 \
 I32ToRecF64 \
 I64ToRecF32 \
 I64ToRecF64 \
 RecF32ToUI32 \
 RecF32ToUI64 \
 RecF64ToUI32 \
 RecF64ToUI64 \
 RecF32ToI32 \
 RecF32ToI64 \
 RecF64ToI32 \
 RecF64ToI64 \
 RecF32ToRecF64 \
 RecF64ToRecF32 \
 MulAddRecF32_add \
 MulAddRecF32_mul \
 MulAddRecF32 \
 MulAddRecF64_add \
 MulAddRecF64_mul \
 MulAddRecF64 \
 DivSqrtRecF64_div \
 DivSqrtRecF64_sqrt \
 CompareRecF32_lt \
 CompareRecF32_le \
 CompareRecF32_eq \
 CompareRecF64_lt \
 CompareRecF64_le \
 CompareRecF64_eq \

#-----------------------------------------------------------------------------
#-----------------------------------------------------------------------------

define test_fNFromRecFN_template

test-$(1)/ValExec_$(1).cpp: src/main/scala/*.scala
	sbt -DchiselVersion=$(CHISEL_VERSION) "run $(1) --targetDir test-$(1)"

test-$(1)/dut: test-$(1)/ValExec_$(1).cpp csrc/*.h csrc/*.cpp
	g++ -c -o test-$(1)/test.o -Icsrc -Itest-$(1) -include csrc/test-$(1).h csrc/test-fNFromRecFN.cpp
	g++ -c -o test-$(1)/ValExec_$(1).o $$<
	g++ -o $$@ test-$(1)/ValExec_$(1).o test-$(1)/test.o

test-c-$(1).log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) $(3) $(2) | $$< ; } > $$@ 2>&1

test-c-$(1): test-c-$(1).log

.PHONY: test-c-$(1) test-v-$(1)

endef

#-----------------------------------------------------------------------------
#-----------------------------------------------------------------------------

define test_RecFNToUIN_template

test-$(1)/ValExec_$(1).cpp: src/main/scala/*.scala
	sbt -DchiselVersion=$(CHISEL_VERSION) "run $(1) --targetDir test-$(1)"

test-$(1)/dut: test-$(1)/ValExec_$(1).cpp csrc/*.h csrc/*.cpp
	g++ -c -o test-$(1)/test.o -Icsrc -Itest-$(1) -include csrc/test-$(1).h csrc/test-RecFNToUIN.cpp
	g++ -c -o test-$(1)/ValExec_$(1).o $$<
	g++ -o $$@ test-$(1)/ValExec_$(1).o test-$(1)/test.o

test-c-$(1).near_even.log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) -rnear_even -exact $(3) $(2) | $$< 0 ; } > $$@ 2>&1

test-c-$(1).minMag.log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) -rminMag -exact $(3) $(2) | $$< 1 ; } > $$@ 2>&1

test-c-$(1).min.log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) -rmin -exact $(3) $(2) | $$< 2 ; } > $$@ 2>&1

test-c-$(1).max.log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) -rmax -exact $(3) $(2) | $$< 3 ; } > $$@ 2>&1

test-c-$(1): \
 test-c-$(1).near_even.log \
 test-c-$(1).minMag.log \
 test-c-$(1).min.log \
 test-c-$(1).max.log \

.PHONY: test-c-$(1) test-v-$(1)

endef

#-----------------------------------------------------------------------------
#-----------------------------------------------------------------------------

define test_RecFNToIN_template

test-$(1)/ValExec_$(1).cpp: src/main/scala/*.scala
	sbt -DchiselVersion=$(CHISEL_VERSION) "run $(1) --targetDir test-$(1)"

test-$(1)/dut: test-$(1)/ValExec_$(1).cpp csrc/*.h csrc/*.cpp
	g++ -c -o test-$(1)/test.o -Icsrc -Itest-$(1) -include csrc/test-$(1).h csrc/test-RecFNToIN.cpp
	g++ -c -o test-$(1)/ValExec_$(1).o $$<
	g++ -o $$@ test-$(1)/ValExec_$(1).o test-$(1)/test.o

test-c-$(1).near_even.log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) -rnear_even -exact $(3) $(2) | $$< 0 ; } > $$@ 2>&1

test-c-$(1).minMag.log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) -rminMag -exact $(3) $(2) | $$< 1 ; } > $$@ 2>&1

test-c-$(1).min.log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) -rmin -exact $(3) $(2) | $$< 2 ; } > $$@ 2>&1

test-c-$(1).max.log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) -rmax -exact $(3) $(2) | $$< 3 ; } > $$@ 2>&1

test-c-$(1): \
 test-c-$(1).near_even.log \
 test-c-$(1).minMag.log \
 test-c-$(1).min.log \
 test-c-$(1).max.log \

.PHONY: test-c-$(1) test-v-$(1)

endef

#-----------------------------------------------------------------------------
#-----------------------------------------------------------------------------

define test_CompareRecFN_template

test-$(1)/ValExec_$(1).cpp: src/main/scala/*.scala
	sbt -DchiselVersion=$(CHISEL_VERSION) "run $(1) --targetDir test-$(1)"

test-$(1)/dut: test-$(1)/ValExec_$(1).cpp csrc/*.h csrc/*.cpp
	g++ -c -o test-$(1)/test.o -Icsrc -Itest-$(1) -include csrc/test-$(1).h csrc/test-CompareRecFN.cpp
	g++ -c -o test-$(1)/ValExec_$(1).o $$<
	g++ -o $$@ test-$(1)/ValExec_$(1).o test-$(1)/test.o

test-c-$(1).log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) $(3) $(2) | $$< ; } > $$@ 2>&1

test-c-$(1): test-c-$(1).log

.PHONY: test-c-$(1) test-v-$(1)

endef

#-----------------------------------------------------------------------------
#-----------------------------------------------------------------------------

define otherTest_template

test-$(1)/ValExec_$(1).cpp: src/main/scala/*.scala
	sbt -DchiselVersion=$(CHISEL_VERSION) "run $(1) --targetDir test-$(1)"

test-$(1)/dut: test-$(1)/ValExec_$(1).cpp csrc/*.h csrc/*.cpp
	g++ -c -o test-$(1)/test.o -Icsrc -Itest-$(1) -include csrc/test-$(1).h csrc/test.cpp
	g++ -c -o test-$(1)/ValExec_$(1).o $$<
	g++ -o $$@ test-$(1)/ValExec_$(1).o test-$(1)/test.o

test-c-$(1).near_even.log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) -rnear_even $(3) $(2) | $$< 0 ; } > $$@ 2>&1

test-c-$(1).minMag.log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) -rminMag $(3) $(2) | $$< 1 ; } > $$@ 2>&1

test-c-$(1).min.log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) -rmin $(3) $(2) | $$< 2 ; } > $$@ 2>&1

test-c-$(1).max.log: test-$(1)/dut $(TESTFLOAT_GEN)
	{ $(TESTFLOAT_GEN) -rmax $(3) $(2) | $$< 3 ; } > $$@ 2>&1

test-c-$(1): \
 test-c-$(1).near_even.log \
 test-c-$(1).minMag.log \
 test-c-$(1).min.log \
 test-c-$(1).max.log \


#*** FOR VERILOG TESTING:
#test-$(1)/ValExec_$(1).v: src/main/scala/*.scala
#	sbt -DchiselVersion=$(CHISEL_VERSION) "run $(1) --targetDir test-$(1) --backend v"
#
#test-$(1)/simv: test-$(1)/ValExec_$(1).v
#	cd test-$(1) && vcs -full64 -timescale=1ns/10ps +define+EXPERIMENT=\"emulator-$(1).vh\" +incdir+../vsrc +rad $$(notdir $$<) ../vsrc/emulator.v -o $$(notdir $$@)
#
#test-$(1)/simv-debug: test-$(1)/ValExec_$(1).v
#	cd test-$(1) && vcs -full64 -timescale=1ns/10ps +define+EXPERIMENT=\"emulator-$(1).vh\" +incdir+../vsrc +define+DEBUG -debug_pp $$(notdir $$<) ../vsrc/emulator.v -o $$(notdir $$@)
#
#test-v-$(1).near_even.log: test-$(1)/simv $(TESTFLOAT_GEN)
#	{ time $(TESTFLOAT_GEN) -rnear_even $(1) | $$< +rm=0 ; } > $$@ 2>&1
#
#test-v-$(1).minMag.log: test-$(1)/simv $(TESTFLOAT_GEN)
#	{ time $(TESTFLOAT_GEN) -rminMag $(1) | $$< +rm=1 ; } > $$@ 2>&1
#
#test-v-$(1).min.log: test-$(1)/simv $(TESTFLOAT_GEN)
#	{ time $(TESTFLOAT_GEN) -rmin $(1) | $$< +rm=2 ; } > $$@ 2>&1
#
#test-v-$(1).max.log: test-$(1)/simv $(TESTFLOAT_GEN)
#	{ time $(TESTFLOAT_GEN) -rmax $(1) | $$< +rm=3 ; } > $$@ 2>&1
#
#$(1)-v: $$(addsuffix .log, $$(addprefix test-v-$(1)., near_even minMag min max))


.PHONY: test-c-$(1) test-v-$(1)

endef

#-----------------------------------------------------------------------------
#-----------------------------------------------------------------------------

$(eval $(call test_fNFromRecFN_template,f32FromRecF32,f32,-level2))
$(eval $(call test_fNFromRecFN_template,f64FromRecF64,f64,-level2))

$(eval $(call otherTest_template,UI32ToRecF32,ui32_to_f32,-level2))
$(eval $(call otherTest_template,UI32ToRecF64,ui32_to_f64,-level2))
$(eval $(call otherTest_template,UI64ToRecF32,ui64_to_f32,-level2))
$(eval $(call otherTest_template,UI64ToRecF64,ui64_to_f64,-level2))
$(eval $(call otherTest_template,I32ToRecF32,i32_to_f32,-level2))
$(eval $(call otherTest_template,I32ToRecF64,i32_to_f64,-level2))
$(eval $(call otherTest_template,I64ToRecF32,i64_to_f32,-level2))
$(eval $(call otherTest_template,I64ToRecF64,i64_to_f64,-level2))

$(eval $(call test_RecFNToUIN_template,RecF32ToUI32,f32_to_ui32,-level2))
$(eval $(call test_RecFNToUIN_template,RecF32ToUI64,f32_to_ui64,-level2))
$(eval $(call test_RecFNToUIN_template,RecF64ToUI32,f64_to_ui32,-level2))
$(eval $(call test_RecFNToUIN_template,RecF64ToUI64,f64_to_ui64,-level2))

$(eval $(call test_RecFNToIN_template,RecF32ToI32,f32_to_i32,-level2))
$(eval $(call test_RecFNToIN_template,RecF32ToI64,f32_to_i64,-level2))
$(eval $(call test_RecFNToIN_template,RecF64ToI32,f64_to_i32,-level2))
$(eval $(call test_RecFNToIN_template,RecF64ToI64,f64_to_i64,-level2))

$(eval $(call otherTest_template,RecF32ToRecF64,f32_to_f64,-level2))
$(eval $(call otherTest_template,RecF64ToRecF32,f64_to_f32,-level2))
$(eval $(call otherTest_template,MulAddRecF32_add,f32_add,))
$(eval $(call otherTest_template,MulAddRecF32_mul,f32_mul,))
$(eval $(call otherTest_template,MulAddRecF32,f32_mulAdd,))
$(eval $(call otherTest_template,MulAddRecF64_add,f64_add,))
$(eval $(call otherTest_template,MulAddRecF64_mul,f64_mul,))
$(eval $(call otherTest_template,MulAddRecF64,f64_mulAdd,))
$(eval $(call otherTest_template,DivSqrtRecF64_div,f64_div,))
$(eval $(call otherTest_template,DivSqrtRecF64_sqrt,f64_sqrt,-level2))

$(eval $(call test_CompareRecFN_template,CompareRecF32_lt,f32_lt,))
$(eval $(call test_CompareRecFN_template,CompareRecF32_le,f32_le,))
$(eval $(call test_CompareRecFN_template,CompareRecF32_eq,f32_eq,))
$(eval $(call test_CompareRecFN_template,CompareRecF64_lt,f64_lt,))
$(eval $(call test_CompareRecFN_template,CompareRecF64_le,f64_le,))
$(eval $(call test_CompareRecFN_template,CompareRecF64_eq,f64_eq,))

test-c: $(addprefix test-c-, $(tests))
	@ if grep -q "expected" test-c-*.log; then \
		echo "some test FAILED!!!"; \
		exit 1; \
	fi


#*** FOR VERILOG TESTING:
#test-v: $(addprefix test-v-, $(tests))
#	@ if grep abort test-v-*.log; then \
#		echo "some test FAILED!!!"; \
#		exit 1; \
#	fi


clean:
	rm -rf test-* ucli.key

.PHONY: test-c test-v clean

