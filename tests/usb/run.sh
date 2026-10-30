#!/bin/sh
# Host test for tools/build-usb.sh: the combined stick has the install descriptor at the
# root, the package and the kit beside it, and no kit descriptor competing with it.
# usage: sh tests/usb/run.sh   (needs dist/hkg-climate-page.zip from tools/build-package.sh)
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
FAILS=0
check() {
    _name=$1; shift
    if "$@"; then echo "ok   $_name"; else echo "FAIL $_name"; FAILS=$((FAILS + 1)); fi
}

T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
sh "$ROOT/tools/build-usb.sh" "$T/usb.zip" > /dev/null || { echo "FAIL build-usb.sh"; exit 1; }
mkdir "$T/stick" "$T/pkg"
(cd "$T/stick" && unzip -q "$T/usb.zip")
(cd "$T/pkg" && unzip -q "$ROOT/dist/hkg-climate-page.zip")

check "descriptor is the install one"   cmp -s "$T/pkg/NEMESIS.data" "$T/stick/NEMESIS.data"
check "descriptor says type=install"     grep -qx 'type=install' "$T/stick/NEMESIS.data"
check "package is the built one"         cmp -s "$T/pkg/packages/hkg.climate.page.zip" "$T/stick/packages/hkg.climate.page.zip"
check "kit script is there"              cmp -s "$ROOT/kit/NEMESIS.sh" "$T/stick/NEMESIS.sh"
check "kit file list is there"           cmp -s "$ROOT/kit/FILES" "$T/stick/FILES"
check "readme is there"                  test -s "$T/stick/README-USB.txt"
check "exactly one NEMESIS.data"         test "$(find "$T/stick" -name NEMESIS.data | wc -l)" = 1
check "nothing else at the root"         test "$(cd "$T/stick" && LC_ALL=C ls | tr "\n" " ")" = "FILES NEMESIS.data NEMESIS.sh README-USB.txt packages "
sh "$ROOT/tools/build-usb.sh" "$T/usb2.zip" > /dev/null
check "same inputs give the same zip"    cmp -s "$T/usb.zip" "$T/usb2.zip"

if [ $FAILS -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "all checks passed"
