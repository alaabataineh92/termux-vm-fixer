#!/data/data/com.termux/files/usr/bin/bash
U=/data/data/com.termux/files/usr
D=/data/data/com.termux/files/usr/etc/termux-vm-shims
export PREFIX=$U PATH="$D:$U/bin:$U/bin/applets"
export HOME=/data/data/com.termux/files/home TMPDIR=$U/tmp LD_LIBRARY_PATH=$U/lib

export LD_PRELOAD="$U/lib/libtermux-exec-ld-preload.so"
[ -f "$U/etc/termux-login.sh" ] && . "$U/etc/termux-login.sh"

case "$LD_PRELOAD" in
    *libvmfix.so*) echo "  login hook:       loaded" ;;
    *)             echo "  login hook:       NOT LOADED - check etc/termux-login.sh" ;;
esac

apt_out=$(timeout 300 apt-get update 2>&1)
apt_rc=$?
if [ "$apt_rc" = "124" ]; then
    echo "  apt update:       TIMED OUT after 300s"
elif [ "$apt_rc" != "0" ]; then
    echo "  apt update:       FAILED (exit $apt_rc)"
    echo "$apt_out" | tail -5 | sed 's/^/      /'
elif echo "$apt_out" | grep -q "invalid signature"; then
    echo "  apt update:       FAILED (signature verification)"
    echo "$apt_out" | grep -i "invalid signature" | head -2 | sed 's/^/      /'
elif echo "$apt_out" | grep -q "^E:"; then
    echo "  apt update:       FAILED"
    echo "$apt_out" | grep "^E:" | head -3 | sed 's/^/      /'
else
    echo "  apt update:       ok"
fi
echo "  pending upgrades: $(apt-get -s upgrade 2>/dev/null | grep -c '^Inst ')"

broken=$(dpkg -l 2>/dev/null | while read -r st n r; do
    case "$st" in ii|"") continue ;; [a-zA-Z]*) [ -n "$n" ] && echo "$n" ;; esac
done)
if [ -z "$broken" ]; then
    echo "  broken packages:  none"
else
    echo "  broken packages:"
    echo "$broken" | sed 's/^/    /'
fi

n=0
for l in $(/system/bin/find "$U" -type l 2>/dev/null); do
    [ -e "$l" ] || n=$((n+1))
done
echo "  broken symlinks:  $n"
