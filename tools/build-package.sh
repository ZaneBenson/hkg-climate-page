#!/bin/sh
# Build the HKG Climate Page NEMESIS install zip: an outer NEMESIS.data (type=install) and
# the package packages/hkg.climate.page.zip, which holds NEMESIS.data, scripts/, patch/,
# bin/lpatch (ARM), app/hkgclimate.apk and BUILD ("<git describe> <UTC time>"). No stock
# file is shipped: the head unit rebuilds its panel app from its own copy and patch/.
# usage: tools/build-package.sh [output zip]   (default: dist/hkg-climate-page.zip)
# Needs build/app/hkgclimate.apk (tools/build-app.sh).
#
# Adapted from HMG Leafia Plus (https://github.com/VysseCodes/HMG-Leafia-Plus) at commit 08f4436.
# This program is free software: you can redistribute it and/or modify it under the terms of
# the GNU General Public License as published by the Free Software Foundation, either version 3
# of the License, or (at your option) any later version. It is distributed WITHOUT ANY
# WARRANTY; see the GNU General Public License (LICENSE) for details.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-$ROOT/dist/hkg-climate-page.zip}
case "$OUT" in /*) ;; *) OUT=$(pwd)/$OUT ;; esac
ID=hkg.climate.page
APK=$ROOT/build/app/hkgclimate.apk
[ -s "$APK" ] || { echo "build-package: missing $APK (run tools/build-app.sh)" >&2; exit 1; }

# The head unit's shell (mksh/toolbox) has no printf, tail, head or seq, and no [[ ]].
# Match them as commands (at the start of a command or after a keyword), outside comments,
# so words like "header" or "the head unit" in text pass.
CMD='(^|[;&|({`!]|(^|[[:space:]])(then|do|else|elif|if|while|until|exec|command|xargs))[[:space:]]*(printf|tail|head|seq)([[:space:];&|)`]|$)'
DBL='(^|[[:space:];&|(!])\[\[([[:space:]]|$)'
_bad=0
for _f in $(find "$ROOT/package" -name '*.sh' | LC_ALL=C sort); do
    _hits=$(sed -e 's/^[[:space:]]*#.*//' -e 's/[[:space:]]#.*//' "$_f" | grep -nE -e "$CMD" -e "$DBL" || true)
    if [ -n "$_hits" ]; then
        echo "$_hits" | sed "s|^|${_f#$ROOT/}:|" >&2
        _bad=1
    fi
done
if [ $_bad = 1 ]; then
    echo "build-package: printf, tail, head, seq or [[ in package scripts (not on the head unit's shell)" >&2
    exit 1
fi

STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
I=$STAGE/inner
mkdir -p "$I/scripts" "$I/patch" "$I/bin" "$I/app" "$STAGE/outer/packages"
cp "$ROOT/package/NEMESIS.data" "$I/"
cp "$ROOT"/package/scripts/*.sh "$I/scripts/"
cp "$ROOT"/package/patch/* "$I/patch/"
cp "$ROOT/tools/lpatch/lpatch" "$I/bin/lpatch"
cp "$APK" "$I/app/hkgclimate.apk"
# which source this package was built from; preinstall logs it
echo "$(git -C "$ROOT" describe --always --dirty 2>/dev/null || echo unknown) $(date -u +%Y-%m-%dT%H:%MZ)" > "$I/BUILD"
# fixed modes and timestamps, so only the sources and BUILD change the zip
find "$I" -type d -exec chmod 755 {} +
find "$I" -type f -exec chmod 644 {} +
chmod 755 "$I"/scripts/*.sh "$I/bin/lpatch"
find "$I" -exec touch -t 202601010000 {} +
(cd "$I" && zip -qrX "$STAGE/outer/packages/$ID.zip" NEMESIS.data BUILD scripts patch bin app)
{
    echo "[nemesis]"
    echo "version=1.0"
    echo "type=install"
    echo "path=packages/$ID.zip"
} > "$STAGE/outer/NEMESIS.data"
chmod 644 "$STAGE/outer/NEMESIS.data" "$STAGE/outer/packages/$ID.zip"
find "$STAGE/outer" -exec touch -t 202601010000 {} +
mkdir -p "$(dirname "$OUT")" && rm -f "$OUT"
(cd "$STAGE/outer" && zip -qrX "$OUT" NEMESIS.data packages)
echo "$OUT"
