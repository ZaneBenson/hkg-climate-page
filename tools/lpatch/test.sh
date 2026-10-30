#!/bin/sh
# Check that lpatch.c still builds into a working lpatch with the clang you have.
# Copied from HMG Leafia Plus (https://github.com/VysseCodes/HMG-Leafia-Plus) at commit 08f4436; changed for hkg-climate-page.
# Builds a fresh copy in a temp dir (the committed lpatch is never touched), runs
# every command on generated files, and requires the fresh ARM build, the
# committed ARM build and the host build to give identical output and files.
# zipadd is also checked against Leafia's original lpatch (byte for byte, on zips
# small enough for it) and on a generated 200 MB zip (valid result, entries
# unchanged, same md5 from every build and run, host peak RSS under 48 MB).
# usage: sh tools/lpatch/test.sh
# Needs: clang, lld, cc, and QEMU_ARM and PY (python with bsdiff4) from tools/toolchain.sh.
# LEAFIA (default ~/code/HMG-Leafia-Plus): a Leafia clone with commit 08f4436, for the
# original lpatch.c; without it that one comparison is skipped.
set -u
cd "$(dirname "$0")" || exit 1
LP=$PWD; REPO=$(cd ../.. && pwd)
eval "$("$REPO/tools/toolchain.sh" env)" || exit 1
LEAFIA=${LEAFIA:-$HOME/code/HMG-Leafia-Plus}
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
FAIL=0
ok()  { echo "ok    $*"; }
bad() { echo "FAIL  $*"; FAIL=1; }
[ -n "$QEMU_ARM" ] || { echo "FAIL  no qemu-arm found (QEMU_ARM is empty)"; exit 1; }
[ -x "$PY" ] || { echo "FAIL  no toolchain python at $PY (see tools/toolchain.sh)"; exit 1; }

# ---- build a fresh copy ----
mkdir "$W/build" && cp build.sh lpatch.c "$W/build/" || exit 1
if ! sh "$W/build/build.sh" > "$W/build.log" 2>&1; then cat "$W/build.log"; echo "FAIL  build"; exit 1; fi
ok "build ($(clang --version | head -1))"

BINS="fresh committed host"
exe() {
    case $1 in
        fresh) echo "$QEMU_ARM $W/build/lpatch" ;;
        committed) echo "$QEMU_ARM $LP/lpatch" ;;
        host) echo "$W/build/lpatch-host" ;;
    esac
}
for b in $BINS; do mkdir "$W/$b"; done

# run <case> <lpatch args...>: run every build in its own dir (inputs are ../in/...)
run() {
    _c=$1; shift
    for b in $BINS; do
        (cd "$W/$b" && $(exe $b) "$@" > "$_c.log" 2>&1; echo "exit=$?" >> "$_c.log")
    done
}
# result <case>: the fresh build's output
result() { cat "$W/fresh/$1.log"; }
expect_exit() { grep -qx "exit=$2" "$W/fresh/$1.log" && ok "$1" || { bad "$1 (wanted exit $2)"; result "$1"; }; }

# ---- generated inputs ----
mkdir "$W/in"
"$PY" -I - "$W/in" <<'EOF' || exit 1
import hashlib, os, random, sys, zipfile
d = sys.argv[1]; r = random.Random(1)
w = lambda n, b: open(os.path.join(d, n), 'wb').write(b)
w('empty', b'')
w('big', r.randbytes(3 << 20 | 12345))                  # over the 1 MiB read size
s = bytearray(r.randbytes(65536)); w('stock', bytes(s))
p = bytearray(s); p[0x1000:0x1004] = b'\xff\xf7\x00\xbe'; p[0x8000] ^= 0xff; w('patched', bytes(p))
w('other', r.randbytes(65536))
a = r.randbytes(200000); w('dsrc', a)
w('dtgt', a[:5000] + r.randbytes(3000) + a[5000:150000] + a[160000:] + r.randbytes(777))
w('classes.dex', b'dex\n035\0' + r.randbytes(100000))
with zipfile.ZipFile(os.path.join(d, 'app.apk'), 'w') as z:
    z.writestr('AndroidManifest.xml', r.randbytes(4000), zipfile.ZIP_DEFLATED)
    z.writestr('res/raw/a.bin', r.randbytes(9000), zipfile.ZIP_STORED)
    z.writestr('resources.arsc', b'x' * 20000, zipfile.ZIP_DEFLATED)
    z.comment = b'apk comment'
# a few MB, still small enough for Leafia's original lpatch, over the 1 MiB copy size
w('mid.dex', b'dex\n035\0' + r.randbytes((1 << 20) + 54321))
with zipfile.ZipFile(os.path.join(d, 'mid.apk'), 'w') as z:
    for i in range(12):
        z.writestr(f'assets/s{i}.bin', r.randbytes(200000 + i * 3001), zipfile.ZIP_STORED)
        z.writestr(f'assets/d{i}.txt', r.randbytes(100000).hex(), zipfile.ZIP_DEFLATED)
    z.comment = b'mid comment ' * 9
# a central directory over 4 MiB
with zipfile.ZipFile(os.path.join(d, 'widecd.apk'), 'w') as z:
    for i in range(16000):
        z.writestr(f'{i:05d}' + 'n' * 245, b'')
# recipe for stock -> patched: each change with 16 bytes of context either side
md5 = lambda b: hashlib.md5(b).hexdigest()
with open(os.path.join(d, 'test.recipe'), 'w') as f:
    f.write('id test-recipe\nfile /system/lib/test.so\n')
    for lo, hi in ((0x1000, 0x1003), (0x8000, 0x8000)):
        vlo, vhi = lo - 16, hi + 16
        f.write(f'verify {vlo:#x} {vhi - vlo + 1} {md5(s[vlo:vhi + 1])} {md5(p[vlo:vhi + 1])}\n')
        f.write(f'patch {lo:#x} {p[lo:hi + 1].hex()}\n')
EOF
"$PY" "$REPO/tools/mkdelta.py" "$W/in/dsrc" "$W/in/dtgt" "$W/in/test.lpd" > /dev/null || exit 1

# ---- md5 ----
for f in empty big; do
    run md5-$f md5 ../in/$f
    [ "$(result md5-$f | head -1 | cut -d' ' -f1)" = "$(md5sum < "$W/in/$f" | cut -d' ' -f1)" ] && ok "md5 $f" || bad "md5 $f"
done

# ---- check / apply ----
for f in stock patched other; do
    run check-$f check ../in/test.recipe ../in/$f
done
result check-stock | grep -q 'status=PATCHABLE' && ok "check stock -> PATCHABLE" || bad "check stock"
result check-patched | grep -q 'status=APPLIED' && ok "check patched -> APPLIED" || bad "check patched"
result check-other | grep -q 'status=MISMATCH' && ok "check other -> MISMATCH" || bad "check other"
run apply apply ../in/test.recipe ../in/stock applied
cmp -s "$W/fresh/applied" "$W/in/patched" && ok "apply rebuilds the patched file" || bad "apply"
run apply-wrong apply ../in/test.recipe ../in/other applied-wrong
[ ! -e "$W/fresh/applied-wrong" ] && ! grep -qx exit=0 "$W/fresh/apply-wrong.log" && ok "apply refuses a wrong file" || bad "apply-wrong"

# ---- delta ----
run delta delta ../in/test.lpd ../in/dsrc delta-out
cmp -s "$W/fresh/delta-out" "$W/in/dtgt" && ok "delta rebuilds the target" || { bad "delta"; result delta; }
run delta-wrong delta ../in/test.lpd ../in/other delta-wrong
[ ! -e "$W/fresh/delta-wrong" ] && ! grep -qx exit=0 "$W/fresh/delta-wrong.log" && ok "delta refuses a wrong source" || bad "delta-wrong"

# ---- zipadd ----
# valid_zip <label> <out.apk> <src.apk> <classes.dex>: a valid zip holding every entry of
# src unchanged (same offsets, sizes, CRCs and bytes), then classes.dex stored, same comment
valid_zip() {
    "$PY" -I - "$@" <<'EOF'
import sys, zipfile
label, out, src, dex = sys.argv[1:5]
zo, zi = zipfile.ZipFile(out), zipfile.ZipFile(src)
fails = 0
def check(name, cond):
    global fails
    print(('ok    ' if cond else 'FAIL  ') + name); fails += not cond
check(f'{label}: testzip is clean', zo.testzip() is None)
old, new = zi.infolist(), zo.infolist()
same = [i.filename for i in new] == [i.filename for i in old] + ['classes.dex']
for a, b in zip(old, new):
    key = lambda i: (i.filename, i.header_offset, i.CRC, i.compress_size, i.file_size, i.compress_type, i.extra)
    same = same and key(a) == key(b) and zi.read(a) == zo.read(b)
check(f'{label}: every original entry is unchanged ({len(old)})', same)
c = zo.getinfo('classes.dex')
check(f'{label}: classes.dex is stored and matches the input',
      c.compress_type == zipfile.ZIP_STORED and zo.read(c) == open(dex, 'rb').read())
check(f'{label}: comment kept', zo.comment == zi.comment)
sys.exit(fails > 0)
EOF
}
run zipadd zipadd ../in/app.apk ../in/classes.dex out.apk
valid_zip zipadd "$W/fresh/out.apk" "$W/in/app.apk" "$W/in/classes.dex" > "$W/valid.log" && ok "zipadd makes a valid apk" || { bad "zipadd"; cat "$W/valid.log"; result zipadd; }
run zipadd-mid zipadd ../in/mid.apk ../in/mid.dex mid-out.apk
valid_zip zipadd-mid "$W/fresh/mid-out.apk" "$W/in/mid.apk" "$W/in/mid.dex" > "$W/valid.log" && ok "zipadd makes a valid apk from a few-MB apk" || { bad "zipadd-mid"; cat "$W/valid.log"; result zipadd-mid; }
run zipadd-twice zipadd out.apk ../in/classes.dex out2.apk
[ ! -e "$W/fresh/out2.apk" ] && ! grep -qx exit=0 "$W/fresh/zipadd-twice.log" && ok "zipadd refuses an apk with classes.dex" || bad "zipadd-twice"
run zipadd-widecd zipadd ../in/widecd.apk ../in/classes.dex widecd-out.apk
[ ! -e "$W/fresh/widecd-out.apk" ] && grep -q 'central directory too large' "$W/fresh/zipadd-widecd.log" && ok "zipadd refuses a central directory over 4 MiB" || { bad "zipadd-widecd"; result zipadd-widecd; }

# ---- zipadd gives the same bytes as Leafia's original lpatch ----
mkdir "$W/leafia"
if git -C "$LEAFIA" show 08f4436:tools/lpatch/lpatch.c > "$W/leafia/lpatch.c" 2> /dev/null; then
    if cc -DHOST -O2 -Wall -o "$W/leafia/lpatch-host" "$W/leafia/lpatch.c" > "$W/leafia/build.log" 2>&1; then
        for c in "zipadd app.apk classes.dex out.apk" "zipadd-mid mid.apk mid.dex mid-out.apk"; do
            set -- $c
            (cd "$W/leafia" && ./lpatch-host zipadd ../in/$2 ../in/$3 $4 > $1.log 2>&1; echo "exit=$?" >> $1.log)
            cmp -s "$W/leafia/$4" "$W/host/$4" && cmp -s "$W/leafia/$1.log" "$W/host/$1.log" \
                && ok "$1 output and md5 line match Leafia's original lpatch" \
                || { bad "$1 differs from Leafia's original lpatch"; cat "$W/leafia/$1.log" "$W/host/$1.log"; }
        done
    else
        bad "build Leafia's original lpatch.c"; cat "$W/leafia/build.log"
    fi
else
    echo "SKIP: zipadd vs Leafia's original lpatch (no commit 08f4436 in $LEAFIA; set LEAFIA)"
fi

# ---- zipadd on a generated 200 MB zip (stored + deflated entries, comment) ----
B=$W/big; mkdir "$B"
"$PY" -I - "$B" <<'EOF' || exit 1
import os, random, sys, zipfile
d = sys.argv[1]; r = random.Random(2)
open(os.path.join(d, 'big.dex'), 'wb').write(b'dex\n035\0' + r.randbytes((3 << 20) + 4321))
with zipfile.ZipFile(os.path.join(d, 'big.apk'), 'w', compresslevel=1) as z:
    for i in range(180):
        z.writestr(f'assets/raw{i:03d}.bin', r.randbytes((1 << 20) + r.randrange(65536)), zipfile.ZIP_STORED)
    for i in range(40):
        z.writestr(f'res/txt{i:02d}.txt', r.randbytes((1 << 19) + r.randrange(65536)).hex(), zipfile.ZIP_DEFLATED)
    z.writestr('AndroidManifest.xml', r.randbytes(3000).hex(), zipfile.ZIP_DEFLATED)
    z.comment = b'big apk comment'
EOF
size=$(wc -c < "$B/big.apk")
[ "$size" -ge 200000000 ] || bad "the generated zip is only $size bytes"
# host run 1, measuring its peak RSS
set -- $(cd "$B" && "$PY" -I -c '
import resource, subprocess, sys
with open(sys.argv[1], "w") as log:
    rc = subprocess.call(sys.argv[2:], stdout=log, stderr=subprocess.STDOUT)
print(rc, resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss)
' host1.log "$W/build/lpatch-host" zipadd big.apk big.dex host1.apk)
rc=$1; rss=$2
if [ "$rc" = 0 ]; then
    m1=$(md5sum < "$B/host1.apk" | cut -d' ' -f1)
    grep -q "md5=$m1\$" "$B/host1.log" && ok "zipadd 200 MB: printed md5 matches the file" || { bad "zipadd 200 MB: printed md5"; cat "$B/host1.log"; }
    [ "$rss" -lt 49152 ] && ok "zipadd 200 MB: host peak RSS ${rss} KiB" || bad "zipadd 200 MB: host peak RSS ${rss} KiB (limit 49152)"
    valid_zip "zipadd 200 MB" "$B/host1.apk" "$B/big.apk" "$B/big.dex" || bad "zipadd 200 MB: result"
    (cd "$B" && "$W/build/lpatch-host" zipadd big.apk big.dex host2.apk > host2.log 2>&1)
    [ "$(md5sum < "$B/host2.apk" | cut -d' ' -f1)" = "$m1" ] && ok "zipadd 200 MB: two host runs give the same md5" || bad "zipadd 200 MB: two host runs differ"
    rm -f "$B/host2.apk"
    for b in fresh committed; do
        (cd "$B" && $(exe $b) zipadd big.apk big.dex $b.apk > $b.log 2>&1)
        [ "$(md5sum < "$B/$b.apk" 2> /dev/null | cut -d' ' -f1)" = "$m1" ] && ok "zipadd 200 MB: $b ARM build matches the host build" || { bad "zipadd 200 MB: $b ARM build"; cat "$B/$b.log"; }
        rm -f "$B/$b.apk"
    done
else
    bad "zipadd 200 MB (exit $rc)"; cat "$B/host1.log"
fi
rm -rf "$B"

run usage bogus
expect_exit usage 2

# ---- all three builds must agree on every output and file ----
for b in committed host; do
    diff -r "$W/fresh" "$W/$b" > "$W/diff" && ok "fresh and $b builds agree" || { bad "fresh and $b builds disagree:"; head -20 "$W/diff"; }
done

[ $FAIL = 0 ] && echo "ALL OK" || echo "SOME TESTS FAILED"
exit $FAIL
