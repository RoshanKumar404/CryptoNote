package com.example.cryptnote.security.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface ClipboardAutoClearManager {
    fun copyWithAutoClear(
        label: String,
        text: String,
        ttlMillis: Long = DEFAULT_CLIPBOARD_TTL_MS,
        onCleared: (() -> Unit)? = null
    )
    fun clearClipboard()

    companion object {
        const val DEFAULT_CLIPBOARD_TTL_MS = 60_000L // 60 seconds
    }
}

@Singleton
class AndroidClipboardAutoClearManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ClipboardAutoClearManager {

    private val scope = CoroutineScope(dispatcher)
    private var scheduledClearJob: Job? = null
    private var lastCopiedText: String? = null

    private val clipboardManager: ClipboardManager? by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    override fun copyWithAutoClear(
        label: String,
        text: String,
        ttlMillis: Long,
        onCleared: (() -> Unit)?
    ) {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager?.setPrimaryClip(clip)
        lastCopiedText = text

        scheduledClearJob?.cancel()
        scheduledClearJob = scope.launch {
            delay(ttlMillis)
            val currentClip = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()
            if (currentClip == lastCopiedText) {
                clearClipboard()
                onCleared?.invoke()
            }
        }
    }

    override fun clearClipboard() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboardManager?.clearPrimaryClip()
            } else {
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (_: Exception) {
            clipboardManager?.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        lastCopiedText = null
        scheduledClearJob?.cancel()
    }
}
