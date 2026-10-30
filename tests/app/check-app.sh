#!/bin/sh
# Host check of the app build: builds the signed APK and asserts its manifest facts, its v1
# signature, a few calls its code must make, that no stock stub class is packaged, and that the
# only send-path references are the approved seat calls, inside UsmSeatCalls.
# usage: sh tests/app/check-app.sh   (prints one line per check; exit 1 if any check fails)
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
eval "$("$ROOT/tools/toolchain.sh" env)"
cd "$ROOT"
FAILS=0
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

check() {
    _name=$1; shift
    if "$@" > /dev/null 2>&1; then echo "ok   $_name"; else echo "FAIL $_name"; FAILS=$((FAILS + 1)); fi
}

APK=build/app/hkgclimate.apk
rm -f "$APK"
check "build-app.sh builds the apk" sh tools/build-app.sh
check "apk exists" [ -s "$APK" ]

if [ -s "$APK" ]; then
    "$BUILD_TOOLS/aapt" dump badging "$APK" > "$TMP/badging" 2>&1
    unzip -p "$APK" classes.dex > "$TMP/classes.dex" 2> /dev/null
    "$BUILD_TOOLS/dexdump" "$TMP/classes.dex" > "$TMP/dex.txt" 2>&1
    # -d lists the code, which is where references to other classes show up
    "$BUILD_TOOLS/dexdump" -d "$TMP/classes.dex" > "$TMP/dexcode.txt" 2>&1
    unzip -Z1 "$APK" > "$TMP/entries" 2>&1
fi
: >> "$TMP/badging"; : >> "$TMP/dex.txt"; : >> "$TMP/dexcode.txt"; : >> "$TMP/entries"

# has_ref <dexdump -d output> <class>: the code refers to the class, e.g. Lcom/hkmc/usm/
has_ref() { grep -q "$2" "$1"; }
# method_has_ref <dexdump -d output> <class.method:> <ref>: that method's code refers to <ref>
method_has_ref() {
    awk -v m="$2" 'index($0, "] " m) { on = 1; next } on && /\|\[/ { on = 0 } on' "$1" | grep -q "$3"
}
method_lacks_ref() { ! method_has_ref "$@"; }

# usm_refs_ok <dexdump -d output>: every Lcom/hkmc/usm/ reference is inside
# hkgclimate.app.UsmSeatCalls (in the code of its methods, or its own declarations such as the
# cached manager field), names no stock class but USMManager, and every USMManager member it names
# is one of the three approved ones. Prints the offending lines; status 1 when there are any.
usm_refs_ok() {
    awk -v cls_ok="'Lhkgclimate/app/UsmSeatCalls;'" -v meth_ok="hkgclimate.app.UsmSeatCalls." '
        /Class descriptor/ { incls = index($0, cls_ok) > 0; meth = ""; next }
        /\|\[[0-9a-f]+\] / { m = $0; sub(/.*\|\[[0-9a-f]+\] /, "", m); meth = m }
        !index($0, "Lcom/hkmc/usm/") { next }
        {
            bad = !incls
            # a code line: it must be in one of UsmSeatCalls own methods
            if (index($0, "|") && index(meth, meth_ok) != 1) bad = 1
            rest = $0
            while ((i = index(rest, "Lcom/hkmc/usm/")) > 0) {
                rest = substr(rest, i)
                j = index(rest, ";")
                if (j == 0) { bad = 1; break }
                if (substr(rest, 1, j) != "Lcom/hkmc/usm/USMManager;") bad = 1
                rest = substr(rest, j + 1)
                if (substr(rest, 1, 1) == ".") {
                    member = rest; sub(/[ ,].*/, "", member)
                    if (member != ".<init>:(Landroid/content/Context;)V" \
                        && member != ".setFrontSeatHeatDrvStep:(I)V" \
                        && member != ".setFrontSeatHeatAstStep:(I)V") bad = 1
                }
            }
            if (bad) { print; n++ }
        }
        END { exit n > 0 }' "$1"
}

# self-tests of has_ref and usm_refs_ok on throwaway dexes. The scratch sources (a com.hkmc.usm
# USMManager with one extra member, and a com.hkmc.usm.Seat) live only in $TMP; they are never in
# the repo or the APK.
ST=$TMP/st
mkdir -p "$ST/s/com/hkmc/usm" "$ST/so"
cat > "$ST/s/com/hkmc/usm/Seat.java" <<'JAVA'
package com.hkmc.usm;
public class Seat { public void send(int v) { } }
JAVA
cat > "$ST/s/com/hkmc/usm/USMManager.java" <<'JAVA'
package com.hkmc.usm;
public class USMManager {
    public USMManager(android.content.Context c) { }
    public void setFrontSeatHeatDrvStep(int v) { }
    public void setFrontSeatHeatAstStep(int v) { }
    public void setOther(int v) { }
}
JAVA
javac --release 8 -cp "$ANDROID_JAR" -d "$ST/so" "$ST/s/com/hkmc/usm/Seat.java" \
    "$ST/s/com/hkmc/usm/USMManager.java" > /dev/null 2>&1
# scratch_dex <name> <file base name> <java source>: compiles one source against the scratch
# stubs, dexes it and writes its dexdump -d listing to $ST/<name>/code.txt (empty on any failure)
scratch_dex() {
    _d=$ST/$1; mkdir -p "$_d/src" "$_d/o"
    _f=$_d/src/$2.java
    echo "$3" > "$_f"
    # shellcheck disable=SC2046 # one path per word; none contain spaces
    if javac -source 8 -target 8 -Xlint:-options -bootclasspath "$ANDROID_JAR" -cp "$ST/so" \
            -d "$_d/o" "$_f" > /dev/null 2>&1 \
        && "$BUILD_TOOLS/d8" --min-api 19 --lib "$ANDROID_JAR" --classpath "$ST/so" --output "$_d" \
            $(find "$_d/o" -name '*.class') > /dev/null 2>&1 \
        && "$BUILD_TOOLS/dexdump" -d "$_d/classes.dex" > "$_d/code.txt" 2>&1; then
        :
    else
        : > "$_d/code.txt"
    fi
}
scratch_dex caller Caller 'public class Caller { void go() { new com.hkmc.usm.Seat().send(3); } }'
scratch_dex other Other 'public class Other { void go(android.content.Context c) { new com.hkmc.usm.USMManager(c).setFrontSeatHeatDrvStep(3); } }'
scratch_dex good UsmSeatCalls 'package hkgclimate.app; class UsmSeatCalls { com.hkmc.usm.USMManager m; void go(android.content.Context c) { if (m == null) m = new com.hkmc.usm.USMManager(c); m.setFrontSeatHeatDrvStep(3); m.setFrontSeatHeatAstStep(3); } }'
scratch_dex extra UsmSeatCalls 'package hkgclimate.app; class UsmSeatCalls { void go(android.content.Context c) { new com.hkmc.usm.USMManager(c).setOther(3); } }'
# usm_flags / usm_passes <listing> <ref>: the listing has <ref> (so the scratch build worked) and
# usm_refs_ok fails / passes on it
usm_flags() { grep -q "$2" "$1" && ! usm_refs_ok "$1"; }
usm_passes() { grep -q "$2" "$1" && usm_refs_ok "$1"; }
check "self-test: the reference check finds a scratch com.hkmc.usm call" has_ref "$ST/caller/code.txt" 'Lcom/hkmc/usm/'
check "self-test: usm check flags a com.hkmc.usm call from another class" \
    usm_flags "$ST/caller/code.txt" 'Lcom/hkmc/usm/Seat;\.send'
check "self-test: usm check flags an approved USMManager call from another class" \
    usm_flags "$ST/other/code.txt" 'USMManager;\.setFrontSeatHeatDrvStep'
check "self-test: usm check passes UsmSeatCalls making the approved calls" \
    usm_passes "$ST/good/code.txt" 'USMManager;\.setFrontSeatHeatAstStep'
check "self-test: usm check flags UsmSeatCalls calling another USMManager member" \
    usm_flags "$ST/extra/code.txt" 'USMManager;\.setOther'

check "package is hkgclimate.app" grep -q "^package: name='hkgclimate.app'" "$TMP/badging"
check "versionName is 0.2.0-test2" grep -q "versionName='0.2.0-test2'" "$TMP/badging"
check "versionCode is 2" grep -q "versionCode='2'" "$TMP/badging"
check "sdkVersion is 19" grep -q "^sdkVersion:'19'" "$TMP/badging"
check "automotive library is not required" grep -q "^uses-library-not-required:'automotive'" "$TMP/badging"
check "WRITE_SETTINGS is requested" grep -q "uses-permission: name='android.permission.WRITE_SETTINGS'" "$TMP/badging"
check "launcher activity is SettingsActivity" grep -q "launchable-activity: name='hkgclimate.app.SettingsActivity'" "$TMP/badging"
check "apk has classes.dex" grep -qx 'classes.dex' "$TMP/entries"
check "apk signature verifies (v1, min sdk 19)" "$BUILD_TOOLS/apksigner" verify --min-sdk-version 19 "$APK"
check "apk is zip aligned" "$BUILD_TOOLS/zipalign" -c 4 "$APK"
check "dex has our ClimateService" grep -q "Lhkgclimate/app/ClimateService;" "$TMP/dex.txt"
check "dex has PageLink" grep -q "Lhkgclimate/link/PageLink;" "$TMP/dex.txt"
check "dex has core classes" grep -q "Lhkgclimate/core/ClimateCore;" "$TMP/dex.txt"
check "dex lists classes" grep -q "Class descriptor" "$TMP/dex.txt"
check "dex has no class under Lcom/hkmc/" sh -c "! grep -q \"Class descriptor *: *'Lcom/hkmc/\" $TMP/dex.txt"
check "dex code listing contains our code" grep -q 'hkgclimate.app.SettingsActivity.onCreate' "$TMP/dexcode.txt"
# the stock services we read: referenced from code, the service name as a real field read (a
# constant would have been inlined by javac), and the DATC manager never constructed directly
check "code calls DATCManager.addTemperatureListener" has_ref "$TMP/dexcode.txt" 'Lcom/hkmc/datc/DATCManager;\.addTemperatureListener'
check "code calls BCMManager.getDrvSeatHeatState" has_ref "$TMP/dexcode.txt" 'Lcom/hkmc/bcm/BCMManager;\.getDrvSeatHeatState'
check "code reads the field VehicleInfoManager.DATC_SERVICE (sget-object)" has_ref "$TMP/dexcode.txt" 'sget-object.*Lcom/hkmc/VehicleInfoManager;\.DATC_SERVICE'
check "code never constructs DATCManager" sh -c "! grep -q 'Lcom/hkmc/datc/DATCManager;\.<init>' $TMP/dexcode.txt"
# the service starts itself so it outlives the page's binds (START_STICKY only holds for a started service)
check "ClimateService calls startService" has_ref "$TMP/dexcode.txt" 'Lhkgclimate/app/ClimateService;\.startService:'
# the Android docs: unbind even when bindService returned false
check "SettingsActivity.onStart can unbind (a failed bind)" \
    method_has_ref "$TMP/dexcode.txt" 'hkgclimate.app.SettingsActivity.onStart:' '\.unbindService:'
# self-test of method_has_ref: it sees onStart's own bind, and stops at the next method (onStop,
# listed later, calls disconnect; onStart does not)
check "self-test: method_has_ref sees onStart's bindService" \
    method_has_ref "$TMP/dexcode.txt" 'hkgclimate.app.SettingsActivity.onStart:' '\.bindService:'
check "self-test: method_has_ref stops at the end of onStart" \
    method_lacks_ref "$TMP/dexcode.txt" 'hkgclimate.app.SettingsActivity.onStart:' 'SettingsActivity;\.disconnect:'
check "dex has PageListKeeper" grep -q "Lhkgclimate/app/PageListKeeper;" "$TMP/dex.txt"
check "code registers a content observer on the settings" has_ref "$TMP/dexcode.txt" 'Landroid/content/ContentResolver;\.registerContentObserver'
check "code writes the page lists with Settings.System.putString" has_ref "$TMP/dexcode.txt" 'Landroid/provider/Settings\$System;\.putString'
check "dex has the page list changed action" grep -q "com.android.settings.splitscreen_settings.changed" "$TMP/dex.txt"
# the seat command: only UsmSeatCalls touches the stock seat service, with the three approved members
check "every Lcom/hkmc/usm/ reference is an approved USMManager call in UsmSeatCalls" usm_refs_ok "$TMP/dexcode.txt"
check "UsmSeatCalls.driver calls setFrontSeatHeatDrvStep" \
    method_has_ref "$TMP/dexcode.txt" 'hkgclimate.app.UsmSeatCalls.driver:' 'Lcom/hkmc/usm/USMManager;\.setFrontSeatHeatDrvStep:(I)V'
check "UsmSeatCalls.passenger calls setFrontSeatHeatAstStep" \
    method_has_ref "$TMP/dexcode.txt" 'hkgclimate.app.UsmSeatCalls.passenger:' 'Lcom/hkmc/usm/USMManager;\.setFrontSeatHeatAstStep:(I)V'
check "UsmSeatCalls constructs USMManager" has_ref "$TMP/dexcode.txt" 'Lcom/hkmc/usm/USMManager;\.<init>:(Landroid/content/Context;)V'
# lazy: the manager is made on the first send, never when the service builds the adapter
check "dex has UsmSeatCalls.<init>" grep -q '\] hkgclimate.app.UsmSeatCalls.<init>:' "$TMP/dexcode.txt"
check "UsmSeatCalls.<init> does not construct USMManager" \
    method_lacks_ref "$TMP/dexcode.txt" 'hkgclimate.app.UsmSeatCalls.<init>:' 'Lcom/hkmc/usm/USMManager;\.<init>'
check "ClimateService builds the CheckedSeatSender" \
    method_has_ref "$TMP/dexcode.txt" 'hkgclimate.app.ClimateService.onCreate:' 'Lhkgclimate/core/CheckedSeatSender;\.<init>'
check "ClimateService builds the TestModeCommander" \
    method_has_ref "$TMP/dexcode.txt" 'hkgclimate.app.ClimateService.onCreate:' 'Lhkgclimate/app/TestModeCommander;\.<init>'
check "ClimateService builds the UsmSeatCalls" \
    method_has_ref "$TMP/dexcode.txt" 'hkgclimate.app.ClimateService.onCreate:' 'Lhkgclimate/app/UsmSeatCalls;\.<init>'

if [ "$FAILS" -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "all checks passed"
