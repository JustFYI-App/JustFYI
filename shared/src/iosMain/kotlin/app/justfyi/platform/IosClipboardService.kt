package app.justfyi.platform

import platform.UIKit.UIPasteboard

/**
 * iOS implementation of ClipboardService using UIPasteboard.
 *
 * UIPasteboard is the iOS equivalent of Android's ClipboardManager.
 * It provides system-wide clipboard functionality.
 */
class IosClipboardService : ClipboardService {
    /**
     * Copies text to the system clipboard.
     *
     * @param label A label describing the content (not used on iOS, but kept for API compatibility)
     * @param text The text to copy
     */
    override fun copyText(
        label: String,
        text: String,
    ) {
        UIPasteboard.generalPasteboard.string = text
    }
}
