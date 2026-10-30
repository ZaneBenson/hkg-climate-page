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

# fake_root <dir>: a stock-like tree; DisplaySettings.odex is deliberately missing. Our install
# log is there, beside another /data file the kit must not take
fake_root() {
    mkdir -p "$1/system/app" "$1/system/priv-app" "$1/system/framework" "$1/proc" "$1/data/nemesis"
    echo "postinstall hkg.climate.page" > "$1/data/nemesis/hkg.climate.page.log"
    echo "not ours" > "$1/data/nemesis/other.log"
    echo "stock automotive" > "$1/system/framework/automotive.odex"
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

# run_kit <fake root> <drive>: run the kit from the drive, as NEMESIS's USB handler does
# (script on the drive, started from the drive root)
run_kit() {
    mkdir -p "$2"
    cp "$ROOT/kit/NEMESIS.sh" "$2/"
    cp "$ROOT/kit/FILES" "$2/" 2>/dev/null
    # FAKE_MD5 defaults to the owner's stock automotive.odex
    (cd "$2" && KIT_SYSROOT=$1 KIT_TEST_PATH="$STUBS:/usr/bin:/bin" FAKE_AM_LOG=$1/am.log \
        FAKE_SERVICE_LOG=$1/service.log FAKE_MD5=${FAKE_MD5-85f94dc6b9f7b331b8edcb0ec2183261} \
        sh NEMESIS.sh > "$1/kit.out" 2>&1)
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
check "install log copied"         cmp -s "$R/data/nemesis/hkg.climate.page.log" "$O/hkg.climate.page.log"
check "install log recorded"       grep -q '^ok   /data/nemesis/hkg.climate.page.log$' "$O/collected.txt"
check "nothing else from /data"    sh -c "! grep -rq 'not ours' '$O'"

# --- no install log (the package was never installed)
R=$T/h; D=$T/h-drive; fake_root "$R"; rm "$R/data/nemesis/hkg.climate.page.log"; run_kit "$R" "$D"
check "no install log: recorded"   grep -q '^missing /data/nemesis/hkg.climate.page.log$' "$D/HKGCLIMATE/collected.txt"
check "no install log: no file"    test ! -e "$D/HKGCLIMATE/hkg.climate.page.log"
check "no install log: finishes"   last_line_done "$D/HKGCLIMATE/summary.txt"

# --- other car: still collects, but says so
R=$T/b; D=$T/b-drive; fake_root "$R"
FAKE_PRODUCT=ECOHMC.USA.0000.V015.012.260507 run_kit "$R" "$D"
check "other car flagged"          grep -q '^car other$' "$D/HKGCLIMATE/summary.txt"
check "other car: no binder calls" test ! -s "$R/service.log"
check "other car: probe skipped"   grep -q '^probe skipped' "$D/HKGCLIMATE/probe.txt"

# --- target car id but a different automotive.odex: the codes may mean other methods
R=$T/g; D=$T/g-drive; fake_root "$R"
FAKE_MD5=00000000000000000000000000000000 run_kit "$R" "$D"
check "odex mismatch: no calls"    test ! -s "$R/service.log"
check "odex mismatch: skipped"     grep -q '^probe skipped' "$D/HKGCLIMATE/probe.txt"
check "odex mismatch finishes"     last_line_done "$D/HKGCLIMATE/summary.txt"

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
R=$T/i; D=$T/i-drive; fake_root "$R"
FAKE_CP_FAIL=hkg.climate.page.log run_kit "$R" "$D"
check "install log copy failure recorded" grep -q '^FAIL /data/nemesis/hkg.climate.page.log$' "$D/HKGCLIMATE/collected.txt"
check "install log copy failure finishes" last_line_done "$D/HKGCLIMATE/summary.txt"

# --- second run on the same drive replaces the old output
R=$T/f; D=$T/f-drive; fake_root "$R"; run_kit "$R" "$D"
touch "$D/HKGCLIMATE/stale"; run_kit "$R" "$D"
check "old output replaced"        sh -c "test ! -e '$D/HKGCLIMATE/stale' && test -s '$D/HKGCLIMATE/summary.txt'"

# --- started by NEMESIS Manager's "Run USB NEMESIS.sh": the kit is called by its full path
# from some other folder, and must still write to its own drive
R=$T/m; D=$T/m-drive; fake_root "$R"; mkdir -p "$D" "$T/elsewhere"
cp "$ROOT/kit/NEMESIS.sh" "$ROOT/kit/FILES" "$D/"
(cd "$T/elsewhere" && KIT_SYSROOT=$R KIT_TEST_PATH="$STUBS:/usr/bin:/bin" FAKE_AM_LOG=$R/am.log \
    FAKE_SERVICE_LOG=$R/service.log FAKE_MD5=85f94dc6b9f7b331b8edcb0ec2183261 \
    sh "$D/NEMESIS.sh" > "$R/kit.out" 2>&1)
check "run by full path writes to the drive" last_line_done "$D/HKGCLIMATE/summary.txt"
check "run by full path copies the files"    cmp -s "$R/system/app/RightScreenHome.apk" "$D/HKGCLIMATE/stock/app/RightScreenHome.apk"
check "run by full path leaves the cwd alone" test ! -e "$T/elsewhere/HKGCLIMATE"

if [ $FAILS -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "all checks passed"
