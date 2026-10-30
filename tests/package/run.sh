#!/bin/sh
# Mock head unit test for the NEMESIS package: runs its install and uninstall hooks against
# a sandbox holding the owner's stock RightScreenHome files, entirely on this computer, with
# stubbed Android commands. Nothing touches a car.
# usage: sh tests/package/run.sh [package zip]   (default: dist/hkg-climate-page.zip)
#
# Cases: install; uninstall after it; wrong firmware; changed stock APK; /system remount
# failing during postinstall; not enough free space on /data; a postinstall step failing
# after the new APK is in place; postinstall killed mid-swap; a kept backup with a live APK
# that is neither stock nor ours; Android down. Also checks the zip's layout
# and that tools/build-package.sh refuses commands the head unit's shell lacks.
#
# Needs private/HKGCLIMATE/stock (app/RightScreenHome.apk, .odex, init.environ.rc); without
# it prints "SKIP: no stock files" and exits 2. QEMU_ARM runs the package's ARM lpatch and
# MOCK_SH runs the hooks (both from tools/toolchain.sh env; set MOCK_SH=mksh to override).
#
# The sandbox: hooks are rewritten with sed so /system/, /data/, /cache/, /storage/,
# /mnt/media_rw/ and /init.environ.rc point into it, nemesis/dexopt/reboot point at stubs,
# and PATH starts with the stubs. Every stub appends its call to <case>/calls.log.
# Stubs: getprop (product from MOCK_PRODUCT), pm (pm_disabled_<pkg> marker while disabled),
# am, settings (one file per key), dexopt (writes the APK's classes.dex as the odex), nemesis
# (remount rw fails while <case>/remount-rw.fail exists: the test creates it after preinstall,
# so only postinstall's remount fails), df (free space from MOCK_DF_SYSTEM/MOCK_DF_DATA),
# md5, chown, restorecon, reboot, and rm (cannot remove the live odex while
# <case>/rm-odex.fail exists); dexopt kills the hook while <case>/dexopt.kill exists;
# MOCK_ANDROID_DOWN=1 makes sys.boot_completed 0 and am, pm and settings fail; bin/lpatch becomes a wrapper running the real one in qemu.
#
# Adapted from HMG Leafia Plus (https://github.com/VysseCodes/HMG-Leafia-Plus) at commit 08f4436.
# This program is free software: you can redistribute it and/or modify it under the terms of
# the GNU General Public License as published by the Free Software Foundation, either version 3
# of the License, or (at your option) any later version. It is distributed WITHOUT ANY
# WARRANTY; see the GNU General Public License (LICENSE) for details.
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
ZIP=${1:-$ROOT/dist/hkg-climate-page.zip}
STOCK=$ROOT/private/HKGCLIMATE/stock
for _f in app/RightScreenHome.apk app/RightScreenHome.odex init.environ.rc; do
    [ -s "$STOCK/$_f" ] || { echo "SKIP: no stock files"; exit 2; }
done
_sh=$MOCK_SH
eval "$("$ROOT/tools/toolchain.sh" env)"
[ -n "$_sh" ] && MOCK_SH=$_sh
[ -n "$QEMU_ARM" ] || { echo "FAIL: no qemu-arm (see tools/toolchain.sh check)"; exit 1; }
[ -s "$ZIP" ] || { echo "FAIL: no package $ZIP (run tools/build-package.sh)"; exit 1; }
case "$MOCK_SH" in
    mksh|*/mksh) ;;
    *) echo "WARNING: hooks run under '$MOCK_SH', not mksh (the head unit's shell): commands mksh lacks are not caught" ;;
esac

ID=hkg.climate.page
PANEL=com.mobis.splitscreen.right
GOOD=NEEV23.USA.0000.V015.012.260507
FAILS=0
CASE_FAILS=0

fails() { ! "$@"; }
check() {
    _name=$1; shift
    if "$@" > /dev/null 2>&1; then echo "ok   $_name"
    else echo "FAIL $_name"; FAILS=$((FAILS + 1)); CASE_FAILS=$((CASE_FAILS + 1)); fi
}

T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT

# ---- the package zip
mkdir -p "$T/outer" "$T/inner"
unzip -q "$ZIP" -d "$T/outer" && unzip -q "$T/outer/packages/$ID.zip" -d "$T/inner"
outer_data() {
    [ "$(cat "$T/outer/NEMESIS.data")" = "$(printf '[nemesis]\nversion=1.0\ntype=install\npath=packages/%s.zip' "$ID")" ]
}
check "outer NEMESIS.data is the install descriptor" outer_data
check "outer zip holds only the descriptor and package" \
    test "$(cd "$T/outer" && find . -type f | LC_ALL=C sort | tr '\n' ' ')" = "./NEMESIS.data ./packages/$ID.zip "
check "inner NEMESIS.data is package/NEMESIS.data" cmp "$T/inner/NEMESIS.data" "$ROOT/package/NEMESIS.data"
for _f in scripts/common.sh scripts/pre.sh scripts/post.sh scripts/preun.sh patch/stock patch/manifest patch/RightScreenHome.lpd; do
    check "inner $_f is the repo's" cmp "$T/inner/$_f" "$ROOT/package/$_f"
done
check "inner bin/lpatch is the committed ARM build" cmp "$T/inner/bin/lpatch" "$ROOT/tools/lpatch/lpatch"
check "inner app/hkgclimate.apk is the built app" cmp "$T/inner/app/hkgclimate.apk" "$ROOT/build/app/hkgclimate.apk"
check "inner BUILD is '<describe> <UTC time>'" \
    grep -qE '^[^ ]+ [0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}Z$' "$T/inner/BUILD"
read -r _lpd WANT_MD5 _size < "$T/inner/patch/manifest"

# ---- build-package.sh refuses commands the head unit's shell lacks (copy of the tree in $T/lint)
L=$T/lint
mkdir -p "$L/tools/lpatch" "$L/build/app"
cp "$ROOT/tools/build-package.sh" "$L/tools/"; cp "$ROOT/tools/lpatch/lpatch" "$L/tools/lpatch/"
cp "$ROOT/build/app/hkgclimate.apk" "$L/build/app/"; cp -R "$ROOT/package" "$L/package"
cp "$L/package/scripts/common.sh" "$T/common.sh.orig"
lint_with() {   # lint_with <line>: build with <line> appended to common.sh
    cp "$T/common.sh.orig" "$L/package/scripts/common.sh"
    echo "$1" >> "$L/package/scripts/common.sh"
    sh "$L/tools/build-package.sh" "$L/out.zip"
}
lint_refuses() { ! lint_with "$1"; }
check "lint: allows 'header', 'head unit' in text, [[:space:]]" \
    lint_with '# header: the head unit
_header=1; echo "check the head unit"; grep -q "[[:space:]]" /dev/null'
check "lint: refuses \$(head ...)"   lint_refuses '_x=$(head -n 1 /dev/null)'
check "lint: refuses | tail"         lint_refuses 'echo a | tail -n 1'
check "lint: refuses printf"         lint_refuses '    printf x'
check "lint: refuses \$(seq ...)"    lint_refuses 'for _i in $(seq 3); do :; done'
check "lint: refuses [[ ]]"          lint_refuses '[[ -n x ]] && echo x'
check "lint: refuses after a case arm" lint_refuses '    *) head -n 1 f ;;'
check "lint: refuses after eval"     lint_refuses 'eval printf x'
check "lint: refuses after time"     lint_refuses 'time head f'

# ---- page-list editing (as PageList.withoutPage: comma split, entries trimmed, empties dropped)
drop4() {
    $MOCK_SH -c "$(sed -n '/^drop_page4()/,/^}/p' "$ROOT/package/scripts/common.sh")
drop_page4 \"\$1\"; echo \"\$DROPPED:\$PAGES\"" sh "$1"
}
check "drop_page4: '1 2,4' -> '1 2'"      test "$(drop4 '1 2,4')" = "1:1 2"
check "drop_page4: trims, drops empties and every 4" test "$(drop4 ' 4, 0 ,,4 ,21, 3 ')" = "1:0,21,3"
check "drop_page4: no 4 -> nothing dropped" test "$(drop4 '0,21,3')" = "0:0,21,3"
check "drop_page4: '*' is not a glob"     test "$(drop4 '*,4')" = "1:*"

# ---- the sandbox
mkdir -p "$T/stock" "$T/stub"
cp "$STOCK/app/RightScreenHome.apk" "$STOCK/app/RightScreenHome.odex" "$T/stock/"
cp "$T/inner/bin/lpatch" "$T/lpatch.arm"

stub() { cat > "$T/stub/$1"; chmod 755 "$T/stub/$1"; }
stub getprop <<'E'
#!/bin/sh
case "$1" in
    ro.build.product) echo "$MOCK_PRODUCT" ;;
    sys.boot_completed) if [ "$MOCK_ANDROID_DOWN" = 1 ]; then echo 0; else echo 1; fi ;;
    *) echo "" ;;
esac
E
stub pm <<'E'
#!/bin/sh
echo "pm $*" >> "$MOCK/calls.log"
[ "$MOCK_ANDROID_DOWN" = 1 ] && { echo "Error: Could not access the Package Manager" >&2; exit 1; }
case "$1" in
    path) [ "$2" = com.mobis.splitscreen.right ] || exit 1; echo "package:/system/app/RightScreenHome.apk" ;;
    disable) : > "$MOCK/pm_disabled_$2"; echo "Package $2 new state: disabled" ;;
    enable) rm -f "$MOCK/pm_disabled_$2"; echo "Package $2 new state: enabled" ;;
esac
exit 0
E
stub am <<'E'
#!/bin/sh
echo "am $*" >> "$MOCK/calls.log"
[ "$MOCK_ANDROID_DOWN" = 1 ] && { echo "Error: Android is down" >&2; exit 1; }
echo "am: ok"
E
stub settings <<'E'
#!/bin/sh
echo "settings $*" >> "$MOCK/calls.log"
[ "$MOCK_ANDROID_DOWN" = 1 ] && { echo "Error: Android is down" >&2; exit 1; }
case "$1" in
    get) cat "$MOCK/settings/$3" 2>/dev/null || echo null ;;
    put) echo "$4" > "$MOCK/settings/$3" ;;
esac
E
stub dexopt <<'E'
#!/bin/sh
echo "dexopt $* BOOTCLASSPATH=$BOOTCLASSPATH" >> "$MOCK/calls.log"
# dexopt.kill: the hook is killed here, after the new APK is in place and the odex removed
[ -e "$MOCK/dexopt.kill" ] && { kill -TERM "$PPID"; exit 1; }
[ "$1" = --preopt ] || exit 1
unzip -p "$2" classes.dex > "$3"
E
stub nemesis <<'E'
#!/bin/sh
echo "nemesis $*" >> "$MOCK/calls.log"
if [ "$1 $2" = "remount rw" ] && [ -e "$MOCK/remount-rw.fail" ]; then
    echo "nemesis: remount failed" >&2; exit 1
fi
exit 0
E
stub df <<'E'
#!/bin/sh
line() { echo "$1                  1.9G     1.7G   $2   4096"; }
echo "Filesystem               Size     Used     Free   Blksize"
[ $# = 0 ] && set -- /system /data
for _m; do
    case "$_m" in
        /system) line /system "${MOCK_DF_SYSTEM:-265.7M}" ;;
        /data) line /data "${MOCK_DF_DATA:-814.2M}" ;;
    esac
done
E
stub rm <<'E'
#!/bin/sh
# rm-odex.fail: the live odex cannot be removed (a failure after the new APK is in place)
if [ -e "$MOCK/rm-odex.fail" ]; then
    for _a; do
        case "$_a" in */system/app/RightScreenHome.odex) echo "rm: Read-only file system" >&2; exit 1 ;; esac
    done
fi
exec /usr/bin/rm "$@"
E
stub md5 <<'E'
#!/bin/sh
md5sum "$@"
E
stub chown <<'E'
#!/bin/sh
echo "chown $*" >> "$MOCK/calls.log"
E
stub restorecon <<'E'
#!/bin/sh
exit 0
E
stub reboot <<'E'
#!/bin/sh
echo "reboot $*" >> "$MOCK/calls.log"
: > "$MOCK/rebooted"
E

# new_case <name> [apk]: a fresh head unit in $T/<name> (sets X R STAGE APPS); the stock files
# are hard links to one copy (every write to /system goes to a new file and a rename), or
# <apk> when given. Unpacks the package into NEMESIS's staging dir, as NEMESIS does.
new_case() {
    X=$T/$1; R=$X/root
    STAGE=$R/data/local/tmp/nemesis/stage/$ID; APPS=$R/data/nemesis/apps/$ID
    CASE_FAILS=0
    echo "--- $1"
    mkdir -p "$R/system/app" "$R/data/dalvik-cache" "$R/data/local/tmp" "$R/cache" "$R/storage/usb0" "$X/settings" "$STAGE"
    ln "$T/stock/RightScreenHome.odex" "$R/system/app/"
    if [ -n "$2" ]; then cp "$2" "$R/system/app/RightScreenHome.apk"; else ln "$T/stock/RightScreenHome.apk" "$R/system/app/"; fi
    cp "$STOCK/init.environ.rc" "$R/init.environ.rc"
    cp "$T/outer/NEMESIS.data" "$R/storage/usb0/NEMESIS.data"
    cp -R "$T/inner/." "$STAGE/"
    printf '#!/bin/sh\nexec %s %s "$@"\n' "$QEMU_ARM" "$T/lpatch.arm" > "$STAGE/bin/lpatch"
    for _s in "$STAGE"/scripts/*.sh; do
        sed -i -e "1s|^#!.*|#!/bin/sh|" \
            -e "s|/system/xbin/nemesis|$T/stub/nemesis|g" -e "s|/system/bin/dexopt|$T/stub/dexopt|g" \
            -e "s|/system/bin/reboot|$T/stub/reboot|g" \
            -e "s|/system/|$R/system/|g" -e "s|/data/|$R/data/|g" -e "s|/cache/|$R/cache/|g" \
            -e "s|/storage/|$R/storage/|g" -e "s|/mnt/media_rw/|$R/mnt/media_rw/|g" \
            -e "s|/init\.environ\.rc|$R/init.environ.rc|g" \
            -e "s|^export PATH=.*|export PATH=$T/stub:/usr/bin:/bin|" -e "s|^export LD_LIBRARY_PATH=.*|:|" "$_s"
    done
    sys_md5 > "$X/before.md5"
    export MOCK=$X MOCK_PRODUCT=$GOOD
    unset MOCK_DF_SYSTEM MOCK_DF_DATA MOCK_ANDROID_DOWN
}
sys_md5() { (cd "$R" && find system -type f | LC_ALL=C sort | while read -r _f; do md5sum "$_f"; done); }
# the hooks, run as NEMESIS runs them: preinstall from the staging dir, then the package is
# copied to apps/<id> and postinstall and preuninstall run from there (here by full path)
run_pre() { (cd "$STAGE" && $MOCK_SH scripts/pre.sh > "$X/pre.out" 2>&1); }
run_post() { mkdir -p "$APPS" && cp -R "$STAGE/." "$APPS/"
    (cd "$APPS" && $MOCK_SH "$APPS/scripts/post.sh" > "$X/post.out" 2>&1); }
run_preun() { (cd "$APPS" && $MOCK_SH "$APPS/scripts/preun.sh" > "$X/preun.out" 2>&1); }

sys_same() { sys_md5 | cmp -s - "$1"; }
panel_enabled() { [ ! -e "$X/pm_disabled_$PANEL" ]; }
called() { grep -qF -- "$1" "$X/calls.log"; }
not_called() { ! grep -qF -- "$1" "$X/calls.log" 2>/dev/null; }
no_build_dir() { [ ! -e "$R/data/nemesis/build/$ID" ]; }
end_case() {
    [ $CASE_FAILS = 0 ] && return
    for _o in "$X"/*.out; do [ -f "$_o" ] && { echo "    == ${_o##*/}"; sed 's/^/    /' "$_o"; }; done
}
LIVE=system/app/RightScreenHome
BAK=data/nemesis/backup/$ID
DALVIK=data/dalvik-cache/system@app@RightScreenHome.apk@classes.dex

# ---- 1 install (and a /data size given in G, as toolbox df prints large sizes)
new_case install
export MOCK_DF_DATA=14.6G
check "pre.sh exits 0"                    run_pre
check "post.sh exits 0"                   run_post
check "live APK md5 is the manifest's"    test "$(md5sum < "$R/$LIVE.apk" | cut -d' ' -f1)" = "$WANT_MD5"
check "odex exists"                       test -s "$R/$LIVE.odex"
check "odex is the new APK's dex"         sh -c "unzip -p '$R/$LIVE.apk' classes.dex | cmp -s - '$R/$LIVE.odex'"
check "dalvik prefill equals the odex"    cmp "$R/$DALVIK" "$R/$LIVE.odex"
check "backup holds the stock APK"        cmp "$R/$BAK/RightScreenHome.apk" "$T/stock/RightScreenHome.apk"
check "backup holds the stock odex"       cmp "$R/$BAK/RightScreenHome.odex" "$T/stock/RightScreenHome.odex"
check "panel was stopped for the swap"    called "pm disable $PANEL"
check "panel enabled afterwards"          panel_enabled
check "dexopt used init.environ.rc's BOOTCLASSPATH" \
    called "dexopt --preopt $R/$LIVE.apk $R/$LIVE.odex m=y,u=n BOOTCLASSPATH=/system/framework/core.jar:"
check "ClimateService started"            called "am startservice -n hkgclimate.app/.ClimateService"
check "reboot requested"                  test -e "$X/rebooted"
check "USB descriptor renamed"            sh -c "test -e '$R/storage/usb0/NEMESIS.data.installed' && test ! -e '$R/storage/usb0/NEMESIS.data'"
check "no temp file left in /system/app"  sh -c "! ls '$R/system/app' | grep -q tmp"
check "build dir removed"                 no_build_dir
check "log records the BUILD line"        grep -q "^build $(cat "$T/inner/BUILD")$" "$R/data/nemesis/$ID.log"
end_case

# ---- 2 uninstall, on the install above
CASE_FAILS=0
echo "--- uninstall"
echo "0,21,3,2,9,10" > "$X/settings/ss_setting"
echo "0,4,21,3,2,6,9,10" > "$X/settings/ss_setting_dr1"
: > "$X/calls.log"
run_preun
check "/system byte-identical to before install" sys_same "$X/before.md5"
check "ss_setting_dr1 without page 4"     test "$(cat "$X/settings/ss_setting_dr1")" = "0,21,3,2,6,9,10"
check "ss_setting left alone"             sh -c "test \"\$(cat '$X/settings/ss_setting')\" = 0,21,3,2,9,10 && ! grep -q 'settings put system ss_setting ' '$X/calls.log'"
check "ss_setting_dr2 (null) not written" test ! -e "$X/settings/ss_setting_dr2"
check "app stopped before the list edit"  sh -c "test \"\$(grep -n -m1 'am force-stop hkgclimate.app' '$X/calls.log' | cut -d: -f1)\" -lt \"\$(grep -n -m1 'settings put' '$X/calls.log' | cut -d: -f1)\""
check "page-list broadcast sent"          called "am broadcast -a com.android.settings.splitscreen_settings.changed"
check "no dalvik entry left"              sh -c "! ls '$R/data/dalvik-cache' | grep -q RightScreenHome"
check "no backup dir left"                test ! -e "$R/$BAK"
check "panel was stopped for the restore" called "pm disable $PANEL"
check "panel enabled afterwards"          panel_enabled
check "log says reboot to finish"         grep -q "reboot the head unit to finish" "$X/preun.out"
end_case

# ---- 3 wrong firmware
new_case firmware
export MOCK_PRODUCT=NEEV23.USA.0000.V015.012.260101
check "pre.sh exits 1"                    fails run_pre
check "toast sent"                        called "HKG Climate Page needs a 2023 US Ioniq 5 on firmware 260507. Update the head unit first."
check "/system untouched"                 sys_same "$X/before.md5"
check "panel enabled"                     panel_enabled
check "no build dir"                      no_build_dir
end_case

# ---- 4 changed stock APK (one byte flipped)
cp "$T/stock/RightScreenHome.apk" "$T/changed.apk"
printf 'X' | dd of="$T/changed.apk" bs=1 seek=1000000 conv=notrunc 2>/dev/null
new_case changed "$T/changed.apk"
check "pre.sh exits 1"                    fails run_pre
check "/system untouched"                 sys_same "$X/before.md5"
check "panel enabled"                     panel_enabled
check "no build dir"                      no_build_dir
end_case

# ---- 5 remount fails during post (preinstall's remount check passes)
new_case remount
check "pre.sh exits 0"                    run_pre
: > "$X/remount-rw.fail"
check "post.sh exits 1"                   fails run_post
check "/system byte-identical to stock"   sys_same "$X/before.md5"
check "panel was stopped"                 called "pm disable $PANEL"
check "panel enabled"                     panel_enabled
check "failure toast sent"                called "HKG Climate Page: install failed"
check "no reboot"                         test ! -e "$X/rebooted"
check "ClimateService not started"        not_called "am startservice"
check "build dir removed"                 no_build_dir
end_case

# ---- 6 not enough free space on /data (owner's /system free space)
new_case space
export MOCK_DF_DATA=300.0M
check "pre.sh exits 1"                    fails run_pre
check "log says why"                      grep -q "FAIL: not enough free space on /data" "$X/pre.out"
check "/system untouched"                 sys_same "$X/before.md5"
check "panel enabled"                     panel_enabled
check "no build dir"                      no_build_dir
end_case

# ---- 7 a step fails after the new APK is in place (the live odex cannot be removed)
new_case undo
check "pre.sh exits 0"                    run_pre
: > "$X/rm-odex.fail"
check "post.sh exits 1"                   fails run_post
check "the new APK was written first"     grep -q "^wrote .*RightScreenHome.apk$" "$X/post.out"
check "the stock APK was written back"    grep -q "^restored .*RightScreenHome.apk$" "$X/post.out"
check "/system byte-identical to stock"   sys_same "$X/before.md5"
check "panel enabled"                     panel_enabled
check "undo toast says restored + uninstall" \
    called "the split-screen panel was restored to stock. Uninstall HKG Climate Page in NEMESIS."
check "no reboot"                         test ! -e "$X/rebooted"
check "no temp file left in /system/app"  sh -c "! ls '$R/system/app' | grep -q tmp"
check "no dalvik entry left"              sh -c "! ls '$R/data/dalvik-cache' | grep -q RightScreenHome"
end_case

# ---- 8 postinstall killed during dexopt (APK replaced, odex removed): the EXIT trap restores
new_case killed
check "pre.sh exits 0"                    run_pre
: > "$X/dexopt.kill"
check "post.sh exits nonzero"             fails run_post
check "the trap put the stock files back" grep -q "postinstall ended during the swap" "$X/post.out"
check "/system byte-identical to stock"   sys_same "$X/before.md5"
check "panel enabled"                     panel_enabled
check "no reboot"                         test ! -e "$X/rebooted"
end_case

# ---- 9 a kept backup, and a live APK that is neither stock nor ours
new_case foreign
mkdir -p "$R/$BAK"
ln "$T/stock/RightScreenHome.apk" "$T/stock/RightScreenHome.odex" "$R/$BAK/"
check "pre.sh exits 0 (backup kept, live stock)" run_pre
rm -f "$R/$LIVE.apk"; cp "$T/changed.apk" "$R/$LIVE.apk"
sys_md5 > "$X/before-post.md5"
check "post.sh exits 1"                   fails run_post
check "log says why"                      grep -q "neither the stock APK nor ours" "$X/post.out"
check "/system untouched"                 sys_same "$X/before-post.md5"
check "panel enabled"                     panel_enabled
check "failure toast sent"                called "HKG Climate Page: install failed; nothing was changed."
check "pre.sh now refuses too"            fails run_pre
check "backup still the stock pair"       sh -c "cmp -s '$R/$BAK/RightScreenHome.apk' '$T/stock/RightScreenHome.apk' && cmp -s '$R/$BAK/RightScreenHome.odex' '$T/stock/RightScreenHome.odex'"
end_case
rm -f "$T/changed.apk"

# ---- 10 Android down (NEMESIS installing before Android starts): no hold; am, pm and
# settings fail, which is not fatal
new_case android-down
export MOCK_ANDROID_DOWN=1
check "pre.sh exits 0"                    run_pre
check "post.sh exits 0"                   run_post
check "live APK md5 is the manifest's"    test "$(md5sum < "$R/$LIVE.apk" | cut -d' ' -f1)" = "$WANT_MD5"
check "panel not stopped"                 not_called "pm disable"
check "service start tried"               called "am startservice -n hkgclimate.app/.ClimateService"
check "reboot requested"                  test -e "$X/rebooted"
check "preun.sh exits 0"                  run_preun
check "page lists tried"                  called "settings get system ss_setting"
check "/system byte-identical to before install" sys_same "$X/before.md5"
check "no backup dir left"                test ! -e "$R/$BAK"
check "panel enabled"                     panel_enabled
end_case

if [ $FAILS -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "all checks passed"
