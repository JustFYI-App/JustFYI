package app.justfyi.util

/**
 * Returns the current time in milliseconds since epoch.
 * Uses System.currentTimeMillis() on Android and NSDate on iOS.
 */
expect fun currentTimeMillis(): Long

/**
 * Generates a random UUID string.
 * Uses java.util.UUID on Android and NSUUID on iOS.
 */
expect fun generateUuid(): String

/**
 * Returns true if the app is running in debug mode.
 * Uses BuildConfig.DEBUG on Android and Platform.isDebugBinary on iOS.
 */
expect val isDebug: Boolean
