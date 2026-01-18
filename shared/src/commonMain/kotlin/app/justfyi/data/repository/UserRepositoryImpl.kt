package app.justfyi.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.data.local.UserQueries
import app.justfyi.domain.model.User
import app.justfyi.domain.repository.UserRepository
import app.justfyi.util.AppCoroutineDispatchers
import app.justfyi.util.HashUtils
import app.justfyi.util.Logger
import app.justfyi.util.currentTimeMillis
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import app.justfyi.data.local.User as DbUser

/**
 * Multiplatform implementation of UserRepository using GitLive Firebase SDK.
 * Follows local-first pattern: writes to SQLDelight first, then syncs to Firebase.
 *
 * This implementation uses the FirebaseProvider abstraction which works
 * on both Android and iOS through the GitLive SDK.
 */
@Inject
class UserRepositoryImpl(
    private val userQueries: UserQueries,
    private val firebaseProvider: FirebaseProvider,
    private val dispatchers: AppCoroutineDispatchers,
) : UserRepository {
    // Scope for fire-and-forget Firebase sync operations
    private val syncScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    override suspend fun getCurrentUser(): User? =
        withContext(dispatchers.io) {
            userQueries.getCurrentUser().executeAsOneOrNull()?.toDomain()
        }

    override fun observeCurrentUser(): Flow<User?> =
        userQueries
            .getCurrentUser()
            .asFlow()
            .mapToOneOrNull(dispatchers.io)
            .map { it?.toDomain() }

    override suspend fun saveUser(user: User): Unit =
        withContext(dispatchers.io) {
            Logger.d(TAG, "saveUser: Saving user ${user.id}")

            // Local-first: save to SQLDelight
            userQueries.insertUser(
                id = user.id,
                anonymous_id = user.anonymousId,
                username = user.username,
                created_at = user.createdAt,
                fcm_token = user.fcmToken,
                id_backup_confirmed = if (user.idBackupConfirmed) 1L else 0L,
            )
            Logger.d(TAG, "saveUser: Local database save successful")

            // Sync to Firebase (with timeout to avoid blocking)
            // IMPORTANT: Use user.id (original Firebase UID) as document ID for security rules
            // The anonymousId is the same as the UID and used for display/backup purposes
            withTimeoutOrNull(FIREBASE_TIMEOUT_MS) {
                try {
                    // Precompute hashed IDs for Cloud Function lookups
                    // hashedInteractionId: allows backend to find user from interaction.ownerId
                    // hashedNotificationId: used directly as notification.recipientId
                    val hashedInteractionId = HashUtils.hashForInteraction(user.id)
                    val hashedNotificationId = HashUtils.hashForNotification(user.id)
                    val userData =
                        mapOf(
                            FIELD_ANONYMOUS_ID to user.anonymousId,
                            FIELD_USERNAME to user.username,
                            FIELD_CREATED_AT to user.createdAt,
                            FIELD_FCM_TOKEN to user.fcmToken,
                            FIELD_HASHED_INTERACTION_ID to hashedInteractionId,
                            FIELD_HASHED_NOTIFICATION_ID to hashedNotificationId,
                        )
                    firebaseProvider.setDocument(
                        collection = COLLECTION_USERS,
                        documentId = user.id, // Original case Firebase UID for security rules
                        data = userData,
                        merge = true,
                    )
                    Logger.d(TAG, "saveUser: Firebase sync successful")
                } catch (e: Exception) {
                    Logger.w(TAG, "saveUser: Firebase sync failed (will retry later)")
                    // Firebase sync failed, but local save succeeded
                }
            } ?: Logger.w(TAG, "saveUser: Firebase sync timed out (will retry later)")
            Unit
        }

    override suspend fun updateUsername(
        userId: String,
        newUsername: String,
    ): Unit =
        withContext(dispatchers.io) {
            Logger.d(TAG, "updateUsername: Updating username to '$newUsername' for user $userId")

            // Local-first: update SQLDelight immediately
            userQueries.updateUsername(username = newUsername, id = userId)
            Logger.d(TAG, "updateUsername: Local database updated successfully")

            // Get user to sync to Firebase (fire-and-forget, don't block UI)
            val user = userQueries.getUserById(userId).executeAsOneOrNull()
            if (user != null) {
                // Fire-and-forget: sync to Firebase without blocking
                // IMPORTANT: Use user.id (original Firebase UID) as document ID for security rules
                syncScope.launch {
                    withTimeoutOrNull(FIREBASE_TIMEOUT_MS) {
                        try {
                            firebaseProvider.updateDocument(
                                collection = COLLECTION_USERS,
                                documentId = user.id,
                                updates = mapOf(FIELD_USERNAME to newUsername),
                            )
                            Logger.d(TAG, "updateUsername: Firebase sync successful")
                        } catch (e: Exception) {
                            Logger.w(TAG, "updateUsername: Firebase sync failed (will retry later)")
                        }
                    } ?: Logger.w(TAG, "updateUsername: Firebase sync timed out (will retry later)")
                }
            }
            Unit
        }

    override suspend fun updateFcmToken(
        userId: String,
        fcmToken: String?,
    ): Unit =
        withContext(dispatchers.io) {
            // Local-first: update SQLDelight
            userQueries.updateFcmToken(fcm_token = fcmToken, id = userId)

            // Get user to sync to Firebase
            // IMPORTANT: Use user.id (original Firebase UID) as document ID for security rules
            val user = userQueries.getUserById(userId).executeAsOneOrNull()
            if (user != null) {
                try {
                    firebaseProvider.updateDocument(
                        collection = COLLECTION_USERS,
                        documentId = user.id,
                        updates = mapOf(FIELD_FCM_TOKEN to fcmToken),
                    )
                } catch (e: Exception) {
                    // Firebase sync failed, local update succeeded
                    Logger.w(TAG, "updateFcmToken: Firebase sync failed")
                }
            }
            Unit
        }

    override suspend fun updateIdBackupConfirmed(
        userId: String,
        confirmed: Boolean,
    ): Unit =
        withContext(dispatchers.io) {
            userQueries.updateIdBackupConfirmed(
                id_backup_confirmed = if (confirmed) 1L else 0L,
                id = userId,
            )
            // ID backup status is local-only, no need to sync to Firebase
            Unit
        }

    override suspend fun deleteUser(userId: String): Unit =
        withContext(dispatchers.io) {
            val user = userQueries.getUserById(userId).executeAsOneOrNull()

            // Delete from Firebase first (if it fails, don't delete locally so user can retry)
            // IMPORTANT: Use user.id (original Firebase UID) as document ID for security rules
            if (user != null) {
                firebaseProvider.deleteDocument(
                    collection = COLLECTION_USERS,
                    documentId = user.id,
                )
            }

            // Delete from local storage only after Firebase succeeds
            userQueries.deleteUser(userId)
            Unit
        }

    override suspend fun syncFromCloud(anonymousId: String): User? =
        withContext(dispatchers.io) {
            try {
                // Direct document lookup - anonymousId is the same as document ID
                val document = firebaseProvider.getDocument(COLLECTION_USERS, anonymousId)
                if (document != null) {
                    val cloudUser =
                        User(
                            id = anonymousId,
                            anonymousId = document[FIELD_ANONYMOUS_ID] as? String ?: anonymousId,
                            username = document[FIELD_USERNAME] as? String ?: "Anonymous",
                            createdAt = (document[FIELD_CREATED_AT] as? Number)?.toLong() ?: currentTimeMillis(),
                            fcmToken = document[FIELD_FCM_TOKEN] as? String,
                            idBackupConfirmed = false, // Reset on recovery
                        )

                    // Server wins: replace local data with cloud data
                    userQueries.deleteAllUsers()
                    userQueries.insertUser(
                        id = cloudUser.id,
                        anonymous_id = cloudUser.anonymousId,
                        username = cloudUser.username,
                        created_at = cloudUser.createdAt,
                        fcm_token = cloudUser.fcmToken,
                        id_backup_confirmed = 0L,
                    )

                    cloudUser
                } else {
                    Logger.d(TAG, "syncFromCloud: No user found with id=$anonymousId")
                    null
                }
            } catch (e: Exception) {
                Logger.e(TAG, "syncFromCloud failed", e)
                null
            }
        }

    private fun DbUser.toDomain(): User =
        User(
            id = id,
            anonymousId = anonymous_id,
            username = username,
            createdAt = created_at,
            fcmToken = fcm_token,
            idBackupConfirmed = id_backup_confirmed == 1L,
        )

    companion object {
        private const val TAG = "UserRepositoryImpl"
        private const val FIREBASE_TIMEOUT_MS = 5000L // 5 second timeout for Firebase operations
        const val COLLECTION_USERS = "users"
        const val FIELD_ANONYMOUS_ID = "anonymousId"
        const val FIELD_USERNAME = "username"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_FCM_TOKEN = "fcmToken"
        const val FIELD_HASHED_INTERACTION_ID = "hashedInteractionId"
        const val FIELD_HASHED_NOTIFICATION_ID = "hashedNotificationId"
    }
}
