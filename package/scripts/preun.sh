#!/system/bin/sh
# hkg.climate.page preuninstall: with the panel stopped, write the stock panel files back,
# drop their Dalvik cache entries, take our page (4) out of the split screen's page lists and
# remove the backup. NEMESIS ignores this hook's exit code, so it does every step it can; the
# backup is kept if /system could not be put back to stock. A restart finishes the uninstall.
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

# every way out of this run starts the panel again
trap release EXIT

log "preuninstall $ID"
hold
_ok=1
restore_stock || _ok=0
wipe_dalvik
clean_build
# our app keeps page 4 in the lists while it runs; stop it before taking the page out
am force-stop "$APP_PKG" > /dev/null 2>&1 || log "WARN: could not stop $APP_PKG"
clean_page_lists
if [ $_ok = 1 ]; then
    remove_backup_dir
else
    log "FAIL: /system is not back to stock - keeping $BACKUP_DIR"
fi
sync
release
log "reboot the head unit to finish"
[ $_ok = 1 ]
