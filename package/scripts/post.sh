#!/system/bin/sh
# hkg.climate.page postinstall: with the panel stopped, back up the stock panel files, put
# the APK preinstall rebuilt in place, write its odex and Dalvik cache entry, start our
# service, then restart the head unit. If anything fails once /system may have changed, the
# stock files are written back and the panel is started again (NEMESIS does not undo a failed
# postinstall itself).
#
# Adapted from HMG Leafia Plus (https://github.com/VysseCodes/HMG-Leafia-Plus) at commit 08f4436.
# This program is free software: you can redistribute it and/or modify it under the terms of
# the GNU General Public License as published by the Free Software Foundation, either version 3
# of the License, or (at your option) any later version. It is distributed WITHOUT ANY
# WARRANTY; see the GNU General Public License (LICENSE) for details.
_d=${0%/*}
[ "$_d" = "$0" ] && _d=.
PKG=$(cd "$_d/.." && pwd) || exit 1
. "$PKG/scripts/common.sh" || exit 1

# SWAPPING=1 from the remount rw until the swap is done: any way out of the run while it is
# set (a failed step, a signal, a shell error) puts the stock files back before the panel is
# started again. put_back clears it first, so it runs once.
SWAPPING=0
on_exit() {
    [ "$SWAPPING" = 1 ] && put_back "postinstall ended during the swap"
    release
}
trap on_exit EXIT
trap 'exit 1' HUP INT TERM

# fail <why>: nothing on /system has changed yet
fail() {
    log "FAIL: $1 - nothing on /system was changed"
    clean_build
    release
    toast "HKG Climate Page: install failed; nothing was changed. Uninstall HKG Climate Page in NEMESIS."
    exit 1
}
# put_back <why>: /system may have changed: write the stock files back
put_back() {
    # a second signal must not cut the restore short
    trap '' HUP INT TERM
    SWAPPING=0
    log "FAIL: $1 - putting the stock files back"
    if restore_stock; then
        wipe_dalvik
        sync
        clean_build
        release
        log "the split-screen panel is back to stock"
        toast "HKG Climate Page: install failed; the split-screen panel was restored to stock. Uninstall HKG Climate Page in NEMESIS."
    else
        wipe_dalvik
        sync
        clean_build
        release
        log "FAIL: could not put every stock file back - keeping $BACKUP_DIR; do not restart yet"
        toast "HKG Climate Page: install failed and could not be fully undone. Do not restart the head unit yet - check the NEMESIS log."
    fi
}
undo() { put_back "$1"; exit 1; }

log "postinstall $ID"
read_manifest || fail "bad package"
hold
[ -s "$NEW_APK" ] && [ "$(lp_md5 "$NEW_APK")" = "$WANT_MD5" ] || fail "the rebuilt APK is missing or changed"
check_stock || fail "stock files changed since preinstall"
live_apk_known || fail "the live APK is neither the stock file nor ours"
backup_stock || fail "backup"

SWAPPING=1
sys_rw || undo "cannot remount /system rw"
put_file "$NEW_APK" "$LIVE_APK" || undo "could not write $LIVE_APK"
log "wrote $LIVE_APK"
rm -f "$LIVE_ODEX" 2>/dev/null
[ -e "$LIVE_ODEX" ] && undo "could not remove $LIVE_ODEX"
if write_odex; then
    log "odex written"
else
    rm -f "$LIVE_ODEX" 2>/dev/null
    log "dexopt failed; boot will optimize"
fi
wipe_dalvik
prefill_dalvik
sys_ro || log "WARN: cannot remount /system ro (it is read-only again after the restart)"
sync
SWAPPING=0
clean_build

am startservice -n hkgclimate.app/.ClimateService > /dev/null 2>&1 || log "WARN: could not start the climate service"
release
retire_usb_descriptor
log "installed - restarting the head unit"
toast "HKG Climate Page installed. Restarting now: remove the USB drive while it restarts."
exec "$REBOOT"
log "FAIL: reboot returned"
exit 1
