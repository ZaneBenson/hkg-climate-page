#!/system/bin/sh
# hkg-climate voice kit - READ ONLY. Changes nothing on the car and sends no vehicle commands.
#
# Copies the stock voice software, so we can find which spoken phrases reach which climate
# commands. Writes HKGVOICE/ on this USB drive:
#   summary.txt     firmware id, target-car check, free space, and one line per copied item
#                   (ok / FAIL / missing)
#   stock/          the files in FILES, and the native libraries in /system/lib whose names
#                   look like the voice engine
#   lib-listing.txt, vendor-lib-listing.txt   all of /system/lib and /system/vendor/lib
#   vr2-listing.txt  every file under /storage/vr2 (the voice data partition), with sizes
#   vr2/            a copy of the US-English recognizer data, /storage/vr2/vr2/LPTE/DATA/ENU
#
# HKGVOICE/ holds Hyundai files: keep it private, never post it. Nothing is taken from /data
# (the voice app's own folder there holds the owner's voice data).
#
# Host tests set KIT_SYSROOT (a fake root) and KIT_TEST_PATH (stub commands).
export PATH=${KIT_TEST_PATH:+$KIT_TEST_PATH:}/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
export LD_LIBRARY_PATH=/vendor/lib:/system/lib

SYS=${KIT_SYSROOT:-}
# The drive is the folder this script is in (NEMESIS starts it from the drive root on insert;
# NEMESIS Manager's "Run USB NEMESIS.sh" calls it by full path).
case "$0" in */*) KIT=${0%/*} ;; *) KIT=. ;; esac
KIT=$(cd "$KIT" && pwd) || exit 1
OUT=$KIT/HKGVOICE
STOCK=$OUT/stock
SUM=$OUT/summary.txt
ENU=/storage/vr2/vr2/LPTE/DATA/ENU

rm -r "$OUT" 2>/dev/null
mkdir -p "$STOCK" || exit 1
: > "$SUM" || exit 1
say() { echo "$1" >> "$SUM"; }

# copy_one <file> <dest dir>: one line in the summary
copy_one() {
    if [ ! -f "$SYS$1" ]; then
        say "missing $1"
    elif mkdir -p "$2" && cp "$SYS$1" "$2/" 2>/dev/null; then
        say "ok   $1"
    else
        say "FAIL $1"
    fi
}

# copy_tree <dir> <dest dir>: every file below dir (no cp -r on the unit)
copy_tree() {
    mkdir -p "$2" || { say "FAIL $1"; return; }
    for _e in "$SYS$1"/*; do
        [ -e "$_e" ] || continue
        _n=${_e##*/}
        if [ -d "$_e" ]; then
            copy_tree "$1/$_n" "$2/$_n"
        else
            copy_one "$1/$_n" "$2"
        fi
    done
}

say "voicekit hkg-climate 1"
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
say "===== files"

while read -r _f; do
    case "$_f" in ''|'#'*) continue ;; esac
    _d=${_f%/*}
    copy_one "$_f" "$STOCK/${_d#/system/}"
done < "$KIT/FILES"

# native libraries that look like the voice engine; the listings show everything else
ls -l "$SYS/system/lib" > "$OUT/lib-listing.txt" 2>&1
ls -l "$SYS/system/vendor/lib" > "$OUT/vendor-lib-listing.txt" 2>&1
for _l in "$SYS/system/lib"/*; do
    [ -f "$_l" ] || continue
    _n=${_l##*/}
    case "$_n" in
        *[Vv][Oo][Cc][Oo][Nn]*|*[Ll][Pp][Tt][Ee]*|*[Vv][Oo][Ii][Cc][Ee]*|*[Vv][Rr]*|*[Aa][Ss][Rr]*|*[Nn][Uu][Aa][Nn][Cc][Ee]*|*[Ss][Pp][Ee][Ee][Cc][Hh]*|*[Rr][Ee][Cc][Oo][Gg]*|*[Gg][Rr][Aa][Mm][Mm][Aa][Rr]*)
            copy_one "/system/lib/$_n" "$STOCK/lib" ;;
    esac
done

ls -lR "$SYS/storage/vr2" > "$OUT/vr2-listing.txt" 2>&1
if [ -d "$SYS$ENU" ]; then
    copy_tree "$ENU" "$OUT/vr2"
else
    say "missing $ENU"
fi

say ""
say "done"
sync
am broadcast -a com.hkmc.intent.action.TOAST \
    -e com.hkmc.intent.extra.TOAST_MESSAGE "hkg-climate voice kit finished, unplug the drive" \
    --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true > /dev/null 2>&1
exit 0
