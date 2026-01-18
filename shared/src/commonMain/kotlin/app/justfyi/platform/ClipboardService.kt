package app.justfyi.platform

/**
 * Platform-agnostic interface for clipboard operations.
 * Implemented in platform-specific source sets.
 *
 * Android: Uses ClipboardManager
 * iOS: Uses UIPasteboard
 */
interface ClipboardService {
    /**
     * Copies text to the system clipboard.
     *
     * @param label A label describing the content
     * @param text The text to copy
     */
    fun copyText(
        label: String,
        text: String,
    )
}
