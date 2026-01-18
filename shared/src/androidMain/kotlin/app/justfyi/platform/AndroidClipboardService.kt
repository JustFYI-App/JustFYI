package app.justfyi.platform

import android.content.ClipData
import android.content.ClipboardManager
import dev.zacsweers.metro.Inject

/**
 * Android implementation of ClipboardService.
 * Uses Android's ClipboardManager system service.
 */
@Inject
class AndroidClipboardService(
    private val clipboardManager: ClipboardManager,
) : ClipboardService {
    override fun copyText(
        label: String,
        text: String,
    ) {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)
    }
}
