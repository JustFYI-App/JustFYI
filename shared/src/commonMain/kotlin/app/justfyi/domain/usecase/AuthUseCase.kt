package app.justfyi.domain.usecase

import app.justfyi.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Use case interface for authentication operations.
 * Handles anonymous authentication, account recovery, and user session management.
 *
 * Key responsibilities:
 * - Anonymous sign-in using Firebase Anonymous Auth
 * - Account recovery using saved ID (Mullvad-style)
 * - Sign-out with local data cleanup
 * - Current user state management
 */
interface AuthUseCase {
    /**
     * Signs in anonymously using Firebase Anonymous Auth.
     * Creates a new user if this is the first launch.
     * Returns the current user if already signed in.
     *
     * @return Result containing the authenticated User or an error
     */
    suspend fun signInAnonymously(): Result<User>

    /**
     * Recovers an existing account using a previously saved ID.
     * This allows users to restore their account after reinstall.
     *
     * The savedId should be the Firebase anonymous UID that the user
     * copied/saved from the Profile screen.
     *
     * @param savedId The Firebase anonymous UID to recover
     * @return Result containing the recovered User or an error
     */
    suspend fun recoverAccount(savedId: String): Result<User>

    /**
     * Signs out the current user and clears local data.
     * After sign-out, a new anonymous account will be created on next launch.
     *
     * @return Result indicating success or failure
     */
    suspend fun signOut(): Result<Unit>

    /**
     * Returns the current user's anonymous UID.
     * Returns null if no user is signed in.
     *
     * @return The current user's Firebase anonymous UID, or null
     */
    suspend fun getCurrentUserId(): String?

    /**
     * Observes the current user as a Flow.
     * Emits null if no user is signed in.
     *
     * @return Flow of the current User or null
     */
    fun observeCurrentUser(): Flow<User?>

    /**
     * Checks if a user is currently signed in.
     *
     * @return true if a user is signed in, false otherwise
     */
    suspend fun isSignedIn(): Boolean

    /**
     * Marks the ID backup as confirmed.
     * Called when user confirms they've saved their ID.
     *
     * @return Result indicating success or failure
     */
    suspend fun confirmIdBackup(): Result<Unit>

    /**
     * Checks if the user has confirmed their ID backup.
     *
     * @return true if backup is confirmed, false otherwise
     */
    suspend fun isIdBackupConfirmed(): Boolean
}

/**
 * Sealed class representing authentication errors.
 */
sealed class AuthError : Exception() {
    /**
     * Firebase authentication failed.
     */
    data class FirebaseAuthFailed(
        override val message: String,
    ) : AuthError()

    /**
     * Account recovery failed - ID not found in cloud.
     */
    data class AccountNotFound(
        val savedId: String,
    ) : AuthError() {
        override val message: String = "Account with ID $savedId not found"
    }

    /**
     * Sign out failed.
     */
    data class SignOutFailed(
        override val message: String,
    ) : AuthError()

    /**
     * No user is currently signed in.
     */
    object NotSignedIn : AuthError() {
        override val message: String = "No user is currently signed in"
    }
}
