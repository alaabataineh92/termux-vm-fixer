#!/system/bin/sh
set -e
PREFIX=/data/data/com.termux/files/usr
TMPD=/data/local/tmp/termux-fix

rm -rf "/data/data/com.termux/files/usr.old" 2>/dev/null || true

echo "[*] clearing the old prefix"
clear_prefix() {
    i=0
    while [ $i -lt 3 ]; do
        i=$((i+1))
        rm -rf "$PREFIX" 2>/dev/null
        [ ! -e "$PREFIX" ] && return 0
        sleep 1
    done

    echo "[!] could not delete the old prefix, moving it aside"
    rm -rf "/data/data/com.termux/files/usr.old" 2>/dev/null
    mv "$PREFIX" "/data/data/com.termux/files/usr.old" 2>/dev/null || return 1
    [ ! -e "$PREFIX" ] && return 0
    return 1
}

if ! clear_prefix; then
    echo "[!] cannot clear $PREFIX - giving up"
    exit 1
fi
mkdir -p "$PREFIX"

echo "[*] extracting"
cd "$PREFIX"
tar xf "$TMPD/bootstrap.tar"

echo "[*] setting permissions"
find "$PREFIX/bin" -type f -exec chmod 700 {} \;
find "$PREFIX/libexec" -type f -exec chmod 700 {} \;
[ -f "$PREFIX/lib/apt/apt-helper" ] && chmod 700 "$PREFIX/lib/apt/apt-helper"
[ -d "$PREFIX/lib/apt/methods" ] && find "$PREFIX/lib/apt/methods" -type f -exec chmod 700 {} \;
chmod 700 "$PREFIX/etc/termux/bootstrap/termux-bootstrap-second-stage.sh"
find "$PREFIX/lib" -maxdepth 1 -type f -name '*.so*' -exec chmod 644 {} \;

echo "[*] creating symlinks"
rm -f "$PREFIX/etc/termux/bootstrap/termux-bootstrap-second-stage.sh.lock"
/system/bin/sh "$TMPD/make_symlinks.sh"
[ -L "$PREFIX/bin/sh" ] || { echo "[!] symlinks missing, aborting"; exit 1; }

echo "[*] installing libvmfix.so"
mkdir -p "$PREFIX/lib"
cat "$TMPD/libvmfix.so" > "/data/data/com.termux/files/usr/lib/libvmfix.so"
chmod 644 "/data/data/com.termux/files/usr/lib/libvmfix.so"

echo "[*] installing shims"
mkdir -p "/data/data/com.termux/files/usr/etc/termux-vm-shims"
for s in touch cp tar; do
    cat "$TMPD/shim-$s" > "/data/data/com.termux/files/usr/etc/termux-vm-shims/$s"
    chmod 755 "/data/data/com.termux/files/usr/etc/termux-vm-shims/$s"
done

echo "[*] running the second stage"
export PREFIX TERMUX__PREFIX="$PREFIX"
export PATH="/data/data/com.termux/files/usr/etc/termux-vm-shims:$PREFIX/bin:$PREFIX/bin/applets"
export HOME=/data/data/com.termux/files/home TMPDIR="$PREFIX/tmp" LD_LIBRARY_PATH="$PREFIX/lib" LANG=en_US.UTF-8
export LD_PRELOAD="/data/data/com.termux/files/usr/lib/libvmfix.so"
cd /
"$PREFIX/etc/termux/bootstrap/termux-bootstrap-second-stage.sh" \
    || echo "[!] second stage had errors, continuing"

echo "[*] fixing the termux-exec preload library"
SRC=""
for cand in libtermux-exec-linker-ld-preload.so libtermux-exec-direct-ld-preload.so; do
    [ -f "$PREFIX/lib/$cand" ] && { SRC="$PREFIX/lib/$cand"; break; }
done
DST="$PREFIX/lib/libtermux-exec-ld-preload.so"
if [ -n "$SRC" ]; then
    if [ ! -f "$DST" ] || ! cmp -s "$SRC" "$DST" 2>/dev/null; then
        cat "$SRC" > "$DST"
    fi
    chmod 644 "$DST"
fi

echo "[*] re-checking permissions"
find "$PREFIX/bin" -type f ! -perm -u+x -exec chmod 700 {} \;

echo "[*] checking bash"
"$PREFIX/bin/bash" -c 'echo "[*] bash $BASH_VERSION"' \
    || { echo "[!] bash will not run"; exit 1; }
echo "[*] bootstrap done"
