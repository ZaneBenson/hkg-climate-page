#!/bin/sh
# Host check of the page build: builds build/page/classes.dex and asserts that it holds only our
# classes, never sends anything to the car, touches the stock panel classes only through the
# names we declared, and that ClimatePageFactory.create has the signature the smali hook expects.
# usage: sh tests/panel/check-page.sh   (prints one line per check; exit 1 if any check fails)
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

DEX=build/page/classes.dex
rm -f "$DEX"
check "build-page.sh builds the dex" sh tools/build-page.sh
check "dex exists" [ -s "$DEX" ]

if [ -s "$DEX" ]; then
    "$BUILD_TOOLS/dexdump" "$DEX" > "$TMP/dex.txt" 2>&1
    # -d lists the code, which is where references to other classes show up
    "$BUILD_TOOLS/dexdump" -d "$DEX" > "$TMP/dexcode.txt" 2>&1
fi
: >> "$TMP/dex.txt"; : >> "$TMP/dexcode.txt"

# the stock base class members our code may name (the stub declares more; we use only these)
ALLOWED="<init> getView f a bh mContext mHandler mView"

# panel_refs <dexdump -d output>: every reference to Lcom/mobis/splitscreen/right/, one per line,
# as the class (e.g. k;) or class;.member
panel_refs() {
    grep -o 'Lcom/mobis/splitscreen/right/[A-Za-z0-9_$]*;\(\.[A-Za-z0-9_<>$]*\)\{0,1\}' "$1" \
        | sed 's|^Lcom/mobis/splitscreen/right/||' | sort -u
}

# bad_panel_refs <dexdump -d output>: the references that are not to k or an allowed member of k
bad_panel_refs() {
    panel_refs "$1" | while read -r r; do
        case "$r" in
            k\;) ;;
            k\;.*)
                m=${r#k;.}
                ok=0
                for a in $ALLOWED; do [ "$m" = "$a" ] && ok=1; done
                [ "$ok" = 1 ] || echo "$r"
                ;;
            *) echo "$r" ;;
        esac
    done
}

has_ref() { grep -q "$2" "$1"; }
no_output() { [ -z "$("$@")" ]; }

# self-test of the reference checks: a throwaway dex whose code calls a scratch com.hkmc.usm class
# and a scratch member of a scratch panel class k, which must both be found; its other panel
# references (k.f, k.mView) must pass. The scratch sources live only in $TMP.
mkdir -p "$TMP/st/s/com/hkmc/usm" "$TMP/st/s/com/mobis/splitscreen/right" "$TMP/st/so" "$TMP/st/o"
cat > "$TMP/st/s/com/hkmc/usm/Seat.java" <<'JAVA'
package com.hkmc.usm;
public class Seat { public void send(int v) { } }
JAVA
cat > "$TMP/st/s/com/mobis/splitscreen/right/k.java" <<'JAVA'
package com.mobis.splitscreen.right;
public abstract class k {
    public volatile android.view.View mView;
    public void f(boolean b) { }
    public void zz() { }
}
JAVA
cat > "$TMP/st/Caller.java" <<'JAVA'
public class Caller extends com.mobis.splitscreen.right.k {
    void go() {
        new com.hkmc.usm.Seat().send(3);
        com.mobis.splitscreen.right.k o = this;   // calls through k are references to k's members
        o.f(true); o.mView = null; o.zz();
    }
}
JAVA
if javac --release 8 -Xlint:-options -cp "$ANDROID_JAR" -d "$TMP/st/so" \
        "$TMP/st/s/com/hkmc/usm/Seat.java" "$TMP/st/s/com/mobis/splitscreen/right/k.java" > /dev/null 2>&1 \
    && javac -source 8 -target 8 -Xlint:-options -bootclasspath "$ANDROID_JAR" -cp "$TMP/st/so" \
        -d "$TMP/st/o" "$TMP/st/Caller.java" > /dev/null 2>&1 \
    && "$BUILD_TOOLS/d8" --min-api 19 --lib "$ANDROID_JAR" --classpath "$TMP/st/so" --output "$TMP/st" \
        "$TMP/st/o/Caller.class" > /dev/null 2>&1 \
    && "$BUILD_TOOLS/dexdump" -d "$TMP/st/classes.dex" > "$TMP/st/code.txt" 2>&1; then
    :
else
    : > "$TMP/st/code.txt"
fi
check "self-test: the reference check finds a scratch com.hkmc.usm call" has_ref "$TMP/st/code.txt" 'Lcom/hkmc/usm/'
bad_panel_refs "$TMP/st/code.txt" > "$TMP/st/bad.txt"
check "self-test: a disallowed panel member is reported" grep -q '^k;\.zz$' "$TMP/st/bad.txt"
check "self-test: allowed panel members are not reported" sh -c "! grep -q 'k;\.f\$\|k;\.mView' $TMP/st/bad.txt"

check "dex lists classes" grep -q "Class descriptor" "$TMP/dex.txt"
check "every defined class is under Lhkgclimate/" sh -c "! grep \"Class descriptor\" $TMP/dex.txt | grep -qv \"'Lhkgclimate/\""
check "dex has ClimatePage" grep -q "Lhkgclimate/page/ClimatePage;" "$TMP/dex.txt"
check "dex has PageView" grep -q "Lhkgclimate/page/PageView;" "$TMP/dex.txt"
check "dex has LinkClient" grep -q "Lhkgclimate/page/LinkClient;" "$TMP/dex.txt"
check "dex has PageLink" grep -q "Lhkgclimate/link/PageLink;" "$TMP/dex.txt"
check "dex code listing contains our code" grep -q 'hkgclimate.page.ClimatePageFactory.create' "$TMP/dexcode.txt"
check "ClimatePage extends the stock base class k" grep -q "Superclass *: *'Lcom/mobis/splitscreen/right/k;'" "$TMP/dex.txt"
check "dex has no reference to Lcom/hkmc/ (code)" sh -c "! grep -q 'Lcom/hkmc/' $TMP/dexcode.txt"
check "dex has no reference to Lcom/hkmc/ (anywhere)" sh -c "! grep -q 'Lcom/hkmc/' $TMP/dex.txt"
check "panel references are only to k and its allowed members" no_output bad_panel_refs "$TMP/dexcode.txt"
check "panel class names in the dex listing are only k" no_output bad_panel_refs "$TMP/dex.txt"
check "the page declares no field named viewId" sh -c "! grep -q 'viewId' $TMP/dex.txt"
check "code reaches k.mContext" has_ref "$TMP/dexcode.txt" '\.mContext'
check "code reads the uiMode of the configuration" has_ref "$TMP/dexcode.txt" 'Landroid/content/res/Configuration;\.uiMode'
check "code binds the service" has_ref "$TMP/dexcode.txt" 'Landroid/content/Context;\.bindService'
check "code sends the tap oneway through transact" has_ref "$TMP/dexcode.txt" 'Landroid/os/IBinder;\.transact'

# ClimatePageFactory.create: the descriptor the smali hook calls
awk '/Class descriptor/ { cls = $0 } /name *: *.create./ { if (cls ~ /ClimatePageFactory/) f = 1 }
     f && /type *:/ { print; exit }' "$TMP/dex.txt" > "$TMP/create.txt"
check "ClimatePageFactory.create has the hook's signature" \
    grep -q "(Landroid/content/Context;Landroid/os/Handler;)Lcom/mobis/splitscreen/right/k;" "$TMP/create.txt"

if [ "$FAILS" -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "all checks passed"
