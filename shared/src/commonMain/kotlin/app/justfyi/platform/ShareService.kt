package app.justfyi.platform

/**
 * Platform-agnostic interface for file sharing operations.
 * Implemented in platform-specific source sets.
 *
 * Android: Uses FileProvider and Intent.ACTION_SEND with share sheet
 * iOS: Uses UIActivityViewController with file URL
 */
interface ShareService {
    /**
     * Opens the native share sheet to share a file.
     *
     * @param filePath The absolute path to the file to share
     * @param mimeType The MIME type of the file (e.g., "application/zip")
     * @param fileName The display name for the file in the share sheet
     * @return true if the share sheet was successfully launched, false otherwise
     */
    fun shareFile(
        filePath: String,
        mimeType: String,
        fileName: String,
    ): Boolean
}
