#!/bin/sh
# Host test for tools/toolchain.sh and tools/test-java.sh: the check passes on this host,
# names what is missing when the toolchain folder is absent, `env` gives eval-able exports
# with whole-file class paths, and `fetch` refuses a jar whose sha256 does not match the pin.
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

# env is used as eval "$(tools/toolchain.sh env)"; run in a subshell so nothing leaks here
env_out=$(eval "$(sh "$TC" env)" && for v in ANDROID_JAR BUILD_TOOLS SMALI_CP BAKSMALI_CP JUNIT_CP PY QEMU_ARM MOCK_SH; do
    eval "[ -n \"\$$v\" ]" || echo "unset $v"; done)
check "env sets all eight variables" [ -z "$env_out" ]
check "env leaves the caller's shell options alone" sh -c "eval \"\$(sh '$TC' env)\"; case \$- in *e*) exit 1;; esac"
check "env paths exist" sh -c "eval \"\$(sh '$TC' env)\" && [ -f \"\$ANDROID_JAR\" ] && [ -x \"\$PY\" ] && [ -d \"\$BUILD_TOOLS\" ]"
# every class-path entry is one existing file (spaces or a bad join would break this)
for v in SMALI_CP BAKSMALI_CP JUNIT_CP; do
    bad=$(eval "$(sh "$TC" env)"; eval "cp=\$$v"; IFS=:; for f in $cp; do [ -f "$f" ] || echo "$f"; done)
    check "$v entries are existing files" [ -z "$bad" ]
done

# fetch refuses an existing jar with the wrong sha256, before downloading anything
mkdir -p "$TMP/tc/junit"
echo "not a jar" > "$TMP/tc/junit/junit-4.13.2.jar"
TOOLCHAIN_DIR=$TMP/tc sh "$TC" fetch > "$TMP/fetch.out" 2>&1; rc=$?
check "fetch exits 1 on a bad jar" [ $rc -eq 1 ]
check "fetch reports the sha256 mismatch" has_line 'sha256 mismatch.*junit-4.13.2.jar' "$TMP/fetch.out"
check "fetch downloaded nothing else" sh -c "[ \"\$(ls '$TMP/tc')\" = junit ] && [ \"\$(ls '$TMP/tc/junit')\" = junit-4.13.2.jar ]"

# the Java test runner says "no tests" and exits 0 when the tree has no core code
mkdir -p "$TMP/root/tools"
cp "$ROOT/tools/toolchain.sh" "$ROOT/tools/test-java.sh" "$TMP/root/tools/"
TOOLCHAIN_DIR=$ROOT/private/toolchain sh "$TMP/root/tools/test-java.sh" > "$TMP/java.out" 2>&1; rc=$?
check "test-java exits 0 with no core code" [ $rc -eq 0 ]
check "test-java says no tests" has_line '^no tests' "$TMP/java.out"

if [ $FAILS -ne 0 ]; then echo "$FAILS failed"; exit 1; fi
echo "all passed"
