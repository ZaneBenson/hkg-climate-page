#!/bin/sh
# Host test for voicekit/NEMESIS.sh: runs the voice kit against a fake head unit and checks
# what it copies to the "drive" (and that nothing comes from /data).
# usage: sh tests/voicekit/run.sh
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STUBS=$ROOT/tests/kit/stubs
FAILS=0
check() {
    _name=$1; shift
    if "$@"; then echo "ok   $_name"; else echo "FAIL $_name"; FAILS=$((FAILS + 1)); fi
}
if command -v mksh > /dev/null 2>&1; then KIT_SH=mksh; else KIT_SH=sh; echo "WARNING: no mksh, the kit runs under sh"; fi

# fake_root <dir>: the voice apps (TAppVRM's odex deliberately missing), some libraries, the
# voice data partition with a nested US-English tree and another language, and /data voice files
fake_root() {
    mkdir -p "$1/system/app" "$1/system/lib" "$1/system/vendor/lib" "$1/system/framework" \
        "$1/storage/vr2/vr2/LPTE/DATA/ENU/sub" "$1/storage/vr2/vr2/LPTE/DATA/FRC" \
        "$1/data/data/com.mobis.voicehandler/LPTE/WORKPLACE/ENU"
    for n in HKMC_VoiceHandler.apk HKMC_VoiceHandler.odex HKMC_VoiceCommand.apk \
             HKMC_VoiceCommand.odex HKMC_TAppVRM.apk HKMC_Climate.apk; do
        echo "stock $n" > "$1/system/app/$n"
    done
    echo "stock automotive" > "$1/system/framework/automotive.odex"
    for n in libVoCon3200.so liblpte_engine.so libvr_bridge.so libCarPlayAndroid.so libc.so; do
        echo "stock $n" > "$1/system/lib/$n"
    done
    echo "vendor" > "$1/system/vendor/lib/libvendor.so"
    echo "grammar" > "$1/storage/vr2/vr2/LPTE/DATA/ENU/climate.CFB"
    echo "dict" > "$1/storage/vr2/vr2/LPTE/DATA/ENU/sub/words.DFT"
    echo "french" > "$1/storage/vr2/vr2/LPTE/DATA/FRC/climate.CFB"
    echo "owner voice data" > "$1/data/data/com.mobis.voicehandler/LPTE/WORKPLACE/ENU/user.dat"
}

# run_kit <fake root> <drive>: run the kit from the drive root, as NEMESIS's USB handler does
run_kit() {
    mkdir -p "$2"
    cp "$ROOT/voicekit/NEMESIS.sh" "$ROOT/voicekit/FILES" "$2/"
    (cd "$2" && KIT_SYSROOT=$1 KIT_TEST_PATH="$STUBS:/usr/bin:/bin" FAKE_AM_LOG=$1/am.log \
        $KIT_SH NEMESIS.sh > "$1/kit.out" 2>&1)
}

T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT

R=$T/a; D=$T/a-drive; fake_root "$R"; run_kit "$R" "$D"; O=$D/HKGVOICE
check "summary header"               grep -q '^voicekit hkg-climate 1$' "$O/summary.txt"
check "target car detected"          grep -q '^car target$' "$O/summary.txt"
check "voice handler apk copied"     cmp -s "$R/system/app/HKMC_VoiceHandler.apk" "$O/stock/app/HKMC_VoiceHandler.apk"
check "voice handler odex copied"    cmp -s "$R/system/app/HKMC_VoiceHandler.odex" "$O/stock/app/HKMC_VoiceHandler.odex"
check "voice command copied"         test -f "$O/stock/app/HKMC_VoiceCommand.odex"
check "framework odex copied"        test -f "$O/stock/framework/automotive.odex"
check "missing odex recorded"        grep -q '^missing /system/app/HKMC_TAppVRM.odex$' "$O/summary.txt"
check "unlisted app not copied"      test ! -e "$O/stock/app/HKMC_Climate.apk"
check "VoCon library copied"         test -f "$O/stock/lib/libVoCon3200.so"
check "lpte library copied"          test -f "$O/stock/lib/liblpte_engine.so"
check "vr library copied"            test -f "$O/stock/lib/libvr_bridge.so"
check "other libraries left"         sh -c "[ ! -e '$O/stock/lib/libc.so' ] && [ ! -e '$O/stock/lib/libCarPlayAndroid.so' ]"
check "lib listing has all"          grep -q 'libc.so' "$O/lib-listing.txt"
check "vendor lib listing"           grep -q 'libvendor.so' "$O/vendor-lib-listing.txt"
check "vr2 listing has FRC too"      grep -q 'FRC' "$O/vr2-listing.txt"
check "ENU grammar copied"           cmp -s "$R/storage/vr2/vr2/LPTE/DATA/ENU/climate.CFB" "$O/vr2/climate.CFB"
check "ENU nested file copied"       cmp -s "$R/storage/vr2/vr2/LPTE/DATA/ENU/sub/words.DFT" "$O/vr2/sub/words.DFT"
check "other language not copied"    sh -c "! grep -rq french '$O/vr2'"
check "nothing from /data"           sh -c "! grep -rq 'owner voice data' '$O'"
check "finishes with done"           sh -c "while read -r l; do last=\$l; done < '$O/summary.txt'; [ \"\$last\" = done ]"
check "toast shown"                  grep -q 'voice kit finished' "$R/am.log"

# no voice data partition: recorded, still finishes
R=$T/b; D=$T/b-drive; fake_root "$R"; rm -r "$R/storage/vr2"; run_kit "$R" "$D"
check "no vr2: recorded"             grep -q '^missing /storage/vr2/vr2/LPTE/DATA/ENU$' "$D/HKGVOICE/summary.txt"
check "no vr2: finishes"             grep -q '^done$' "$D/HKGVOICE/summary.txt"

check "no printf/tail/head/seq/[[ ]]" sh -c "! grep -n -E '(^|[^A-Za-z_])(printf|tail|head|seq)([^A-Za-z_]|$)|\[\[' '$ROOT/voicekit/NEMESIS.sh'"

if [ $FAILS -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "all checks passed"
