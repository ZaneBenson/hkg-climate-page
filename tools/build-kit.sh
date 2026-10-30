#!/bin/sh
# Assemble the read-only hkg-climate kit zip from kit/.
# usage: tools/build-kit.sh [output zip]   (default: dist/hkg-climate-kit.zip)
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-$ROOT/dist/hkg-climate-kit.zip}
case "$OUT" in /*) ;; *) OUT=$(pwd)/$OUT ;; esac
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
cp "$ROOT/kit/NEMESIS.data" "$ROOT/kit/NEMESIS.sh" "$ROOT/kit/FILES" "$ROOT/kit/README.txt" "$STAGE/"
# fixed timestamps so the same sources always give the same zip
touch -t 202601010000 "$STAGE"/*
mkdir -p "$(dirname "$OUT")" && rm -f "$OUT"
(cd "$STAGE" && zip -qrX "$OUT" NEMESIS.data NEMESIS.sh FILES README.txt)
echo "$OUT"
