default: all

ifeq (, $(shell which testfloat_gen))
$(error "No testfloat_gen in $(PATH), install testfloat_gen")
endif

tests = \
	sp-fma \

test-sp-fma.log: src/main/scala/*.scala
	sbt -DchiselVersion=latest.release "run sp-fma --targetDir test-sp-fma" | tee $@
	g++ -c -o test-sp-fma/FMA-emulator.o  -I../ -Itest-sp-fma -include csrc/test-sp-fma.h csrc/emulator.cpp
	g++ -c -o test-sp-fma/FMA.o  -I../ -Inull/csrc/  test-sp-fma/FMA.cpp
	g++ -o test-sp-fma/FMA test-sp-fma/FMA.o test-sp-fma/FMA-emulator.o
	echo "testing near_even" >> $@
	time testfloat_gen f32_mulAdd | ./test-sp-fma/FMA 0 >> $@
	echo "testing minMag" >> $@
	time testfloat_gen -rminMag f32_mulAdd | ./test-sp-fma/FMA 1 >> $@
	echo "testing min" >> $@
	time testfloat_gen -rmin f32_mulAdd | ./test-sp-fma/FMA 2 >> $@
	echo "testing max" >> $@
	time testfloat_gen -rmax f32_mulAdd | ./test-sp-fma/FMA 3 >> $@

$(tests): %: test-%.log

$(addsuffix -v, $(tests)): %-v: test-%-v.log

all: $(tests)
	@ if grep FAIL test-*.log; then \
		echo "Test FAILED!!!"; \
		exit 1; \
	fi

verilog: $(addsuffix -v, $(tests))
	@ if grep FAIL test-*-v.log; then \
		echo "Test FAILED!!!"; \
		exit 1; \
	fi

clean:
	rm -rf test* csrc ucli.key

.PHONY: $(tests) clean
