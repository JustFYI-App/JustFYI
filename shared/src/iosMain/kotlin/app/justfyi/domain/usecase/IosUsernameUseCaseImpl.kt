package app.justfyi.domain.usecase

import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.domain.repository.UserRepository
import app.justfyi.util.AppCoroutineDispatchers
import app.justfyi.util.Logger
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * iOS implementation of UsernameUseCase.
 * Uses GitLive Firebase SDK via FirebaseProvider for Firestore queries.
 *
 * Key features:
 * - Username validation (ASCII only, max 30 chars)
 * - Duplicate username detection via Firestore
 * - Unique username generation with emoji suffix
 */
class IosUsernameUseCaseImpl
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val firebaseProvider: FirebaseProvider,
        private val dispatchers: AppCoroutineDispatchers,
    ) : UsernameUseCase {
        companion object {
            private const val TAG = "IosUsernameUseCase"
            private const val USERS_COLLECTION = "users"
            private const val USERNAME_FIELD = "username"
        }

        override suspend fun setUsername(name: String): Result<String> =
            withContext(dispatchers.io) {
                try {
                    // Validate the username
                    val validationResult = validateUsername(name)
                    if (!validationResult.isValid()) {
                        return@withContext Result.failure(UsernameError.ValidationFailed(validationResult))
                    }

                    // Get current user
                    val currentUser =
                        userRepository.getCurrentUser()
                            ?: return@withContext Result.failure(UsernameError.NotSignedIn)

                    // Generate unique username (adds emoji suffix if duplicate)
                    val uniqueUsername = generateUniqueUsername(name)

                    // Update the user's username
                    userRepository.updateUsername(currentUser.id, uniqueUsername)

                    Logger.d(TAG, "Username set to: $uniqueUsername")
                    Result.success(uniqueUsername)
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to set username", e)
                    Result.failure(UsernameError.UpdateFailed(e.message ?: "Unknown error"))
                }
            }

        override fun validateUsername(name: String): UsernameValidationResult {
            // Check if empty
            if (name.isBlank()) {
                return UsernameValidationResult.Empty
            }

            // Check max length
            if (name.length > UsernameConstants.MAX_USERNAME_LENGTH) {
                return UsernameValidationResult.TooLong(
                    maxLength = UsernameConstants.MAX_USERNAME_LENGTH,
                    actualLength = name.length,
                )
            }

            // Check for non-printable characters (ASCII 32-126)
            if (name.any { it.code < 32 || it.code > 126 }) {
                return UsernameValidationResult.NonPrintableCharacters
            }

            // Check for non-ASCII characters
            val nonAscii = name.filter { it.code > 127 }
            if (nonAscii.isNotEmpty()) {
                return UsernameValidationResult.NonAsciiCharacters(nonAscii.toList())
            }

            return UsernameValidationResult.Valid
        }

        override suspend fun generateUniqueUsername(base: String): String =
            withContext(dispatchers.io) {
                // First check if base username is available
                if (!isUsernameTaken(base)) {
                    return@withContext base
                }

                // Username is taken, try adding emoji suffixes
                for (emoji in UsernameConstants.EMOJI_SUFFIXES) {
                    val candidate = "$base $emoji"
                    if (candidate.length <= UsernameConstants.MAX_USERNAME_LENGTH && !isUsernameTaken(candidate)) {
                        return@withContext candidate
                    }
                }

                // All emoji suffixes tried, add timestamp as last resort
                val timestamp = (NSDate().timeIntervalSince1970.toLong()) % 10000
                "$base#$timestamp"
            }

        override suspend fun getCurrentUsername(): String? =
            withContext(dispatchers.io) {
                userRepository.getCurrentUser()?.username
            }

        override suspend fun isUsernameTaken(username: String): Boolean =
            withContext(dispatchers.io) {
                try {
                    // Query Firestore for existing username
                    val results =
                        firebaseProvider.queryCollection(
                            collection = USERS_COLLECTION,
                            whereField = USERNAME_FIELD,
                            whereValue = username,
                        )

                    // Check if any other user has this username (excluding current user)
                    // Document ID is user.id (original case Firebase UID)
                    val currentUserId = userRepository.getCurrentUser()?.id
                    results.any { doc ->
                        val docId = doc["id"] as? String
                        docId != currentUserId
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Failed to check username availability, assuming available")
                    false // Assume username is available if check fails
                }
            }
    }
