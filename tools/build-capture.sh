#!/bin/sh
# Assemble the read-only hkg-climate capture zip from capture/ (no NEMESIS.data: it runs from
# NEMESIS Manager's "Run USB NEMESIS.sh", a first run starts the capture, a second stops it).
# usage: tools/build-capture.sh [output zip]   (default: dist/hkg-climate-capture.zip)
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-$ROOT/dist/hkg-climate-capture.zip}
case "$OUT" in /*) ;; *) OUT=$(pwd)/$OUT ;; esac
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
cp "$ROOT/capture/NEMESIS.sh" "$ROOT/capture/README.txt" "$STAGE/"
chmod 755 "$STAGE/NEMESIS.sh"; chmod 644 "$STAGE/README.txt"
# fixed timestamps so the same sources always give the same zip
touch -t 202601010000 "$STAGE"/*
mkdir -p "$(dirname "$OUT")" && rm -f "$OUT"
(cd "$STAGE" && zip -qrX "$OUT" NEMESIS.sh README.txt)
echo "$OUT"
