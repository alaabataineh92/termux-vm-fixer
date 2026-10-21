#!/data/data/com.termux/files/usr/bin/sh
B=/data/data/com.termux/files/usr/bin
D=/data/data/com.termux/files/usr/etc/termux-vm-shims
S=/data/local/tmp/termux-fix

mkdir -p "$D" "$B/../etc/profile.d" "$B/../etc/apt/apt.conf.d" || exit 1

for s in touch cp tar; do
    if [ ! -e "$B/$s.real" ] && [ -e "$B/$s" ]; then
        r=$(readlink -f "$B/$s" 2>/dev/null)
        [ -n "$r" ] && [ -f "$r" ] && {
            cp "$r" "$B/$s.real"
            chmod 755 "$B/$s.real"
        }
    fi
done

for s in touch cp tar; do
    cat "$S/shim-$s" > "$D/$s" && chmod 755 "$D/$s" || { echo "failed: $s"; exit 1; }
done

cat "$S/libvmfix.so" > "$B/../lib/libvmfix.so"
chmod 644 "$B/../lib/libvmfix.so"

cat "$S/00-shims.sh" > "$B/../etc/profile.d/00-termux-vm-shims.sh"
chmod 644 "$B/../etc/profile.d/00-termux-vm-shims.sh"
cat "$S/01-vmfix.sh" > "$B/../etc/profile.d/01-termux-vmfix.sh"
chmod 644 "$B/../etc/profile.d/01-termux-vmfix.sh"

cat "$S/termux-login.sh" > "$B/../etc/termux-login.sh"
chmod 644 "$B/../etc/termux-login.sh"

cat "$S/99-termux-vm-shims.conf" > "$B/../etc/apt/apt.conf.d/99-termux-vm-shims"
chmod 644 "$B/../etc/apt/apt.conf.d/99-termux-vm-shims"

echo "  library   $B/../lib/libvmfix.so"
echo "  shims     $D"
echo "  PATH hook $B/../etc/profile.d/"
echo "  login hook $B/../etc/termux-login.sh"
echo "  apt conf  $B/../etc/apt/apt.conf.d/99-termux-vm-shims"
