#!/system/bin/sh
# hkg.climate.page preinstall: refuse (exit 1, before NEMESIS copies anything) unless this is
# a 2023 US Ioniq 5 on 260507 with the stock panel files, there is room, /system can be
# remounted, and the panel app rebuilds from its stock files to the expected md5. The
# rebuilt APK waits in BUILD_DIR for postinstall. Changes nothing on /system.
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

refuse() { toast "$1"; exit 1; }
# a refused install removes its build and starts a panel an earlier run left stopped
trap '[ $? = 0 ] || { clean_build; release; }' EXIT

log "preinstall $ID"
[ -s "$PKG/BUILD" ] && log "build $(cat "$PKG/BUILD")"
_p=$(getprop ro.build.product 2>/dev/null)
case "$_p" in
    NEEV23.USA.*.260507) log "product $_p" ;;
    *)
        log "FAIL: product $_p is not a 2023 US Ioniq 5 on firmware 260507"
        refuse "HKG Climate Page needs a 2023 US Ioniq 5 on firmware 260507. Update the head unit first." ;;
esac
read_manifest || refuse "HKG Climate Page was not installed: the package is damaged."
check_stock || refuse "HKG Climate Page was not installed: this head unit's split-screen app is not the stock file. Nothing was changed."
clean_build
check_space || refuse "HKG Climate Page was not installed: not enough free space. Nothing was changed."
check_remount || refuse "HKG Climate Page was not installed: could not unlock the system partition. Restart the head unit and try again."
build_apk || refuse "HKG Climate Page was not installed: the split-screen app could not be rebuilt. Nothing was changed."
log "preinstall ok"
exit 0
