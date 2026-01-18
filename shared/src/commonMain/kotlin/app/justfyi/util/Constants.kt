package app.justfyi.util

/**
 * Application-wide constants for the Just FYI app.
 */
object Constants {
    /**
     * Data retention period in days.
     * All interaction and notification data older than this will be deleted.
     */
    const val DATA_RETENTION_DAYS = 180

    /**
     * Maximum length for usernames.
     */
    const val MAX_USERNAME_LENGTH = 30

    /**
     * BLE-related constants
     */
    object Ble {
        /**
         * Custom Service UUID for Just FYI BLE advertising.
         */
        const val SERVICE_UUID = "7b5c3a1e-8f2d-4e6a-9c0b-1d2e3f4a5b6c"

        /**
         * Characteristic UUID for user ID hash.
         */
        const val USER_ID_CHARACTERISTIC_UUID = "7b5c3a1e-8f2d-4e6a-9c0b-1d2e3f4a5b6d"

        /**
         * Characteristic UUID for username.
         */
        const val USERNAME_CHARACTERISTIC_UUID = "7b5c3a1e-8f2d-4e6a-9c0b-1d2e3f4a5b6e"

        /**
         * Scan timeout in milliseconds.
         */
        const val SCAN_TIMEOUT_MS = 10_000L

        /**
         * Stale device threshold in milliseconds.
         */
        const val STALE_DEVICE_THRESHOLD_MS = 30_000L
    }

    /**
     * Notification channel IDs
     */
    object NotificationChannels {
        const val EXPOSURE_NOTIFICATIONS = "exposure_notifications"
        const val UPDATES = "updates"
    }
}
