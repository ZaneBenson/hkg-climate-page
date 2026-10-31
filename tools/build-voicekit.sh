#!/bin/sh
# Assemble the read-only hkg-climate voice kit zip from voicekit/.
# usage: tools/build-voicekit.sh [output zip]   (default: dist/hkg-climate-voicekit.zip)
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-$ROOT/dist/hkg-climate-voicekit.zip}
case "$OUT" in /*) ;; *) OUT=$(pwd)/$OUT ;; esac
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
cp "$ROOT/voicekit/NEMESIS.data" "$ROOT/voicekit/NEMESIS.sh" "$ROOT/voicekit/FILES" "$ROOT/voicekit/README.txt" "$STAGE/"
chmod 755 "$STAGE/NEMESIS.sh"; chmod 644 "$STAGE/NEMESIS.data" "$STAGE/FILES" "$STAGE/README.txt"
# fixed timestamps so the same sources always give the same zip
touch -t 202601010000 "$STAGE"/*
mkdir -p "$(dirname "$OUT")" && rm -f "$OUT"
(cd "$STAGE" && zip -qrX "$OUT" NEMESIS.data NEMESIS.sh FILES README.txt)
echo "$OUT"
