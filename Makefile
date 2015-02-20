default: all

ifeq (, $(shell which testfloat_gen))
$(error "No testfloat_gen in $(PATH), install testfloat_gen")
endif

tests = \
	f32_mulAdd \
	f64_mulAdd \

define test_template

test-$(1)/Test_$(1).cpp: src/main/scala/*.scala
	sbt -DchiselVersion=latest.release "run $(1) --targetDir test-$(1)"

test-$(1)/dut: test-$(1)/Test_$(1).cpp csrc/*.h csrc/*.cpp
	g++ -c -o test-$(1)/emulator.o -Icsrc -Itest-$(1) -include csrc/emulator-$(1).h csrc/emulator.cpp
	g++ -c -o test-$(1)/Test_$(1).o test-$(1)/Test_$(1).cpp
	g++ -o test-$(1)/dut test-$(1)/Test_$(1).o test-$(1)/emulator.o

test-$(1).log: test-$(1)/dut
	echo "testing near_even" > $$@
	time testfloat_gen -rnear_even $(1) | ./test-$(1)/dut 0 >> $$@
	echo "testing minMag" >> $$@
	time testfloat_gen -rminMag $(1) | ./test-$(1)/dut 1 >> $$@
	echo "testing min" >> $$@
	time testfloat_gen -rmin $(1) | ./test-$(1)/dut 2 >> $$@
	echo "testing max" >> $$@
	time testfloat_gen -rmax $(1) | ./test-$(1)/dut 3 >> $$@

$(1): test-$(1).log

.PHONY: $(1)

endef

$(foreach test,$(tests),$(eval $(call test_template,$(test))))

all: $(tests)

clean:
	rm -rf test* ucli.key

.PHONY: $(tests) clean
