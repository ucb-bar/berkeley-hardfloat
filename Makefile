default: all

ifeq (, $(shell which testfloat_gen))
$(error "No testfloat_gen in $(PATH), install testfloat_gen")
endif

tests = \
	f32_mulAdd \
	f64_mulAdd \
	f64_div \
	f64_sqrt \

define test_template

test-$(1)/Test_$(1).cpp: src/main/scala/*.scala
	sbt -DchiselVersion=latest.release "run $(1) --targetDir test-$(1)"

test-$(1)/dut: test-$(1)/Test_$(1).cpp csrc/*.h csrc/*.cpp
	g++ -c -o test-$(1)/emulator.o -Icsrc -Itest-$(1) -include csrc/emulator-$(1).h csrc/emulator.cpp
	g++ -c -o test-$(1)/Test_$(1).o $$<
	g++ -o $$@ test-$(1)/Test_$(1).o test-$(1)/emulator.o

test-c-$(1).near_even.log: test-$(1)/dut
	{ time testfloat_gen -rnear_even $(1) | $$< 0 ; } > $$@ 2>&1

test-c-$(1).minMag.log: test-$(1)/dut
	{ time testfloat_gen -rminMag $(1) | $$< 1 ; } > $$@ 2>&1

test-c-$(1).min.log: test-$(1)/dut
	{ time testfloat_gen -rmin $(1) | $$< 2 ; } > $$@ 2>&1

test-c-$(1).max.log: test-$(1)/dut
	{ time testfloat_gen -rmax $(1) | $$< 3 ; } > $$@ 2>&1

$(1): $$(addsuffix .log, $$(addprefix test-c-$(1)., near_even minMag min max))

test-$(1)/Test_$(1).v: src/main/scala/*.scala
	sbt -DchiselVersion=latest.release "run $(1) --targetDir test-$(1) --backend v"

test-$(1)/simv: test-$(1)/Test_$(1).v
	cd test-$(1) && vcs -full64 -timescale=1ns/10ps +define+EXPERIMENT=\"emulator-$(1).vh\" +incdir+../vsrc +rad $$(notdir $$<) ../vsrc/emulator.v -o $$(notdir $$@)

test-$(1)/simv-debug: test-$(1)/Test_$(1).v
	cd test-$(1) && vcs -full64 -timescale=1ns/10ps +define+EXPERIMENT=\"emulator-$(1).vh\" +incdir+../vsrc +define+DEBUG -debug_pp $$(notdir $$<) ../vsrc/emulator.v -o $$(notdir $$@)

test-v-$(1).near_even.log: test-$(1)/simv
	{ time testfloat_gen -rnear_even $(1) | $$< +rm=0 ; } > $$@ 2>&1

test-v-$(1).minMag.log: test-$(1)/simv
	{ time testfloat_gen -rminMag $(1) | $$< +rm=1 ; } > $$@ 2>&1

test-v-$(1).min.log: test-$(1)/simv
	{ time testfloat_gen -rmin $(1) | $$< +rm=2 ; } > $$@ 2>&1

test-v-$(1).max.log: test-$(1)/simv
	{ time testfloat_gen -rmax $(1) | $$< +rm=3 ; } > $$@ 2>&1

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
	rm -rf test* ucli.key

.PHONY: $(tests) clean
