#!/bin/sh
# Host test for tools/toolchain.sh and tools/test-java.sh: the check passes on this host,
# names what is missing when the toolchain folder is absent, `env` exports the variables
# later tools need, and `fetch` refuses a jar whose sha256 does not match the pin.
# usage: sh tests/toolchain/run.sh
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TC=$ROOT/tools/toolchain.sh
FAILS=0
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

check() {
    _name=$1; shift
    if "$@"; then echo "ok   $_name"; else echo "FAIL $_name"; FAILS=$((FAILS + 1)); fi
}

has_line() { grep -q "$1" "$2"; }

# the check passes on this host and reports each tool as "ok <name> <path>"
sh "$TC" check > "$TMP/check.out" 2>&1; rc=$?
check "check exits 0" [ $rc -eq 0 ]
check "check reports junit" has_line '^ok junit ' "$TMP/check.out"
check "check reports baksmali" has_line '^ok baksmali ' "$TMP/check.out"
check "check reports qemu-arm" has_line '^ok qemu-arm ' "$TMP/check.out"
check "check has no missing lines" sh -c "! grep -q '^missing' $TMP/check.out"

# with no toolchain folder it fails and names what is missing
TOOLCHAIN_DIR=/nonexistent sh "$TC" check > "$TMP/gone.out" 2>&1; rc=$?
check "check exits 1 without the toolchain folder" [ $rc -eq 1 ]
check "check says missing junit" has_line '^missing junit' "$TMP/gone.out"
check "check says missing smali" has_line '^missing smali' "$TMP/gone.out"

# env, run directly, prints export lines; sourced from sh it exports
sh "$TC" env > "$TMP/env.out" 2>&1
for v in ANDROID_JAR BUILD_TOOLS SMALI_CP BAKSMALI_CP JUNIT_CP PY QEMU_ARM MOCK_SH; do
    check "env prints $v" has_line "^export $v=" "$TMP/env.out"
done
check "env exports when sourced" sh -c ". '$TC' env && [ -f \"\$ANDROID_JAR\" ] && [ -x \"\$PY\" ] && [ -n \"\$MOCK_SH\" ]"

# fetch refuses an existing jar with the wrong sha256, before downloading anything
mkdir -p "$TMP/tc/junit"
echo "not a jar" > "$TMP/tc/junit/junit-4.13.2.jar"
TOOLCHAIN_DIR=$TMP/tc sh "$TC" fetch > "$TMP/fetch.out" 2>&1; rc=$?
check "fetch exits 1 on a bad jar" [ $rc -eq 1 ]
check "fetch reports the sha256 mismatch" has_line 'sha256 mismatch.*junit-4.13.2.jar' "$TMP/fetch.out"
check "fetch downloaded nothing else" sh -c "[ \"\$(ls '$TMP/tc')\" = junit ] && [ \"\$(ls '$TMP/tc/junit')\" = junit-4.13.2.jar ]"

# the Java test runner copes with there being no core code yet
sh "$ROOT/tools/test-java.sh" > "$TMP/java.out" 2>&1; rc=$?
check "test-java exits 0" [ $rc -eq 0 ]

if [ $FAILS -ne 0 ]; then echo "$FAILS failed"; exit 1; fi
echo "all passed"
