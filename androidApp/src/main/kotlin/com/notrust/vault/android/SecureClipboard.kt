package com.notrust.vault.android

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CLEAR_AFTER_MS = 30_000L

/** Copies [text] to the clipboard, marked sensitive on API 33+, then wipes it after 30s. */
fun copyThenAutoClear(context: Context, scope: CoroutineScope, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)

    scope.launch {
        delay(CLEAR_AFTER_MS)
        val current = clipboard.primaryClip
        val stillOurs = current != null &&
            current.itemCount > 0 &&
            current.getItemAt(0).text?.toString() == text
        if (stillOurs) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, ""))
        }
    }
}
