#!/bin/sh
# Build one zip for one USB stick that carries both the HKG Climate Page install and the
# read-only hkg-climate kit. Unzip it onto the root of an empty exFAT/FAT32 stick.
#   NEMESIS.data, packages/  the install (from tools/build-package.sh): runs on insert
#   NEMESIS.sh, FILES        the kit, without its own NEMESIS.data: run it from NEMESIS
#                            Manager, "Run USB NEMESIS.sh"
#   README-USB.txt           what to do with the stick
# A stick has only one NEMESIS.data, so only the install starts on insert; the install
# renames it to NEMESIS.data.installed when it finishes.
# usage: tools/build-usb.sh [output zip]   (default: dist/hkg-climate-usb.zip)
# Needs dist/hkg-climate-page.zip (tools/build-package.sh).
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-$ROOT/dist/hkg-climate-usb.zip}
case "$OUT" in /*) ;; *) OUT=$(pwd)/$OUT ;; esac
PKG=$ROOT/dist/hkg-climate-page.zip
[ -s "$PKG" ] || { echo "build-usb: missing $PKG (run tools/build-package.sh)" >&2; exit 1; }

STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
S=$STAGE/stick
mkdir -p "$S"
(cd "$S" && unzip -q "$PKG")
cp "$ROOT/kit/NEMESIS.sh" "$ROOT/kit/FILES" "$S/"
cat > "$S/README-USB.txt" <<'EOF'
HKG Climate Page - install and kit on one USB stick

Copy everything in this zip to the root of an empty exFAT/FAT32 USB stick with at
least 400 MB free.

Install
1. Car on, plug in the stick, tap Yes. The package installs and the head unit
   restarts. At the end the install renames NEMESIS.data to
   NEMESIS.data.installed, so the stick does not install again.

Kit (after testing)
2. Leave the stick in (or plug it back in; it must be the only stick).
3. Open NEMESIS Manager (three presses of the button chosen in its Options) and
   choose "Run USB NEMESIS.sh". Wait for "hkg-climate kit finished, unplug the
   drive" (a few minutes), then unplug the stick.
4. The kit writes an HKGCLIMATE folder on the stick. It holds Hyundai system
   files, the Android log and the install log: keep it private and only send it
   to the person who asked for it.

If "Run USB NEMESIS.sh" is missing or Manager closes: put the kit's own
NEMESIS.data (from hkg-climate-kit.zip) on the stick instead and plug it in; the
kit then runs on insert like before.

The kit changes nothing on the car and sends no vehicle commands.
EOF
find "$S" -type d -exec chmod 755 {} +
find "$S" -type f -exec chmod 644 {} +
chmod 755 "$S/NEMESIS.sh"
find "$S" -exec touch -t 202601010000 {} +
mkdir -p "$(dirname "$OUT")" && rm -f "$OUT"
(cd "$S" && zip -qrX "$OUT" NEMESIS.data packages NEMESIS.sh FILES README-USB.txt)
echo "$OUT"
