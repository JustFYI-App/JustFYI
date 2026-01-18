package app.justfyi.platform

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

/**
 * iOS implementation of ShareService.
 * Uses UIActivityViewController for native share sheet.
 */
class IosShareService : ShareService {
    /**
     * Opens the iOS share sheet to share a file.
     *
     * Converts the file path to an NSURL and presents a UIActivityViewController
     * from the root view controller. The share sheet allows the user to share
     * the file via AirDrop, email, Messages, Files app, and other iOS sharing options.
     *
     * @param filePath The absolute path to the file to share
     * @param mimeType The MIME type of the file (not directly used on iOS, but kept for API compatibility)
     * @param fileName The display name for the file (used for the activity item title)
     * @return true if the share sheet was successfully launched, false otherwise
     */
    override fun shareFile(
        filePath: String,
        mimeType: String,
        fileName: String,
    ): Boolean =
        try {
            // Convert file path to NSURL
            val fileUrl = NSURL.fileURLWithPath(filePath)

            // Get the root view controller to present the share sheet
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (rootViewController == null) {
                false
            } else {
                // Create UIActivityViewController with the file URL
                val activityViewController =
                    UIActivityViewController(
                        activityItems = listOf(fileUrl),
                        applicationActivities = null,
                    )

                // Present the share sheet
                rootViewController.presentViewController(
                    activityViewController,
                    animated = true,
                    completion = null,
                )
                true
            }
        } catch (e: Exception) {
            // Return false gracefully on any error
            e.printStackTrace()
            false
        }
}
