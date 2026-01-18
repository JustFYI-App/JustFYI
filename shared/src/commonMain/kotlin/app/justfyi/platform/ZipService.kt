package app.justfyi.platform

import app.justfyi.domain.model.ExportData

/**
 * Platform-agnostic interface for ZIP file creation.
 * Implemented in platform-specific source sets.
 *
 * Android: Uses java.util.zip.ZipOutputStream
 * iOS: Uses Foundation NSFileManager and Compression framework
 */
interface ZipService {
    /**
     * Creates a ZIP file containing the exported user data as JSON files.
     *
     * The ZIP file will contain:
     * - user.json: User profile data
     * - interactions.json: List of recorded interactions
     * - notifications.json: List of exposure notifications
     * - reports.json: List of exposure reports
     *
     * JSON files are formatted with pretty-print enabled for readability.
     *
     * @param data The export data to include in the ZIP
     * @param fileName The name for the ZIP file (e.g., "justfyi-export-1234567890.zip")
     * @return FileResult containing the file path and MIME type
     */
    suspend fun createZip(
        data: ExportData,
        fileName: String,
    ): FileResult
}

/**
 * Result of a file creation operation.
 *
 * @property filePath Absolute path to the created file
 * @property mimeType MIME type of the file (e.g., "application/zip")
 */
data class FileResult(
    val filePath: String,
    val mimeType: String,
)
