package app.justfyi.domain.repository

import app.justfyi.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for user data operations.
 * Follows local-first pattern with Firebase sync.
 */
interface UserRepository {
    /**
     * Gets the current authenticated user.
     * Returns null if no user is signed in.
     */
    suspend fun getCurrentUser(): User?

    /**
     * Gets the current user as a Flow for reactive updates.
     */
    fun observeCurrentUser(): Flow<User?>

    /**
     * Saves a new user to local storage and syncs to Firebase.
     * @param user The user to save
     */
    suspend fun saveUser(user: User)

    /**
     * Updates the user's username.
     * Local-first: updates locally, then syncs to Firebase.
     * @param userId The user's ID
     * @param newUsername The new username (must be validated before calling)
     */
    suspend fun updateUsername(
        userId: String,
        newUsername: String,
    )

    /**
     * Updates the user's FCM token for push notifications.
     * @param userId The user's ID
     * @param fcmToken The new FCM token
     */
    suspend fun updateFcmToken(
        userId: String,
        fcmToken: String?,
    )

    /**
     * Updates the user's ID backup confirmation status.
     * @param userId The user's ID
     * @param confirmed Whether the user has confirmed backing up their ID
     */
    suspend fun updateIdBackupConfirmed(
        userId: String,
        confirmed: Boolean,
    )

    /**
     * Deletes the user from local storage and Firebase (GDPR compliance).
     * @param userId The user's ID
     */
    suspend fun deleteUser(userId: String)

    /**
     * Syncs user data from Firebase to local storage.
     * Used for account recovery and initial sync.
     * @param anonymousId The Firebase anonymous ID to look up
     * @return The synced user, or null if not found
     */
    suspend fun syncFromCloud(anonymousId: String): User?
}
