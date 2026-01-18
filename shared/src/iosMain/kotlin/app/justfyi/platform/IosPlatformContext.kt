package app.justfyi.platform

import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS implementation of PlatformContext.
 *
 * Provides iOS-specific context operations like:
 * - Opening URLs in Safari
 * - Getting app version from Info.plist
 */
class IosPlatformContext : PlatformContext {
    /**
     * Opens a URL in Safari.
     *
     * @param url The URL to open
     * @return true if the URL was opened successfully, false otherwise
     */
    override fun openUrl(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        return UIApplication.sharedApplication.openURL(nsUrl)
    }

    /**
     * Gets the application version name from Info.plist.
     *
     * @return The version name (e.g., "1.0.0") or "Unknown" if unavailable
     */
    override fun getAppVersionName(): String =
        NSBundle.mainBundle
            .objectForInfoDictionaryKey("CFBundleShortVersionString")
            ?.toString() ?: "Unknown"
}
