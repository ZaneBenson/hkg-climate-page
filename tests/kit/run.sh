#!/bin/sh
# Host test for kit/NEMESIS.sh: runs the kit against a fake /system with stubbed
# Android commands and checks what it writes to the "drive".
# usage: sh tests/kit/run.sh
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STUBS=$ROOT/tests/kit/stubs
FAILS=0

check() {
    _name=$1; shift
    if "$@"; then echo "ok   $_name"; else echo "FAIL $_name"; FAILS=$((FAILS + 1)); fi
}

# fake_root <dir>: a stock-like tree; DisplaySettings.odex is deliberately missing
fake_root() {
    mkdir -p "$1/system/app" "$1/system/priv-app" "$1/proc"
    for n in RightScreenHome.apk RightScreenHome.odex DisplaySettings.apk \
             Settings_W.apk Settings_W.odex SettingsCommon.apk SettingsCommon.odex; do
        echo "stock $n" > "$1/system/app/$n"
    done
    echo "stock SystemUI" > "$1/system/priv-app/SystemUI.apk"
    echo "    export BOOTCLASSPATH /system/framework/core.jar:/system/framework/automotive.jar" > "$1/init.environ.rc"
    for l in "MemTotal: 1 kB" "MemFree: 2 kB" "Buffers: 3 kB" "Cached: 4 kB" "SwapCached: 5 kB"; do
        echo "$l" >> "$1/proc/meminfo"
    done
}

# run_kit <fake root> <drive>: run the kit from the drive, as NEMESIS does
run_kit() {
    mkdir -p "$2"
    cp "$ROOT/kit/FILES" "$2/" 2>/dev/null
    (cd "$2" && KIT_SYSROOT=$1 KIT_TEST_PATH="$STUBS:/usr/bin:/bin" FAKE_AM_LOG=$1/am.log \
        sh "$ROOT/kit/NEMESIS.sh" > "$1/kit.out" 2>&1)
}

last_line_done() { _l=; while read -r _x; do _l=$_x; done < "$1"; [ "$_l" = done ]; }

T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT

# --- normal run on the target car
R=$T/a; D=$T/a-drive; fake_root "$R"; run_kit "$R" "$D"; O=$D/HKGCLIMATE
check "summary has kit line"       grep -q '^kit hkg-climate 1$' "$O/summary.txt"
check "target car detected"        grep -q '^car target$' "$O/summary.txt"
check "apk copied"                 cmp -s "$R/system/app/RightScreenHome.apk" "$O/stock/app/RightScreenHome.apk"
check "missing file recorded"      grep -q '^missing /system/app/DisplaySettings.odex$' "$O/collected.txt"
check "copied file recorded"       grep -q '^ok   /system/app/RightScreenHome.apk$' "$O/collected.txt"
check "ss_setting recorded"        grep -q '^ss_setting 10,13,2$' "$O/summary.txt"
check "ss_setting_dr1 recorded"    grep -q '^ss_setting_dr1 null$' "$O/summary.txt"
check "probe has 8 calls"          test "$(grep -c '^call ' "$O/probe.txt")" = 8
check "probe has parcel output"    grep -q 'Result: Parcel' "$O/probe.txt"
check "logcat written"             test -s "$O/logcat-full.txt"
check "df recorded"                grep -q '^/data ' "$O/summary.txt"
check "meminfo 4 lines"            sh -c "grep -q '^Cached: 4 kB$' '$O/summary.txt' && ! grep -q '^SwapCached' '$O/summary.txt'"
check "init.environ.rc copied"     cmp -s "$R/init.environ.rc" "$O/stock/init.environ.rc"
check "priv-app listing"           grep -q 'SystemUI.apk' "$O/stock/priv-app-listing.txt"
check "app listing"                grep -q 'RightScreenHome.apk' "$O/stock/app-listing.txt"
check "finishes with done"         last_line_done "$O/summary.txt"
check "toast shown"                grep -q 'com.hkmc.intent.action.TOAST' "$R/am.log"

# --- other car: still collects, but says so
R=$T/b; D=$T/b-drive; fake_root "$R"
FAKE_PRODUCT=ECOHMC.USA.0000.V015.012.260507 run_kit "$R" "$D"
check "other car flagged"          grep -q '^car other$' "$D/HKGCLIMATE/summary.txt"

# --- settings command failing
R=$T/c; D=$T/c-drive; fake_root "$R"
FAKE_SETTINGS_FAIL=1 run_kit "$R" "$D"
check "settings failure recorded"  grep -q '^ss_setting unavailable$' "$D/HKGCLIMATE/summary.txt"
check "settings failure finishes"  last_line_done "$D/HKGCLIMATE/summary.txt"

# --- probe uid refused
R=$T/d; D=$T/d-drive; fake_root "$R"
FAKE_NEMESIS_UID_FAIL=1 run_kit "$R" "$D"
check "uid refusal recorded"       grep -q 'nemesis: uid refused' "$D/HKGCLIMATE/probe.txt"
check "uid refusal finishes"       last_line_done "$D/HKGCLIMATE/summary.txt"

# --- copy failing (drive full)
R=$T/e; D=$T/e-drive; fake_root "$R"
FAKE_CP_FAIL=RightScreenHome.apk run_kit "$R" "$D"
check "copy failure recorded"      grep -q '^FAIL /system/app/RightScreenHome.apk$' "$D/HKGCLIMATE/collected.txt"
check "copy failure finishes"      last_line_done "$D/HKGCLIMATE/summary.txt"

# --- second run on the same drive replaces the old output
R=$T/f; D=$T/f-drive; fake_root "$R"; run_kit "$R" "$D"
touch "$D/HKGCLIMATE/stale"; run_kit "$R" "$D"
check "old output replaced"        sh -c "test ! -e '$D/HKGCLIMATE/stale' && test -s '$D/HKGCLIMATE/summary.txt'"

if [ $FAILS -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "all checks passed"
