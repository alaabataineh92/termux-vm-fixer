package io.github.alaabataineh92.termuxvmfixer.core.model

import android.annotation.SuppressLint

object TermuxPaths {
    const val PACKAGE = "com.termux"
    @SuppressLint("SdCardPath")
    const val DATA_DIR = "/data/data/$PACKAGE"
    const val PREFIX = "$DATA_DIR/files/usr"
    const val BIN = "$PREFIX/bin"
    const val SHIM_DIR = "$PREFIX/etc/termux-vm-shims"
    const val LIB_DIR = "$PREFIX/lib"
    const val LIBRARY = "$LIB_DIR/libvmfix.so"
    const val WORK_DIR = "/data/local/tmp/termux-fix"
    val REQUIRED_DIRS = listOf("bin", "lib", "libexec", "etc", "share")
    val SHIMS = listOf("touch", "cp", "tar")
    const val BOOTSTRAP_ENTRY_SUFFIX = "libtermux-bootstrap.so"
    const val SYMLINKS_FILE = "SYMLINKS.txt"
    const val SYMLINK_SEPARATOR = '←'
}
