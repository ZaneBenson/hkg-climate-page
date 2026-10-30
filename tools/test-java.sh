#!/bin/sh
# Compile core/src and core/test and run every *Test class with JUnit on the host JDK.
# With no Java sources or no test classes it says so and exits 0. Exit status is JUnit's.
# usage: tools/test-java.sh
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
eval "$("$ROOT/tools/toolchain.sh" env)"
cd "$ROOT"

SRC=$(find core/src core/test -name '*.java' 2> /dev/null | sort)
if [ -z "$SRC" ]; then echo "no tests: no Java sources under core/src or core/test"; exit 0; fi

OUT=build/test-classes
rm -rf "$OUT" && mkdir -p "$OUT"
# shellcheck disable=SC2086 # one path per word; none contain spaces
javac -encoding UTF-8 --release 8 -Xlint:-options -cp "$JUNIT_CP" -d "$OUT" $SRC

# every core/test/**/FooTest.java becomes the class name pkg.FooTest
TESTS=
for f in $(find core/test -name '*Test.java' 2> /dev/null | sort); do
    c=${f#core/test/}; c=${c%.java}
    TESTS="$TESTS $(echo "$c" | tr / .)"
done
if [ -z "$TESTS" ]; then echo "no tests: no *Test classes under core/test"; exit 0; fi

# shellcheck disable=SC2086
java -cp "$OUT:$JUNIT_CP" org.junit.runner.JUnitCore $TESTS
