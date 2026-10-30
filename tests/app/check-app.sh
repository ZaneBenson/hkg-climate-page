#!/bin/sh
# Host check of the app build: builds the signed APK and asserts its manifest facts, its v1
# signature, and that no stock stub class or send-path reference is packaged.
# usage: sh tests/app/check-app.sh   (prints one line per check; exit 1 if any check fails)
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
eval "$("$ROOT/tools/toolchain.sh" env)"
cd "$ROOT"
FAILS=0
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

check() {
    _name=$1; shift
    if "$@" > /dev/null 2>&1; then echo "ok   $_name"; else echo "FAIL $_name"; FAILS=$((FAILS + 1)); fi
}

APK=build/app/hkgclimate.apk
rm -f "$APK"
check "build-app.sh builds the apk" sh tools/build-app.sh
check "apk exists" [ -s "$APK" ]

if [ -s "$APK" ]; then
    "$BUILD_TOOLS/aapt" dump badging "$APK" > "$TMP/badging" 2>&1
    unzip -p "$APK" classes.dex > "$TMP/classes.dex" 2> /dev/null
    "$BUILD_TOOLS/dexdump" "$TMP/classes.dex" > "$TMP/dex.txt" 2>&1
    unzip -Z1 "$APK" > "$TMP/entries" 2>&1
fi
: >> "$TMP/badging"; : >> "$TMP/dex.txt"; : >> "$TMP/entries"

check "package is hkgclimate.app" grep -q "^package: name='hkgclimate.app'" "$TMP/badging"
check "versionName is 0.1.0-test1" grep -q "versionName='0.1.0-test1'" "$TMP/badging"
check "sdkVersion is 19" grep -q "^sdkVersion:'19'" "$TMP/badging"
check "automotive library is not required" grep -q "^uses-library-not-required:'automotive'" "$TMP/badging"
check "WRITE_SETTINGS is requested" grep -q "uses-permission: name='android.permission.WRITE_SETTINGS'" "$TMP/badging"
check "launcher activity is SettingsActivity" grep -q "launchable-activity: name='hkgclimate.app.SettingsActivity'" "$TMP/badging"
check "apk has classes.dex" grep -qx 'classes.dex' "$TMP/entries"
check "apk signature verifies (v1, min sdk 19)" "$BUILD_TOOLS/apksigner" verify --min-sdk-version 19 "$APK"
check "apk is zip aligned" "$BUILD_TOOLS/zipalign" -c 4 "$APK"
check "dex has our ClimateService" grep -q "Lhkgclimate/app/ClimateService;" "$TMP/dex.txt"
check "dex has PageLink" grep -q "Lhkgclimate/link/PageLink;" "$TMP/dex.txt"
check "dex has core classes" grep -q "Lhkgclimate/core/ClimateCore;" "$TMP/dex.txt"
check "dex lists classes" grep -q "Class descriptor" "$TMP/dex.txt"
check "dex has no class under Lcom/hkmc/" sh -c "! grep -q \"Class descriptor *: *'Lcom/hkmc/\" $TMP/dex.txt"
check "dex has no reference to Lcom/hkmc/usm/" sh -c "! grep -q 'Lcom/hkmc/usm/' $TMP/dex.txt"

if [ "$FAILS" -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "all checks passed"
