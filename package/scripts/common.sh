#!/system/bin/sh
# hkg.climate.page - shared helpers for the install and uninstall hooks. The package swaps
# the split-screen panel app (/system/app/RightScreenHome.apk + .odex) for one rebuilt on
# this head unit from its own stock files (patch/), keeps the stock pair in BACKUP_DIR, and
# uninstall writes it back. Sourced by pre.sh, post.sh and preun.sh after they set PKG (the
# package folder, from the script's own path: the hooks run from different folders).
# Runs under the head unit's mksh/toolbox: no printf, tail, head, seq, sed, [[ ]].
#
# Adapted from HMG Leafia Plus (https://github.com/VysseCodes/HMG-Leafia-Plus) at commit 08f4436.
# This program is free software: you can redistribute it and/or modify it under the terms of
# the GNU General Public License as published by the Free Software Foundation, either version 3
# of the License, or (at your option) any later version. It is distributed WITHOUT ANY
# WARRANTY; see the GNU General Public License (LICENSE) for details.
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
export LD_LIBRARY_PATH=/vendor/lib:/system/lib

ID=hkg.climate.page
LOG=/data/nemesis/hkg.climate.page.log
BUILD_DIR=/data/nemesis/build/hkg.climate.page
BACKUP_DIR=/data/nemesis/backup/hkg.climate.page
HOLD_FILE=/data/nemesis/hkg.climate.page.stopped
APP_DIR=/system/app
LIVE_APK=/system/app/RightScreenHome.apk
LIVE_ODEX=/system/app/RightScreenHome.odex
NEW_APK=/data/nemesis/build/hkg.climate.page/RightScreenHome.apk
DALVIK_ENTRY=/data/dalvik-cache/system@app@RightScreenHome.apk@classes.dex
FILES="RightScreenHome.apk RightScreenHome.odex"
PANEL=com.mobis.splitscreen.right
APP_PKG=hkgclimate.app
NEMESIS=/system/xbin/nemesis
DEXOPT=/system/bin/dexopt
REBOOT=/system/bin/reboot
LPATCH=$PKG/bin/lpatch
chmod 755 "$LPATCH" 2>/dev/null

log() { echo "$1"; mkdir -p /data/nemesis 2>/dev/null; echo "$1" >> "$LOG" 2>/dev/null; true; }

toast() {
    am broadcast -a com.hkmc.intent.action.TOAST \
        -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true > /dev/null 2>&1 || true
}

# toolbox `mount -o remount` is refused on these units even as root; only NEMESIS remounts
sys_rw() { "$NEMESIS" remount rw > /dev/null 2>&1; }
sys_ro() { "$NEMESIS" remount ro > /dev/null 2>&1; }

chown_system() { chown 0.0 "$1" 2>/dev/null || chown 0:0 "$1" 2>/dev/null || true; }

# lp_md5 <file>: md5 by lpatch (streams; toolbox md5 is not trusted with a 160 MB file)
lp_md5() {
    local _l
    _l=$("$LPATCH" md5 "$1" 2>/dev/null) || return 1
    set -- $_l
    echo "$1"
}

# stock_md5 <name>: the stock file's md5 from patch/stock ("<name> <md5>" lines)
stock_md5() {
    local _n _m
    while read -r _n _m; do
        [ "$_n" = "$1" ] && { echo "$_m"; return 0; }
    done < "$PKG/patch/stock"
    return 1
}

# stock_src <name>: the stock copy of a panel file: the backup when there is one (the live
# file may be ours), otherwise the live file
stock_src() {
    if [ -s "$BACKUP_DIR/$1" ]; then echo "$BACKUP_DIR/$1"; else echo "$APP_DIR/$1"; fi
}

# check_stock: the stock copies must be exactly the files patch/ was made from
check_stock() {
    local _f _src _want _got
    for _f in $FILES; do
        _src=$(stock_src "$_f")
        _want=$(stock_md5 "$_f") || { log "FAIL: no checksum for $_f in patch/stock"; return 1; }
        if [ ! -s "$_src" ]; then log "FAIL: $_src missing"; return 1; fi
        _got=$(lp_md5 "$_src") || { log "FAIL: cannot read $_src"; return 1; }
        if [ "$_got" != "$_want" ]; then
            log "FAIL: $_src checksum $_got, want $_want (not the stock 260507 file)"
            return 1
        fi
    done
    log "stock files ok"
    return 0
}

# live_apk_known: with a backup kept from an earlier run the live APK may be ours rather than
# stock; it must be one of the two (needs read_manifest), anything else is not ours to replace
live_apk_known() {
    local _got
    [ -s "$BACKUP_DIR/RightScreenHome.apk" ] || return 0
    _got=$(lp_md5 "$LIVE_APK")
    if [ "$_got" != "$(stock_md5 RightScreenHome.apk)" ] && [ "$_got" != "$WANT_MD5" ]; then
        log "FAIL: $LIVE_APK checksum ${_got:-unreadable} is neither the stock APK nor ours"
        return 1
    fi
    return 0
}

# read_manifest: PATCH_NAME, WANT_MD5 and WANT_SIZE (bytes) of the rebuilt APK, from patch/manifest
read_manifest() {
    [ -s "$PKG/patch/manifest" ] || { log "FAIL: patch/manifest missing"; return 1; }
    read -r PATCH_NAME WANT_MD5 WANT_SIZE < "$PKG/patch/manifest"
    case "$WANT_SIZE" in ''|*[!0-9]*) log "FAIL: bad patch/manifest"; return 1 ;; esac
    [ -n "$WANT_MD5" ] && [ -s "$PKG/patch/$PATCH_NAME" ] || { log "FAIL: bad patch/manifest"; return 1; }
    return 0
}

# size_kb <size>: a toolbox df size (265.7M, 14.6G, 152.0K) in KB; integers only (mksh)
size_kb() {
    local _v=$1 _u _i _t
    _u=${_v#"${_v%?}"}
    _v=${_v%?}
    case "$_v" in
        *.*) _i=${_v%%.*}; _t=${_v#*.}; _t=${_t%"${_t#?}"} ;;
        *) _i=$_v; _t=0 ;;
    esac
    case "$_i" in ''|*[!0-9]*) return 1 ;; esac
    case "$_t" in [0-9]) ;; *) return 1 ;; esac
    while :; do case "$_i" in 0?*) _i=${_i#0} ;; *) break ;; esac; done
    case "$_u" in
        K) echo $(( (_i * 10 + _t) / 10 )) ;;
        M) echo $(( (_i * 10 + _t) * 1024 / 10 )) ;;
        G) echo $(( (_i * 10 + _t) * 1048576 / 10 )) ;;
        *) return 1 ;;
    esac
}

# free_kb <mount point>: its Free column from toolbox df ("Filesystem Size Used Free Blksize"),
# the third word after the mount point (no here-doc: that needs a writable TMPDIR)
free_kb() {
    local _m=$1 _out _w _n=0
    _out=$(df "$_m" 2>/dev/null)
    set -f
    set -- $_out
    set +f
    for _w; do
        case $_n in
            1) size_kb "$_w"; return ;;
            2|3) _n=$((_n - 1)) ;;
            *) [ "$_w" = "$_m" ] && _n=3 ;;
        esac
    done
    return 1
}

# check_space: /data takes the rebuilt APK (and the stock backup, unless one is kept from an
# earlier run: APK + odex, within 1 MB of the new APK's size) plus 32 MB; /system takes the new
# APK beside the stock one before the rename, plus 16 MB
check_space() {
    local _apk _need_d _need_s _d _s
    _apk=$(( (WANT_SIZE + 1023) / 1024 ))
    _need_d=$(( _apk + 32768 ))
    if [ ! -s "$BACKUP_DIR/RightScreenHome.apk" ] || [ ! -s "$BACKUP_DIR/RightScreenHome.odex" ]; then
        _need_d=$(( _need_d + _apk ))
    fi
    _need_s=$(( _apk + 16384 ))
    _d=$(free_kb /data) || { log "FAIL: cannot read free space on /data"; return 1; }
    _s=$(free_kb /system) || { log "FAIL: cannot read free space on /system"; return 1; }
    log "free space: /data $_d KB (need $_need_d), /system $_s KB (need $_need_s)"
    if [ "$_d" -lt "$_need_d" ]; then log "FAIL: not enough free space on /data"; return 1; fi
    if [ "$_s" -lt "$_need_s" ]; then log "FAIL: not enough free space on /system"; return 1; fi
    return 0
}

# check_remount: postinstall needs /system writable; find out before anything is copied
check_remount() {
    if ! sys_rw; then log "FAIL: cannot remount /system rw"; return 1; fi
    sys_ro || log "WARN: cannot remount /system ro"
    return 0
}

clean_build() {
    case "$BUILD_DIR" in
        /data/nemesis/build/hkg.climate.page) rm -rf "$BUILD_DIR"; rmdir /data/nemesis/build 2>/dev/null ;;
    esac
    true
}

# build_apk: rebuild the panel's code from the stock odex (lpatch delta checks the source is
# the exact original), add it to the stock APK, and require the manifest's md5
build_apk() {
    local _dex=$BUILD_DIR/classes.dex
    mkdir -p "$BUILD_DIR" || { log "FAIL: cannot create $BUILD_DIR"; return 1; }
    if ! "$LPATCH" delta "$PKG/patch/$PATCH_NAME" "$(stock_src RightScreenHome.odex)" "$_dex" >> "$LOG" 2>&1; then
        log "FAIL: lpatch delta (the stock odex is not the file this patch needs)"
        return 1
    fi
    if ! "$LPATCH" zipadd "$(stock_src RightScreenHome.apk)" "$_dex" "$NEW_APK" >> "$LOG" 2>&1; then
        rm -f "$_dex"
        log "FAIL: lpatch zipadd could not assemble RightScreenHome.apk"
        return 1
    fi
    rm -f "$_dex"
    if [ "$(lp_md5 "$NEW_APK")" != "$WANT_MD5" ]; then
        rm -f "$NEW_APK"
        log "FAIL: the rebuilt RightScreenHome.apk does not match patch/manifest"
        return 1
    fi
    log "built $NEW_APK ($WANT_MD5)"
    return 0
}

# backup_stock: copy the stock APK and odex to BACKUP_DIR (an existing backup is kept);
# each copy is verified, then renamed into place, so a backup file is always whole
backup_stock() {
    local _f _b
    mkdir -p "$BACKUP_DIR" || { log "FAIL: cannot create $BACKUP_DIR"; return 1; }
    for _f in $FILES; do
        _b=$BACKUP_DIR/$_f
        if [ -s "$_b" ]; then log "backup of $_f exists - keeping it"; continue; fi
        rm -f "$_b.tmp" 2>/dev/null
        if cp "$APP_DIR/$_f" "$_b.tmp" && cmp "$APP_DIR/$_f" "$_b.tmp" > /dev/null 2>&1 \
            && chmod 644 "$_b.tmp" && mv "$_b.tmp" "$_b"; then
            log "backed up $APP_DIR/$_f"
        else
            rm -f "$_b.tmp" 2>/dev/null
            log "FAIL: could not back up $APP_DIR/$_f"
            return 1
        fi
    done
    return 0
}

# put_file <src> <dst>: copy beside the target, check, then rename over it (a new inode: a
# running app keeps its old mapping)
put_file() {
    local _tmp=$2.hkgtmp
    rm -f "$_tmp" 2>/dev/null
    if ! cp "$1" "$_tmp" || ! cmp "$1" "$_tmp" > /dev/null 2>&1; then
        rm -f "$_tmp" 2>/dev/null
        return 1
    fi
    chmod 644 "$_tmp"
    chown_system "$_tmp"
    mv "$_tmp" "$2" || { rm -f "$_tmp" 2>/dev/null; return 1; }
    restorecon "$2" > /dev/null 2>&1
    cmp "$1" "$2" > /dev/null 2>&1
}

# boot_classpath: BOOTCLASSPATH as init exports it (dexopt needs it; hooks may not inherit it)
boot_classpath() {
    local _a _b _c
    while read -r _a _b _c; do
        [ "$_a" = export ] && [ "$_b" = BOOTCLASSPATH ] && [ -n "$_c" ] && { echo "$_c"; return 0; }
    done < /init.environ.rc
    return 1
}

# write_odex: optimise the new APK into the odex beside it
write_odex() {
    local _bcp
    _bcp=$(boot_classpath 2> /dev/null) || _bcp=$BOOTCLASSPATH
    [ -n "$_bcp" ] || { log "no BOOTCLASSPATH for dexopt"; return 1; }
    export BOOTCLASSPATH="$_bcp"
    log "dexopt boot classpath: $BOOTCLASSPATH"
    [ -x "$DEXOPT" ] || { log "dexopt missing"; return 1; }
    "$DEXOPT" --preopt "$LIVE_APK" "$LIVE_ODEX" m=y,u=n >> "$LOG" 2>&1 || return 1
    [ -s "$LIVE_ODEX" ] || return 1
    chmod 644 "$LIVE_ODEX"
    chown_system "$LIVE_ODEX"
    restorecon "$LIVE_ODEX" > /dev/null 2>&1
    return 0
}

wipe_dalvik() {
    rm -f /data/dalvik-cache/*RightScreenHome* /data/dalvik-cache/arm/*RightScreenHome* \
        /cache/dalvik-cache/*RightScreenHome* 2>/dev/null
    true
}

# prefill_dalvik: the new APK carries classes.dex, so at boot Dalvik looks in its cache, not
# at the odex, and would re-optimise it before the head unit finishes starting. The cache
# entry is the same file as the odex, so put it there now; if Dalvik rejects it, it rebuilds
# it at boot as before. A failure only logs.
prefill_dalvik() {
    local _tmp=$DALVIK_ENTRY.tmp
    [ -s "$LIVE_ODEX" ] && [ -d /data/dalvik-cache ] || return 0
    rm -f "$_tmp" 2>/dev/null
    if ! cp "$LIVE_ODEX" "$_tmp" 2>/dev/null || ! cmp "$LIVE_ODEX" "$_tmp" > /dev/null 2>&1; then
        rm -f "$_tmp" 2>/dev/null
        log "dalvik cache not filled; boot will optimize"
        return 0
    fi
    chmod 644 "$_tmp"
    chown 1000:1000 "$_tmp" 2>/dev/null || chown 1000.1000 "$_tmp" 2>/dev/null
    mv "$_tmp" "$DALVIK_ENTRY" 2>/dev/null || { rm -f "$_tmp" 2>/dev/null; log "dalvik cache not filled; boot will optimize"; return 0; }
    restorecon "$DALVIK_ENTRY" > /dev/null 2>&1
    log "dalvik cache filled"
    return 0
}

# restore_stock: write the backed-up stock files back wherever the live ones differ; a file
# with no backup must already be stock. Returns 1 unless /system then holds the stock pair.
restore_stock() {
    local _f _b _want _need="" _bad=0
    for _f in $FILES; do
        _b=$BACKUP_DIR/$_f
        _want=$(stock_md5 "$_f")
        if [ -s "$_b" ]; then
            if [ "$(lp_md5 "$_b")" != "$_want" ]; then
                log "FAIL: backup $_b is not the stock file - not restoring it"
                _bad=1
            elif ! cmp "$_b" "$APP_DIR/$_f" > /dev/null 2>&1; then
                _need="$_need $_f"
            fi
        elif [ "$(lp_md5 "$APP_DIR/$_f")" != "$_want" ]; then
            log "FAIL: no backup of $_f and $APP_DIR/$_f is not the stock file"
            _bad=1
        fi
    done
    if [ -n "$_need" ] || [ -e "$LIVE_APK.hkgtmp" ] || [ -e "$LIVE_ODEX.hkgtmp" ]; then
        if sys_rw; then
            rm -f "$LIVE_APK.hkgtmp" "$LIVE_ODEX.hkgtmp" 2>/dev/null
            for _f in $_need; do
                if put_file "$BACKUP_DIR/$_f" "$APP_DIR/$_f"; then
                    log "restored $APP_DIR/$_f"
                else
                    log "FAIL: could not restore $APP_DIR/$_f"
                    _bad=1
                fi
            done
            sync
            sys_ro || log "WARN: cannot remount /system ro"
        else
            log "FAIL: cannot remount /system rw to restore the stock files"
            _bad=1
        fi
    fi
    [ $_bad = 0 ] && log "/system has the stock RightScreenHome files"
    return $_bad
}

remove_backup_dir() {
    case "$BACKUP_DIR" in
        /data/nemesis/backup/hkg.climate.page) rm -rf "$BACKUP_DIR" && log "removed $BACKUP_DIR" ;;
        *) log "FAIL: refusing to remove $BACKUP_DIR"; return 1 ;;
    esac
}

# hold / release: with Android up, the panel is stopped (pm disable, which also keeps it from
# restarting) while its files change. It is recorded in HOLD_FILE first, and release, which
# every way out of a hook runs, starts it again; a run that dies leaves the record, and the
# next run starts it.
android_up() { [ "$(getprop sys.boot_completed 2>/dev/null)" = 1 ]; }
hold() {
    android_up || return 0
    pm path "$PANEL" > /dev/null 2>&1 || { log "WARN: $PANEL not found - not stopping it"; return 0; }
    echo "$PANEL" > "$HOLD_FILE" 2>/dev/null || { log "WARN: cannot record the stopped panel - not stopping it"; return 0; }
    if pm disable "$PANEL" > /dev/null 2>&1; then log "stopped $PANEL"; else log "WARN: could not stop $PANEL"; fi
    return 0
}
release() {
    [ -f "$HOLD_FILE" ] || return 0
    if ! android_up; then log "WARN: $PANEL stays stopped until a hook runs with Android up"; return 0; fi
    if pm enable "$PANEL" > /dev/null 2>&1; then
        rm -f "$HOLD_FILE"
        log "started $PANEL again"
    else
        log "WARN: could not start $PANEL again"
        toast "HKG Climate Page: the split-screen panel could not be started again. Run the install again to fix it."
    fi
    return 0
}

# NEMESIS 1.15 force-updates an installed package from a USB drive while Android is down, and
# we reboot after installing: rename our install descriptor so the next boot leaves it alone
retire_usb_descriptor() {
    local _d
    for _d in /storage/usb0 /storage/usb1 /storage/usb2 /mnt/media_rw/usb0 /mnt/media_rw/usb1 /mnt/media_rw/usb2; do
        [ -f "$_d/NEMESIS.data" ] || continue
        grep -q '^type=install' "$_d/NEMESIS.data" 2>/dev/null || continue
        grep -q 'hkg\.climate\.page\.zip' "$_d/NEMESIS.data" 2>/dev/null || continue
        if mv "$_d/NEMESIS.data" "$_d/NEMESIS.data.installed" 2>/dev/null; then
            log "renamed $_d/NEMESIS.data to NEMESIS.data.installed so the next boot does not reinstall"
        else
            log "WARN: could not rename $_d/NEMESIS.data - remove the USB drive before the head unit restarts"
        fi
    done
    true
}

# drop_page4 <list>: PAGES = the comma list without page 4, each entry trimmed of spaces and
# empty entries dropped (as PageList.withoutPage); DROPPED=1 when a 4 was taken out
drop_page4() {
    local _t _ifs=$IFS
    PAGES=""
    DROPPED=0
    set -f
    IFS=,
    for _t in $1; do
        while :; do
            case "$_t" in
                " "*) _t=${_t# } ;;
                *" ") _t=${_t% } ;;
                *) break ;;
            esac
        done
        [ -n "$_t" ] || continue
        if [ "$_t" = 4 ]; then DROPPED=1; continue; fi
        PAGES=${PAGES:+$PAGES,}$_t
    done
    IFS=$_ifs
    set +f
}

# clean_page_lists: take our page (4) out of the split screen's three page lists, writing only
# a list that had it, then tell the panel to reload them
clean_page_lists() {
    local _k _v
    for _k in ss_setting ss_setting_dr1 ss_setting_dr2; do
        _v=$(settings get system "$_k" 2>/dev/null) || { log "WARN: cannot read $_k"; continue; }
        case "$_v" in ''|null) continue ;; esac
        drop_page4 "$_v"
        [ "$DROPPED" = 1 ] || continue
        if settings put system "$_k" "$PAGES" > /dev/null 2>&1; then
            log "$_k: $_v -> $PAGES"
        else
            log "WARN: could not write $_k"
        fi
    done
    # sent while the panel is still held, on purpose: the panel re-reads the lists when it
    # starts again, and the broadcast covers a panel that was not stopped
    am broadcast -a com.android.settings.splitscreen_settings.changed > /dev/null 2>&1 \
        || log "WARN: page-list broadcast failed"
}
