#!/bin/sh
# Build the patched panel: our hooks in the deodexed stock RightScreenHome plus our page classes,
# reassembled into one classes.dex; then the delta that rebuilds it on the car from the stock odex,
# the stock APK with that classes.dex added, and the package/patch files that gate the install.
# usage: tools/build-panel.sh
#   writes private/work/classes.dex, build/RightScreenHome.apk (both private, never committed) and
#   package/patch/{RightScreenHome.lpd,stock,manifest}; prints the dex md5, delta size and APK md5
# Build inputs come from the read-only private/stock/260507; kit output folders (such as
# private/HKGCLIMATE/stock, which holds whatever the car has now) are never used as build input.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
eval "$("$ROOT/tools/toolchain.sh" env)"
cd "$ROOT"

STOCK=private/stock/260507/app
W=private/work
STOCK_SMALI=$W/panel-stock-smali
SMALI_DIR=$W/panel-smali
PAGE_SMALI=$W/page-smali
DEX=$W/classes.dex
OUT=package/patch
LPD=$OUT/RightScreenHome.lpd
APK=build/RightScreenHome.apk
LPATCH=tools/lpatch/lpatch-host
MAX_LPD=$((6 * 1024 * 1024))   # lpatch MAXDELTA
MAX_DEX=$((12 * 1024 * 1024))  # lpatch MAXOUT

die() { echo "build-panel: $*" >&2; exit 1; }
md5() { md5sum "$1" | cut -d' ' -f1; }
size() { wc -c < "$1" | tr -d ' '; }
smali_count() { find "$1" -name '*.smali' | wc -l | tr -d ' '; }

# the stock files this delta is made against (sizes from the 260507 build)
[ "$(size "$STOCK/RightScreenHome.apk")" = 161884916 ] || die "$STOCK/RightScreenHome.apk is not the 260507 stock APK"
[ "$(size "$STOCK/RightScreenHome.odex")" = 2137656 ] || die "$STOCK/RightScreenHome.odex is not the 260507 stock odex"
cc -DHOST -O2 -Wall -o "$LPATCH" tools/lpatch/lpatch.c  # the host build of the code the car runs

# 0. our page classes
sh tools/build-page.sh > /dev/null

# 1. deodex (cached: deodex-panel.sh only creates the folder when it is complete)
[ -d "$STOCK_SMALI" ] || sh tools/deodex-panel.sh "$STOCK" "$HOME/code/LEAFIA/private/stock/framework" "$STOCK_SMALI"

# 2. hooks
rm -rf "$SMALI_DIR" "$PAGE_SMALI"
cp -R "$STOCK_SMALI" "$SMALI_DIR"
python3 tools/patch-panel.py "$SMALI_DIR" > /dev/null

# 3. our page classes into the same tree
java -cp "$BAKSMALI_CP" org.jf.baksmali.Main d -o "$PAGE_SMALI" build/page/classes.dex
cp -R "$PAGE_SMALI"/. "$SMALI_DIR"/

# 4. reassemble
rm -f "$DEX"
java -cp "$SMALI_CP" org.jf.smali.Main a -j 1 --api 18 -o "$DEX" "$SMALI_DIR"

# 5. every stock class and every page class, nothing lost to a name clash
STOCK_N=$(smali_count "$STOCK_SMALI")
PAGE_N=$(smali_count "$PAGE_SMALI")
DEX_N=$(java -cp "$BAKSMALI_CP" org.jf.baksmali.Main list classes "$DEX" | wc -l | tr -d ' ')
[ "$DEX_N" -eq $((STOCK_N + PAGE_N)) ] || die "classes.dex has $DEX_N classes, want $STOCK_N stock + $PAGE_N page"
[ "$(size "$DEX")" -le "$MAX_DEX" ] || die "classes.dex is $(size "$DEX") bytes, over lpatch's $MAX_DEX"

# 6. delta from the stock odex, checked with the same lpatch code the car runs
mkdir -p "$OUT"
"$PY" tools/mkdelta.py "$STOCK/RightScreenHome.odex" "$DEX" "$LPD" > /dev/null
[ "$(size "$LPD")" -le "$MAX_LPD" ] || die "$LPD is $(size "$LPD") bytes, over lpatch's $MAX_LPD"
rm -f "$W/delta-check.dex"
"$LPATCH" delta "$LPD" "$STOCK/RightScreenHome.odex" "$W/delta-check.dex" > /dev/null
[ "$(md5 "$W/delta-check.dex")" = "$(md5 "$DEX")" ] || die "lpatch delta does not rebuild classes.dex"
rm -f "$W/delta-check.dex"

# 7. the stock APK with our classes.dex added
mkdir -p build
rm -f "$APK"
RESULT=$("$LPATCH" zipadd "$STOCK/RightScreenHome.apk" "$DEX" "$APK")
APK_MD5=$(md5 "$APK")
case $RESULT in
    *" md5=$APK_MD5") ;;
    *) die "zipadd: $RESULT (file md5 $APK_MD5)" ;;
esac

# 8. install gate files
{
    echo "RightScreenHome.apk $(md5 "$STOCK/RightScreenHome.apk")"
    echo "RightScreenHome.odex $(md5 "$STOCK/RightScreenHome.odex")"
} > "$OUT/stock"
echo "RightScreenHome.lpd $APK_MD5 $(size "$APK")" > "$OUT/manifest"

echo "classes.dex $(md5 "$DEX") $(size "$DEX") bytes ($STOCK_N stock + $PAGE_N page classes)"
echo "delta $LPD $(size "$LPD") bytes"
echo "apk $APK $APK_MD5 $(size "$APK") bytes"
