package io.github.alaabataineh92.termuxvmfixer.core.common

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
