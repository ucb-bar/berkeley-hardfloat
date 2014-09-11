default: all

tests = \
	sp-fma \
	dp-fma \

$(addsuffix .log, $(addprefix test-, $(tests))): test-%.log: src/main/scala/*.scala
	sbt -DchiselVersion=latest.release "run $* --targetDir test-$*" > $@

$(addsuffix -v.log, $(addprefix test-, $(tests))): test-%-v.log: src/main/scala/*.scala
	sbt -DchiselVersion=latest.release "run $* --backend v --targetDir test-$*-v" > $@

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
