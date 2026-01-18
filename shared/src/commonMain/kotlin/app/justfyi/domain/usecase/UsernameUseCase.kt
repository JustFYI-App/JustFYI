package app.justfyi.domain.usecase

/**
 * Use case interface for username management.
 * Handles username validation, setting, and duplicate handling.
 *
 * Key features:
 * - Username validation (ASCII only, max 30 chars)
 * - Duplicate username detection via Firestore
 * - Unique username generation with emoji suffix
 */
interface UsernameUseCase {
    /**
     * Sets the username for the current user.
     * Validates the username before saving.
     *
     * @param name The desired username
     * @return Result containing the final username (may have emoji suffix if duplicate)
     *         or an error if validation fails
     */
    suspend fun setUsername(name: String): Result<String>

    /**
     * Validates a username according to requirements.
     * - ASCII characters only (printable: 32-126)
     * - Maximum 30 characters
     * - Non-empty
     *
     * @param name The username to validate
     * @return ValidationResult indicating success or failure with reason
     */
    fun validateUsername(name: String): UsernameValidationResult

    /**
     * Generates a unique username by adding emoji suffix if duplicate exists.
     * Queries Firestore to check for existing usernames.
     *
     * @param base The desired base username
     * @return The unique username (base if unique, or base + emoji if duplicate)
     */
    suspend fun generateUniqueUsername(base: String): String

    /**
     * Gets the current user's username.
     *
     * @return The current username, or null if no user is signed in
     */
    suspend fun getCurrentUsername(): String?

    /**
     * Checks if a username is already taken.
     *
     * @param username The username to check
     * @return true if the username is taken, false if available
     */
    suspend fun isUsernameTaken(username: String): Boolean
}

/**
 * Result of username validation.
 */
sealed class UsernameValidationResult {
    /**
     * Username is valid.
     */
    object Valid : UsernameValidationResult()

    /**
     * Username is empty.
     */
    object Empty : UsernameValidationResult()

    /**
     * Username exceeds maximum length.
     */
    data class TooLong(
        val maxLength: Int,
        val actualLength: Int,
    ) : UsernameValidationResult()

    /**
     * Username contains non-ASCII characters.
     */
    data class NonAsciiCharacters(
        val invalidChars: List<Char>,
    ) : UsernameValidationResult()

    /**
     * Username contains non-printable characters.
     */
    object NonPrintableCharacters : UsernameValidationResult()

    /**
     * Helper to check if result is valid.
     */
    fun isValid(): Boolean = this is Valid
}

/**
 * Constants for username validation.
 */
object UsernameConstants {
    const val MAX_USERNAME_LENGTH = 30
    const val MIN_USERNAME_LENGTH = 1

    /**
     * Range of printable ASCII characters (code points 32-126).
     * Used for filtering input to only allow valid username characters.
     */
    private val PRINTABLE_ASCII_RANGE = 32..126

    /**
     * Filters a string to only contain printable ASCII characters (code points 32-126).
     * Non-ASCII characters (including emojis) are stripped from the input.
     *
     * @param input The input string to filter
     * @return The filtered string containing only printable ASCII characters
     */
    fun filterToAscii(input: String): String {
        return input.filter { it.code in PRINTABLE_ASCII_RANGE }
    }

    /**
     * Filters and truncates input for username fields.
     * Combines ASCII filtering with max length enforcement.
     *
     * @param input The input string to filter
     * @return The filtered string, truncated to MAX_USERNAME_LENGTH
     */
    fun filterUsernameInput(input: String): String {
        return filterToAscii(input).take(MAX_USERNAME_LENGTH)
    }

    /**
     * Curated list of emoji suffixes for duplicate username handling.
     * These are friendly, neutral emojis that won't cause confusion.
     */
    val EMOJI_SUFFIXES =
        listOf(
            "\ud83c\udf1f", // Star
            "\ud83c\udf08", // Rainbow
            "\ud83c\udf3b", // Sunflower
            "\ud83c\udf3c", // Blossom
            "\ud83c\udf3f", // Herb
            "\ud83c\udf40", // Four Leaf Clover
            "\ud83c\udf52", // Cherries
            "\ud83c\udf53", // Strawberry
            "\ud83e\udd8b", // Butterfly
            "\ud83e\udd9a", // Peacock
            "\ud83e\udd89", // Owl
            "\ud83d\udc22", // Turtle
            "\ud83d\udc1d", // Bee
            "\ud83c\udf32", // Evergreen Tree
            "\ud83c\udfb5", // Musical Note
            "\ud83d\udca7", // Droplet
            "\u2728", // Sparkles
            "\ud83c\udf38", // Cherry Blossom
            "\ud83c\udf3a", // Hibiscus
            "\ud83c\udf41", // Maple Leaf
        )
}

/**
 * Sealed class representing username errors.
 */
sealed class UsernameError : Exception() {
    /**
     * Username validation failed.
     */
    data class ValidationFailed(
        val result: UsernameValidationResult,
    ) : UsernameError() {
        override val message: String =
            when (result) {
                is UsernameValidationResult.Empty -> "Username cannot be empty"
                is UsernameValidationResult.TooLong -> "Username cannot exceed ${result.maxLength} characters"
                is UsernameValidationResult.NonAsciiCharacters -> "Username can only contain ASCII characters"
                is UsernameValidationResult.NonPrintableCharacters -> "Username contains invalid characters"
                is UsernameValidationResult.Valid -> "Username is valid" // Should not happen
            }
    }

    /**
     * User is not signed in.
     */
    object NotSignedIn : UsernameError() {
        override val message: String = "No user is currently signed in"
    }

    /**
     * Failed to update username.
     */
    data class UpdateFailed(
        override val message: String,
    ) : UsernameError()
}
