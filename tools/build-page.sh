#!/bin/sh
# Build the page dex: compile link/src and panel/src (against the stub of the stock base class,
# which is never packaged) and dex only our own classes.
# usage: tools/build-page.sh          writes build/page/classes.dex and prints its path
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
eval "$("$ROOT/tools/toolchain.sh" env)"
cd "$ROOT"

OUT=build/page
rm -rf "$OUT" build/panel-stub
mkdir -p "$OUT/classes" build/panel-stub

# the stub declares the stock base class names we use; javac/d8 class path only
# shellcheck disable=SC2046 # one path per word; none contain spaces
javac -encoding UTF-8 --release 8 -Xlint:-options -cp "$ANDROID_JAR" -d build/panel-stub \
    $(find panel/stub/src -name '*.java' | sort)

# shellcheck disable=SC2046
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$ANDROID_JAR" \
    -cp build/panel-stub -d "$OUT/classes" \
    $(find link/src panel/src -name '*.java' | sort)

# dex only our own classes (never build/panel-stub)
# shellcheck disable=SC2046
"$BUILD_TOOLS/d8" --min-api 19 --lib "$ANDROID_JAR" --classpath build/panel-stub --output "$OUT" \
    $(find "$OUT/classes" -name '*.class' | sort)
echo "$OUT/classes.dex"
