#!/bin/sh
# Check, fetch and export the host build toolchain (kept in private/toolchain, git-ignored).
# usage: tools/toolchain.sh check   one "ok <name> <path>" line per tool; exit 1 if any is missing
#        tools/toolchain.sh fetch   download the missing Maven Central jars (sha1 + pinned sha256)
#        tools/toolchain.sh env     print export lines for ANDROID_JAR BUILD_TOOLS SMALI_CP BAKSMALI_CP
#                                   JUNIT_CP PY QEMU_ARM MOCK_SH; use: eval "$(tools/toolchain.sh env)"
# environment: TOOLCHAIN_DIR (default <repo>/private/toolchain), ANDROID_SDK (default ~/Android/sdk)

set -e
TC_ROOT=$(cd "$(dirname "$0")/.." && pwd)
TOOLCHAIN_DIR=${TOOLCHAIN_DIR:-$TC_ROOT/private/toolchain}
ANDROID_SDK=${ANDROID_SDK:-$HOME/Android/sdk}
TC_BUILD_TOOLS=$ANDROID_SDK/build-tools/30.0.3
TC_MAVEN=https://repo1.maven.org/maven2

# Maven Central jars: <folder under TOOLCHAIN_DIR> <path under Maven Central> <pinned sha256>
tc_jars() {
    cat <<LIST
smali org/smali/smali/2.5.2/smali-2.5.2.jar 136c5c4653d6531bd7b6f10f35f8691cb96432e727d30b4d5579826ee01e9419
smali org/smali/baksmali/2.5.2/baksmali-2.5.2.jar 1ed236266d7dc4907aade0b19a34f77efac25342b63c8ace52e579039941b389
smali org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar 5a5c8982d8bd7d6e3bb1a0713049e3c78b719ec32b20f6b619885cec30a0dd61
smali org/smali/util/2.5.2/util-2.5.2.jar 4f580a9cff3ebb83cb3fd20bec88e37e4f183796ca652732992611620282daea
smali com/google/guava/guava/31.0.1-jre/guava-31.0.1-jre.jar d5be94d65e87bd219fb3193ad1517baa55a3b88fc91d21cf735826ab5af087b9
smali com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar a171ee4c734dd2da837e4b16be9df4661afab72a41adaf31eb84dfdaf936ca26
smali com/beust/jcommander/1.64/jcommander-1.64.jar 156be736199c990321d9ff77090b199629cfc9865e2d6c13f7cd291bb1641817
smali com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar 766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7
smali antlr/antlr/2.7.7/antlr-2.7.7.jar 88fbda4b912596b9f56e8e12e580cc954bacfb51776ecfddd3e18fc1cf56dc4c
smali org/antlr/antlr-runtime/3.5.2/antlr-runtime-3.5.2.jar ce3fc8ecb10f39e9a3cddcbb2ce350d272d9cd3d0b1e18e6fe73c3b9389c8734
smali org/antlr/stringtemplate/3.2.1/stringtemplate-3.2.1.jar f66ce72e965e5301cb0f020e54d2ba6ad76feb91b3cbfc30dbbf00c06a6df6d7
junit junit/junit/4.13.2/junit-4.13.2.jar 8e495b634469d64fb8acfa3495a065cbacc8a0fff55ce1e31007be4c16dc57d3
junit org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar 66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9
LIST
}

tc_cp() { # tc_cp <folder> <jar>...: a class path of those jars
    _cp=
    _d=$1; shift
    for _j in "$@"; do _cp=$_cp${_cp:+:}$TOOLCHAIN_DIR/$_d/$_j.jar; done
    echo "$_cp"
}

tc_env() {
    PY=$TOOLCHAIN_DIR/venv/bin/python
    ANDROID_JAR=$ANDROID_SDK/platforms/android-19/android.jar
    BUILD_TOOLS=$TC_BUILD_TOOLS
    SMALI_CP=$(tc_cp smali smali-2.5.2 util-2.5.2 dexlib2-2.5.2 guava-31.0.1-jre failureaccess-1.0.1 \
        jcommander-1.64 jsr305-3.0.2 antlr-2.7.7 antlr-runtime-3.5.2 stringtemplate-3.2.1)
    BAKSMALI_CP=$(tc_cp smali baksmali-2.5.2 util-2.5.2 dexlib2-2.5.2 guava-31.0.1-jre failureaccess-1.0.1 \
        jcommander-1.64 jsr305-3.0.2)
    JUNIT_CP=$(tc_cp junit junit-4.13.2 hamcrest-core-1.3)
    QEMU_ARM=$(command -v qemu-arm-static || command -v qemu-arm || true)
    if command -v mksh > /dev/null 2>&1; then MOCK_SH=mksh; else MOCK_SH="bash --posix"; fi
    export PY ANDROID_JAR BUILD_TOOLS SMALI_CP BAKSMALI_CP JUNIT_CP QEMU_ARM MOCK_SH
}

tc_print_env() {
    for _v in ANDROID_JAR BUILD_TOOLS SMALI_CP BAKSMALI_CP JUNIT_CP PY QEMU_ARM MOCK_SH; do
        eval "_val=\$$_v"
        echo "export $_v='$_val'"
    done
}

tc_check() {
    tc_env
    TC_FAILS=0
    ok() { echo "ok $1 $2"; }
    missing() { echo "missing $1${2:+ ($2)}"; TC_FAILS=$((TC_FAILS + 1)); }

    _jv=$(javac -version 2>&1 || true)
    _jv=${_jv#javac }
    _jm=${_jv%%.*}
    case $_jm in
        ''|*[!0-9]*) missing javac "no JDK found" ;;
        *) if [ "$_jm" -ge 11 ]; then ok javac "$(command -v javac) $_jv"; else missing javac "JDK $_jv, need 11 or newer"; fi ;;
    esac

    if [ -f "$ANDROID_JAR" ]; then ok android.jar "$ANDROID_JAR"; else missing android.jar "$ANDROID_JAR"; fi
    for _t in d8 aapt zipalign apksigner dexdump; do
        if [ -x "$TC_BUILD_TOOLS/$_t" ]; then ok "$_t" "$TC_BUILD_TOOLS/$_t"; else missing "$_t" "$TC_BUILD_TOOLS/$_t"; fi
    done

    if java -cp "$SMALI_CP" org.jf.smali.Main --version > /dev/null 2>&1; then ok smali "$TOOLCHAIN_DIR/smali"; else missing smali "$TOOLCHAIN_DIR/smali; run tools/toolchain.sh fetch"; fi
    if java -cp "$BAKSMALI_CP" org.jf.baksmali.Main --version > /dev/null 2>&1; then ok baksmali "$TOOLCHAIN_DIR/smali"; else missing baksmali "$TOOLCHAIN_DIR/smali; run tools/toolchain.sh fetch"; fi

    if [ -f "$TOOLCHAIN_DIR/junit/junit-4.13.2.jar" ] && [ -f "$TOOLCHAIN_DIR/junit/hamcrest-core-1.3.jar" ]; then ok junit "$TOOLCHAIN_DIR/junit"; else missing junit "$TOOLCHAIN_DIR/junit; run tools/toolchain.sh fetch"; fi

    _bv=$("$PY" -c 'import importlib.metadata as m; print(m.version("bsdiff4"))' 2>/dev/null || true)
    if [ "$_bv" = 1.3.1 ]; then ok bsdiff4 "$PY $_bv"; else missing bsdiff4 "$TOOLCHAIN_DIR/venv with bsdiff4 1.3.1"; fi

    for _t in clang ld.lld keytool; do
        _p=$(command -v "$_t" || true)
        if [ -n "$_p" ]; then ok "$_t" "$_p"; else missing "$_t"; fi
    done
    if [ -n "$QEMU_ARM" ]; then ok qemu-arm "$QEMU_ARM"; else missing qemu-arm "qemu-arm-static or qemu-arm"; fi

    _p=$(command -v mksh || true)
    if [ -n "$_p" ]; then ok mksh "$_p"; else echo "optional mksh missing (tests use: $MOCK_SH)"; fi

    [ "$TC_FAILS" -eq 0 ]
}

# sha256 of a file
tc_sha256() { sha256sum "$1" | cut -d' ' -f1; }

tc_fetch() {
    # refuse before downloading anything if a jar already here is not the pinned one
    _bad=0
    while read -r _dir _path _sha; do
        _dest=$TOOLCHAIN_DIR/$_dir/${_path##*/}
        [ -f "$_dest" ] || continue
        if [ "$(tc_sha256 "$_dest")" != "$_sha" ]; then
            echo "sha256 mismatch: $_dest (delete it and fetch again)"
            _bad=1
        fi
    done <<LIST
$(tc_jars)
LIST
    [ "$_bad" -eq 0 ] || return 1

    _tmp=$(mktemp -d)
    trap 'rm -rf "$_tmp"' EXIT
    while read -r _dir _path _sha; do
        _name=${_path##*/}
        _dest=$TOOLCHAIN_DIR/$_dir/$_name
        [ -f "$_dest" ] && continue
        echo "fetching $_name"
        curl -fsS -o "$_tmp/$_name" "$TC_MAVEN/$_path"
        curl -fsS -o "$_tmp/$_name.sha1" "$TC_MAVEN/$_path.sha1"
        _want=$(cut -c1-40 "$_tmp/$_name.sha1")
        _have=$(sha1sum "$_tmp/$_name" | cut -d' ' -f1)
        if [ "$_have" != "$_want" ]; then echo "sha1 mismatch: $_name"; return 1; fi
        if [ "$(tc_sha256 "$_tmp/$_name")" != "$_sha" ]; then echo "sha256 mismatch: $_name"; return 1; fi
        mkdir -p "$TOOLCHAIN_DIR/$_dir"
        mv "$_tmp/$_name" "$_dest"
    done <<LIST
$(tc_jars)
LIST
}

case ${1:-} in
    check) tc_check ;;
    fetch) tc_fetch ;;
    env) tc_env; tc_print_env ;;
    *) echo "usage: tools/toolchain.sh check|fetch|env" >&2; exit 2 ;;
esac
