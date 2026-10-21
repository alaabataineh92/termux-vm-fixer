#!/system/bin/sh
U=/data/data/com.termux/files/usr
B=$U/bin
D=/data/data/com.termux/files/usr/etc/termux-vm-shims
SYS=/system/bin
export PREFIX=$U PATH="$D:$B:$B/applets:$SYS"
export HOME=/data/data/com.termux/files/home TMPDIR=$U/tmp LD_LIBRARY_PATH=$U/lib
export LD_PRELOAD=/data/data/com.termux/files/usr/lib/libvmfix.so
export DEBIAN_FRONTEND=noninteractive

i=0
while [ $i -lt 10 ]; do
    i=$((i+1))
    $B/dpkg --configure -a --force-confold < /dev/null >/dev/null 2>&1 && break
    sleep 2
done

i=0
while [ $i -lt 3 ]; do
    i=$((i+1))
    broken=$($B/dpkg -l 2>/dev/null | while read -r st n r; do
        case "$st" in ii|"") continue ;; [a-zA-Z]*) [ -n "$n" ] && echo "$n" ;; esac
    done)
    [ -z "$broken" ] && break
    for p in $broken; do
        d=$($SYS/ls /data/data/com.termux/cache/apt/archives/${p}_*.deb 2>/dev/null | $SYS/head -1)
        [ -n "$d" ] && $B/dpkg -i --force-confold "$d" < /dev/null >/dev/null 2>&1
    done
done
