default: all

tests = \
	sp-fma \
	dp-fma \

$(addsuffix .log, $(addprefix test-, $(tests))): test-%.log: src/main/scala/*.scala
	sbt -DchiselVersion=latest.release "run $* --targetDir test-$*" > $@

$(tests): %: test-%.log

all: $(tests)

clean:
	rm -rf test*

.PHONY: $(tests) clean
