package app.justfyi.domain.usecase

import app.justfyi.domain.repository.UserRepository
import app.justfyi.util.AppCoroutineDispatchers
import app.justfyi.util.Logger
import com.google.firebase.firestore.FirebaseFirestore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Implementation of UsernameUseCase for Android.
 * Uses Firebase Firestore for duplicate username detection.
 *
 * Key features:
 * - Validates username (ASCII only, max 30 chars)
 * - Queries Firestore for existing usernames
 * - Generates unique usernames with emoji suffix
 */
@Inject
class UsernameUseCaseImpl(
    private val userRepository: UserRepository,
    private val firestore: FirebaseFirestore,
    private val dispatchers: AppCoroutineDispatchers,
) : UsernameUseCase {
    private val usersCollection = firestore.collection(COLLECTION_USERS)

    companion object {
        private const val TAG = "UsernameUseCaseImpl"
        private const val COLLECTION_USERS = "users"
        private const val FIELD_USERNAME = "username"
        private const val FIRESTORE_TIMEOUT_MS = 3000L // 3 second timeout for Firestore queries
    }

    override suspend fun setUsername(name: String): Result<String> =
        withContext(dispatchers.io) {
            Logger.d(TAG, "setUsername: Starting update to '$name'")
            try {
                // Validate username
                val validation = validateUsername(name)
                if (!validation.isValid()) {
                    Logger.w(TAG, "setUsername: Validation failed: $validation")
                    return@withContext Result.failure(UsernameError.ValidationFailed(validation))
                }

                // Get current user
                val currentUser = userRepository.getCurrentUser()
                if (currentUser == null) {
                    Logger.e(TAG, "setUsername: No user signed in")
                    return@withContext Result.failure(UsernameError.NotSignedIn)
                }
                Logger.d(TAG, "setUsername: Current user ID: ${Logger.truncateId(currentUser.id)}")

                // Generate unique username if duplicate exists
                val finalUsername = generateUniqueUsername(name)
                Logger.d(TAG, "setUsername: Final username: '$finalUsername'")

                // Update username in repository
                userRepository.updateUsername(currentUser.id, finalUsername)
                Logger.d(TAG, "setUsername: Username updated successfully")

                Result.success(finalUsername)
            } catch (e: Exception) {
                Logger.e(TAG, "setUsername: Failed", e)
                Result.failure(UsernameError.UpdateFailed(e.message ?: "Unknown error"))
            }
        }

    override fun validateUsername(name: String): UsernameValidationResult {
        // Check empty
        if (name.isEmpty()) {
            return UsernameValidationResult.Empty
        }

        // Check length
        if (name.length > UsernameConstants.MAX_USERNAME_LENGTH) {
            return UsernameValidationResult.TooLong(
                maxLength = UsernameConstants.MAX_USERNAME_LENGTH,
                actualLength = name.length,
            )
        }

        // Check for non-ASCII characters
        val nonAsciiChars = name.filter { it.code > 127 }
        if (nonAsciiChars.isNotEmpty()) {
            return UsernameValidationResult.NonAsciiCharacters(nonAsciiChars.toList())
        }

        // Check for non-printable ASCII characters (0-31)
        if (name.any { it.code < 32 }) {
            return UsernameValidationResult.NonPrintableCharacters
        }

        return UsernameValidationResult.Valid
    }

    override suspend fun generateUniqueUsername(base: String): String =
        withContext(dispatchers.io) {
            // Check if base username is available
            if (!isUsernameTaken(base)) {
                return@withContext base
            }

            // Username is taken, try adding emoji suffixes
            val shuffledEmojis = UsernameConstants.EMOJI_SUFFIXES.shuffled()

            for (emoji in shuffledEmojis) {
                val candidate = "$base$emoji"
                if (!isUsernameTaken(candidate)) {
                    return@withContext candidate
                }
            }

            // All single emojis are taken (unlikely), try combinations
            for (i in shuffledEmojis.indices) {
                for (j in shuffledEmojis.indices) {
                    if (i != j) {
                        val candidate = "$base${shuffledEmojis[i]}${shuffledEmojis[j]}"
                        if (!isUsernameTaken(candidate)) {
                            return@withContext candidate
                        }
                    }
                }
            }

            // Ultimate fallback: add timestamp-based suffix
            "$base${shuffledEmojis.random()}${System.currentTimeMillis() % 1000}"
        }

    override suspend fun getCurrentUsername(): String? =
        withContext(dispatchers.io) {
            userRepository.getCurrentUser()?.username
        }

    override suspend fun isUsernameTaken(username: String): Boolean =
        withContext(dispatchers.io) {
            // Use timeout to avoid blocking on slow network
            val result =
                withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                    try {
                        // Query Firestore for users with this username
                        val querySnapshot =
                            usersCollection
                                .whereEqualTo(FIELD_USERNAME, username)
                                .limit(1)
                                .get()
                                .await()

                        // If any document exists with this username, it's taken
                        val isTaken = !querySnapshot.isEmpty

                        // Also check if it's our own username (user can keep their current username)
                        if (isTaken) {
                            val currentUser = userRepository.getCurrentUser()
                            if (currentUser != null) {
                                val matchingDoc = querySnapshot.documents.firstOrNull()
                                // Document ID is user.id (original case Firebase UID)
                                if (matchingDoc?.id == currentUser.id) {
                                    // It's our own username, not considered "taken"
                                    return@withTimeoutOrNull false
                                }
                            }
                        }

                        isTaken
                    } catch (e: Exception) {
                        Logger.w(TAG, "isUsernameTaken: Firestore query failed", e)
                        // If we can't check Firestore, assume it's not taken
                        false
                    }
                }

            // If timeout occurred, assume username is available (local-first)
            result ?: run {
                Logger.w(TAG, "isUsernameTaken: Firestore query timed out, assuming available")
                false
            }
        }
}
