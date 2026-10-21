# Added by the patched Termux bootstrap.
if [ -f /data/data/com.termux/files/usr/lib/libvmfix.so ]; then
    case ":$LD_PRELOAD:" in
        *:/data/data/com.termux/files/usr/lib/libvmfix.so:*) ;;
        *) LD_PRELOAD="/data/data/com.termux/files/usr/lib/libvmfix.so${LD_PRELOAD:+:$LD_PRELOAD}" ;;
    esac
fi
export LD_PRELOAD

if [ -x /system/bin/ln ] && [ -x /system/bin/readlink ]; then
    _vmfix_damaged=0

    for _t in rm head grep ls cat sed ln mkdir; do
        _p=$(command -v "$_t" 2>/dev/null) || { _vmfix_damaged=1; break; }
        [ -e "$_p" ] || { _vmfix_damaged=1; break; }
    done

    for _f in "/data/data/com.termux/files/usr"/bin/*.dpkg-tmp "/data/data/com.termux/files/usr"/bin/*.dpkg-new; do
        [ -e "$_f" ] || [ -L "$_f" ] || continue
        _vmfix_damaged=1
        break
    done

    if [ "$_vmfix_damaged" = "1" ]; then
        echo "[*] repairing a damaged prefix"

        _fixed=0
        for _dir in "/data/data/com.termux/files/usr/bin" "/data/data/com.termux/files/usr/lib" "/data/data/com.termux/files/usr/libexec"; do
            [ -d "$_dir" ] || continue
            for _f in "$_dir"/*; do
                [ -L "$_f" ] || continue

                _target=$(/system/bin/readlink "$_f" 2>/dev/null) || continue
                case "$_target" in
                    /*) ;;
                    *) continue ;;
                esac

                [ -e "$_target" ] && continue

                _base=${_target##*/}
                [ -n "$_base" ] || continue

                if [ -e "$_dir/$_base" ]; then
                    if /system/bin/ln -sfn "$_dir/$_base" "$_f" 2>/dev/null; then
                        _fixed=$((_fixed + 1))
                    fi
                fi
            done
        done

        _removed=0
        for _dir in "/data/data/com.termux/files/usr/bin" "/data/data/com.termux/files/usr/lib" "/data/data/com.termux/files/usr/libexec" "/data/data/com.termux/files/usr/etc"; do
            [ -d "$_dir" ] || continue
            for _f in "$_dir"/*.dpkg-tmp "$_dir"/*.dpkg-new "$_dir"/*.dpkg-old; do
                [ -e "$_f" ] || [ -L "$_f" ] || continue
                if /system/bin/rm -f "$_f" 2>/dev/null; then
                    _removed=$((_removed + 1))
                fi
            done
        done

        echo "[*] links: $_fixed re-pointed, $_removed dpkg leftovers cleared"

        unset _vmfix_damaged _fixed _removed _dir _f _target _base _t _p
    fi
fi
