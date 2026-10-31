#!/bin/sh
# Host test for capture/NEMESIS.sh: start and stop a capture against stub commands and check
# what it writes to the "drive", that the log keeps running after the script returns, and that
# it only ever calls the checked read-only getter codes.
# usage: sh tests/capture/run.sh
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STUBS=$ROOT/tests/capture/stubs
FAILS=0
check() {
    _name=$1; shift
    if "$@"; then echo "ok   $_name"; else echo "FAIL $_name"; FAILS=$((FAILS + 1)); fi
}

T=$(mktemp -d)
cleanup() {
    # stop any capture a failed check left running
    for _c in "$T"/*/CAPTURE/current; do
        [ -f "$_c" ] && { read -r _n _l _t < "$_c"; kill "$_l" "$_t" 2> /dev/null; }
    done
    rm -rf "$T"
}
trap cleanup EXIT

# the head unit's shell is mksh: use it when installed (CI has it), else sh
if command -v mksh > /dev/null 2>&1; then CAP_SH=mksh; else CAP_SH=sh; echo "WARNING: no mksh, the capture runs under sh"; fi

# run_cap <drive>: run the capture from the drive, as NEMESIS Manager does (by full path)
run_cap() {
    mkdir -p "$1"
    cp "$ROOT/capture/NEMESIS.sh" "$1/"
    KIT_SYSROOT=$1/root KIT_TEST_PATH="$STUBS:/usr/bin:/bin" FAKE_AM_LOG=$1/am.log \
        FAKE_SERVICE_LOG=$1/service.log FAKE_LOG_LOG=$1/log.log \
        CAPTURE_MAX_SECONDS=${CAPTURE_MAX_SECONDS:-60} $CAP_SH "$1/NEMESIS.sh" > "$1/out.txt" 2>&1
}
pid_alive() { kill -0 "$1" 2> /dev/null; }
# only the codes checked against the owner's stock files: datc 3 64 39, 38 i32 24,
# 41 i32 <flag>, usm 553 577-580
only_checked_codes() {
    while read -r _l; do
        case "$_l" in
            "service call com.hkmc.micom.datc 3"|"service call com.hkmc.micom.datc 64"|\
            "service call com.hkmc.micom.datc 39"|"service call com.hkmc.micom.datc 38 i32 24"|\
            "service call com.hkmc.micom.usm 553"|"service call com.hkmc.micom.usm 577"|\
            "service call com.hkmc.micom.usm 578"|"service call com.hkmc.micom.usm 579"|\
            "service call com.hkmc.micom.usm 580") ;;
            "service call com.hkmc.micom.datc 41 i32 "*) ;;
            *) echo "unexpected: $_l"; return 1 ;;
        esac
    done < "$1"
}

# --- start, the log keeps running after the script returns, then stop
D=$T/a; run_cap "$D"; C=$D/CAPTURE
check "start: folder 1"                 test -d "$C/1"
check "start: current names folder 1"   sh -c "read -r n l t < '$C/current' && [ \"\$n\" = 1 ]"
read -r _n LPID TPID < "$C/current"
check "start: log still running"        pid_alive "$LPID"
check "start: timer still running"      pid_alive "$TPID"
check "start: log file written"         grep -q 'setFrontClimateControl' "$C/1/logcat.txt"
check "start: session header"           grep -q '^capture hkg-climate 1$' "$C/1/session.txt"
check "start: state read"               test "$(grep -c '^call ' "$C/1/state-start.txt")" = 26
check "start: marker logged"            grep -q 'capture 1 started' "$D/log.log"
check "start: started toast"            grep -q 'capture 1 started' "$D/am.log"
run_cap "$D"
check "stop: log stopped"               sh -c "! kill -0 $LPID 2> /dev/null"
check "stop: timer stopped"             sh -c "! kill -0 $TPID 2> /dev/null"
check "stop: current removed"           test ! -e "$C/current"
check "stop: ran the whole time"        grep -q '^log ran until the stop: yes$' "$C/1/session.txt"
check "stop: no fallback log"           test ! -e "$C/1/logcat-end.txt"
check "stop: state read again"          test "$(grep -c '^call ' "$C/1/state-stop.txt")" = 26
check "stop: file list"                 grep -q 'logcat.txt' "$C/1/session.txt"
check "stop: stopped toast"             grep -q 'capture 1 stopped and saved' "$D/am.log"
check "only checked read-only codes"    only_checked_codes "$D/service.log"
check "no vehicle setter called"        sh -c "! grep -q -E 'datc 65|usm (194|212|213|214)\b' '$D/service.log'"
# --- a second capture on the same drive gets its own folder
run_cap "$D"
check "second capture: folder 2"        test -d "$C/2"
check "second capture: folder 1 kept"   test -s "$C/1/logcat.txt"
run_cap "$D"
check "second capture: stopped"         test ! -e "$C/current"

# --- the log ended early (killed with Manager, or the timer): the stop saves the last minute
D=$T/b; FAKE_LOGCAT_DIES=1 run_cap "$D"; C=$D/CAPTURE
check "early end: start toast says so"  grep -q 'did not start' "$D/am.log"
run_cap "$D"
check "early end: recorded"             grep -q '^log ran until the stop: no$' "$C/1/session.txt"
check "early end: last minute saved"    test -s "$C/1/logcat-end.txt"
check "early end: toast says so"        grep -q 'stopped early' "$D/am.log"

# --- the timer stops the log by itself
D=$T/c; CAPTURE_MAX_SECONDS=1 run_cap "$D"; C=$D/CAPTURE
read -r _n LPID TPID < "$C/current"
sleep 3
check "timer: log stopped by itself"    sh -c "! kill -0 $LPID 2> /dev/null"
run_cap "$D"
check "timer: stop still saves"         grep -q '^log ran until the stop: no$' "$C/1/session.txt"

# --- another build: no state reads at all
D=$T/d; FAKE_MD5_SVC=0123456789abcdef0123456789abcdef run_cap "$D"; C=$D/CAPTURE
check "other build: state skipped"      grep -q '^state skipped' "$C/1/state-start.txt"
check "other build: no service calls"   test ! -s "$D/service.log"
FAKE_MD5_SVC=0123456789abcdef0123456789abcdef run_cap "$D"
D=$T/e; FAKE_PRODUCT=ECOHMC.USA.0000.V015.012.260507 run_cap "$D"
check "other car: no service calls"     test ! -s "$D/service.log"
FAKE_PRODUCT=ECOHMC.USA.0000.V015.012.260507 run_cap "$D"

# --- the head unit's shell lacks these
check "no printf/tail/head/seq/[[ ]]"   sh -c "! grep -n -E '(^|[^A-Za-z_])(printf|tail|head|seq)([^A-Za-z_]|$)|\[\[' '$ROOT/capture/NEMESIS.sh'"

if [ $FAILS -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "all checks passed"
