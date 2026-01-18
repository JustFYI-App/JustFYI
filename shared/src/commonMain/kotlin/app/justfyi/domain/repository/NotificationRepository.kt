package app.justfyi.domain.repository

import app.justfyi.domain.model.Notification
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for exposure notification operations.
 * Listens to Firestore for real-time updates and caches locally for offline access.
 */
interface NotificationRepository {
    /**
     * Gets all notifications as a Flow for reactive updates.
     * Ordered by date (newest first).
     */
    fun getNotifications(): Flow<List<Notification>>

    /**
     * Gets the count of unread notifications as a Flow.
     * Used for displaying the notification badge.
     */
    fun getUnreadCount(): Flow<Int>

    /**
     * Gets a single notification by ID from local cache.
     * @param notificationId The notification ID
     * @return The notification, or null if not found locally
     */
    suspend fun getNotificationById(notificationId: String): Notification?

    /**
     * Fetches a single notification directly from Firestore by document ID.
     * Use this when notification is not found locally (e.g., deep link from push notification).
     * @param notificationId The Firestore document ID
     * @return The notification, or null if not found
     */
    suspend fun fetchNotificationFromCloud(notificationId: String): Notification?

    /**
     * Marks a notification as read.
     * @param notificationId The notification ID
     */
    suspend fun markAsRead(notificationId: String)

    /**
     * Marks all notifications as read.
     */
    suspend fun markAllAsRead()

    /**
     * Updates the chain data for a notification.
     * Called when someone in the chain reports a negative test result.
     *
     * @param notificationId The notification ID
     * @param newChainData Updated JSON chain data with new test status
     */
    suspend fun updateChainData(
        notificationId: String,
        newChainData: String,
    )

    /**
     * Syncs notifications from Firestore to local cache.
     * Sets up a real-time listener for ongoing updates.
     *
     * @param userId The current user's anonymous ID to filter notifications
     */
    suspend fun syncFromCloud(userId: String)

    /**
     * Starts listening for real-time notification updates from Firestore.
     * Should be called when the app becomes active.
     *
     * @param userId The current user's anonymous ID
     */
    fun startRealtimeSync(userId: String)

    /**
     * Stops listening for real-time updates.
     * Should be called when the app goes to background.
     */
    fun stopRealtimeSync()

    /**
     * Deletes all notifications (GDPR compliance).
     */
    suspend fun deleteAllNotifications()

    /**
     * Convenience method that syncs notifications for the current user.
     * Gets the user ID from Firebase internally.
     * @return true if sync was successful, false if user not authenticated
     */
    suspend fun syncFromCloudForCurrentUser(): Boolean

    /**
     * Convenience method that starts real-time sync for the current user.
     * Gets the user ID from Firebase internally.
     * @return true if sync was started, false if user not authenticated
     */
    fun startRealtimeSyncForCurrentUser(): Boolean

    /**
     * Submits a negative test result for an exposure notification.
     * Updates the chain data to show reduced risk for others in the chain.
     *
     * @param notificationId The notification ID
     * @param stiType The STI type that was tested negative
     * @return true if submission was successful
     */
    suspend fun submitNegativeResult(
        notificationId: String,
        stiType: String,
    ): Boolean
}
