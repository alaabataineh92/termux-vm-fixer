(
    PREFIX="__PREFIX__"
    LOCK="$PREFIX/etc/termux/bootstrap/termux-vm-links.lock"
    MANIFEST="$PREFIX/SYMLINKS.txt"

    if [ ! -f "$LOCK" ] && [ -f "$MANIFEST" ]; then
        echo "[*] verifying symlinks (the installer does not retry)"

        fixed=0
        broken=0
        arrow=$(printf '\342\206\220')

        while IFS= read -r line; do
            case "$line" in
                *"$arrow"*) ;;
                *) continue ;;
            esac

            target=$(printf '%s' "$line" | sed 's/'"$arrow"'.*//')
            link=${line#*"$arrow"}
            link=${link#./}
            full="$PREFIX/$link"

            if [ -L "$full" ] && [ "$(readlink "$full")" = "$target" ]; then
                continue
            fi

            ok=0
            tries=0
            while [ $tries -lt 3 ]; do
                tries=$((tries + 1))
                mkdir -p "$(dirname "$full")" 2>/dev/null
                if ln -sfn "$target" "$full" 2>/dev/null; then
                    ok=1
                    break
                fi
            done

            if [ "$ok" = "1" ]; then
                fixed=$((fixed + 1))
            else
                broken=$((broken + 1))
            fi
        done < "$MANIFEST"

        echo "[*] symlinks: $fixed repaired, $broken still failing"

        if [ "$broken" = "0" ]; then
            mkdir -p "$(dirname "$LOCK")" 2>/dev/null
            : > "$LOCK"
        fi
    fi
)
