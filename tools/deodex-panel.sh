#!/bin/sh
# Deodex the stock split-screen panel (RightScreenHome.odex) against the car's own framework, with
# baksmali at API 18 (Dalvik's field layout). The boot class path comes from the init.environ.rc
# next to the app folder (its .jar names as .odex). If baksmali skips classes, it runs again with
# every other framework odex after the boot entries. The output folder appears only when complete.
# Build inputs come from the read-only private/stock/260507; kit output folders (such as
# private/HKGCLIMATE/stock, which holds whatever the car has now) are never used as build input.
# usage: tools/deodex-panel.sh [<stock app dir> [<framework dir> [<out dir>]]]
#        defaults: private/stock/260507/app (init.environ.rc: private/stock/260507/init.environ.rc)
#                  ~/code/LEAFIA/private/stock/framework  private/work/panel-stock-smali
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
eval "$("$ROOT/tools/toolchain.sh" env)"

APP=${1:-$ROOT/private/stock/260507/app}
FW=${2:-$HOME/code/LEAFIA/private/stock/framework}
OUT=${3:-$ROOT/private/work/panel-stock-smali}
RC=$(dirname "$APP")/init.environ.rc
ODEX=$APP/RightScreenHome.odex

for f in "$ODEX" "$RC"; do
    [ -f "$f" ] || { echo "deodex-panel: missing $f" >&2; exit 1; }
done
[ -d "$FW" ] || { echo "deodex-panel: missing framework folder $FW" >&2; exit 1; }
[ ! -e "$OUT" ] || { echo "deodex-panel: $OUT already exists (delete it to deodex again)" >&2; exit 1; }

BCP_LINE=$(grep 'export BOOTCLASSPATH ' "$RC" || true)
[ -n "$BCP_LINE" ] || { echo "deodex-panel: no BOOTCLASSPATH in $RC" >&2; exit 1; }
BOOT=
for j in $(echo "${BCP_LINE##* }" | tr ':' ' '); do
    o=$(basename "$j" .jar).odex
    [ -f "$FW/$o" ] || { echo "deodex-panel: boot entry $o not in $FW" >&2; exit 1; }
    BOOT=$BOOT${BOOT:+:}$o
done

TMP=$OUT.tmp
rm -rf "$TMP"
mkdir -p "$(dirname "$OUT")"
trap 'rm -rf "$TMP" "$TMP.log"' EXIT

# deodex <class path>: true if baksmali succeeded without skipping a class (its output: $TMP.log)
deodex() {
    rm -rf "$TMP"
    java -cp "$BAKSMALI_CP" org.jf.baksmali.Main x -a 18 -d "$FW" -b "$1" -o "$TMP" "$ODEX" \
        > "$TMP.log" 2>&1 || return 1
    ! grep -q 'skipping class' "$TMP.log"
}

if ! deodex "$BOOT"; then
    echo "deodex-panel: the boot class path was not enough; adding the other framework odex files"
    EXTRA=
    for f in $(cd "$FW" && ls *.odex | sort); do
        case ":$BOOT:" in *":$f:"*) continue ;; esac
        EXTRA=$EXTRA:$f
    done
    if ! deodex "$BOOT$EXTRA"; then
        grep -i 'skipping class\|exception' "$TMP.log" | sort -u >&2 || true
        echo "deodex-panel: failed" >&2
        exit 1
    fi
fi
mv "$TMP" "$OUT"
echo "$OUT: $(find "$OUT" -name '*.smali' | wc -l) classes"
