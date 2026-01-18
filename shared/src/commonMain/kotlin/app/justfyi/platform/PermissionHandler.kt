package app.justfyi.platform

/**
 * Platform-agnostic interface for BLE permission handling.
 * Implemented in platform-specific source sets.
 *
 * Android: Uses BlePermissionHandler
 * iOS: Uses Core Bluetooth permission APIs
 */
interface BlePermissionHandler {
    /**
     * Gets the list of required permissions for BLE operations.
     *
     * @return List of permission strings that need to be granted
     */
    fun getRequiredPermissions(): List<String>

    /**
     * Checks if all required BLE permissions are granted.
     *
     * @return true if all required permissions are granted
     */
    fun hasAllPermissions(): Boolean

    /**
     * Gets a user-friendly description of what permissions are needed and why.
     *
     * @return Description of required permissions
     */
    fun getPermissionRationale(): String
}

/**
 * Platform-agnostic interface for notification permission handling.
 * Implemented in platform-specific source sets.
 *
 * Android: Checks POST_NOTIFICATIONS permission (Android 13+)
 * iOS: Uses UNUserNotificationCenter authorization status
 */
interface NotificationPermissionHandler {
    /**
     * Checks if notification permission is granted.
     *
     * @return true if notification permission is granted
     */
    fun hasPermission(): Boolean
}
