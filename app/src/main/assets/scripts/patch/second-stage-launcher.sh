#!/bin/dash
P=/data/data/com.termux/files/usr
if [ -d "$P/etc/termux-vm-shims" ]; then
    chmod 755 "$P/etc/termux-vm-shims"/* 2>/dev/null
    PATH="$P/etc/termux-vm-shims:$P/bin"
fi
if [ -f "$P/lib/libvmfix.so" ]; then
    LD_PRELOAD="$P/lib/libvmfix.so"
fi
export PATH LD_PRELOAD
exec "$P/bin/bash" "$P/etc/termux/bootstrap/termux-bootstrap-second-stage-real.sh" "$@"
