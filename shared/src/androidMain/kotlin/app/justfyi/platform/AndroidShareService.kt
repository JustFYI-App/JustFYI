package app.justfyi.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.zacsweers.metro.Inject
import java.io.File

/**
 * Android implementation of ShareService.
 * Uses FileProvider for secure file URI and Intent.ACTION_SEND for share sheet.
 *
 * Requirements:
 * - FileProvider must be configured in AndroidManifest.xml
 * - file_paths.xml must be present in res/xml with cache-path defined
 */
@Inject
class AndroidShareService(
    private val context: Context,
) : ShareService {
    /**
     * Opens the Android share sheet to share a file.
     *
     * Uses FileProvider to generate a secure content:// URI for the file,
     * which allows other apps to access the file without requiring
     * direct file system permissions.
     *
     * @param filePath The absolute path to the file to share
     * @param mimeType The MIME type of the file (e.g., "application/zip")
     * @param fileName The display name for the file in the share sheet
     * @return true if the share sheet was successfully launched, false otherwise
     */
    override fun shareFile(
        filePath: String,
        mimeType: String,
        fileName: String,
    ): Boolean =
        try {
            val file = File(filePath)
            if (!file.exists()) {
                false
            } else {
                // Generate a content:// URI using FileProvider for secure sharing
                val fileUri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )

                // Create the share intent
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        // Grant temporary read permission to receiving apps
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        // Start activity in new task since we're using application context
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                // Create and launch the chooser
                val chooserIntent =
                    Intent.createChooser(shareIntent, null).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(chooserIntent)
                true
            }
        } catch (e: Exception) {
            // Log error for debugging but return false gracefully
            e.printStackTrace()
            false
        }
}
