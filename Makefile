default: all

SoftFloat-3.zip:
	wget http://www.jhauser.us/arithmetic/SoftFloat-3.zip

TestFloat-3.zip:
	wget http://www.jhauser.us/arithmetic/TestFloat-3.zip

SoftFloat-3/extract.stamp: SoftFloat-3.zip
	rm -rf SoftFloat-3
	unzip SoftFloat-3.zip
	touch SoftFloat-3/extract.stamp

TestFloat-3/extract.stamp: TestFloat-3.zip
	rm -rf TestFloat-3
	unzip TestFloat-3.zip
	patch -p0 < patches/TestFloat-3/0001-gcc_lm.patch
	touch TestFloat-3/extract.stamp

SoftFloat-3/build/Linux-x86_64-GCC/softfloat.a: SoftFloat-3/extract.stamp
	$(MAKE) -C SoftFloat-3/build/Linux-x86_64-GCC

TestFloat-3/build/Linux-x86_64-GCC/testfloat_gen: TestFloat-3/extract.stamp SoftFloat-3/build/Linux-x86_64-GCC/softfloat.a
	$(MAKE) -C TestFloat-3/build/Linux-x86_64-GCC

testfloat_gen: TestFloat-3/build/Linux-x86_64-GCC/testfloat_gen
	cp TestFloat-3/build/Linux-x86_64-GCC/testfloat_gen .

tests = \
	f32_mulAdd \
	f64_mulAdd \
	f64_div \
	f64_sqrt \

SBT = java -Xmx2048M -Xss8M -XX:MaxPermSize=128M -jar sbt-launch.jar

define test_template

test-$(1)/Test_$(1).cpp: src/main/scala/*.scala
	$(SBT) -DchiselVersion=latest.release "run $(1) --targetDir test-$(1)"

test-$(1)/dut: test-$(1)/Test_$(1).cpp csrc/*.h csrc/*.cpp
	g++ -c -o test-$(1)/emulator.o -Icsrc -Itest-$(1) -include csrc/emulator-$(1).h csrc/emulator.cpp
	g++ -c -o test-$(1)/Test_$(1).o $$<
	g++ -o $$@ test-$(1)/Test_$(1).o test-$(1)/emulator.o

test-c-$(1).near_even.log: test-$(1)/dut testfloat_gen
	{ time ./testfloat_gen -rnear_even $(1) | $$< 0 ; } > $$@ 2>&1

test-c-$(1).minMag.log: test-$(1)/dut testfloat_gen
	{ time ./testfloat_gen -rminMag $(1) | $$< 1 ; } > $$@ 2>&1

test-c-$(1).min.log: test-$(1)/dut testfloat_gen
	{ time ./testfloat_gen -rmin $(1) | $$< 2 ; } > $$@ 2>&1

test-c-$(1).max.log: test-$(1)/dut testfloat_gen
	{ time ./testfloat_gen -rmax $(1) | $$< 3 ; } > $$@ 2>&1

$(1): $$(addsuffix .log, $$(addprefix test-c-$(1)., near_even minMag min max))

test-$(1)/Test_$(1).v: src/main/scala/*.scala
	$(SBT) -DchiselVersion=latest.release "run $(1) --targetDir test-$(1) --backend v"

test-$(1)/simv: test-$(1)/Test_$(1).v
	cd test-$(1) && vcs -full64 -timescale=1ns/10ps +define+EXPERIMENT=\"emulator-$(1).vh\" +incdir+../vsrc +rad $$(notdir $$<) ../vsrc/emulator.v -o $$(notdir $$@)

test-$(1)/simv-debug: test-$(1)/Test_$(1).v
	cd test-$(1) && vcs -full64 -timescale=1ns/10ps +define+EXPERIMENT=\"emulator-$(1).vh\" +incdir+../vsrc +define+DEBUG -debug_pp $$(notdir $$<) ../vsrc/emulator.v -o $$(notdir $$@)

test-v-$(1).near_even.log: test-$(1)/simv testfloat_gen
	{ time ./testfloat_gen -rnear_even $(1) | $$< +rm=0 ; } > $$@ 2>&1

test-v-$(1).minMag.log: test-$(1)/simv testfloat_gen
	{ time ./testfloat_gen -rminMag $(1) | $$< +rm=1 ; } > $$@ 2>&1

test-v-$(1).min.log: test-$(1)/simv testfloat_gen
	{ time ./testfloat_gen -rmin $(1) | $$< +rm=2 ; } > $$@ 2>&1

test-v-$(1).max.log: test-$(1)/simv testfloat_gen
	{ time ./testfloat_gen -rmax $(1) | $$< +rm=3 ; } > $$@ 2>&1

$(1)-v: $$(addsuffix .log, $$(addprefix test-v-$(1)., near_even minMag min max))

.PHONY: $(1) $(1)-v

endef

$(foreach test,$(tests),$(eval $(call test_template,$(test))))

all: $(tests)
	@ if grep abort test-c-*.*.log; then \
		echo "some tests FAILED!!!"; \
		exit 1; \
	fi

verilog: $(addsuffix -v, $(tests))
	@ if grep abort test-v-*.*.log; then \
		echo "some tests FAILED!!!"; \
		exit 1; \
	fi

clean:
	rm -rf test* ucli.key *.zip TestFloat-3 SoftFloat-3 testfloat_gen

.PHONY: $(tests) clean
