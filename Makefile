default: test

berkeley-softfloat-3/.git:
	git submodule update --init berkeley-softfloat-3

berkeley-testfloat-3/.git:
	git submodule update --init berkeley-testfloat-3

berkeley-softfloat-3/build/Linux-x86_64-GCC/softfloat.a: berkeley-softfloat-3/.git
	$(MAKE) -C berkeley-softfloat-3/build/Linux-x86_64-GCC SPECIALIZE_TYPE=RISCV

berkeley-testfloat-3/build/Linux-x86_64-GCC/testfloat_gen: berkeley-testfloat-3/.git \
                                                           berkeley-softfloat-3/build/Linux-x86_64-GCC/softfloat.a
	$(MAKE) -C berkeley-testfloat-3/build/Linux-x86_64-GCC SPECIALIZE_TYPE=RISCV

ifneq (,$(shell which testfloat_gen 2> /dev/null))
TESTFLOAT_GEN: ;
else
PATH := $(PATH):$(PWD)/berkeley-testfloat-3/build/Linux-x86_64-GCC/
TESTFLOAT_GEN: berkeley-testfloat-3/build/Linux-x86_64-GCC/testfloat_gen
endif

test: TESTFLOAT_GEN
	sbt test

clean:
	git clean -fdx
	git submodule foreach git clean -fdx

.PHONY: test clean
