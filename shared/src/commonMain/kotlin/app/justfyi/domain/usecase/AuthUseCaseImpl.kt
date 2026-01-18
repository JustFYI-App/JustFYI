package app.justfyi.domain.usecase

import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.domain.model.User
import app.justfyi.domain.repository.UserRepository
import app.justfyi.util.AppCoroutineDispatchers
import app.justfyi.util.Logger
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Common implementation of AuthUseCase for all platforms.
 * Uses FirebaseProvider abstraction for Firebase operations.
 *
 * Key features:
 * - Anonymous sign-in creates a new Firebase user
 * - Account recovery syncs data from Firestore using saved ID
 * - Sign-out clears both Firebase auth and local data
 * - Mullvad-style ID backup confirmation tracking
 * - Fallback to local-only user when Firebase is unavailable
 * - Validates Firebase account exists on startup (with offline support)
 */
@Inject
class AuthUseCaseImpl(
    private val firebaseProvider: FirebaseProvider,
    private val userRepository: UserRepository,
    private val dispatchers: AppCoroutineDispatchers,
) : AuthUseCase {
    companion object {
        private const val TAG = "AuthUseCaseImpl"
        private const val DEFAULT_USERNAME = "Anonymous"
    }

    override suspend fun signInAnonymously(): Result<User> =
        withContext(dispatchers.io) {
            try {
                Logger.d(TAG, "signInAnonymously: Starting sign-in process")

                // Check if user already exists locally
                val existingUser = userRepository.getCurrentUser()
                if (existingUser != null) {
                    Logger.d(
                        TAG,
                        "signInAnonymously: Found existing local user: ${Logger.truncateId(
                            existingUser.anonymousId,
                        )}",
                    )

                    // Validate that Firebase account still exists and matches
                    val currentUserId = firebaseProvider.getCurrentUserId()
                    when {
                        currentUserId != null && currentUserId == existingUser.anonymousId -> {
                            // Firebase account matches local user - all good
                            Logger.d(TAG, "signInAnonymously: Firebase account matches local user")
                            fetchAndSaveFcmToken(existingUser.id)
                            return@withContext Result.success(existingUser)
                        }
                        currentUserId != null && currentUserId != existingUser.anonymousId -> {
                            // Firebase account exists but doesn't match - clear local and use Firebase
                            Logger.w(
                                TAG,
                                "signInAnonymously: Firebase UID mismatch. Local: ${Logger.truncateId(existingUser.anonymousId)}, Firebase: ${Logger.truncateId(currentUserId)}",
                            )
                            userRepository.deleteUser(existingUser.id)
                            val syncedUser = userRepository.syncFromCloud(currentUserId)
                            if (syncedUser != null) {
                                fetchAndSaveFcmToken(syncedUser.id)
                                return@withContext Result.success(syncedUser)
                            }
                            val newUser = createNewUser(currentUserId)
                            userRepository.saveUser(newUser)
                            fetchAndSaveFcmToken(newUser.id)
                            return@withContext Result.success(newUser)
                        }
                        else -> {
                            // Firebase currentUser is null - could be offline or account deleted
                            // Try to re-authenticate; if offline this will fail gracefully
                            Logger.d(TAG, "signInAnonymously: No Firebase user, attempting re-auth")
                            try {
                                val newUserId = firebaseProvider.signInAnonymously()
                                if (newUserId != null) {
                                    if (newUserId == existingUser.anonymousId) {
                                        // Same account restored
                                        Logger.d(TAG, "signInAnonymously: Re-auth restored same account")
                                        fetchAndSaveFcmToken(existingUser.id)
                                        return@withContext Result.success(existingUser)
                                    } else {
                                        // Got a different account - old one was deleted
                                        Logger.w(TAG, "signInAnonymously: Re-auth created new account, old was deleted")
                                        userRepository.deleteUser(existingUser.id)
                                        val newUser = createNewUser(newUserId)
                                        userRepository.saveUser(newUser)
                                        fetchAndSaveFcmToken(newUser.id)
                                        return@withContext Result.success(newUser)
                                    }
                                }
                            } catch (e: Exception) {
                                // Re-auth failed - likely offline, continue with local user
                                Logger.w(TAG, "signInAnonymously: Re-auth failed (likely offline), using local user", e)
                                return@withContext Result.success(existingUser)
                            }
                            // Fallback to local user
                            return@withContext Result.success(existingUser)
                        }
                    }
                }

                // Check if Firebase has a current user
                val currentUserId = firebaseProvider.getCurrentUserId()
                if (currentUserId != null) {
                    Logger.d(
                        TAG,
                        "signInAnonymously: Found existing Firebase user: ${Logger.truncateId(currentUserId)}",
                    )
                    // User exists in Firebase, sync to local
                    val syncedUser = userRepository.syncFromCloud(currentUserId)
                    if (syncedUser != null) {
                        fetchAndSaveFcmToken(syncedUser.id)
                        return@withContext Result.success(syncedUser)
                    }
                    // No cloud data, create new local user
                    val newUser = createNewUser(currentUserId)
                    userRepository.saveUser(newUser)
                    fetchAndSaveFcmToken(newUser.id)
                    return@withContext Result.success(newUser)
                }

                // No existing user, try to create new anonymous user with Firebase
                Logger.d(TAG, "signInAnonymously: Attempting Firebase anonymous sign-in")
                try {
                    val newUserId = firebaseProvider.signInAnonymously()
                    if (newUserId != null) {
                        Logger.d(
                            TAG,
                            "signInAnonymously: Firebase sign-in successful: ${Logger.truncateId(newUserId)}",
                        )
                        val newUser = createNewUser(newUserId)
                        userRepository.saveUser(newUser)
                        fetchAndSaveFcmToken(newUser.id)
                        return@withContext Result.success(newUser)
                    }
                } catch (firebaseError: Exception) {
                    Logger.w(
                        TAG,
                        "signInAnonymously: Firebase sign-in failed, falling back to local user",
                        firebaseError,
                    )
                    // Fall through to create local-only user
                }

                // Fallback: Create a local-only user when Firebase is unavailable
                // This allows the app to work during development or when offline
                Logger.d(TAG, "signInAnonymously: Creating local-only user")
                val localOnlyId = generateLocalId()
                val localUser = createNewUser(localOnlyId)
                userRepository.saveUser(localUser)
                fetchAndSaveFcmToken(localUser.id)
                Logger.d(TAG, "signInAnonymously: Local user created: ${Logger.truncateId(localUser.anonymousId)}")
                Result.success(localUser)
            } catch (e: Exception) {
                Logger.e(TAG, "signInAnonymously: Unexpected error", e)
                Result.failure(AuthError.FirebaseAuthFailed(e.message ?: "Unknown error during sign-in"))
            }
        }

    override suspend fun recoverAccount(savedId: String): Result<User> =
        withContext(dispatchers.io) {
            try {
                val trimmedId = savedId.trim()
                Logger.d(TAG, "recoverAccount: Attempting recovery with ID: ${Logger.truncateId(trimmedId)}")

                // Step 1: Call Cloud Function to get a custom token for the saved ID
                val functionResult =
                    firebaseProvider.callFunction(
                        "recoverAccount",
                        mapOf("savedId" to trimmedId),
                    )

                val success = functionResult?.get("success") as? Boolean ?: false
                if (!success) {
                    val error = functionResult?.get("error") as? String ?: "Account not found"
                    Logger.w(TAG, "recoverAccount: Cloud function returned error: $error")
                    return@withContext Result.failure(AuthError.AccountNotFound(savedId))
                }

                val customToken =
                    functionResult["customToken"] as? String
                        ?: return@withContext Result.failure(
                            AuthError.FirebaseAuthFailed("No custom token received"),
                        )

                Logger.d(TAG, "recoverAccount: Got custom token, signing in...")

                // Step 2: Sign in with the custom token to restore the original account
                val userId =
                    firebaseProvider.signInWithCustomToken(customToken)
                        ?: return@withContext Result.failure(
                            AuthError.FirebaseAuthFailed("Custom token sign-in failed"),
                        )

                Logger.d(TAG, "recoverAccount: Signed in as ${Logger.truncateId(userId)}")

                // Step 3: Sync user data from Firestore
                val recoveredUser =
                    userRepository.syncFromCloud(userId)
                        ?: return@withContext Result.failure(AuthError.AccountNotFound(savedId))

                // Step 4: Update FCM token for push notifications
                fetchAndSaveFcmToken(recoveredUser.id)

                Logger.d(TAG, "recoverAccount: Recovery successful for user ${Logger.truncateId(recoveredUser.id)}")
                Result.success(recoveredUser)
            } catch (e: Exception) {
                Logger.e(TAG, "recoverAccount: Recovery failed", e)
                Result.failure(AuthError.FirebaseAuthFailed(e.message ?: "Unknown error during recovery"))
            }
        }

    override suspend fun signOut(): Result<Unit> =
        withContext(dispatchers.io) {
            try {
                // Get current user before signing out
                val currentUser = userRepository.getCurrentUser()

                // Sign out from Firebase
                firebaseProvider.signOut()

                // Delete local user data
                if (currentUser != null) {
                    userRepository.deleteUser(currentUser.id)
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(AuthError.SignOutFailed(e.message ?: "Unknown error during sign-out"))
            }
        }

    override suspend fun getCurrentUserId(): String? =
        withContext(dispatchers.io) {
            userRepository.getCurrentUser()?.anonymousId
        }

    override fun observeCurrentUser(): Flow<User?> = userRepository.observeCurrentUser()

    override suspend fun isSignedIn(): Boolean =
        withContext(dispatchers.io) {
            firebaseProvider.isAuthenticated() && userRepository.getCurrentUser() != null
        }

    override suspend fun confirmIdBackup(): Result<Unit> =
        withContext(dispatchers.io) {
            try {
                val currentUser =
                    userRepository.getCurrentUser()
                        ?: return@withContext Result.failure(AuthError.NotSignedIn)

                userRepository.updateIdBackupConfirmed(currentUser.id, true)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun isIdBackupConfirmed(): Boolean =
        withContext(dispatchers.io) {
            userRepository.getCurrentUser()?.idBackupConfirmed ?: false
        }

    /**
     * Creates a new User instance for first-time sign-in.
     *
     * Both `id` and `anonymousId` store the original Firebase UID.
     * This is shown to user for account recovery and used as Firestore document ID.
     */
    private fun createNewUser(firebaseUid: String): User =
        User(
            id = firebaseUid,
            anonymousId = firebaseUid, // Same as id - shown to user for backup
            username = DEFAULT_USERNAME,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            fcmToken = null,
            idBackupConfirmed = false,
        )

    /**
     * Generates a local-only ID when Firebase is unavailable.
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun generateLocalId(): String =
        Uuid.random()
            .toString()
            .replace("-", "")
            .uppercase()

    /**
     * Fetches and saves the FCM token for the given user.
     * This is called after user creation to enable push notifications.
     */
    private suspend fun fetchAndSaveFcmToken(userId: String) {
        try {
            val fcmToken = firebaseProvider.getFcmToken()
            if (fcmToken != null) {
                Logger.d(TAG, "fetchAndSaveFcmToken: Got FCM token, saving for user ${Logger.truncateId(userId)}")
                userRepository.updateFcmToken(userId, fcmToken)
            } else {
                Logger.w(TAG, "fetchAndSaveFcmToken: FCM token is null")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "fetchAndSaveFcmToken: Failed to get/save FCM token", e)
            // Non-fatal error - app can work without FCM token
        }
    }
}
