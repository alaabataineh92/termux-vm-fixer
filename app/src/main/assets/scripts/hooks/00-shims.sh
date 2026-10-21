if [ -d /data/data/com.termux/files/usr/etc/termux-vm-shims ]; then
    case ":$PATH:" in
        *:/data/data/com.termux/files/usr/etc/termux-vm-shims:*) ;;
        *) PATH="/data/data/com.termux/files/usr/etc/termux-vm-shims:$PATH" ;;
    esac
fi
export PATH
