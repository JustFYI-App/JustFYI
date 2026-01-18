package app.justfyi.domain.usecase

/**
 * Use case for ID backup and recovery functionality.
 * Implements Mullvad-style ID backup system for anonymous account recovery.
 *
 * Key features:
 * - Format anonymous ID for user-friendly display
 * - Track backup confirmation status
 * - Copy ID to clipboard
 */
interface IdBackupUseCase {
    /**
     * Formats the anonymous ID for user-friendly display.
     * Groups the ID into readable segments (e.g., "XXXX-XXXX-XXXX").
     *
     * @param anonymousId The raw Firebase anonymous UID
     * @return Formatted ID string for display
     */
    fun formatIdForDisplay(anonymousId: String): String

    /**
     * Gets the raw anonymous ID from a formatted display ID.
     * Removes formatting characters (spaces, dashes).
     *
     * @param formattedId The formatted ID from user input
     * @return Raw anonymous ID
     */
    fun parseFormattedId(formattedId: String): String

    /**
     * Validates if a provided ID has the correct format.
     *
     * @param id The ID to validate (can be raw or formatted)
     * @return true if the ID format is valid
     */
    fun isValidIdFormat(id: String): Boolean
}

/**
 * Default implementation of IdBackupUseCase.
 * Can be used in common code without platform dependencies.
 */
class IdBackupUseCaseImpl : IdBackupUseCase {
    override fun formatIdForDisplay(anonymousId: String): String {
        // Firebase anonymous UIDs are typically 28 characters
        // Format as groups of 4-4-4-4-4-4-4 for readability
        val cleaned = anonymousId.replace("[^a-zA-Z0-9]".toRegex(), "")

        return cleaned
            .chunked(GROUP_SIZE)
            .joinToString(SEPARATOR)
    }

    override fun parseFormattedId(formattedId: String): String {
        // Remove all formatting characters (spaces, dashes, etc.)
        return formattedId
            .replace("[^a-zA-Z0-9]".toRegex(), "")
    }

    override fun isValidIdFormat(id: String): Boolean {
        val cleaned = parseFormattedId(id)
        // Firebase anonymous UIDs are typically 28 characters
        // But allow some flexibility (20-40 chars)
        return cleaned.length in MIN_ID_LENGTH..MAX_ID_LENGTH &&
            cleaned.all { it.isLetterOrDigit() }
    }

    companion object {
        private const val GROUP_SIZE = 4
        private const val SEPARATOR = "-"
        private const val MIN_ID_LENGTH = 20
        private const val MAX_ID_LENGTH = 40
    }
}
