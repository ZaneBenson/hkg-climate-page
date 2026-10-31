#!/system/bin/sh
# hkg-climate capture - READ ONLY. Changes nothing on the car and sends no vehicle commands.
#
# Records the Android log while the owner uses the stock climate controls, so we can see
# exactly which command and value each control sends. Run it from NEMESIS Manager
# ("Run USB NEMESIS.sh"):
#   first run    starts writing the Android log to CAPTURE/<n>/ on this drive and records
#                the climate state; it stops by itself after MAX_SECONDS
#   second run   stops it and records the climate state again
# Each start makes a new numbered folder, so several captures fit on one drive.
#
# CAPTURE/<n>/
#   session.txt       kit version, product, start and stop times, whether the log ran the
#                     whole time, the file list
#   logcat.txt[.N]    the Android log from about a minute before the start until the stop:
#                     can hold phone and contact names, Bluetooth addresses and other ids
#   logcat-end.txt    only if the background log had ended early: the last minute
#   state-start.txt   climate and steering-wheel state reads at the start (stock services'
#   state-stop.txt    cached values, read-only getters) and at the stop
#
# CAPTURE/ holds Hyundai logs: keep it private, never post it.
#
# Host tests set KIT_SYSROOT (a fake root), KIT_TEST_PATH (stub commands) and
# CAPTURE_MAX_SECONDS.
export PATH=${KIT_TEST_PATH:+$KIT_TEST_PATH:}/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
export LD_LIBRARY_PATH=/vendor/lib:/system/lib

SYS=${KIT_SYSROOT:-}
# The drive is the folder this script is in (NEMESIS Manager calls it by full path).
case "$0" in */*) KIT=${0%/*} ;; *) KIT=. ;; esac
KIT=$(cd "$KIT" && pwd) || exit 1
CAP=$KIT/CAPTURE
CURRENT=$CAP/current
MAX_SECONDS=${CAPTURE_MAX_SECONDS:-1800}
# The getter codes below were checked against these two files of NEEV23.USA 260507 (the
# interface codes in automotive.odex, the service code in automotive-service.odex). On
# another build the same code could be a setter, which would send a vehicle command.
PROBE_ODEX_MD5=85f94dc6b9f7b331b8edcb0ec2183261
PROBE_SVC_MD5=23e123f1cc296943b32391a70b2f20f5

toast() {
    am broadcast -a com.hkmc.intent.action.TOAST \
        -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true > /dev/null 2>&1
}

alive() { [ -n "$1" ] && kill -0 "$1" 2> /dev/null; }

# state <file>: read-only getters (each returns a cached value and logs it; none sends)
state() {
    _p=$(getprop ro.build.product)
    _m=$(md5 "$SYS/system/framework/automotive.odex" 2>/dev/null); _m=${_m%% *}
    _s=$(md5 "$SYS/system/framework/automotive-service.odex" 2>/dev/null); _s=${_s%% *}
    echo "date $(date)" > "$1"
    case "$_p" in NEEV23.USA.*.260507) _t=1 ;; *) _t=0 ;; esac
    if [ $_t != 1 ] || [ "$_m" != "$PROBE_ODEX_MD5" ] || [ "$_s" != "$PROBE_SVC_MD5" ]; then
        echo "state skipped (not the checked build: product $_p, automotive.odex md5 ${_m:-unreadable}, automotive-service.odex md5 ${_s:-unreadable})" >> "$1"
        return
    fi
    for _c in "3 getTemperatureUnit" "64 supportFrontClimateControl" "39 getDriverOnlyValue"; do
        echo "call com.hkmc.micom.datc $_c" >> "$1"
        service call com.hkmc.micom.datc ${_c%% *} >> "$1" 2>&1
    done
    echo "call com.hkmc.micom.datc 38 i32 24 getClimateInfo(0x18)" >> "$1"
    service call com.hkmc.micom.datc 38 i32 24 >> "$1" 2>&1
    # getVRClimateStatus(flag): 1 TEMP_DISP_STATUS, 2 TEMP, 5 BLOWER_LEVEL, 6 BLOWER_MODE,
    # 8 AUTO, 9 AC, 10 FRONT_DEFOG, 11 INTAKE, 12 OP_STATE, 13 TEMP_UNIT, 22 SYNC,
    # 24 DRIVER_ONLY, 28-31 TEMP_DR_C, TEMP_PS_C, TEMP_DR_F, TEMP_PS_F, 212 TEMP_PS
    for _f in 1 2 5 6 8 9 10 11 12 13 22 24 28 29 30 31 212; do
        echo "call com.hkmc.micom.datc 41 i32 $_f getVRClimateStatus($_f)" >> "$1"
        service call com.hkmc.micom.datc 41 i32 $_f >> "$1" 2>&1
    done
    for _c in "553 getClimateControlECOMode" "577 getSWRC_SteeringwheelHeatingOpt" \
              "578 getSWRC_SteeringwheelHeatingSWInf" "579 getSWRC_SteeringwheelHeatIndStep1" \
              "580 getSWRC_SteeringwheelHeatIndStep2"; do
        echo "call com.hkmc.micom.usm $_c" >> "$1"
        service call com.hkmc.micom.usm ${_c%% *} >> "$1" 2>&1
    done
}

mkdir -p "$CAP" || exit 1

# a capture is running (or was, and ended early): stop it
if [ -f "$CURRENT" ]; then
    read -r N LPID TPID < "$CURRENT"
    S=$CAP/$N
    log -t HkgCapture "capture $N stopping"
    sleep 1
    if alive "$LPID"; then _ran=yes; else _ran=no; fi
    alive "$TPID" && kill "$TPID" 2> /dev/null
    alive "$LPID" && kill "$LPID" 2> /dev/null
    # let the log close its file before the sync
    _i=0
    while alive "$LPID" && [ $_i -lt 5 ]; do sleep 1; _i=$((_i + 1)); done
    if [ $_ran = no ]; then
        logcat -d -v threadtime > "$S/logcat-end.txt" 2>&1
    fi
    state "$S/state-stop.txt"
    {
        echo "stop $(date)"
        echo "log ran until the stop: $_ran"
        echo ""
        echo "===== files"
        ls -l "$S"
    } >> "$S/session.txt" 2>&1
    rm -f "$CURRENT"
    sync
    if [ $_ran = yes ]; then
        toast "hkg-climate capture $N stopped and saved. Unplug the drive."
    else
        toast "hkg-climate capture $N: the log had stopped early (timer or Manager). Saved what there is. Unplug the drive."
    fi
    exit 0
fi

# start a new capture in the first free numbered folder
N=1
while [ -e "$CAP/$N" ]; do N=$((N + 1)); done
S=$CAP/$N
mkdir -p "$S" || exit 1
{
    echo "capture hkg-climate 1"
    echo "product $(getprop ro.build.product)"
    echo "start $(date)"
    echo "stops by itself after $MAX_SECONDS s"
} > "$S/session.txt"
state "$S/state-start.txt"

# the log keeps running after this script returns: ignore the hang-up signal, and stop by
# itself after MAX_SECONDS in case the second run never comes
( trap '' HUP; exec logcat -v threadtime -f "$S/logcat.txt" -r 8192 -n 8 ) > /dev/null 2>&1 &
LPID=$!
( trap '' HUP; sleep "$MAX_SECONDS"; kill "$LPID" ) > /dev/null 2>&1 &
TPID=$!
echo "$N $LPID $TPID" > "$CURRENT"
log -t HkgCapture "capture $N started"
sleep 2
sync
if alive "$LPID"; then
    toast "hkg-climate capture $N started. Use the climate controls now, then choose Run USB NEMESIS.sh again to stop."
else
    echo "log did not start" >> "$S/session.txt"
    toast "hkg-climate capture $N: the log did not start. Choose Run USB NEMESIS.sh again to save what there is."
fi
exit 0
