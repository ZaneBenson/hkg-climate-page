#!/system/bin/sh
# hkg-climate kit - READ ONLY. Changes nothing on the car.
#
# Collects what the hkg-climate-page research needs and writes HKGCLIMATE/ on this
# USB drive:
#   summary.txt         firmware id, target-car check, free space, memory, the
#                       split-screen page settings (ss_setting*)
#   collected.txt       one line per file in FILES: ok / FAIL / missing
#   probe.txt           four seat-state reads (stock services' cached values), as root
#                       and as an ordinary app uid, to see if a normal app can read them
#   logcat-full.txt     the Android log (about the last minute)
#   stock/              the stock files listed in FILES, /init.environ.rc and the
#                       /system/app and /system/priv-app listings
#
# HKGCLIMATE/ holds Hyundai files and logs: keep it private, never post it.
# No vehicle commands are sent. Never collected: accounts, paired phone data, Wi-Fi
# passwords, the vehicle security partition, VIN, serial numbers, /data contents.
#
# Host tests set KIT_SYSROOT (a fake root) and KIT_TEST_PATH (stub commands).
export PATH=${KIT_TEST_PATH:+$KIT_TEST_PATH:}/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
export LD_LIBRARY_PATH=/vendor/lib:/system/lib

SYS=${KIT_SYSROOT:-}
KIT=$(pwd)
OUT=$KIT/HKGCLIMATE
STOCK=$OUT/stock
SUM=$OUT/summary.txt
COLLECT=$OUT/collected.txt
PROBE=$OUT/probe.txt
PROBE_UID=10999

rm -r "$OUT" 2>/dev/null
mkdir -p "$STOCK/app" "$STOCK/priv-app" || exit 1
: > "$SUM" && : > "$COLLECT" && : > "$PROBE" || exit 1
say() { echo "$1" >> "$SUM"; }

# logs first, while they still cover what the owner just did
logcat -d -v time > "$OUT/logcat-full.txt" 2>&1

say "kit hkg-climate 1"
say "date $(date)"
_p=$(getprop ro.build.product)
say "product $_p"
case "$_p" in
    NEEV23.USA.*.260507) say "car target" ;;
    *) say "car other" ;;
esac

say ""
say "===== free space"
df >> "$SUM" 2>&1
say ""
say "===== memory"
_n=0
while read -r _l; do
    [ $_n -ge 4 ] && break
    say "$_l"
    _n=$((_n + 1))
done < "$SYS/proc/meminfo"

say ""
say "===== split-screen pages"
for _k in ss_setting ss_setting_dr1 ss_setting_dr2; do
    _v=$(settings get system $_k 2>/dev/null)
    [ -n "$_v" ] || _v=unavailable
    say "$_k $_v"
done

# seat-state reads: getters that return the stock services' cached values
probe() {
    echo "call $1 $2 ($3) as root" >> "$PROBE"
    service call $1 $2 >> "$PROBE" 2>&1
    echo "call $1 $2 ($3) as uid $PROBE_UID" >> "$PROBE"
    nemesis root $PROBE_UID -c "service call $1 $2" >> "$PROBE" 2>&1
}
probe com.hkmc.micom.bcm 21 getDrvSeatHeatState
probe com.hkmc.micom.bcm 23 getAstSeatHeatState
probe com.hkmc.micom.usm 583 getFrontSeatHeat_DrvState
probe com.hkmc.micom.usm 584 getFrontSeatHeat_AstState

# stock files named in FILES
while read -r _f; do
    case "$_f" in ''|'#'*) continue ;; esac
    _d=${_f%/*}; _d=$STOCK/${_d#/system/}
    if [ ! -f "$SYS$_f" ]; then
        echo "missing $_f" >> "$COLLECT"
    elif mkdir -p "$_d" && cp "$SYS$_f" "$_d/" 2>/dev/null; then
        echo "ok   $_f" >> "$COLLECT"
    else
        echo "FAIL $_f" >> "$COLLECT"
    fi
done < "$KIT/FILES"
ls -l "$SYS/system/app" > "$STOCK/app-listing.txt" 2>&1
ls -l "$SYS/system/priv-app" > "$STOCK/priv-app-listing.txt" 2>&1
cat "$SYS/init.environ.rc" > "$STOCK/init.environ.rc" 2>/dev/null

say ""
say "done"
sync
am broadcast -a com.hkmc.intent.action.TOAST \
    -e com.hkmc.intent.extra.TOAST_MESSAGE "hkg-climate kit finished, unplug the drive" \
    --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true > /dev/null 2>&1
exit 0
