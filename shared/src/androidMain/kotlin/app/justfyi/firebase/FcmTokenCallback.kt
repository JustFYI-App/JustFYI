package app.justfyi.firebase

/**
 * Callback interface for Firebase Cloud Messaging token events.
 * Used to handle FCM token retrieval and refresh events.
 */
interface FcmTokenCallback {
    /**
     * Called when a new FCM token is received or refreshed.
     *
     * @param token The new FCM registration token
     */
    fun onTokenReceived(token: String)

    /**
     * Called when FCM token retrieval fails.
     *
     * @param exception The exception that caused the failure
     */
    fun onTokenError(exception: Exception)
}
