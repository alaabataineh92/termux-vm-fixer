#!/system/bin/sh
U=/data/data/com.termux/files/usr
B=$U/bin
P=/system/bin

for l in $B/*; do
    [ -L "$l" ] || continue
    [ -e "$l" ] && continue
    base=$($P/basename "$($P/readlink "$l")")
    c="$B/$base"
    if [ -f "$c" ] && [ ! -L "$c" ]; then
        $P/ln -sfn "$c" "$l"
    elif [ -f "$B/coreutils" ]; then
        $P/ln -sfn "$B/coreutils" "$l"
    fi
done

n=0
for l in $($P/find "$U" -type l 2>/dev/null); do
    [ -e "$l" ] && continue
    case "$l" in "$B/"*) continue ;; esac
    base=$($P/basename "$($P/readlink "$l")")
    f=$($P/find "$U" -name "$base" -type f 2>/dev/null | $P/head -1)
    [ -n "$f" ] && $P/ln -sfn "$f" "$l" && n=$((n+1))
done
echo "  symlinks repaired: $n"
