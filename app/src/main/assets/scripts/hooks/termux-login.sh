if [ -f /data/data/com.termux/files/usr/lib/libvmfix.so ]; then
    case ":$LD_PRELOAD:" in
        *:/data/data/com.termux/files/usr/lib/libvmfix.so:*) ;;
        *) LD_PRELOAD="/data/data/com.termux/files/usr/lib/libvmfix.so${LD_PRELOAD:+:$LD_PRELOAD}" ;;
    esac
fi
export LD_PRELOAD
