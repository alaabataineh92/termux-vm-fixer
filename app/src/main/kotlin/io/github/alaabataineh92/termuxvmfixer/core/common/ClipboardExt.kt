package io.github.alaabataineh92.termuxvmfixer.core.common

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

suspend fun Clipboard.putText(text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText("text", text)))
}
