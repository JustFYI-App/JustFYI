package app.justfyi.platform

/**
 * Platform-agnostic interface for context-dependent operations.
 * Implemented in platform-specific source sets.
 *
 * This interface abstracts platform-specific context operations like:
 * - Opening URLs in the default browser
 * - Getting app version information
 * - Starting system activities
 */
interface PlatformContext {
    /**
     * Opens a URL in the system's default browser.
     *
     * @param url The URL to open
     * @return true if the URL was opened successfully, false otherwise
     */
    fun openUrl(url: String): Boolean

    /**
     * Gets the application version name.
     *
     * @return The version name (e.g., "1.0.0") or "Unknown" if unavailable
     */
    fun getAppVersionName(): String
}
