#!/system/bin/sh
# hkg-climate kit - READ ONLY. Changes nothing on the car.
#
# Collects what the hkg-climate-page research needs and writes HKGCLIMATE/ on this
# USB drive:
#   summary.txt         firmware id, target-car check, free space, memory, the
#                       split-screen page settings (ss_setting*)
#   collected.txt       one line per file in FILES, plus the install log: ok / FAIL /
#                       missing
#   probe.txt           four seat-state reads (stock services' cached values), as root
#                       and as an ordinary app uid, to see if a normal app can read them
#   logcat-full.txt     the Android log (about the last minute): can hold phone and
#                       contact names, Bluetooth addresses and other ids the car logs
#   hkg.climate.page.log  the HKG Climate Page install and uninstall log (only our
#                       own install messages), if the package was ever installed
#   stock/              the stock files listed in FILES, /init.environ.rc and the
#                       /system/app and /system/priv-app listings
#
# HKGCLIMATE/ holds Hyundai files and logs: keep it private, never post it.
# No vehicle commands are sent. No files are copied from /data (except our own install
# log above), the vehicle security partition, accounts, paired phone data or Wi-Fi
# settings; but the Android log is copied as-is (see logcat-full.txt above).
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
# automotive.odex of NEEV23.USA 260507, whose getter codes the probe was checked against
PROBE_ODEX_MD5=85f94dc6b9f7b331b8edcb0ec2183261

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
# The codes were checked against one automotive.odex only; on another build the same
# code could be a setter, which would send a vehicle command. Probe only that build.
_m=$(md5 "$SYS/system/framework/automotive.odex" 2>/dev/null); _m=${_m%% *}
case "$_p" in NEEV23.USA.*.260507) _t=1 ;; *) _t=0 ;; esac
if [ $_t = 1 ] && [ "$_m" = "$PROBE_ODEX_MD5" ]; then
    probe com.hkmc.micom.bcm 21 getDrvSeatHeatState
    probe com.hkmc.micom.bcm 23 getAstSeatHeatState
    probe com.hkmc.micom.usm 583 getFrontSeatHeat_DrvState
    probe com.hkmc.micom.usm 584 getFrontSeatHeat_AstState
else
    echo "probe skipped (not the checked build: product $_p, automotive.odex md5 ${_m:-unreadable})" >> "$PROBE"
fi

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

# our own install log: the one file taken from /data
_f=/data/nemesis/hkg.climate.page.log
if [ ! -f "$SYS$_f" ]; then
    echo "missing $_f" >> "$COLLECT"
elif cp "$SYS$_f" "$OUT/" 2>/dev/null; then
    echo "ok   $_f" >> "$COLLECT"
else
    echo "FAIL $_f" >> "$COLLECT"
fi

say ""
say "done"
sync
am broadcast -a com.hkmc.intent.action.TOAST \
    -e com.hkmc.intent.extra.TOAST_MESSAGE "hkg-climate kit finished, unplug the drive" \
    --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true > /dev/null 2>&1
exit 0
